package com.example.vision.ui.screens

import android.content.res.Configuration
import android.graphics.Paint
import android.graphics.RectF
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.vision.R
import com.example.vision.data.DetectionItem
import com.example.vision.data.HazardCatalog
import com.example.vision.engine.AlertSystem
import com.example.vision.ui.AppStrings
import java.util.Locale

@Composable
fun DashboardScreen(
    isMonitoring: Boolean,
    onToggleMonitoring: () -> Unit,
    detections: List<DetectionItem>,
    currentAlert: AlertSystem.AlertState?,
    inferenceLatencyMs: Long = 0L,
    topConfidence: Float = 0f,
    topClassName: String = "",
    currentZoom: Float,
    onZoomChanged: (Float) -> Unit,
    isAdmin: Boolean,
    isArabic: Boolean = true,
    cameraFrameWidth: Int = 0,
    cameraFrameHeight: Int = 0,
    // Screen Navigation:
    onNavigateToSettings: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToAnalytics: () -> Unit,
    onNavigateToAdmin: () -> Unit,
    onLogout: (() -> Unit)? = null,
    onPreviewViewCreated: (PreviewView) -> Unit,
    errorMessage: String? = null
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // 1. Live Camera Preview Feed
        if (isMonitoring) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }.also { pv ->
                        onPreviewViewCreated(pv)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { pv ->
                    onPreviewViewCreated(pv)
                }
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF0F172A)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.mipmap.ic_launcher),
                        contentDescription = "VISION App Icon",
                        modifier = Modifier
                            .size(if (isLandscape) 64.dp else 84.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(if (isLandscape) 10.dp else 16.dp))
                    Text(
                        text = AppStrings.get("monitoring_prompt", isArabic),
                        color = Color.LightGray,
                        fontSize = if (isLandscape) 13.sp else 14.sp
                    )
                }
            }
        }

        // 2. High-Precision Bounding Boxes Canvas
        if (isMonitoring && detections.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                if (canvasWidth <= 0f || canvasHeight <= 0f) return@Canvas

                val textPaint = Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 34f
                    isFakeBoldText = true
                    isAntiAlias = true
                }

                val pillBgPaint = Paint().apply {
                    style = Paint.Style.FILL
                    isAntiAlias = true
                }

                // Projection for PreviewView.ScaleType.FILL_CENTER
                val hasAspect = cameraFrameWidth > 0 && cameraFrameHeight > 0
                val frameAspect = if (hasAspect) cameraFrameWidth.toFloat() / cameraFrameHeight.toFloat() else 1f
                val canvasAspect = canvasWidth / canvasHeight

                val scale: Float
                val offsetX: Float
                val offsetY: Float

                if (hasAspect) {
                    if (frameAspect > canvasAspect) {
                        scale = canvasHeight / cameraFrameHeight.toFloat()
                        val scaledWidth = cameraFrameWidth * scale
                        offsetX = (canvasWidth - scaledWidth) / 2f
                        offsetY = 0f
                    } else {
                        scale = canvasWidth / cameraFrameWidth.toFloat()
                        val scaledHeight = cameraFrameHeight * scale
                        offsetX = 0f
                        offsetY = (canvasHeight - scaledHeight) / 2f
                    }
                } else {
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                }

                detections.forEach { item ->
                    val box = item.boundingBox

                    val left: Float
                    val top: Float
                    val width: Float
                    val height: Float

                    if (hasAspect) {
                        left = offsetX + (box.left * cameraFrameWidth * scale)
                        top = offsetY + (box.top * cameraFrameHeight * scale)
                        width = ((box.right - box.left) * cameraFrameWidth * scale).coerceAtLeast(4f)
                        height = ((box.bottom - box.top) * cameraFrameHeight * scale).coerceAtLeast(4f)
                    } else {
                        left = box.left * canvasWidth
                        top = box.top * canvasHeight
                        width = ((box.right - box.left) * canvasWidth).coerceAtLeast(4f)
                        height = ((box.bottom - box.top) * canvasHeight).coerceAtLeast(4f)
                    }

                    val boxColor = when {
                        item.className.contains("pedestrian") || item.className.contains("traffic") -> Color(0xFFFF1744)
                        item.className.contains("bump") || item.className.contains("stop") -> Color(0xFFFF9100)
                        else -> Color(0xFF00E5FF)
                    }

                    drawRect(
                        color = boxColor,
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(width = 4.dp.toPx())
                    )

                    val labelText = "${item.displayName(isArabic)} ${(item.confidence * 100).toInt()}%"
                    val textWidth = textPaint.measureText(labelText)
                    val pillHeight = 42f
                    val pillWidth = textWidth + 24f
                    val pillLeft = left.coerceIn(8f, canvasWidth - pillWidth - 8f)
                    val pillTop = (top - pillHeight - 4f).coerceAtLeast(8f)

                    pillBgPaint.color = boxColor.copy(alpha = 0.90f).toArgb()
                    drawContext.canvas.nativeCanvas.drawRoundRect(
                        pillLeft,
                        pillTop,
                        pillLeft + pillWidth,
                        pillTop + pillHeight,
                        12f,
                        12f,
                        pillBgPaint
                    )

                    drawContext.canvas.nativeCanvas.drawText(
                        labelText,
                        pillLeft + 12f,
                        pillTop + 30f,
                        textPaint
                    )
                }
            }
        }

        // 3. Top Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (isLandscape) 10.dp else 36.dp, start = 12.dp, end = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xDD0F172A)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (isMonitoring) Color(0xFF00E676) else Color(0xFFFF5252), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isMonitoring) {
                            if (isArabic) "نظام الرؤية (نشط)" else "VISION ADAS (Active)"
                        } else {
                            AppStrings.get("stopped", isArabic)
                        },
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (isMonitoring) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFF00E5FF).copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                                .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 7.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = if (inferenceLatencyMs > 0L) "${inferenceLatencyMs} ms" else "... ms",
                                color = Color(0xFF00E5FF),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }

            // Top action icons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isAdmin) {
                    IconButton(
                        onClick = onNavigateToAdmin,
                        modifier = Modifier.background(Color(0xFFE50914).copy(alpha = 0.8f), CircleShape)
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = AppStrings.get("desc_admin", isArabic), tint = Color.White)
                    }
                }
                IconButton(
                    onClick = onNavigateToAnalytics,
                    modifier = Modifier.background(Color(0x991E293B), CircleShape)
                ) {
                    Icon(Icons.Default.Analytics, contentDescription = AppStrings.get("desc_analytics", isArabic), tint = Color.White)
                }
                IconButton(
                    onClick = onNavigateToHistory,
                    modifier = Modifier.background(Color(0x991E293B), CircleShape)
                ) {
                    Icon(Icons.Default.History, contentDescription = AppStrings.get("desc_history", isArabic), tint = Color.White)
                }
                IconButton(
                    onClick = onNavigateToSettings,
                    modifier = Modifier.background(Color(0x991E293B), CircleShape)
                ) {
                    Icon(Icons.Default.Settings, contentDescription = AppStrings.get("desc_settings", isArabic), tint = Color.White)
                }
            }
        }

        // 4. Zoom Control
        if (isMonitoring) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1.0f, 1.4f, 1.6f, 1.8f, 2.0f).forEach { zoom ->
                    val isSelected = kotlin.math.abs(currentZoom - zoom) < 0.08f
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) Color(0xFF00E5FF) else Color(0xCC1E293B)
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.clickable { onZoomChanged(zoom) }
                    ) {
                        Text(
                            text = "${zoom}x",
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // 6. Bottom Controls & Alerts
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isLandscape) 8.dp else 24.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Active Alert Banner placed at the bottom
            AnimatedVisibility(
                visible = currentAlert != null,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                currentAlert?.let { alert ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE50914).copy(alpha = 0.95f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth(if (isLandscape) 0.65f else 0.92f)
                            .padding(horizontal = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = if (isLandscape) 6.dp else 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (isLandscape) 32.dp else 40.dp)
                                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(if (isLandscape) 20.dp else 24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = AppStrings.get("hazard_alert", isArabic),
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = if (isLandscape) 10.sp else 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = alert.message,
                                    color = Color.White,
                                    fontSize = if (isLandscape) 14.sp else 17.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xCCE50914)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = errorMessage,
                        color = Color.White,
                        modifier = Modifier.padding(8.dp),
                        fontSize = 12.sp
                    )
                }
            }

            // Start / Stop Main Button
            Button(
                onClick = onToggleMonitoring,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isMonitoring) Color(0xFFD32F2F) else Color(0xFF00C853)
                ),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth(if (isLandscape) 0.45f else 0.75f)
                    .height(if (isLandscape) 44.dp else 54.dp)
            ) {
                Icon(
                    imageVector = if (isMonitoring) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(if (isLandscape) 22.dp else 26.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isMonitoring) AppStrings.get("stop_monitoring", isArabic) else AppStrings.get("start_monitoring", isArabic),
                    fontSize = if (isLandscape) 14.sp else 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
