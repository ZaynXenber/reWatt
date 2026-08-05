package com.rewatt.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rewatt.app.perception.CaptureStartSource
import com.rewatt.app.perception.PerceptionViewModel
import com.rewatt.app.perception.PerceptionViewModelFactory
import com.rewatt.app.ui.SetupGuideDialog
import com.rewatt.app.ui.sensor.SensorScreen
import com.rewatt.app.ui.sensor.SensorViewModel
import com.rewatt.app.ui.sensor.SensorViewModelFactory
import com.rewatt.app.ui.theme.ReWattTheme
import com.rewatt.app.usb.UsbSentinelService

class MainActivity : AppCompatActivity() {
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            startUsbService()
        }
    private val perceptionPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val cameraGranted = permissions[Manifest.permission.CAMERA] == true ||
                hasPermission(Manifest.permission.CAMERA)
            perceptionViewModel.setPermissionState(cameraGranted)
            if (cameraGranted) {
                perceptionViewModel.startPerception(pendingCaptureSource ?: CaptureStartSource.Manual)
            }
            pendingCaptureSource = null
        }

    private var pendingCaptureSource: CaptureStartSource? = null

    private val sensorViewModel: SensorViewModel by viewModels {
        val graph = (application as ReWattApplication).appGraph
        SensorViewModelFactory(
            appContext = applicationContext,
            telemetryRepository = graph.telemetryRepository,
            mqttRepository = graph.mqttSyncRepository,
            perceptionStateHolder = graph.perceptionStateHolder,
        )
    }

    private val perceptionViewModel: PerceptionViewModel by viewModels {
        PerceptionViewModelFactory(
            appContext = applicationContext,
            stateHolder = (application as ReWattApplication).appGraph.perceptionStateHolder,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()
        perceptionViewModel.setPermissionState(
            cameraGranted = hasPermission(Manifest.permission.CAMERA),
        )

        setContent {
            ReWattTheme {
                val uiState by sensorViewModel.uiState.collectAsStateWithLifecycle()
                var showSetupGuide by remember { mutableStateOf(false) }

                SensorScreen(
                    uiState = uiState,
                    onScanUsb = ::startUsbService,
                    onStartPerception = ::requestPerceptionPermissionsThenStart,
                    onStopPerception = perceptionViewModel::stopPerception,
                    onDebugCapture = perceptionViewModel::captureNow,
                    onUpdateMqttConfig = sensorViewModel::updateMqttConfig,
                    onOpenSetupGuide = { showSetupGuide = true },
                )

                if (showSetupGuide) {
                    SetupGuideDialog(onDismiss = { name, capacity ->
                        showSetupGuide = false
                        if (name != null) {
                            sensorViewModel.updateMqttConfig(
                                sensorViewModel.mqttRepository.host,
                                sensorViewModel.mqttRepository.port,
                                sensorViewModel.mqttRepository.username,
                                sensorViewModel.mqttRepository.password,
                                name,
                            )
                        }
                    })
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startUsbService()
        }
    }

    private fun startUsbService() {
        ContextCompat.startForegroundService(
            this,
            UsbSentinelService.createStartIntent(this),
        )
    }

    private fun requestPerceptionPermissionsThenStart(source: CaptureStartSource) {
        pendingCaptureSource = source
        val cameraGranted = hasPermission(Manifest.permission.CAMERA)
        perceptionViewModel.setPermissionState(cameraGranted)

        if (cameraGranted) {
            pendingCaptureSource = null
            perceptionViewModel.startPerception(source)
            return
        }

        perceptionPermissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}
