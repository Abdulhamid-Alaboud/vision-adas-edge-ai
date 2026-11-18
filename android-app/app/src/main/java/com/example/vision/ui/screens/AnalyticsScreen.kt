package com.example.vision.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vision.data.DatabaseHelper
import com.example.vision.data.HazardCatalog
import com.example.vision.ui.AppStrings

@Composable
fun AnalyticsScreen(
    dbHelper: DatabaseHelper,
    isArabic: Boolean = true,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedScope by remember { mutableIntStateOf(0) } // 0: Latest Trip, 1: All Trips
    var refreshKey by remember { mutableIntStateOf(0) }
    var showResetDialog by remember { mutableStateOf(false) }

    val latestTripId = remember(refreshKey) { dbHelper.getLatestTripId() }
    val totalTripsCount = remember(refreshKey) { dbHelper.getAllTrips().size }

    val hazardDistribution = remember(selectedScope, refreshKey) {
        if (selectedScope == 0 && latestTripId > 0) {
            dbHelper.getTripHazardDistribution(latestTripId)
        } else {
            dbHelper.getHazardDistribution()
        }
    }

    val totalTrips = if (selectedScope == 0) (if (latestTripId > 0) 1 else 0) else totalTripsCount
    val totalHazards = hazardDistribution.values.sum()

    val avgDetectionsPerTrip = if (totalTrips > 0) {
        String.format(java.util.Locale.US, "%.1f", totalHazards.toFloat() / totalTrips)
    } else {
        "0.0"
    }

    val palette = listOf(
        Color(0xFFFF1744),
        Color(0xFFFF9100),
        Color(0xFF00E5FF),
        Color(0xFF00E676),
        Color(0xFF7C4DFF),
        Color(0xFFFFD600),
        Color(0xFFFF4081),
        Color(0xFF40C4FF)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
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
                    text = AppStrings.get("analytics_screen_title", isArabic),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(
                onClick = { showResetDialog = true },
                modifier = Modifier.background(Color(0xFFE50914).copy(alpha = 0.2f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = AppStrings.get("btn_clear_history", isArabic),
                    tint = Color(0xFFFF5252)
                )
            }
        }

        PrimaryTabRow(
            selectedTabIndex = selectedScope,
            containerColor = Color(0xFF1E293B),
            contentColor = Color(0xFF00E5FF),
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(selectedScope),
                    color = Color(0xFF00E5FF)
                )
            }
        ) {
            Tab(
                selected = selectedScope == 0,
                onClick = { selectedScope = 0 },
                text = {
                    Text(
                        text = AppStrings.get("tab_latest_trip", isArabic),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            )
            Tab(
                selected = selectedScope == 1,
                onClick = { selectedScope = 1 },
                text = {
                    Text(
                        text = AppStrings.get("tab_all_trips", isArabic),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Icon(Icons.Default.Assessment, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = AppStrings.get("metric_avg_per_trip", isArabic), color = Color.LightGray, fontSize = 11.sp)
                    Text(text = avgDetectionsPerTrip, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Icon(Icons.Default.Dangerous, contentDescription = null, tint = Color(0xFFFF9100), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = AppStrings.get("metric_total_hazards", isArabic), color = Color.LightGray, fontSize = 11.sp)
                    Text(text = "$totalHazards", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.weight(1f)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = AppStrings.get("completed_trips", isArabic), color = Color.LightGray, fontSize = 11.sp)
                    Text(text = "$totalTrips", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Donut Chart Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = AppStrings.get("hazard_breakdown_title", isArabic),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier.size(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (totalHazards == 0) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                color = Color(0xFF334155),
                                style = Stroke(width = 28.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            text = AppStrings.get("no_hazards", isArabic),
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )
                    } else {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            var startAngle = -90f
                            val strokeWidth = 28.dp.toPx()
                            val padding = strokeWidth / 2
                            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)

                            hazardDistribution.entries.forEachIndexed { index, entry ->
                                val sweepAngle = (entry.value.toFloat() / totalHazards) * 360f
                                val color = palette[index % palette.size]

                                drawArc(
                                    color = color,
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle - 2f,
                                    useCenter = false,
                                    topLeft = Offset(padding, padding),
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                                startAngle += sweepAngle
                            }
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$totalHazards",
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = AppStrings.get("detections_count", isArabic),
                                color = Color(0xFF00E5FF),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                if (hazardDistribution.isEmpty()) {
                    Text(
                        text = if (isArabic) "ابدأ رحلة جديدة لرصد الإحصائيات الميدانية أثناء القيادة." else "Start a new trip to record road hazard statistics during driving.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                } else {
                    hazardDistribution.entries.forEachIndexed { index, entry ->
                        val ratio = entry.value.toFloat() / totalHazards
                        val percentage = (ratio * 100).toInt()
                        val color = palette[index % palette.size]
                        val localizedName = HazardCatalog.getLocalizedTitle(entry.key, isArabic)

                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(color, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = localizedName, color = Color.White, fontSize = 14.sp)
                                }
                                Text(
                                    text = "${entry.value} (${percentage}%)",
                                    color = color,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { ratio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = color,
                                trackColor = Color(0xFF334155)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Privacy & Edge AI Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF162032)),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF00E676), modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isArabic) "معالجة ذكية محلية بالكامل على الجهاز" else "100% On-Device Edge AI Processing",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isArabic)
                            "تتم معالجة الرؤية الحاسوبية على الهاتف مباشرة دون الحاجة لرفع الفيديو لخوادم خارجية؛ لضمان السرعة والخصوصية."
                        else
                            "Computer vision inference executes locally on the mobile chip without streaming video to external cloud servers, guaranteeing real-time speed and driver privacy.",
                        color = Color.LightGray,
                        fontSize = 11.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    text = AppStrings.get("dialog_clear_title", isArabic),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = AppStrings.get("dialog_clear_msg", isArabic),
                    color = Color.LightGray
                )
            },
            containerColor = Color(0xFF1E293B),
            confirmButton = {
                Button(
                    onClick = {
                        dbHelper.clearAllHistory()
                        refreshKey++
                        showResetDialog = false
                        Toast.makeText(context, AppStrings.get("toast_history_cleared", isArabic), Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) {
                    Text(
                        text = AppStrings.get("btn_confirm_clear", isArabic),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(text = AppStrings.get("cancel", isArabic), color = Color.White)
                }
            }
        )
    }
}
