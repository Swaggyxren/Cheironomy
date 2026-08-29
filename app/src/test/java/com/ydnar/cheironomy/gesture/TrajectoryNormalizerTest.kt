package com.ydnar.cheironomy.gesture

import com.ydnar.cheironomy.data.template.Point2D
import com.ydnar.cheironomy.gesture.classifier.TrajectoryNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class TrajectoryNormalizerTest {

    @Test
    fun testNormalizeLinearHorizontalPath() {
        // Raw line from (0.2, 0.5) to (0.8, 0.5)
        val raw = listOf(
            Point2D(0.2f, 0.5f),
            Point2D(0.4f, 0.5f),
            Point2D(0.6f, 0.5f),
            Point2D(0.8f, 0.5f)
        )

        val result = TrajectoryNormalizer.normalizeTrajectory(raw, targetPoints = 20)
        assertNotNull(result)

        val (normalizedPoints, stats) = result!!

        // 1. Must produce exactly 20 points
        assertEquals(20, normalizedPoints.size)

        // 2. First point must be translated to (0, 0)
        assertEquals(0f, normalizedPoints.first().x, 1e-4f)
        assertEquals(0f, normalizedPoints.first().y, 1e-4f)

        // 3. For horizontal line, all y values must be 0
        normalizedPoints.forEach { pt ->
            assertEquals(0f, pt.y, 1e-4f)
        }

        // 4. Last point x must be 1.0 (since total path length is 1.0)
        assertEquals(1.0f, normalizedPoints.last().x, 1e-3f)

        // 5. Points must be equidistant along x
        for (i in 0 until normalizedPoints.size - 1) {
            val step = normalizedPoints[i + 1].x - normalizedPoints[i].x
            assertEquals(1f / 19f, step, 1e-3f)
        }

        // 6. Summary stats check
        assertEquals(0.6f, stats.totalPathLength, 1e-3f)
        assertEquals(1.0f, stats.boundingBoxWidth, 1e-3f)
        assertEquals(0.0f, stats.boundingBoxHeight, 1e-3f)
        assertEquals(1.0f, stats.displacementX, 1e-3f)
        assertEquals(0.0f, stats.displacementY, 1e-3f)
    }

    @Test
    fun testNormalizeCurvedCirclePath() {
        // 36-point circle starting and ending near (0.5, 0.5)
        val raw = mutableListOf<Point2D>()
        for (i in 0..36) {
            val angle = Math.toRadians((i * 10).toDouble())
            val x = (0.5 + 0.2 * Math.cos(angle)).toFloat()
            val y = (0.5 + 0.2 * Math.sin(angle)).toFloat()
            raw.add(Point2D(x, y))
        }

        val result = TrajectoryNormalizer.normalizeTrajectory(raw, targetPoints = 20)
        assertNotNull(result)

        val (normalizedPoints, stats) = result!!
        assertEquals(20, normalizedPoints.size)

        // First point is origin (0, 0)
        assertEquals(0f, normalizedPoints.first().x, 1e-4f)
        assertEquals(0f, normalizedPoints.first().y, 1e-4f)

        // Closed loop should have near-zero start->end displacement
        val dispLen = hypot(stats.displacementX, stats.displacementY)
        assertTrue("Circle displacement should be small, was $dispLen", dispLen < 0.10f)
    }

    @Test
    fun testRejectStationaryPoints() {
        val stationary = listOf(
            Point2D(0.5f, 0.5f),
            Point2D(0.5f, 0.5f),
            Point2D(0.5f, 0.5f)
        )
        assertNull(TrajectoryNormalizer.normalizeTrajectory(stationary))
    }

    @Test
    fun testRejectInsufficientPoints() {
        val single = listOf(Point2D(0.5f, 0.5f))
        assertNull(TrajectoryNormalizer.normalizeTrajectory(single))
    }
}
