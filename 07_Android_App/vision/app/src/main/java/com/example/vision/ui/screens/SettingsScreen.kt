package com.example.vision.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Traffic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vision.data.HazardCatalog
import com.example.vision.data.PreferencesManager
import com.example.vision.data.UserPreferences
import com.example.vision.ui.AppStrings

@Composable
fun SettingsScreen(
    prefsManager: PreferencesManager,
    currentPrefs: UserPreferences,
    onSavePrefs: (UserPreferences) -> Unit,
    isArabic: Boolean = true,
    onLanguageChanged: (String) -> Unit = {},
    onLogout: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    var volume by remember { mutableFloatStateOf(currentPrefs.alertVolume.toFloat()) }
    var enabledClasses by remember { mutableStateOf(currentPrefs.enabledClassIds.toMutableSet()) }
    var showSavedMessage by remember { mutableStateOf(false) }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val pedestrianClasses = listOf(3, 4)
    val criticalSignsClasses = listOf(11, 13, 12, 1, 2, 0)
    val speedLimitClasses = listOf(14, 6, 7, 8, 9, 15, 10, 16, 5, 17, 18, 19)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F172A))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = AppStrings.get("back", isArabic),
                    tint = Color.White
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = AppStrings.get("settings_title", isArabic),
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Section 1: Language Switcher
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = AppStrings.get("language_section_title", isArabic),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = AppStrings.get("language_section_subtitle", isArabic),
                    color = Color.LightGray,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onLanguageChanged("ar") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isArabic) Color(0xFF00E5FF) else Color(0xFF0F172A),
                            contentColor = if (isArabic) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (isArabic) Color(0xFF00E5FF) else Color(0xFF334155)),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("🇸🇦 العربية", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Button(
                        onClick = { onLanguageChanged("en") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isArabic) Color(0xFF00E5FF) else Color(0xFF0F172A),
                            contentColor = if (!isArabic) Color.Black else Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, if (!isArabic) Color(0xFF00E5FF) else Color(0xFF334155)),
                        modifier = Modifier.weight(1f).height(44.dp)
                    ) {
                        Text("🇬🇧 English", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        // Section 2: Volume Control
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = AppStrings.get("alert_volume", isArabic),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.VolumeDown, contentDescription = null, tint = Color.LightGray)
                    Slider(
                        value = volume,
                        onValueChange = { volume = it },
                        valueRange = 0f..100f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF00E5FF),
                            activeTrackColor = Color(0xFF00E5FF),
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = Color(0xFF00E5FF))
                }
                Text(
                    text = "${volume.toInt()}%",
                    color = Color(0xFF00E5FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Section 3: Hazard Groups
        Text(
            text = AppStrings.get("hazards_toggle_title", isArabic),
            color = Color.White,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Pedestrians
        Text(
            text = AppStrings.get("group_pedestrians", isArabic),
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
                pedestrianClasses.forEachIndexed { idx, classId ->
                    val meta = HazardCatalog.getMeta(classId)
                    val isChecked = enabledClasses.contains(classId)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, tint = Color(0xFFFF1744))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = meta?.title(isArabic) ?: "", color = Color.White, fontSize = 14.sp)
                        }
                        Switch(
                            checked = isChecked,
                            onCheckedChange = { check ->
                                val copy = enabledClasses.toMutableSet()
                                if (check) copy.add(classId) else copy.remove(classId)
                                enabledClasses = copy
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
                        )
                    }
                    if (idx < pedestrianClasses.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF334155))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Critical Signs & Speed Bumps
        Text(
            text = AppStrings.get("group_critical", isArabic),
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
                criticalSignsClasses.forEachIndexed { idx, classId ->
                    val meta = HazardCatalog.getMeta(classId)
                    val isChecked = enabledClasses.contains(classId)
                    val icon = when (classId) {
                        11 -> Icons.Default.Warning
                        13 -> Icons.Default.Traffic
                        12 -> Icons.Default.Dangerous
                        1, 2 -> Icons.Default.Block
                        else -> Icons.Default.Warning
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, contentDescription = null, tint = Color(0xFFFF9100))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = meta?.title(isArabic) ?: "", color = Color.White, fontSize = 14.sp)
                        }
                        Switch(
                            checked = isChecked,
                            onCheckedChange = { check ->
                                val copy = enabledClasses.toMutableSet()
                                if (check) copy.add(classId) else copy.remove(classId)
                                enabledClasses = copy
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
                        )
                    }
                    if (idx < criticalSignsClasses.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF334155))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Speed Limit Signs
        Text(
            text = AppStrings.get("group_speed", isArabic),
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
                speedLimitClasses.forEachIndexed { idx, classId ->
                    val meta = HazardCatalog.getMeta(classId)
                    val isChecked = enabledClasses.contains(classId)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFF00E5FF))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = meta?.title(isArabic) ?: "", color = Color.White, fontSize = 14.sp)
                        }
                        Switch(
                            checked = isChecked,
                            onCheckedChange = { check ->
                                val copy = enabledClasses.toMutableSet()
                                if (check) copy.add(classId) else copy.remove(classId)
                                enabledClasses = copy
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E5FF))
                        )
                    }
                    if (idx < speedLimitClasses.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = Color(0xFF334155))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                onSavePrefs(
                    currentPrefs.copy(
                        alertVolume = volume.toInt(),
                        enabledClassIds = enabledClasses
                    )
                )
                showSavedMessage = true
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = AppStrings.get("btn_save_prefs", isArabic), color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        if (showSavedMessage) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = AppStrings.get("msg_prefs_saved", isArabic),
                color = Color.Green,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Dedicated Logout Button (Exclusive place to sign out)
        if (onLogout != null) {
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = { showLogoutConfirm = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626).copy(alpha = 0.15f)),
                border = BorderStroke(1.dp, Color(0xFFEF4444)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = Color(0xFFEF4444))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = AppStrings.get("btn_logout", isArabic),
                    color = Color(0xFFEF4444),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = {
                Text(
                    text = AppStrings.get("dialog_logout_title", isArabic),
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Text(
                    text = AppStrings.get("dialog_logout_msg", isArabic),
                    color = Color.LightGray
                )
            },
            containerColor = Color(0xFF1E293B),
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirm = false
                        onLogout?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text(
                        text = AppStrings.get("btn_logout_confirm", isArabic),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text(
                        text = AppStrings.get("cancel", isArabic),
                        color = Color.White
                    )
                }
            }
        )
    }
}
