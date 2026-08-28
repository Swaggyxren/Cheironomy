package com.ydnar.cheironomy.gesture.engine

import android.os.SystemClock
import android.util.Log
import com.ydnar.cheironomy.data.AppSettings
import com.ydnar.cheironomy.gesture.classifier.MotionDeltaTracker
import com.ydnar.cheironomy.gesture.classifier.PoseClassifier
import com.ydnar.cheironomy.gesture.model.GestureEvent
import com.ydnar.cheironomy.gesture.model.HandLandmarkResultBundle
import com.ydnar.cheironomy.gesture.model.PoseType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State of the gesture recognition pipeline.
 */
enum class GestureEngineStatus {
    IDLE,
    SCANNING,
    HAND_DETECTED,
    COOLDOWN,
    ACTION_TRIGGERED
}

/**
 * Central gesture engine coordinating pose classification, motion tracking, and debounced dispatch.
 */
class GestureEngine(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    var settings: AppSettings = AppSettings()
) {

    private val _gestureEvents = MutableSharedFlow<GestureEvent>(extraBufferCapacity = 8)
    val gestureEvents: SharedFlow<GestureEvent> = _gestureEvents.asSharedFlow()

    private val _status = MutableStateFlow(GestureEngineStatus.SCANNING)
    val status: StateFlow<GestureEngineStatus> = _status.asStateFlow()

    private val _currentPose = MutableStateFlow(PoseType.UNKNOWN)
    val currentPose: StateFlow<PoseType> = _currentPose.asStateFlow()

    private val motionTracker = MotionDeltaTracker(swipeThreshold = settings.swipeSensitivity)

    private var poseStartTimeMs: Long = 0L
    private var lastTriggerTimeMs: Long = 0L
    private var activePose: PoseType = PoseType.UNKNOWN

    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
        motionTracker.swipeThreshold = newSettings.swipeSensitivity
    }

    fun processFrame(resultBundle: HandLandmarkResultBundle) {
        val now = SystemClock.uptimeMillis()
        val result = resultBundle.result
        val allLandmarks = result?.landmarks()

        // 1. Check Cooldown state
        val isInCooldown = (now - lastTriggerTimeMs < settings.cooldownMs)
        if (isInCooldown) {
            _status.value = GestureEngineStatus.COOLDOWN
            motionTracker.clear()
            poseStartTimeMs = 0L
            activePose = PoseType.UNKNOWN
            return
        }

        // 2. Handle no-hand detected
        if (allLandmarks == null || allLandmarks.isEmpty() || allLandmarks[0].size < 21) {
            _status.value = GestureEngineStatus.SCANNING
            _currentPose.value = PoseType.UNKNOWN
            poseStartTimeMs = 0L
            activePose = PoseType.UNKNOWN
            motionTracker.clear()
            return
        }

        val landmarks = allLandmarks[0]
        _status.value = GestureEngineStatus.HAND_DETECTED

        // 3. Classify Pose
        val pose = PoseClassifier.classifyPose(landmarks)
        _currentPose.value = pose

        // 4. Check Static Pose Hold (e.g. Open Palm)
        if (pose != PoseType.UNKNOWN) {
            if (pose == activePose) {
                val heldDuration = now - poseStartTimeMs
                if (heldDuration >= settings.holdDurationMs) {
                    // Trigger Static Pose Action
                    triggerGestureEvent(GestureEvent.StaticPoseHeld(pose, heldDuration), now)
                    return
                }
            } else {
                activePose = pose
                poseStartTimeMs = now
            }
        } else {
            activePose = PoseType.UNKNOWN
            poseStartTimeMs = 0L
        }

        // 5. Check Motion Delta Swipes (only if not holding a static palm)
        if (pose == PoseType.UNKNOWN || pose == PoseType.OPEN_PALM) {
            val (centroidX, centroidY) = PoseClassifier.calculatePalmCentroid(landmarks)
            val swipeEvent = motionTracker.processCentroid(centroidX, centroidY, now)
            if (swipeEvent != null) {
                triggerGestureEvent(swipeEvent, now)
            }
        }
    }

    private fun triggerGestureEvent(event: GestureEvent, timestampMs: Long) {
        lastTriggerTimeMs = timestampMs
        poseStartTimeMs = 0L
        activePose = PoseType.UNKNOWN
        motionTracker.clear()
        _status.value = GestureEngineStatus.ACTION_TRIGGERED

        scope.launch {
            Log.i(TAG, "Dispatched GestureEvent: $event")
            _gestureEvents.emit(event)
        }
    }

    companion object {
        private const val TAG = "GestureEngine"
    }
}
