package com.ai.rankboard.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val updateReminders: Boolean = true,
)

class SettingsStore(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _settings.value = _settings.value.copy(dynamicColor = enabled)
    }

    fun setUpdateReminders(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_UPDATE_REMINDERS, enabled).apply()
        _settings.value = _settings.value.copy(updateReminders = enabled)
    }

    private fun read(): AppSettings {
        val themeName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return AppSettings(
            themeMode = ThemeMode.entries.firstOrNull { it.name == themeName } ?: ThemeMode.SYSTEM,
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, false),
            updateReminders = prefs.getBoolean(KEY_UPDATE_REMINDERS, true),
        )
    }

    companion object {
        private const val PREFS_NAME = "rankboard_settings"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_UPDATE_REMINDERS = "update_reminders"
    }
}
