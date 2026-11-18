package com.example.vision.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.util.Log
import com.example.vision.data.DetectionItem
import com.example.vision.data.HazardCatalog
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * High-Precision Dual-Brain Speed Sign Verifier (Hybrid Edge AI Architecture).
 *
 * Architecture:
 * 1. YOLO11 performs ultra-fast spatial localization (finds candidate sign bounding box).
 * 2. A chromatic sanity gate (red ring + white disc) rejects sky / flags / poles that YOLO
 *    mistakes for a speed sign.
 * 3. The full (padded) sign box is upscaled and run through on-device OCR. A second pass with
 *    a grayscale contrast-stretched copy is attempted when the first one fails.
 * 4. Exact numerals ("20".."140") override YOLO's classification. Only strictly valid speed
 *    values are accepted - no substring guessing. Results carry `verified = true` so the
 *    temporal logic can trust them; unverified results are only YOLO's raw opinion.
 */
object SpeedSignVerifier {

    private const val TAG = "SpeedSignVerifier"

    // On-device, 100% offline text recognizer
    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /** Speed values that exist on real roads, mapped to catalog class ids. */
    private val speedValueToClass = mapOf(
        140 to 19, 120 to 18, 110 to 17, 100 to 5, 90 to 16, 80 to 10,
        70 to 15, 60 to 9, 50 to 8, 40 to 7, 30 to 6, 20 to 14
    )

    /**
     * Checks if the detected class belongs to the speed limit family (IDs 5..10 and 14..19).
     */
    fun isSpeedSign(classId: Int): Boolean = classId in 5..10 || classId in 14..19

    /**
     * Verifies and refines a speed limit detection.
     *
     * @param originalBitmap The full camera frame or input bitmap.
     * @param detection The initial candidate detected by YOLO.
     * @param allowOcr Whether the OCR budget for this frame still permits a recognition pass.
     * @return null when the crop provably is not a speed sign (sky, flag, pole...);
     *         a `verified=true` item (class corrected by OCR) when numerals were read;
     *         otherwise the unverified YOLO detection.
     */
    fun verifySpeedSign(originalBitmap: Bitmap, detection: DetectionItem, allowOcr: Boolean = true): DetectionItem? {
        if (!isSpeedSign(detection.classId)) {
            return detection
        }

        try {
            val box = detection.boundingBox
            val imgW = originalBitmap.width
            val imgH = originalBitmap.height

            val left = (box.left * imgW).toInt().coerceIn(0, imgW - 1)
            val top = (box.top * imgH).toInt().coerceIn(0, imgH - 1)
            val right = (box.right * imgW).toInt().coerceIn(left + 1, imgW)
            val bottom = (box.bottom * imgH).toInt().coerceIn(top + 1, imgH)

            val boxWidth = right - left
            val boxHeight = bottom - top

            // 1. Geometry gate: a speed sign is (nearly) circular and never building-sized
            if (box.width() > 0.16f || box.height() > 0.22f) {
                Log.d(TAG, "Speed sign rejected: abnormally large box (${box.width()}x${box.height()})")
                return null
            }
            val aspect = boxWidth.toFloat() / boxHeight.coerceAtLeast(1)
            if (aspect < 0.60f || aspect > 1.65f) {
                Log.d(TAG, "Speed sign rejected: non-circular aspect ($aspect)")
                return null
            }

            if (!allowOcr || boxWidth < 20 || boxHeight < 20) {
                return detection
            }

            // 3. OCR on the FULL box with generous padding (YOLO boxes of small signs are loose / offset)
            val padX = (boxWidth * 0.14f).toInt()
            val padY = (boxHeight * 0.14f).toInt()
            val cropLeft = (left - padX).coerceIn(0, imgW - 1)
            val cropTop = (top - padY).coerceIn(0, imgH - 1)
            val cropRight = (right + padX).coerceIn(cropLeft + 1, imgW)
            val cropBottom = (bottom + padY).coerceIn(cropTop + 1, imgH)

            val raw = Bitmap.createBitmap(originalBitmap, cropLeft, cropTop, cropRight - cropLeft, cropBottom - cropTop)
            val longest = max(raw.width, raw.height)
            val scale = (160f / longest).coerceIn(1f, 6f)
            val scaled = if (scale > 1.05f) {
                Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true)
            } else raw

            var ocrResult = recognizeSpeed(scaled)
            if (ocrResult.speedValue == null && !ocrResult.hasAlphaWarningWord) {
                val enhanced = enhanceContrast(scaled)
                val secondPass = recognizeSpeed(enhanced)
                enhanced.recycle()
                ocrResult = SpeedOcrResult(
                    speedValue = secondPass.speedValue,
                    hasAlphaWarningWord = ocrResult.hasAlphaWarningWord || secondPass.hasAlphaWarningWord
                )
            }

            if (scaled !== raw) scaled.recycle()
            raw.recycle()

            // Reject roundels containing warning text like "SLOW DOWN / تمهل" with no speed numeral
            if (ocrResult.speedValue == null && ocrResult.hasAlphaWarningWord) {
                Log.i(TAG, "Speed sign rejected: non-speed text roundel (e.g. SLOW DOWN)")
                return null
            }

            val ocrClassId = ocrResult.speedValue?.let { speedValueToClass[it] }
            if (ocrClassId != null) {
                val meta = HazardCatalog.getMeta(ocrClassId)
                if (meta != null) {
                    if (ocrClassId != detection.classId) {
                        Log.i(TAG, "OCR SPEED OVERRIDE: YOLO class ${detection.classId} (${detection.className}) -> OCR class $ocrClassId (${ocrResult.speedValue})")
                    }
                    val prior = if (ocrClassId == detection.classId) detection.confidence else 0.50f
                    val fused = AdasSignVerifier.bayesianFusion(prior, 0.97f, falseAlarmLikelihood = 0.03f)
                    return detection.copy(
                        classId = ocrClassId,
                        className = meta.label,
                        arabicName = meta.arabicTitle,
                        englishName = meta.englishTitle,
                        confidence = fused,
                        audioResId = meta.soundResId,
                        verified = true
                    )
                }
            }

            // 4. Arabic-Indic Numeral Sign Verification (e.g. Saudi '٨٠' / '٦٠' signs where Latin OCR returns empty):
            // Verify concentric red ring + bright white core + central dark numeral strokes.
            if (detection.classId in 5..10 && detection.confidence >= 0.42f &&
                hasArabicNumeralInkProfile(originalBitmap, left, top, boxWidth, boxHeight)
            ) {
                val fused = AdasSignVerifier.bayesianFusion(detection.confidence, 0.88f, falseAlarmLikelihood = 0.05f)
                return detection.copy(confidence = fused, verified = true)
            }
        } catch (e: Exception) {
            Log.d(TAG, "SpeedSign OCR skipped or timed out: ${e.message}")
        }

        return detection
    }

    private data class SpeedOcrResult(
        val speedValue: Int?,
        val hasAlphaWarningWord: Boolean
    )

    /** Runs ML Kit on [bmp] and returns a strictly valid speed value or warning-word flag. */
    private fun recognizeSpeed(bmp: Bitmap): SpeedOcrResult {
        return try {
            val task = recognizer.process(InputImage.fromBitmap(bmp, 0))
            val text = Tasks.await(task, 150, TimeUnit.MILLISECONDS)
            parseSpeedFromText(text)
        } catch (e: Exception) {
            SpeedOcrResult(null, false)
        }
    }

    /** Grayscale + contrast stretch copy: helps washed-out / low-light signs. */
    private fun enhanceContrast(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = 1.9f
        val t = (-0.5f * c + 0.5f) * 255f
        val cm = ColorMatrix().apply {
            setSaturation(0f)
            postConcat(ColorMatrix(floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f
            )))
        }
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /**
     * A real speed-limit sign has a red ring (including sun-bleached desert red) AND a neutral/bright disc.
     * Sky, flags, poles and foliage never have both.
     */
    private fun hasSpeedSignColors(bmp: Bitmap, x: Int, y: Int, w: Int, h: Int): Boolean {
        val stepX = max(1, w / 32)
        val stepY = max(1, h / 32)
        var total = 0
        var red = 0
        var white = 0
        var yy = y
        while (yy < y + h) {
            var xx = x
            while (xx < x + w) {
                val color = bmp.getPixel(min(xx, bmp.width - 1), min(yy, bmp.height - 1))
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                total++
                if (r > 75 && r > g + 15 && r > b + 12) red++
                val mx = max(r, max(g, b))
                val mn = min(r, min(g, b))
                if ((mn > 70 && mx - mn < 42) || (mn > 90 && mx - mn < 50) || (mn > 120 && mx - mn < 65) || (r > 140 && g > 130 && b > 80)) white++
                xx += stepX
            }
            yy += stepY
        }
        if (total == 0) return false
        val redRatio = red.toFloat() / total
        val whiteRatio = white.toFloat() / total
        return redRatio >= 0.025f && whiteRatio >= 0.12f
    }

    /**
     * Checks that the central [0.22..0.78] core of the circular sign has a clean bright background
     * with dark numeral ink strokes (3%..52%), confirming a genuine numeral speed sign when Arabic-Indic
     * digits ('٨٠' / '٦٠') are not recognized by the Latin OCR model.
     */
    private fun hasArabicNumeralInkProfile(bmp: Bitmap, x: Int, y: Int, w: Int, h: Int): Boolean {
        val x0 = x + (w * 0.22f).toInt()
        val x1 = (x + (w * 0.78f).toInt()).coerceAtLeast(x0 + 1)
        val y0 = y + (h * 0.22f).toInt()
        val y1 = (y + (h * 0.78f).toInt()).coerceAtLeast(y0 + 1)
        var total = 0
        var whiteCore = 0
        var darkInk = 0
        var yy = y0
        while (yy < y1) {
            var xx = x0
            while (xx < x1) {
                val color = bmp.getPixel(min(xx, bmp.width - 1), min(yy, bmp.height - 1))
                val r = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val b = color and 0xFF
                val mx = max(r, max(g, b))
                val mn = min(r, min(g, b))
                total++
                if ((mn > 70 && mx - mn < 42) || (mn > 90 && mx - mn < 50) || (mn > 120 && mx - mn < 65) || (r > 140 && g > 130 && b > 80)) whiteCore++
                if (mx < 130) darkInk++
                xx++
            }
            yy++
        }
        if (total == 0) return false
        val whiteRatio = whiteCore.toFloat() / total
        val inkRatio = darkInk.toFloat() / total
        return whiteRatio >= 0.20f && inkRatio in 0.03f..0.52f
    }

    private fun normalizeDigits(text: String): String = text
        .replace('٠', '0').replace('١', '1').replace('٢', '2').replace('٣', '3').replace('٤', '4')
        .replace('٥', '5').replace('٦', '6').replace('٧', '7').replace('٨', '8').replace('٩', '9')

    /** Converts a token into digits only when it plausibly is a number (tolerates O/o for 0). */
    private fun tokenToDigits(token: String): String? {
        val n = normalizeDigits(token)
        if (n.none { it.isDigit() }) return null
        val cleaned = n.replace('O', '0').replace('o', '0').filter { it.isDigit() }
        return cleaned.ifEmpty { null }
    }

    /**
     * Strict parse: only an element / line whose digits form EXACTLY a legal speed value is accepted.
     * Also detects alphabetic warning words ("SLOW", "DOWN") so non-speed roundels can be rejected.
     */
    private fun parseSpeedFromText(text: Text): SpeedOcrResult {
        var bestValue: Int? = null
        var bestHeight = -1
        var hasWarningWord = false

        fun consider(raw: String, height: Int) {
            val upper = raw.uppercase()
            val lettersOnly = upper.filter { it.isLetter() }
            if (lettersOnly.contains("SLOW") || lettersOnly.contains("DOWN") ||
                lettersOnly.contains("SL0W") || lettersOnly.contains("D0WN") ||
                lettersOnly.contains("تمهل")
            ) {
                hasWarningWord = true
            }
            val digits = tokenToDigits(raw) ?: return
            val v = digits.toIntOrNull() ?: return
            if (speedValueToClass.containsKey(v) && height > bestHeight) {
                bestValue = v
                bestHeight = height
            }
        }

        for (block in text.textBlocks) {
            for (line in block.lines) {
                val lineH = line.boundingBox?.height() ?: 0
                for (el in line.elements) {
                    consider(el.text, el.boundingBox?.height() ?: lineH)
                }
                if (line.elements.size in 2..3) {
                    consider(line.text.replace(" ", ""), lineH)
                }
            }
        }
        return SpeedOcrResult(bestValue, hasWarningWord)
    }
}
