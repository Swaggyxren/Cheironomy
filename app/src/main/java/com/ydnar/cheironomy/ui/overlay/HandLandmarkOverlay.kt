package com.ydnar.cheironomy.ui.overlay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.ydnar.cheironomy.gesture.model.HandLandmarkResultBundle

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
 * Real-time Canvas overlay rendering 21-point hand landmark skeleton,
 * with exact FILL_CENTER aspect ratio scaling and front-camera mirror alignment.
 */
@Composable
fun HandLandmarkOverlay(
    resultBundle: HandLandmarkResultBundle?,
    modifier: Modifier = Modifier,
    isFrontCamera: Boolean = true,
    lineColor: Color = Color(0xCC00E5FF),
    jointColor: Color = Color(0xFF00E5FF),
    tipColor: Color = Color(0xFF00E676)
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val bundle = resultBundle ?: return@Canvas
        val landmarkResult = bundle.result ?: return@Canvas
        if (landmarkResult.landmarks().isEmpty()) return@Canvas

        val canvasWidth = size.width
        val canvasHeight = size.height

        val imgWidth = bundle.inputImageWidth.toFloat().coerceAtLeast(1f)
        val imgHeight = bundle.inputImageHeight.toFloat().coerceAtLeast(1f)

        // Compute FILL_CENTER scale and offset to match PreviewView
        val scale = maxOf(canvasWidth / imgWidth, canvasHeight / imgHeight)
        val scaledWidth = imgWidth * scale
        val scaledHeight = imgHeight * scale
        val offsetX = (canvasWidth - scaledWidth) / 2f
        val offsetY = (canvasHeight - scaledHeight) / 2f

        fun mapPoint(landmark: NormalizedLandmark): Offset {
            val normalizedX = if (isFrontCamera) (1f - landmark.x()) else landmark.x()
            val normalizedY = landmark.y()

            val px = normalizedX * scaledWidth + offsetX
            val py = normalizedY * scaledHeight + offsetY
            return Offset(px, py)
        }

        for (handLandmarks in landmarkResult.landmarks()) {
            if (handLandmarks.size < 21) continue

            // 1. Draw skeletal connector lines
            for ((startIdx, endIdx) in HAND_CONNECTIONS) {
                val start = handLandmarks[startIdx]
                val end = handLandmarks[endIdx]

                val startOffset = mapPoint(start)
                val endOffset = mapPoint(end)

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
                val center = mapPoint(landmark)
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
