package com.example.vision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.vision.data.CloudSyncManager
import com.example.vision.data.DatabaseHelper
import com.example.vision.data.DetectionItem
import com.example.vision.data.LocationTracker
import com.example.vision.data.PreferencesManager
import com.example.vision.data.User
import com.example.vision.engine.AlertSystem
import com.example.vision.engine.CameraManager
import com.example.vision.engine.DetectionEngine
import com.example.vision.engine.HazardEvaluator
import com.example.vision.ui.screens.AdminDashboardScreen
import com.example.vision.ui.screens.AnalyticsScreen
import com.example.vision.ui.screens.AuthScreen
import com.example.vision.ui.screens.DashboardScreen
import com.example.vision.ui.screens.HistoryScreen
import com.example.vision.ui.screens.SettingsScreen
import com.example.vision.ui.theme.VISIONTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

enum class Screen {
    AUTH,
    DASHBOARD,
    SETTINGS,
    HISTORY,
    ANALYTICS,
    ADMIN
}

class MainActivity : ComponentActivity() {

    private lateinit var dbHelper: DatabaseHelper
    private lateinit var prefsManager: PreferencesManager
    private lateinit var cloudSyncManager: CloudSyncManager
    private lateinit var alertSystem: AlertSystem
    private lateinit var detectionEngine: DetectionEngine
    private lateinit var hazardEvaluator: HazardEvaluator
    private lateinit var locationTracker: LocationTracker
    private var cameraManager: CameraManager? = null

    private var hasCameraPermission by mutableStateOf(false)
    private var isMonitoring by mutableStateOf(false)
    private var activeDetections by mutableStateOf<List<DetectionItem>>(emptyList())
    private var inferenceLatency by mutableLongStateOf(0L)
    private var cameraFrameWidth by mutableIntStateOf(0)
    private var cameraFrameHeight by mutableIntStateOf(0)
    private var errorMessage by mutableStateOf<String?>(null)
    private var activePreviewView: PreviewView? = null
    private var currentZoom by mutableFloatStateOf(1.0f)
    private var topConfidence by mutableFloatStateOf(0f)
    private var topClassName by mutableStateOf("")
    private var appLanguage by mutableStateOf("ar")

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPermission = permissions[Manifest.permission.CAMERA] == true
        val hasLocation = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (hasLocation) {
            locationTracker.startUpdates()
        }
        if (hasCameraPermission && isMonitoring && activePreviewView != null) {
            cameraManager?.startCamera(activePreviewView!!)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        dbHelper = DatabaseHelper(this)
        prefsManager = PreferencesManager(this)
        appLanguage = prefsManager.appLanguage
        cloudSyncManager = CloudSyncManager(this)
        locationTracker = LocationTracker(this)
        alertSystem = AlertSystem(this).apply {
            volumePercent = prefsManager.alertVolume
            isArabic = prefsManager.isArabic
            alertCooldownMs = prefsManager.alertCooldownMs
        }
        detectionEngine = DetectionEngine(this).apply {
            confidenceThreshold = prefsManager.detectionThreshold
        }
        hazardEvaluator = HazardEvaluator(
            alertSystem = alertSystem,
            dbHelper = dbHelper,
            locationProvider = { locationTracker.getCurrentCoordinates() }
        ).apply {
            temporalConfirmationFrames = prefsManager.temporalConfirmationFrames
            trackExpiryMs = prefsManager.trackExpiryMs
        }

        cameraManager = CameraManager(
            context = this,
            lifecycleOwner = this,
            onFrameAnalyzed = { bitmap: Bitmap, rotation: Int ->
                if (isMonitoring) {
                    if (cameraFrameWidth != bitmap.width || cameraFrameHeight != bitmap.height) {
                        cameraFrameWidth = bitmap.width
                        cameraFrameHeight = bitmap.height
                    }
                    val orientationName = if (bitmap.width > bitmap.height) "Landscape" else "Portrait"
                    val rawItems = detectionEngine.detect(bitmap)
                    val evaluated = hazardEvaluator.evaluate(rawItems, prefsManager.toUserPreferences())
                    Log.d("MainActivity", "Orientation: $orientationName | Frame: ${bitmap.width}x${bitmap.height} | Rotation: $rotation | Detections: ${evaluated.size}")
                    runOnUiThread {
                        activeDetections = evaluated
                        topConfidence = detectionEngine.lastMaxConfidence
                        topClassName = detectionEngine.lastMaxClassName
                        inferenceLatency = detectionEngine.lastInferenceTimeMs
                    }
                    bitmap.recycle()
                }
            },
            onError = { err ->
                runOnUiThread {
                    errorMessage = err
                    isMonitoring = false
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        )

        checkPermissions()

        setContent {
            val isArabic = appLanguage == "ar"
            val onLanguageChanged: (String) -> Unit = { newLang ->
                prefsManager.appLanguage = newLang
                appLanguage = newLang
                alertSystem.isArabic = (newLang == "ar")
            }

            VISIONTheme {
                CompositionLocalProvider(LocalLayoutDirection provides (if (isArabic) LayoutDirection.Rtl else LayoutDirection.Ltr)) {
                    MainAppContent(
                        isArabic = isArabic,
                        onLanguageChanged = onLanguageChanged
                    )
                }
            }
        }
    }

    private fun checkPermissions() {
        val cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val fineLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseLocationGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        hasCameraPermission = cameraGranted
        if (fineLocationGranted || coarseLocationGranted) {
            locationTracker.startUpdates()
        }

        if (!cameraGranted || !fineLocationGranted) {
            requestPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    override fun onResume() {
        super.onResume()
        if (locationTracker.hasLocationPermission()) {
            locationTracker.startUpdates()
        }
    }

    @Composable
    private fun MainAppContent(
        isArabic: Boolean,
        onLanguageChanged: (String) -> Unit
    ) {
        var currentScreen by remember { mutableStateOf(Screen.AUTH) }
        var currentUser by remember { mutableStateOf<User?>(null) }
        var userPrefs by remember { mutableStateOf(prefsManager.toUserPreferences()) }

        val currentAlert by alertSystem.currentAlert.collectAsState()

        when (currentScreen) {
            Screen.AUTH -> {
                AuthScreen(
                    dbHelper = dbHelper,
                    cloudSyncManager = cloudSyncManager,
                    isArabic = isArabic,
                    onLanguageChanged = onLanguageChanged,
                    onLoginSuccess = { user ->
                        currentUser = user
                        prefsManager.currentUser = user.username
                        prefsManager.currentRole = user.role
                        if (user.role == "Guest") {
                            dbHelper.clearGuestHistory()
                        }
                        currentScreen = Screen.DASHBOARD
                        if (user.role != "Guest") {
                            lifecycleScope.launch(Dispatchers.IO) {
                                val unsynced = dbHelper.getUnsyncedDetections(100)
                                if (unsynced.isNotEmpty()) {
                                    val syncedIds = cloudSyncManager.uploadDetectionsBatch(unsynced)
                                    dbHelper.markDetectionsAsSynced(syncedIds)
                                }
                            }
                        }
                    }
                )
            }

            Screen.DASHBOARD -> {
                DashboardScreen(
                    isMonitoring = isMonitoring,
                    onToggleMonitoring = {
                        if (!hasCameraPermission) {
                            checkPermissions()
                            return@DashboardScreen
                        }

                        if (isMonitoring) {
                            isMonitoring = false
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            cameraManager?.stopCamera()
                            if (hazardEvaluator.currentTripId > 0) {
                                dbHelper.endTrip(hazardEvaluator.currentTripId)
                                if (currentUser?.role != "Guest") {
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        val unsynced = dbHelper.getUnsyncedDetections(200)
                                        if (unsynced.isNotEmpty()) {
                                            val syncedIds = cloudSyncManager.uploadDetectionsBatch(unsynced)
                                            dbHelper.markDetectionsAsSynced(syncedIds)
                                        }
                                    }
                                }
                            }
                            hazardEvaluator.resetTrip()
                            activeDetections = emptyList()
                            alertSystem.clearAlert()
                        } else {
                            val currentDriver = currentUser?.username ?: "driver"
                            val isGuest = (currentUser?.role == "Guest" || currentDriver.lowercase() in listOf("guest", "ضيف"))
                            val tripId = if (isGuest) 0L else dbHelper.startTrip(currentDriver)
                            hazardEvaluator.currentTripId = tripId
                            locationTracker.startUpdates()
                            isMonitoring = true
                            errorMessage = null
                            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            activePreviewView?.let { pv ->
                                cameraManager?.startCamera(pv)
                            }
                        }
                    },
                    detections = activeDetections,
                    currentAlert = currentAlert,
                    inferenceLatencyMs = inferenceLatency,
                    topConfidence = topConfidence,
                    topClassName = topClassName,
                    currentZoom = currentZoom,
                    onZoomChanged = { zoom ->
                        currentZoom = zoom
                        cameraManager?.setZoomRatio(zoom)
                    },
                    isAdmin = currentUser?.role == "Administrator",
                    isArabic = isArabic,
                    cameraFrameWidth = cameraFrameWidth,
                    cameraFrameHeight = cameraFrameHeight,
                    onNavigateToSettings = { currentScreen = Screen.SETTINGS },
                    onNavigateToHistory = { currentScreen = Screen.HISTORY },
                    onNavigateToAnalytics = { currentScreen = Screen.ANALYTICS },
                    onNavigateToAdmin = { currentScreen = Screen.ADMIN },
                    onPreviewViewCreated = { pv ->
                        activePreviewView = pv
                        if (isMonitoring) {
                            cameraManager?.startCamera(pv)
                        }
                    },
                    errorMessage = errorMessage
                )
            }

            Screen.SETTINGS -> {
                SettingsScreen(
                    prefsManager = prefsManager,
                    currentPrefs = userPrefs,
                    onSavePrefs = { updated ->
                        userPrefs = updated
                        dbHelper.updateUserPreferences(updated)
                        prefsManager.save(updated)
                        alertSystem.volumePercent = updated.alertVolume
                        alertSystem.alertCooldownMs = prefsManager.alertCooldownMs
                        hazardEvaluator.temporalConfirmationFrames = prefsManager.temporalConfirmationFrames
                        hazardEvaluator.trackExpiryMs = prefsManager.trackExpiryMs
                        detectionEngine.confidenceThreshold = prefsManager.detectionThreshold
                    },
                    isArabic = isArabic,
                    onLanguageChanged = onLanguageChanged,
                    onLogout = {
                        if (isMonitoring) {
                            isMonitoring = false
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                            cameraManager?.stopCamera()
                            if (hazardEvaluator.currentTripId > 0) {
                                dbHelper.endTrip(hazardEvaluator.currentTripId)
                            }
                            hazardEvaluator.resetTrip()
                            activeDetections = emptyList()
                            alertSystem.clearAlert()
                        }
                        currentUser = null
                        prefsManager.currentUser = null
                        prefsManager.currentRole = null
                        currentScreen = Screen.AUTH
                    },
                    onBack = { currentScreen = Screen.DASHBOARD }
                )
            }

            Screen.HISTORY -> {
                val driverFilter = if (currentUser?.role == "Administrator" || currentUser?.role == "Guest") {
                    null
                } else {
                    currentUser?.username
                }
                val records = remember(currentUser, isMonitoring) {
                    if (driverFilter == null) {
                        dbHelper.getAllDetections(500)
                    } else {
                        dbHelper.getAllDetections(200, driverFilter = driverFilter)
                    }
                }
                HistoryScreen(
                    records = records,
                    dbHelper = dbHelper,
                    driverFilter = driverFilter,
                    isArabic = isArabic,
                    onBack = { currentScreen = Screen.DASHBOARD }
                )
            }

            Screen.ANALYTICS -> {
                AnalyticsScreen(
                    dbHelper = dbHelper,
                    isArabic = isArabic,
                    onBack = { currentScreen = Screen.DASHBOARD }
                )
            }

            Screen.ADMIN -> {
                AdminDashboardScreen(
                    dbHelper = dbHelper,
                    cloudSyncManager = cloudSyncManager,
                    currentThreshold = detectionEngine.confidenceThreshold,
                    onThresholdChanged = { newThresh ->
                        detectionEngine.confidenceThreshold = newThresh
                        prefsManager.detectionThreshold = newThresh
                        userPrefs = userPrefs.copy(detectionThreshold = newThresh)
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                dbHelper.updateUserPreferences(userPrefs)
                            } catch (_: Exception) {}
                        }
                    },
                    isArabic = isArabic,
                    onLogout = {
                        currentUser = null
                        prefsManager.currentUser = null
                        prefsManager.currentRole = null
                        currentScreen = Screen.AUTH
                    },
                    onBack = { currentScreen = Screen.DASHBOARD }
                )
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
        cameraManager?.updateTargetRotation(rotation)
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        locationTracker.stopUpdates()
        cameraManager?.shutdown()
        detectionEngine.close()
        alertSystem.release()
    }
}