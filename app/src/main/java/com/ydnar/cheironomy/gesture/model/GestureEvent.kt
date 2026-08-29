package com.ydnar.cheironomy.gesture.model

import com.ydnar.cheironomy.data.template.GestureTemplate

/**
 * High-level abstract gesture events emitted by GestureEngine.
 * Every recognized gesture is a user-recorded custom template.
 */
sealed interface GestureEvent {
    data class CustomGestureTriggered(
        val template: GestureTemplate
    ) : GestureEvent
}
