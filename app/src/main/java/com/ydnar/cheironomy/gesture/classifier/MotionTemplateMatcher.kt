package com.ydnar.cheironomy.gesture.classifier

import com.ydnar.cheironomy.data.template.GestureTemplate.MotionGestureTemplate
import com.ydnar.cheironomy.data.template.Point2D
import com.ydnar.cheironomy.data.template.TrajectoryStats
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Dual-pass template matcher for custom motion gestures:
 * Pass 1: O(1) geometric summary prefiltering.
 * Pass 2: Dynamic Time Warping (DTW) time-invariant path alignment.
 */
object MotionTemplateMatcher {

    /**
     * Cheap O(1) prefilter pass checking bounding-box aspect ratio and displacement direction.
     */
    fun passesPrefilter(
        candidateStats: TrajectoryStats,
        templateStats: TrajectoryStats,
        tolerance: Float = 0.40f
    ): Boolean {
        // 1. Bounding box width check
        val widthDiff = abs(candidateStats.boundingBoxWidth - templateStats.boundingBoxWidth)
        val maxAllowedWidthDiff = max(templateStats.boundingBoxWidth, 0.15f) * (1f + tolerance)
        if (widthDiff > maxAllowedWidthDiff) return false

        // 2. Bounding box height check
        val heightDiff = abs(candidateStats.boundingBoxHeight - templateStats.boundingBoxHeight)
        val maxAllowedHeightDiff = max(templateStats.boundingBoxHeight, 0.15f) * (1f + tolerance)
        if (heightDiff > maxAllowedHeightDiff) return false

        // 3. Displacement vector direction check
        val lenC = hypot(candidateStats.displacementX, candidateStats.displacementY)
        val lenT = hypot(templateStats.displacementX, templateStats.displacementY)

        val isDirectionalTemplate = lenT > 0.15f
        val isDirectionalCandidate = lenC > 0.15f

        if (isDirectionalTemplate && isDirectionalCandidate) {
            val dotProduct = (candidateStats.displacementX * templateStats.displacementX) +
                    (candidateStats.displacementY * templateStats.displacementY)
            val cosAngle = dotProduct / (lenC * lenT)
            // Reject if displacement vectors point in significantly different directions (> ~75 degrees)
            if (cosAngle < 0.25f) return false
        } else if (isDirectionalTemplate && !isDirectionalCandidate) {
            // Template expects directional movement, but candidate is closed-loop
            if (lenC < 0.08f) return false
        } else if (!isDirectionalTemplate && isDirectionalCandidate) {
            // Template is closed-loop (e.g. circle), but candidate has large open displacement
            if (lenC > 0.35f) return false
        }

        return true
    }

    /**
     * Computes normalized Dynamic Time Warping (DTW) distance between two point sequences.
     */
    fun computeDtwDistance(
        seqA: List<Point2D>,
        seqB: List<Point2D>
    ): Float {
        val n = seqA.size
        val m = seqB.size
        if (n == 0 || m == 0) return Float.MAX_VALUE

        val dtw = Array(n + 1) { FloatArray(m + 1) { Float.POSITIVE_INFINITY } }
        dtw[0][0] = 0f

        for (i in 1..n) {
            val pA = seqA[i - 1]
            for (j in 1..m) {
                val pB = seqB[j - 1]
                val cost = hypot(pA.x - pB.x, pA.y - pB.y)
                val minPrev = min(dtw[i - 1][j], min(dtw[i][j - 1], dtw[i - 1][j - 1]))
                dtw[i][j] = cost + minPrev
            }
        }

        val totalDistance = dtw[n][m]
        return totalDistance / (n + m)
    }

    /**
     * Evaluates a candidate trajectory against all registered motion templates.
     * Returns the best matching template and its DTW score if under [threshold], or null.
     */
    fun match(
        candidateTrajectory: List<Point2D>,
        candidateStats: TrajectoryStats,
        templates: List<MotionGestureTemplate>,
        threshold: Float = 0.22f,
        prefilterTolerance: Float = 0.40f
    ): Pair<MotionGestureTemplate, Float>? {
        if (templates.isEmpty()) return null

        var bestMatch: MotionGestureTemplate? = null
        var bestScore = Float.MAX_VALUE

        for (template in templates) {
            // 1. Cheap O(1) prefilter pass
            if (!passesPrefilter(candidateStats, template.stats, prefilterTolerance)) {
                continue
            }

            // 2. DTW pass
            val dtwScore = computeDtwDistance(candidateTrajectory, template.normalizedPoints)
            if (dtwScore < threshold && dtwScore < bestScore) {
                bestScore = dtwScore
                bestMatch = template
            }
        }

        return bestMatch?.let { Pair(it, bestScore) }
    }
}
