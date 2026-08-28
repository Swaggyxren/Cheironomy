package com.ydnar.cheironomy.gesture

import com.ydnar.cheironomy.gesture.classifier.MotionDeltaTracker
import com.ydnar.cheironomy.gesture.model.SwipeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class MotionDeltaTrackerTest {

    private lateinit var tracker: MotionDeltaTracker

    @Before
    fun setUp() {
        tracker = MotionDeltaTracker(
            swipeThreshold = 0.20f,
            windowDurationMs = 400L,
            emaAlpha = 1.0f // Pure linear coordinates for unit testing
        )
    }

    @Test
    fun `test stationary hand does not trigger swipe`() {
        assertNull(tracker.processCentroid(0.5f, 0.5f, 1000L))
        assertNull(tracker.processCentroid(0.505f, 0.502f, 1100L))
        assertNull(tracker.processCentroid(0.498f, 0.501f, 1200L))
        assertNull(tracker.processCentroid(0.502f, 0.499f, 1300L))
    }

    @Test
    fun `test horizontal swipe right detection`() {
        assertNull(tracker.processCentroid(0.20f, 0.50f, 1000L))
        assertNull(tracker.processCentroid(0.30f, 0.50f, 1100L))
        val event = tracker.processCentroid(0.55f, 0.50f, 1200L)

        assertNotNull(event)
        assertEquals(SwipeDirection.RIGHT, event?.direction)
    }

    @Test
    fun `test horizontal swipe left detection`() {
        assertNull(tracker.processCentroid(0.80f, 0.50f, 1000L))
        assertNull(tracker.processCentroid(0.65f, 0.50f, 1100L))
        val event = tracker.processCentroid(0.45f, 0.50f, 1200L)

        assertNotNull(event)
        assertEquals(SwipeDirection.LEFT, event?.direction)
    }

    @Test
    fun `test vertical scroll down detection`() {
        assertNull(tracker.processCentroid(0.50f, 0.20f, 1000L))
        assertNull(tracker.processCentroid(0.50f, 0.35f, 1100L))
        val event = tracker.processCentroid(0.50f, 0.60f, 1200L)

        assertNotNull(event)
        assertEquals(SwipeDirection.DOWN, event?.direction)
    }

    @Test
    fun `test vertical scroll up detection`() {
        assertNull(tracker.processCentroid(0.50f, 0.80f, 1000L))
        assertNull(tracker.processCentroid(0.50f, 0.65f, 1100L))
        val event = tracker.processCentroid(0.50f, 0.40f, 1200L)

        assertNotNull(event)
        assertEquals(SwipeDirection.UP, event?.direction)
    }
}
