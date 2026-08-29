package com.ydnar.cheironomy.gesture.classifier

import com.ydnar.cheironomy.data.template.Point2D
import com.ydnar.cheironomy.data.template.TrajectoryStats
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Normalizes 2D gesture trajectories for size, speed, and origin invariance.
 */
object TrajectoryNormalizer {

    const val DEFAULT_RESAMPLE_POINTS = 20

    /**
     * Normalizes a raw trajectory:
     * 1. Translates start to origin (0, 0).
     * 2. Scales total path length to 1.0.
     * 3. Resamples path into [targetPoints] equidistant points via arc-length interpolation.
     * 4. Computes prefilter summary statistics.
     */
    fun normalizeTrajectory(
        rawPoints: List<Point2D>,
        targetPoints: Int = DEFAULT_RESAMPLE_POINTS
    ): Pair<List<Point2D>, TrajectoryStats>? {
        if (rawPoints.size < 2 || targetPoints < 2) return null

        // 1. Origin Translation: shift so rawPoints[0] is (0, 0)
        val p0 = rawPoints.first()
        val translated = rawPoints.map { Point2D(it.x - p0.x, it.y - p0.y) }

        // 2. Compute Cumulative Arc-Lengths and Bounding Box
        var totalLength = 0f
        val segmentLengths = mutableListOf<Float>()
        var minX = translated[0].x
        var maxX = translated[0].x
        var minY = translated[0].y
        var maxY = translated[0].y

        for (i in 0 until translated.size - 1) {
            val pA = translated[i]
            val pB = translated[i + 1]
            val segLen = hypot(pB.x - pA.x, pB.y - pA.y)
            segmentLengths.add(segLen)
            totalLength += segLen

            minX = min(minX, pB.x)
            maxX = max(maxX, pB.x)
            minY = min(minY, pB.y)
            maxY = max(maxY, pB.y)
        }

        // If path has virtually no movement, cannot normalize
        if (totalLength < 1e-4f) return null

        val scale = 1.0f / totalLength

        // 3. Scale Points
        val scaledPoints = translated.map { Point2D(it.x * scale, it.y * scale) }
        val scaledSegmentLengths = segmentLengths.map { it * scale }
        val scaledBoxWidth = (maxX - minX) * scale
        val scaledBoxHeight = (maxY - minY) * scale

        // 4. Arc-Length Resampling to [targetPoints] equidistant points
        val resampled = mutableListOf<Point2D>()
        resampled.add(scaledPoints.first())

        val stepLength = 1.0f / (targetPoints - 1)
        var currentTargetDist = stepLength
        var accumulatedDist = 0f
        var segIndex = 0

        while (resampled.size < targetPoints - 1 && segIndex < scaledSegmentLengths.size) {
            val segLen = scaledSegmentLengths[segIndex]
            val segStart = scaledPoints[segIndex]
            val segEnd = scaledPoints[segIndex + 1]

            if (segLen > 0f && accumulatedDist + segLen >= currentTargetDist) {
                // The target distance falls within this segment
                val remain = currentTargetDist - accumulatedDist
                val t = (remain / segLen).coerceIn(0f, 1f)
                val interpX = segStart.x + t * (segEnd.x - segStart.x)
                val interpY = segStart.y + t * (segEnd.y - segStart.y)

                resampled.add(Point2D(interpX, interpY))
                currentTargetDist += stepLength
            } else {
                accumulatedDist += segLen
                segIndex++
            }
        }

        // Fill remaining with last point if needed to guarantee exact count
        while (resampled.size < targetPoints) {
            resampled.add(scaledPoints.last())
        }

        val lastPoint = resampled.last()
        val firstPoint = resampled.first()

        val stats = TrajectoryStats(
            totalPathLength = totalLength,
            boundingBoxWidth = scaledBoxWidth,
            boundingBoxHeight = scaledBoxHeight,
            displacementX = lastPoint.x - firstPoint.x,
            displacementY = lastPoint.y - firstPoint.y
        )

        return Pair(resampled, stats)
    }
}
