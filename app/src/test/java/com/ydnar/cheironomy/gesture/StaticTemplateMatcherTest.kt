package com.ydnar.cheironomy.gesture

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
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
    fun `test translation and scale invariance`() {
        val baseHand = List(21) { i ->
            Point2D(0.5f + (i % 5) * 0.05f, 0.5f - (i / 5) * 0.08f)
        }

        val normA = StaticTemplateMatcher.normalizePoints(baseHand)
        assertNotNull(normA)
        assertEquals(21, normA!!.size)

        // Translate and scale hand
        val transformedHand = baseHand.map { Point2D((it.x + 0.3f) * 1.5f, (it.y + 0.2f) * 1.5f) }
        val normB = StaticTemplateMatcher.normalizePoints(transformedHand)
        assertNotNull(normB)

        val dist = StaticTemplateMatcher.computeDistance(normA, normB!!)
        assertTrue("Distance after translation & scale should be virtually 0, was $dist", dist < 1e-4f)
    }

    @Test
    fun `test nearest match wins among multiple registered templates`() {
        // Template 1: Peace Sign
        val peacePoints = List(21) { i ->
            Point2D(0.3f + (i % 5) * 0.03f, 0.4f - (i / 5) * 0.05f)
        }
        val templatePeace = StaticGestureTemplate(
            id = "pose_peace",
            name = "Peace Sign",
            action = GestureAction.MEDIA_PLAY_PAUSE,
            landmarks = StaticTemplateMatcher.normalizePoints(peacePoints)!!
        )

        // Template 2: Fist (distinctly different layout)
        val fistPoints = List(21) { i ->
            Point2D(0.6f + (i % 3) * 0.08f, 0.6f + (i / 3) * 0.08f)
        }
        val templateFist = StaticGestureTemplate(
            id = "pose_fist",
            name = "Fist",
            action = GestureAction.SCROLL_DOWN,
            landmarks = StaticTemplateMatcher.normalizePoints(fistPoints)!!
        )

        val templates = listOf(templatePeace, templateFist)

        // Live input: very close to Peace Sign (distance < 0.05)
        val liveLandmarks = peacePoints.map {
            NormalizedLandmark.create(it.x + 0.005f, it.y - 0.005f, 0f)
        }

        val match = StaticTemplateMatcher.match(
            landmarks = liveLandmarks,
            templates = templates,
            rejectCeiling = StaticTemplateMatcher.DEFAULT_REJECT_CEILING,
            marginThreshold = 0.15f
        )

        assertNotNull(match)
        assertEquals("pose_peace", match?.first?.id)
        assertEquals("Peace Sign", match?.first?.name)
    }

    @Test
    fun `test reject ceiling rejects random or dissimilar hand poses`() {
        val templatePoints = List(21) { i ->
            Point2D(0.2f + (i % 5) * 0.04f, 0.3f - (i / 5) * 0.06f)
        }
        val template = StaticGestureTemplate(
            id = "pose_1",
            name = "Recorded Pose",
            action = GestureAction.MEDIA_NEXT,
            landmarks = StaticTemplateMatcher.normalizePoints(templatePoints)!!
        )

        // Dissimilar live landmarks
        val dissimilarLive = List(21) { i ->
            NormalizedLandmark.create(0.8f - (i % 3) * 0.12f, 0.8f + (i / 3) * 0.12f, 0f)
        }

        val match = StaticTemplateMatcher.match(
            landmarks = dissimilarLive,
            templates = listOf(template),
            rejectCeiling = StaticTemplateMatcher.DEFAULT_REJECT_CEILING
        )

        assertNull("Dissimilar input exceeding reject ceiling must be rejected", match)
    }

    @Test
    fun `test relaxed neutral hand is rejected by tightened 0_11 ceiling against open palm`() {
        // Defined open palm
        val openPalm = List(21) { idx ->
            Point2D(0.5f, 0.8f - idx * 0.03f)
        }
        val template = StaticGestureTemplate(
            id = "open_palm",
            name = "Open Palm",
            action = GestureAction.MEDIA_PLAY_PAUSE,
            landmarks = StaticTemplateMatcher.normalizePoints(openPalm)!!
        )

        // Relaxed hand (curled fingertips with average deviation of ~0.14)
        val relaxedHand = openPalm.mapIndexed { idx, p ->
            val curl = if (idx in listOf(4, 8, 12, 16, 20)) 0.04f else 0.015f
            NormalizedLandmark.create(p.x + curl, p.y + curl, 0f)
        }

        val match = StaticTemplateMatcher.match(
            landmarks = relaxedHand,
            templates = listOf(template),
            rejectCeiling = StaticTemplateMatcher.DEFAULT_REJECT_CEILING
        )

        assertNull("Neutral/relaxed hand must not trigger static open palm template", match)
    }

    @Test
    fun `test margin check rejects ambiguous input between two very similar templates`() {
        val basePoints = List(21) { i ->
            Point2D(0.4f + (i % 5) * 0.04f, 0.4f - (i / 5) * 0.06f)
        }

        val templateA = StaticGestureTemplate(
            id = "pose_a",
            name = "Pose A",
            action = GestureAction.MEDIA_PLAY_PAUSE,
            landmarks = StaticTemplateMatcher.normalizePoints(basePoints)!!
        )

        // Template B is almost identical to Template A
        val slightlyModified = basePoints.map { Point2D(it.x + 0.005f, it.y + 0.005f) }
        val templateB = StaticGestureTemplate(
            id = "pose_b",
            name = "Pose B",
            action = GestureAction.MEDIA_NEXT,
            landmarks = StaticTemplateMatcher.normalizePoints(slightlyModified)!!
        )

        // Live input positioned directly between A and B
        val ambiguousLive = basePoints.map {
            NormalizedLandmark.create(it.x + 0.0025f, it.y + 0.0025f, 0f)
        }

        val match = StaticTemplateMatcher.match(
            landmarks = ambiguousLive,
            templates = listOf(templateA, templateB),
            rejectCeiling = 0.15f,
            marginThreshold = 0.20f // 20% margin required
        )

        assertNull("Ambiguous input with margin < 20% must be rejected", match)
    }
}
