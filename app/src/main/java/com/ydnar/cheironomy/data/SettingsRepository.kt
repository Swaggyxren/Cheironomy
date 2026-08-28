package com.ydnar.cheironomy.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages persistence and reactive updates for AppSettings.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        return AppSettings(
            confidenceThreshold = prefs.getFloat(KEY_CONFIDENCE, 0.5f),
            cooldownMs = prefs.getLong(KEY_COOLDOWN_MS, 1200L),
            holdDurationMs = prefs.getLong(KEY_HOLD_DURATION_MS, 500L),
            swipeSensitivity = prefs.getFloat(KEY_SWIPE_SENSITIVITY, 0.22f),
            isOverlayEnabled = prefs.getBoolean(KEY_OVERLAY_ENABLED, true),
            openPalmAction = getAction(KEY_ACTION_OPEN_PALM, GestureAction.MEDIA_PLAY_PAUSE),
            swipeLeftAction = getAction(KEY_ACTION_SWIPE_LEFT, GestureAction.SWIPE_LEFT),
            swipeRightAction = getAction(KEY_ACTION_SWIPE_RIGHT, GestureAction.SWIPE_RIGHT),
            swipeUpAction = getAction(KEY_ACTION_SWIPE_UP, GestureAction.SCROLL_DOWN),
            swipeDownAction = getAction(KEY_ACTION_SWIPE_DOWN, GestureAction.SCROLL_UP)
        )
    }

    private fun getAction(key: String, default: GestureAction): GestureAction {
        val name = prefs.getString(key, null) ?: return default
        return try {
            GestureAction.valueOf(name)
        } catch (e: Exception) {
            default
        }
    }

    fun updateConfidenceThreshold(value: Float) {
        prefs.edit().putFloat(KEY_CONFIDENCE, value).apply()
        _settings.value = _settings.value.copy(confidenceThreshold = value)
    }

    fun updateCooldownMs(value: Long) {
        prefs.edit().putLong(KEY_COOLDOWN_MS, value).apply()
        _settings.value = _settings.value.copy(cooldownMs = value)
    }

    fun updateHoldDurationMs(value: Long) {
        prefs.edit().putLong(KEY_HOLD_DURATION_MS, value).apply()
        _settings.value = _settings.value.copy(holdDurationMs = value)
    }

    fun updateSwipeSensitivity(value: Float) {
        prefs.edit().putFloat(KEY_SWIPE_SENSITIVITY, value).apply()
        _settings.value = _settings.value.copy(swipeSensitivity = value)
    }

    fun updateOverlayEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, value).apply()
        _settings.value = _settings.value.copy(isOverlayEnabled = value)
    }

    fun updateAction(key: String, action: GestureAction) {
        prefs.edit().putString(key, action.name).apply()
        _settings.value = when (key) {
            KEY_ACTION_OPEN_PALM -> _settings.value.copy(openPalmAction = action)
            KEY_ACTION_SWIPE_LEFT -> _settings.value.copy(swipeLeftAction = action)
            KEY_ACTION_SWIPE_RIGHT -> _settings.value.copy(swipeRightAction = action)
            KEY_ACTION_SWIPE_UP -> _settings.value.copy(swipeUpAction = action)
            KEY_ACTION_SWIPE_DOWN -> _settings.value.copy(swipeDownAction = action)
            else -> _settings.value
        }
    }

    companion object {
        private const val PREFS_NAME = "cheironomy_settings_prefs"

        const val KEY_CONFIDENCE = "key_confidence"
        const val KEY_COOLDOWN_MS = "key_cooldown_ms"
        const val KEY_HOLD_DURATION_MS = "key_hold_duration_ms"
        const val KEY_SWIPE_SENSITIVITY = "key_swipe_sensitivity"
        const val KEY_OVERLAY_ENABLED = "key_overlay_enabled"

        const val KEY_ACTION_OPEN_PALM = "key_action_open_palm"
        const val KEY_ACTION_SWIPE_LEFT = "key_action_swipe_left"
        const val KEY_ACTION_SWIPE_RIGHT = "key_action_swipe_right"
        const val KEY_ACTION_SWIPE_UP = "key_action_swipe_up"
        const val KEY_ACTION_SWIPE_DOWN = "key_action_swipe_down"

        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
