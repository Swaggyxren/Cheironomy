package com.ydnar.cheironomy.gesture.classifier

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.ydnar.cheironomy.data.template.GestureTemplate.StaticGestureTemplate
import com.ydnar.cheironomy.data.template.Point2D
import kotlin.math.hypot

/**
 * Geometric Nearest-Neighbor matcher for user-recorded static hand poses.
 * Normalizes 21-point landmarks relative to wrist and palm scale for position and distance invariance.
 * Selects the best candidate with a reject ceiling and runner-up margin ambiguity check.
 */
object StaticTemplateMatcher {

    /**
     * Normalizes a 21-point hand landmark set:
     * 1. Translates wrist (landmark 0) to origin (0, 0).
     * 2. Scales by distance between wrist (0) and middle finger MCP (9).
     */
    fun normalizeLandmarks(landmarks: List<NormalizedLandmark>): List<Point2D>? {
        if (landmarks.size < 21) return null

        val wrist = landmarks[0]
        val translated = landmarks.map {
            Point2D(it.x() - wrist.x(), it.y() - wrist.y())
        }

        val middleMcp = translated[9]
        val palmScale = hypot(middleMcp.x, middleMcp.y).coerceAtLeast(0.01f)

        return translated.map {
            Point2D(it.x / palmScale, it.y / palmScale)
        }
    }

    /**
     * Normalizes a 21-point Point2D list directly.
     */
    fun normalizePoints(points: List<Point2D>): List<Point2D>? {
        if (points.size < 21) return null

        val wrist = points[0]
        val translated = points.map {
            Point2D(it.x - wrist.x, it.y - wrist.y)
        }

        val middleMcp = translated[9]
        val palmScale = hypot(middleMcp.x, middleMcp.y).coerceAtLeast(0.01f)

        return translated.map {
            Point2D(it.x / palmScale, it.y / palmScale)
        }
    }

    /**
     * Computes the mean Euclidean distance across all 21 normalized landmarks.
     */
    fun computeDistance(
        normalizedA: List<Point2D>,
        normalizedB: List<Point2D>
    ): Float {
        if (normalizedA.size != 21 || normalizedB.size != 21) return Float.MAX_VALUE

        var sumDistance = 0f
        for (i in 0 until 21) {
            val pA = normalizedA[i]
            val pB = normalizedB[i]
            sumDistance += hypot(pA.x - pB.x, pA.y - pB.y)
        }

        return sumDistance / 21f
    }

    /**
     * Nearest-Neighbor matching of live landmarks against registered static templates.
     * 1. Evaluates distance to ALL registered templates.
     * 2. Selects top candidate (d1).
     * 3. Rejects if d1 > rejectCeiling.
     * 4. Rejects if top two candidates are ambiguous ((d2 - d1) / d2 < marginThreshold).
     */
    fun match(
        landmarks: List<NormalizedLandmark>,
        templates: List<StaticGestureTemplate>,
        rejectCeiling: Float = DEFAULT_REJECT_CEILING,
        marginThreshold: Float = DEFAULT_MARGIN_THRESHOLD
    ): Pair<StaticGestureTemplate, Float>? {
        if (templates.isEmpty()) return null
        val normalizedLive = normalizeLandmarks(landmarks) ?: return null

        val scored = templates.map { template ->
            val dist = computeDistance(normalizedLive, template.landmarks)
            Pair(template, dist)
        }.sortedBy { it.second }

        val (winner, d1) = scored[0]

        // 1. Reject ceiling check
        if (d1 > rejectCeiling) {
            return null
        }

        // 2. Margin ambiguity check (if 2 or more templates are registered)
        if (scored.size >= 2) {
            val (_, d2) = scored[1]
            if (d2 > 0f) {
                val relativeMargin = (d2 - d1) / d2
                if (relativeMargin < marginThreshold) {
                    // Ambiguous match between top two templates
                    return null
                }
            }
        }

        return Pair(winner, d1)
    }

    const val DEFAULT_REJECT_CEILING = 0.18f
    const val DEFAULT_MARGIN_THRESHOLD = 0.15f // Winner must be at least 15% closer than runner-up
}
