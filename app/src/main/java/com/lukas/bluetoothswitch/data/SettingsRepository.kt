package com.lukas.bluetoothswitch.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map { preferences ->
            AppSettings(
                themeMode = ThemeMode.fromStorage(preferences[Keys.THEME_MODE]),
                dynamicColor = preferences[Keys.DYNAMIC_COLOR] ?: true,
                autoConnectMacs = preferences[Keys.AUTO_CONNECT_MACS].orEmpty(),
                autoRefreshMode = AutoRefreshMode.fromStorage(preferences[Keys.AUTO_REFRESH_MODE]),
                autoRefreshSeconds = (preferences[Keys.AUTO_REFRESH_SECONDS]
                    ?: AppSettings.DEFAULT_AUTO_REFRESH_SECONDS)
                    .coerceIn(
                        AppSettings.MIN_AUTO_REFRESH_SECONDS,
                        AppSettings.MAX_AUTO_REFRESH_SECONDS,
                    ),
                delayMemory = preferences[Keys.DELAY_MEMORY].orEmpty()
                    .mapNotNull(::parseDelayMemory)
                    .toMap(),
            )
        }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[Keys.THEME_MODE] = themeMode.storageValue
        }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[Keys.DYNAMIC_COLOR] = enabled
        }
    }

    suspend fun setAutoConnect(address: String, enabled: Boolean) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.AUTO_CONNECT_MACS].orEmpty()
            val updated = if (enabled) current + address else current - address
            if (updated.isEmpty()) {
                preferences.remove(Keys.AUTO_CONNECT_MACS)
            } else {
                preferences[Keys.AUTO_CONNECT_MACS] = updated
            }
        }
    }

    suspend fun setAutoRefreshMode(mode: AutoRefreshMode) {
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_REFRESH_MODE] = mode.storageValue
        }
    }

    suspend fun setAutoRefreshSeconds(seconds: Int) {
        val safe = seconds.coerceIn(
            AppSettings.MIN_AUTO_REFRESH_SECONDS,
            AppSettings.MAX_AUTO_REFRESH_SECONDS,
        )
        dataStore.edit { preferences ->
            preferences[Keys.AUTO_REFRESH_SECONDS] = safe
        }
    }

    suspend fun setDelayMemory(address: String, channel: Int, seconds: Int?) {
        dataStore.edit { preferences ->
            val current = preferences[Keys.DELAY_MEMORY].orEmpty()
            val entries = current
                .filterNot { entry -> entry.startsWith("$address|$channel|") }
                .toMutableSet()
            if (seconds != null && seconds in 0..Protocol.MAX_DELAY_SECONDS) {
                entries += "$address|$channel|$seconds"
            }
            if (entries.isEmpty()) {
                preferences.remove(Keys.DELAY_MEMORY)
            } else {
                preferences[Keys.DELAY_MEMORY] = entries
            }
        }
    }

    suspend fun clearDelayMemories() {
        dataStore.edit { preferences ->
            preferences.remove(Keys.DELAY_MEMORY)
        }
    }

    private fun parseDelayMemory(value: String): Pair<String, Int>? {
        val parts = value.split('|')
        if (parts.size != 3) return null
        val channel = parts[1].toIntOrNull() ?: return null
        val seconds = parts[2].toIntOrNull() ?: return null
        if (channel !in 0..255) return null
        if (seconds !in 0..Protocol.MAX_DELAY_SECONDS) return null
        return DelayMemoryEntry.key(parts[0], channel) to seconds
    }

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AUTO_CONNECT_MACS = stringSetPreferencesKey("auto_connect_macs")
        val AUTO_REFRESH_MODE = stringPreferencesKey("auto_refresh_mode")
        val AUTO_REFRESH_SECONDS = intPreferencesKey("auto_refresh_seconds")
        val DELAY_MEMORY = stringSetPreferencesKey("delay_memory")
    }
}
