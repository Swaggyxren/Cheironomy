package com.ydnar.cheironomy.gesture.classifier

import com.ydnar.cheironomy.gesture.model.GestureEvent
import com.ydnar.cheironomy.gesture.model.SwipeDirection
import kotlin.math.abs
import kotlin.math.hypot

/**
 * State machine for MotionDeltaTracker.
 */
enum class MotionTrackerState {
    IDLE,               // Resting or waiting for motion to cross start threshold
    TRACKING,           // Motion actively accumulating above start threshold
    RECOGNIZED          // Swipe gesture threshold crossed and event emitted
}

/**
 * Tracks 1€-filtered palm centroid trajectory across frames and classifies directional swipes
 * using distinct start, recognition, and decay thresholds.
 */
class MotionDeltaTracker(
    var triggerThreshold: Float = DEFAULT_TRIGGER_THRESHOLD,
    private val windowDurationMs: Long = 350L
) {

    // Motion start threshold: motion must exceed this to transition from IDLE to TRACKING
    val startThreshold: Float
        get() = (triggerThreshold * 0.22f).coerceAtLeast(0.035f)

    // Motion decay threshold: motion falling below this resets state back to IDLE without firing
    val decayThreshold: Float
        get() = (triggerThreshold * 0.15f).coerceAtLeast(0.02f)

    var state: MotionTrackerState = MotionTrackerState.IDLE
        private set

    // Live diagnostics for UI overlay and telemetry
    var currentDeltaX: Float = 0f
        private set
    var currentDeltaY: Float = 0f
        private set
    var currentDistance: Float = 0f
        private set
    var currentVelocity: Float = 0f
        private set

    private data class TimestampedPoint(
        val x: Float,
        val y: Float,
        val timestampMs: Long
    )

    private val pointHistory = ArrayDeque<TimestampedPoint>()

    /**
     * Feeds a 1€-filtered centroid coordinate into tracker.
     * Returns a GestureEvent.MotionSwipe if a full swipe motion is recognized,
     * or null otherwise.
     */
    fun processCentroid(filteredX: Float, filteredY: Float, timestampMs: Long): GestureEvent.MotionSwipe? {
        // 1. Append to history buffer
        pointHistory.addLast(TimestampedPoint(filteredX, filteredY, timestampMs))

        // 2. Evict entries outside the sliding window duration
        while (pointHistory.isNotEmpty() && (timestampMs - pointHistory.first().timestampMs > windowDurationMs)) {
            pointHistory.removeFirst()
        }

        if (pointHistory.size < 3) {
            currentDeltaX = 0f
            currentDeltaY = 0f
            currentDistance = 0f
            currentVelocity = 0f
            state = MotionTrackerState.IDLE
            return null
        }

        val oldestPoint = pointHistory.first()
        val deltaX = filteredX - oldestPoint.x
        val deltaY = filteredY - oldestPoint.y
        val elapsedSec = (timestampMs - oldestPoint.timestampMs) / 1000f

        currentDeltaX = deltaX
        currentDeltaY = deltaY

        if (elapsedSec <= 0.03f) return null

        val absDeltaX = abs(deltaX)
        val absDeltaY = abs(deltaY)
        val totalDistance = hypot(deltaX, deltaY)
        currentDistance = totalDistance
        val velocity = totalDistance / elapsedSec
        currentVelocity = velocity

        // 3. State machine transitions
        if (totalDistance < decayThreshold) {
            // Motion has decayed back to resting state
            state = MotionTrackerState.IDLE
            return null
        } else if (totalDistance >= startThreshold && totalDistance < triggerThreshold) {
            // Motion has started and is accumulating
            state = MotionTrackerState.TRACKING
        }

        // 4. Trigger swipe only when motion crosses recognition triggerThreshold with dominant axis
        if (absDeltaX >= triggerThreshold && absDeltaX > absDeltaY * 1.2f) {
            val direction = if (deltaX > 0f) SwipeDirection.RIGHT else SwipeDirection.LEFT
            state = MotionTrackerState.RECOGNIZED
            clearHistory()
            return GestureEvent.MotionSwipe(
                direction = direction,
                displacement = absDeltaX,
                velocity = velocity
            )
        } else if (absDeltaY >= triggerThreshold && absDeltaY > absDeltaX * 1.2f) {
            val direction = if (deltaY > 0f) SwipeDirection.DOWN else SwipeDirection.UP
            state = MotionTrackerState.RECOGNIZED
            clearHistory()
            return GestureEvent.MotionSwipe(
                direction = direction,
                displacement = absDeltaY,
                velocity = velocity
            )
        }

        return null
    }

    fun clearHistory() {
        pointHistory.clear()
    }

    fun clear() {
        clearHistory()
        state = MotionTrackerState.IDLE
        currentDeltaX = 0f
        currentDeltaY = 0f
        currentDistance = 0f
        currentVelocity = 0f
    }

    companion object {
        const val DEFAULT_TRIGGER_THRESHOLD = 0.20f
    }
}
