package com.lukas.bluetoothswitch.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AUTO_CONNECT_MACS = stringSetPreferencesKey("auto_connect_macs")
    }
}
