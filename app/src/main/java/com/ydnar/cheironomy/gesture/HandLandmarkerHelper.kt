package com.ydnar.cheironomy.gesture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.ydnar.cheironomy.gesture.model.HandLandmarkResultBundle

/**
 * Helper class that wraps MediaPipe HandLandmarker for live-stream inference
 * with confidence extraction and FPS telemetry tracking.
 */
class HandLandmarkerHelper(
    var minHandDetectionConfidence: Float = DEFAULT_HAND_DETECTION_CONFIDENCE,
    var minHandTrackingConfidence: Float = DEFAULT_HAND_TRACKING_CONFIDENCE,
    var minHandPresenceConfidence: Float = DEFAULT_HAND_PRESENCE_CONFIDENCE,
    var maxNumHands: Int = DEFAULT_NUM_HANDS,
    var currentDelegate: Int = DELEGATE_GPU,
    var runningMode: RunningMode = RunningMode.LIVE_STREAM,
    val context: Context,
    val handLandmarkerHelperListener: LandmarkerListener? = null
) {

    private var handLandmarker: HandLandmarker? = null

    // FPS tracking
    private var lastFrameTimeMs: Long = 0L
    private var smoothedFps: Float = 30f

    init {
        setupHandLandmarker()
    }

    fun clearHandLandmarker() {
        handLandmarker?.close()
        handLandmarker = null
    }

    fun isClose(): Boolean {
        return handLandmarker == null
    }

    fun setupHandLandmarker() {
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath(MP_HAND_LANDMARKER_TASK)

        when (currentDelegate) {
            DELEGATE_CPU -> baseOptionsBuilder.setDelegate(Delegate.CPU)
            DELEGATE_GPU -> baseOptionsBuilder.setDelegate(Delegate.GPU)
        }

        try {
            val baseOptions = baseOptionsBuilder.build()
            val optionsBuilder = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinHandDetectionConfidence(minHandDetectionConfidence)
                .setMinTrackingConfidence(minHandTrackingConfidence)
                .setMinHandPresenceConfidence(minHandPresenceConfidence)
                .setNumHands(maxNumHands)
                .setRunningMode(runningMode)

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder
                    .setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            val options = optionsBuilder.build()
            handLandmarker = HandLandmarker.createFromOptions(context, options)
            Log.i(TAG, "MediaPipe HandLandmarker initialized successfully with delegate=$currentDelegate")
        } catch (e: IllegalStateException) {
            Log.e(TAG, "MediaPipe failed to load model with delegate $currentDelegate: ${e.message}")
            if (currentDelegate == DELEGATE_GPU) {
                Log.w(TAG, "Falling back to CPU delegate.")
                currentDelegate = DELEGATE_CPU
                setupHandLandmarker()
            } else {
                handLandmarkerHelperListener?.onError(
                    "MediaPipe Hand Landmarker failed to initialize: " + e.message
                )
            }
        } catch (e: RuntimeException) {
            Log.e(TAG, "MediaPipe initialization runtime exception: ${e.message}", e)
            handLandmarkerHelperListener?.onError(
                "MediaPipe Hand Landmarker runtime error: " + e.message
            )
        }
    }

    fun detectLiveStream(
        imageProxy: ImageProxy,
        isFrontCamera: Boolean = true
    ) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException(
                "Attempting to call detectLiveStream while runningMode is not LIVE_STREAM"
            )
        }

        val frameTime = SystemClock.uptimeMillis()

        // Calculate live FPS
        if (lastFrameTimeMs > 0L) {
            val deltaMs = (frameTime - lastFrameTimeMs).coerceAtLeast(1L)
            val instantFps = 1000f / deltaMs
            smoothedFps = smoothedFps * 0.85f + instantFps * 0.15f
        }
        lastFrameTimeMs = frameTime

        imageProxy.use {
            val bitmap = imageProxy.toBitmap()
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val finalBitmap = if (rotationDegrees != 0) {
                val matrix = Matrix().apply {
                    postRotate(rotationDegrees.toFloat())
                }
                val rotated = Bitmap.createBitmap(
                    bitmap,
                    0,
                    0,
                    bitmap.width,
                    bitmap.height,
                    matrix,
                    true
                )
                if (rotated !== bitmap) {
                    bitmap.recycle()
                }
                rotated
            } else {
                bitmap
            }

            val mpImage = BitmapImageBuilder(finalBitmap).build()
            handLandmarker?.detectAsync(mpImage, frameTime)
        }
    }

    private fun returnLivestreamResult(
        result: HandLandmarkerResult,
        input: MPImage
    ) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()

        // Extract confidence from first detected hand handedness category score
        val confidence = try {
            result.handedness().firstOrNull()?.firstOrNull()?.score() ?: 0f
        } catch (e: Exception) {
            0f
        }

        handLandmarkerHelperListener?.onResults(
            HandLandmarkResultBundle(
                result = result,
                inferenceTimeMs = inferenceTime,
                inputImageHeight = input.height,
                inputImageWidth = input.width,
                confidence = confidence,
                fps = smoothedFps
            )
        )
    }

    private fun returnLivestreamError(
        error: RuntimeException
    ) {
        Log.e(TAG, "MediaPipe livestream error: ${error.message}", error)
        handLandmarkerHelperListener?.onError(
            error.message ?: "An unknown MediaPipe inference error occurred."
        )
    }

    interface LandmarkerListener {
        fun onError(error: String, errorCode: Int = 0)
        fun onResults(resultBundle: HandLandmarkResultBundle)
    }

    companion object {
        private const val TAG = "HandLandmarkerHelper"
        private const val MP_HAND_LANDMARKER_TASK = "hand_landmarker.task"

        const val DELEGATE_CPU = 0
        const val DELEGATE_GPU = 1

        const val DEFAULT_HAND_DETECTION_CONFIDENCE = 0.65f
        const val DEFAULT_HAND_TRACKING_CONFIDENCE = 0.65f
        const val DEFAULT_HAND_PRESENCE_CONFIDENCE = 0.65f
        const val DEFAULT_NUM_HANDS = 1
    }
}
