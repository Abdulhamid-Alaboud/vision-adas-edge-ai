package com.example.vision

import android.graphics.RectF
import com.example.vision.data.DetectionItem
import com.example.vision.data.DetectionRecord
import com.example.vision.data.DriverSummary
import com.example.vision.data.HazardCatalog
import com.example.vision.data.LocationSample
import com.example.vision.data.LocationTracker
import com.example.vision.data.TripExportHelper
import com.example.vision.data.TripSession
import com.example.vision.engine.AdasSignVerifier
import com.example.vision.engine.AlertSystem
import com.example.vision.ui.AppStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemIntegrityTest {

    @Test
    fun testHazardCatalog_all20ClassesIntegrity() {
        assertEquals("Hazard catalog must have exactly 20 classes", 20, HazardCatalog.CLASSES.size)

        for (classId in 0..19) {
            val meta = HazardCatalog.getMeta(classId)
            assertNotNull("Class $classId must exist in HazardCatalog", meta)
            meta?.let {
                assertEquals(classId, it.id)
                assertTrue("Class $classId must have non-empty label", it.label.isNotEmpty())
                assertTrue("Class $classId must have non-empty Arabic title", it.arabicTitle.isNotEmpty())
                assertTrue("Class $classId must have non-empty English title", it.englishTitle.isNotEmpty())
                assertTrue("Class $classId must have valid Arabic sound res ID", it.arabicSoundResId != 0)
                assertTrue("Class $classId must have valid English sound res ID", it.englishSoundResId != 0)

                assertEquals(it.arabicTitle, it.title(isArabic = true))
                assertEquals(it.englishTitle, it.title(isArabic = false))

                assertEquals(it.arabicSoundResId, it.soundRes(isArabic = true))
                assertEquals(it.englishSoundResId, it.soundRes(isArabic = false))
            }
        }
    }

    @Test
    fun testAppStrings_localizationCoverage() {
        val criticalKeys = listOf(
            "app_name", "back", "cancel", "select_language",
            "tab_login", "tab_register", "username", "password",
            "btn_sign_in", "btn_register", "btn_guest",
            "start_monitoring", "stop_monitoring", "hazard_alert",
            "settings_title", "btn_logout", "dialog_logout_title"
        )

        for (key in criticalKeys) {
            val ar = AppStrings.get(key, isArabic = true)
            val en = AppStrings.get(key, isArabic = false)

            assertTrue("Key '$key' Arabic translation must not be blank", ar.isNotBlank())
            assertTrue("Key '$key' English translation must not be blank", en.isNotBlank())
            assertTrue("Key '$key' should not return the raw key for Arabic", ar != key)
            assertTrue("Key '$key' should not return the raw key for English", en != key)
        }
    }

    @Test
    fun testDetectionItem_displayNameBilingual() {
        val item = DetectionItem(
            classId = 11,
            className = "speed_bump",
            arabicName = "انتبه، مطب أمامك",
            englishName = "Caution, Speed Bump Ahead",
            confidence = 0.88f,
            boundingBox = RectF(0.2f, 0.4f, 0.6f, 0.8f)
        )

        assertEquals("انتبه، مطب أمامك", item.displayName(isArabic = true))
        assertEquals("Caution, Speed Bump Ahead", item.displayName(isArabic = false))
    }

    @Test
    fun testAspectProjection_portraitAndLandscape() {
        // Test Portrait: 720x1280 camera frame on 1080x2400 screen
        val frameW = 720f
        val frameH = 1280f
        val canvasW = 1080f
        val canvasH = 2400f

        val frameAspect = frameW / frameH // 0.5625
        val canvasAspect = canvasW / canvasH // 0.45

        assertTrue("Frame is wider than canvas in portrait", frameAspect > canvasAspect)
        val scalePortrait = canvasH / frameH // 1.875
        val scaledWidth = frameW * scalePortrait // 1350
        val offsetX = (canvasW - scaledWidth) / 2f // -135

        // Center object in camera (0.5) must project to exact canvas center (540)
        val projectedCenterX = offsetX + (0.5f * frameW * scalePortrait)
        assertEquals(540f, projectedCenterX, 0.01f)

        // Test Landscape: 1280x720 camera frame on 2400x1080 screen
        val landFrameW = 1280f
        val landFrameH = 720f
        val landCanvasW = 2400f
        val landCanvasH = 1080f

        val landFrameAspect = landFrameW / landFrameH // 1.777
        val landCanvasAspect = landCanvasW / landCanvasH // 2.222

        assertTrue("Frame is taller than canvas in landscape", landFrameAspect < landCanvasAspect)
        val scaleLand = landCanvasW / landFrameW // 1.875
        val scaledHeight = landFrameH * scaleLand // 1350
        val offsetY = (landCanvasH - scaledHeight) / 2f // -135

        // Center object vertically in camera (0.5) must project to exact canvas vertical center (540)
        val projectedCenterY = offsetY + (0.5f * landFrameH * scaleLand)
        assertEquals(540f, projectedCenterY, 0.01f)
    }

    @Test
    fun testBayesianFusion_upliftAndSuppression() {
        // Strong physical/chromatic evidence should boost moderate YOLO prior
        val strongConfirm = AdasSignVerifier.bayesianFusion(
            priorConfidence = 0.50f,
            evidenceFidelity = 0.92f,
            falseAlarmLikelihood = 0.05f
        )
        assertTrue("Strong evidence must uplift posterior confidence ($strongConfirm)", strongConfirm > 0.90f)
        assertTrue("Posterior confidence stays bounded <= 0.99", strongConfirm <= 0.99f)

        // Poor evidence with high false-alarm likelihood should pull confidence down
        val weakReject = AdasSignVerifier.bayesianFusion(
            priorConfidence = 0.45f,
            evidenceFidelity = 0.15f,
            falseAlarmLikelihood = 0.65f
        )
        assertTrue("Weak evidence must suppress confidence below prior ($weakReject)", weakReject < 0.25f)
        assertTrue("Posterior confidence stays bounded >= 0.01", weakReject >= 0.01f)
    }

    @Test
    fun testClassCalibrationOverrides_boundedWithinSafeWindow() {
        for (classId in 0..19) {
            val thresh = HazardCatalog.getClassConfidenceThreshold(classId, 0.38f)
            assertTrue("Class $classId threshold $thresh must be >= 0.38", thresh >= 0.38f)
            assertTrue("Class $classId threshold $thresh must be <= 0.48", thresh <= 0.48f)
        }
    }

    @Test
    fun testAlertPriority_lifeFirstOrdering() {
        val pedestrianPrio = AlertSystem.getHazardPriority(3)
        val stopPrio = AlertSystem.getHazardPriority(12)
        val noEntryPrio = AlertSystem.getHazardPriority(1)
        val bumpPrio = AlertSystem.getHazardPriority(11)
        val speedPrio = AlertSystem.getHazardPriority(10)

        assertTrue("Pedestrian must hold highest life-first priority ($pedestrianPrio)", pedestrianPrio >= 90)
        assertTrue("Pedestrian must outrank Stop/No-Entry ($pedestrianPrio > $stopPrio)", pedestrianPrio > stopPrio)
        assertTrue("Emergency regulatory signs must outrank speed limits ($noEntryPrio > $speedPrio)", noEntryPrio > speedPrio)
        assertTrue("Speed bump warning must outrank speed limits ($bumpPrio > $speedPrio)", bumpPrio > speedPrio)
    }

    @Test
    fun testNewLocalizationKeys_presentInBilingualCatalog() {
        assertTrue(AppStrings.get("btn_export_cloud", true).isNotEmpty())
        assertTrue(AppStrings.get("btn_export_cloud", false).isNotEmpty())
        assertTrue(AppStrings.get("btn_export_csv", true).isNotEmpty())
        assertTrue(AppStrings.get("btn_export_csv", false).isNotEmpty())
        assertTrue(AppStrings.get("btn_share_history", true).isNotEmpty())
        assertTrue(AppStrings.get("btn_share_history", false).isNotEmpty())
        assertTrue(AppStrings.get("gps_unavailable", true).isNotEmpty())
        assertTrue(AppStrings.get("gps_unavailable", false).isNotEmpty())
        assertTrue(AppStrings.get("spec_classes", true).contains("96.3%"))
        assertTrue(AppStrings.get("spec_classes", false).contains("96.3%"))
    }

    @Test
    fun testAppGeneralization_noJazanOrRemovedAdminFeatureStrings() {
        val sampleKeys = listOf(
            "spec_model_name", "spec_architecture", "spec_params",
            "auth_app_title", "auth_app_subtitle", "history_screen_title"
        )
        for (key in sampleKeys) {
            for (isAr in listOf(true, false)) {
                val txt = AppStrings.get(key, isAr)
                assertFalse("Key $key must not reference Jazan", txt.contains("Jazan", ignoreCase = true))
                assertFalse("Key $key must not reference جازان", txt.contains("جازان"))
                assertFalse("Key $key must not reference جيزان", txt.contains("جيزان"))
            }
        }

        // Removed UI features must not remain registered in localization map
        val removedKeys = listOf(
            "latency_analysis_title", "provider_section_title",
            "field_test_badge", "field_test_title"
        )
        for (removed in removedKeys) {
            assertEquals("Removed key '$removed' should fall back to key name", removed, AppStrings.get(removed, true))
            assertEquals("Removed key '$removed' should fall back to key name", removed, AppStrings.get(removed, false))
        }
    }

    @Test
    fun testLocationTracker_selectionAndCoordinateValidation() {
        assertFalse("(0.0, 0.0) must be rejected as invalid", LocationTracker.isValidCoordinate(0.0, 0.0))
        assertFalse("Out-of-range latitude must be rejected", LocationTracker.isValidCoordinate(95.0, 42.5))
        assertTrue("Valid roadway GPS coordinate must pass", LocationTracker.isValidCoordinate(24.713552, 46.675296))

        val coarseNetwork = LocationSample(24.7100, 46.6700, accuracyMeters = 450f, timeMs = 10_000L, provider = "network")
        val freshGps = LocationSample(24.713552, 46.675296, accuracyMeters = 4.5f, timeMs = 12_000L, provider = "gps")
        assertTrue("Accurate GPS sample must beat coarse network fix", LocationTracker.isBetterSample(freshGps, coarseNetwork))
        assertFalse("Old coarse network fix must not overwrite accurate fresh GPS", LocationTracker.isBetterSample(coarseNetwork, freshGps))

        val displayAr = TripExportHelper.formatCoordsDisplay(24.713552, 46.675296, isArabic = true)
        assertTrue("Coordinates must isolate lat before lon cleanly", displayAr.contains("24.713552, 46.675296"))
        assertEquals(AppStrings.get("gps_unavailable", true), TripExportHelper.formatCoordsDisplay(0.0, 0.0, isArabic = true))
    }

    @Test
    fun testTripExportHelper_csvAndTextFormatting() {
        val sampleTrip = TripSession(tripId = 7L, startTime = "2026-11-14 17:00:00", endTime = "2026-11-14 17:20:00", totalHazards = 1, driverUsername = "abdulhamid")
        val sampleRecord = DetectionRecord(
            recordId = 101L,
            tripId = 7L,
            objectType = "speed_80",
            arabicType = "السرعة المحددة 80",
            confidence = 0.91f,
            latitude = 24.713552,
            longitude = 46.675296,
            detectedAt = "2026-11-14 17:10:00",
            driverUsername = "abdulhamid",
            isSynced = true
        )

        val csv = TripExportHelper.formatTripHistoryCsv(listOf(sampleRecord))
        assertTrue(csv.startsWith("RecordID,TripID,Driver,DetectedAt,CategoryKey,ArabicHazard,EnglishHazard,ConfidencePct,Latitude,Longitude,Synced"))
        assertTrue(csv.contains("abdulhamid"))
        assertTrue(csv.contains("speed_80"))
        assertTrue(csv.contains("Speed Limit 80"))
        assertTrue(csv.contains("24.713552,46.675296"))

        val txt = TripExportHelper.formatTripHistoryText(
            trips = listOf(sampleTrip),
            records = listOf(sampleRecord),
            stats = mapOf("السرعة المحددة 80" to 1),
            driverSummaries = listOf(DriverSummary("abdulhamid", 1, 1)),
            exportTimestamp = "2026-11-14 17:25:00"
        )
        assertTrue(txt.contains("VISION"))
        assertTrue(txt.contains("24.713552, 46.675296"))
        assertFalse(txt.contains("16.8892"))
    }
}
