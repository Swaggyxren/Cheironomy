package com.ydnar.cheironomy.gesture.classifier

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

/**
 * Utility helper to compute the palm centroid from hand landmarks.
 */
object PalmCentroidHelper {

    /**
     * Calculates the palm centroid (geometric center of palm base cluster).
     * Cluster: Wrist (0), Index MCP (5), Middle MCP (9), Ring MCP (13), Pinky MCP (17).
     */
    fun calculatePalmCentroid(landmarks: List<NormalizedLandmark>): Pair<Float, Float> {
        if (landmarks.size < 21) return Pair(0f, 0f)

        val palmIndices = intArrayOf(0, 5, 9, 13, 17)
        var sumX = 0f
        var sumY = 0f

        for (idx in palmIndices) {
            sumX += landmarks[idx].x()
            sumY += landmarks[idx].y()
        }

        return Pair(sumX / palmIndices.size, sumY / palmIndices.size)
    }
}
