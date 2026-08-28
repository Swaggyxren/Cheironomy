package com.ydnar.cheironomy.gesture

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.ydnar.cheironomy.gesture.classifier.PoseClassifier
import com.ydnar.cheironomy.gesture.model.PoseType
import org.junit.Assert.assertEquals
import org.junit.Test

class PoseClassifierTest {

    @Test
    fun `test classify empty or insufficient landmarks returns UNKNOWN`() {
        assertEquals(PoseType.UNKNOWN, PoseClassifier.classifyPose(emptyList()))
        assertEquals(PoseType.UNKNOWN, PoseClassifier.classifyPose(List(20) { NormalizedLandmark.create(0f, 0f, 0f) }))
    }

    @Test
    fun `test palm centroid calculation with 21 landmarks`() {
        val landmarks = List(21) { idx ->
            NormalizedLandmark.create(idx.toFloat() * 0.01f, idx.toFloat() * 0.02f, 0f)
        }
        val (cx, cy) = PoseClassifier.calculatePalmCentroid(landmarks)

        // Cluster indices: 0, 5, 9, 13, 17
        val expectedX = (0f + 0.05f + 0.09f + 0.13f + 0.17f) / 5f
        val expectedY = (0f + 0.10f + 0.18f + 0.26f + 0.34f) / 5f

        assertEquals(expectedX, cx, 0.0001f)
        assertEquals(expectedY, cy, 0.0001f)
    }

    @Test
    fun `test euclidean distance calculation`() {
        val a = NormalizedLandmark.create(0f, 0f, 0f)
        val b = NormalizedLandmark.create(3f, 4f, 0f)
        assertEquals(5f, PoseClassifier.distance(a, b), 0.0001f)
    }
}
