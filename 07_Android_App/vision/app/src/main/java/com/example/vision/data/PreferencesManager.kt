package com.example.vision.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("vision_user_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_VOLUME = "key_alert_volume"
        private const val KEY_THRESHOLD = "key_detection_threshold"
        private const val KEY_ENABLED_CLASSES = "key_enabled_classes"
        private const val KEY_LOGGED_USER = "key_logged_user"
        private const val KEY_LOGGED_ROLE = "key_logged_role"
        private const val KEY_LANGUAGE = "key_app_language"
        private const val KEY_TEMPORAL_FRAMES = "key_temporal_frames"
        private const val KEY_ALERT_COOLDOWN = "key_alert_cooldown"
        private const val KEY_TRACK_EXPIRY = "key_track_expiry"
    }

    var temporalConfirmationFrames: Int
        get() = prefs.getInt(KEY_TEMPORAL_FRAMES, 2)
        set(value) = prefs.edit().putInt(KEY_TEMPORAL_FRAMES, value.coerceIn(1, 4)).apply()

    var alertCooldownMs: Long
        get() = prefs.getLong(KEY_ALERT_COOLDOWN, 3500L)
        set(value) = prefs.edit().putLong(KEY_ALERT_COOLDOWN, value.coerceIn(1000L, 10000L)).apply()

    var trackExpiryMs: Long
        get() = prefs.getLong(KEY_TRACK_EXPIRY, 2500L)
        set(value) = prefs.edit().putLong(KEY_TRACK_EXPIRY, value.coerceIn(1000L, 5000L)).apply()

    var appLanguage: String
        get() = prefs.getString(KEY_LANGUAGE, "ar") ?: "ar"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    val isArabic: Boolean
        get() = appLanguage == "ar"

    var alertVolume: Int
        get() = prefs.getInt(KEY_VOLUME, 100)
        set(value) = prefs.edit().putInt(KEY_VOLUME, value).apply()

    var detectionThreshold: Float
        get() = prefs.getFloat(KEY_THRESHOLD, 0.40f).coerceIn(0.20f, 0.80f)
        set(value) = prefs.edit().putFloat(KEY_THRESHOLD, value.coerceIn(0.20f, 0.80f)).apply()

    var enabledClassIds: Set<Int>
        get() {
            val raw = prefs.getString(KEY_ENABLED_CLASSES, null) ?: return (0..19).toSet()
            val parsed = raw.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
            return if (parsed.isEmpty() || !parsed.contains(3)) {
                (0..19).toSet()
            } else {
                // Ensure new speed limit classes (14..19) are enabled by default for existing installations
                if (parsed.none { it in 14..19 }) parsed + (14..19).toSet() else parsed
            }
        }
        set(value) {
            val raw = value.joinToString(",")
            prefs.edit().putString(KEY_ENABLED_CLASSES, raw).apply()
        }

    fun isClassEnabled(classId: Int): Boolean {
        return enabledClassIds.contains(classId)
    }

    fun setClassEnabled(classId: Int, enabled: Boolean) {
        val current = enabledClassIds.toMutableSet()
        if (enabled) current.add(classId) else current.remove(classId)
        enabledClassIds = current
    }

    var currentUser: String?
        get() = prefs.getString(KEY_LOGGED_USER, null)
        set(value) = prefs.edit().putString(KEY_LOGGED_USER, value).apply()

    var currentRole: String?
        get() = prefs.getString(KEY_LOGGED_ROLE, null)
        set(value) = prefs.edit().putString(KEY_LOGGED_ROLE, value).apply()

    fun toUserPreferences(): UserPreferences {
        return UserPreferences(
            prefId = 1,
            alertVolume = alertVolume,
            detectionThreshold = detectionThreshold,
            enabledClassIds = enabledClassIds
        )
    }

    fun save(userPreferences: UserPreferences) {
        alertVolume = userPreferences.alertVolume
        detectionThreshold = userPreferences.detectionThreshold
        enabledClassIds = userPreferences.enabledClassIds
    }
}
