package com.aicontrol.launcher.data

import android.content.Context

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("ai_launcher", Context.MODE_PRIVATE)
    var provider: String get() = prefs.getString("provider", "OpenAI") ?: "OpenAI"; set(v) { prefs.edit().putString("provider", v).apply() }
    var endpoint: String get() = prefs.getString("endpoint", "https://api.openai.com/v1/chat/completions") ?: ""; set(v) { prefs.edit().putString("endpoint", v).apply() }
    var apiKey: String get() = prefs.getString("api_key", "") ?: ""; set(v) { prefs.edit().putString("api_key", v).apply() }
    var model: String get() = prefs.getString("model", "gpt-4o-mini") ?: "gpt-4o-mini"; set(v) { prefs.edit().putString("model", v).apply() }
}
