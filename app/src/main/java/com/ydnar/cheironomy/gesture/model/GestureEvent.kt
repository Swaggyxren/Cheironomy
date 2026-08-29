package com.ydnar.cheironomy.gesture.model

import com.ydnar.cheironomy.data.template.GestureTemplate

/**
 * High-level abstract gesture events emitted by GestureEngine.
 */
sealed interface GestureEvent {

    data class StaticPoseHeld(
        val pose: PoseType,
        val durationMs: Long
    ) : GestureEvent

    data class MotionSwipe(
        val direction: SwipeDirection,
        val displacement: Float,
        val velocity: Float
    ) : GestureEvent

    data class CustomGestureTriggered(
        val template: GestureTemplate
    ) : GestureEvent
}

enum class PoseType {
    OPEN_PALM,
    FIST,
    PEACE_SIGN,
    UNKNOWN
}

enum class SwipeDirection {
    LEFT,
    RIGHT,
    UP,
    DOWN
}
