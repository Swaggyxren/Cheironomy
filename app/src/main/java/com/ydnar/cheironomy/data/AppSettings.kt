package com.ydnar.cheironomy.data

/**
 * User-configurable settings and sensitivity parameters.
 */
data class AppSettings(
    val confidenceThreshold: Float = 0.5f,
    val cooldownMs: Long = 1200L,
    val holdDurationMs: Long = 500L,
    val swipeSensitivity: Float = 0.22f,
    val isOverlayEnabled: Boolean = true,
    val openPalmAction: GestureAction = GestureAction.MEDIA_PLAY_PAUSE,
    val swipeLeftAction: GestureAction = GestureAction.SWIPE_LEFT,
    val swipeRightAction: GestureAction = GestureAction.SWIPE_RIGHT,
    val swipeUpAction: GestureAction = GestureAction.SCROLL_DOWN,
    val swipeDownAction: GestureAction = GestureAction.SCROLL_UP
)
