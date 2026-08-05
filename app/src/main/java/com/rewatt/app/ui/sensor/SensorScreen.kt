package com.rewatt.app.ui.sensor

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rewatt.app.data.MqttStatus
import com.rewatt.app.data.SensorDashboardState
import com.rewatt.app.perception.CaptureStartSource
import com.rewatt.app.perception.PerceptionUiState
import com.rewatt.app.usb.UsbConnectionStatus
import kotlin.math.cos
import kotlin.math.sin

private enum class SensorTab(val label: String, val icon: ImageVector) {
    Main("Main", Icons.Outlined.Speed),
    Debug("Debug", Icons.Outlined.BugReport),
    Settings("Settings", Icons.Outlined.Settings),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensorScreen(
    uiState: SensorNodeUiState,
    onScanUsb: () -> Unit,
    onStartPerception: (CaptureStartSource) -> Unit,
    onStopPerception: () -> Unit,
    onDebugCapture: () -> Unit,
    onSendPhotoMqtt: () -> Unit,
    onUpdateMqttConfig: (String, Int, String, String, String) -> Unit,
    onOpenSetupGuide: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(SensorTab.Main) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Sensors,
                                contentDescription = null,
                                tint = Color(0xFF00E676),
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text("reWatt Sensor", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Room: ${uiState.roomId}", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        // Minimal Status Pill on Top Header
                        val (pillBg, pillColor, pillText) = when (uiState.mqttState.status) {
                            MqttStatus.Connected -> Triple(Color(0x2200E676), Color(0xFF00E676), "LIVE 1Hz")
                            MqttStatus.Connecting -> Triple(Color(0x22FFEA00), Color(0xFFFFEA00), "CONNECTING")
                            MqttStatus.Error -> Triple(Color(0x22FF1744), Color(0xFFFF1744), "ERROR")
                            MqttStatus.Disconnected -> Triple(Color(0x229E9E9E), Color(0xFF9E9E9E), "OFFLINE")
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(pillBg)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(pillText, color = pillColor, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                ),
            )
        },
        bottomBar = {
            NavigationBar {
                SensorTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
                .padding(paddingValues),
        ) {
            when (selectedTab) {
                SensorTab.Main -> MainTabContent(uiState = uiState)
                SensorTab.Debug -> DebugTabContent(
                    uiState = uiState,
                    onScanUsb = onScanUsb,
                    onStartPerception = onStartPerception,
                    onStopPerception = onStopPerception,
                    onDebugCapture = onDebugCapture,
                    onSendPhotoMqtt = onSendPhotoMqtt,
                )
                SensorTab.Settings -> SettingsTabContent(
                    uiState = uiState,
                    onUpdateMqttConfig = onUpdateMqttConfig,
                    onOpenSetupGuide = onOpenSetupGuide,
                )
            }
        }
    }
}

// ── TAB 1: MAIN (Analog Visual Gauges & Clean Telemetry) ──────────────────────

@Composable
private fun MainTabContent(uiState: SensorNodeUiState) {
    val telemetry = uiState.sensorState.latestTelemetry
    val perception = uiState.perceptionState
    val tempC = telemetry?.temperatureC ?: 0.0f
    val humidity = telemetry?.humidityPercent ?: 0.0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Room & Device Banner
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "Room ${uiState.roomId}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Node ID: ${uiState.nodeId}",
                    fontSize = 12.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.BatteryFull, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("100%", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        // Row 1: Analog Temperature & Humidity Gauges
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("TEMPERATURE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    AnalogGaugeDial(
                        value = tempC.coerceIn(0f, 50f),
                        minValue = 0f,
                        maxValue = 50f,
                        unit = "°C",
                        accentColor = Color(0xFFFF7043),
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("HUMIDITY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    AnalogGaugeDial(
                        value = humidity.coerceIn(0f, 100f),
                        minValue = 0f,
                        maxValue = 100f,
                        unit = "% RH",
                        accentColor = Color(0xFF29B6F6),
                    )
                }
            }
        }

        // Row 2: Occupancy & Light Visual Status Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Occupancy Visual Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("OCCUPANCY", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${perception.personCount}",
                        fontSize = 36.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676),
                    )
                    Text("People Detected", fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    val occupied = telemetry?.presence == true
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (occupied) Color(0x3300E676) else Color(0x339E9E9E))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = if (occupied) "Radar: Occupied" else "Radar: Empty",
                            fontSize = 11.sp,
                            color = if (occupied) Color(0xFF00E676) else Color(0xFF9E9E9E),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Light Status Visual Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("LIGHT STATE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    val lightsOn = perception.lightsOn == true
                    Icon(
                        imageVector = if (lightsOn) Icons.Outlined.WbSunny else Icons.Outlined.Lightbulb,
                        contentDescription = null,
                        tint = if (lightsOn) Color(0xFFFFCA28) else Color(0xFF757575),
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (lightsOn) "Lights ON" else "Lights OFF",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    val evText = perception.exposureEv?.let { "EV ${"%.1f".format(it)}" } ?: "--"
                    Text(evText, fontSize = 12.sp, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Custom Analog Visual Dial Gauge drawn on Compose Canvas. */
@Composable
private fun AnalogGaugeDial(
    value: Float,
    minValue: Float,
    maxValue: Float,
    unit: String,
    accentColor: Color,
) {
    Box(
        modifier = Modifier.size(110.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 10.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
            val size2 = Size(diameter, diameter)

            // Background arc (240 degree sweep)
            drawArc(
                color = Color(0x33FFFFFF),
                startAngle = 150f,
                sweepAngle = 240f,
                useCenter = false,
                topLeft = topLeft,
                size = size2,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            // Active value sweep arc
            val fraction = ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)
            val sweep = fraction * 240f
            drawArc(
                color = accentColor,
                startAngle = 150f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = size2,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%.1f".format(value),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = unit,
                fontSize = 10.sp,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── TAB 2: DEBUG (Hardware Inspection & Camera Photo Inspector) ───────────────

@Composable
private fun DebugTabContent(
    uiState: SensorNodeUiState,
    onScanUsb: () -> Unit,
    onStartPerception: (CaptureStartSource) -> Unit,
    onStopPerception: () -> Unit,
    onDebugCapture: () -> Unit,
    onSendPhotoMqtt: () -> Unit,
) {
    val telemetry = uiState.sensorState.latestTelemetry
    val usbStatus = uiState.sensorState.connectionStatus
    val perception = uiState.perceptionState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // USB CDC & Radar Inspection Card
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
                    Text("USB & Radar Hardware Diagnostic", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    IconButton(onClick = onScanUsb) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Rescan USB")
                    }
                }

                Spacer(Modifier.height(6.dp))
                Text("USB Status: ${usbStatus.name}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                Text("Radar Raw State: ${telemetry?.radarState ?: 0}", fontSize = 13.sp)
                Text("Target Distance: ${telemetry?.movingDistanceCm ?: 0} cm", fontSize = 13.sp)
                Text("Target Energy: ${telemetry?.movingEnergy ?: 0}", fontSize = 13.sp)
            }
        }

        // YOLO Camera & Photo Inspection Card
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
                    Text("YOLO Camera Inspection", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Button(
                        onClick = {
                            if (perception.isRunning) onStopPerception()
                            else onStartPerception(CaptureStartSource.Manual)
                        },
                    ) {
                        Text(if (perception.isRunning) "Stop" else "Start Perception", fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(10.dp))
                perception.latestFrame?.let { bitmap ->
                    Text("Live Camera Frame:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Camera Frame",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(4f / 3f)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onDebugCapture,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Capture Frame", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onSendPhotoMqtt,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Send Photo MQTT", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ── TAB 3: SETTINGS (MQTT Setup & Installation Guide) ─────────────────────────

@Composable
private fun SettingsTabContent(
    uiState: SensorNodeUiState,
    onUpdateMqttConfig: (String, Int, String, String, String) -> Unit,
    onOpenSetupGuide: () -> Unit,
) {
    val context = LocalContext.current
    var host by remember { mutableStateOf(uiState.mqttState.brokerHost) }
    var portText by remember { mutableStateOf("8883") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var roomId by remember { mutableStateOf(uiState.roomId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("HiveMQ Cloud MQTT Setup", fontWeight = FontWeight.Bold, fontSize = 16.sp)

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

                Button(
                    onClick = {
                        val p = portText.toIntOrNull() ?: 8883
                        onUpdateMqttConfig(host, p, user, pass, roomId)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save & Connect Broker")
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Hardware Installation & System Settings", fontWeight = FontWeight.Bold, fontSize = 16.sp)

                OutlinedButton(
                    onClick = onOpenSetupGuide,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Open Hardware Mounting Guide")
                }

                OutlinedButton(
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Configure Battery Saver Optimization")
                }
            }
        }
    }
}
