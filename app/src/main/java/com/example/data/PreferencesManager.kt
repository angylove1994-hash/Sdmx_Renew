package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "sdmx_prefs")

class PreferencesManager(private val context: Context) {
    companion object {
        val KEY_USER = stringPreferencesKey("user_sdmx")
        val KEY_PASS = stringPreferencesKey("pass_sdmx")
        val KEY_INTERVAL = stringPreferencesKey("interval_hours")
    }

    val userSdmx: Flow<String> = context.dataStore.data.map { it[KEY_USER] ?: "" }
    val passSdmx: Flow<String> = context.dataStore.data.map { it[KEY_PASS] ?: "" }
    val intervalHours: Flow<String> = context.dataStore.data.map { it[KEY_INTERVAL] ?: "24" }

    suspend fun saveCredentials(user: String, pass: String) {
        context.dataStore.edit {
            it[KEY_USER] = user
            it[KEY_PASS] = pass
        }
    }

    suspend fun saveInterval(hours: String) {
        context.dataStore.edit {
            it[KEY_INTERVAL] = hours
        }
    }
}
