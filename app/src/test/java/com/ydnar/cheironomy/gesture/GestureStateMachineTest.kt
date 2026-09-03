package com.ydnar.cheironomy.gesture

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.ydnar.cheironomy.data.AppSettings
import com.ydnar.cheironomy.data.GestureAction
import com.ydnar.cheironomy.data.template.GestureTemplate.MotionGestureTemplate
import com.ydnar.cheironomy.data.template.GestureTemplate.StaticGestureTemplate
import com.ydnar.cheironomy.data.template.Point2D
import com.ydnar.cheironomy.gesture.classifier.StaticTemplateMatcher
import com.ydnar.cheironomy.gesture.classifier.TrajectoryNormalizer
import com.ydnar.cheironomy.gesture.engine.GestureEngine
import com.ydnar.cheironomy.gesture.engine.GestureEngineStatus
import com.ydnar.cheironomy.gesture.engine.GestureState
import com.ydnar.cheironomy.gesture.model.GestureEvent
import com.ydnar.cheironomy.gesture.model.HandLandmarkResultBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class GestureStateMachineTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = CoroutineScope(testDispatcher)
    private lateinit var gestureEngine: GestureEngine
    private lateinit var testTemplate: StaticGestureTemplate

    @Before
    fun setUp() {
        val openPalmLandmarks = createOpenPalmLanmarks(0.5f, 0.5f)
        val normalizedPoints = StaticTemplateMatcher.normalizeLandmarks(openPalmLandmarks)!!

        testTemplate = StaticGestureTemplate(
            id = "template_open_palm",
            name = "Open Palm",
            action = GestureAction.MEDIA_PLAY_PAUSE,
            landmarks = normalizedPoints
        )

        gestureEngine = GestureEngine(
            scope = testScope,
            settings = AppSettings(
                holdDurationMs = 500L,
                confidenceThreshold = 0.60f,
                staticRejectCeiling = 0.11f,
                customTemplates = listOf(testTemplate)
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
        val landmarks = createOpenPalmLanmarks(0.5f, 0.5f)
        val bundle = createBundle(landmarks)

        for (i in 1..4) {
            gestureEngine.processFrame(bundle)
            assertEquals("Frame $i should be WARMING_UP", GestureEngineStatus.WARMING_UP, gestureEngine.status.value)
            assertEquals(GestureState.IDLE, gestureEngine.gestureState.value)
        }

        gestureEngine.processFrame(bundle)
        assertTrue(gestureEngine.status.value != GestureEngineStatus.WARMING_UP)
    }

    @Test
    fun `test holding custom static pose while still transitions IDLE to HOLDING then RECOGNIZED`() = runTest {
        val events = mutableListOf<GestureEvent>()
        val job = launch(testDispatcher) {
            gestureEngine.gestureEvents.collect { events.add(it) }
        }

        val landmarks = createOpenPalmLanmarks(0.5f, 0.5f)
        val bundle = createBundle(landmarks)

        for (i in 1..4) gestureEngine.processFrame(bundle)

        gestureEngine.processFrame(bundle)
        assertEquals(GestureEngineStatus.HOLDING, gestureEngine.status.value)
        assertEquals(GestureState.HOLDING, gestureEngine.gestureState.value)

        Thread.sleep(550L)
        gestureEngine.processFrame(bundle)

        assertEquals(GestureEngineStatus.RECOGNIZED, gestureEngine.status.value)
        assertEquals(GestureState.RECOGNIZED, gestureEngine.gestureState.value)
        assertTrue(events.any { it is GestureEvent.CustomGestureTriggered && it.template.id == testTemplate.id })

        job.cancel()
    }

    @Test
    fun `test moving hand prevents entering or accumulating static pose hold time`() {
        // Warm up at (0.3, 0.5)
        for (i in 1..4) {
            gestureEngine.processFrame(createBundle(createOpenPalmLanmarks(0.3f, 0.5f)))
        }

        // Rapidly move hand across the screen (0.05 units per frame)
        var posX = 0.35f
        for (i in 1..5) {
            gestureEngine.processFrame(createBundle(createOpenPalmLanmarks(posX, 0.5f)))
            posX += 0.05f
        }

        // Moving hand exceeds STILLNESS_MAX_VELOCITY, must not be in HOLDING
        assertNotEquals("Moving hand must not enter or hold static pose", GestureState.HOLDING, gestureEngine.gestureState.value)
    }

    @Test
    fun `test breaking pose prematurely resets immediately from HOLDING to IDLE with no delay`() {
        val openPalmLandmarks = createOpenPalmLanmarks(0.5f, 0.5f)
        val neutralLandmarks = createNeutralLandmarks(0.5f, 0.5f)

        for (i in 1..4) gestureEngine.processFrame(createBundle(openPalmLandmarks))

        gestureEngine.processFrame(createBundle(openPalmLandmarks))
        assertEquals(GestureState.HOLDING, gestureEngine.gestureState.value)

        gestureEngine.processFrame(createBundle(neutralLandmarks))

        assertEquals(GestureState.IDLE, gestureEngine.gestureState.value)
        assertEquals(GestureEngineStatus.IDLE, gestureEngine.status.value)
    }

    @Test
    fun `test hand loss immediately resets state machine and clears buffers`() {
        val openPalmLandmarks = createOpenPalmLanmarks(0.5f, 0.5f)

        for (i in 1..5) gestureEngine.processFrame(createBundle(openPalmLandmarks))
        assertEquals(GestureState.HOLDING, gestureEngine.gestureState.value)

        val lostBundle = HandLandmarkResultBundle(
            result = null,
            inferenceTimeMs = 5L,
            inputImageHeight = 480,
            inputImageWidth = 640,
            confidence = 0.1f,
            fps = 30f
        )
        gestureEngine.processFrame(lostBundle)

        assertEquals(GestureEngineStatus.SCANNING, gestureEngine.status.value)
        assertEquals(GestureState.IDLE, gestureEngine.gestureState.value)
    }

    private fun createBundle(landmarks: List<NormalizedLandmark>): HandLandmarkResultBundle {
        val result = mock(HandLandmarkerResult::class.java)
        `when`(result.landmarks()).thenReturn(listOf(landmarks))
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
        list.add(NormalizedLandmark.create(centerX, centerY + 0.15f, 0f))
        list.add(NormalizedLandmark.create(centerX - 0.04f, centerY + 0.10f, 0f))
        list.add(NormalizedLandmark.create(centerX - 0.08f, centerY + 0.06f, 0f))
        list.add(NormalizedLandmark.create(centerX - 0.12f, centerY + 0.03f, 0f))
        list.add(NormalizedLandmark.create(centerX - 0.16f, centerY + 0.00f, 0f))
        list.add(NormalizedLandmark.create(centerX - 0.04f, centerY + 0.05f, 0f))
        list.add(NormalizedLandmark.create(centerX - 0.05f, centerY - 0.03f, 0f))
        list.add(NormalizedLandmark.create(centerX - 0.06f, centerY - 0.10f, 0f))
        list.add(NormalizedLandmark.create(centerX - 0.07f, centerY - 0.18f, 0f))
        list.add(NormalizedLandmark.create(centerX, centerY + 0.05f, 0f))
        list.add(NormalizedLandmark.create(centerX, centerY - 0.04f, 0f))
        list.add(NormalizedLandmark.create(centerX, centerY - 0.12f, 0f))
        list.add(NormalizedLandmark.create(centerX, centerY - 0.20f, 0f))
        list.add(NormalizedLandmark.create(centerX + 0.04f, centerY + 0.05f, 0f))
        list.add(NormalizedLandmark.create(centerX + 0.05f, centerY - 0.03f, 0f))
        list.add(NormalizedLandmark.create(centerX + 0.06f, centerY - 0.10f, 0f))
        list.add(NormalizedLandmark.create(centerX + 0.07f, centerY - 0.17f, 0f))
        list.add(NormalizedLandmark.create(centerX + 0.08f, centerY + 0.07f, 0f))
        list.add(NormalizedLandmark.create(centerX + 0.09f, centerY + 0.00f, 0f))
        list.add(NormalizedLandmark.create(centerX + 0.10f, centerY - 0.06f, 0f))
        list.add(NormalizedLandmark.create(centerX + 0.11f, centerY - 0.13f, 0f))
        return list
    }

    private fun createNeutralLandmarks(centerX: Float, centerY: Float): List<NormalizedLandmark> {
        return List(21) { idx ->
            NormalizedLandmark.create(centerX + idx * 0.005f, centerY + idx * 0.005f, 0f)
        }
    }
}
