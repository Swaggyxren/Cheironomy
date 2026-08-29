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
    fun `test nearest match wins among multiple registered motion templates`() {
        // Template 1: Rightward Swipe
        val rightSwipePoints = listOf(
            Point2D(0.1f, 0.5f),
            Point2D(0.5f, 0.5f),
            Point2D(0.9f, 0.5f)
        )
        val (normRight, statsRight) = TrajectoryNormalizer.normalizeTrajectory(rightSwipePoints)!!
        val templateRight = MotionGestureTemplate(
            id = "motion_right",
            name = "Swipe Right",
            action = GestureAction.MEDIA_NEXT,
            normalizedPoints = normRight,
            stats = statsRight
        )

        // Template 2: Downward Swipe
        val downSwipePoints = listOf(
            Point2D(0.5f, 0.1f),
            Point2D(0.5f, 0.5f),
            Point2D(0.5f, 0.9f)
        )
        val (normDown, statsDown) = TrajectoryNormalizer.normalizeTrajectory(downSwipePoints)!!
        val templateDown = MotionGestureTemplate(
            id = "motion_down",
            name = "Swipe Down",
            action = GestureAction.SCROLL_DOWN,
            normalizedPoints = normDown,
            stats = statsDown
        )

        val templates = listOf(templateRight, templateDown)

        // Live candidate matching rightward swipe
        val liveCandidate = listOf(
            Point2D(0.15f, 0.48f),
            Point2D(0.52f, 0.51f),
            Point2D(0.88f, 0.49f)
        )
        val (liveNorm, liveStats) = TrajectoryNormalizer.normalizeTrajectory(liveCandidate)!!

        val match = MotionTemplateMatcher.match(
            candidateTrajectory = liveNorm,
            candidateStats = liveStats,
            templates = templates,
            rejectCeiling = 0.22f,
            marginThreshold = 0.15f
        )

        assertNotNull(match)
        assertEquals("motion_right", match?.first?.id)
        assertEquals(GestureAction.MEDIA_NEXT, match?.first?.action)
    }

    @Test
    fun `test reject ceiling rejects erratic candidate trajectories`() {
        val sCurveRaw = listOf(
            Point2D(0.1f, 0.1f),
            Point2D(0.3f, 0.4f),
            Point2D(0.1f, 0.7f),
            Point2D(0.4f, 0.9f)
        )
        val (normPoints, stats) = TrajectoryNormalizer.normalizeTrajectory(sCurveRaw)!!
        val sCurveTemplate = MotionGestureTemplate(
            id = "scurve_1",
            name = "S Curve",
            action = GestureAction.MEDIA_NEXT,
            normalizedPoints = normPoints,
            stats = stats
        )

        // Dissimilar straight horizontal line
        val straightLine = listOf(
            Point2D(0.1f, 0.5f),
            Point2D(0.9f, 0.5f)
        )
        val (straightNorm, straightStats) = TrajectoryNormalizer.normalizeTrajectory(straightLine)!!

        val match = MotionTemplateMatcher.match(
            candidateTrajectory = straightNorm,
            candidateStats = straightStats,
            templates = listOf(sCurveTemplate),
            rejectCeiling = 0.22f
        )
        assertNull("Dissimilar motion exceeding reject ceiling must be rejected", match)
    }

    @Test
    fun `test margin check rejects ambiguous candidate between two similar motion templates`() {
        val baseMotion = listOf(
            Point2D(0.1f, 0.1f),
            Point2D(0.3f, 0.3f),
            Point2D(0.6f, 0.7f),
            Point2D(0.9f, 0.9f)
        )
        val (normA, statsA) = TrajectoryNormalizer.normalizeTrajectory(baseMotion)!!
        val templateA = MotionGestureTemplate(
            id = "template_a",
            name = "Diagonal A",
            action = GestureAction.SWIPE_RIGHT,
            normalizedPoints = normA,
            stats = statsA
        )

        // Template B is almost identical to Template A
        val slightlyShifted = baseMotion.map { Point2D(it.x + 0.01f, it.y) }
        val (normB, statsB) = TrajectoryNormalizer.normalizeTrajectory(slightlyShifted)!!
        val templateB = MotionGestureTemplate(
            id = "template_b",
            name = "Diagonal B",
            action = GestureAction.SWIPE_LEFT,
            normalizedPoints = normB,
            stats = statsB
        )

        // Live candidate between A and B
        val candidate = baseMotion.map { Point2D(it.x + 0.005f, it.y) }
        val (candNorm, candStats) = TrajectoryNormalizer.normalizeTrajectory(candidate)!!

        val match = MotionTemplateMatcher.match(
            candidateTrajectory = candNorm,
            candidateStats = candStats,
            templates = listOf(templateA, templateB),
            rejectCeiling = 0.22f,
            marginThreshold = 0.20f // 20% margin required
        )

        assertNull("Ambiguous candidate with margin < 20% must be rejected", match)
    }
}
