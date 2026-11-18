package com.example.vision.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vision.data.DatabaseHelper
import com.example.vision.data.DetectionRecord
import com.example.vision.data.HazardCatalog
import com.example.vision.data.TripExportHelper
import com.example.vision.ui.AppStrings

private data class HistoryFilter(val key: String, val titleAr: String, val titleEn: String)

@Composable
fun HistoryScreen(
    records: List<DetectionRecord>,
    dbHelper: DatabaseHelper? = null,
    driverFilter: String? = null,
    isArabic: Boolean = true,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val filters = remember {
        listOf(
            HistoryFilter("all", "الكل", "All"),
            HistoryFilter("prohibitory", "لوحات المنع والتوقف", "Prohibitory & Stop Signs"),
            HistoryFilter("speed", "لوحات السرعة", "Speed Limits"),
            HistoryFilter("pedestrian", "المشاة وممراتهم", "Pedestrians & Crossings"),
            HistoryFilter("bump", "المطبات الصناعية", "Speed Bumps"),
            HistoryFilter("signal", "إشارات المرور", "Traffic Signals"),
            HistoryFilter("gravel", "حصى متطاير", "Loose Gravel")
        )
    }

    var selectedFilterKey by remember { mutableStateOf("all") }

    val filteredRecords = remember(records, selectedFilterKey) {
        when (selectedFilterKey) {
            "prohibitory" -> records.filter {
                it.objectType.contains("no_entry") ||
                it.objectType.contains("no_parking") ||
                it.objectType.contains("stoop") ||
                it.objectType.contains("stop")
            }
            "speed" -> records.filter {
                it.objectType.startsWith("speed_") && !it.objectType.contains("bump")
            }
            "pedestrian" -> records.filter {
                it.objectType.contains("pedestrian")
            }
            "bump" -> records.filter {
                it.objectType.contains("bump")
            }
            "signal" -> records.filter {
                it.objectType.contains("signal") || it.objectType.contains("traffic")
            }
            "gravel" -> records.filter {
                it.objectType.contains("gravel")
            }
            else -> records
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = AppStrings.get("back", isArabic),
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = AppStrings.get("history_screen_title", isArabic),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = {
                    val exportList = filteredRecords.ifEmpty { records }
                    if (exportList.isEmpty()) {
                        Toast.makeText(context, AppStrings.get("toast_no_data", isArabic), Toast.LENGTH_SHORT).show()
                        return@IconButton
                    }
                    val csv = TripExportHelper.formatTripHistoryCsv(exportList)
                    TripExportHelper.exportCsvToDownloads(context, csv, isArabic)
                },
                modifier = Modifier.background(Color(0xFF00E676).copy(alpha = 0.16f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = AppStrings.get("btn_export_csv", isArabic),
                    tint = Color(0xFF00E676)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = {
                    val exportList = filteredRecords.ifEmpty { records }
                    if (exportList.isEmpty()) {
                        Toast.makeText(context, AppStrings.get("toast_no_data", isArabic), Toast.LENGTH_SHORT).show()
                        return@IconButton
                    }
                    val textReport = dbHelper?.exportTripHistoryText(driverFilter)
                        ?.takeIf { exportList.isNotEmpty() }
                        ?: TripExportHelper.formatTripHistoryText(
                            trips = emptyList(),
                            records = exportList,
                            stats = exportList.groupingBy { it.arabicType }.eachCount(),
                            driverSummaries = emptyList()
                        )
                    val fileName = "VISION_Trips_${TripExportHelper.timestampTag()}.txt"
                    TripExportHelper.shareReportFile(
                        context = context,
                        content = textReport,
                        fileName = fileName,
                        mimeType = "text/plain",
                        chooserTitle = AppStrings.get("btn_share_history", isArabic),
                        isArabic = isArabic
                    )
                },
                modifier = Modifier.background(Color(0xFF00E5FF).copy(alpha = 0.16f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = AppStrings.get("btn_share_history", isArabic),
                    tint = Color(0xFF00E5FF)
                )
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            items(filters) { filter ->
                val isSelected = selectedFilterKey == filter.key
                val title = if (isArabic) filter.titleAr else filter.titleEn
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilterKey = filter.key },
                    label = { Text(title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFF00E5FF),
                        selectedLabelColor = Color.Black,
                        containerColor = Color(0xFF1E293B),
                        labelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filteredRecords.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = AppStrings.get("no_hazards_filtered", isArabic),
                    color = Color.Gray,
                    fontSize = 15.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredRecords) { record ->
                    HazardHistoryCard(record = record, isArabic = isArabic)
                }
            }
        }
    }
}

@Composable
private fun HazardHistoryCard(record: DetectionRecord, isArabic: Boolean) {
    val (icon, iconColor) = when {
        record.objectType.contains("no_entry") -> Pair(Icons.Default.Block, Color(0xFFFF1744))
        record.objectType.contains("no_parking") -> Pair(Icons.Default.Block, Color(0xFFFF5252))
        record.objectType.contains("stoop") || record.objectType.contains("stop") -> Pair(Icons.Default.Stop, Color(0xFFD50000))
        record.objectType.contains("gravel") -> Pair(Icons.Default.Terrain, Color(0xFFFFD600))
        record.objectType.contains("pedestrian") -> Pair(Icons.AutoMirrored.Filled.DirectionsWalk, Color(0xFFFF4081))
        record.objectType.contains("bump") -> Pair(Icons.Default.Warning, Color(0xFFFF9100))
        record.objectType.contains("speed") -> Pair(Icons.Default.Speed, Color(0xFF00E5FF))
        else -> Pair(Icons.Default.Traffic, Color(0xFF00E676))
    }

    val displayTitle = HazardCatalog.getLocalizedTitle(record.objectType, isArabic)

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
                    .background(iconColor.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(26.dp))
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayTitle,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${AppStrings.get("driver_label", isArabic)}: ${record.driverUsername} • ${record.detectedAt}",
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${AppStrings.get("location_coords", isArabic)}: ${TripExportHelper.formatCoordsDisplay(record.latitude, record.longitude, isArabic)}",
                        color = Color(0xFF00E5FF),
                        fontSize = 11.sp
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "${(record.confidence * 100).toInt()}%",
                    color = Color.Green,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
