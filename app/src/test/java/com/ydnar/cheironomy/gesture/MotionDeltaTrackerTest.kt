package com.ydnar.cheironomy.gesture

import com.ydnar.cheironomy.gesture.classifier.MotionDeltaTracker
import com.ydnar.cheironomy.gesture.classifier.MotionTrackerState
import com.ydnar.cheironomy.gesture.model.SwipeDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MotionDeltaTrackerTest {

    private lateinit var tracker: MotionDeltaTracker

    @Before
    fun setUp() {
        tracker = MotionDeltaTracker(
            triggerThreshold = 0.20f,
            windowDurationMs = 400L
        )
    }

    @Test
    fun `test stationary hand remains in IDLE and does not trigger swipe`() {
        assertEquals(MotionTrackerState.IDLE, tracker.state)
        assertNull(tracker.processCentroid(0.5f, 0.5f, 1000L))
        assertNull(tracker.processCentroid(0.502f, 0.501f, 1050L))
        assertNull(tracker.processCentroid(0.499f, 0.501f, 1100L))
        assertNull(tracker.processCentroid(0.501f, 0.499f, 1150L))
        assertEquals(MotionTrackerState.IDLE, tracker.state)
    }

    @Test
    fun `test small motion transitions to TRACKING then returns to IDLE on decay`() {
        assertNull(tracker.processCentroid(0.50f, 0.50f, 1000L))
        assertNull(tracker.processCentroid(0.52f, 0.50f, 1050L))
        assertNull(tracker.processCentroid(0.55f, 0.50f, 1100L)) // delta = 0.05 (>= startThreshold 0.044)

        assertEquals(MotionTrackerState.TRACKING, tracker.state)

        // Hand stops moving and settles
        tracker.processCentroid(0.55f, 0.50f, 1300L)
        tracker.processCentroid(0.55f, 0.50f, 1550L) // delta = 0 (< decayThreshold)

        assertEquals(MotionTrackerState.IDLE, tracker.state)
    }

    @Test
    fun `test horizontal swipe right detection`() {
        assertNull(tracker.processCentroid(0.20f, 0.50f, 1000L))
        assertNull(tracker.processCentroid(0.30f, 0.50f, 1100L))
        val event = tracker.processCentroid(0.55f, 0.50f, 1200L) // delta = 0.35 >= 0.20

        assertNotNull(event)
        assertEquals(SwipeDirection.RIGHT, event?.direction)
        assertEquals(MotionTrackerState.RECOGNIZED, tracker.state)
    }

    @Test
    fun `test horizontal swipe left detection`() {
        assertNull(tracker.processCentroid(0.80f, 0.50f, 1000L))
        assertNull(tracker.processCentroid(0.65f, 0.50f, 1100L))
        val event = tracker.processCentroid(0.45f, 0.50f, 1200L) // delta = -0.35

        assertNotNull(event)
        assertEquals(SwipeDirection.LEFT, event?.direction)
        assertEquals(MotionTrackerState.RECOGNIZED, tracker.state)
    }

    @Test
    fun `test vertical scroll down detection`() {
        assertNull(tracker.processCentroid(0.50f, 0.20f, 1000L))
        assertNull(tracker.processCentroid(0.50f, 0.35f, 1100L))
        val event = tracker.processCentroid(0.50f, 0.60f, 1200L) // delta = 0.40

        assertNotNull(event)
        assertEquals(SwipeDirection.DOWN, event?.direction)
        assertEquals(MotionTrackerState.RECOGNIZED, tracker.state)
    }

    @Test
    fun `test vertical scroll up detection`() {
        assertNull(tracker.processCentroid(0.50f, 0.80f, 1000L))
        assertNull(tracker.processCentroid(0.50f, 0.65f, 1100L))
        val event = tracker.processCentroid(0.50f, 0.40f, 1200L) // delta = -0.40

        assertNotNull(event)
        assertEquals(SwipeDirection.UP, event?.direction)
        assertEquals(MotionTrackerState.RECOGNIZED, tracker.state)
    }
}
