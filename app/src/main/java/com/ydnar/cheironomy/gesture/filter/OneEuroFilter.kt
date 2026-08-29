package com.ydnar.cheironomy.gesture.filter

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.PI
import kotlin.math.abs

/**
 * 1€ Filter (One Euro Filter) implementation for real-time low-latency, adaptive jitter removal.
 * Reference: Casiez, G., Roussel, N. and Vogel, D. (2012). 1€ Filter: A Simple Speed-based Low-pass Filter for Noisy Input in Interactive Systems.
 */
class OneEuroFilter1D(
    var minCutoff: Float = DEFAULT_MIN_CUTOFF,
    var beta: Float = DEFAULT_BETA,
    var dCutoff: Float = DEFAULT_D_CUTOFF
) {
    private var xHatPrev: Float? = null
    private var dxHatPrev: Float = 0f
    private var tPrev: Float? = null // in seconds

    fun filter(x: Float, timestampSeconds: Float): Float {
        val prevX = xHatPrev
        val prevT = tPrev

        if (prevX == null || prevT == null) {
            xHatPrev = x
            dxHatPrev = 0f
            tPrev = timestampSeconds
            return x
        }

        val dt = timestampSeconds - prevT
        if (dt <= 0.0001f) {
            return prevX
        }

        tPrev = timestampSeconds

        // 1. Calculate raw derivative and smooth it with derivative filter
        val dx = (x - prevX) / dt
        val alphaD = calculateAlpha(dCutoff, dt)
        val dxHat = alphaD * dx + (1f - alphaD) * dxHatPrev
        dxHatPrev = dxHat

        // 2. Calculate adaptive cutoff frequency based on velocity
        val cutoff = minCutoff + beta * abs(dxHat)

        // 3. Filter current value with adaptive alpha
        val alpha = calculateAlpha(cutoff, dt)
        val xHat = alpha * x + (1f - alpha) * prevX
        xHatPrev = xHat

        return xHat
    }

    fun reset() {
        xHatPrev = null
        dxHatPrev = 0f
        tPrev = null
    }

    private fun calculateAlpha(cutoff: Float, dt: Float): Float {
        val tau = 1.0 / (2.0 * PI * cutoff)
        return (1.0 / (1.0 + tau / dt)).toFloat()
    }

    companion object {
        const val DEFAULT_MIN_CUTOFF = 1.0f  // Hz - Lower value = heavy smoothing at slow speeds (kills jitter)
        const val DEFAULT_BETA = 0.007f      // Speed coefficient - Higher value = light smoothing at fast speeds (low lag)
        const val DEFAULT_D_CUTOFF = 1.0f    // Hz - Cutoff frequency for derivative smoothing
    }
}

/**
 * 2D One Euro Filter for coordinate pairs (e.g. palm centroid).
 */
class OneEuroFilter2D(
    minCutoff: Float = OneEuroFilter1D.DEFAULT_MIN_CUTOFF,
    beta: Float = OneEuroFilter1D.DEFAULT_BETA,
    dCutoff: Float = OneEuroFilter1D.DEFAULT_D_CUTOFF
) {
    private val filterX = OneEuroFilter1D(minCutoff, beta, dCutoff)
    private val filterY = OneEuroFilter1D(minCutoff, beta, dCutoff)

    fun filter(x: Float, y: Float, timestampSeconds: Float): Pair<Float, Float> {
        val fx = filterX.filter(x, timestampSeconds)
        val fy = filterY.filter(y, timestampSeconds)
        return Pair(fx, fy)
    }

    fun reset() {
        filterX.reset()
        filterY.reset()
    }
}

/**
 * One Euro Filter for the full 21 MediaPipe hand landmarks.
 */
class OneEuroFilterLandmarks(
    minCutoff: Float = OneEuroFilter1D.DEFAULT_MIN_CUTOFF,
    beta: Float = OneEuroFilter1D.DEFAULT_BETA,
    dCutoff: Float = OneEuroFilter1D.DEFAULT_D_CUTOFF
) {
    private val xFilters = Array(21) { OneEuroFilter1D(minCutoff, beta, dCutoff) }
    private val yFilters = Array(21) { OneEuroFilter1D(minCutoff, beta, dCutoff) }
    private val zFilters = Array(21) { OneEuroFilter1D(minCutoff, beta, dCutoff) }

    fun filter(landmarks: List<NormalizedLandmark>, timestampSeconds: Float): List<NormalizedLandmark> {
        if (landmarks.size != 21) return landmarks
        return landmarks.mapIndexed { index, lm ->
            val fx = xFilters[index].filter(lm.x(), timestampSeconds)
            val fy = yFilters[index].filter(lm.y(), timestampSeconds)
            val fz = zFilters[index].filter(lm.z(), timestampSeconds)
            NormalizedLandmark.create(fx, fy, fz)
        }
    }

    fun reset() {
        xFilters.forEach { it.reset() }
        yFilters.forEach { it.reset() }
        zFilters.forEach { it.reset() }
    }
}
