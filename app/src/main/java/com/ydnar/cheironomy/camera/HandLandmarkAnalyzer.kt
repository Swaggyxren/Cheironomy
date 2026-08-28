package com.ydnar.cheironomy.camera

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.ydnar.cheironomy.gesture.HandLandmarkerHelper

/**
 * CameraX ImageAnalysis.Analyzer that streams camera frames to MediaPipe HandLandmarker.
 */
class HandLandmarkAnalyzer(
    private val handLandmarkerHelper: HandLandmarkerHelper,
    private val isFrontCamera: Boolean = true
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: ImageProxy) {
        handLandmarkerHelper.detectLiveStream(
            imageProxy = imageProxy,
            isFrontCamera = isFrontCamera
        )
    }
}
