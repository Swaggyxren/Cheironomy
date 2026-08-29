package com.ydnar.cheironomy.data

import com.ydnar.cheironomy.data.template.GestureTemplate

/**
 * User-configurable settings and nearest-neighbor sensitivity parameters.
 */
data class AppSettings(
    val confidenceThreshold: Float = 0.65f,
    val holdDurationMs: Long = 500L,
    val isOverlayEnabled: Boolean = true,
    val staticRejectCeiling: Float = 0.18f,       // Max Euclidean distance for static poses
    val staticMarginThreshold: Float = 0.15f,     // Required margin over runner-up static match (15%)
    val motionRejectCeiling: Float = 0.22f,       // Max DTW distance for motion gestures
    val motionMarginThreshold: Float = 0.15f,     // Required margin over runner-up motion match (15%)
    val motionPrefilterTolerance: Float = 0.40f,  // Geometric prefilter bounding-box tolerance
    val customTemplates: List<GestureTemplate> = emptyList()
)
