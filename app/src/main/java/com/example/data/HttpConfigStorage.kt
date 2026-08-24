package com.example.data

import android.content.Context
import com.example.network.HttpConfig

object HttpConfigStorage {
    private const val PREF_NAME = "sdmx_http_prefs"
    private const val KEY_CONFIG = "http_config_json"

    fun getConfig(context: Context?): HttpConfig {
        if (context == null) return HttpConfig()
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CONFIG, null)
        return HttpConfig.fromJson(json)
    }

    fun saveConfig(context: Context, config: HttpConfig) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CONFIG, config.toJson()).apply()
    }

    fun resetDefaults(context: Context): HttpConfig {
        val defaultConfig = HttpConfig()
        saveConfig(context, defaultConfig)
        return defaultConfig
    }
}
