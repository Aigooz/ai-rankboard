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
    val snapshotUrl: String = "",
    val appUpdateUrl: String = "",
    val relayUrl: String = "",
    val relayApiKey: String = "",
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

    fun setSnapshotUrl(url: String) {
        val normalized = url.trim()
        prefs.edit().putString(KEY_SNAPSHOT_URL, normalized).apply()
        _settings.value = _settings.value.copy(snapshotUrl = normalized)
    }

    fun setAppUpdateUrl(url: String) {
        val normalized = url.trim()
        prefs.edit().putString(KEY_APP_UPDATE_URL, normalized).apply()
        _settings.value = _settings.value.copy(appUpdateUrl = normalized)
    }

    fun setRelay(url: String, apiKey: String) {
        val normalizedUrl = url.trim()
        val normalizedKey = apiKey.trim()
        prefs.edit()
            .putString(KEY_RELAY_URL, normalizedUrl)
            .putString(KEY_RELAY_API_KEY, normalizedKey)
            .apply()
        _settings.value = _settings.value.copy(relayUrl = normalizedUrl, relayApiKey = normalizedKey)
    }

    private fun read(): AppSettings {
        val themeName = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return AppSettings(
            themeMode = ThemeMode.entries.firstOrNull { it.name == themeName } ?: ThemeMode.SYSTEM,
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, false),
            updateReminders = prefs.getBoolean(KEY_UPDATE_REMINDERS, true),
            snapshotUrl = prefs.getString(KEY_SNAPSHOT_URL, "").orEmpty().trim(),
            appUpdateUrl = prefs.getString(KEY_APP_UPDATE_URL, "").orEmpty().trim(),
            relayUrl = prefs.getString(KEY_RELAY_URL, "").orEmpty().trim(),
            relayApiKey = prefs.getString(KEY_RELAY_API_KEY, "").orEmpty().trim(),
        )
    }

    companion object {
        private const val PREFS_NAME = "rankboard_settings"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_UPDATE_REMINDERS = "update_reminders"
        private const val KEY_SNAPSHOT_URL = "snapshot_url"
        private const val KEY_APP_UPDATE_URL = "app_update_url"
        private const val KEY_RELAY_URL = "relay_url"
        private const val KEY_RELAY_API_KEY = "relay_api_key"
    }
}
