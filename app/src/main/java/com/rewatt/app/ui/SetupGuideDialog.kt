package com.rewatt.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rewatt.app.R

private const val TOTAL_STEPS = 6

@Composable
fun SetupGuideDialog(onDismiss: (spaceName: String?, capacity: Int?) -> Unit) {
    var step by remember { mutableStateOf(0) }
    var spaceName by remember { mutableStateOf("") }
    var capacityText by remember { mutableStateOf("") }
    var isProcessingSwipe by remember { mutableStateOf(false) }
    val capacity = capacityText.toIntOrNull()

    Dialog(
        onDismissRequest = { onDismiss(null, null) },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF9FAFB)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                // Header (Google Stitch Style)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.ic_app_logo),
                            contentDescription = "ReWatt Logo",
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "ReWatt Setup",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color(0xFF09090B),
                        )
                    }

                    TextButton(onClick = { onDismiss(null, null) }) {
                        Text("Skip", fontWeight = FontWeight.Bold, color = Color(0xFF6B7280), fontSize = 14.sp)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Progress Step Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(TOTAL_STEPS) { index ->
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .weight(1f)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (index <= step) Color(0xFF09090B) else Color(0xFFE2E8F0),
                                ),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Step content (scrollable with swipe navigation)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                if (!isProcessingSwipe) {
                                    when {
                                        dragAmount > 100 && step > 0 -> {
                                            step--
                                            isProcessingSwipe = true
                                        }
                                        dragAmount < -100 && step < TOTAL_STEPS - 1 -> {
                                            step++
                                            isProcessingSwipe = true
                                        }
                                    }
                                }
                            }
                        },
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (step) {
                        0 -> PermissionsStep()
                        1 -> BatteryStep()
                        2 -> MountStep()
                        3 -> VerifyViewStep()
                        4 -> NameYourSpaceStep(
                            spaceName = spaceName,
                            onSpaceNameChange = { spaceName = it },
                            capacityText = capacityText,
                            onCapacityChange = { capacityText = it },
                        )
                        5 -> DoneStep()
                    }
                }

                LaunchedEffect(step) {
                    isProcessingSwipe = false
                }

                Spacer(Modifier.height(12.dp))

                // Stitch Navigation Buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (step > 0) {
                        OutlinedButton(
                            onClick = { step-- },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF09090B)),
                        ) {
                            Text("Back", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }

                    Button(
                        onClick = {
                            if (step < TOTAL_STEPS - 1) step++ else onDismiss(spaceName.ifBlank { null }, capacity)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF09090B), contentColor = Color.White),
                        enabled = !(step == 4 && spaceName.isBlank()),
                    ) {
                        Text(
                            text = if (step == TOTAL_STEPS - 1) "Done" else "Next",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }
    }
}

private const val REQUEST_IDLE = 0
private const val REQUEST_CAMERA = 1
private const val REQUEST_LOCATION = 2

@Composable
private fun PermissionsStep() {
    val context = LocalContext.current
    var cameraGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var locationGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    var requestStep by remember { mutableStateOf(REQUEST_IDLE) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        cameraGranted = granted
        requestStep = REQUEST_LOCATION
    }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        locationGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        requestStep = REQUEST_IDLE
    }

    LaunchedEffect(requestStep) {
        when (requestStep) {
            REQUEST_CAMERA ->
                if (cameraGranted) requestStep = REQUEST_LOCATION
                else cameraLauncher.launch(Manifest.permission.CAMERA)
            REQUEST_LOCATION ->
                if (locationGranted) requestStep = REQUEST_IDLE
                else locationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            else -> Unit
        }
    }

    StepHeader(
        title = stringResource(R.string.setup_s1_title),
        body = stringResource(R.string.setup_s1_body),
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFECEEEF)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PermissionRow(label = stringResource(R.string.setup_perm_camera), granted = cameraGranted)
            PermissionRow(label = stringResource(R.string.setup_perm_location), granted = locationGranted)
        }
    }

    if (!cameraGranted || !locationGranted) {
        Button(
            onClick = { requestStep = REQUEST_CAMERA },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF09090B), contentColor = Color.White),
        ) {
            Text(stringResource(R.string.setup_allow_permissions), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PermissionRow(label: String, granted: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (granted) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.Check else Icons.Default.Close,
                contentDescription = null,
                tint = if (granted) Color(0xFF15803D) else Color(0xFFB91C1C),
                modifier = Modifier.size(14.dp),
            )
        }
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF09090B), modifier = Modifier.weight(1f))
        Text(
            text = if (granted) "Allowed" else "Not Allowed",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (granted) Color(0xFF15803D) else Color(0xFFB91C1C),
        )
    }
}

@Composable
private fun BatteryStep() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    fun saverIsOff(): Boolean = !powerManager.isPowerSaveMode

    var saverOff by remember { mutableStateOf(saverIsOff()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) saverOff = saverIsOff()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    StepHeader(
        title = stringResource(R.string.setup_s2_title),
        body = stringResource(R.string.setup_s2_body),
    )

    Card(
        onClick = { saverOff = !saverOff },
        colors = CardDefaults.cardColors(
            containerColor = if (saverOff) Color(0xFFECEEEF) else Color(0xFFFEF2F2),
        ),
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (saverOff) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (saverOff) Icons.Default.Check else Icons.Default.Close,
                    contentDescription = null,
                    tint = if (saverOff) Color(0xFF15803D) else Color(0xFFB91C1C),
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = if (saverOff) stringResource(R.string.setup_batt_off) else stringResource(R.string.setup_batt_on),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (saverOff) Color(0xFF15803D) else Color(0xFFB91C1C),
            )
        }
    }

    if (!saverOff) {
        Button(
            onClick = {
                val saverIntent = Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (saverIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(saverIntent)
                } else {
                    context.startActivity(
                        Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF09090B), contentColor = Color.White),
        ) { Text(stringResource(R.string.setup_allow_always_on), fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun MountStep() {
    StepHeader(
        title = stringResource(R.string.setup_s3_title),
        body = stringResource(R.string.setup_s3_body),
    )

    MountPhoto()

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFECEEEF)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BulletTip(stringResource(R.string.setup_s3_tip1))
            BulletTip(stringResource(R.string.setup_s3_tip2))
            BulletTip(stringResource(R.string.setup_s3_tip3))
            BulletTip(stringResource(R.string.setup_s3_tip4))
        }
    }
}

@Composable
private fun VerifyViewStep() {
    StepHeader(title = stringResource(R.string.setup_s4_title), body = null)

    CameraPreviewBox()

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFECEEEF)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BulletTip(stringResource(R.string.setup_s4_tip1))
            BulletTip(stringResource(R.string.setup_s4_tip2))
            BulletTip(stringResource(R.string.setup_s4_tip3))
        }
    }
}

@Composable
private fun NameYourSpaceStep(
    spaceName: String,
    onSpaceNameChange: (String) -> Unit,
    capacityText: String,
    onCapacityChange: (String) -> Unit,
) {
    StepHeader(
        title = stringResource(R.string.setup_s5_title),
        body = stringResource(R.string.setup_s5_body),
    )

    OutlinedTextField(
        value = spaceName,
        onValueChange = { if (it.length <= 40) onSpaceNameChange(it) },
        label = { Text(stringResource(R.string.setup_space_name)) },
        placeholder = { Text(stringResource(R.string.setup_space_name_hint)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
        ),
    )

    OutlinedTextField(
        value = capacityText,
        onValueChange = { input -> onCapacityChange(input.filter { it.isDigit() }.take(5)) },
        label = { Text(stringResource(R.string.setup_capacity)) },
        placeholder = { Text(stringResource(R.string.setup_capacity_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
        ),
    )
}

@Composable
private fun DoneStep() {
    StepHeader(
        title = stringResource(R.string.setup_s6_title),
        body = stringResource(R.string.setup_s6_body),
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color(0xFF15803D),
                modifier = Modifier.size(48.dp),
            )
            Text(
                stringResource(R.string.setup_complete),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color(0xFF15803D),
            )
            Text(
                stringResource(R.string.setup_complete_desc),
                fontSize = 13.sp,
                color = Color(0xFF166534),
            )
        }
    }
}

@Composable
private fun StepHeader(title: String, body: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color(0xFF09090B))
        if (!body.isNullOrBlank()) {
            Text(
                body,
                fontSize = 14.sp,
                color = Color(0xFF6B7280),
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun BulletTip(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text("•", fontSize = 14.sp, color = Color(0xFF09090B), fontWeight = FontWeight.Bold)
        Text(text, fontSize = 13.sp, color = Color(0xFF374151))
    }
}

@Composable
private fun MountPhoto() {
    val context = LocalContext.current
    val imageBitmap = produceState<androidx.compose.ui.graphics.ImageBitmap?>(initialValue = null) {
        try {
            val assetManager = context.assets
            val file = java.io.File(context.cacheDir, "mount_photo.heic")
            assetManager.open("mount_photo.heic").use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val source = ImageDecoder.createSource(file)
            val bitmap = ImageDecoder.decodeBitmap(source)
            value = bitmap.asImageBitmap()
        } catch (e: Exception) {
            value = null
        }
    }
    
    if (imageBitmap.value != null) {
        Image(
            bitmap = imageBitmap.value!!,
            contentDescription = "Mount photo",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
                .clip(RoundedCornerShape(20.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFFECEEEF)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.CameraAlt, contentDescription = null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
private fun CameraPreviewBox() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        hasCameraPermission = isGranted
    }
    
    LaunchedEffect(Unit) {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        
        if (hasPermission) {
            hasCameraPermission = true
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFECEEEF)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            if (hasCameraPermission) {
                if (cameraError != null) {
                    Text("Camera Error: $cameraError", fontSize = 13.sp, color = Color(0xFFEF4444))
                } else {
                    AndroidView(
                        factory = { ctx ->
                            PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                                post {
                                    try {
                                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                        cameraProviderFuture.addListener({
                                            try {
                                                val cameraProvider = cameraProviderFuture.get()
                                                val availableCameras = cameraProvider.availableCameraInfos
                                                if (availableCameras.isEmpty()) {
                                                    cameraError = "No cameras available"
                                                    return@addListener
                                                }
                                                val preview = Preview.Builder().build().also {
                                                    it.surfaceProvider = surfaceProvider
                                                }
                                                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                                                cameraProvider.unbindAll()
                                                cameraProvider.bindToLifecycle(
                                                    lifecycleOwner,
                                                    cameraSelector,
                                                    preview,
                                                )
                                            } catch (e: Exception) {
                                                cameraError = e.message ?: "Camera binding failed"
                                            }
                                        }, ContextCompat.getMainExecutor(ctx))
                                    } catch (e: Exception) {
                                        cameraError = e.message ?: "Failed to get camera provider"
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 240.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black),
                    )
                }
            } else {
                Text("Requesting camera access...", fontSize = 13.sp, color = Color(0xFF6B7280))
            }
        }
    }
}
