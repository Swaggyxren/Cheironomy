package com.ydnar.cheironomy.data

import com.ydnar.cheironomy.data.template.GestureTemplate
import com.ydnar.cheironomy.data.template.GestureTemplate.MotionGestureTemplate
import com.ydnar.cheironomy.data.template.GestureTemplate.StaticGestureTemplate
import com.ydnar.cheironomy.data.template.Point2D
import com.ydnar.cheironomy.data.template.TrajectoryStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureTemplateSerializationTest {

    @Test
    fun testMotionTemplateRoundtripJson() {
        val points = listOf(
            Point2D(0f, 0f),
            Point2D(0.5f, 0.3f),
            Point2D(1.0f, 0.6f)
        )
        val stats = TrajectoryStats(
            totalPathLength = 0.75f,
            boundingBoxWidth = 1.0f,
            boundingBoxHeight = 0.6f,
            displacementX = 1.0f,
            displacementY = 0.6f
        )
        val motionTemplate = MotionGestureTemplate(
            id = "test_motion_1",
            name = "Test Wave",
            action = GestureAction.MEDIA_PLAY_PAUSE,
            createdAt = 1234567890L,
            normalizedPoints = points,
            stats = stats
        )

        val json = GestureTemplate.toJson(motionTemplate)
        val deserialized = GestureTemplate.fromJson(json) as? MotionGestureTemplate

        assertEquals(motionTemplate.id, deserialized?.id)
        assertEquals(motionTemplate.name, deserialized?.name)
        assertEquals(motionTemplate.action, deserialized?.action)
        assertEquals(motionTemplate.createdAt, deserialized?.createdAt)
        assertEquals(3, deserialized?.normalizedPoints?.size)
        assertEquals(0.75f, deserialized?.stats?.totalPathLength ?: 0f, 1e-4f)
    }

    @Test
    fun testStaticTemplateRoundtripJson() {
        val landmarks = List(21) { i -> Point2D(i * 0.05f, i * 0.04f) }
        val staticTemplate = StaticGestureTemplate(
            id = "test_static_1",
            name = "Test Fist",
            action = GestureAction.SWIPE_LEFT,
            createdAt = 9876543210L,
            landmarks = landmarks
        )

        val json = GestureTemplate.toJson(staticTemplate)
        val deserialized = GestureTemplate.fromJson(json) as? StaticGestureTemplate

        assertEquals(staticTemplate.id, deserialized?.id)
        assertEquals(staticTemplate.name, deserialized?.name)
        assertEquals(staticTemplate.action, deserialized?.action)
        assertEquals(21, deserialized?.landmarks?.size)
    }

    @Test
    fun testListSerialization() {
        val motion = MotionGestureTemplate(
            id = "m1",
            name = "Motion 1",
            action = GestureAction.SCROLL_DOWN,
            normalizedPoints = listOf(Point2D(0f, 0f), Point2D(1f, 1f)),
            stats = TrajectoryStats(1.414f, 1f, 1f, 1f, 1f)
        )
        val static = StaticGestureTemplate(
            id = "s1",
            name = "Static 1",
            action = GestureAction.SCROLL_UP,
            landmarks = List(21) { Point2D(0f, 0f) }
        )

        val list = listOf(motion, static)
        val jsonStr = GestureTemplate.listToJson(list)
        val deserializedList = GestureTemplate.listFromJson(jsonStr)

        assertEquals(2, deserializedList.size)
        assertTrue(deserializedList[0] is MotionGestureTemplate)
        assertTrue(deserializedList[1] is StaticGestureTemplate)
    }
}
