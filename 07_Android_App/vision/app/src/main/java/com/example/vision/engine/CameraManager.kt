package com.example.vision.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import android.view.OrientationEventListener
import android.view.Surface
import android.util.Size
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraManager responsible for CameraX lifecycle binding, frame acquisition,
 * digital zoom control for distant road sign capture, orientation rotation tracking, and COMPATIBLE mode.
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val onFrameAnalyzed: (Bitmap, Int) -> Unit,
    private val onError: (String) -> Unit
) {

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var previewUseCase: Preview? = null
    private var imageAnalysisUseCase: ImageAnalysis? = null
    private var isRunning = false

    // Default zoom ratio 1.0x (wide natural field of view)
    var currentZoom: Float = 1.0f
        private set

    private val orientationEventListener by lazy {
        object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                updateTargetRotation(rotation)
            }
        }
    }

    fun startCamera(previewView: PreviewView) {
        previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindCameraUseCases(previewView)
                if (orientationEventListener.canDetectOrientation()) {
                    orientationEventListener.enable()
                }
            } catch (e: Exception) {
                Log.e("CameraManager", "Camera binding failed: ${e.message}", e)
                onError("خطأ: تعذر الوصول للكاميرا: ${e.localizedMessage}")
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun updateTargetRotation(rotation: Int) {
        try {
            previewUseCase?.targetRotation = rotation
            imageAnalysisUseCase?.targetRotation = rotation
        } catch (e: Exception) {
            Log.w("CameraManager", "Failed to update target rotation: ${e.message}")
        }
    }

    private fun bindCameraUseCases(previewView: PreviewView) {
        val provider = cameraProvider ?: return

        try {
            provider.unbindAll()

            val displayRotation = previewView.display?.rotation ?: Surface.ROTATION_0

            val preview = Preview.Builder()
                .setTargetRotation(displayRotation)
                .build()
                .also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
            previewUseCase = preview

            val imageAnalysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(640, 480))
                .setTargetRotation(displayRotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }
            imageAnalysisUseCase = imageAnalysis

            val cameraSelector = if (provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else if (provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )
            isRunning = true
            setZoomRatio(currentZoom)
            Log.i("CameraManager", "Camera bound with zoom $currentZoom and targetRotation $displayRotation")
        } catch (e: Exception) {
            Log.e("CameraManager", "Use case binding failed: ${e.message}", e)
            onError("خطأ: تعذر تشغيل الكاميرا: ${e.localizedMessage}")
        }
    }

    fun setZoomRatio(ratio: Float) {
        currentZoom = ratio.coerceIn(1.0f, 3.0f)
        try {
            camera?.cameraControl?.setZoomRatio(currentZoom)
        } catch (e: Exception) {
            Log.w("CameraManager", "Failed to set zoom ratio $currentZoom: ${e.message}")
        }
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            val bitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees

            val rotatedBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                bitmap.recycle()
                rotated
            } else {
                bitmap
            }

            onFrameAnalyzed(rotatedBitmap, rotationDegrees)
        } catch (e: Exception) {
            Log.e("CameraManager", "Frame conversion error: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }

    fun stopCamera() {
        isRunning = false
        try {
            orientationEventListener.disable()
            cameraProvider?.unbindAll()
        } catch (e: Exception) {
            Log.e("CameraManager", "Error stopping camera: ${e.message}")
        }
    }

    fun shutdown() {
        stopCamera()
        cameraExecutor.shutdown()
    }
}
