package com.aicontrol.launcher.actions

import android.content.Context
import com.aicontrol.launcher.assets.LauncherAssetManager
import org.json.JSONArray
import org.json.JSONObject

class ActionEngine(context: Context) {
    private val prefs = context.getSharedPreferences("launcher_state", Context.MODE_PRIVATE)
    private val assets = LauncherAssetManager(context)

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
                "CREATE_WORKSPACE" -> {
                    val name = a.optString("name").trim()
                    require(name.isNotEmpty()) { "Workspace name is required" }
                    prefs.edit().putString("workspace:" + name, a.optJSONArray("packages")?.toString() ?: "[]").apply()
                    applied++
                }
                "DOWNLOAD_WALLPAPER" -> {
                    val url = a.optString("url")
                    require(url.startsWith("https://"))
                    val file = assets.downloadWallpaper(url).getOrThrow()
                    if (a.optBoolean("apply", true)) assets.applyWallpaper(file).getOrThrow()
                    applied++
                }
                "DOWNLOAD_IMAGE" -> {
                    val url = a.optString("url")
                    val name = a.optString("name").ifBlank { "downloaded-image" }
                    assets.downloadImage(url, name).getOrThrow()
                    applied++
                }
                "DOWNLOAD_ICON" -> {
                    val url = a.optString("url")
                    val pkg = a.optString("package")
                    require(pkg.isNotBlank())
                    assets.downloadIcon(url, pkg).getOrThrow()
                    applied++
                }
                "CLEAR_ICON_OVERRIDE" -> {
                    assets.clearIconOverride(a.optString("package"))
                    applied++
                }
            }
        }
        applied
    }

    private fun setPackages(key: String, arr: JSONArray?) {
        val current = prefs.getStringSet(key, emptySet())!!.toMutableSet()
        if (arr != null) for (i in 0 until arr.length()) current.add(arr.getString(i))
        prefs.edit().putStringSet(key, current).apply()
    }

    private fun removePackages(key: String, arr: JSONArray?) {
        val current = prefs.getStringSet(key, emptySet())!!.toMutableSet()
        if (arr != null) for (i in 0 until arr.length()) current.remove(arr.getString(i))
        prefs.edit().putStringSet(key, current).apply()
    }

    fun hiddenPackages(): Set<String> = prefs.getStringSet("hidden", emptySet()) ?: emptySet()
    fun theme(): String = prefs.getString("theme", "default") ?: "default"
    fun layout(): String = prefs.getString("layout", "grid") ?: "grid"
}
