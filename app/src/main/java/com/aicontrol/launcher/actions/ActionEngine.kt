package com.aicontrol.launcher.actions

import android.content.Context
import com.aicontrol.launcher.data.SettingsStore
import org.json.JSONArray
import org.json.JSONObject

class ActionEngine(context: Context) {
    private val prefs = context.getSharedPreferences("launcher_state", Context.MODE_PRIVATE)
    fun applyJson(json: String): Result<Int> = runCatching {
        val actions = JSONObject(json).optJSONArray("actions") ?: JSONArray()
        var applied = 0
        for (i in 0 until actions.length()) {
            val a = actions.getJSONObject(i)
            when (a.optString("action")) {
                "HIDE_APPS" -> { setPackages("hidden", a.optJSONArray("packages")); applied++ }
                "SHOW_APPS" -> { removePackages("hidden", a.optJSONArray("packages")); applied++ }
                "SET_THEME" -> { prefs.edit().putString("theme", a.optString("theme", "default")).apply(); applied++ }
                "SET_LAYOUT" -> { prefs.edit().putString("layout", a.optString("layout", "grid")).apply(); applied++ }
                "CREATE_WORKSPACE" -> { prefs.edit().putString("workspace:" + a.optString("name"), a.optJSONArray("packages")?.toString() ?: "[]").apply(); applied++ }
            }
        }
        applied
    }
    private fun setPackages(key: String, arr: JSONArray?) { val current = prefs.getStringSet(key, emptySet())!!.toMutableSet(); if (arr != null) for (i in 0 until arr.length()) current.add(arr.getString(i)); prefs.edit().putStringSet(key, current).apply() }
    private fun removePackages(key: String, arr: JSONArray?) { val current = prefs.getStringSet(key, emptySet())!!.toMutableSet(); if (arr != null) for (i in 0 until arr.length()) current.remove(arr.getString(i)); prefs.edit().putStringSet(key, current).apply() }
    fun hiddenPackages(): Set<String> = prefs.getStringSet("hidden", emptySet()) ?: emptySet()
    fun theme(): String = prefs.getString("theme", "default") ?: "default"
    fun layout(): String = prefs.getString("layout", "grid") ?: "grid"
}
