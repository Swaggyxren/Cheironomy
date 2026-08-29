package com.ydnar.cheironomy.gesture

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.ydnar.cheironomy.data.AppSettings
import com.ydnar.cheironomy.gesture.classifier.MotionTrackerState
import com.ydnar.cheironomy.gesture.engine.GestureEngine
import com.ydnar.cheironomy.gesture.engine.GestureEngineStatus
import com.ydnar.cheironomy.gesture.engine.GestureState
import com.ydnar.cheironomy.gesture.model.GestureEvent
import com.ydnar.cheironomy.gesture.model.HandLandmarkResultBundle
import com.ydnar.cheironomy.gesture.model.PoseType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GestureStateMachineTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = CoroutineScope(testDispatcher)
    private lateinit var gestureEngine: GestureEngine

    @Before
    fun setUp() {
        gestureEngine = GestureEngine(
            scope = testScope,
            settings = AppSettings(
                swipeSensitivity = 0.20f,
                holdDurationMs = 500L,
                confidenceThreshold = 0.60f
            )
        )
    }

    @Test
    fun `test no hand frame sets SCANNING and IDLE state`() {
        val bundle = HandLandmarkResultBundle(
            result = null,
            inferenceTimeMs = 10L,
            inputImageHeight = 480,
            inputImageWidth = 640,
            confidence = 0f,
            fps = 30f
        )

        gestureEngine.processFrame(bundle)

        assertEquals(GestureEngineStatus.SCANNING, gestureEngine.status.value)
        assertEquals(GestureState.IDLE, gestureEngine.gestureState.value)
    }

    @Test
    fun `test initial 4 frames are WARMING_UP debounce`() {
        val landmarks = createOpenPalmLanmarks(centerX = 0.5f, centerY = 0.5f)
        val bundle = createBundle(landmarks)

        // Frames 1 to 4 should be WARMING_UP
        for (i in 1..4) {
            gestureEngine.processFrame(bundle)
            assertEquals("Frame $i should be WARMING_UP", GestureEngineStatus.WARMING_UP, gestureEngine.status.value)
            assertEquals(GestureState.IDLE, gestureEngine.gestureState.value)
        }

        // Frame 5 should transition out of WARMING_UP
        gestureEngine.processFrame(bundle)
        assertTrue(gestureEngine.status.value != GestureEngineStatus.WARMING_UP)
    }

    @Test
    fun `test holding open palm transitions IDLE to HOLDING then RECOGNIZED`() = runTest {
        val events = mutableListOf<GestureEvent>()
        val job = launch(testDispatcher) {
            gestureEngine.gestureEvents.collect { events.add(it) }
        }

        val landmarks = createOpenPalmLanmarks(centerX = 0.5f, centerY = 0.5f)
        val bundle = createBundle(landmarks)

        // Warm up (4 frames)
        for (i in 1..4) gestureEngine.processFrame(bundle)

        // Frame 5: pose detected, enters HOLDING
        gestureEngine.processFrame(bundle)
        assertEquals(GestureEngineStatus.HOLDING, gestureEngine.status.value)
        assertEquals(GestureState.HOLDING, gestureEngine.gestureState.value)

        // Simulate time passing (550ms hold)
        Thread.sleep(550L)
        gestureEngine.processFrame(bundle)

        assertEquals(GestureEngineStatus.RECOGNIZED, gestureEngine.status.value)
        assertEquals(GestureState.RECOGNIZED, gestureEngine.gestureState.value)
        assertTrue(events.any { it is GestureEvent.StaticPoseHeld && it.pose == PoseType.OPEN_PALM })

        job.cancel()
    }

    @Test
    fun `test breaking pose prematurely resets immediately from HOLDING to IDLE with no delay`() {
        val openPalmLandmarks = createOpenPalmLanmarks(centerX = 0.5f, centerY = 0.5f)
        val neutralLandmarks = createNeutralLandmarks(centerX = 0.5f, centerY = 0.5f)

        // Warm up
        for (i in 1..4) gestureEngine.processFrame(createBundle(openPalmLandmarks))

        // Enter HOLDING
        gestureEngine.processFrame(createBundle(openPalmLandmarks))
        assertEquals(GestureState.HOLDING, gestureEngine.gestureState.value)

        // Break pose before hold duration (send neutral landmarks)
        gestureEngine.processFrame(createBundle(neutralLandmarks))

        // Should immediately return to IDLE without waiting for any cooldown
        assertEquals(GestureState.IDLE, gestureEngine.gestureState.value)
        assertEquals(GestureEngineStatus.IDLE, gestureEngine.status.value)
    }

    @Test
    fun `test hand loss immediately resets state machine and clears buffers`() {
        val openPalmLandmarks = createOpenPalmLanmarks(centerX = 0.5f, centerY = 0.5f)

        // Warm up + enter HOLDING
        for (i in 1..5) gestureEngine.processFrame(createBundle(openPalmLandmarks))
        assertEquals(GestureState.HOLDING, gestureEngine.gestureState.value)

        // Hand lost frame (result = null, low confidence)
        val lostBundle = HandLandmarkResultBundle(
            result = null,
            inferenceTimeMs = 5L,
            inputImageHeight = 480,
            inputImageWidth = 640,
            confidence = 0.1f,
            fps = 30f
        )
        gestureEngine.processFrame(lostBundle)

        // Must reset immediately to SCANNING and IDLE
        assertEquals(GestureEngineStatus.SCANNING, gestureEngine.status.value)
        assertEquals(GestureState.IDLE, gestureEngine.gestureState.value)
    }

    private fun createBundle(landmarks: List<NormalizedLandmark>): HandLandmarkResultBundle {
        val result = HandLandmarkerResult.create(
            listOf(landmarks),
            emptyList(),
            emptyList(),
            0L
        )
        return HandLandmarkResultBundle(
            result = result,
            inferenceTimeMs = 15L,
            inputImageHeight = 480,
            inputImageWidth = 640,
            confidence = 0.95f,
            fps = 30f
        )
    }

    private fun createOpenPalmLanmarks(centerX: Float, centerY: Float): List<NormalizedLandmark> {
        val list = ArrayList<NormalizedLandmark>(21)
        // Wrist (0)
        list.add(NormalizedLandmark.create(centerX, centerY + 0.15f, 0f))
        // Thumb (1..4) extended outward
        list.add(NormalizedLandmark.create(centerX - 0.04f, centerY + 0.10f, 0f))
        list.add(NormalizedLandmark.create(centerX - 0.08f, centerY + 0.06f, 0f))
        list.add(NormalizedLandmark.create(centerX - 0.12f, centerY + 0.03f, 0f))
        list.add(NormalizedLandmark.create(centerX - 0.16f, centerY + 0.00f, 0f))
        // Index (5..8) extended upward
        list.add(NormalizedLandmark.create(centerX - 0.04f, centerY + 0.05f, 0f))
        list.add(NormalizedLandmark.create(centerX - 0.05f, centerY - 0.03f, 0f))
        list.add(NormalizedLandmark.create(centerX - 0.06f, centerY - 0.10f, 0f))
        list.add(NormalizedLandmark.create(centerX - 0.07f, centerY - 0.18f, 0f))
        // Middle (9..12) extended upward
        list.add(NormalizedLandmark.create(centerX, centerY + 0.05f, 0f))
        list.add(NormalizedLandmark.create(centerX, centerY - 0.04f, 0f))
        list.add(NormalizedLandmark.create(centerX, centerY - 0.12f, 0f))
        list.add(NormalizedLandmark.create(centerX, centerY - 0.20f, 0f))
        // Ring (13..16) extended upward
        list.add(NormalizedLandmark.create(centerX + 0.04f, centerY + 0.05f, 0f))
        list.add(NormalizedLandmark.create(centerX + 0.05f, centerY - 0.03f, 0f))
        list.add(NormalizedLandmark.create(centerX + 0.06f, centerY - 0.10f, 0f))
        list.add(NormalizedLandmark.create(centerX + 0.07f, centerY - 0.17f, 0f))
        // Pinky (17..20) extended upward
        list.add(NormalizedLandmark.create(centerX + 0.08f, centerY + 0.07f, 0f))
        list.add(NormalizedLandmark.create(centerX + 0.09f, centerY + 0.00f, 0f))
        list.add(NormalizedLandmark.create(centerX + 0.10f, centerY - 0.06f, 0f))
        list.add(NormalizedLandmark.create(centerX + 0.11f, centerY - 0.13f, 0f))
        return list
    }

    private fun createNeutralLandmarks(centerX: Float, centerY: Float): List<NormalizedLandmark> {
        // Flat landmarks with curled fingers -> UNKNOWN pose
        return List(21) { idx ->
            NormalizedLandmark.create(centerX + idx * 0.005f, centerY + idx * 0.005f, 0f)
        }
    }
}
