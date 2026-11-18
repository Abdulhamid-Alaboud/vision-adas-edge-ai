package com.example.vision.ui.screens

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vision.data.CloudSyncManager
import com.example.vision.data.DatabaseHelper
import com.example.vision.data.User
import com.example.vision.ui.AppStrings
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    dbHelper: DatabaseHelper,
    cloudSyncManager: CloudSyncManager,
    isArabic: Boolean = true,
    onLanguageChanged: (String) -> Unit = {},
    onLoginSuccess: (User) -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableIntStateOf(0) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var showForgotPasswordDialog by remember { mutableStateOf(false) }
    var resetUsername by remember { mutableStateOf("") }
    var resetNewPassword by remember { mutableStateOf("") }
    var resetConfirmPassword by remember { mutableStateOf("") }
    var resetError by remember { mutableStateOf<String?>(null) }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0E17))
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161F2E)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.65f else 0.92f)
                .padding(vertical = if (isLandscape) 16.dp else 24.dp, horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(if (isLandscape) 16.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Language Selector Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = AppStrings.get("select_language", isArabic),
                        color = Color.LightGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF0F172A),
                        border = BorderStroke(1.dp, Color(0xFF334155))
                    ) {
                        Row(modifier = Modifier.padding(2.dp)) {
                            Button(
                                onClick = { onLanguageChanged("ar") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isArabic) Color(0xFF00E5FF) else Color.Transparent,
                                    contentColor = if (isArabic) Color.Black else Color.LightGray
                                ),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("العربية", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { onLanguageChanged("en") },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (!isArabic) Color(0xFF00E5FF) else Color.Transparent,
                                    contentColor = if (!isArabic) Color.Black else Color.LightGray
                                ),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("English", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 14.dp))

                Box(
                    modifier = Modifier
                        .size(if (isLandscape) 48.dp else 64.dp)
                        .background(Color(0xFF00E5FF).copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(if (isLandscape) 28.dp else 36.dp)
                    )
                }

                Spacer(modifier = Modifier.height(if (isLandscape) 6.dp else 12.dp))

                Text(
                    text = if (isArabic) "نظام VISION الذكي" else "VISION ADAS",
                    color = Color.White,
                    fontSize = if (isLandscape) 18.sp else 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = AppStrings.get("auth_app_subtitle", isArabic),
                    color = Color.LightGray,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(if (isLandscape) 10.dp else 16.dp))

                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF0F172A),
                    contentColor = Color(0xFF00E5FF),
                    indicator = {
                        TabRowDefaults.PrimaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(selectedTab),
                            color = Color(0xFF00E5FF)
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0; errorMessage = null; successMessage = null },
                        text = { Text(AppStrings.get("tab_login", isArabic), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1; errorMessage = null; successMessage = null },
                        text = { Text(AppStrings.get("tab_register", isArabic), fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(if (isLandscape) 10.dp else 16.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; errorMessage = null; successMessage = null },
                    label = { Text(AppStrings.get("username", isArabic)) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF00E5FF)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedLabelColor = Color(0xFF00E5FF),
                        unfocusedLabelColor = Color.LightGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null; successMessage = null },
                    label = { Text(AppStrings.get("password", isArabic)) },
                    visualTransformation = PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF00E5FF)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00E5FF),
                        unfocusedBorderColor = Color(0xFF334155),
                        focusedLabelColor = Color(0xFF00E5FF),
                        unfocusedLabelColor = Color.LightGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (selectedTab == 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                resetUsername = username
                                resetNewPassword = ""
                                resetConfirmPassword = ""
                                resetError = null
                                showForgotPasswordDialog = true
                            }
                        ) {
                            Text(AppStrings.get("link_forgot_password", isArabic), color = Color(0xFF00E5FF), fontSize = 12.sp)
                        }
                    }
                }

                if (selectedTab == 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it; errorMessage = null },
                        label = { Text(AppStrings.get("confirm_password", isArabic)) },
                        visualTransformation = PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFF00E5FF)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedLabelColor = Color(0xFF00E5FF),
                            unfocusedLabelColor = Color.LightGray,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                if (successMessage != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = successMessage ?: "",
                        color = Color(0xFF00E676),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(if (isLandscape) 10.dp else 18.dp))

                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color(0xFF00E5FF),
                        modifier = Modifier.size(36.dp)
                    )
                } else if (selectedTab == 0) {
                    Button(
                        onClick = {
                            errorMessage = null
                            successMessage = null
                            if (username.isBlank() || password.isBlank()) {
                                errorMessage = if (isArabic) "يرجى إدخال اسم المستخدم وكلمة المرور" else "Please enter username and password"
                                return@Button
                            }

                            val localUser = dbHelper.authenticate(username, password)
                            if (localUser != null) {
                                onLoginSuccess(localUser)
                                return@Button
                            }

                            scope.launch {
                                isLoading = true
                                val passHash = DatabaseHelper.hashPassword(password)
                                val cloudResult = cloudSyncManager.authenticateCloud(username, passHash)
                                isLoading = false
                                val cloudUser = cloudResult.getOrNull()
                                if (cloudUser != null) {
                                    dbHelper.registerDriver(cloudUser.username, password)
                                    onLoginSuccess(cloudUser)
                                } else {
                                    errorMessage = AppStrings.get("err_invalid_creds", isArabic)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(if (isLandscape) 42.dp else 48.dp)
                    ) {
                        Text(text = AppStrings.get("btn_sign_in", isArabic), color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = {
                            errorMessage = null
                            successMessage = null
                            val cleanUser = username.trim()
                            if (cleanUser.isBlank() || password.isBlank()) {
                                errorMessage = if (isArabic) "يرجى ملء جميع الحقول" else "Please fill all fields"
                                return@Button
                            }
                            if (cleanUser.length < 3) {
                                errorMessage = if (isArabic) "اسم المستخدم يجب ألا يقل عن 3 أحرف" else "Username must be at least 3 characters"
                                return@Button
                            }
                            if (password.length < 4) {
                                errorMessage = if (isArabic) "كلمة المرور يجب ألا تقل عن 4 خانات" else "Password must be at least 4 characters"
                                return@Button
                            }
                            if (password != confirmPassword) {
                                errorMessage = AppStrings.get("err_passwords_mismatch", isArabic)
                                return@Button
                            }
                            if (cleanUser.equals("admin", ignoreCase = true)) {
                                errorMessage = if (isArabic) "اسم المستخدم 'admin' محجوز للإدارة والمشرفين" else "Username 'admin' is reserved"
                                return@Button
                            }

                            scope.launch {
                                isLoading = true
                                val availResult = cloudSyncManager.checkUsernameAvailability(cleanUser)
                                if (availResult.isSuccess) {
                                    val isAvailable = availResult.getOrThrow()
                                    if (!isAvailable) {
                                        isLoading = false
                                        errorMessage = AppStrings.get("err_user_taken", isArabic)
                                        return@launch
                                    }
                                }

                                val passHash = DatabaseHelper.hashPassword(password)
                                val cloudReg = cloudSyncManager.registerUserCloud(cleanUser, passHash, "Driver")
                                val localReg = dbHelper.registerDriver(cleanUser, password)

                                isLoading = false
                                if (cloudReg.isSuccess || localReg) {
                                    successMessage = AppStrings.get("msg_account_created", isArabic)
                                    selectedTab = 0
                                    password = ""
                                    confirmPassword = ""
                                } else {
                                    errorMessage = if (isArabic) "تعذر إتمام التسجيل، يرجى التحقق من الاتصال بالإنترنت" else "Registration failed, please check internet connection"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(if (isLandscape) 42.dp else 48.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = AppStrings.get("btn_register", isArabic), color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 14.dp))
                HorizontalDivider(color = Color(0xFF334155))
                Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 14.dp))

                Button(
                    onClick = {
                        dbHelper.clearGuestHistory()
                        val guestUser = User(userId = 0, username = "guest", passwordHash = "", role = "Guest")
                        onLoginSuccess(guestUser)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(if (isLandscape) 42.dp else 48.dp)
                ) {
                    Icon(Icons.Default.DirectionsCar, contentDescription = null, tint = Color(0xFF00E5FF))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppStrings.get("btn_guest", isArabic),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    if (showForgotPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showForgotPasswordDialog = false },
            title = { Text(AppStrings.get("dialog_reset_title", isArabic), fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = AppStrings.get("dialog_reset_msg", isArabic),
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = resetUsername,
                        onValueChange = { resetUsername = it; resetError = null },
                        label = { Text(AppStrings.get("username", isArabic)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = resetNewPassword,
                        onValueChange = { resetNewPassword = it; resetError = null },
                        label = { Text(AppStrings.get("new_password", isArabic)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = resetConfirmPassword,
                        onValueChange = { resetConfirmPassword = it; resetError = null },
                        label = { Text(AppStrings.get("confirm_password", isArabic)) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0xFF334155),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (resetError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(resetError ?: "", color = Color(0xFFFF5252), fontSize = 12.sp)
                    }
                }
            },
            containerColor = Color(0xFF1E293B),
            confirmButton = {
                Button(
                    onClick = {
                        val cleanUser = resetUsername.trim()
                        if (cleanUser.isBlank() || resetNewPassword.isBlank()) {
                            resetError = if (isArabic) "يرجى ملء جميع الحقول" else "Please fill all fields"
                            return@Button
                        }
                        if (resetNewPassword.length < 4) {
                            resetError = if (isArabic) "كلمة المرور يجب ألا تقل عن 4 خانات" else "Password must be at least 4 characters"
                            return@Button
                        }
                        if (resetNewPassword != resetConfirmPassword) {
                            resetError = AppStrings.get("err_passwords_mismatch", isArabic)
                            return@Button
                        }
                        if (cleanUser.equals("admin", ignoreCase = true)) {
                            resetError = if (isArabic) "لا يمكن تغيير كلمة مرور المشرف من هنا" else "Cannot reset admin password here"
                            return@Button
                        }

                        val localOk = dbHelper.resetPassword(cleanUser, resetNewPassword)
                        scope.launch {
                            val passHash = DatabaseHelper.hashPassword(resetNewPassword)
                            cloudSyncManager.updatePasswordCloud(cleanUser, passHash)
                        }

                        if (localOk) {
                            showForgotPasswordDialog = false
                            successMessage = AppStrings.get("msg_password_reset", isArabic)
                        } else {
                            resetError = AppStrings.get("err_user_not_found", isArabic)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF))
                ) {
                    Text(AppStrings.get("btn_save_password", isArabic), color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showForgotPasswordDialog = false }) {
                    Text(AppStrings.get("cancel", isArabic), color = Color.White)
                }
            }
        )
    }
}
