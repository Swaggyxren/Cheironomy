package com.ydnar.cheironomy.data

/**
 * System and media actions triggered by recognized hand gestures.
 */
enum class GestureAction(val displayName: String) {
    NONE("None (Disabled)"),
    MEDIA_PLAY_PAUSE("Media: Play / Pause"),
    MEDIA_NEXT("Media: Next Track"),
    MEDIA_PREVIOUS("Media: Previous Track"),
    SWIPE_LEFT("Touch: Swipe Left"),
    SWIPE_RIGHT("Touch: Swipe Right"),
    SCROLL_UP("Touch: Scroll Up"),
    SCROLL_DOWN("Touch: Scroll Down")
}
