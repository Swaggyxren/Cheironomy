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

    // Anchor centroid when motion stroke begins
    private var motionAnchorPoint: Point2D? = null
    private var motionAnchorTimeMs: Long = 0L
    private var isMotionTrackingActive: Boolean = false

    // Motion stroke lifecycle tracking (segmentation + completion gating)
    private var motionStartTimeMs: Long = 0L
    private var settledFrameCount: Int = 0
    private var lastCentroidPoint: Point2D? = null
    private var lastFrameTimeMs: Long = 0L
    private var strokePeakVelocity: Float = 0f

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

        // Palm-scale plausibility gate: reject degenerate / hallucinated hands.
        val palmScale = rawLandmarks[9].let { mcp ->
            hypot(mcp.x() - rawLandmarks[0].x(), mcp.y() - rawLandmarks[0].y())
        }
        if (palmScale < MIN_PALM_SCALE || palmScale > MAX_PALM_SCALE) {
            resetToIdleState()
            _status.value = GestureEngineStatus.SCANNING
            _recognizedGestureName.value = null
            return
        }

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
            lastCentroidPoint = filteredCentroidPoint
            lastFrameTimeMs = now
            return
        }

        // Per-frame displacement and instant velocity calculation
        val frameDelta = lastCentroidPoint?.let {
            hypot(filteredCentroidX - it.x, filteredCentroidY - it.y)
        } ?: 0f
        val frameDtSec = if (lastFrameTimeMs > 0L) {
            ((now - lastFrameTimeMs) / 1000f).coerceIn(0.005f, 0.2f)
        } else {
            0.033f
        }
        lastFrameTimeMs = now
        val instantVelocity = frameDelta / frameDtSec
        _telemetryVelocity.value = instantVelocity

        val isHandStill = instantVelocity <= STILLNESS_MAX_VELOCITY

        // 4. Rate-Limit Safety Backstop Check (prevents physical double-fire only)
        val inSafetyWindow = (now - lastTriggerTimeMs < SAFETY_RATE_LIMIT_MS)

        // 5. Static Pose Classification (Nearest-Neighbor match among registered StaticGestureTemplates)
        // MUTUAL EXCLUSION: If motion stroke is actively being tracked, bypass static pose classification
        // to prevent erasing in-flight motion trajectories. Furthermore, require hand stillness to accumulate hold time.
        val staticTemplates = settings.customTemplates.filterIsInstance<StaticGestureTemplate>()
        val staticMatch = if (!isMotionTrackingActive && staticTemplates.isNotEmpty() && isHandStill) {
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
            settledFrameCount = 0
            lastCentroidPoint = filteredCentroidPoint
            _telemetryDeltaX.value = 0f
            _telemetryDeltaY.value = 0f
            return
        } else {
            // Pose broken / released / moved: transition from HOLDING back to IDLE immediately with 0 delay
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

        _telemetryDeltaX.value = dx
        _telemetryDeltaY.value = dy

        val motionTemplates = settings.customTemplates.filterIsInstance<MotionGestureTemplate>()

        if (!isMotionTrackingActive) {
            // Intentional stroke entry gate: requires significant displacement, frame movement, and velocity
            if (displacement >= MOTION_START_THRESHOLD && frameDelta >= MOTION_FRAME_DELTA_THRESHOLD && instantVelocity >= MOTION_START_VELOCITY) {
                isMotionTrackingActive = true
                motionStartTimeMs = now
                strokePeakVelocity = instantVelocity
                settledFrameCount = 0
                motionBuffer.clear()
                motionBuffer.addLast(TimedCentroid(anchor, motionAnchorTimeMs))
                motionBuffer.addLast(TimedCentroid(filteredCentroidPoint, now))
                lastCentroidPoint = filteredCentroidPoint
                _gestureState.value = GestureState.TRACKING
                _status.value = GestureEngineStatus.TRACKING
                _recognizedGestureName.value = null
            } else {
                // If hand was resting or moving slowly, drift the anchor point along with the hand
                if (frameDelta < MOTION_FRAME_DELTA_THRESHOLD || instantVelocity < STILLNESS_MAX_VELOCITY * 1.5f) {
                    motionAnchorPoint = filteredCentroidPoint
                    motionAnchorTimeMs = now
                }
                lastCentroidPoint = filteredCentroidPoint
                if (_gestureState.value != GestureState.HOLDING) {
                    _gestureState.value = GestureState.IDLE
                    _status.value = GestureEngineStatus.IDLE
                    _recognizedGestureName.value = null
                }
            }
            return
        }

        // TRACKING active: accumulate samples for the live stroke and record peak velocity
        strokePeakVelocity = maxOf(strokePeakVelocity, instantVelocity)
        motionBuffer.addLast(TimedCentroid(filteredCentroidPoint, now))
        while (motionBuffer.isNotEmpty() && (now - motionBuffer.first().timestampMs > MAX_STROKE_WINDOW_MS)) {
            motionBuffer.removeFirst()
        }
        lastCentroidPoint = filteredCentroidPoint

        val isSettled = frameDelta < MOTION_SETTLED_DELTA_THRESHOLD
        settledFrameCount = if (isSettled) settledFrameCount + 1 else 0

        if (settledFrameCount >= SETTLED_FRAMES_REQUIRED) {
            val strokeDurationMs = now - motionStartTimeMs
            val hasPeakVelocity = strokePeakVelocity >= MIN_STROKE_PEAK_VELOCITY
            val matchedTemplate =
                if (motionTemplates.isNotEmpty() && strokeDurationMs >= MIN_STROKE_DURATION_MS && hasPeakVelocity) {
                    evaluateMotionBuffer(motionTemplates)
                } else null

            if (matchedTemplate != null && !inSafetyWindow) {
                dispatchRecognizedAction(GestureEvent.CustomGestureTriggered(matchedTemplate), now)
            } else {
                resetMotionTracking(filteredCentroidPoint, now)
            }
        } else if ((now - motionStartTimeMs) > MAX_STROKE_DURATION_MS) {
            resetMotionTracking(filteredCentroidPoint, now)
        }
    }

    private fun resetMotionTracking(centroid: Point2D, now: Long) {
        isMotionTrackingActive = false
        motionStartTimeMs = 0L
        strokePeakVelocity = 0f
        settledFrameCount = 0
        lastCentroidPoint = centroid
        motionBuffer.clear()
        motionAnchorPoint = centroid
        motionAnchorTimeMs = now
        _gestureState.value = GestureState.IDLE
        _status.value = GestureEngineStatus.IDLE
        _recognizedGestureName.value = null
        _telemetryDeltaX.value = 0f
        _telemetryDeltaY.value = 0f
    }

    private fun evaluateMotionBuffer(templates: List<MotionGestureTemplate>): MotionGestureTemplate? {
        if (motionBuffer.size < MIN_MOTION_BUFFER_POINTS) return null

        val points = motionBuffer.map { it.point }
        val normResult = TrajectoryNormalizer.normalizeTrajectory(points) ?: return null
        val (normalizedPoints, stats) = normResult

        if (stats.totalPathLength < MIN_PATH_LENGTH) return null

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
        motionStartTimeMs = 0L
        strokePeakVelocity = 0f
        settledFrameCount = 0
        lastCentroidPoint = null
        motionBuffer.clear()
        motionAnchorPoint = null
        _telemetryDeltaX.value = 0f
        _telemetryDeltaY.value = 0f

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
        motionAnchorTimeMs = 0L
        isMotionTrackingActive = false
        motionStartTimeMs = 0L
        strokePeakVelocity = 0f
        lastFrameTimeMs = 0L
        settledFrameCount = 0
        lastCentroidPoint = null
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
        private const val STILLNESS_MAX_VELOCITY = 0.08f // Maximum velocity (units/sec) allowed while holding a static pose
        private const val MOTION_START_THRESHOLD = 0.12f // Screen-space displacement to enter TRACKING
        private const val MOTION_FRAME_DELTA_THRESHOLD = 0.015f  // Per-frame movement proving a deliberate stroke
        private const val MOTION_START_VELOCITY = 0.20f  // Minimum velocity to trigger motion tracking
        private const val MIN_STROKE_PEAK_VELOCITY = 0.28f // Minimum peak velocity required during deliberate stroke
        private const val MOTION_SETTLED_DELTA_THRESHOLD = 0.008f // Per-frame movement below which the stroke is settled
        private const val SETTLED_FRAMES_REQUIRED = 3            // Consecutive settled frames before completing the stroke
        private const val MIN_STROKE_DURATION_MS = 150L          // Minimum deliberate stroke duration
        private const val MAX_STROKE_DURATION_MS = 1600L         // Abort stale strokes that never settle
        private const val MAX_STROKE_WINDOW_MS = 2000L           // Rolling buffer cap while tracking
        private const val MIN_MOTION_BUFFER_POINTS = 8            // Minimum trajectory samples before matching
        private const val MIN_PATH_LENGTH = 0.20f                 // Minimum physical path length to spend DTW cost
        private const val MIN_PALM_SCALE = 0.04f
        private const val MAX_PALM_SCALE = 0.60f
    }
}
