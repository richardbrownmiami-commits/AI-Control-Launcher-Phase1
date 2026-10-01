package com.aicontrol.launcher.data

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ai_launcher", Context.MODE_PRIVATE)
    var provider: String
        get() = prefs.getString("provider", PROVIDER_OPENROUTER) ?: PROVIDER_OPENROUTER
        set(value) = prefs.edit().putString("provider", value).apply()
    var model: String
        get() = prefs.getString("model", defaultModel(provider)) ?: defaultModel(provider)
        set(value) = prefs.edit().putString("model", value).apply()
    var openRouterKey: String
        get() = prefs.getString("openrouter_key", "") ?: ""
        set(value) = prefs.edit().putString("openrouter_key", value).apply()
    var geminiKey: String
        get() = prefs.getString("gemini_key", "") ?: ""
        set(value) = prefs.edit().putString("gemini_key", value).apply()
    fun activeKey(): String = if (provider == PROVIDER_GEMINI) geminiKey else openRouterKey
    companion object {
        const val PROVIDER_OPENROUTER = "openrouter"
        const val PROVIDER_GEMINI = "gemini"
        fun defaultModel(provider: String): String = if (provider == PROVIDER_GEMINI) "gemini-2.0-flash" else "openrouter/free"
    }
}