package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
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
        val KEY_AGGRESSIVE_MODE = booleanPreferencesKey("aggressive_mode_enabled")
        val KEY_LAST_EXECUTION = longPreferencesKey("last_execution_time")
        val KEY_NEXT_EXECUTION = longPreferencesKey("next_execution_time")
        val KEY_NTFY_TOPIC = stringPreferencesKey("ntfy_topic")
        
        // Fast SharedPreferences fallback for immediate synchronous access in receivers/services
        private const val SYNC_PREFS_NAME = "sdmx_sync_prefs"
        private const val SYNC_KEY_USER = "user_sdmx"
        private const val SYNC_KEY_PASS = "pass_sdmx"
        private const val SYNC_KEY_INTERVAL = "interval_hours"
        private const val SYNC_KEY_AGGRESSIVE = "aggressive_mode"
        private const val SYNC_KEY_LAST_RUN = "last_run"
        private const val SYNC_KEY_NEXT_RUN = "next_run"
        private const val SYNC_KEY_NTFY_TOPIC = "ntfy_topic"
        const val DEFAULT_NTFY_TOPIC = "Gato_Negro_Reportes"

        fun getNtfyTopic(context: Context): String {
            val sp = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            return sp.getString(SYNC_KEY_NTFY_TOPIC, DEFAULT_NTFY_TOPIC) ?: DEFAULT_NTFY_TOPIC
        }

        fun setSyncNtfyTopic(context: Context, topic: String) {
            val sp = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            sp.edit().putString(SYNC_KEY_NTFY_TOPIC, topic.ifEmpty { DEFAULT_NTFY_TOPIC }).apply()
        }

        fun getSyncCredentials(context: Context): Pair<String, String> {
            val sp = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            val user = sp.getString(SYNC_KEY_USER, "") ?: ""
            val pass = sp.getString(SYNC_KEY_PASS, "") ?: ""
            return Pair(user, pass)
        }

        fun getSyncIntervalHours(context: Context): Int {
            val sp = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            return sp.getInt(SYNC_KEY_INTERVAL, 24)
        }

        fun isSyncAggressiveMode(context: Context): Boolean {
            val sp = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            return sp.getBoolean(SYNC_KEY_AGGRESSIVE, true)
        }

        fun setSyncExecutionTimes(context: Context, lastRun: Long, nextRun: Long) {
            val sp = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            sp.edit().putLong(SYNC_KEY_LAST_RUN, lastRun).putLong(SYNC_KEY_NEXT_RUN, nextRun).apply()
        }

        fun getSyncNextRunTime(context: Context): Long {
            val sp = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            return sp.getLong(SYNC_KEY_NEXT_RUN, 0L)
        }

        fun getSyncLastRunTime(context: Context): Long {
            val sp = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
            return sp.getLong(SYNC_KEY_LAST_RUN, 0L)
        }
    }

    val userSdmx: Flow<String> = context.dataStore.data.map { it[KEY_USER] ?: "" }
    val passSdmx: Flow<String> = context.dataStore.data.map { it[KEY_PASS] ?: "" }
    val intervalHours: Flow<String> = context.dataStore.data.map { it[KEY_INTERVAL] ?: "24" }
    val isAggressiveMode: Flow<Boolean> = context.dataStore.data.map { it[KEY_AGGRESSIVE_MODE] ?: true }
    val lastExecutionTime: Flow<Long> = context.dataStore.data.map { it[KEY_LAST_EXECUTION] ?: 0L }
    val nextExecutionTime: Flow<Long> = context.dataStore.data.map { it[KEY_NEXT_EXECUTION] ?: 0L }
    val ntfyTopic: Flow<String> = context.dataStore.data.map { it[KEY_NTFY_TOPIC] ?: DEFAULT_NTFY_TOPIC }

    suspend fun saveNtfyTopic(topic: String) {
        val cleanTopic = topic.trim().ifEmpty { DEFAULT_NTFY_TOPIC }
        context.dataStore.edit {
            it[KEY_NTFY_TOPIC] = cleanTopic
        }
        setSyncNtfyTopic(context, cleanTopic)
    }

    suspend fun saveCredentials(user: String, pass: String) {
        context.dataStore.edit {
            it[KEY_USER] = user
            it[KEY_PASS] = pass
        }
        val sp = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putString(SYNC_KEY_USER, user).putString(SYNC_KEY_PASS, pass).apply()
    }

    suspend fun saveInterval(hours: String) {
        context.dataStore.edit {
            it[KEY_INTERVAL] = hours
        }
        val sp = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        val h = hours.toIntOrNull() ?: 24
        sp.edit().putInt(SYNC_KEY_INTERVAL, if (h < 1) 1 else h).apply()
    }

    suspend fun setAggressiveMode(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_AGGRESSIVE_MODE] = enabled
        }
        val sp = context.getSharedPreferences(SYNC_PREFS_NAME, Context.MODE_PRIVATE)
        sp.edit().putBoolean(SYNC_KEY_AGGRESSIVE, enabled).apply()
    }

    suspend fun recordExecutionTimes(lastRun: Long, nextRun: Long) {
        context.dataStore.edit {
            it[KEY_LAST_EXECUTION] = lastRun
            it[KEY_NEXT_EXECUTION] = nextRun
        }
        setSyncExecutionTimes(context, lastRun, nextRun)
    }
}
