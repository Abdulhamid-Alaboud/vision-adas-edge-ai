package com.example.vision.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "vision_local.db"
        const val DATABASE_VERSION = 4

        const val TABLE_TRIP = "TRIP"
        const val COL_TRIP_ID = "trip_id"
        const val COL_TRIP_DRIVER = "driver_username"
        const val COL_START_TIME = "start_time"
        const val COL_END_TIME = "end_time"
        const val COL_TOTAL_HAZARDS = "total_hazards"

        const val TABLE_DETECTION = "DETECTION_RECORD"
        const val COL_RECORD_ID = "record_id"
        const val COL_DET_TRIP_ID = "trip_id"
        const val COL_OBJECT_TYPE = "object_type"
        const val COL_ARABIC_TYPE = "arabic_type"
        const val COL_CONFIDENCE = "confidence"
        const val COL_LATITUDE = "latitude"
        const val COL_LONGITUDE = "longitude"
        const val COL_DETECTED_AT = "detected_at"
        const val COL_IS_SYNCED = "is_synced"

        const val TABLE_PREFERENCES = "USER_PREFERENCES"
        const val COL_PREF_ID = "pref_id"
        const val COL_ALERT_VOLUME = "alert_volume"
        const val COL_DETECTION_THRESHOLD = "detection_threshold"
        const val COL_ENABLED_CLASSES = "enabled_classes"

        const val TABLE_USERS = "USERS"
        const val COL_USER_ID = "user_id"
        const val COL_USERNAME = "username"
        const val COL_PASSWORD_HASH = "password_hash"
        const val COL_ROLE = "role"

        fun hashPassword(password: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE $TABLE_TRIP (
                $COL_TRIP_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TRIP_DRIVER TEXT DEFAULT 'driver',
                $COL_START_TIME TEXT NOT NULL,
                $COL_END_TIME TEXT,
                $COL_TOTAL_HAZARDS INTEGER DEFAULT 0
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_DETECTION (
                $COL_RECORD_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_DET_TRIP_ID INTEGER NOT NULL,
                $COL_OBJECT_TYPE TEXT NOT NULL,
                $COL_ARABIC_TYPE TEXT NOT NULL,
                $COL_CONFIDENCE REAL NOT NULL,
                $COL_LATITUDE REAL NOT NULL,
                $COL_LONGITUDE REAL NOT NULL,
                $COL_DETECTED_AT TEXT NOT NULL,
                $COL_IS_SYNCED INTEGER DEFAULT 0,
                FOREIGN KEY ($COL_DET_TRIP_ID) REFERENCES $TABLE_TRIP($COL_TRIP_ID) ON DELETE CASCADE
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_PREFERENCES (
                $COL_PREF_ID INTEGER PRIMARY KEY,
                $COL_ALERT_VOLUME INTEGER DEFAULT 100,
                $COL_DETECTION_THRESHOLD REAL DEFAULT 0.38,
                $COL_ENABLED_CLASSES TEXT DEFAULT '0,1,2,3,4,5,6,7,8,9,10,11,12,13'
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE $TABLE_USERS (
                $COL_USER_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_USERNAME TEXT UNIQUE NOT NULL,
                $COL_PASSWORD_HASH TEXT NOT NULL,
                $COL_ROLE TEXT NOT NULL
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO $TABLE_PREFERENCES ($COL_PREF_ID, $COL_ALERT_VOLUME, $COL_DETECTION_THRESHOLD, $COL_ENABLED_CLASSES)
            VALUES (1, 100, 0.38, '0,1,2,3,4,5,6,7,8,9,10,11,12,13')
        """.trimIndent())

        val adminPass = hashPassword("admin123")
        val driverPass = hashPassword("123456")
        db.execSQL("INSERT INTO $TABLE_USERS ($COL_USERNAME, $COL_PASSWORD_HASH, $COL_ROLE) VALUES ('admin', '$adminPass', 'Administrator')")
        db.execSQL("INSERT INTO $TABLE_USERS ($COL_USERNAME, $COL_PASSWORD_HASH, $COL_ROLE) VALUES ('driver', '$driverPass', 'Driver')")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) {
            try {
                db.execSQL("ALTER TABLE $TABLE_TRIP ADD COLUMN $COL_TRIP_DRIVER TEXT DEFAULT 'driver'")
            } catch (_: Exception) {}
        }
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE $TABLE_DETECTION ADD COLUMN $COL_IS_SYNCED INTEGER DEFAULT 0")
            } catch (_: Exception) {}
        }
    }

    private fun getCurrentTimestamp(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
    }

    fun startTrip(driverUsername: String = "driver"): Long {
        if (driverUsername.lowercase() in listOf("guest", "ضيف")) {
            return 0L
        }
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TRIP_DRIVER, driverUsername)
            put(COL_START_TIME, getCurrentTimestamp())
            put(COL_TOTAL_HAZARDS, 0)
        }
        return db.insert(TABLE_TRIP, null, values)
    }

    fun endTrip(tripId: Long) {
        if (tripId <= 0L) return
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_END_TIME, getCurrentTimestamp())
        }
        db.update(TABLE_TRIP, values, "$COL_TRIP_ID = ?", arrayOf(tripId.toString()))
    }

    fun insertDetection(tripId: Long, item: DetectionItem, latitude: Double = 0.0, longitude: Double = 0.0): Long {
        if (tripId <= 0L) return -1L
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_DET_TRIP_ID, tripId)
            put(COL_OBJECT_TYPE, item.className)
            put(COL_ARABIC_TYPE, item.arabicName)
            put(COL_CONFIDENCE, item.confidence)
            put(COL_LATITUDE, latitude)
            put(COL_LONGITUDE, longitude)
            put(COL_DETECTED_AT, getCurrentTimestamp())
        }
        val id = db.insert(TABLE_DETECTION, null, values)
        if (id > 0 && tripId > 0) {
            db.execSQL("UPDATE $TABLE_TRIP SET $COL_TOTAL_HAZARDS = $COL_TOTAL_HAZARDS + 1 WHERE $COL_TRIP_ID = $tripId")
        }
        return id
    }

    fun getAllDetections(limit: Int = 200, driverFilter: String? = null): List<DetectionRecord> {
        val list = mutableListOf<DetectionRecord>()
        val db = readableDatabase
        val query = if (driverFilter != null) {
            """
                SELECT d.*, COALESCE(t.$COL_TRIP_DRIVER, 'driver') AS driver_username
                FROM $TABLE_DETECTION d
                LEFT JOIN $TABLE_TRIP t ON d.$COL_DET_TRIP_ID = t.$COL_TRIP_ID
                WHERE t.$COL_TRIP_DRIVER = ?
                ORDER BY d.$COL_RECORD_ID DESC LIMIT ?
            """.trimIndent()
        } else {
            """
                SELECT d.*, COALESCE(t.$COL_TRIP_DRIVER, 'driver') AS driver_username
                FROM $TABLE_DETECTION d
                LEFT JOIN $TABLE_TRIP t ON d.$COL_DET_TRIP_ID = t.$COL_TRIP_ID
                ORDER BY d.$COL_RECORD_ID DESC LIMIT ?
            """.trimIndent()
        }
        val args = if (driverFilter != null) arrayOf(driverFilter, limit.toString()) else arrayOf(limit.toString())
        val cursor = db.rawQuery(query, args)
        cursor.use {
            while (it.moveToNext()) {
                val driverColIdx = it.getColumnIndex("driver_username")
                val driver = if (driverColIdx != -1) it.getString(driverColIdx) ?: "driver" else "driver"
                val syncIdx = it.getColumnIndex(COL_IS_SYNCED)
                val isSynced = if (syncIdx != -1) it.getInt(syncIdx) == 1 else false
                list.add(
                    DetectionRecord(
                        recordId = it.getLong(it.getColumnIndexOrThrow(COL_RECORD_ID)),
                        tripId = it.getLong(it.getColumnIndexOrThrow(COL_DET_TRIP_ID)),
                        objectType = it.getString(it.getColumnIndexOrThrow(COL_OBJECT_TYPE)),
                        arabicType = it.getString(it.getColumnIndexOrThrow(COL_ARABIC_TYPE)),
                        confidence = it.getFloat(it.getColumnIndexOrThrow(COL_CONFIDENCE)),
                        latitude = it.getDouble(it.getColumnIndexOrThrow(COL_LATITUDE)),
                        longitude = it.getDouble(it.getColumnIndexOrThrow(COL_LONGITUDE)),
                        detectedAt = it.getString(it.getColumnIndexOrThrow(COL_DETECTED_AT)),
                        driverUsername = driver,
                        isSynced = isSynced
                    )
                )
            }
        }
        return list
    }

    fun getUnsyncedDetections(limit: Int = 100): List<DetectionRecord> {
        val list = mutableListOf<DetectionRecord>()
        val db = readableDatabase
        val query = """
            SELECT d.*, COALESCE(t.$COL_TRIP_DRIVER, 'driver') AS driver_username
            FROM $TABLE_DETECTION d
            LEFT JOIN $TABLE_TRIP t ON d.$COL_DET_TRIP_ID = t.$COL_TRIP_ID
            WHERE d.$COL_IS_SYNCED = 0
            ORDER BY d.$COL_RECORD_ID ASC LIMIT ?
        """.trimIndent()
        val cursor = db.rawQuery(query, arrayOf(limit.toString()))
        cursor.use {
            while (it.moveToNext()) {
                val driverColIdx = it.getColumnIndex("driver_username")
                val driver = if (driverColIdx != -1) it.getString(driverColIdx) ?: "driver" else "driver"
                list.add(
                    DetectionRecord(
                        recordId = it.getLong(it.getColumnIndexOrThrow(COL_RECORD_ID)),
                        tripId = it.getLong(it.getColumnIndexOrThrow(COL_DET_TRIP_ID)),
                        objectType = it.getString(it.getColumnIndexOrThrow(COL_OBJECT_TYPE)),
                        arabicType = it.getString(it.getColumnIndexOrThrow(COL_ARABIC_TYPE)),
                        confidence = it.getFloat(it.getColumnIndexOrThrow(COL_CONFIDENCE)),
                        latitude = it.getDouble(it.getColumnIndexOrThrow(COL_LATITUDE)),
                        longitude = it.getDouble(it.getColumnIndexOrThrow(COL_LONGITUDE)),
                        detectedAt = it.getString(it.getColumnIndexOrThrow(COL_DETECTED_AT)),
                        driverUsername = driver,
                        isSynced = false
                    )
                )
            }
        }
        return list
    }

    fun markDetectionsAsSynced(recordIds: List<Long>) {
        if (recordIds.isEmpty()) return
        val db = writableDatabase
        val inClause = recordIds.joinToString(",")
        db.execSQL("UPDATE $TABLE_DETECTION SET $COL_IS_SYNCED = 1 WHERE $COL_RECORD_ID IN ($inClause)")
    }

    fun getAllTrips(driverFilter: String? = null): List<TripSession> {
        val list = mutableListOf<TripSession>()
        val db = readableDatabase
        val query = if (driverFilter != null) {
            "SELECT * FROM $TABLE_TRIP WHERE $COL_TRIP_DRIVER = ? ORDER BY $COL_TRIP_ID DESC"
        } else {
            "SELECT * FROM $TABLE_TRIP ORDER BY $COL_TRIP_ID DESC"
        }
        val args = if (driverFilter != null) arrayOf(driverFilter) else null
        val cursor = db.rawQuery(query, args)
        cursor.use {
            while (it.moveToNext()) {
                val driverColIndex = it.getColumnIndex(COL_TRIP_DRIVER)
                val driver = if (driverColIndex != -1) it.getString(driverColIndex) ?: "driver" else "driver"
                list.add(
                    TripSession(
                        tripId = it.getLong(it.getColumnIndexOrThrow(COL_TRIP_ID)),
                        startTime = it.getString(it.getColumnIndexOrThrow(COL_START_TIME)),
                        endTime = it.getString(it.getColumnIndexOrThrow(COL_END_TIME)),
                        totalHazards = it.getInt(it.getColumnIndexOrThrow(COL_TOTAL_HAZARDS)),
                        driverUsername = driver
                    )
                )
            }
        }
        return list
    }

    fun getDriverSummaries(): List<DriverSummary> {
        val list = mutableListOf<DriverSummary>()
        val db = readableDatabase
        val query = """
            SELECT 
                COALESCE(t.$COL_TRIP_DRIVER, 'driver') AS driver,
                COUNT(DISTINCT t.$COL_TRIP_ID) AS trip_count,
                COUNT(d.$COL_RECORD_ID) AS hazard_count
            FROM $TABLE_TRIP t
            LEFT JOIN $TABLE_DETECTION d ON t.$COL_TRIP_ID = d.$COL_DET_TRIP_ID
            WHERE LOWER(COALESCE(t.$COL_TRIP_DRIVER, 'driver')) NOT IN ('guest', 'ضيف')
            GROUP BY driver
            ORDER BY hazard_count DESC
        """.trimIndent()
        val cursor = db.rawQuery(query, null)
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    DriverSummary(
                        username = it.getString(0) ?: "driver",
                        tripCount = it.getInt(1),
                        hazardCount = it.getInt(2)
                    )
                )
            }
        }
        return list
    }

    fun getHazardDistribution(tripId: Long? = null, excludeGuest: Boolean = false): Map<String, Int> {
        val distribution = mutableMapOf<String, Int>()
        val db = readableDatabase
        val query = when {
            tripId != null && tripId > 0 -> {
                "SELECT $COL_ARABIC_TYPE, COUNT(*) as cnt FROM $TABLE_DETECTION WHERE $COL_DET_TRIP_ID = ? GROUP BY $COL_ARABIC_TYPE ORDER BY cnt DESC"
            }
            excludeGuest -> {
                """
                    SELECT d.$COL_ARABIC_TYPE, COUNT(*) as cnt 
                    FROM $TABLE_DETECTION d
                    LEFT JOIN $TABLE_TRIP t ON d.$COL_DET_TRIP_ID = t.$COL_TRIP_ID
                    WHERE LOWER(COALESCE(t.$COL_TRIP_DRIVER, 'driver')) NOT IN ('guest', 'ضيف')
                    GROUP BY d.$COL_ARABIC_TYPE ORDER BY cnt DESC
                """.trimIndent()
            }
            else -> {
                "SELECT $COL_ARABIC_TYPE, COUNT(*) as cnt FROM $TABLE_DETECTION GROUP BY $COL_ARABIC_TYPE ORDER BY cnt DESC"
            }
        }
        val args = if (tripId != null && tripId > 0) arrayOf(tripId.toString()) else null
        val cursor = db.rawQuery(query, args)
        cursor.use {
            while (it.moveToNext()) {
                val name = it.getString(0)
                val count = it.getInt(1)
                distribution[name] = count
            }
        }
        return distribution
    }

    fun getTripHazardDistribution(tripId: Long): Map<String, Int> {
        return getHazardDistribution(tripId = tripId)
    }

    fun getLatestTripId(): Long {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT $COL_TRIP_ID FROM $TABLE_TRIP ORDER BY $COL_TRIP_ID DESC LIMIT 1", null)
        return cursor.use {
            if (it.moveToFirst()) it.getLong(0) else 0L
        }
    }

    fun clearAllHistory() {
        val db = writableDatabase
        db.execSQL("DELETE FROM $TABLE_DETECTION")
        db.execSQL("DELETE FROM $TABLE_TRIP")
    }

    fun clearGuestHistory() {
        val db = writableDatabase
        try {
            db.execSQL("""
                DELETE FROM $TABLE_DETECTION 
                WHERE $COL_DET_TRIP_ID IN (
                    SELECT $COL_TRIP_ID FROM $TABLE_TRIP 
                    WHERE LOWER(COALESCE($COL_TRIP_DRIVER, '')) IN ('guest', 'ضيف')
                )
            """.trimIndent())
            db.execSQL("DELETE FROM $TABLE_TRIP WHERE LOWER(COALESCE($COL_TRIP_DRIVER, '')) IN ('guest', 'ضيف')")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getUserPreferences(): UserPreferences {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_PREFERENCES WHERE $COL_PREF_ID = 1", null)
        cursor.use {
            if (it.moveToFirst()) {
                val volume = it.getInt(it.getColumnIndexOrThrow(COL_ALERT_VOLUME))
                val thresh = it.getFloat(it.getColumnIndexOrThrow(COL_DETECTION_THRESHOLD))
                val rawClasses = it.getString(it.getColumnIndexOrThrow(COL_ENABLED_CLASSES)) ?: "0,1,2,3,4,5,6,7,8,9,10,11,12,13"
                val classIds = rawClasses.split(",").mapNotNull { s -> s.trim().toIntOrNull() }.toSet()
                return UserPreferences(
                    prefId = 1,
                    alertVolume = volume,
                    detectionThreshold = thresh,
                    enabledClassIds = classIds
                )
            }
        }
        return UserPreferences()
    }

    fun updateUserPreferences(prefs: UserPreferences) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_ALERT_VOLUME, prefs.alertVolume)
            put(COL_DETECTION_THRESHOLD, prefs.detectionThreshold)
            put(COL_ENABLED_CLASSES, prefs.enabledClassIds.joinToString(","))
        }
        db.update(TABLE_PREFERENCES, values, "$COL_PREF_ID = 1", null)
    }

    fun authenticate(username: String, password: String): User? {
        val cleanUser = username.trim()
        val hash = hashPassword(password)
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT * FROM $TABLE_USERS WHERE LOWER($COL_USERNAME) = LOWER(?) AND $COL_PASSWORD_HASH = ?",
            arrayOf(cleanUser, hash)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return User(
                    userId = it.getLong(it.getColumnIndexOrThrow(COL_USER_ID)),
                    username = it.getString(it.getColumnIndexOrThrow(COL_USERNAME)),
                    passwordHash = it.getString(it.getColumnIndexOrThrow(COL_PASSWORD_HASH)),
                    role = it.getString(it.getColumnIndexOrThrow(COL_ROLE))
                )
            }
        }
        return null
    }

    fun registerDriver(username: String, password: String): Boolean {
        val cleanUser = username.trim()
        if (cleanUser.length < 3 || password.length < 4) return false
        if (cleanUser.equals("admin", ignoreCase = true)) return false

        val db = writableDatabase
        val checkCursor = db.rawQuery(
            "SELECT 1 FROM $TABLE_USERS WHERE LOWER($COL_USERNAME) = LOWER(?)",
            arrayOf(cleanUser)
        )
        val exists = checkCursor.use { it.moveToFirst() }
        if (exists) return false

        val values = ContentValues().apply {
            put(COL_USERNAME, cleanUser)
            put(COL_PASSWORD_HASH, hashPassword(password))
            put(COL_ROLE, "Driver")
        }
        val id = db.insert(TABLE_USERS, null, values)
        return id > 0
    }

    fun resetPassword(username: String, newPassword: String): Boolean {
        val cleanUser = username.trim()
        if (cleanUser.length < 3 || newPassword.length < 4) return false
        if (cleanUser.equals("admin", ignoreCase = true)) return false

        val db = writableDatabase
        val check = db.rawQuery("SELECT 1 FROM $TABLE_USERS WHERE LOWER($COL_USERNAME) = LOWER(?)", arrayOf(cleanUser))
        val exists = check.use { it.moveToFirst() }
        if (!exists) return false

        val values = ContentValues().apply {
            put(COL_PASSWORD_HASH, hashPassword(newPassword))
        }
        val rows = db.update(TABLE_USERS, values, "LOWER($COL_USERNAME) = LOWER(?)", arrayOf(cleanUser))
        return rows > 0
    }

    fun exportTripHistoryText(driverFilter: String? = null): String {
        val trips = getAllTrips(driverFilter).filter { it.driverUsername.lowercase() != "guest" && it.driverUsername != "ضيف" }
        val records = getAllDetections(500, driverFilter).filter { it.driverUsername.lowercase() != "guest" && it.driverUsername != "ضيف" }
        val stats = getHazardDistribution(excludeGuest = true)
        val driverSummaries = if (driverFilter == null) {
            getDriverSummaries()
        } else {
            listOf(DriverSummary(username = driverFilter, tripCount = trips.size, hazardCount = records.size))
        }
        return TripExportHelper.formatTripHistoryText(
            trips = trips,
            records = records,
            stats = stats,
            driverSummaries = driverSummaries
        )
    }

    fun exportTripHistoryCsv(driverFilter: String? = null): String {
        val records = getAllDetections(1000, driverFilter).filter {
            it.driverUsername.lowercase() != "guest" && it.driverUsername != "ضيف"
        }
        return TripExportHelper.formatTripHistoryCsv(records)
    }
}
