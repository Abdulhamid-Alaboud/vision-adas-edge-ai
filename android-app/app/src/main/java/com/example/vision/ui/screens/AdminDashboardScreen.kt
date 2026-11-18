package com.example.vision.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vision.data.CloudSyncManager
import com.example.vision.data.DatabaseHelper
import com.example.vision.data.DriverSummary
import com.example.vision.data.HazardCatalog
import com.example.vision.data.TripExportHelper
import com.example.vision.ui.AppStrings
import kotlinx.coroutines.launch

@Composable
fun AdminDashboardScreen(
    dbHelper: DatabaseHelper,
    cloudSyncManager: CloudSyncManager,
    currentThreshold: Float,
    onThresholdChanged: (Float) -> Unit,
    isArabic: Boolean = true,
    onLogout: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshTrigger by remember { mutableStateOf(0) }
    val distribution = remember(refreshTrigger) { dbHelper.getHazardDistribution() }
    val totalTrips = remember(refreshTrigger) { dbHelper.getAllTrips().size }
    val totalDetections = remember(refreshTrigger) { dbHelper.getAllDetections(1000).size }
    val localDriverSummaries = remember(refreshTrigger) { dbHelper.getDriverSummaries() }

    var isOnline by remember { mutableStateOf(cloudSyncManager.isNetworkAvailable()) }
    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var cloudDrivers by remember { mutableStateOf<List<DriverSummary>>(emptyList()) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var previewReportText by remember { mutableStateOf<String?>(null) }

    fun exportAndShare(reportContent: String, title: String, extension: String = "txt", mimeType: String = "text/plain") {
        val fileName = "VISION_Report_${TripExportHelper.timestampTag()}.$extension"
        TripExportHelper.shareReportFile(
            context = context,
            content = reportContent,
            fileName = fileName,
            mimeType = mimeType,
            chooserTitle = title,
            isArabic = isArabic
        )
    }

    fun copyToClipboard(content: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("VISION Report", content)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, AppStrings.get("toast_report_copied", isArabic), Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        isOnline = cloudSyncManager.isNetworkAvailable()
        if (isOnline) {
            val unsynced = dbHelper.getUnsyncedDetections(200)
            if (unsynced.isNotEmpty()) {
                val syncedIds = cloudSyncManager.uploadDetectionsBatch(unsynced)
                dbHelper.markDetectionsAsSynced(syncedIds)
            }
            val res = cloudSyncManager.fetchCloudFleetSummaries()
            res.getOrNull()?.let { cloudDrivers = it }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E17))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = AppStrings.get("back", isArabic),
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = AppStrings.get("admin_screen_title", isArabic),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = { showLogoutConfirmDialog = true },
                modifier = Modifier.background(Color(0xFFDC2626).copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = AppStrings.get("btn_logout", isArabic),
                    tint = Color(0xFFEF4444)
                )
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color(0xFFE50914).copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color(0xFFE50914), modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = AppStrings.get("admin_screen_title", isArabic),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = AppStrings.get("admin_screen_subtitle", isArabic),
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // System Overview Stat Card
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(AppStrings.get("total_trips_stat", isArabic), color = Color.LightGray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$totalTrips", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(AppStrings.get("total_detections_stat", isArabic), color = Color.LightGray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("$totalDetections", color = Color(0xFF00E5FF), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(AppStrings.get("registered_drivers", isArabic), color = Color.LightGray, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("${localDriverSummaries.size}", color = Color(0xFF00E676), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Dynamic Per-Class Confidence Calibration Matrix Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF162032)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = Color(0xFF00E5FF))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isArabic) "مصفوفة المعايرة المتقدمة لكل فئة (Per-Class Matrix)" else "Per-Class Confidence Calibration Matrix",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (isArabic)
                        "تمت معايرة نسب الثقة لكل فئة بدقة منفصلة بناءً على نتائج التدريب الميداني لمنع الإنذارات الكاذبة وتعزيز دقة الرصد:"
                    else
                        "Confidence thresholds dynamically calibrated per class from validation Pareto curves to eliminate false alarms and maximize precision:",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                HazardCatalog.CLASSES.values.forEachIndexed { idx, meta ->
                    val thresh = HazardCatalog.classConfidenceOverrides[meta.id] ?: 0.38f
                    val threshPct = (thresh * 100f).toInt()
                    val tagText = when (meta.id) {
                        1 -> if (isArabic) "رصد فوري مع فلتر الإسطبات ($threshPct%)" else "Instant Detection & Taillight Filter ($threshPct%)"
                        2 -> if (isArabic) "حماية ضد الإنذارات الخاطئة ($threshPct%)" else "False Alarm Suppression ($threshPct%)"
                        3 -> if (isArabic) "رصد آمن ومستقر للمشاة ($threshPct%)" else "Stabilized Pedestrian Filter ($threshPct%)"
                        11 -> if (isArabic) "لوحة المطب التحذيرية ($threshPct%)" else "Speed Bump Calibration ($threshPct%)"
                        12 -> if (isArabic) "حماية لوحة قف مع OCR ($threshPct%)" else "Stop Sign + OCR Calibration ($threshPct%)"
                        13 -> if (isArabic) "تحقق لوني لإشارات المرور ($threshPct%)" else "Traffic Signal Chromatic Filter ($threshPct%)"
                        in listOf(5, 6, 7, 8, 9, 10) -> if (isArabic) "تحقق هجين للوحات السرعة ($threshPct%)" else "Hybrid Speed Sign Calibration ($threshPct%)"
                        in 14..19 -> if (isArabic) "سرعة مدعومة بالـ OCR ($threshPct%)" else "Zero-Shot OCR Speed Limit ($threshPct%)"
                        else -> if (isArabic) "معايرة متوازنة ($threshPct%)" else "Calibrated Precision ($threshPct%)"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${idx + 1}. ${meta.title(isArabic)}",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = tagText,
                                color = Color(0xFF00E5FF).copy(alpha = 0.8f),
                                fontSize = 11.sp
                            )
                        }
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF00E5FF).copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$threshPct%",
                                color = Color(0xFF00E5FF),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (idx < HazardCatalog.CLASSES.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color(0xFF24334A))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // System & Model Architecture Specs Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF162032)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = Color(0xFFFF9100))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppStrings.get("model_specs_title", isArabic),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                val specs = listOf(
                    AppStrings.get("spec_model_name", isArabic) to Color(0xFF00E5FF),
                    AppStrings.get("spec_architecture", isArabic) to Color.White,
                    AppStrings.get("spec_params", isArabic) to Color(0xFF00E676),
                    AppStrings.get("spec_input", isArabic) to Color.LightGray,
                    AppStrings.get("spec_output", isArabic) to Color.LightGray,
                    AppStrings.get("spec_classes", isArabic) to Color(0xFFFF9100),
                    AppStrings.get("spec_storage", isArabic) to Color.LightGray,
                    (if (isArabic) "• خصوصية الضيف: عزل كامل لجلسات الضيوف (لا يتم تسجيلها للوحة التحكم)"
                     else "• Guest Privacy: Full Isolation (Guest driving data is never stored)") to Color(0xFF00E5FF)
                )

                specs.forEachIndexed { idx, (specText, specColor) ->
                    Text(
                        text = specText,
                        color = specColor,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontWeight = if (idx == 0 || idx == 2) FontWeight.Bold else FontWeight.Normal
                    )
                    if (idx < specs.size - 1) {
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Traffic Categories Log Card
        Text(
            text = AppStrings.get("hazards_catalog_title", isArabic),
            color = Color(0xFF00E5FF),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                HazardCatalog.CLASSES.values.forEachIndexed { index, meta ->
                    val count = distribution[meta.arabicTitle] ?: distribution[meta.label] ?: 0
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "${index + 1}. ${meta.title(isArabic)}", color = Color.White, fontSize = 13.sp)
                        Text(
                            text = "$count ${AppStrings.get("detections_count", isArabic)}",
                            color = if (count > 0) Color(0xFF00E5FF) else Color.Gray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (index < HazardCatalog.CLASSES.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp), color = Color(0xFF334155))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Export & Cloud Reports Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF162032)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color(0xFF00E676))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppStrings.get("export_cloud_section", isArabic),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = {
                        val report = dbHelper.exportTripHistoryText()
                        exportAndShare(report, "VISION ADAS Report")
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = AppStrings.get("btn_export_txt", isArabic), color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val csv = dbHelper.exportTripHistoryCsv()
                        TripExportHelper.exportCsvToDownloads(context, csv, isArabic)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0EA5E9)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = AppStrings.get("btn_export_csv", isArabic), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        scope.launch {
                            isSyncing = true
                            val result = cloudSyncManager.generateUnifiedCloudReport()
                            isSyncing = false
                            result.onSuccess { report ->
                                exportAndShare(report, "VISION Unified Cloud Fleet Report")
                            }.onFailure { err ->
                                Toast.makeText(
                                    context,
                                    err.message ?: AppStrings.get("toast_no_data", isArabic),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    enabled = !isSyncing,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = AppStrings.get("btn_export_cloud", isArabic), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val report = dbHelper.exportTripHistoryText()
                            copyToClipboard(report)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = AppStrings.get("btn_copy_report", isArabic), color = Color.White, fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            previewReportText = dbHelper.exportTripHistoryText()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = AppStrings.get("preview_report_title", isArabic), color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Reset History Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF221518)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color(0xFFFF5252))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppStrings.get("dialog_clear_title", isArabic),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = AppStrings.get("dialog_clear_msg", isArabic),
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { showResetConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = AppStrings.get("btn_clear_history", isArabic), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text(AppStrings.get("dialog_clear_title", isArabic), fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text(AppStrings.get("dialog_clear_msg", isArabic), color = Color.LightGray) },
            containerColor = Color(0xFF1E293B),
            confirmButton = {
                Button(
                    onClick = {
                        dbHelper.clearAllHistory()
                        refreshTrigger++
                        showResetConfirmDialog = false
                        Toast.makeText(context, AppStrings.get("toast_history_cleared", isArabic), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text(AppStrings.get("btn_confirm_clear", isArabic), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text(AppStrings.get("cancel", isArabic), color = Color.White)
                }
            }
        )
    }

    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = { Text(AppStrings.get("dialog_logout_title", isArabic), fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text(AppStrings.get("dialog_logout_msg", isArabic), color = Color.LightGray) },
            containerColor = Color(0xFF1E293B),
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text(AppStrings.get("btn_logout_confirm", isArabic), color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text(AppStrings.get("cancel", isArabic), color = Color.White)
                }
            }
        )
    }

    if (previewReportText != null) {
        AlertDialog(
            onDismissRequest = { previewReportText = null },
            title = { Text(AppStrings.get("preview_report_title", isArabic), color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(text = previewReportText ?: "", color = Color.LightGray, fontSize = 11.sp)
                }
            },
            containerColor = Color(0xFF1E293B),
            confirmButton = {
                Button(onClick = { previewReportText = null }) {
                    Text(AppStrings.get("close", isArabic))
                }
            }
        )
    }
}
