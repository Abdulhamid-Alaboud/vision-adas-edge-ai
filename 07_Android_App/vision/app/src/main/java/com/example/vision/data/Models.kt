package com.example.vision.data

import android.graphics.RectF
import androidx.annotation.RawRes
import com.example.vision.R

data class DetectionItem(
    val classId: Int,
    val className: String,
    val arabicName: String,
    val englishName: String = "",
    val confidence: Float,
    val boundingBox: RectF,
    @param:RawRes val audioResId: Int? = null,
    /** True only when an independent verifier (OCR / geometry / luminous-bulb check) confirmed the YOLO candidate. */
    val verified: Boolean = false
) {
    fun displayName(isArabic: Boolean): String {
        return if (isArabic) arabicName else (if (englishName.isNotEmpty()) englishName else className)
    }
}

data class DetectionRecord(
    val recordId: Long = 0,
    val tripId: Long,
    val objectType: String,
    val arabicType: String,
    val confidence: Float,
    val latitude: Double,
    val longitude: Double,
    val detectedAt: String,
    val driverUsername: String = "driver",
    val isSynced: Boolean = false
)

data class TripSession(
    val tripId: Long = 0,
    val startTime: String,
    val endTime: String? = null,
    val totalHazards: Int = 0,
    val driverUsername: String = "driver"
)

data class DriverSummary(
    val username: String,
    val tripCount: Int,
    val hazardCount: Int
)

data class UserPreferences(
    val prefId: Int = 1,
    val alertVolume: Int = 100,
    val detectionThreshold: Float = 0.45f,
    val enabledClassIds: Set<Int> = (0..19).toSet()
)

data class User(
    val userId: Long = 0,
    val username: String,
    val passwordHash: String = "",
    val role: String
)

/**
 * Catalog of model classes, bilingual labels, audio mappings, and high-level safety categories.
 */
object HazardCatalog {

    enum class HazardCategory {
        PEDESTRIAN,
        SPEED_BUMP,
        TRAFFIC_SIGN
    }

    data class HazardMeta(
        val id: Int,
        val label: String,
        val arabicTitle: String,
        val englishTitle: String,
        val category: HazardCategory,
        @param:RawRes val arabicSoundResId: Int,
        @param:RawRes val englishSoundResId: Int
    ) {
        // Backward-compatible accessor
        val soundResId: Int
            get() = arabicSoundResId

        fun title(isArabic: Boolean): String = if (isArabic) arabicTitle else englishTitle
        fun soundRes(isArabic: Boolean): Int = if (isArabic) arabicSoundResId else englishSoundResId
    }

    val CLASSES: Map<Int, HazardMeta> = mapOf(
        0 to HazardMeta(0, "loose_gravel", "انتبه، حصى متطاير", "Caution, Loose Gravel", HazardCategory.TRAFFIC_SIGN, R.raw.ar_loose_gravel, R.raw.en_loose_gravel),
        1 to HazardMeta(1, "no_entry", "ممنوع الدخول", "No Entry", HazardCategory.TRAFFIC_SIGN, R.raw.ar_no_entry, R.raw.en_no_entry),
        2 to HazardMeta(2, "no_parking_waiting", "ممنوع الوقوف والانتظار", "No Parking or Waiting", HazardCategory.TRAFFIC_SIGN, R.raw.ar_no_parking_waiting, R.raw.en_no_parking_waiting),
        3 to HazardMeta(3, "pedestrian", "انتبه، مشاة أمامك", "Caution, Pedestrian Ahead", HazardCategory.PEDESTRIAN, R.raw.ar_pedestrian, R.raw.en_pedestrian),
        4 to HazardMeta(4, "pedestrian_crossing_sign", "انتبه، ممر مشاة", "Caution, Pedestrian Crossing", HazardCategory.TRAFFIC_SIGN, R.raw.ar_pedestrian_crossing_sign, R.raw.en_pedestrian_crossing_sign),
        5 to HazardMeta(5, "speed_100", "السرعة المحددة 100", "Speed Limit 100", HazardCategory.TRAFFIC_SIGN, R.raw.ar_speed_100, R.raw.en_speed_100),
        6 to HazardMeta(6, "speed_30", "السرعة المحددة 30", "Speed Limit 30", HazardCategory.TRAFFIC_SIGN, R.raw.ar_speed_30, R.raw.en_speed_30),
        7 to HazardMeta(7, "speed_40", "السرعة المحددة 40", "Speed Limit 40", HazardCategory.TRAFFIC_SIGN, R.raw.ar_speed_40, R.raw.en_speed_40),
        8 to HazardMeta(8, "speed_50", "السرعة المحددة 50", "Speed Limit 50", HazardCategory.TRAFFIC_SIGN, R.raw.ar_speed_50, R.raw.en_speed_50),
        9 to HazardMeta(9, "speed_60", "السرعة المحددة 60", "Speed Limit 60", HazardCategory.TRAFFIC_SIGN, R.raw.ar_speed_60, R.raw.en_speed_60),
        10 to HazardMeta(10, "speed_80", "السرعة المحددة 80", "Speed Limit 80", HazardCategory.TRAFFIC_SIGN, R.raw.ar_speed_80, R.raw.en_speed_80),
        11 to HazardMeta(11, "speed_bump", "انتبه، مطب أمامك", "Caution, Speed Bump Ahead", HazardCategory.SPEED_BUMP, R.raw.ar_speed_bump, R.raw.en_speed_bump),
        12 to HazardMeta(12, "stoop_sign", "انتبه، لوحة قف أمامك", "Caution, Stop Sign Ahead", HazardCategory.TRAFFIC_SIGN, R.raw.ar_stoop_sign, R.raw.en_stoop_sign),
        13 to HazardMeta(13, "traffic_signal", "انتبه، إشارة مرور", "Caution, Traffic Signal", HazardCategory.TRAFFIC_SIGN, R.raw.ar_traffic_signal, R.raw.en_traffic_signal),
        // Zero-shot hybrid verified speed limits (20, 70, 90, 110, 120, 140 km/h)
        14 to HazardMeta(14, "speed_20", "السرعة المحددة 20", "Speed Limit 20", HazardCategory.TRAFFIC_SIGN, R.raw.ar_speed_20, R.raw.en_speed_20),
        15 to HazardMeta(15, "speed_70", "السرعة المحددة 70", "Speed Limit 70", HazardCategory.TRAFFIC_SIGN, R.raw.ar_speed_70, R.raw.en_speed_70),
        16 to HazardMeta(16, "speed_90", "السرعة المحددة 90", "Speed Limit 90", HazardCategory.TRAFFIC_SIGN, R.raw.ar_speed_90, R.raw.en_speed_90),
        17 to HazardMeta(17, "speed_110", "السرعة المحددة 110", "Speed Limit 110", HazardCategory.TRAFFIC_SIGN, R.raw.ar_speed_110, R.raw.en_speed_110),
        18 to HazardMeta(18, "speed_120", "السرعة المحددة 120", "Speed Limit 120", HazardCategory.TRAFFIC_SIGN, R.raw.ar_speed_120, R.raw.en_speed_120),
        19 to HazardMeta(19, "speed_140", "السرعة المحددة 140", "Speed Limit 140", HazardCategory.TRAFFIC_SIGN, R.raw.ar_speed_140, R.raw.en_speed_140)
    )

    // Clean Per-Class Confidence Calibration Map (High-precision ADAS)
    val classConfidenceOverrides: MutableMap<Int, Float> = mutableMapOf(
        13 to 0.45f, // traffic_signal
        3 to 0.48f,  // pedestrian (Clean threshold to eliminate false positives on trees/trash)
        11 to 0.45f, // speed_bump (True YOLO class prediction)
        1 to 0.40f,  // no_entry
        2 to 0.45f,  // no_parking_waiting
        12 to 0.40f, // stoop_sign
        4 to 0.45f,  // pedestrian_crossing_sign
        0 to 0.45f,  // loose_gravel
        10 to 0.38f, // speed_80
        9 to 0.38f,  // speed_60
        8 to 0.38f,  // speed_50
        7 to 0.38f,  // speed_40
        6 to 0.38f,  // speed_30
        5 to 0.38f,  // speed_100
        14 to 0.38f, // speed_20
        15 to 0.38f, // speed_70
        16 to 0.38f, // speed_90
        17 to 0.38f, // speed_110
        18 to 0.38f, // speed_120
        19 to 0.38f  // speed_140
    )

    fun getClassConfidenceThreshold(classId: Int, defaultThreshold: Float): Float {
        return classConfidenceOverrides[classId] ?: defaultThreshold
    }

    fun getMeta(classId: Int): HazardMeta? = CLASSES[classId]

    fun getLocalizedTitle(identifier: String, isArabic: Boolean): String {
        val meta = CLASSES.values.firstOrNull {
            it.label == identifier || it.arabicTitle == identifier || it.englishTitle == identifier
        }
        return meta?.title(isArabic) ?: identifier
    }
}
