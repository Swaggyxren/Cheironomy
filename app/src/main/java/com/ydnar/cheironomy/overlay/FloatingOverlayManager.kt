package com.ydnar.cheironomy.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.ydnar.cheironomy.R
import com.ydnar.cheironomy.data.GestureAction
import com.ydnar.cheironomy.gesture.engine.GestureEngineStatus

/**
 * Manages the floating system overlay pill to display real-time gesture feedback across all apps.
 */
class FloatingOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: View? = null
    private var statusIcon: ImageView? = null
    private var statusText: TextView? = null
    private var containerLayout: LinearLayout? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isVisible = false

    @SuppressLint("ClickableViewAccessibility")
    fun show() {
        if (isVisible) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW permission not granted.")
            return
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = 200
        }

        // Build Programmatic Pill View
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(28, 18, 28, 18)

            val shape = GradientDrawable().apply {
                setColor(Color.parseColor("#E61E2228"))
                cornerRadius = 60f
                setStroke(3, Color.parseColor("#4000E5FF"))
            }
            background = shape
        }

        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_launcher_foreground)
            layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                marginEnd = 14
            }
            setColorFilter(Color.parseColor("#00E5FF"))
        }

        val text = TextView(context).apply {
            this.text = "Cheironomy"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        container.addView(icon)
        container.addView(text)

        // Drag-to-move listener
        container.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager?.updateViewLayout(view, params)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating overlay layout: ${e.message}")
                    }
                    true
                }
                else -> false
            }
        }

        try {
            windowManager?.addView(container, params)
            overlayView = container
            containerLayout = container
            statusIcon = icon
            statusText = text
            isVisible = true
            Log.i(TAG, "Floating overlay displayed.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating overlay: ${e.message}", e)
        }
    }

    fun updateStatus(status: GestureEngineStatus) {
        if (!isVisible) return
        mainHandler.post {
            when (status) {
                GestureEngineStatus.SCANNING, GestureEngineStatus.IDLE -> {
                    statusText?.text = "Scanning"
                    statusIcon?.setColorFilter(Color.parseColor("#00E5FF"))
                }
                GestureEngineStatus.HAND_DETECTED -> {
                    statusText?.text = "Hand Ready"
                    statusIcon?.setColorFilter(Color.parseColor("#00E676"))
                }
                GestureEngineStatus.COOLDOWN -> {
                    statusText?.text = "Cooldown"
                    statusIcon?.setColorFilter(Color.parseColor("#FFAB00"))
                }
                GestureEngineStatus.ACTION_TRIGGERED -> {
                    statusText?.text = "Action!"
                    statusIcon?.setColorFilter(Color.parseColor("#00E676"))
                }
            }
        }
    }

    fun showActionTriggered(action: GestureAction) {
        if (!isVisible) return
        mainHandler.post {
            statusText?.text = action.displayName.replace("Media: ", "").replace("Touch: ", "")
            statusIcon?.setColorFilter(Color.parseColor("#00E676"))

            // Reset label after 1.2s
            mainHandler.postDelayed({
                if (isVisible) {
                    statusText?.text = "Scanning"
                    statusIcon?.setColorFilter(Color.parseColor("#00E5FF"))
                }
            }, 1200L)
        }
    }

    fun hide() {
        if (!isVisible) return
        try {
            overlayView?.let { windowManager?.removeView(it) }
            overlayView = null
            isVisible = false
            Log.i(TAG, "Floating overlay hidden.")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing floating overlay: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "FloatingOverlayManager"
    }
}
