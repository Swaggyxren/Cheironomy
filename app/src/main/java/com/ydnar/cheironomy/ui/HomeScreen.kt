package com.ydnar.cheironomy.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ydnar.cheironomy.accessibility.CheironomyAccessibilityService
import com.ydnar.cheironomy.camera.CameraPreview
import com.ydnar.cheironomy.data.SettingsRepository
import com.ydnar.cheironomy.gesture.engine.GestureEngine
import com.ydnar.cheironomy.gesture.engine.GestureEngineStatus
import com.ydnar.cheironomy.gesture.engine.GestureState
import com.ydnar.cheironomy.gesture.model.HandLandmarkResultBundle
import com.ydnar.cheironomy.service.CheironomyForegroundService
import com.ydnar.cheironomy.ui.overlay.HandLandmarkOverlay
import com.ydnar.cheironomy.ui.theme.BackgroundDark
import com.ydnar.cheironomy.ui.theme.CardBackground
import com.ydnar.cheironomy.ui.theme.PrimaryTeal
import com.ydnar.cheironomy.ui.theme.StatusAmber
import com.ydnar.cheironomy.ui.theme.StatusGreen
import com.ydnar.cheironomy.ui.theme.StatusRed

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settingsRepo = remember { SettingsRepository.getInstance(context) }

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = BackgroundDark,
                contentColor = Color.White
            ) {
                NavigationBarItem(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = PrimaryTeal,
                        indicatorColor = PrimaryTeal,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )
                NavigationBarItem(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Black,
                        selectedTextColor = PrimaryTeal,
                        indicatorColor = PrimaryTeal,
                        unselectedIconColor = Color.Gray,
                        unselectedTextColor = Color.Gray
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTabIndex) {
                0 -> DashboardTab(
                    context = context,
                    lifecycleOwner = lifecycleOwner,
                    settingsRepo = settingsRepo,
                    onNavigateToRecord = { selectedTabIndex = 1 }
                )
                1 -> SettingsScreen(settingsRepo)
            }
        }
    }
}

@Composable
private fun DashboardTab(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    settingsRepo: SettingsRepository,
    onNavigateToRecord: () -> Unit
) {
    // Live State from Services
    val isServiceRunning by CheironomyForegroundService.isRunning.collectAsStateWithLifecycle()
    val isA11yConnected by CheironomyAccessibilityService.isServiceConnected.collectAsStateWithLifecycle()

    // Live MediaPipe Detection State
    var landmarkResultBundle by remember { mutableStateOf<HandLandmarkResultBundle?>(null) }
    var inferenceLatencyMs by remember { mutableLongStateOf(0L) }
    var liveFps by remember { mutableStateOf(0f) }
    var liveConfidence by remember { mutableStateOf(0f) }

    val appSettings by settingsRepo.settings.collectAsStateWithLifecycle()
    val serviceEngine by CheironomyForegroundService.activeEngine.collectAsStateWithLifecycle()
    val localEngine = remember { GestureEngine(settings = appSettings) }
    val effectiveEngine = serviceEngine ?: localEngine

    val gestureStatus by effectiveEngine.status.collectAsStateWithLifecycle()
    val gestureState by effectiveEngine.gestureState.collectAsStateWithLifecycle()
    val recognizedName by effectiveEngine.recognizedGestureName.collectAsStateWithLifecycle()
    val rawCentroid by effectiveEngine.telemetryRawCentroid.collectAsStateWithLifecycle()
    val filteredCentroid by effectiveEngine.telemetryFilteredCentroid.collectAsStateWithLifecycle()
    val deltaX by effectiveEngine.telemetryDeltaX.collectAsStateWithLifecycle()
    val deltaY by effectiveEngine.telemetryDeltaY.collectAsStateWithLifecycle()

    // Permission States
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasNotificationPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    var hasOverlayPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.canDrawOverlays(context)
            } else {
                true
            }
        )
    }

    var isA11yEnabledInSettings by remember {
        mutableStateOf(checkAccessibilityServiceEnabled(context))
    }

    // Permission Launchers
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    // Re-check permissions on activity resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    hasNotificationPermission = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    hasOverlayPermission = Settings.canDrawOverlays(context)
                }

                isA11yEnabledInSettings = checkAccessibilityServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Cheironomy",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
                Text(
                    text = "Touchless Hand Gesture Controller",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )
            }

            // Status Badge
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isServiceRunning) StatusGreen.copy(alpha = 0.15f) else Color.DarkGray.copy(alpha = 0.4f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isServiceRunning) StatusGreen else Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isServiceRunning) "ACTIVE" else "IDLE",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isServiceRunning) StatusGreen else Color.Gray
                    )
                }
            }
        }

        // Camera Preview & Live Gesture Detection Overlay Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (hasCameraPermission) {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onLandmarkResults = { bundle ->
                            landmarkResultBundle = bundle
                            inferenceLatencyMs = bundle.inferenceTimeMs
                            liveFps = bundle.fps
                            liveConfidence = bundle.confidence
                            effectiveEngine.updateSettings(appSettings)
                            effectiveEngine.processFrame(bundle)
                        }
                    )

                    // Real-time skeletal overlay with exact aspect ratio scaling & front-camera mirror alignment
                    HandLandmarkOverlay(
                        resultBundle = landmarkResultBundle,
                        modifier = Modifier.fillMaxSize(),
                        isFrontCamera = true
                    )

                    // Real-time diagnostics & telemetry chips overlay
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Top Telemetry Bar: Pose State & Confidence
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.75f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val stateColor = when (gestureStatus) {
                                    GestureEngineStatus.RECOGNIZED -> StatusGreen
                                    GestureEngineStatus.HOLDING -> StatusAmber
                                    GestureEngineStatus.TRACKING -> PrimaryTeal
                                    GestureEngineStatus.WARMING_UP -> StatusAmber
                                    GestureEngineStatus.IDLE -> PrimaryTeal
                                    GestureEngineStatus.SCANNING -> Color.LightGray
                                }

                                val statusLabel = when (gestureStatus) {
                                    GestureEngineStatus.RECOGNIZED -> if (recognizedName != null) "Recognized: $recognizedName" else "Recognized!"
                                    GestureEngineStatus.HOLDING -> if (recognizedName != null) "Holding: $recognizedName" else "Holding Pose"
                                    GestureEngineStatus.TRACKING -> "Tracking Motion"
                                    GestureEngineStatus.WARMING_UP -> "Warming Up"
                                    GestureEngineStatus.IDLE -> "Ready [IDLE]"
                                    GestureEngineStatus.SCANNING -> "Scanning"
                                }

                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = stateColor,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(text = "•", color = Color.DarkGray, fontSize = 10.sp)

                                val confPercent = (liveConfidence * 100).toInt()
                                val confColor = if (liveConfidence >= appSettings.confidenceThreshold) StatusGreen else StatusAmber
                                Text(
                                    text = "Conf: $confPercent%",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = confColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        // Middle Telemetry Bar: 1€ Filter Comparison (Filtered vs Raw)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.75f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "1€: (${String.format("%.2f", filteredCentroid.x)}, ${String.format("%.2f", filteredCentroid.y)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrimaryTeal,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "•",
                                    color = Color.DarkGray,
                                    fontSize = 10.sp
                                )
                                Text(
                                    text = "Raw: (${String.format("%.2f", rawCentroid.x)}, ${String.format("%.2f", rawCentroid.y)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Bottom Telemetry Bar: Centroid Delta, State & FPS
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.75f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Δ(${String.format("%.2f", deltaX)}, ${String.format("%.2f", deltaY)})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontSize = 10.sp
                                )

                                Text(text = "•", color = Color.DarkGray, fontSize = 10.sp)

                                val stateColor = when (gestureState) {
                                    GestureState.IDLE -> Color.Gray
                                    GestureState.HOLDING -> StatusAmber
                                    GestureState.TRACKING -> PrimaryTeal
                                    GestureState.RECOGNIZED -> StatusGreen
                                }
                                Text(
                                    text = "[${gestureState.name}]",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = stateColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )

                                Text(text = "•", color = Color.DarkGray, fontSize = 10.sp)

                                Text(
                                    text = "${liveFps.toInt()} FPS (${inferenceLatencyMs}ms)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrimaryTeal,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Camera Permission",
                                tint = StatusAmber,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Camera Permission Required",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Camera preview is required to detect hand landmarks on-device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                            ) {
                                Text("Grant Permission", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Onboarding Empty-State Card: Prompt to record first gesture if none exist
        if (appSettings.customTemplates.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardBackground)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Gesture,
                        contentDescription = null,
                        tint = PrimaryTeal,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No Gestures Recorded Yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Cheironomy uses pure custom templates. Record your first motion gesture or static pose to start controlling your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = onNavigateToRecord,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryTeal)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Record First Gesture", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Foreground Service Controller Action Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Background Gesture Service",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Runs touchless gesture detection in the background with floating status pill across all apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (!isServiceRunning) {
                        Button(
                            onClick = {
                                if (!hasCameraPermission) {
                                    cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                                } else {
                                    CheironomyForegroundService.start(context)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusGreen),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Service", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                CheironomyForegroundService.stop(context)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = StatusRed),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Stop Service", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Permissions & System Requirements List
        Text(
            text = "Permissions & System Setup",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )

        // 1. Camera Permission
        StatusRow(
            icon = Icons.Default.CameraAlt,
            title = "Camera Permission",
            subtitle = if (hasCameraPermission) "Granted" else "Required for gesture inference",
            isOk = hasCameraPermission,
            actionLabel = if (!hasCameraPermission) "Grant" else null,
            onActionClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
        )

        // 2. Accessibility Service
        StatusRow(
            icon = Icons.Default.AccessibilityNew,
            title = "Accessibility Service",
            subtitle = if (isA11yConnected || isA11yEnabledInSettings) "Connected (Swipe & Scroll ready)" else "Enable Cheironomy in Settings",
            isOk = isA11yConnected || isA11yEnabledInSettings,
            actionLabel = "Settings",
            onActionClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        )

        // 3. Floating Overlay Permission
        StatusRow(
            icon = Icons.Default.Layers,
            title = "Floating Overlay Permission",
            subtitle = if (hasOverlayPermission) "Granted (Floating pill enabled)" else "Allow display over other apps",
            isOk = hasOverlayPermission,
            actionLabel = if (!hasOverlayPermission) "Allow" else null,
            onActionClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:${context.packageName}")
                    ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                    context.startActivity(intent)
                }
            }
        )

        // 4. Notification Permission (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            StatusRow(
                icon = Icons.Default.Notifications,
                title = "Notification Permission",
                subtitle = if (hasNotificationPermission) "Granted" else "Required for foreground service status",
                isOk = hasNotificationPermission,
                actionLabel = if (!hasNotificationPermission) "Grant" else null,
                onActionClick = { notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun StatusRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isOk: Boolean,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isOk) StatusGreen.copy(alpha = 0.15f) else StatusAmber.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isOk) StatusGreen else StatusAmber,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Color.White
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOk) StatusGreen else Color.LightGray
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (actionLabel != null && onActionClick != null) {
                    OutlinedButton(
                        onClick = onActionClick,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PrimaryTeal)
                    ) {
                        Text(actionLabel, fontSize = 12.sp)
                    }
                } else if (isOk) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "OK",
                        tint = StatusGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

private fun checkAccessibilityServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
    val enabledServices = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC)
    val expectedPackage = context.packageName
    return enabledServices.any { it.resolveInfo.serviceInfo.packageName == expectedPackage }
}
