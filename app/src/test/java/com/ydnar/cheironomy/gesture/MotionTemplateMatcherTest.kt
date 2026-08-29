package com.ydnar.cheironomy.gesture

import com.ydnar.cheironomy.data.GestureAction
import com.ydnar.cheironomy.data.template.GestureTemplate.MotionGestureTemplate
import com.ydnar.cheironomy.data.template.Point2D
import com.ydnar.cheironomy.data.template.TrajectoryStats
import com.ydnar.cheironomy.gesture.classifier.MotionTemplateMatcher
import com.ydnar.cheironomy.gesture.classifier.TrajectoryNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTemplateMatcherTest {

    @Test
    fun testPrefilterPassAndReject() {
        // Stats for a horizontal right swipe
        val horizontalSwipeStats = TrajectoryStats(
            totalPathLength = 0.5f,
            boundingBoxWidth = 1.0f,
            boundingBoxHeight = 0.05f,
            displacementX = 1.0f,
            displacementY = 0.0f
        )

        // Candidate 1: Another horizontal right swipe (similar stats)
        val similarCandidate = TrajectoryStats(
            totalPathLength = 0.45f,
            boundingBoxWidth = 0.95f,
            boundingBoxHeight = 0.08f,
            displacementX = 0.95f,
            displacementY = 0.05f
        )
        assertTrue(MotionTemplateMatcher.passesPrefilter(similarCandidate, horizontalSwipeStats))

        // Candidate 2: Vertical downward swipe (different bounding box & opposite angle)
        val verticalCandidate = TrajectoryStats(
            totalPathLength = 0.5f,
            boundingBoxWidth = 0.05f,
            boundingBoxHeight = 1.0f,
            displacementX = 0.0f,
            displacementY = 1.0f
        )
        assertFalse(MotionTemplateMatcher.passesPrefilter(verticalCandidate, horizontalSwipeStats))

        // Candidate 3: Leftward swipe (opposite displacement direction)
        val oppositeCandidate = TrajectoryStats(
            totalPathLength = 0.5f,
            boundingBoxWidth = 1.0f,
            boundingBoxHeight = 0.05f,
            displacementX = -1.0f,
            displacementY = 0.0f
        )
        assertFalse(MotionTemplateMatcher.passesPrefilter(oppositeCandidate, horizontalSwipeStats))
    }

    @Test
    fun testDtwDistanceIdenticalPaths() {
        val (points, _) = TrajectoryNormalizer.normalizeTrajectory(listOf(
            Point2D(0f, 0f),
            Point2D(0.5f, 0.2f),
            Point2D(1.0f, 0.8f)
        ))!!

        val dtwDist = MotionTemplateMatcher.computeDtwDistance(points, points)
        assertEquals(0f, dtwDist, 1e-5f)
    }

    @Test
    fun testDtwSpeedInvariance() {
        // Path A: Recorded at 1x speed (fewer raw sample points)
        val rawSlow = listOf(
            Point2D(0.1f, 0.1f),
            Point2D(0.3f, 0.2f),
            Point2D(0.5f, 0.6f),
            Point2D(0.7f, 0.8f)
        )

        // Path B: Same geometry performed at different speed / frame rate (many sample points)
        val rawFast = listOf(
            Point2D(0.1f, 0.1f),
            Point2D(0.2f, 0.15f),
            Point2D(0.3f, 0.2f),
            Point2D(0.4f, 0.4f),
            Point2D(0.5f, 0.6f),
            Point2D(0.6f, 0.7f),
            Point2D(0.7f, 0.8f)
        )

        val normA = TrajectoryNormalizer.normalizeTrajectory(rawSlow)!!
        val normB = TrajectoryNormalizer.normalizeTrajectory(rawFast)!!

        val dtwDist = MotionTemplateMatcher.computeDtwDistance(normA.first, normB.first)
        assertTrue("DTW should be small for same shape at different speed, was $dtwDist", dtwDist < 0.08f)
    }

    @Test
    fun testMatchRecognizesRegisteredMotionTemplate() {
        // Register an "S-Curve" Motion Template
        val sCurveRaw = listOf(
            Point2D(0.1f, 0.1f),
            Point2D(0.3f, 0.4f),
            Point2D(0.1f, 0.7f),
            Point2D(0.4f, 0.9f)
        )
        val (normPoints, stats) = TrajectoryNormalizer.normalizeTrajectory(sCurveRaw)!!
        val sCurveTemplate = MotionGestureTemplate(
            id = "scurve_1",
            name = "S Curve Flick",
            action = GestureAction.MEDIA_NEXT,
            normalizedPoints = normPoints,
            stats = stats
        )

        val templates = listOf(sCurveTemplate)

        // 1. Live performance of S-Curve
        val livePerformance = listOf(
            Point2D(0.2f, 0.2f),
            Point2D(0.4f, 0.5f),
            Point2D(0.2f, 0.8f),
            Point2D(0.5f, 1.0f)
        )
        val (liveNorm, liveStats) = TrajectoryNormalizer.normalizeTrajectory(livePerformance)!!

        val match = MotionTemplateMatcher.match(liveNorm, liveStats, templates, threshold = 0.22f)
        assertNotNull(match)
        assertEquals("scurve_1", match!!.first.id)
        assertEquals(GestureAction.MEDIA_NEXT, match.first.action)

        // 2. Unrelated straight horizontal line should not match
        val straightLine = listOf(
            Point2D(0.1f, 0.5f),
            Point2D(0.9f, 0.5f)
        )
        val (straightNorm, straightStats) = TrajectoryNormalizer.normalizeTrajectory(straightLine)!!

        val mismatch = MotionTemplateMatcher.match(straightNorm, straightStats, templates, threshold = 0.22f)
        assertNull(mismatch)
    }
}
