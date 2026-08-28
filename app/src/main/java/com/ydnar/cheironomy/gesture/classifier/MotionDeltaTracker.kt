package com.ydnar.cheironomy.gesture.classifier

import com.ydnar.cheironomy.gesture.model.GestureEvent
import com.ydnar.cheironomy.gesture.model.SwipeDirection
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Tracks palm centroid trajectory across frames and classifies directional swipes.
 */
class MotionDeltaTracker(
    var swipeThreshold: Float = 0.22f,
    private val windowDurationMs: Long = 350L,
    private val emaAlpha: Float = 0.65f // Smoothing factor
) {

    private data class TimestampedPoint(
        val x: Float,
        val y: Float,
        val timestampMs: Long
    )

    private val pointHistory = ArrayDeque<TimestampedPoint>()
    private var smoothedX: Float? = null
    private var smoothedY: Float? = null

    /**
     * Feeds a new centroid coordinate into tracker.
     * Returns a GestureEvent.MotionSwipe if a swipe motion is detected, or null otherwise.
     */
    fun processCentroid(rawX: Float, rawY: Float, timestampMs: Long): GestureEvent.MotionSwipe? {
        // 1. Apply Exponential Moving Average (EMA) smoothing
        val currentSmoothedX = smoothedX?.let { it * (1f - emaAlpha) + rawX * emaAlpha } ?: rawX
        val currentSmoothedY = smoothedY?.let { it * (1f - emaAlpha) + rawY * emaAlpha } ?: rawY

        smoothedX = currentSmoothedX
        smoothedY = currentSmoothedY

        // 2. Append to history buffer
        pointHistory.addLast(TimestampedPoint(currentSmoothedX, currentSmoothedY, timestampMs))

        // 3. Evict entries outside the sliding window duration
        while (pointHistory.isNotEmpty() && (timestampMs - pointHistory.first().timestampMs > windowDurationMs)) {
            pointHistory.removeFirst()
        }

        if (pointHistory.size < 3) return null

        val oldestPoint = pointHistory.first()
        val deltaX = currentSmoothedX - oldestPoint.x
        val deltaY = currentSmoothedY - oldestPoint.y
        val elapsedSec = (timestampMs - oldestPoint.timestampMs) / 1000f

        if (elapsedSec <= 0.05f) return null

        val absDeltaX = abs(deltaX)
        val absDeltaY = abs(deltaY)
        val totalDistance = hypot(deltaX, deltaY)
        val velocity = totalDistance / elapsedSec

        // 4. Classify dominant motion direction
        if (absDeltaX >= swipeThreshold && absDeltaX > absDeltaY * 1.25f) {
            val direction = if (deltaX > 0f) SwipeDirection.RIGHT else SwipeDirection.LEFT
            clear()
            return GestureEvent.MotionSwipe(
                direction = direction,
                displacement = absDeltaX,
                velocity = velocity
            )
        } else if (absDeltaY >= swipeThreshold && absDeltaY > absDeltaX * 1.25f) {
            val direction = if (deltaY > 0f) SwipeDirection.DOWN else SwipeDirection.UP
            clear()
            return GestureEvent.MotionSwipe(
                direction = direction,
                displacement = absDeltaY,
                velocity = velocity
            )
        }

        return null
    }

    fun clear() {
        pointHistory.clear()
        smoothedX = null
        smoothedY = null
    }
}
