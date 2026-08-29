package com.ydnar.cheironomy.gesture

import com.ydnar.cheironomy.data.GestureAction
import com.ydnar.cheironomy.data.template.GestureTemplate.StaticGestureTemplate
import com.ydnar.cheironomy.data.template.Point2D
import com.ydnar.cheironomy.gesture.classifier.StaticTemplateMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticTemplateMatcherTest {

    @Test
    fun testNormalizePointsAndDistance() {
        // Create 21 dummy points representing an open hand
        val baseHand = List(21) { i ->
            Point2D(0.5f + (i % 5) * 0.05f, 0.5f - (i / 5) * 0.08f)
        }

        val normA = StaticTemplateMatcher.normalizePoints(baseHand)
        assertNotNull(normA)
        assertEquals(21, normA!!.size)

        // Translation invariance: shift whole hand by (+0.3, +0.2)
        val shiftedHand = baseHand.map { Point2D(it.x + 0.3f, it.y + 0.2f) }
        val normB = StaticTemplateMatcher.normalizePoints(shiftedHand)
        assertNotNull(normB)

        // Distance between base and shifted should be virtually 0
        val dist = StaticTemplateMatcher.computeDistance(normA, normB!!)
        assertTrue("Distance after translation should be ~0, was $dist", dist < 1e-4f)
    }

    @Test
    fun testStaticMatching() {
        val templatePoints = List(21) { i ->
            Point2D(0.4f + (i % 5) * 0.04f, 0.4f - (i / 5) * 0.06f)
        }
        val normalizedTemplate = StaticTemplateMatcher.normalizePoints(templatePoints)!!

        val template = StaticGestureTemplate(
            id = "pose_1",
            name = "Custom Thumbs Up",
            action = GestureAction.SWIPE_RIGHT,
            landmarks = normalizedTemplate
        )

        // Near-identical performance
        val liveHand = templatePoints.map { Point2D(it.x + 0.01f, it.y - 0.01f) }
        val normalizedLive = StaticTemplateMatcher.normalizePoints(liveHand)!!

        val dist = StaticTemplateMatcher.computeDistance(normalizedLive, template.landmarks)
        assertTrue("Distance should be small for similar pose, was $dist", dist < 0.05f)
    }
}
