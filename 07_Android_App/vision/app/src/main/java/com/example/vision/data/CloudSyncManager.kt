package com.example.vision.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.vision.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit


class CloudSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "CloudSyncManager"
        private val DEFAULT_CLOUD_BASE_URL = BuildConfig.CLOUD_BASE_URL
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .build()

    var cloudBaseUrl: String = DEFAULT_CLOUD_BASE_URL
        get() = field.trimEnd('/')
        set(value) {
            field = if (value.isNotBlank()) value.trimEnd('/') else DEFAULT_CLOUD_BASE_URL
        }

    /**
     * Check if device currently has an active internet connection.
     */
    fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Check if a username is available globally across all devices.
     * Returns true if username is available (NOT taken).
     * Returns false if username is already registered in the cloud.
     * Throws exception on network failure.
     */
    suspend fun checkUsernameAvailability(username: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim().lowercase(Locale.ROOT)
        if (cleanUser.length < 3) {
            return@withContext Result.failure(Exception("اسم المستخدم يجب أن يتكون من 3 أحرف على الأقل"))
        }
        if (cleanUser == "admin") {
            return@withContext Result.failure(Exception("اسم المستخدم 'admin' محجوز للإدارة والمطورين"))
        }
        if (cleanUser.contains(Regex("[.#$\\[\\]/ ]"))) {
            return@withContext Result.failure(Exception("اسم المستخدم يجب ألا يحتوي على مسافات أو رموز خاصة مثل (. # $ [ ] /)"))
        }

        if (!isNetworkAvailable()) {
            return@withContext Result.failure(Exception("لا يوجد اتصال بالإنترنت للتحقق من فرادة اسم المستخدم في السحابة"))
        }

        try {
            val url = "$cloudBaseUrl/users/$cleanUser.json"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("استجابة السحابة غير ناجحة: ${response.code}"))
                }
                val body = response.body?.string()?.trim()
                val isAvailable = body.isNullOrBlank() || body == "null"
                Result.success(isAvailable)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check username availability", e)
            Result.failure(Exception("تعذر الاتصال بالسحابة: ${e.localizedMessage ?: "خطأ في الشبكة"}"))
        }
    }

    suspend fun registerUserCloud(username: String, passwordHash: String, role: String = "Driver"): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim().lowercase(Locale.ROOT)
        if (cleanUser.length < 3 || cleanUser.contains(Regex("[.#$\\[\\]/ ]"))) {
            return@withContext Result.failure(Exception("اسم المستخدم غير صالح أو يحتوي على رموز غير مسموحة"))
        }
        if (!isNetworkAvailable()) {
            return@withContext Result.failure(Exception("لا يوجد اتصال بالإنترنت لحفظ الحساب في السحابة"))
        }

        try {
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            val jsonPayload = JSONObject().apply {
                put("username", cleanUser)
                put("password_hash", passwordHash)
                put("role", role)
                put("registered_at", timestamp)
            }.toString()

            val url = "$cloudBaseUrl/users/$cleanUser.json"
            val body = jsonPayload.toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder().url(url).put(body).build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("فشل تسجيل الحساب السحابي: رمز ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud user registration failed", e)
            Result.failure(e)
        }
    }

    suspend fun authenticateCloud(username: String, passwordHash: String): Result<User?> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim().lowercase(Locale.ROOT)
        if (cleanUser.isBlank() || cleanUser.contains(Regex("[.#$\\[\\]/ ]"))) {
            return@withContext Result.success(null)
        }
        if (!isNetworkAvailable()) {
            return@withContext Result.failure(Exception("لا يوجد اتصال بالإنترنت للتحقق السحابي"))
        }

        try {
            val url = "$cloudBaseUrl/users/$cleanUser.json"
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("خطأ في الاتصال بالسحابة: ${response.code}"))
                }
                val body = response.body?.string()?.trim()
                if (body.isNullOrBlank() || body == "null") {
                    return@withContext Result.success(null)
                }

                val obj = JSONObject(body)
                val storedHash = obj.optString("password_hash")
                val role = obj.optString("role", "Driver")

                if (storedHash == passwordHash) {
                    Result.success(User(username = cleanUser, passwordHash = storedHash, role = role))
                } else {
                    Result.success(null)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud authentication error", e)
            Result.failure(e)
        }
    }

    suspend fun updatePasswordCloud(username: String, passwordHash: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val cleanUser = username.trim().lowercase(Locale.ROOT)
        if (cleanUser.length < 3 || cleanUser.contains(Regex("[.#$\\[\\]/ ]"))) {
            return@withContext Result.failure(Exception("اسم المستخدم غير صالح"))
        }
        if (!isNetworkAvailable()) {
            return@withContext Result.failure(Exception("لا يوجد اتصال بالإنترنت لتحديث كلمة المرور في السحابة"))
        }

        try {
            val url = "$cloudBaseUrl/users/$cleanUser/password_hash.json"
            val body = "\"$passwordHash\"".toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder().url(url).put(body).build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("فشل تحديث كلمة المرور في السحابة: رمز ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud password update failed", e)
            Result.failure(e)
        }
    }

    /**
     * Upload a batch of unsynced detection records to the Cloud.
     * Skips guest detections so guest driving data is never uploaded.
     */
    suspend fun uploadDetectionsBatch(detections: List<DetectionRecord>): List<Long> = withContext(Dispatchers.IO) {
        if (detections.isEmpty() || !isNetworkAvailable()) return@withContext emptyList()

        val syncedIds = mutableListOf<Long>()
        for (record in detections) {
            if (record.driverUsername.lowercase() in listOf("guest", "ضيف")) {
                syncedIds.add(record.recordId) // Mark processed without cloud upload
                continue
            }

            try {
                val jsonPayload = JSONObject().apply {
                    put("record_id", record.recordId)
                    put("trip_id", record.tripId)
                    put("driver_username", record.driverUsername)
                    put("object_type", record.objectType)
                    put("arabic_type", record.arabicType)
                    put("confidence", record.confidence)
                    put("latitude", record.latitude)
                    put("longitude", record.longitude)
                    put("detected_at", record.detectedAt)
                }.toString()

                val url = "$cloudBaseUrl/detections.json"
                val body = jsonPayload.toRequestBody(JSON_MEDIA_TYPE)
                val request = Request.Builder().url(url).post(body).build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        syncedIds.add(record.recordId)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to upload detection ${record.recordId}: ${e.message}")
                break
            }
        }
        syncedIds
    }

    /**
     * Fetch driver activity summaries from the Cloud for all registered drivers across all devices.
     */
    suspend fun fetchCloudFleetSummaries(): Result<List<DriverSummary>> = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            return@withContext Result.failure(Exception("لا يوجد اتصال بالإنترنت لجلب بيانات أسطول السائقين"))
        }

        try {
            // 1. Fetch Users
            val usersUrl = "$cloudBaseUrl/users.json"
            val usersReq = Request.Builder().url(usersUrl).get().build()
            val driverNames = mutableSetOf<String>()

            client.newCall(usersReq).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()?.trim()
                    if (!body.isNullOrBlank() && body != "null") {
                        val json = JSONObject(body)
                        json.keys().forEach { k ->
                            val userObj = json.optJSONObject(k)
                            val role = userObj?.optString("role") ?: "Driver"
                            if (role.equals("Driver", ignoreCase = true) && k.lowercase() !in listOf("guest", "ضيف")) {
                                driverNames.add(k)
                            }
                        }
                    }
                }
            }

            // 2. Fetch Detections to aggregate count per driver
            val detUrl = "$cloudBaseUrl/detections.json"
            val detReq = Request.Builder().url(detUrl).get().build()
            val hazardCounts = mutableMapOf<String, Int>()
            val tripSets = mutableMapOf<String, MutableSet<Long>>()

            client.newCall(detReq).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()?.trim()
                    if (!body.isNullOrBlank() && body != "null") {
                        val json = JSONObject(body)
                        json.keys().forEach { k ->
                            val detObj = json.optJSONObject(k)
                            if (detObj != null) {
                                val driver = detObj.optString("driver_username", "driver")
                                if (driver.lowercase() !in listOf("guest", "ضيف")) {
                                    val tripId = detObj.optLong("trip_id", 0L)
                                    driverNames.add(driver)
                                    hazardCounts[driver] = (hazardCounts[driver] ?: 0) + 1
                                    if (tripId > 0) {
                                        tripSets.getOrPut(driver) { mutableSetOf() }.add(tripId)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val resultList = driverNames.map { driver ->
                DriverSummary(
                    username = driver,
                    tripCount = tripSets[driver]?.size ?: (if ((hazardCounts[driver] ?: 0) > 0) 1 else 0),
                    hazardCount = hazardCounts[driver] ?: 0
                )
            }.sortedByDescending { it.hazardCount }

            Result.success(resultList)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch cloud fleet summaries", e)
            Result.failure(e)
        }
    }

    /**
     * Generate a unified cloud report compiling detections from ALL drivers in the system.
     */
    suspend fun generateUnifiedCloudReport(): Result<String> = withContext(Dispatchers.IO) {
        if (!isNetworkAvailable()) {
            return@withContext Result.failure(Exception("لا يوجد اتصال بالإنترنت لتصدير التقرير السحابي الموحد"))
        }

        try {
            val detUrl = "$cloudBaseUrl/detections.json"
            val detReq = Request.Builder().url(detUrl).get().build()
            val detections = mutableListOf<JSONObject>()

            client.newCall(detReq).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("فشل جلب السجلات من السحابة: ${response.code}"))
                }
                val body = response.body?.string()?.trim()
                if (!body.isNullOrBlank() && body != "null") {
                    val json = JSONObject(body)
                    json.keys().forEach { k ->
                        json.optJSONObject(k)?.let { det ->
                            val driver = det.optString("driver_username", "driver")
                            if (driver.lowercase() !in listOf("guest", "ضيف")) {
                                detections.add(det)
                            }
                        }
                    }
                }
            }

            // Also fetch drivers summary
            val summariesResult = fetchCloudFleetSummaries()
            val summaries = summariesResult.getOrNull() ?: emptyList()

            val sb = StringBuilder()
            val now = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
            sb.appendLine("=========================================================")
            sb.appendLine("   نظام VISION - التقرير السحابي الموحد لأسطول السائقين")
            sb.appendLine("   تاريخ الاستخراج السحابي: $now")
            sb.appendLine("   خادم السحابة: $cloudBaseUrl")
            sb.appendLine("=========================================================")
            sb.appendLine()
            sb.appendLine("1. ملخص الأسطول السحابي العام:")
            sb.appendLine(" - إجمالي الأحداث المرصودة سحابياً: ${detections.size}")
            sb.appendLine(" - عدد حسابات السائقين المسجلين في السحابة: ${summaries.size}")
            sb.appendLine()
            sb.appendLine("2. تفصيل نشاط السائقين عبر كافة الهواتف:")
            if (summaries.isEmpty()) {
                sb.appendLine(" (لا توجد بيانات سائقين مسجلة)")
            } else {
                summaries.forEach { ds ->
                    sb.appendLine(" • السائق: [${ds.username}] | عدد الرحلات: ${ds.tripCount} | إجمالي الرصدات: ${ds.hazardCount}")
                }
            }
            sb.appendLine()
            sb.appendLine("3. تفاصيل الرصد الميداني المرفوع للسحابة:")
            sb.appendLine("---------------------------------------------------------")
            detections.reversed().take(500).forEachIndexed { index, det ->
                val time = det.optString("detected_at", "-")
                val driver = det.optString("driver_username", "driver")
                val arabic = det.optString("arabic_type", "-")
                val conf = (det.optDouble("confidence", 0.0) * 100).toInt()
                val lat = det.optDouble("latitude", 0.0)
                val lon = det.optDouble("longitude", 0.0)
                sb.appendLine("[${index + 1}] الوقت: $time")
                sb.appendLine("    السائق: $driver")
                sb.appendLine("    نوع الرصد: $arabic")
                sb.appendLine("    نسبة الثقة: $conf%")
                sb.appendLine("    الإحداثيات: خط عرض $lat ، خط طول $lon")
                sb.appendLine("---------------------------------------------------------")
            }

            Result.success(sb.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate unified cloud report", e)
            Result.failure(e)
        }
    }
}
