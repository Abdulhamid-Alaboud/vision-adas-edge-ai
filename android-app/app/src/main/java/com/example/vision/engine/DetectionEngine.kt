package com.example.vision.engine

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.vision.data.DetectionItem

/**
 * Universal Mobile ADAS Detection Engine for VISION.
 *
 * YOLO11n Mobile ADAS Engine (yolo11n_adas_v2_640.onnx):
 *  - 2.58M Parameters, 6.4 GFLOPs (Lightweight mobile edge CNN)
 *  - Native [1, 18, 8400] output decoding with NMS
 *  - Calibrated per-class confidence thresholds
 */
class DetectionEngine(
    private val context: Context,
    val preferredModelName: String = "yolo11n_adas_v2_640.onnx"
) {

    private var yoloEngine: YoloOnnxEngine? = null

    val inputWidth = 640
    val inputHeight = 640

    var confidenceThreshold: Float = 0.45f
        set(value) {
            field = value
            yoloEngine?.confidenceThreshold = value
        }

    val lastMaxConfidence: Float
        get() = yoloEngine?.lastMaxConfidence ?: 0f

    val lastMaxClassId: Int
        get() = yoloEngine?.lastMaxClassId ?: -1

    val lastMaxClassName: String
        get() = yoloEngine?.lastMaxClassName ?: ""

    var iouThreshold: Float = 0.45f
        set(value) {
            field = value
            yoloEngine?.iouThreshold = value
        }

    val lastInferenceTimeMs: Long
        get() = yoloEngine?.lastInferenceTimeMs ?: 0L

    val activeEngineName: String
        get() = when {
            yoloEngine?.isLoaded == true -> "YOLO11n-ADAS-v2"
            else -> "VISION ADAS (Initializing...)"
        }

    val isLoaded: Boolean
        get() = yoloEngine?.isLoaded == true

    init {
        initializeEngine()
    }

    private fun initializeEngine() {
        val yoloCandidates = listOf(
            preferredModelName,
            "yolo11n_adas_v2_640.onnx"
        ).distinct()

        for (candidate in yoloCandidates) {
            if (isAssetPresent(candidate)) {
                try {
                    Log.i(TAG, "Initializing YOLO11 ONNX engine with model: $candidate")
                    val engine = YoloOnnxEngine(context, candidate).apply {
                        confidenceThreshold = this@DetectionEngine.confidenceThreshold
                        iouThreshold = this@DetectionEngine.iouThreshold
                    }
                    if (engine.isLoaded) {
                        yoloEngine = engine
                        Log.i(TAG, "Successfully activated YOLO11n Mobile ADAS as primary detection engine.")
                        return
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load YOLO11 ONNX engine: ${e.message}", e)
                }
            }
        }

        Log.w(TAG, "No valid ONNX model found in assets.")
    }

    private fun isAssetPresent(name: String): Boolean {
        return try {
            context.assets.open(name).close()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun detect(bitmap: Bitmap): List<DetectionItem> {
        val yEngine = yoloEngine
        if (yEngine != null && yEngine.isLoaded) {
            return yEngine.detect(bitmap)
        }
        return emptyList()
    }

    fun close() {
        try {
            yoloEngine?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing YOLO engine: ${e.message}")
        }
        yoloEngine = null
    }

    companion object {
        private const val TAG = "DetectionEngine"
    }
}
