package com.ydnar.cheironomy.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.ydnar.cheironomy.CheironomyApp
import com.ydnar.cheironomy.MainActivity
import com.ydnar.cheironomy.R
import com.ydnar.cheironomy.accessibility.CheironomyAccessibilityService
import com.ydnar.cheironomy.camera.HandLandmarkAnalyzer
import com.ydnar.cheironomy.data.GestureAction
import com.ydnar.cheironomy.data.SettingsRepository
import com.ydnar.cheironomy.gesture.HandLandmarkerHelper
import com.ydnar.cheironomy.gesture.engine.GestureEngine
import com.ydnar.cheironomy.gesture.model.GestureEvent
import com.ydnar.cheironomy.gesture.model.HandLandmarkResultBundle
import com.ydnar.cheironomy.media.MediaActionDispatcher
import com.ydnar.cheironomy.overlay.FloatingOverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Foreground Service owning the background camera analysis loop, gesture engine, and action dispatchers.
 */
class CheironomyForegroundService : Service(), LifecycleOwner {

    private val serviceLifecycle = LifecycleRegistry(this)
    override val lifecycle: Lifecycle = serviceLifecycle

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private val backgroundExecutor = Executors.newSingleThreadExecutor()

    private lateinit var settingsRepo: SettingsRepository
    private lateinit var mediaDispatcher: MediaActionDispatcher
    private lateinit var overlayManager: FloatingOverlayManager
    private lateinit var gestureEngine: GestureEngine
    private var handLandmarkerHelper: HandLandmarkerHelper? = null

    private var cameraProvider: ProcessCameraProvider? = null

    companion object {
        private const val TAG = "CheironomyService"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.ydnar.cheironomy.action.START"
        const val ACTION_STOP = "com.ydnar.cheironomy.action.STOP"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, CheironomyForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CheironomyForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        serviceLifecycle.currentState = Lifecycle.State.CREATED
        settingsRepo = SettingsRepository.getInstance(this)
        mediaDispatcher = MediaActionDispatcher(this)
        overlayManager = FloatingOverlayManager(this)
        gestureEngine = GestureEngine(serviceScope, settingsRepo.settings.value)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.i(TAG, "Received ACTION_STOP. Stopping foreground service.")
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                Log.i(TAG, "Starting foreground service pipeline.")
                startInForeground()
            }
        }
        return START_STICKY
    }

    private fun startInForeground() {
        val notification = buildNotification()

        val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        } else {
            0
        }

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                foregroundServiceType
            )
            serviceLifecycle.currentState = Lifecycle.State.STARTED
            serviceLifecycle.currentState = Lifecycle.State.RESUMED
            _isRunning.value = true

            // Setup MediaPipe Inference Helper
            setupInferenceEngine()

            // Setup Background Camera Frame Analysis Loop
            setupBackgroundCamera()

            // Listen to settings changes
            serviceScope.launch {
                settingsRepo.settings.collect { newSettings ->
                    gestureEngine.updateSettings(newSettings)
                    handLandmarkerHelper?.minHandDetectionConfidence = newSettings.confidenceThreshold
                    if (newSettings.isOverlayEnabled) {
                        overlayManager.show()
                    } else {
                        overlayManager.hide()
                    }
                }
            }

            // Listen to gesture events and dispatch actions
            serviceScope.launch {
                gestureEngine.gestureEvents.collect { event ->
                    handleGestureEvent(event)
                }
            }

            // Listen to engine status for overlay updates
            serviceScope.launch {
                gestureEngine.status.collect { status ->
                    overlayManager.updateStatus(status)
                }
            }

            if (settingsRepo.settings.value.isOverlayEnabled) {
                overlayManager.show()
            }

            Log.i(TAG, "Cheironomy Foreground Service started successfully with camera loop.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}", e)
            stopForegroundService()
        }
    }

    private fun setupInferenceEngine() {
        val currentSettings = settingsRepo.settings.value
        handLandmarkerHelper = HandLandmarkerHelper(
            context = this,
            minHandDetectionConfidence = currentSettings.confidenceThreshold,
            minHandTrackingConfidence = currentSettings.confidenceThreshold,
            handLandmarkerHelperListener = object : HandLandmarkerHelper.LandmarkerListener {
                override fun onError(error: String, errorCode: Int) {
                    Log.e(TAG, "Inference error: $error")
                }

                override fun onResults(resultBundle: HandLandmarkResultBundle) {
                    gestureEngine.processFrame(resultBundle)
                }
            }
        )
    }

    private fun setupBackgroundCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also {
                        val helper = handLandmarkerHelper ?: return@also
                        it.setAnalyzer(
                            backgroundExecutor,
                            HandLandmarkAnalyzer(helper, isFrontCamera = true)
                        )
                    }

                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build()

                cameraProvider?.unbindAll()
                cameraProvider?.bindToLifecycle(
                    this,
                    cameraSelector,
                    imageAnalyzer
                )
                Log.i(TAG, "Headless background camera bound successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind background camera: ${e.message}", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun handleGestureEvent(event: GestureEvent) {
        val targetAction = when (event) {
            is GestureEvent.CustomGestureTriggered -> event.template.action
        }

        if (targetAction == GestureAction.NONE) return

        Log.i(TAG, "Executing Action: ${targetAction.displayName} for template: ${(event as GestureEvent.CustomGestureTriggered).template.name}")
        overlayManager.showActionTriggered(targetAction)

        // Route to Media or Accessibility
        when (targetAction) {
            GestureAction.MEDIA_PLAY_PAUSE,
            GestureAction.MEDIA_NEXT,
            GestureAction.MEDIA_PREVIOUS -> {
                mediaDispatcher.dispatchAction(targetAction)
            }
            GestureAction.SWIPE_LEFT,
            GestureAction.SWIPE_RIGHT,
            GestureAction.SCROLL_UP,
            GestureAction.SCROLL_DOWN -> {
                CheironomyAccessibilityService.dispatchAction(targetAction)
            }
            GestureAction.NONE -> Unit
        }
    }

    private fun stopForegroundService() {
        _isRunning.value = false
        overlayManager.hide()
        handLandmarkerHelper?.clearHandLandmarker()
        cameraProvider?.unbindAll()
        serviceLifecycle.currentState = Lifecycle.State.DESTROYED
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "Foreground service stopped.")
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, CheironomyForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CheironomyApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                0,
                getString(R.string.action_stop_service),
                stopPendingIntent
            )
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        overlayManager.hide()
        handLandmarkerHelper?.clearHandLandmarker()
        cameraProvider?.unbindAll()
        backgroundExecutor.shutdown()
        serviceScope.cancel()
        serviceLifecycle.currentState = Lifecycle.State.DESTROYED
        Log.i(TAG, "Foreground service destroyed.")
    }
}
