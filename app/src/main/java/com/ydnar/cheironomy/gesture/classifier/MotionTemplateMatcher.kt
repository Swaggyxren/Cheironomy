package com.ydnar.cheironomy.gesture.classifier

import com.ydnar.cheironomy.data.template.GestureTemplate.MotionGestureTemplate
import com.ydnar.cheironomy.data.template.Point2D
import com.ydnar.cheironomy.data.template.TrajectoryStats
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Dual-pass Nearest-Neighbor template matcher for custom motion gestures:
 * Pass 1: O(1) geometric summary prefiltering.
 * Pass 2: Dynamic Time Warping (DTW) time-invariant path alignment.
 * Selects the best candidate with reject ceiling and runner-up margin ambiguity check.
 */
object MotionTemplateMatcher {

    /**
     * Cheap O(1) prefilter pass checking bounding-box aspect ratio and displacement direction.
     */
    fun passesPrefilter(
        candidateStats: TrajectoryStats,
        templateStats: TrajectoryStats,
        tolerance: Float = DEFAULT_PREFILTER_TOLERANCE
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
     * Nearest-Neighbor evaluation of a candidate trajectory against all registered motion templates.
     * 1. Filters templates that pass the cheap O(1) prefilter.
     * 2. Computes DTW distance for all candidates.
     * 3. Selects top candidate (d1).
     * 4. Rejects if d1 > rejectCeiling.
     * 5. Rejects if top two candidates are ambiguous ((d2 - d1) / d2 < marginThreshold).
     */
    fun match(
        candidateTrajectory: List<Point2D>,
        candidateStats: TrajectoryStats,
        templates: List<MotionGestureTemplate>,
        rejectCeiling: Float = DEFAULT_REJECT_CEILING,
        marginThreshold: Float = DEFAULT_MARGIN_THRESHOLD,
        prefilterTolerance: Float = DEFAULT_PREFILTER_TOLERANCE
    ): Pair<MotionGestureTemplate, Float>? {
        if (templates.isEmpty()) return null

        val candidateMatches = templates.filter { template ->
            passesPrefilter(candidateStats, template.stats, prefilterTolerance)
        }

        if (candidateMatches.isEmpty()) return null

        val scored = candidateMatches.map { template ->
            val dist = computeDtwDistance(candidateTrajectory, template.normalizedPoints)
            Pair(template, dist)
        }.sortedBy { it.second }

        val (winner, d1) = scored[0]

        // 1. Reject ceiling check
        if (d1 > rejectCeiling) {
            return null
        }

        // 2. Margin ambiguity check (if 2 or more candidate templates match prefiltering)
        if (scored.size >= 2) {
            val (_, d2) = scored[1]
            if (d2 > 0f) {
                val relativeMargin = (d2 - d1) / d2
                if (relativeMargin < marginThreshold) {
                    // Ambiguous match between top two motion templates
                    return null
                }
            }
        }

        return Pair(winner, d1)
    }

    const val DEFAULT_REJECT_CEILING = 0.22f
    const val DEFAULT_MARGIN_THRESHOLD = 0.15f // Winner must be at least 15% closer than runner-up
    const val DEFAULT_PREFILTER_TOLERANCE = 0.40f
}
