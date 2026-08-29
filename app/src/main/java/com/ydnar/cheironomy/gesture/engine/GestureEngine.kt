package com.ydnar.cheironomy.gesture.engine

import android.os.SystemClock
import android.util.Log
import com.ydnar.cheironomy.data.AppSettings
import com.ydnar.cheironomy.data.template.GestureTemplate.MotionGestureTemplate
import com.ydnar.cheironomy.data.template.GestureTemplate.StaticGestureTemplate
import com.ydnar.cheironomy.data.template.Point2D
import com.ydnar.cheironomy.gesture.classifier.MotionDeltaTracker
import com.ydnar.cheironomy.gesture.classifier.MotionTemplateMatcher
import com.ydnar.cheironomy.gesture.classifier.MotionTrackerState
import com.ydnar.cheironomy.gesture.classifier.PoseClassifier
import com.ydnar.cheironomy.gesture.classifier.StaticTemplateMatcher
import com.ydnar.cheironomy.gesture.classifier.TrajectoryNormalizer
import com.ydnar.cheironomy.gesture.filter.OneEuroFilter2D
import com.ydnar.cheironomy.gesture.filter.OneEuroFilterLandmarks
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
 * State machine for gesture lifecycle.
 */
enum class GestureState {
    IDLE,               // Hand present, resting / awaiting gesture initiation
    HOLDING,            // Static pose recognized, accumulating hold time
    TRACKING,           // Motion exceeding start threshold, buffering trajectory
    RECOGNIZED          // Gesture pattern confirmed and dispatched
}

/**
 * High-level status of the gesture recognition pipeline for UI and floating overlay.
 */
enum class GestureEngineStatus {
    SCANNING,           // No hand detected
    WARMING_UP,         // Re-acquisition debounce (ignoring initial jump frames)
    IDLE,               // Hand detected and ready
    HOLDING,            // Holding a static pose
    TRACKING,           // Tracking motion gesture
    RECOGNIZED          // Gesture action fired
}

/**
 * Central gesture engine coordinating One Euro Filter signal smoothing,
 * explicit gesture lifecycle state machine, confidence gating, and DTW matching.
 */
class GestureEngine(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    var settings: AppSettings = AppSettings()
) {

    private val _gestureEvents = MutableSharedFlow<GestureEvent>(extraBufferCapacity = 8)
    val gestureEvents: SharedFlow<GestureEvent> = _gestureEvents.asSharedFlow()

    private val _status = MutableStateFlow(GestureEngineStatus.SCANNING)
    val status: StateFlow<GestureEngineStatus> = _status.asStateFlow()

    private val _gestureState = MutableStateFlow(GestureState.IDLE)
    val gestureState: StateFlow<GestureState> = _gestureState.asStateFlow()

    private val _currentPose = MutableStateFlow(PoseType.UNKNOWN)
    val currentPose: StateFlow<PoseType> = _currentPose.asStateFlow()

    // Real-time telemetry for diagnostics overlay and filter inspection
    private val _telemetryConfidence = MutableStateFlow(0f)
    val telemetryConfidence: StateFlow<Float> = _telemetryConfidence.asStateFlow()

    private val _telemetryRawCentroid = MutableStateFlow(Point2D(0f, 0f))
    val telemetryRawCentroid: StateFlow<Point2D> = _telemetryRawCentroid.asStateFlow()

    private val _telemetryFilteredCentroid = MutableStateFlow(Point2D(0f, 0f))
    val telemetryFilteredCentroid: StateFlow<Point2D> = _telemetryFilteredCentroid.asStateFlow()

    private val _telemetryTrackerState = MutableStateFlow(MotionTrackerState.IDLE)
    val telemetryTrackerState: StateFlow<MotionTrackerState> = _telemetryTrackerState.asStateFlow()

    private val _telemetryDeltaX = MutableStateFlow(0f)
    val telemetryDeltaX: StateFlow<Float> = _telemetryDeltaX.asStateFlow()

    private val _telemetryDeltaY = MutableStateFlow(0f)
    val telemetryDeltaY: StateFlow<Float> = _telemetryDeltaY.asStateFlow()

    private val _telemetryVelocity = MutableStateFlow(0f)
    val telemetryVelocity: StateFlow<Float> = _telemetryVelocity.asStateFlow()

    private val _telemetryFps = MutableStateFlow(0f)
    val telemetryFps: StateFlow<Float> = _telemetryFps.asStateFlow()

    // 1€ Filters for landmark coordinates and palm centroid
    val oneEuroCentroidFilter = OneEuroFilter2D(minCutoff = 1.0f, beta = 0.007f)
    val oneEuroLandmarksFilter = OneEuroFilterLandmarks(minCutoff = 1.0f, beta = 0.007f)

    val motionTracker = MotionDeltaTracker(triggerThreshold = settings.swipeSensitivity)

    // Timestamped trajectory buffer for custom motion matching
    private data class TimedCentroid(val point: Point2D, val timestampMs: Long)
    private val motionBuffer = ArrayDeque<TimedCentroid>()
    private var lastMotionCheckTimeMs: Long = 0L

    private var poseStartTimeMs: Long = 0L
    private var lastTriggerTimeMs: Long = 0L
    private var activePose: PoseType = PoseType.UNKNOWN

    private var activeStaticTemplateId: String? = null
    private var staticTemplateStartTimeMs: Long = 0L

    // Re-acquisition debounce counter (ignoring first 4 frames on hand entry)
    private var consecutiveHandFrames: Int = 0

    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
        motionTracker.triggerThreshold = newSettings.swipeSensitivity
    }

    fun processFrame(resultBundle: HandLandmarkResultBundle) {
        val now = SystemClock.uptimeMillis()
        val timestampSec = now / 1000f
        val result = resultBundle.result
        val allLandmarks = result?.landmarks()
        val confidence = resultBundle.confidence
        val fps = resultBundle.fps

        _telemetryConfidence.value = confidence
        _telemetryFps.value = fps

        // 1. Natural Boundary: Hand Presence & Confidence Gate
        val isConfident = confidence >= (settings.confidenceThreshold - 0.05f)
        val hasLandmarks = (allLandmarks != null && allLandmarks.isNotEmpty() && allLandmarks[0].size >= 21)

        if (!hasLandmarks || !isConfident) {
            // Hand was lost or confidence dropped: immediately reset state machine to IDLE
            resetToIdleState()
            _status.value = GestureEngineStatus.SCANNING
            _currentPose.value = PoseType.UNKNOWN
            return
        }

        consecutiveHandFrames++
        val rawLandmarks = allLandmarks[0]
        val (rawCentroidX, rawCentroidY) = PoseClassifier.calculatePalmCentroid(rawLandmarks)
        _telemetryRawCentroid.value = Point2D(rawCentroidX, rawCentroidY)

        // 2. Apply One Euro Filter immediately on raw landmarks and centroid
        val filteredLandmarks = oneEuroLandmarksFilter.filter(rawLandmarks, timestampSec)
        val (filteredCentroidX, filteredCentroidY) = oneEuroCentroidFilter.filter(rawCentroidX, rawCentroidY, timestampSec)
        val filteredCentroidPoint = Point2D(filteredCentroidX, filteredCentroidY)
        _telemetryFilteredCentroid.value = filteredCentroidPoint

        // 3. Re-acquisition Warmup Debounce: prime the 1€ filter and ignore initial coordinate jump
        if (consecutiveHandFrames <= WARMUP_FRAMES) {
            _status.value = GestureEngineStatus.WARMING_UP
            _gestureState.value = GestureState.IDLE
            _currentPose.value = PoseType.UNKNOWN
            motionBuffer.clear()
            motionBuffer.addLast(TimedCentroid(filteredCentroidPoint, now))
            motionTracker.clear()
            _telemetryTrackerState.value = motionTracker.state
            return
        }

        // 4. Rate-Limit Safety Backstop Check (prevents physical double-fire only)
        val inSafetyWindow = (now - lastTriggerTimeMs < SAFETY_RATE_LIMIT_MS)

        // 5. Static Pose Classification (Custom Templates + Built-in Poses)
        val staticTemplates = settings.customTemplates.filterIsInstance<StaticGestureTemplate>()
        val staticMatch = if (staticTemplates.isNotEmpty()) {
            StaticTemplateMatcher.match(
                landmarks = filteredLandmarks,
                templates = staticTemplates,
                threshold = settings.staticMatchThreshold
            )
        } else null

        val classifiedPose = if (staticMatch == null) {
            PoseClassifier.classifyPose(filteredLandmarks)
        } else PoseType.UNKNOWN

        _currentPose.value = classifiedPose

        val isAnyStaticPoseActive = (staticMatch != null || classifiedPose != PoseType.UNKNOWN)

        if (isAnyStaticPoseActive) {
            // Static Pose Holding State
            if (staticMatch != null) {
                val (template, _) = staticMatch
                if (template.id == activeStaticTemplateId) {
                    val heldDuration = now - staticTemplateStartTimeMs
                    if (heldDuration >= settings.holdDurationMs && !inSafetyWindow) {
                        dispatchRecognizedAction(GestureEvent.CustomGestureTriggered(template), now)
                        return
                    }
                    _gestureState.value = GestureState.HOLDING
                    _status.value = GestureEngineStatus.HOLDING
                } else {
                    activeStaticTemplateId = template.id
                    staticTemplateStartTimeMs = now
                    _gestureState.value = GestureState.HOLDING
                    _status.value = GestureEngineStatus.HOLDING
                }
            } else if (classifiedPose != PoseType.UNKNOWN) {
                if (classifiedPose == activePose) {
                    val heldDuration = now - poseStartTimeMs
                    if (heldDuration >= settings.holdDurationMs && !inSafetyWindow) {
                        dispatchRecognizedAction(GestureEvent.StaticPoseHeld(classifiedPose, heldDuration), now)
                        return
                    }
                    _gestureState.value = GestureState.HOLDING
                    _status.value = GestureEngineStatus.HOLDING
                } else {
                    activePose = classifiedPose
                    poseStartTimeMs = now
                    _gestureState.value = GestureState.HOLDING
                    _status.value = GestureEngineStatus.HOLDING
                }
            }

            // Clear motion buffer while holding a static pose to prevent cross-contamination
            motionBuffer.clear()
            motionTracker.clear()
            _telemetryTrackerState.value = motionTracker.state
            return
        } else {
            // Pose broken / released: transition from HOLDING back to IDLE immediately
            if (_gestureState.value == GestureState.HOLDING) {
                _gestureState.value = GestureState.IDLE
                _status.value = GestureEngineStatus.IDLE
            }
            activePose = PoseType.UNKNOWN
            poseStartTimeMs = 0L
            activeStaticTemplateId = null
        }

        // 6. Motion Delta Tracking & Gesture Recognition
        val swipeEvent = motionTracker.processCentroid(filteredCentroidX, filteredCentroidY, now)
        _telemetryTrackerState.value = motionTracker.state
        _telemetryDeltaX.value = motionTracker.currentDeltaX
        _telemetryDeltaY.value = motionTracker.currentDeltaY
        _telemetryVelocity.value = motionTracker.currentVelocity

        // Append to rolling motion buffer
        motionBuffer.addLast(TimedCentroid(filteredCentroidPoint, now))
        while (motionBuffer.isNotEmpty() && (now - motionBuffer.first().timestampMs > 1500L)) {
            motionBuffer.removeFirst()
        }

        // Check if motion is active (TRACKING state)
        if (motionTracker.state == MotionTrackerState.TRACKING) {
            _gestureState.value = GestureState.TRACKING
            _status.value = GestureEngineStatus.TRACKING

            // Evaluate Custom Motion Templates
            val motionTemplates = settings.customTemplates.filterIsInstance<MotionGestureTemplate>()
            if (motionTemplates.isNotEmpty() && (now - lastMotionCheckTimeMs > 80L)) {
                lastMotionCheckTimeMs = now
                val matchedTemplate = evaluateMotionBuffer(motionTemplates)
                if (matchedTemplate != null && !inSafetyWindow) {
                    dispatchRecognizedAction(GestureEvent.CustomGestureTriggered(matchedTemplate), now)
                    return
                }
            }

            // Evaluate Directional Swipes
            if (swipeEvent != null && !inSafetyWindow) {
                dispatchRecognizedAction(swipeEvent, now)
                return
            }
        } else if (swipeEvent != null && !inSafetyWindow) {
            // Direct swipe threshold cross
            dispatchRecognizedAction(swipeEvent, now)
            return
        } else {
            // Motion decayed back to resting state -> IDLE
            _gestureState.value = GestureState.IDLE
            _status.value = GestureEngineStatus.IDLE
        }
    }

    private fun evaluateMotionBuffer(templates: List<MotionGestureTemplate>): MotionGestureTemplate? {
        if (motionBuffer.size < 6) return null

        val points = motionBuffer.map { it.point }
        val normResult = TrajectoryNormalizer.normalizeTrajectory(points) ?: return null
        val (normalizedPoints, stats) = normResult

        // Only evaluate if there is meaningful path length (> 0.08 normalized screen space)
        if (stats.totalPathLength < 0.08f) return null

        val match = MotionTemplateMatcher.match(
            candidateTrajectory = normalizedPoints,
            candidateStats = stats,
            templates = templates,
            threshold = settings.motionMatchThreshold
        )

        return match?.first
    }

    private fun dispatchRecognizedAction(event: GestureEvent, timestampMs: Long) {
        lastTriggerTimeMs = timestampMs
        _gestureState.value = GestureState.RECOGNIZED
        _status.value = GestureEngineStatus.RECOGNIZED

        // Reset tracking buffers
        poseStartTimeMs = 0L
        activePose = PoseType.UNKNOWN
        activeStaticTemplateId = null
        motionTracker.clear()
        motionBuffer.clear()
        _telemetryTrackerState.value = motionTracker.state

        scope.launch {
            Log.i(TAG, "Recognized and dispatched GestureEvent: $event")
            _gestureEvents.emit(event)
        }
    }

    private fun resetToIdleState() {
        consecutiveHandFrames = 0
        oneEuroCentroidFilter.reset()
        oneEuroLandmarksFilter.reset()
        motionBuffer.clear()
        motionTracker.clear()
        poseStartTimeMs = 0L
        activePose = PoseType.UNKNOWN
        activeStaticTemplateId = null
        _gestureState.value = GestureState.IDLE
        _telemetryTrackerState.value = motionTracker.state
        _telemetryDeltaX.value = 0f
        _telemetryDeltaY.value = 0f
        _telemetryVelocity.value = 0f
    }

    companion object {
        private const val TAG = "GestureEngine"
        private const val WARMUP_FRAMES = 4          // ~120ms debounce on re-acquisition
        private const val SAFETY_RATE_LIMIT_MS = 350L // Minimal safety rate-limit backstop against double-firing
    }
}
