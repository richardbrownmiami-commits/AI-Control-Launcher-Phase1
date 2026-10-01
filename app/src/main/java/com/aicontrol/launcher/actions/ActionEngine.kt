package com.aicontrol.launcher.actions

import android.content.Context
import android.graphics.Color
import com.aicontrol.launcher.assets.AssetStore
import com.aicontrol.launcher.assets.LauncherAssetManager
import org.json.JSONArray
import org.json.JSONObject

class ActionEngine(context: Context) {
    private val prefs = context.getSharedPreferences("launcher_state", Context.MODE_PRIVATE)
    private val assets = LauncherAssetManager(context)
    private val store = AssetStore(context)

    fun applyJson(json: String): Result<Int> = runCatching {
        val actions = JSONObject(json).optJSONArray("actions") ?: JSONArray()
        var applied = 0
        for (i in 0 until actions.length()) {
            val a = actions.getJSONObject(i)
            when (a.optString("action")) {
                "HIDE_APPS" -> { setPackages("hidden", a.optJSONArray("packages")); applied++ }
                "SHOW_APPS" -> { removePackages("hidden", a.optJSONArray("packages")); applied++ }
                "SET_THEME" -> { prefs.edit().putString("theme", a.optString("theme", "default")).apply(); applied++ }
                "CREATE_THEME" -> {
                    val name = a.optString("name").trim()
                    require(name.isNotEmpty())
                    store.themeFile(name).writeText(JSONObject().put("name", name).put("bg", a.optString("bg", "#101218")).put("accent", a.optString("accent", "#66B3FF")).toString())
                    prefs.edit().putString("theme", name).apply()
                    applied++
                }
                "SET_LAYOUT" -> { prefs.edit().putString("layout", a.optString("layout", "grid")).apply(); applied++ }
                "CREATE_WORKSPACE" -> {
                    val name = a.optString("name").trim()
                    require(name.isNotEmpty())
                    prefs.edit().putString("workspace:" + name, a.optJSONArray("packages")?.toString() ?: "[]").apply()
                    applied++
                }
                "DOWNLOAD_WALLPAPER" -> {
                    val url = a.optString("url")
                    require(url.startsWith("https://"))
                    val file = assets.downloadWallpaper(url, a.optString("name", "current")).getOrThrow()
                    if (a.optBoolean("apply", true)) assets.applyWallpaper(file).getOrThrow()
                    applied++
                }
                "DOWNLOAD_IMAGE" -> {
                    val url = a.optString("url")
                    require(url.startsWith("https://"))
                    assets.downloadImage(url, a.optString("name", "downloaded-image")).getOrThrow()
                    applied++
                }
                "DOWNLOAD_ICON" -> {
                    val url = a.optString("url")
                    require(url.startsWith("https://"))
                    assets.downloadIcon(url, a.optString("package")).getOrThrow()
                    applied++
                }
                "CREATE_ICON_PACK" -> {
                    val name = a.optString("name").trim()
                    val items = mutableListOf<Pair<String, String>>()
                    val icons = a.optJSONArray("icons") ?: JSONArray()
                    for (j in 0 until icons.length()) {
                        val item = icons.getJSONObject(j)
                        items += item.optString("package") to item.optString("url")
                    }
                    assets.createIconPack(name, items).getOrThrow()
                    assets.applyIconPack(name).getOrThrow()
                    prefs.edit().putString("icon_pack", name).apply()
                    applied++
                }
                "APPLY_ICON_PACK" -> {
                    val name = a.optString("name").trim()
                    assets.applyIconPack(name).getOrThrow()
                    prefs.edit().putString("icon_pack", name).apply()
                    applied++
                }
                "CLEAR_ICON_OVERRIDE" -> { assets.clearIconOverride(a.optString("package")); applied++ }
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
    fun iconPack(): String = prefs.getString("icon_pack", "") ?: ""
    fun stateSummary(): String = "theme=" + theme() + ", layout=" + layout() + ", iconPack=" + iconPack() + ", hidden=" + hiddenPackages()

    fun themeBackground(): Int = when (theme().lowercase()) {
        "midnight" -> Color.rgb(5, 8, 14)
        "ocean" -> Color.rgb(6, 24, 38)
        "ember" -> Color.rgb(38, 16, 12)
        "default" -> Color.rgb(16, 18, 24)
        else -> runCatching {
            Color.parseColor(JSONObject(store.themeFile(theme()).readText()).optString("bg", "#101218"))
        }.getOrDefault(Color.rgb(16, 18, 24))
    }
}