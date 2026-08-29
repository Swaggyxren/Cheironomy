package com.ydnar.cheironomy.data

import com.ydnar.cheironomy.data.template.GestureTemplate

/**
 * User-configurable settings and sensitivity parameters.
 */
data class AppSettings(
    val confidenceThreshold: Float = 0.5f,
    val cooldownMs: Long = 1200L,
    val holdDurationMs: Long = 500L,
    val swipeSensitivity: Float = 0.22f,
    val isOverlayEnabled: Boolean = true,
    val motionMatchThreshold: Float = 0.22f, // DTW distance threshold
    val staticMatchThreshold: Float = 0.16f, // Euclidean landmark threshold
    val openPalmAction: GestureAction = GestureAction.MEDIA_PLAY_PAUSE,
    val swipeLeftAction: GestureAction = GestureAction.SWIPE_LEFT,
    val swipeRightAction: GestureAction = GestureAction.SWIPE_RIGHT,
    val swipeUpAction: GestureAction = GestureAction.SCROLL_DOWN,
    val swipeDownAction: GestureAction = GestureAction.SCROLL_UP,
    val customTemplates: List<GestureTemplate> = emptyList()
)
