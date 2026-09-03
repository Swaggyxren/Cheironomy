package com.ydnar.cheironomy.data

import android.content.Context
import android.content.SharedPreferences
import com.ydnar.cheironomy.data.template.GestureTemplate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages persistence and reactive updates for AppSettings and custom GestureTemplates.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        val templatesJson = prefs.getString(KEY_CUSTOM_TEMPLATES, null)
        val templates = GestureTemplate.listFromJson(templatesJson)

        return AppSettings(
            confidenceThreshold = prefs.getFloat(KEY_CONFIDENCE, 0.65f),
            holdDurationMs = prefs.getLong(KEY_HOLD_DURATION_MS, 700L),
            isOverlayEnabled = prefs.getBoolean(KEY_OVERLAY_ENABLED, true),
            staticRejectCeiling = prefs.getFloat(KEY_STATIC_REJECT_CEILING, 0.11f),
            staticMarginThreshold = prefs.getFloat(KEY_STATIC_MARGIN_THRESHOLD, 0.15f),
            motionRejectCeiling = prefs.getFloat(KEY_MOTION_REJECT_CEILING, 0.22f),
            motionMarginThreshold = prefs.getFloat(KEY_MOTION_MARGIN_THRESHOLD, 0.15f),
            motionPrefilterTolerance = prefs.getFloat(KEY_MOTION_PREFILTER_TOLERANCE, 0.40f),
            customTemplates = templates
        )
    }

    fun updateConfidenceThreshold(value: Float) {
        prefs.edit().putFloat(KEY_CONFIDENCE, value).apply()
        _settings.value = _settings.value.copy(confidenceThreshold = value)
    }

    fun updateHoldDurationMs(value: Long) {
        prefs.edit().putLong(KEY_HOLD_DURATION_MS, value).apply()
        _settings.value = _settings.value.copy(holdDurationMs = value)
    }

    fun updateOverlayEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_OVERLAY_ENABLED, value).apply()
        _settings.value = _settings.value.copy(isOverlayEnabled = value)
    }

    fun updateStaticRejectCeiling(value: Float) {
        prefs.edit().putFloat(KEY_STATIC_REJECT_CEILING, value).apply()
        _settings.value = _settings.value.copy(staticRejectCeiling = value)
    }

    fun updateStaticMarginThreshold(value: Float) {
        prefs.edit().putFloat(KEY_STATIC_MARGIN_THRESHOLD, value).apply()
        _settings.value = _settings.value.copy(staticMarginThreshold = value)
    }

    fun updateMotionRejectCeiling(value: Float) {
        prefs.edit().putFloat(KEY_MOTION_REJECT_CEILING, value).apply()
        _settings.value = _settings.value.copy(motionRejectCeiling = value)
    }

    fun updateMotionMarginThreshold(value: Float) {
        prefs.edit().putFloat(KEY_MOTION_MARGIN_THRESHOLD, value).apply()
        _settings.value = _settings.value.copy(motionMarginThreshold = value)
    }

    fun updateMotionPrefilterTolerance(value: Float) {
        prefs.edit().putFloat(KEY_MOTION_PREFILTER_TOLERANCE, value).apply()
        _settings.value = _settings.value.copy(motionPrefilterTolerance = value)
    }

    fun addCustomTemplate(template: GestureTemplate) {
        val currentList = _settings.value.customTemplates.toMutableList()
        currentList.removeAll { it.id == template.id }
        currentList.add(template)
        saveTemplates(currentList)
    }

    fun removeCustomTemplate(templateId: String) {
        val currentList = _settings.value.customTemplates.toMutableList()
        currentList.removeAll { it.id == templateId }
        saveTemplates(currentList)
    }

    private fun saveTemplates(templates: List<GestureTemplate>) {
        val jsonStr = GestureTemplate.listToJson(templates)
        prefs.edit().putString(KEY_CUSTOM_TEMPLATES, jsonStr).apply()
        _settings.value = _settings.value.copy(customTemplates = templates)
    }

    companion object {
        private const val PREFS_NAME = "cheironomy_settings_prefs"

        const val KEY_CONFIDENCE = "key_confidence"
        const val KEY_HOLD_DURATION_MS = "key_hold_duration_ms"
        const val KEY_OVERLAY_ENABLED = "key_overlay_enabled"
        const val KEY_STATIC_REJECT_CEILING = "key_static_reject_ceiling"
        const val KEY_STATIC_MARGIN_THRESHOLD = "key_static_margin_threshold"
        const val KEY_MOTION_REJECT_CEILING = "key_motion_reject_ceiling"
        const val KEY_MOTION_MARGIN_THRESHOLD = "key_motion_margin_threshold"
        const val KEY_MOTION_PREFILTER_TOLERANCE = "key_motion_prefilter_tolerance"
        const val KEY_CUSTOM_TEMPLATES = "key_custom_templates"

        @Volatile
        private var instance: SettingsRepository? = null

        fun getInstance(context: Context): SettingsRepository {
            return instance ?: synchronized(this) {
                instance ?: SettingsRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
