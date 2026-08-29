package com.ydnar.cheironomy.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.ydnar.cheironomy.data.GestureAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AccessibilityService that dispatches system-wide touch gestures (swipe, scroll)
 * using dispatchGesture() on API 24+.
 */
class CheironomyAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceConnected.value = true
        Log.i(TAG, "AccessibilityService connected successfully.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Passive listener
    }

    override fun onInterrupt() {
        Log.w(TAG, "AccessibilityService interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
            _isServiceConnected.value = false
        }
        Log.i(TAG, "AccessibilityService destroyed.")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        _isServiceConnected.value = false
        instance = null
        return super.onUnbind(intent)
    }

    /**
     * Executes simulated swipe or scroll gesture on screen.
     */
    fun performAction(action: GestureAction): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

        val metrics: DisplayMetrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()

        val (startX, startY, endX, endY) = when (action) {
            GestureAction.SWIPE_LEFT -> {
                // Swipe Left: Finger moves Right to Left
                listOf(width * 0.85f, height * 0.5f, width * 0.15f, height * 0.5f)
            }
            GestureAction.SWIPE_RIGHT -> {
                // Swipe Right: Finger moves Left to Right
                listOf(width * 0.15f, height * 0.5f, width * 0.85f, height * 0.5f)
            }
            GestureAction.SCROLL_DOWN -> {
                // Scroll Down: Finger moves Bottom to Top
                listOf(width * 0.5f, height * 0.75f, width * 0.5f, height * 0.25f)
            }
            GestureAction.SCROLL_UP -> {
                // Scroll Up: Finger moves Top to Bottom
                listOf(width * 0.5f, height * 0.25f, width * 0.5f, height * 0.75f)
            }
            else -> return false
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
                    Log.i(TAG, "Gesture completed: ${action.displayName}")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.w(TAG, "Gesture cancelled: ${action.displayName}")
                }
            },
            null
        )

        Log.i(TAG, "Dispatched gesture: ${action.displayName} (success=$dispatched)")
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

            return service.performAction(action)
        }
    }
}
