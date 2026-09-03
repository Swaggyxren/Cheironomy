package com.ydnar.cheironomy.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `test default app settings`() {
        val settings = AppSettings()
        assertEquals(0.65f, settings.confidenceThreshold, 0.001f)
        assertEquals(700L, settings.holdDurationMs)
        assertTrue(settings.isOverlayEnabled)
        assertEquals(0.11f, settings.staticRejectCeiling, 0.001f)
        assertEquals(0.15f, settings.staticMarginThreshold, 0.001f)
        assertEquals(0.22f, settings.motionRejectCeiling, 0.001f)
        assertEquals(0.15f, settings.motionMarginThreshold, 0.001f)
        assertEquals(0.40f, settings.motionPrefilterTolerance, 0.001f)
    }

    @Test
    fun `test custom nearest-neighbor threshold assignment`() {
        val settings = AppSettings(
            confidenceThreshold = 0.80f,
            staticRejectCeiling = 0.12f,
            motionRejectCeiling = 0.16f
        )
        assertEquals(0.80f, settings.confidenceThreshold, 0.001f)
        assertEquals(0.12f, settings.staticRejectCeiling, 0.001f)
        assertEquals(0.16f, settings.motionRejectCeiling, 0.001f)
    }
}
