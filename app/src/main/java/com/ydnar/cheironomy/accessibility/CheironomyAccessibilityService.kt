package com.ydnar.cheironomy.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.ydnar.cheironomy.data.GestureAction
import com.ydnar.cheironomy.gesture.model.SwipeDirection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AccessibilityService that receives GestureEvents and dispatches simulated touch gestures.
 */
class CheironomyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true
        Log.i(TAG, "Cheironomy AccessibilityService connected and ready for gestures.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op: we only use this service to dispatch touch gestures
    }

    override fun onInterrupt() {
        Log.w(TAG, "Cheironomy AccessibilityService interrupted.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        _isServiceConnected.value = false
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "Cheironomy AccessibilityService unbound.")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceConnected.value = false
        if (instance == this) {
            instance = null
        }
    }

    /**
     * Executes simulated swipe or scroll gesture on screen.
     */
    fun performSwipe(direction: SwipeDirection): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

        val metrics: DisplayMetrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()

        val (startX, startY, endX, endY) = when (direction) {
            SwipeDirection.LEFT -> {
                // Swipe Left: Finger moves Right to Left
                listOf(width * 0.85f, height * 0.5f, width * 0.15f, height * 0.5f)
            }
            SwipeDirection.RIGHT -> {
                // Swipe Right: Finger moves Left to Right
                listOf(width * 0.15f, height * 0.5f, width * 0.85f, height * 0.5f)
            }
            SwipeDirection.UP -> {
                // Scroll Down: Finger moves Bottom to Top
                listOf(width * 0.5f, height * 0.75f, width * 0.5f, height * 0.25f)
            }
            SwipeDirection.DOWN -> {
                // Scroll Up: Finger moves Top to Bottom
                listOf(width * 0.5f, height * 0.25f, width * 0.5f, height * 0.75f)
            }
        }

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, 200L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        val dispatched = dispatchGesture(
            gesture,
            object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.i(TAG, "Gesture completed: $direction")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.w(TAG, "Gesture cancelled: $direction")
                }
            },
            null
        )

        Log.i(TAG, "Dispatched gesture: $direction (success=$dispatched)")
        return dispatched
    }

    companion object {
        private const val TAG = "CheironomyA11y"
        private val _isServiceConnected = MutableStateFlow(false)
        val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

        var instance: CheironomyAccessibilityService? = null
            private set

        /**
         * Dispatches touch gesture for a specified GestureAction if service is connected.
         */
        fun dispatchAction(action: GestureAction): Boolean {
            val service = instance ?: run {
                Log.w(TAG, "AccessibilityService is not enabled or connected.")
                return false
            }

            return when (action) {
                GestureAction.SWIPE_LEFT -> service.performSwipe(SwipeDirection.LEFT)
                GestureAction.SWIPE_RIGHT -> service.performSwipe(SwipeDirection.RIGHT)
                GestureAction.SCROLL_DOWN -> service.performSwipe(SwipeDirection.UP) // swipe up scrolls down
                GestureAction.SCROLL_UP -> service.performSwipe(SwipeDirection.DOWN) // swipe down scrolls up
                else -> false
            }
        }
    }
}
