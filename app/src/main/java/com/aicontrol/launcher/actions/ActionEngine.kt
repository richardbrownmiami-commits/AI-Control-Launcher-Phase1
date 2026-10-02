package com.aicontrol.launcher.actions

import android.content.Context
import android.graphics.Color
import com.aicontrol.launcher.apps.AppRepository
import com.aicontrol.launcher.assets.AssetStore
import com.aicontrol.launcher.assets.LauncherAssetManager
import com.aicontrol.launcher.theme.ThemeSpec
import org.json.JSONArray
import org.json.JSONObject

 data class ThemeState(val bg: Int, val accent: Int, val accent2: Int, val card: Int, val style: String)

class ActionEngine(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("launcher_state", Context.MODE_PRIVATE)
    private val assets = LauncherAssetManager(appContext)
    private val store = AssetStore(appContext)
    private val apps = AppRepository(appContext)

    fun applyJson(json: String): Result<Int> = runCatching {
        val actions = JSONObject(json).optJSONArray("actions") ?: JSONArray()
        var applied = 0
        for (i in 0 until actions.length()) {
            val action = actions.getJSONObject(i)
            when (action.optString("action")) {
                "HIDE_APPS" -> {
                    setPackages("hidden", action.optJSONArray("packages"))
                    applied++
                }
                "SHOW_APPS" -> {
                    removePackages("hidden", action.optJSONArray("packages"))
                    applied++
                }
                "HIDE_APPS_BY_NAME" -> {
                    val targets = apps.listLaunchableApps()
                    val names = action.optJSONArray("names") ?: JSONArray()
                    for (j in 0 until names.length()) {
                        val wanted = names.optString(j).trim()
                        val matches = targets.filter { it.label.equals(wanted, ignoreCase = true) }
                        require(matches.size <= 1) { "App name '$wanted' is ambiguous" }
                        matches.singleOrNull()?.let { setAppVisible(it.packageName, false) }
                    }
                    applied++
                }
                "SET_THEME" -> {
                    setTheme(action.optString("theme", "default"))
                    applied++
                }
                "CREATE_THEME" -> {
                    val values = ThemeSpec.validate(
                        name = action.optString("name"),
                        background = action.optString("bg", ThemeSpec.DEFAULT_BACKGROUND),
                        accent = action.optString("accent", ThemeSpec.DEFAULT_ACCENT),
                        accent2 = action.optString("accent2", ThemeSpec.DEFAULT_ACCENT2),
                        card = action.optString("card", ThemeSpec.DEFAULT_CARD),
                        style = action.optString("style", ThemeSpec.DEFAULT_STYLE)
                    )
                    saveTheme(values)
                    applied++
                }
                "SET_LAYOUT" -> {
                    setLayout(action.optString("layout", "grid"))
                    applied++
                }
                "SET_STYLE" -> {
                    setStyle(action.optString("style", ThemeSpec.DEFAULT_STYLE))
                    applied++
                }
                "CREATE_WORKSPACE" -> {
                    val name = action.optString("name").trim()
                    require(name.matches(Regex("[A-Za-z0-9][A-Za-z0-9 _.-]{0,39}"))) { "Workspace name is invalid" }
                    prefs.edit().putString("workspace:$name", action.optJSONArray("packages")?.toString() ?: "[]").apply()
                    applied++
                }
                "DOWNLOAD_WALLPAPER" -> {
                    val url = action.optString("url")
                    require(url.startsWith("https://", ignoreCase = true)) { "Wallpaper URL must use HTTPS" }
                    val file = assets.downloadWallpaper(url, action.optString("name", "wallpaper")).getOrThrow()
                    if (action.optBoolean("apply", true)) assets.applyWallpaper(file).getOrThrow()
                    applied++
                }
                "DOWNLOAD_IMAGE" -> {
                    val url = action.optString("url")
                    require(url.startsWith("https://", ignoreCase = true)) { "Image URL must use HTTPS" }
                    assets.downloadImage(url, action.optString("name", "downloaded-image")).getOrThrow()
                    applied++
                }
                "DOWNLOAD_ICON" -> {
                    val url = action.optString("url")
                    val packageName = action.optString("package").trim()
                    require(url.startsWith("https://", ignoreCase = true)) { "Icon URL must use HTTPS" }
                    require(packageName.isNotEmpty()) { "Icon package is empty" }
                    assets.downloadIcon(url, packageName).getOrThrow()
                    applied++
                }
                "CREATE_ICON_PACK" -> {
                    val name = action.optString("name").trim()
                    require(name.isNotEmpty()) { "Icon pack name is empty" }
                    val icons = action.optJSONArray("icons") ?: JSONArray()
                    val items = mutableListOf<Pair<String, String>>()
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
                    val name = action.optString("name").trim()
                    assets.applyIconPack(name).getOrThrow()
                    prefs.edit().putString("icon_pack", name).apply()
                    applied++
                }
                "CLEAR_ICON_OVERRIDE" -> {
                    val packageName = action.optString("package").trim()
                    require(packageName.isNotEmpty()) { "Icon package is empty" }
                    assets.clearIconOverride(packageName)
                    applied++
                }
                "CLEAR_ICON_PACK" -> {
                    val name = action.optString("name").trim()
                    if (name.isNotEmpty()) assets.clearIconPack(name).getOrThrow() else assets.clearAllIconOverrides()
                    prefs.edit().remove("icon_pack").apply()
                    applied++
                }
                "DELETE_ASSET" -> {
                    assets.deleteAsset(action.optString("type"), action.optString("name")).getOrThrow()
                    applied++
                }
                "ADD_SHORTCUT" -> {
                    addShortcut(action.optString("label"), action.optString("package"), action.optString("activity"))
                    applied++
                }
                "REMOVE_SHORTCUT" -> {
                    removeShortcut(action.optString("label"), action.optString("package"))
                    applied++
                }
                else -> error("Unsupported launcher action: ${action.optString("action")}")
            }
        }
        applied
    }

    private fun saveTheme(values: ThemeSpec.Values) {
        val conflictingCustomName = store.themes.listFiles()?.firstOrNull {
            it.isFile && it.extension.equals("json", true) &&
                it.nameWithoutExtension.equals(values.name, ignoreCase = true) &&
                it.nameWithoutExtension != values.name
        }
        require(conflictingCustomName == null) { "A custom theme with the same name already exists; use the exact spelling to replace it." }
        val data = JSONObject()
            .put("name", values.name)
            .put("bg", values.background)
            .put("accent", values.accent)
            .put("accent2", values.accent2)
            .put("card", values.card)
            .put("style", values.style)
        store.themeFile(values.name).writeText(data.toString())
        prefs.edit().putString("theme", values.name).apply()
    }

    private fun addHidden(packageName: String) {
        val current = hiddenPackages().toMutableSet()
        current.add(packageName)
        prefs.edit().putStringSet("hidden", current).apply()
    }

    private fun setPackages(key: String, values: JSONArray?) {
        val current = (prefs.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
        if (values != null) for (i in 0 until values.length()) current.add(values.getString(i))
        prefs.edit().putStringSet(key, current).apply()
    }

    private fun removePackages(key: String, values: JSONArray?) {
        val current = (prefs.getStringSet(key, emptySet()) ?: emptySet()).toMutableSet()
        if (values != null) for (i in 0 until values.length()) current.remove(values.getString(i))
        prefs.edit().putStringSet(key, current).apply()
    }

    private fun addShortcut(label: String, packageName: String, activity: String) {
        require(label.isNotBlank() && packageName.isNotBlank()) { "Shortcut label/package required" }
        val shortcuts = JSONArray(prefs.getString("shortcuts", "[]") ?: "[]")
        shortcuts.put(JSONObject().put("label", label).put("package", packageName).put("activity", activity))
        prefs.edit().putString("shortcuts", shortcuts.toString()).apply()
    }

    private fun removeShortcut(label: String, packageName: String) {
        val old = JSONArray(prefs.getString("shortcuts", "[]") ?: "[]")
        val filtered = JSONArray()
        for (i in 0 until old.length()) {
            val item = old.getJSONObject(i)
            if ((label.isNotBlank() && item.optString("label") == label) ||
                (packageName.isNotBlank() && item.optString("package") == packageName)) continue
            filtered.put(item)
        }
        prefs.edit().putString("shortcuts", filtered.toString()).apply()
    }

    fun shortcuts(): List<JSONObject> {
        val values = JSONArray(prefs.getString("shortcuts", "[]") ?: "[]")
        return (0 until values.length()).map { values.getJSONObject(it) }
    }

    fun hiddenPackages(): Set<String> = prefs.getStringSet("hidden", emptySet())?.toSet() ?: emptySet()
    fun theme(): String = prefs.getString("theme", "default") ?: "default"
    fun layout(): String = prefs.getString("layout", "grid") ?: "grid"
    fun style(): String = prefs.getString("style", themeState().style) ?: ThemeSpec.DEFAULT_STYLE
    fun iconPack(): String = prefs.getString("icon_pack", "") ?: ""
    fun installedIconPack(): String = prefs.getString("installed_icon_pack", "") ?: ""
    fun showLabels(): Boolean = prefs.getBoolean("show_labels", true)
    fun dockCount(): Int = prefs.getInt("dock_count", 5)

    fun availableThemes(): Set<String> {
        val custom = store.themes.listFiles()?.filter { it.isFile && it.extension.equals("json", true) }
            ?.map { it.nameWithoutExtension }?.toSet() ?: emptySet()
        val ordered = linkedMapOf<String, String>()
        custom.sorted().forEach { ordered.putIfAbsent(it.lowercase(), it) }
        listOf("default", "midnight", "ocean", "ember").forEach { ordered.putIfAbsent(it, it) }
        return ordered.values.toSet()
    }

    fun setTheme(value: String) {
        val requested = value.trim()
        require(availableThemes().any { it.equals(requested, ignoreCase = true) }) { "Unknown theme '$requested'" }
        val stored = availableThemes().first { it.equals(requested, ignoreCase = true) }
        prefs.edit().putString("theme", stored).apply()
    }

    fun setStyle(value: String) {
        val normalized = value.trim().lowercase()
        require(normalized in ThemeSpec.styles) { "Unsupported card style '$value'" }
        prefs.edit().putString("style", normalized).apply()
    }

    fun setLayout(value: String) {
        val normalized = value.trim().lowercase()
        require(normalized in setOf("grid", "compact", "dense", "wide")) { "Unsupported layout '$value'" }
        prefs.edit().putString("layout", normalized).apply()
    }

    fun setAppVisible(packageName: String, visible: Boolean) {
        require(packageName.isNotBlank()) { "App package is empty" }
        if (visible) removePackages("hidden", JSONArray().put(packageName)) else addHidden(packageName)
    }

    fun setInstalledIconPack(value: String) { prefs.edit().putString("installed_icon_pack", value).apply() }
    fun setShowLabels(value: Boolean) { prefs.edit().putBoolean("show_labels", value).apply() }
    fun setDockCount(value: Int) { prefs.edit().putInt("dock_count", value.coerceIn(3, 6)).apply() }

    fun themeState(): ThemeState {
        val storedStyle = prefs.getString("style", ThemeSpec.DEFAULT_STYLE)?.lowercase()
        val safeStyle = storedStyle?.takeIf { it in ThemeSpec.styles } ?: ThemeSpec.DEFAULT_STYLE
        val default = ThemeState(
            Color.rgb(8, 10, 18), Color.rgb(70, 210, 255), Color.rgb(155, 92, 255),
            Color.rgb(20, 23, 35), safeStyle
        )
        val customFile = store.themeFile(theme())
        if (customFile.isFile) {
            return runCatching {
                val json = JSONObject(customFile.readText())
                val values = ThemeSpec.validate(
                    name = json.optString("name", theme()),
                    background = json.optString("bg", ThemeSpec.DEFAULT_BACKGROUND),
                    accent = json.optString("accent", ThemeSpec.DEFAULT_ACCENT),
                    accent2 = json.optString("accent2", ThemeSpec.DEFAULT_ACCENT2),
                    card = json.optString("card", ThemeSpec.DEFAULT_CARD),
                    style = json.optString("style", ThemeSpec.DEFAULT_STYLE)
                )
                ThemeState(
                    Color.parseColor(values.background), Color.parseColor(values.accent),
                    Color.parseColor(values.accent2), Color.parseColor(values.card), values.style
                )
            }.getOrDefault(default)
        }
        return when (theme().lowercase()) {
            "midnight" -> default.copy(bg = Color.rgb(5, 8, 14))
            "ocean" -> default.copy(bg = Color.rgb(6, 24, 38), accent = Color.rgb(64, 214, 255))
            "ember" -> default.copy(bg = Color.rgb(38, 16, 12), accent = Color.rgb(255, 130, 70), accent2 = Color.rgb(255, 70, 150))
            else -> default
        }
    }

    fun stateSummary(): String {
        val labels = apps.listLaunchableApps().take(40).joinToString(",") { it.label }
        return "theme=${theme()}, layout=${layout()}, style=${style()}, iconPack=${iconPack()}, hiddenCount=${hiddenPackages().size}, apps=[$labels]"
    }
}
