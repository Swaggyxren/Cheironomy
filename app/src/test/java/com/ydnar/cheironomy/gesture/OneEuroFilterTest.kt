package com.ydnar.cheironomy.gesture

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.ydnar.cheironomy.gesture.filter.OneEuroFilter1D
import com.ydnar.cheironomy.gesture.filter.OneEuroFilter2D
import com.ydnar.cheironomy.gesture.filter.OneEuroFilterLandmarks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

class OneEuroFilterTest {

    @Test
    fun `test stationary noisy signal reduces variance by over 80 percent`() {
        val filter = OneEuroFilter1D(minCutoff = 1.0f, beta = 0.007f)
        val rng = Random(42)

        val rawValues = mutableListOf<Float>()
        val filteredValues = mutableListOf<Float>()

        val baseValue = 0.50f
        var timeSec = 0f
        val dt = 1f / 30f // 30 FPS

        for (i in 0 until 100) {
            val noise = (rng.nextFloat() - 0.5f) * 0.08f // +/- 0.04 noise
            val raw = baseValue + noise
            val filtered = filter.filter(raw, timeSec)

            rawValues.add(raw)
            if (i > 10) { // Skip initial convergence frame
                filteredValues.add(filtered)
            }
            timeSec += dt
        }

        val rawVariance = calculateVariance(rawValues)
        val filteredVariance = calculateVariance(filteredValues)

        // Filtered signal should have significantly less variance than raw noisy signal
        assertTrue("Filtered variance ($filteredVariance) should be < 20% of raw ($rawVariance)", filteredVariance < rawVariance * 0.20f)
    }

    @Test
    fun `test high-speed movement adapts cutoff and tracks with low lag`() {
        val filter = OneEuroFilter1D(minCutoff = 1.0f, beta = 0.007f)

        var timeSec = 0f
        val dt = 1f / 30f

        // Fast ramp from 0.0 to 1.0 in 10 frames (velocity = 3.0 / sec)
        for (i in 0 until 10) {
            val raw = i * 0.10f
            val filtered = filter.filter(raw, timeSec)
            timeSec += dt

            if (i >= 5) {
                // Lag should remain small during fast movement
                val lag = abs(raw - filtered)
                assertTrue("Lag ($lag) should be within 0.15 during fast swipe", lag < 0.15f)
            }
        }
    }

    @Test
    fun `test OneEuroFilter2D filters coordinate pairs`() {
        val filter2D = OneEuroFilter2D()
        val (fx, fy) = filter2D.filter(0.5f, 0.5f, 0.0f)
        assertEquals(0.5f, fx, 0.001f)
        assertEquals(0.5f, fy, 0.001f)
    }

    @Test
    fun `test OneEuroFilterLandmarks filters all 21 hand landmarks`() {
        val landmarkFilter = OneEuroFilterLandmarks()
        val rawLandmarks = (0 until 21).map {
            NormalizedLandmark.create(0.5f, 0.5f, 0.0f)
        }

        val filtered = landmarkFilter.filter(rawLandmarks, 1.0f)
        assertEquals(21, filtered.size)
        assertEquals(0.5f, filtered[0].x(), 0.001f)
        assertEquals(0.5f, filtered[20].y(), 0.001f)
    }

    @Test
    fun `test filter reset restores initial state`() {
        val filter = OneEuroFilter1D()
        filter.filter(0.2f, 1.0f)
        filter.filter(0.8f, 1.1f)
        filter.reset()

        val afterReset = filter.filter(0.5f, 2.0f)
        assertEquals(0.5f, afterReset, 0.001f)
    }

    private fun calculateVariance(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average().toFloat()
        return values.map { (it - mean) * (it - mean) }.average().toFloat()
    }
}
