package com.homepilot.app.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.homepilot.app.model.HomeButton
import com.homepilot.app.model.ServerConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "homepilot_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        private val KEY_HOST = stringPreferencesKey("server_host")
        private val KEY_PORT = intPreferencesKey("server_port")
        private val KEY_TOKEN = stringPreferencesKey("access_token")
        private val KEY_USE_TLS = booleanPreferencesKey("use_tls")
        private val KEY_HOME_BUTTONS = stringPreferencesKey("home_buttons")
    }

    private val gson = Gson()

    val serverConfigFlow: Flow<ServerConfig> = context.dataStore.data.map { preferences ->
        ServerConfig(
            host = preferences[KEY_HOST] ?: "",
            port = preferences[KEY_PORT] ?: 8123,
            accessToken = preferences[KEY_TOKEN] ?: "",
            useTls = preferences[KEY_USE_TLS] ?: false
        )
    }

    val homeButtonsFlow: Flow<List<HomeButton>> = context.dataStore.data.map { preferences ->
        val json = preferences[KEY_HOME_BUTTONS] ?: "[]"
        val type = object : TypeToken<List<HomeButton>>() {}.type
        try {
            gson.fromJson<List<HomeButton>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun saveServerConfig(config: ServerConfig) {
        context.dataStore.edit { preferences ->
            preferences[KEY_HOST] = config.host
            preferences[KEY_PORT] = config.port
            preferences[KEY_TOKEN] = config.accessToken
            preferences[KEY_USE_TLS] = config.useTls
        }
    }

    suspend fun saveHomeButtons(buttons: List<HomeButton>) {
        context.dataStore.edit { preferences ->
            preferences[KEY_HOME_BUTTONS] = gson.toJson(buttons)
        }
    }

    suspend fun clearServerConfig() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
