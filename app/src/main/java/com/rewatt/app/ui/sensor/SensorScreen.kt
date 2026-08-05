package com.rewatt.app.ui.sensor

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rewatt.app.data.MqttStatus
import com.rewatt.app.data.SensorDashboardState
import com.rewatt.app.perception.CaptureStartSource
import com.rewatt.app.perception.PerceptionUiState
import com.rewatt.app.usb.UsbConnectionStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorScreen(
    uiState: SensorNodeUiState,
    onScanUsb: () -> Unit,
    onStartPerception: (CaptureStartSource) -> Unit,
    onStopPerception: () -> Unit,
    onDebugCapture: () -> Unit,
    onUpdateMqttConfig: (String, Int, String, String, String) -> Unit,
    onOpenSetupGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showConfigDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Sensors,
                            contentDescription = null,
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "REWATT SENSOR NODE",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                            Text(
                                text = "Room: ${uiState.roomId} · ${uiState.nodeId}",
                                fontSize = 12.sp,
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSetupGuide) {
                        Icon(Icons.Outlined.HelpOutline, contentDescription = "Setup Guide")
                    }
                    IconButton(onClick = { showConfigDialog = true }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "MQTT Config")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Section 1: Live MQTT Stream Status Banner ──
            MqttStatusBanner(
                status = uiState.mqttState.status,
                brokerHost = uiState.mqttState.brokerHost,
                topic = uiState.mqttState.activeTopic,
                sentCount = uiState.mqttState.messagesSentCount,
                lastSentAt = uiState.mqttState.lastPublishedAtMillis,
                errorMessage = uiState.mqttState.errorMessage,
                onConfigureClick = { showConfigDialog = true },
            )

            // ── Section 2: Hardware Sensor Diagnostics (USB / Radar / SHT30) ──
            UsbDiagnosticCard(
                sensorState = uiState.sensorState,
                onScanUsb = onScanUsb,
            )

            // ── Section 3: Vision Perception & YOLO Diagnostics ──
            PerceptionDiagnosticCard(
                perceptionState = uiState.perceptionState,
                onStartPerception = onStartPerception,
                onStopPerception = onStopPerception,
                onDebugCapture = onDebugCapture,
            )
        }
    }

    if (showConfigDialog) {
        MqttConfigDialog(
            currentHost = uiState.mqttState.brokerHost,
            currentPort = 8883,
            currentRoomId = uiState.roomId,
            onDismiss = { showConfigDialog = false },
            onSave = { host, port, user, pass, room ->
                onUpdateMqttConfig(host, port, user, pass, room)
                showConfigDialog = false
            },
        )
    }
}

@Composable
private fun MqttStatusBanner(
    status: MqttStatus,
    brokerHost: String,
    topic: String,
    sentCount: Long,
    lastSentAt: Long?,
    errorMessage: String?,
    onConfigureClick: () -> Unit,
) {
    val (bg, accent, text) = when (status) {
        MqttStatus.Connected -> Triple(Color(0x1A00E676), Color(0xFF00E676), "STREAMING LIVE (1Hz)")
        MqttStatus.Connecting -> Triple(Color(0x1AFFEA00), Color(0xFFFFEA00), "CONNECTING TO BROKER...")
        MqttStatus.Error -> Triple(Color(0x1AFF1744), Color(0xFFFF1744), "STREAM ERROR")
        MqttStatus.Disconnected -> Triple(Color(0x1A9E9E9E), Color(0xFF9E9E9E), "DISCONNECTED")
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.5f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = text,
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        fontSize = 14.sp,
                    )
                }
                TextButton(onClick = onConfigureClick) {
                    Text("Edit Config", fontSize = 12.sp, color = accent)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "Broker: ${if (brokerHost.isNotBlank()) brokerHost else "Not configured"}",
                fontSize = 12.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Topic: ${if (topic.isNotBlank()) topic else "--"}",
                fontSize = 12.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Packets Sent: $sentCount",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (lastSentAt != null) {
                    val secondsAgo = (System.currentTimeMillis() - lastSentAt) / 1000
                    Text(
                        text = "Last payload: ${secondsAgo}s ago",
                        fontSize = 12.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (errorMessage != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Error: $errorMessage",
                    fontSize = 12.sp,
                    color = Color(0xFFFF1744),
                )
            }
        }
    }
}

@Composable
private fun UsbDiagnosticCard(
    sensorState: SensorDashboardState,
    onScanUsb: () -> Unit,
) {
    val telemetry = sensorState.latestTelemetry
    val usbStatus = sensorState.connectionStatus

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Usb,
                        contentDescription = null,
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("USB Sensor Payload (ESP32-C6)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
                IconButton(onClick = onScanUsb) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Scan USB", modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = "USB Connection: ${usbStatus.name}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (usbStatus == UsbConnectionStatus.Connected) Color(0xFF00E676) else Color(0xFFFFEA00),
            )

            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Radar Presence", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = if (telemetry?.presence == true) "OCCUPIED (${telemetry.radarState})" else "EMPTY (0)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
                Column {
                    Text("Target Dist / Energy", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${telemetry?.movingDistanceCm ?: 0} cm / ${telemetry?.movingEnergy ?: 0} E",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Temperature", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${telemetry?.temperatureC ?: "--"} °C",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
                Column {
                    Text("Humidity", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${telemetry?.humidityPercent ?: "--"} % RH",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun PerceptionDiagnosticCard(
    perceptionState: PerceptionUiState,
    onStartPerception: (CaptureStartSource) -> Unit,
    onStopPerception: () -> Unit,
    onDebugCapture: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CameraAlt,
                        contentDescription = null,
                        tint = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Vision & YOLO Perception", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Button(
                    onClick = {
                        if (perceptionState.isRunning) onStopPerception()
                        else onStartPerception(CaptureStartSource.Manual)
                    },
                ) {
                    Text(if (perceptionState.isRunning) "Stop" else "Start Capture", fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("Headcount", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "${perceptionState.personCount} People",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                    )
                }
                Column {
                    Text("Light State (EV)", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    val evText = perceptionState.exposureEv?.let { "EV ${"%.1f".format(it)}" } ?: ""
                    Text(
                        text = "${if (perceptionState.lightsOn == true) "Lights ON" else "Lights OFF"} $evText",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            perceptionState.latestFrame?.let { bitmap ->
                Text("Latest Capture Preview:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "YOLO Camera Capture Preview",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }

            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onDebugCapture,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Test Debug Frame Capture Now")
            }
        }
    }
}

@Composable
private fun MqttConfigDialog(
    currentHost: String,
    currentPort: Int,
    currentRoomId: String,
    onDismiss: () -> Unit,
    onSave: (String, Int, String, String, String) -> Unit,
) {
    var host by remember { mutableStateOf(currentHost) }
    var portText by remember { mutableStateOf(currentPort.toString()) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var roomId by remember { mutableStateOf(currentRoomId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("HiveMQ Cloud MQTT Setup", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Broker Host (e.g. xxx.s1.eu.hivemq.cloud)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = roomId,
                    onValueChange = { roomId = it },
                    label = { Text("Room ID (e.g. DK1, DK2, MM8)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("MQTT Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("MQTT Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val p = portText.toIntOrNull() ?: 8883
                    onSave(host, p, user, pass, roomId)
                },
            ) {
                Text("Save & Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
