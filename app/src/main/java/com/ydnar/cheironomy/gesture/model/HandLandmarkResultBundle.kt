package com.ydnar.cheironomy.gesture.model

import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

/**
 * Encapsulates the results of a MediaPipe Hand Landmarker detection frame.
 */
data class HandLandmarkResultBundle(
    val result: HandLandmarkerResult?,
    val inferenceTimeMs: Long,
    val inputImageHeight: Int,
    val inputImageWidth: Int
)
