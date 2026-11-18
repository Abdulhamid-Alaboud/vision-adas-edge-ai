package com.example.vision.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import com.example.vision.data.DetectionItem
import com.example.vision.data.HazardCatalog
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

/**
 * Ultra-optimized ONNX Runtime inference engine for YOLO11n Mobile ADAS.
 *
 * Input:  "images"  -> [1, 3, 640, 640] Float32 (NCHW RGB, 0.0 - 1.0)
 * Output: "output0" -> [1, 18, 8400] Float32
 *   - Channel 0: cx (center x in 640px)
 *   - Channel 1: cy (center y in 640px)
 *   - Channel 2: w  (width in 640px)
 *   - Channel 3: h  (height in 640px)
 *   - Channels 4..17: Class probabilities for 14 ADAS categories
 *   - 8400 spatial candidate anchor cells
 */
class YoloOnnxEngine(
    private val context: Context,
    val modelFileName: String = "yolo11n_adas_v2_640.onnx"
) {

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    val inputWidth = 640
    val inputHeight = 640
    private val numChannels = 3
    private val totalPixels = inputWidth * inputHeight
    private val numAnchors = 8400
    private val numClasses = 14

    // Direct reusable buffer to avoid GC allocation overhead on each camera frame
    private val inputDirectByteBuffer = ByteBuffer.allocateDirect(1 * numChannels * totalPixels * 4).apply {
        order(ByteOrder.nativeOrder())
    }
    private val inputFloatBuffer: FloatBuffer = inputDirectByteBuffer.asFloatBuffer()

    private val intValues = IntArray(totalPixels)
    private val rPlane = FloatArray(totalPixels)
    private val gPlane = FloatArray(totalPixels)
    private val bPlane = FloatArray(totalPixels)

    var confidenceThreshold: Float = 0.45f
    var iouThreshold: Float = 0.45f

    var lastMaxConfidence: Float = 0f
        private set
    var lastMaxClassId: Int = -1
        private set
    var lastMaxClassName: String = ""
        private set

    var lastInferenceTimeMs: Long = 0L
        private set

    val isLoaded: Boolean
        get() = ortSession != null

    init {
        initializeModel()
    }

    private fun initializeModel() {
        try {
            if (ortEnv == null) {
                ortEnv = OrtEnvironment.getEnvironment()
            }
            val env = ortEnv ?: return
            val numCores = Runtime.getRuntime().availableProcessors()
            val threads = minOf(4, maxOf(2, numCores - 1))
            val options = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(threads)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                setMemoryPatternOptimization(true)
                setCPUArenaAllocator(true)
            }
            Log.i(TAG, "Initializing ONNX Runtime with $threads CPU threads")

            // Copy model to internal files directory once to allow fast OS mmap loading
            val modelFile = File(context.filesDir, modelFileName)
            val assetLength = try {
                context.assets.openFd(modelFileName).use { it.length }
            } catch (_: Exception) {
                -1L
            }

            if (!modelFile.exists() || (assetLength > 0 && modelFile.length() != assetLength)) {
                Log.i(TAG, "Extracting $modelFileName to internal storage for fast memory mapping...")
                context.assets.open(modelFileName).use { input ->
                    FileOutputStream(modelFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            ortSession = try {
                env.createSession(modelFile.absolutePath, options)
            } catch (e: Exception) {
                Log.w(TAG, "createSession from file failed, falling back to byte stream: ${e.message}")
                val modelBytes = context.assets.open(modelFileName).use { it.readBytes() }
                env.createSession(modelBytes, options)
            }

            Log.i(TAG, "YOLO11 session created with CPU execution")
            Log.i(TAG, "Model: $modelFileName loaded successfully (${if (modelFile.exists()) modelFile.length() / (1024 * 1024) else 0} MB)")
        } catch (e: Exception) {
            Log.e(TAG, "YOLO11 INITIALIZATION ERROR: Failed to load '$modelFileName': ${e.message}", e)
        }
    }

    fun detect(bitmap: Bitmap): List<DetectionItem> {
        val session = ortSession ?: return emptyList()
        val env = ortEnv ?: return emptyList()

        // 1. Resize frame to 640x640
        val scaledBitmap = if (bitmap.width == inputWidth && bitmap.height == inputHeight) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true)
        }

        // 2. Preprocess: Convert ARGB to planar NCHW Float32 [1, 3, 640, 640] normalized to [0, 1]
        scaledBitmap.getPixels(intValues, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        val inv255 = 1.0f / 255.0f
        for (i in 0 until totalPixels) {
            val pixel = intValues[i]
            rPlane[i] = ((pixel shr 16) and 0xFF) * inv255
            gPlane[i] = ((pixel shr 8) and 0xFF) * inv255
            bPlane[i] = (pixel and 0xFF) * inv255
        }

        inputFloatBuffer.rewind()
        inputFloatBuffer.put(rPlane)
        inputFloatBuffer.put(gPlane)
        inputFloatBuffer.put(bPlane)
        inputFloatBuffer.rewind()

        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }

        // 3. Prepare Single Input Tensor "images" [1, 3, 640, 640]
        val imagesTensor = OnnxTensor.createTensor(
            env,
            inputFloatBuffer,
            longArrayOf(1, 3, inputHeight.toLong(), inputWidth.toLong())
        )

        val candidates = mutableListOf<DetectionItem>()

        try {
            val inputs = mapOf("images" to imagesTensor)

            // 4. Run inference
            val tInfStart = SystemClock.uptimeMillis()
            session.run(inputs).use { results ->
                lastInferenceTimeMs = SystemClock.uptimeMillis() - tInfStart

                var outputTensor: OnnxTensor? = null
                for (entry in results) {
                    if (entry.key == "output0" || entry.key == "output") {
                        outputTensor = entry.value as? OnnxTensor
                        break
                    }
                }
                if (outputTensor == null && results.size() > 0) {
                    outputTensor = results.get(0) as? OnnxTensor
                }

                if (outputTensor == null) {
                    Log.e(TAG, "YOLO11 INFERENCE ERROR: output0 could not be resolved as OnnxTensor")
                    return emptyList()
                }

                val outBuf = outputTensor.floatBuffer.apply { rewind() }
                var topScore = 0f
                var topClassId = -1

                // Strides for [1, 18, 8400]:
                // Coordinate channels: 0 -> cx, 1 -> cy, 2 -> w, 3 -> h
                // Class probability channels: 4 + c (for c in 0..13)
                val cxOffset = 0 * numAnchors
                val cyOffset = 1 * numAnchors
                val wOffset = 2 * numAnchors
                val hOffset = 3 * numAnchors

                val inv640 = 1.0f / 640.0f

                for (a in 0 until numAnchors) {
                    var bestScore = 0f
                    var bestClass = -1

                    for (c in 0 until numClasses) {
                        val score = outBuf.get((4 + c) * numAnchors + a)
                        if (score > bestScore) {
                            bestScore = score
                            bestClass = c
                        }
                    }

                    if (bestScore > topScore) {
                        topScore = bestScore
                        topClassId = bestClass
                    }

                    if (bestClass !in 0 until numClasses) {
                        continue
                    }

                    // Strict argmax: An anchor belongs to its highest-confidence class.
                    // Reject if that winning class does not satisfy its calibrated threshold.
                    val effectiveThreshold = HazardCatalog.getClassConfidenceThreshold(bestClass, confidenceThreshold)
                    if (bestScore < effectiveThreshold) {
                        continue
                    }

                    val cx = outBuf.get(cxOffset + a)
                    val cy = outBuf.get(cyOffset + a)
                    val w = outBuf.get(wOffset + a)
                    val h = outBuf.get(hOffset + a)

                    val left = ((cx - w * 0.5f) * inv640).coerceIn(0f, 1f)
                    val top = ((cy - h * 0.5f) * inv640).coerceIn(0f, 1f)
                    val right = ((cx + w * 0.5f) * inv640).coerceIn(0f, 1f)
                    val bottom = ((cy + h * 0.5f) * inv640).coerceIn(0f, 1f)

                    val boxW = right - left
                    val boxH = bottom - top

                    // Discard degenerate or microscopic boxes (< 0.5% width/height)
                    if (boxW < 0.005f || boxH < 0.005f) continue

                    // 1. Distance filter for Speed signs (classes 5..10 and 14..19):
                    // Minimum ~19-20 px (0.030f) in 640x640 frame for reliable numeral reading while allowing long-range street detection.
                    val isSpeedSign = bestClass in 5..10 || bestClass in 14..19
                    if (isSpeedSign && (boxW < 0.030f || boxH < 0.030f)) continue

                    // 2. Stop sign (class 12) distance filter:
                    // Prevents distant tiny red specks (50m+ away) from being falsely announced as Stop sign.
                    if (bestClass == 12 && (boxW < 0.035f || boxH < 0.035f)) continue

                    // 3. No Entry sign (class 1) aspect-ratio filter:
                    // Genuine No Entry road sign is circular (ratio 0.65..1.45).
                    // Car taillights / horizontal light bars are elongated (> 1.45 or < 0.65).
                    if (bestClass == 1) {
                        val aspect = boxW / boxH.coerceAtLeast(0.001f)
                        if (aspect > 1.45f || aspect < 0.65f) continue
                    }

                    // 4. Sky prior: tiny regulatory-sign candidates in the upper sky band are flags, poles,
                    // lamps or cloud artifacts - never a real road sign at that size.
                    val centerYNorm = (top + bottom) * 0.5f
                    if ((isSpeedSign || bestClass == 12 || bestClass == 1 || bestClass == 2) &&
                        boxH < 0.07f && centerYNorm < 0.28f) continue

                    val rect = RectF(left, top, right, bottom)
                    val meta = HazardCatalog.getMeta(bestClass)

                    candidates.add(
                        DetectionItem(
                            classId = bestClass,
                            className = meta?.label ?: "class_$bestClass",
                            arabicName = meta?.arabicTitle ?: "خطر غير معروف",
                            englishName = meta?.englishTitle ?: (meta?.label ?: "Unknown Hazard"),
                            confidence = bestScore,
                            boundingBox = rect,
                            audioResId = meta?.soundResId
                        )
                    )
                }

                lastMaxConfidence = topScore
                lastMaxClassId = topClassId
                lastMaxClassName = if (topClassId in 0 until numClasses) {
                    HazardCatalog.getMeta(topClassId)?.label ?: "class_$topClassId"
                } else ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "YOLO11 INFERENCE ERROR: ${e.message}", e)
        } finally {
            try {
                imagesTensor.close()
            } catch (_: Exception) {}
        }

        // Lightweight NMS pass to eliminate overlapping boxes
        val nmsItems = if (candidates.size > 1) applyNms(candidates, iouThreshold) else candidates

        // Cascaded ADAS Hybrid Verification Pipeline:
        // 1. Speed signs (classes 5..10 & 14..19): Digit OCR verification
        // 2. Non-speed hazards (classes 0..4 & 11..13): Deterministic chromatic, geometric, and silhouette verification
        // OCR budget: only the 2 largest speed-sign candidates get a (costly) text-recognition pass per frame
        val ocrCandidates = nmsItems
            .filter { SpeedSignVerifier.isSpeedSign(it.classId) }
            .sortedByDescending { it.boundingBox.height() }
            .take(2)
        val finalItems = nmsItems.mapNotNull { item ->
            if (SpeedSignVerifier.isSpeedSign(item.classId)) {
                SpeedSignVerifier.verifySpeedSign(bitmap, item, allowOcr = ocrCandidates.contains(item))
            } else {
                AdasSignVerifier.verifyHazard(bitmap, item)
            }
        }

        return finalItems
    }

    private fun sameNmsGroup(classA: Int, classB: Int): Boolean {
        if (classA == classB) return true
        if (SpeedSignVerifier.isSpeedSign(classA) && SpeedSignVerifier.isSpeedSign(classB)) return true
        if ((classA == 4 || classA == 11) && (classB == 4 || classB == 11)) return true
        return false
    }

    private fun applyNms(boxes: List<DetectionItem>, iouThresh: Float): List<DetectionItem> {
        val sorted = boxes.sortedByDescending { it.confidence }.toMutableList()
        val selected = mutableListOf<DetectionItem>()

        while (sorted.isNotEmpty()) {
            val first = sorted.removeAt(0)
            selected.add(first)

            val iterator = sorted.iterator()
            while (iterator.hasNext()) {
                val next = iterator.next()
                if (sameNmsGroup(first.classId, next.classId) && calculateIoU(first.boundingBox, next.boundingBox) >= iouThresh) {
                    iterator.remove()
                }
            }
        }
        return selected
    }

    private fun calculateIoU(boxA: RectF, boxB: RectF): Float {
        val xA = max(boxA.left, boxB.left)
        val yA = max(boxA.top, boxB.top)
        val xB = min(boxA.right, boxB.right)
        val yB = min(boxA.bottom, boxB.bottom)

        val intersectionArea = max(0f, xB - xA) * max(0f, yB - yA)
        val boxAArea = (boxA.right - boxA.left) * (boxA.bottom - boxA.top)
        val boxBArea = (boxB.right - boxB.left) * (boxB.bottom - boxB.top)

        val unionArea = boxAArea + boxBArea - intersectionArea
        return if (unionArea > 0f) intersectionArea / unionArea else 0f
    }

    fun close() {
        try {
            ortSession?.close()
            ortSession = null
            ortEnv?.close()
            ortEnv = null
            Log.i(TAG, "YOLO11 ONNX session closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ONNX session: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "YoloOnnxEngine"
    }
}
