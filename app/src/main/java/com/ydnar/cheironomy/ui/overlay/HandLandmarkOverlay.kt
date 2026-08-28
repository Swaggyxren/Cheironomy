package com.ydnar.cheironomy.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

// Standard MediaPipe Hand Landmark skeleton connections
private val HAND_CONNECTIONS = listOf(
    // Thumb
    0 to 1, 1 to 2, 2 to 3, 3 to 4,
    // Index Finger
    0 to 5, 5 to 6, 6 to 7, 7 to 8,
    // Middle Finger
    0 to 9, 9 to 10, 10 to 11, 11 to 12,
    // Ring Finger
    0 to 13, 13 to 14, 14 to 15, 15 to 16,
    // Pinky
    0 to 17, 17 to 18, 18 to 19, 19 to 20,
    // Palm Base
    5 to 9, 9 to 13, 13 to 17
)

private val FINGER_TIPS = setOf(4, 8, 12, 16, 20)

/**
 * Real-time Canvas overlay rendering 21-point hand landmark skeleton.
 */
@Composable
fun HandLandmarkOverlay(
    landmarkResult: HandLandmarkerResult?,
    modifier: Modifier = Modifier,
    lineColor: Color = Color(0xCC00E5FF),
    jointColor: Color = Color(0xFF00E5FF),
    tipColor: Color = Color(0xFF00E676)
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (landmarkResult == null || landmarkResult.landmarks().isEmpty()) return@Canvas

        val canvasWidth = size.width
        val canvasHeight = size.height

        for (handLandmarks in landmarkResult.landmarks()) {
            if (handLandmarks.size < 21) continue

            // 1. Draw skeletal connector lines
            for ((startIdx, endIdx) in HAND_CONNECTIONS) {
                val start = handLandmarks[startIdx]
                val end = handLandmarks[endIdx]

                val startOffset = Offset(
                    x = start.x() * canvasWidth,
                    y = start.y() * canvasHeight
                )
                val endOffset = Offset(
                    x = end.x() * canvasWidth,
                    y = end.y() * canvasHeight
                )

                drawLine(
                    color = lineColor,
                    start = startOffset,
                    end = endOffset,
                    strokeWidth = 5f,
                    cap = StrokeCap.Round
                )
            }

            // 2. Draw joint dots
            handLandmarks.forEachIndexed { index, landmark ->
                val center = Offset(
                    x = landmark.x() * canvasWidth,
                    y = landmark.y() * canvasHeight
                )

                val isTip = index in FINGER_TIPS
                val radius = if (isTip) 10f else 7f
                val color = if (isTip) tipColor else jointColor

                // Outer halo for fingertips
                if (isTip) {
                    drawCircle(
                        color = tipColor.copy(alpha = 0.35f),
                        radius = 16f,
                        center = center
                    )
                }

                drawCircle(
                    color = color,
                    radius = radius,
                    center = center
                )
            }
        }
    }
}
