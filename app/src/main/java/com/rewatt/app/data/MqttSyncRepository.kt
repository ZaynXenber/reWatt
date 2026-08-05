package com.rewatt.app.data

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import com.rewatt.app.BuildConfig
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class MqttStatus {
    Disconnected,
    Connecting,
    Connected,
    Error,
}

data class MqttState(
    val status: MqttStatus = MqttStatus.Disconnected,
    val brokerHost: String = "",
    val activeTopic: String = "",
    val messagesSentCount: Long = 0,
    val lastPublishedAtMillis: Long? = null,
    val errorMessage: String? = null,
)

class MqttSyncRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = appContext.getSharedPreferences("rewatt_mqtt_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val nodeId: String by lazy {
        Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }
            ?: "node-${UUID.randomUUID().toString().take(8)}"
    }

    private val _mqttState = MutableStateFlow(MqttState())
    val mqttState: StateFlow<MqttState> = _mqttState.asStateFlow()

    private var client: Mqtt3AsyncClient? = null
    private var messagesSent: Long = 0

    var host: String
        get() = prefs.getString("mqtt_host", "") ?: ""
        set(value) { prefs.edit().putString("mqtt_host", value).apply() }

    var port: Int
        get() = prefs.getInt("mqtt_port", 8883)
        set(value) { prefs.edit().putInt("mqtt_port", value).apply() }

    var username: String
        get() = prefs.getString("mqtt_user", "") ?: ""
        set(value) { prefs.edit().putString("mqtt_user", value).apply() }

    var password: String
        get() = prefs.getString("mqtt_pass", "") ?: ""
        set(value) { prefs.edit().putString("mqtt_pass", value).apply() }

    var roomId: String
        get() = prefs.getString("room_id", "DK1") ?: "DK1"
        set(value) { prefs.edit().putString("room_id", value).apply() }

    init {
        // Auto-connect if credentials are present
        if (host.isNotBlank()) {
            connect()
        }
    }

    fun configure(host: String, port: Int = 8883, user: String, pass: String, roomId: String) {
        this.host = host.trim()
        this.port = port
        this.username = user.trim()
        this.password = pass.trim()
        this.roomId = roomId.trim()
        connect()
    }

    @Synchronized
    fun connect() {
        if (host.isBlank()) {
            _mqttState.value = _mqttState.value.copy(
                status = MqttStatus.Disconnected,
                errorMessage = "MQTT Host not configured. Set broker host in settings.",
            )
            return
        }

        _mqttState.value = _mqttState.value.copy(
            status = MqttStatus.Connecting,
            brokerHost = host,
            activeTopic = getTopicForRoom(roomId),
            errorMessage = null,
        )

        try {
            client?.disconnect()

            val builder = MqttClient.builder()
                .useMqttVersion3()
                .identifier("rewatt-$nodeId")
                .serverHost(host)
                .serverPort(port)

            // Enable SSL for secure HiveMQ Cloud endpoint
            if (port == 8883 || host.contains("hivemq", ignoreCase = true)) {
                builder.sslWithDefaultConfig()
            }

            val clientInstance = builder.buildAsync()
            this.client = clientInstance

            val connectBuilder = clientInstance.connectWith()
            if (username.isNotBlank()) {
                connectBuilder.simpleAuth()
                    .username(username)
                    .password(password.toByteArray(StandardCharsets.UTF_8))
                    .applySimpleAuth()
            }

            connectBuilder.send()
                .whenComplete { ack, throwable ->
                    if (throwable != null) {
                        Log.e("MqttSyncRepository", "MQTT Connect Failed: ${throwable.message}")
                        _mqttState.value = _mqttState.value.copy(
                            status = MqttStatus.Error,
                            errorMessage = throwable.message ?: "Connection failed",
                        )
                    } else {
                        Log.i("MqttSyncRepository", "MQTT Connected successfully to HiveMQ Cloud!")
                        _mqttState.value = _mqttState.value.copy(
                            status = MqttStatus.Connected,
                            brokerHost = host,
                            activeTopic = getTopicForRoom(roomId),
                            errorMessage = null,
                        )
                    }
                }
        } catch (e: Exception) {
            Log.e("MqttSyncRepository", "Error setting up MQTT client", e)
            _mqttState.value = _mqttState.value.copy(
                status = MqttStatus.Error,
                errorMessage = e.message ?: "Configuration error",
            )
        }
    }

    fun publishTelemetryJson(jsonPayload: String, targetRoomId: String = roomId) {
        val topic = getTopicForRoom(targetRoomId)
        val activeClient = client

        if (activeClient == null || _mqttState.value.status != MqttStatus.Connected) {
            // Attempt reconnect if disconnected
            if (host.isNotBlank() && _mqttState.value.status != MqttStatus.Connecting) {
                connect()
            }
            return
        }

        activeClient.publishWith()
            .topic(topic)
            .payload(jsonPayload.toByteArray(StandardCharsets.UTF_8))
            .send()
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.w("MqttSyncRepository", "Failed to publish telemetry to $topic: ${throwable.message}")
                } else {
                    messagesSent++
                    _mqttState.value = _mqttState.value.copy(
                        messagesSentCount = messagesSent,
                        lastPublishedAtMillis = System.currentTimeMillis(),
                        activeTopic = topic,
                    )
                }
            }
    }

    fun disconnect() {
        client?.disconnect()
        client = null
        _mqttState.value = _mqttState.value.copy(status = MqttStatus.Disconnected)
    }

    private fun getTopicForRoom(room: String): String = "rewatt/sensors/${room.trim()}/telemetry"
}
