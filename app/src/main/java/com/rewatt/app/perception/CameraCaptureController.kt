package com.rewatt.app.perception

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.view.OrientationEventListener
import android.view.Surface
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import java.io.Closeable
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Manages on-demand camera frame capture. Unlocks captureInFlight immediately upon frame decoding (~30ms)
 * and runs slow YOLO model detection asynchronously so repeated Test Captures are ultra-fast and never blocked.
 */
class CameraCaptureController(
    private val context: Context,
    private val yoloDetectorProvider: () -> YoloDetector?,
    private val onFramePreview: (Bitmap, ExposureReading?, String) -> Unit,
    private val onFrameAnalyzed: (Bitmap?, List<ObjectDetection>, String, ExposureReading?, String) -> Unit,
    private val onError: (String) -> Unit,
) : Closeable {
    private val analysisExecutor: ExecutorService = Executors.newFixedThreadPool(2)
    private var lifecycleOwner: LifecycleOwner? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var activeImageCapture: ImageCapture? = null

    @Volatile
    private var closed = false

    @Volatile
    private var captureInFlight = false

    @Volatile
    private var deviceRotation: Int = Surface.ROTATION_0

    private val orientationListener = object : OrientationEventListener(context) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            deviceRotation = when (orientation) {
                in 45 until 135 -> Surface.ROTATION_270
                in 135 until 225 -> Surface.ROTATION_180
                in 225 until 315 -> Surface.ROTATION_90
                else -> Surface.ROTATION_0
            }
        }
    }

    fun attach(owner: LifecycleOwner) {
        lifecycleOwner = owner
        ContextCompat.getMainExecutor(context).execute {
            runCatching { if (orientationListener.canDetectOrientation()) orientationListener.enable() }
        }
    }

    /**
     * Instantly capture a frame. Reuses warm bound ImageCapture pipeline for ~30ms repeated captures.
     */
    fun capture() {
        if (closed || captureInFlight) return
        val owner = lifecycleOwner ?: return
        captureInFlight = true

        val provider = cameraProvider
        val currentCapture = activeImageCapture

        if (provider != null && currentCapture != null) {
            // Fast path: Camera pipeline is already warm and bound! Capture immediately (~30ms).
            takePictureInternal(currentCapture)
        } else {
            // Cold start path: Bind camera once to lifecycle.
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener(
                {
                    if (closed) {
                        captureInFlight = false
                        return@addListener
                    }
                    runCatching {
                        val p = providerFuture.get()
                        cameraProvider = p
                        val newCapture = buildImageCapture()
                        p.unbindAll()
                        p.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, newCapture)
                        activeImageCapture = newCapture
                        takePictureInternal(newCapture)
                    }.onFailure { error ->
                        captureInFlight = false
                        onError("Camera failed to start: ${error.message ?: error::class.java.simpleName}")
                    }
                },
                ContextCompat.getMainExecutor(context),
            )
        }
    }

    fun captureNow() = capture()

    private fun buildImageCapture(): ImageCapture =
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build(),
            )
            .build()
            .also {
                it.flashMode = ImageCapture.FLASH_MODE_OFF
                it.targetRotation = deviceRotation
            }

    private fun takePictureInternal(capture: ImageCapture) {
        val photoFile = runCatching {
            File.createTempFile("rewatt-exposure-", ".jpg", context.cacheDir)
        }.getOrElse { error ->
            captureInFlight = false
            notifyCaptureFailure("Camera capture failed: ${error.message ?: error::class.java.simpleName}")
            return
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        runCatching {
            capture.takePicture(
                outputOptions,
                analysisExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        try {
                            processSavedImageAsync(photoFile)
                        } catch (error: Throwable) {
                            captureInFlight = false
                            photoFile.delete()
                            notifyCaptureFailure(
                                "Camera analysis failed: ${error.message ?: error::class.java.simpleName}",
                            )
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        photoFile.delete()
                        captureInFlight = false
                        notifyCaptureFailure(
                            "Camera image capture failed: ${exception.message ?: exception::class.java.simpleName}",
                        )
                    }
                },
            )
        }.onFailure { error ->
            photoFile.delete()
            captureInFlight = false
            notifyCaptureFailure("Camera image capture failed: ${error.message ?: error::class.java.simpleName}")
        }
    }

    override fun close() {
        closed = true
        captureInFlight = false
        lifecycleOwner = null
        runCatching { orientationListener.disable() }
        ContextCompat.getMainExecutor(context).execute {
            runCatching {
                cameraProvider?.unbindAll()
                cameraProvider = null
                activeImageCapture = null
            }
        }
        analysisExecutor.shutdownNow()
    }

    private fun processSavedImageAsync(photoFile: File) {
        if (closed) {
            photoFile.delete()
            captureInFlight = false
            return
        }

        val exposureReading = runCatching {
            ExposureValueCalculator.fromExif(photoFile.absolutePath)
        }.getOrNull()

        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
            ?.rotateFromExif(photoFile.absolutePath)

        photoFile.delete()

        if (bitmap == null) {
            captureInFlight = false
            onFrameAnalyzed(
                null,
                emptyList(),
                "Camera frame could not be decoded.",
                exposureReading,
                exposureReading.toExposureMessage(),
            )
            return
        }

        // 1. Publish preview frame to UI immediately (~30ms)!
        onFramePreview(bitmap, exposureReading, exposureReading.toExposureMessage())

        // 2. UNLOCK captureInFlight IMMEDIATELY so user can tap Test Capture again right away!
        captureInFlight = false

        // 3. Run slow YOLO detection asynchronously in background without blocking capture lock!
        analysisExecutor.execute {
            if (closed) return@execute
            val yoloDetector = yoloDetectorProvider()
            val detections = yoloDetector?.detect(bitmap).orEmpty()
            val personCount = detections.count { it.isPerson }
            val message = if (yoloDetector == null) {
                "Captured frame; YOLO is still loading."
            } else if (detections.isEmpty()) {
                "No people detected above the confidence threshold."
            } else {
                "People detected: $personCount."
            }

            onFrameAnalyzed(
                bitmap,
                detections,
                message,
                exposureReading,
                exposureReading.toExposureMessage(),
            )
        }
    }

    private fun notifyCaptureFailure(message: String) {
        if (!closed) {
            onFrameAnalyzed(null, emptyList(), message, null, "Exposure data unavailable.")
        }
    }
}

private fun ExposureReading?.toExposureMessage(): String =
    this?.let {
        "EV %.1f; lights %s.".format(it.ev, if (it.lightsOn) "on" else "off")
    } ?: "Exposure data unavailable."

private fun Bitmap.rotateFromExif(imagePath: String): Bitmap {
    val orientation = runCatching {
        ExifInterface(imagePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }
    return rotate(degrees)
}

private fun Bitmap.rotate(degrees: Int): Bitmap {
    if (degrees == 0) return this
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
