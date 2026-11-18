package com.example.vision.engine

import android.os.SystemClock
import com.example.vision.data.DatabaseHelper
import com.example.vision.data.DetectionItem
import com.example.vision.data.HazardCatalog
import com.example.vision.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.hypot

/**
 * Tracks physical hazard objects across frames to prevent duplicate counting and duplicate audio alerts.
 */
data class TrackedHazard(
    var classId: Int,
    var centerX: Float,
    var centerY: Float,
    var boxHeight: Float = 0f,
    var lastSeenTimeMs: Long,
    var consecutiveHits: Int = 1,
    var verifiedHits: Int = 0,
    var hasAlerted: Boolean = false,
    var announcedClassId: Int? = null,
    var hasLoggedToDb: Boolean = false,
    var loomRate: Float = 0f,
    var estimatedTtcSeconds: Float = Float.MAX_VALUE,
    val speedVotes: MutableMap<Int, Int> = mutableMapOf()
)

/**
 * Evaluates detection results:
 * 1. Filters items by individual user preferences and per-class confidence thresholds.
 * 2. Applies configurable temporal confirmation debouncing (1, 2, 3, or 4 frames).
 * 3. Suppresses duplicate alerts for the same physical sign across frames.
 * 4. Persists confirmed detections with live device GPS coordinates.
 */
class HazardEvaluator(
    private val alertSystem: AlertSystem,
    private val dbHelper: DatabaseHelper,
    var locationProvider: (() -> Pair<Double, Double>)? = null
) {

    private val bgScope = CoroutineScope(Dispatchers.IO)
    var currentTripId: Long = 0L

    // Configurable detection debouncing & spatial tracking parameters
    var temporalConfirmationFrames: Int = 2 // Default 2 frames (configurable: 1, 2, 3, 4)
    var trackExpiryMs: Long = 2500L // Object must disappear for 2.5s before a new instance can trigger
    var matchDistanceThreshold: Float = 0.22f // Normalized screen distance to associate same physical sign

    // Active spatial tracks across frames
    private val activeTracks = mutableListOf<TrackedHazard>()

    /**
     * Groups hazard classes into spatial-tracking families so nearby detections of unrelated
     * types (e.g. a speed sign next to a traffic signal) never pollute each other's track.
     */
    private fun classFamily(classId: Int): Int = when {
        SpeedSignVerifier.isSpeedSign(classId) -> 100
        classId == 4 || classId == 11 -> 101
        else -> classId
    }

    /**
     * Returns the leading OCR-verified speed class and its vote count, plus the runner-up count.
     */
    private fun leadingSpeedVote(votes: Map<Int, Int>): Triple<Int?, Int, Int> {
        if (votes.isEmpty()) return Triple(null, 0, 0)
        val sorted = votes.entries.sortedByDescending { it.value }
        val leader = sorted[0]
        val runnerUpVotes = if (sorted.size >= 2) sorted[1].value else 0
        return Triple(leader.key, leader.value, runnerUpVotes)
    }

    private fun relabelToClass(item: DetectionItem, targetClassId: Int): DetectionItem {
        if (item.classId == targetClassId) return item
        val meta = HazardCatalog.getMeta(targetClassId) ?: return item
        return item.copy(
            classId = targetClassId,
            className = meta.label,
            arabicName = meta.arabicTitle,
            englishName = meta.englishTitle,
            audioResId = meta.soundResId
        )
    }

    private fun insertDetectionWithLocation(item: DetectionItem) {
        val tripId = currentTripId
        if (tripId <= 0L) return
        val (lat, lon) = locationProvider?.invoke() ?: Pair(0.0, 0.0)
        bgScope.launch {
            try {
                dbHelper.insertDetection(tripId, item, latitude = lat, longitude = lon)
            } catch (_: Exception) {}
        }
    }

    private fun logToDatabaseOnce(track: TrackedHazard, item: DetectionItem) {
        if (track.hasLoggedToDb) return
        track.hasLoggedToDb = true
        insertDetectionWithLocation(item)
    }

    /**
     * Process detected items in the current frame.
     * Returns confirmed/displayable items for drawing bounding boxes, and speaks only after
     * temporal & verification requirements are satisfied.
     */
    fun evaluate(
        items: List<DetectionItem>,
        prefs: UserPreferences
    ): List<DetectionItem> {
        if (items.isEmpty()) return emptyList()

        // 0. Filter detections by per-class calibrated confidence and speed sign distance threshold
        val validItems = items.filter { item ->
            val requiredConfidence = HazardCatalog.getClassConfidenceThreshold(item.classId, prefs.detectionThreshold)
            val passesConfidence = item.confidence >= requiredConfidence
            val passesSize = if (SpeedSignVerifier.isSpeedSign(item.classId)) {
                // Reject tiny distant speed signs (< 3.5% frame size) where numerals blur into 80/60
                item.boundingBox.width() >= 0.035f && item.boundingBox.height() >= 0.035f
            } else {
                true
            }
            passesConfidence && passesSize
        }

        if (validItems.isEmpty()) return emptyList()

        val now = SystemClock.uptimeMillis()

        // 1. Prune expired tracks that have left the camera frame
        // Traffic signals (Class 13) get 10s retention so momentary occlusion or stops at intersections never lose the track
        activeTracks.removeAll { track ->
            val expiry = if (track.classId == 13) 10000L else trackExpiryMs
            now - track.lastSeenTimeMs > expiry
        }

        val displayItems = mutableListOf<DetectionItem>()
        val baseRequiredHits = temporalConfirmationFrames.coerceIn(1, 4)

        // 2. Spatial object matching within the same class family
        for (item in validItems) {
            val cx = item.boundingBox.centerX()
            val cy = item.boundingBox.centerY()
            val currentH = item.boundingBox.height()
            val family = classFamily(item.classId)
            val isSpeed = SpeedSignVerifier.isSpeedSign(item.classId)

            val matchingTrack = activeTracks
                .filter { classFamily(it.classId) == family }
                .minByOrNull { hypot(it.centerX - cx, it.centerY - cy) }

            val distance = if (matchingTrack != null) hypot(matchingTrack.centerX - cx, matchingTrack.centerY - cy) else Float.MAX_VALUE

            val track: TrackedHazard
            if (matchingTrack != null && distance < matchDistanceThreshold) {
                track = matchingTrack
                // Calculate optical looming rate and predictive TTC
                val dt = (now - track.lastSeenTimeMs) / 1000.0f
                if (dt in 0.015f..1.5f && track.boxHeight > 0f) {
                    val dh = currentH - track.boxHeight
                    val rate = dh / dt
                    track.loomRate = rate
                    if (rate > 0.015f && currentH > 0.035f) {
                        track.estimatedTtcSeconds = (currentH / rate).coerceAtLeast(0.1f)
                    } else {
                        track.estimatedTtcSeconds = Float.MAX_VALUE
                    }
                }
                track.boxHeight = currentH
                track.centerX = cx
                track.centerY = cy
                track.lastSeenTimeMs = now
                track.consecutiveHits++
            } else {
                track = TrackedHazard(
                    classId = item.classId,
                    centerX = cx,
                    centerY = cy,
                    boxHeight = currentH,
                    lastSeenTimeMs = now,
                    consecutiveHits = 1
                )
                activeTracks.add(track)
            }

            if (item.verified) {
                track.verifiedHits++
            }

            if (isSpeed) {
                if (item.verified) {
                    track.speedVotes[item.classId] = (track.speedVotes[item.classId] ?: 0) + 1
                }
                val (leaderClass, leaderVotes, runnerUpVotes) = leadingSpeedVote(track.speedVotes)
                if (leaderClass != null) {
                    track.classId = leaderClass
                } else if (!track.hasAlerted) {
                    track.classId = item.classId
                }

                // Decide whether this speed track is ready to speak (or correct an earlier announcement)
                val ocrReady = leaderClass != null &&
                    (leaderVotes >= 2 || (leaderVotes >= 1 && track.consecutiveHits >= 2)) &&
                    leaderVotes > runnerUpVotes
                val fallbackReady = leaderClass == null &&
                    track.consecutiveHits >= 6 &&
                    item.classId in 5..10 &&
                    item.confidence >= 0.75f &&
                    currentH >= 0.07f

                val resolvedClassId = leaderClass ?: track.classId
                val effectiveItem = relabelToClass(item, resolvedClassId)

                // Hide unverified raw YOLO speed guesses until OCR/verification has at least one vote (or high-conf close fallback)
                if (item.verified || leaderClass != null || fallbackReady) {
                    displayItems.add(effectiveItem)
                }

                if (!track.hasAlerted) {
                    if (ocrReady || fallbackReady) {
                        logToDatabaseOnce(track, effectiveItem)
                        if (prefs.enabledClassIds.contains(resolvedClassId)) {
                            val isClose = track.estimatedTtcSeconds in 0.1f..4.0f || currentH >= 0.14f
                            val priority = AlertSystem.getHazardPriority(resolvedClassId)
                            val alertFired = alertSystem.triggerAlert(resolvedClassId, isUrgent = isClose, priorityOverride = priority)
                            if (alertFired) {
                                track.hasAlerted = true
                                track.announcedClassId = resolvedClassId
                            }
                        }
                    }
                } else {
                    // Already announced: check if stronger closer-range OCR votes now override an earlier misread
                    val needsCorrection = leaderClass != null &&
                        leaderClass != track.announcedClassId &&
                        leaderVotes >= 3 &&
                        (leaderVotes - runnerUpVotes) >= 2
                    if (needsCorrection && prefs.enabledClassIds.contains(leaderClass!!)) {
                        val isClose = track.estimatedTtcSeconds in 0.1f..4.0f || currentH >= 0.14f
                        val priority = AlertSystem.getHazardPriority(leaderClass)
                        val corrected = alertSystem.triggerAlert(leaderClass, isUrgent = isClose, priorityOverride = priority, force = true)
                        if (corrected) {
                            track.announcedClassId = leaderClass
                            insertDetectionWithLocation(effectiveItem)
                        }
                    }
                }
            } else {
                // Non-speed hazard (classes 0..4, 11..13)
                if (track.classId != item.classId && !track.hasAlerted) {
                    track.classId = item.classId
                }

                // Pedestrians (3) high confidence (>= 0.50f) requires only 1 hit for immediate early warning,
                // moderate confidence (>= 0.38f) requires 2 hits.
                val requiredHits = when (track.classId) {
                    3 -> if (item.confidence >= 0.50f) 1 else 2
                    12 -> maxOf(baseRequiredHits, 2)
                    13 -> 1
                    else -> maxOf(baseRequiredHits, 2)
                }
                val verificationPassed = when (track.classId) {
                    12 -> item.verified && track.verifiedHits >= 2
                    1, 2, 4, 11, 13 -> item.verified && track.verifiedHits >= 1
                    else -> true
                }

                // Only draw boxes when verified (or class 0)
                if (track.classId == 0 || item.verified) {
                    displayItems.add(item)
                    logToDatabaseOnce(track, item)
                }

                val isConfirmed = track.consecutiveHits >= requiredHits && verificationPassed

                if (isConfirmed && !track.hasAlerted && prefs.enabledClassIds.contains(track.classId)) {
                    // ADAS Safety Zoning:
                    // Emergency Preemption (P=100) for pedestrians in ego corridor early (bottom >= 0.42f, h >= 0.045f).
                    // Human Life Safety: Pedestrian alert always has top priority (100) so no road sign can preempt it
                    val isInDrivingCorridor = cx in 0.30f..0.70f && item.boundingBox.bottom >= 0.42f && currentH >= 0.045f
                    val (priority, isUrgent) = when (track.classId) {
                        3 -> Pair(100, isInDrivingCorridor)
                        13 -> {
                            val isClose = currentH >= 0.10f || track.estimatedTtcSeconds < 4.5f
                            Pair(if (isClose) 85 else 65, isClose)
                        }
                        1, 12 -> Pair(AlertSystem.getHazardPriority(track.classId), true)
                        else -> {
                            val isClose = track.estimatedTtcSeconds in 0.1f..4.0f || currentH >= 0.14f
                            Pair(AlertSystem.getHazardPriority(track.classId), isClose)
                        }
                    }

                    val alertFired = alertSystem.triggerAlert(track.classId, isUrgent = isUrgent, priorityOverride = priority)
                    if (alertFired) {
                        track.hasAlerted = true
                        track.announcedClassId = track.classId
                    }
                }
            }
        }

        return displayItems
    }

    fun resetTrip() {
        activeTracks.clear()
        currentTripId = 0L
    }
}
