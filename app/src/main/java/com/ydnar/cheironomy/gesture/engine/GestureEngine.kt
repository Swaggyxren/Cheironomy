package com.ydnar.cheironomy.gesture.engine

import android.os.SystemClock
import android.util.Log
import com.ydnar.cheironomy.data.AppSettings
import com.ydnar.cheironomy.data.template.GestureTemplate.MotionGestureTemplate
import com.ydnar.cheironomy.data.template.GestureTemplate.StaticGestureTemplate
import com.ydnar.cheironomy.data.template.Point2D
import com.ydnar.cheironomy.gesture.classifier.PalmCentroidHelper
import com.ydnar.cheironomy.gesture.classifier.MotionTemplateMatcher
import com.ydnar.cheironomy.gesture.classifier.StaticTemplateMatcher
import com.ydnar.cheironomy.gesture.classifier.TrajectoryNormalizer
import com.ydnar.cheironomy.gesture.filter.OneEuroFilter2D
import com.ydnar.cheironomy.gesture.filter.OneEuroFilterLandmarks
import com.ydnar.cheironomy.gesture.model.GestureEvent
import com.ydnar.cheironomy.gesture.model.HandLandmarkResultBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

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
    IDLE,               // Hand detected and ready (or no templates registered)
    HOLDING,            // Holding a static pose
    TRACKING,           // Tracking motion gesture
    RECOGNIZED          // Gesture action fired
}

/**
 * Central gesture engine coordinating One Euro Filter signal smoothing,
 * explicit gesture lifecycle state machine, confidence gating, and Nearest-Neighbor DTW/Euclidean matching.
 * Exclusively recognizes user-recorded custom templates.
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

    private val _recognizedGestureName = MutableStateFlow<String?>(null)
    val recognizedGestureName: StateFlow<String?> = _recognizedGestureName.asStateFlow()

    // Real-time telemetry for diagnostics overlay and filter inspection
    private val _telemetryConfidence = MutableStateFlow(0f)
    val telemetryConfidence: StateFlow<Float> = _telemetryConfidence.asStateFlow()

    private val _telemetryRawCentroid = MutableStateFlow(Point2D(0f, 0f))
    val telemetryRawCentroid: StateFlow<Point2D> = _telemetryRawCentroid.asStateFlow()

    private val _telemetryFilteredCentroid = MutableStateFlow(Point2D(0f, 0f))
    val telemetryFilteredCentroid: StateFlow<Point2D> = _telemetryFilteredCentroid.asStateFlow()

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

    // Timestamped trajectory buffer for custom motion matching
    private data class TimedCentroid(val point: Point2D, val timestampMs: Long)
    private val motionBuffer = ArrayDeque<TimedCentroid>()
    private var lastMotionCheckTimeMs: Long = 0L

    // Anchor centroid when motion stroke begins
    private var motionAnchorPoint: Point2D? = null
    private var motionAnchorTimeMs: Long = 0L
    private var isMotionTrackingActive: Boolean = false

    private var activeStaticTemplateId: String? = null
    private var staticTemplateStartTimeMs: Long = 0L
    private var lastTriggerTimeMs: Long = 0L

    // Re-acquisition debounce counter (ignoring first 4 frames on hand entry)
    private var consecutiveHandFrames: Int = 0

    fun updateSettings(newSettings: AppSettings) {
        settings = newSettings
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
            _recognizedGestureName.value = null
            return
        }

        consecutiveHandFrames++
        val rawLandmarks = allLandmarks[0]
        val (rawCentroidX, rawCentroidY) = PalmCentroidHelper.calculatePalmCentroid(rawLandmarks)
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
            _recognizedGestureName.value = null
            motionBuffer.clear()
            motionBuffer.addLast(TimedCentroid(filteredCentroidPoint, now))
            motionAnchorPoint = filteredCentroidPoint
            motionAnchorTimeMs = now
            return
        }

        // 4. Rate-Limit Safety Backstop Check (prevents physical double-fire only)
        val inSafetyWindow = (now - lastTriggerTimeMs < SAFETY_RATE_LIMIT_MS)

        // 5. Static Pose Classification (Nearest-Neighbor match among registered StaticGestureTemplates)
        val staticTemplates = settings.customTemplates.filterIsInstance<StaticGestureTemplate>()
        val staticMatch = if (staticTemplates.isNotEmpty()) {
            StaticTemplateMatcher.match(
                landmarks = filteredLandmarks,
                templates = staticTemplates,
                rejectCeiling = settings.staticRejectCeiling,
                marginThreshold = settings.staticMarginThreshold
            )
        } else null

        if (staticMatch != null) {
            val (template, dist) = staticMatch
            if (template.id == activeStaticTemplateId) {
                val heldDuration = now - staticTemplateStartTimeMs
                if (heldDuration >= settings.holdDurationMs && !inSafetyWindow) {
                    dispatchRecognizedAction(GestureEvent.CustomGestureTriggered(template), now)
                    return
                }
                _gestureState.value = GestureState.HOLDING
                _status.value = GestureEngineStatus.HOLDING
                _recognizedGestureName.value = template.name
            } else {
                activeStaticTemplateId = template.id
                staticTemplateStartTimeMs = now
                _gestureState.value = GestureState.HOLDING
                _status.value = GestureEngineStatus.HOLDING
                _recognizedGestureName.value = template.name
            }

            // Clear motion tracking while holding static pose
            motionBuffer.clear()
            motionAnchorPoint = filteredCentroidPoint
            motionAnchorTimeMs = now
            isMotionTrackingActive = false
            _telemetryDeltaX.value = 0f
            _telemetryDeltaY.value = 0f
            _telemetryVelocity.value = 0f
            return
        } else {
            // Pose broken / released: transition from HOLDING back to IDLE immediately with 0 delay
            if (_gestureState.value == GestureState.HOLDING) {
                _gestureState.value = GestureState.IDLE
                _status.value = GestureEngineStatus.IDLE
                _recognizedGestureName.value = null
            }
            activeStaticTemplateId = null
            staticTemplateStartTimeMs = 0L
        }

        // 6. Motion Delta Tracking & Nearest-Neighbor DTW Template Recognition
        if (motionAnchorPoint == null) {
            motionAnchorPoint = filteredCentroidPoint
            motionAnchorTimeMs = now
        }

        val anchor = motionAnchorPoint!!
        val dx = filteredCentroidX - anchor.x
        val dy = filteredCentroidY - anchor.y
        val displacement = hypot(dx, dy)
        val dtSec = ((now - motionAnchorTimeMs) / 1000f).coerceAtLeast(0.01f)
        val velocity = displacement / dtSec

        _telemetryDeltaX.value = dx
        _telemetryDeltaY.value = dy
        _telemetryVelocity.value = velocity

        // Append to rolling motion buffer
        motionBuffer.addLast(TimedCentroid(filteredCentroidPoint, now))
        while (motionBuffer.isNotEmpty() && (now - motionBuffer.first().timestampMs > 1500L)) {
            motionBuffer.removeFirst()
        }

        val motionTemplates = settings.customTemplates.filterIsInstance<MotionGestureTemplate>()

        if (displacement >= MOTION_START_THRESHOLD) {
            isMotionTrackingActive = true
            _gestureState.value = GestureState.TRACKING
            _status.value = GestureEngineStatus.TRACKING

            // Evaluate registered custom motion templates via Nearest-Neighbor DTW
            if (motionTemplates.isNotEmpty() && (now - lastMotionCheckTimeMs > 80L)) {
                lastMotionCheckTimeMs = now
                val matchedTemplate = evaluateMotionBuffer(motionTemplates)
                if (matchedTemplate != null && !inSafetyWindow) {
                    dispatchRecognizedAction(GestureEvent.CustomGestureTriggered(matchedTemplate), now)
                    return
                }
            }
        } else if (isMotionTrackingActive && displacement < MOTION_DECAY_THRESHOLD) {
            // Motion decayed back below threshold -> return to IDLE immediately
            isMotionTrackingActive = false
            motionAnchorPoint = filteredCentroidPoint
            motionAnchorTimeMs = now
            _gestureState.value = GestureState.IDLE
            _status.value = GestureEngineStatus.IDLE
            _recognizedGestureName.value = null
        } else if (!isMotionTrackingActive) {
            // Update resting anchor
            motionAnchorPoint = filteredCentroidPoint
            motionAnchorTimeMs = now
            _gestureState.value = GestureState.IDLE
            _status.value = GestureEngineStatus.IDLE
            _recognizedGestureName.value = null
        }
    }

    private fun evaluateMotionBuffer(templates: List<MotionGestureTemplate>): MotionGestureTemplate? {
        if (motionBuffer.size < 6) return null

        val points = motionBuffer.map { it.point }
        val normResult = TrajectoryNormalizer.normalizeTrajectory(points) ?: return null
        val (normalizedPoints, stats) = normResult

        if (stats.totalPathLength < 0.08f) return null

        val match = MotionTemplateMatcher.match(
            candidateTrajectory = normalizedPoints,
            candidateStats = stats,
            templates = templates,
            rejectCeiling = settings.motionRejectCeiling,
            marginThreshold = settings.motionMarginThreshold,
            prefilterTolerance = settings.motionPrefilterTolerance
        )

        return match?.first
    }

    private fun dispatchRecognizedAction(event: GestureEvent, timestampMs: Long) {
        lastTriggerTimeMs = timestampMs
        _gestureState.value = GestureState.RECOGNIZED
        _status.value = GestureEngineStatus.RECOGNIZED

        val templateName = when (event) {
            is GestureEvent.CustomGestureTriggered -> event.template.name
        }
        _recognizedGestureName.value = templateName

        // Reset tracking buffers
        activeStaticTemplateId = null
        staticTemplateStartTimeMs = 0L
        isMotionTrackingActive = false
        motionBuffer.clear()
        motionAnchorPoint = null
        _telemetryDeltaX.value = 0f
        _telemetryDeltaY.value = 0f
        _telemetryVelocity.value = 0f

        scope.launch {
            Log.i(TAG, "Recognized and dispatched GestureEvent: $event ($templateName)")
            _gestureEvents.emit(event)
        }
    }

    private fun resetToIdleState() {
        consecutiveHandFrames = 0
        oneEuroCentroidFilter.reset()
        oneEuroLandmarksFilter.reset()
        motionBuffer.clear()
        motionAnchorPoint = null
        isMotionTrackingActive = false
        activeStaticTemplateId = null
        staticTemplateStartTimeMs = 0L
        _gestureState.value = GestureState.IDLE
        _recognizedGestureName.value = null
        _telemetryDeltaX.value = 0f
        _telemetryDeltaY.value = 0f
        _telemetryVelocity.value = 0f
    }

    companion object {
        private const val TAG = "GestureEngine"
        private const val WARMUP_FRAMES = 4             // ~120ms debounce on re-acquisition
        private const val SAFETY_RATE_LIMIT_MS = 350L    // Minimal safety rate-limit backstop against double-firing
        private const val MOTION_START_THRESHOLD = 0.044f // Screen space displacement to enter TRACKING
        private const val MOTION_DECAY_THRESHOLD = 0.030f // Displacement threshold below which motion settles to IDLE
    }
}
