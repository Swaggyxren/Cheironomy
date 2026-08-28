package com.ydnar.cheironomy.media

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.ydnar.cheironomy.data.GestureAction

/**
 * Dispatches standard Android media key events via AudioManager.
 */
class MediaActionDispatcher(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    /**
     * Dispatches the corresponding media key for the given GestureAction.
     */
    fun dispatchAction(action: GestureAction): Boolean {
        return when (action) {
            GestureAction.MEDIA_PLAY_PAUSE -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            GestureAction.MEDIA_NEXT -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            GestureAction.MEDIA_PREVIOUS -> dispatchMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            else -> false
        }
    }

    fun dispatchMediaKey(keyCode: Int): Boolean {
        if (audioManager == null) {
            Log.w(TAG, "AudioManager not available, cannot dispatch media key.")
            return false
        }

        val eventTime = SystemClock.uptimeMillis()

        // 1. Dispatch ACTION_DOWN
        val downEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_DOWN, keyCode, 0)
        audioManager.dispatchMediaKeyEvent(downEvent)

        // 2. Dispatch ACTION_UP
        val upEvent = KeyEvent(eventTime, eventTime, KeyEvent.ACTION_UP, keyCode, 0)
        audioManager.dispatchMediaKeyEvent(upEvent)

        Log.i(TAG, "Successfully dispatched MediaKeyEvent: keyCode=$keyCode")
        return true
    }

    companion object {
        private const val TAG = "MediaActionDispatcher"
    }
}
