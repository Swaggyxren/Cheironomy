package com.ydnar.cheironomy.gesture.classifier

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.ydnar.cheironomy.data.template.GestureTemplate.StaticGestureTemplate
import com.ydnar.cheironomy.data.template.Point2D
import kotlin.math.hypot

/**
 * Geometric matcher for user-recorded static hand poses.
 * Normalizes 21-point landmarks relative to wrist and palm scale for position and distance invariance.
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

        // Distance from wrist (0) to middle finger MCP (9)
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
     * Matches live landmarks against registered static templates.
     */
    fun match(
        landmarks: List<NormalizedLandmark>,
        templates: List<StaticGestureTemplate>,
        threshold: Float = 0.16f
    ): Pair<StaticGestureTemplate, Float>? {
        if (templates.isEmpty()) return null
        val normalizedLive = normalizeLandmarks(landmarks) ?: return null

        var bestMatch: StaticGestureTemplate? = null
        var bestDistance = Float.MAX_VALUE

        for (template in templates) {
            val dist = computeDistance(normalizedLive, template.landmarks)
            if (dist < threshold && dist < bestDistance) {
                bestDistance = dist
                bestMatch = template
            }
        }

        return bestMatch?.let { Pair(it, bestDistance) }
    }
}
