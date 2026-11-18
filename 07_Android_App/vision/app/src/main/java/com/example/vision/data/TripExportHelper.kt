package com.example.vision.data

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.vision.ui.AppStrings
import java.io.File
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared formatting, file export (Downloads), and FileProvider sharing utilities for VISION trip logs.
 */
object TripExportHelper {

    private const val UTF8_BOM = "\uFEFF"

    fun formatCoordsDisplay(latitude: Double, longitude: Double, isArabic: Boolean): String {
        return if (LocationTracker.isValidCoordinate(latitude, longitude)) {
            // Isolate coordinates in explicit LTR order so Arabic RTL layout never reverses lat and lon
            String.format(Locale.US, "\u2066%.6f, %.6f\u2069", latitude, longitude)
        } else {
            AppStrings.get("gps_unavailable", isArabic)
        }
    }

    fun formatCoordsPlain(latitude: Double, longitude: Double): String {
        return if (LocationTracker.isValidCoordinate(latitude, longitude)) {
            String.format(Locale.US, "%.6f, %.6f", latitude, longitude)
        } else {
            "N/A"
        }
    }

    fun formatTripHistoryText(
        trips: List<TripSession>,
        records: List<DetectionRecord>,
        stats: Map<String, Int>,
        driverSummaries: List<DriverSummary>,
        exportTimestamp: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    ): String {
        val sb = StringBuilder()
        sb.appendLine("=========================================================")
        sb.appendLine("   نظام VISION - تقرير سجل الرحلات وتحليلات السلامة")
        sb.appendLine("   VISION ADAS - Trip Log & Safety Report")
        sb.appendLine("   تاريخ التصدير / Exported: $exportTimestamp")
        sb.appendLine("=========================================================")
        sb.appendLine()
        sb.appendLine("1. ملخص النظام العام / System Summary:")
        sb.appendLine(" - إجمالي عدد الرحلات / Total Trips: ${trips.size}")
        sb.appendLine(" - إجمالي الأحداث المرصودة / Total Detections: ${records.size}")
        sb.appendLine(" - عدد حسابات السائقين النشطة / Active Drivers: ${driverSummaries.size}")
        sb.appendLine()
        sb.appendLine("2. تفاصيل نشاط السائقين / Driver Activity:")
        if (driverSummaries.isEmpty()) {
            sb.appendLine(" (لا توجد بيانات سائقين مسجلة / No recorded driver data)")
        } else {
            driverSummaries.forEach { ds ->
                sb.appendLine(" • السائق [${ds.username}] | الرحلات: ${ds.tripCount} | الرصدات: ${ds.hazardCount}")
            }
        }
        sb.appendLine()
        sb.appendLine("3. إحصائيات المخاطر حسب الفئات / Hazard Distribution:")
        if (stats.isEmpty()) {
            sb.appendLine(" (لا توجد إحصائيات مسجلة / No statistics recorded)")
        } else {
            stats.forEach { (cat, count) ->
                sb.appendLine(" - $cat: $count")
            }
        }
        sb.appendLine()
        sb.appendLine("4. تفاصيل السجلات الزمنية / Detection Timeline:")
        sb.appendLine("---------------------------------------------------------")
        if (records.isEmpty()) {
            sb.appendLine(" (لا توجد سجلات رصد في النطاق المحدد / No detections recorded)")
        } else {
            records.forEachIndexed { index, r ->
                val coordsText = if (LocationTracker.isValidCoordinate(r.latitude, r.longitude)) {
                    String.format(Locale.US, "%.6f, %.6f", r.latitude, r.longitude)
                } else {
                    "غير متاح (GPS Unavailable)"
                }
                sb.appendLine("[${index + 1}] الوقت / Time: ${r.detectedAt} (Trip #${r.tripId})")
                sb.appendLine("    السائق / Driver: ${r.driverUsername}")
                sb.appendLine("    النوع / Hazard: ${r.arabicType} (${r.objectType})")
                sb.appendLine("    نسبة الثقة / Confidence: ${(r.confidence * 100).toInt()}%")
                sb.appendLine("    الإحداثيات / GPS: $coordsText")
                sb.appendLine("---------------------------------------------------------")
            }
        }
        return sb.toString()
    }

    fun formatTripHistoryCsv(records: List<DetectionRecord>): String {
        val sb = StringBuilder()
        sb.appendLine("RecordID,TripID,Driver,DetectedAt,CategoryKey,ArabicHazard,EnglishHazard,ConfidencePct,Latitude,Longitude,Synced")
        records.forEach { r ->
            val enName = HazardCatalog.getLocalizedTitle(r.objectType, isArabic = false)
            val latStr = if (LocationTracker.isValidCoordinate(r.latitude, r.longitude)) {
                String.format(Locale.US, "%.6f", r.latitude)
            } else {
                ""
            }
            val lonStr = if (LocationTracker.isValidCoordinate(r.latitude, r.longitude)) {
                String.format(Locale.US, "%.6f", r.longitude)
            } else {
                ""
            }
            val confPct = (r.confidence * 100).toInt()
            sb.appendLine(
                listOf(
                    r.recordId.toString(),
                    r.tripId.toString(),
                    csvCell(r.driverUsername),
                    csvCell(r.detectedAt),
                    csvCell(r.objectType),
                    csvCell(r.arabicType),
                    csvCell(enName),
                    confPct.toString(),
                    latStr,
                    lonStr,
                    r.isSynced.toString()
                ).joinToString(",")
            )
        }
        return sb.toString()
    }

    private fun csvCell(value: String): String {
        val clean = value.replace("\r", " ").replace("\n", " ")
        return "\"${clean.replace("\"", "\"\"")}\""
    }

    fun timestampTag(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    fun exportCsvToDownloads(context: Context, csvContent: String, isArabic: Boolean) {
        val fileName = "VISION_Trips_${timestampTag()}.csv"
        saveToDownloads(context, csvContent, fileName, "text/csv")
            .onSuccess { savedPath ->
                Toast.makeText(
                    context,
                    "${AppStrings.get("toast_export_saved", isArabic)} $savedPath",
                    Toast.LENGTH_LONG
                ).show()
            }
            .onFailure {
                Toast.makeText(context, AppStrings.get("toast_export_failed", isArabic), Toast.LENGTH_SHORT).show()
            }
    }

    fun saveToDownloads(
        context: Context,
        content: String,
        fileName: String,
        mimeType: String
    ): Result<String> {
        return try {
            val payload = (UTF8_BOM + content).toByteArray(StandardCharsets.UTF_8)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, mimeType)
                    put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/VISION")
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                    ?: return Result.failure(IllegalStateException("MediaStore returned null URI"))

                resolver.openOutputStream(uri)?.use { out ->
                    out.write(payload)
                    out.flush()
                } ?: return Result.failure(IllegalStateException("Unable to open Downloads output stream"))

                values.clear()
                values.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                Result.success("Download/VISION/$fileName")
            } else {
                val dir = File(
                    context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                    "VISION"
                ).apply { mkdirs() }
                val outFile = File(dir, fileName)
                outFile.writeBytes(payload)
                Result.success(outFile.absolutePath)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun shareReportFile(
        context: Context,
        content: String,
        fileName: String,
        mimeType: String,
        chooserTitle: String,
        isArabic: Boolean
    ) {
        try {
            val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(exportsDir, fileName)
            file.writeBytes((UTF8_BOM + content).toByteArray(StandardCharsets.UTF_8))

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, chooserTitle)
                putExtra(Intent.EXTRA_TEXT, content.take(2000))
                clipData = ClipData.newUri(context.contentResolver, chooserTitle, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(sendIntent, chooserTitle).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            val savedPath = saveToDownloads(context, content, fileName, mimeType).getOrNull()
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            clipboard?.setPrimaryClip(ClipData.newPlainText(chooserTitle, content))
            val msg = if (savedPath != null) {
                if (isArabic) "تم حفظ التقرير في التنزيلات ونسخه: $savedPath" else "Report saved to $savedPath and copied!"
            } else {
                if (isArabic) "تم نسخ التقرير للحافظة مباشرة!" else "Report copied to clipboard!"
            }
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
}
