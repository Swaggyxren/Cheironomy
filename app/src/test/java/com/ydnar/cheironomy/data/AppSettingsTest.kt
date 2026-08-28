package com.ydnar.cheironomy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `test default app settings`() {
        val settings = AppSettings()
        assertEquals(0.5f, settings.confidenceThreshold, 0.001f)
        assertEquals(1200L, settings.cooldownMs)
        assertEquals(500L, settings.holdDurationMs)
        assertTrue(settings.isOverlayEnabled)
        assertEquals(GestureAction.MEDIA_PLAY_PAUSE, settings.openPalmAction)
        assertEquals(GestureAction.SWIPE_LEFT, settings.swipeLeftAction)
        assertEquals(GestureAction.SWIPE_RIGHT, settings.swipeRightAction)
        assertEquals(GestureAction.SCROLL_DOWN, settings.swipeUpAction)
        assertEquals(GestureAction.SCROLL_UP, settings.swipeDownAction)
    }

    @Test
    fun `test custom action assignment`() {
        val settings = AppSettings(
            openPalmAction = GestureAction.NONE,
            swipeLeftAction = GestureAction.MEDIA_PREVIOUS,
            swipeRightAction = GestureAction.MEDIA_NEXT
        )
        assertEquals(GestureAction.NONE, settings.openPalmAction)
        assertEquals(GestureAction.MEDIA_PREVIOUS, settings.swipeLeftAction)
        assertEquals(GestureAction.MEDIA_NEXT, settings.swipeRightAction)
    }
}
