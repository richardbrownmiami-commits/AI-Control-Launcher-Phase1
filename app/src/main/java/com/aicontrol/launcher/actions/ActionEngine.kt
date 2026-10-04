package com.aicontrol.launcher.actions

import android.content.Context
import android.graphics.Color
import com.aicontrol.launcher.apps.AppRepository
import com.aicontrol.launcher.assets.AssetStore
import com.aicontrol.launcher.assets.ImageAssetValidation
import com.aicontrol.launcher.assets.LauncherAssetManager
import com.aicontrol.launcher.theme.ThemeSpec
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject

data class ThemeState(
    val bg: Int, val accent: Int, val accent2: Int, val card: Int, val style: String,
    val typography: String = ThemeSpec.DEFAULT_TYPOGRAPHY,
    val iconStyle: String = ThemeSpec.DEFAULT_ICON_STYLE,
    val backgroundStyle: String = ThemeSpec.DEFAULT_BACKGROUND_STYLE
)

class ActionEngine(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("launcher_state", Context.MODE_PRIVATE)
    private val assets = LauncherAssetManager(appContext)
    private val store = AssetStore(appContext)
    private val assetAttributions = com.aicontrol.launcher.assets.AssetAttributionStore(appContext)
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
                        style = action.optString("style", ThemeSpec.DEFAULT_STYLE),
                        typography = action.optString("typography", ThemeSpec.DEFAULT_TYPOGRAPHY),
                        iconStyle = action.optString("iconStyle", ThemeSpec.DEFAULT_ICON_STYLE),
                        backgroundStyle = action.optString("backgroundStyle", ThemeSpec.DEFAULT_BACKGROUND_STYLE),
                        layout = action.optString("layout", ThemeSpec.DEFAULT_LAYOUT),
                        wallpaperAsset = action.optString("wallpaperAsset").takeIf { it.isNotBlank() },
                        iconAssets = action.optJSONObject("iconAssets")?.let { objectValue ->
                            buildMap { objectValue.keys().forEach { key -> put(key, objectValue.optString(key)) } }
                        } ?: emptyMap(),
                        iconPackPackage = action.optString("iconPackPackage").takeIf { it.isNotBlank() }
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
        recordThemeForRollback()
        writeTheme(values)
        setTheme(values.name)
    }

    /** Saves a reviewed custom theme without changing the currently active launcher appearance. */
    fun saveThemeDraft(values: ThemeSpec.Values) {
        val checked = ThemeSpec.validate(
            values.name, values.background, values.accent, values.accent2, values.card, values.style,
            values.typography, values.iconStyle, values.backgroundStyle, values.layout,
            values.wallpaperAsset, values.iconAssets, values.iconPackPackage
        )
        val conflictingCustomName = store.themes.listFiles()?.firstOrNull {
            it.isFile && it.extension.equals("json", true) &&
                it.nameWithoutExtension.equals(checked.name, ignoreCase = true) && it.nameWithoutExtension != checked.name
        }
        require(conflictingCustomName == null) { "A custom theme with the same name already exists; use the exact spelling to replace it." }
        writeTheme(checked)
    }

    /** Attach a verified image from the private library to a saved custom theme. */
    fun linkThemeWallpaper(themeName: String, file: java.io.File) {
        val path = file.canonicalPath
        require(store.themeFile(themeName).isFile) { "Save this custom theme before adding wallpaper." }
        require(path.startsWith(store.wallpapers.canonicalPath + java.io.File.separator) && file.isFile) {
            "Choose a wallpaper from this launcher's private asset library."
        }
        com.aicontrol.launcher.assets.ImageAssetValidation.validate(file)
        val current = themeValues(themeName)
        writeTheme(current.copy(wallpaperAsset = file.name))
    }

    /** Copy a user-selected/local-library image into a theme-scoped app-icon mapping. */
    fun linkThemeIcon(themeName: String, packageName: String, source: java.io.File) {
        require(store.themeFile(themeName).isFile) { "Save this custom theme before adding app icons." }
        require(packageName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) { "Choose an installed app." }
        val path = source.canonicalPath
        val fromPrivateWallpapers = path.startsWith(store.wallpapers.canonicalPath + java.io.File.separator)
        val fromPrivateImages = path.startsWith(store.images.canonicalPath + java.io.File.separator)
        require(source.isFile && (fromPrivateWallpapers || fromPrivateImages)) {
            "Choose an image from this launcher's private asset library."
        }
        com.aicontrol.launcher.assets.ImageAssetValidation.validate(source)
        val target = store.themeIcon(themeName, packageName)
        source.copyTo(target, overwrite = true)
        val current = themeValues(themeName)
        writeTheme(current.copy(iconAssets = current.iconAssets + (packageName to source.name)))
        assetAttributions.forAnyAsset(source.name)?.let { sourceAttribution ->
            assetAttributions.record(sourceAttribution.copy(type = "theme_icon"))
        }
    }

    fun themeIconFile(themeName: String, packageName: String): java.io.File? {
        val values = runCatching { themeValues(themeName) }.getOrNull() ?: return null
        if (packageName !in values.iconAssets) return null
        return store.themeIcon(themeName, packageName).takeIf { it.isFile }
    }

    private fun writeTheme(values: ThemeSpec.Values) {
        val target = store.themeFile(values.name)
        val temporary = File(target.parentFile, target.name + ".tmp")
        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(themeJson(values).toString().toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            if (!temporary.renameTo(target)) temporary.copyTo(target, overwrite = true)
        } finally {
            if (temporary.exists()) temporary.delete()
        }
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
        val old = JSONArray(prefs.getString("shortcuts", "[]") ?: "[]")
        val shortcuts = JSONArray()
        for (index in 0 until old.length()) {
            val item = old.optJSONObject(index) ?: continue
            if (item.optString("package") == packageName) continue
            shortcuts.put(item)
        }
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
    fun homeRows(): Int = prefs.getInt("home_rows", 5).coerceIn(3, 8)
    fun homeColumns(): Int = prefs.getInt("home_columns", 4).coerceIn(3, 7)
    fun iconSize(): Int = prefs.getInt("icon_size", 48).coerceIn(32, 72)
    fun dockVisible(): Boolean = prefs.getBoolean("dock_visible", true)
    fun homePage(): Int = prefs.getInt("home_page", 1).coerceIn(0, 2)
    fun drawerSort(): String = prefs.getString("drawer_sort", "A–Z") ?: "A–Z"
    fun drawerSearchVisible(): Boolean = prefs.getBoolean("drawer_search", true)
    fun drawerLabels(): Boolean = prefs.getBoolean("drawer_labels", true)

    fun availableThemes(): Set<String> {
        val custom = store.themes.listFiles()?.filter { it.isFile && it.extension.equals("json", true) }
            ?.map { it.nameWithoutExtension }?.toSet() ?: emptySet()
        val ordered = linkedMapOf<String, String>()
        custom.sorted().forEach { ordered.putIfAbsent(it.lowercase(), it) }
        ThemeSpec.builtInNames.forEach { ordered.putIfAbsent(it, it) }
        return ordered.values.toSet()
    }

    fun isCustomTheme(name: String): Boolean = store.themeFile(name).isFile

    fun setTheme(value: String) {
        val requested = value.trim()
        require(availableThemes().any { it.equals(requested, ignoreCase = true) }) { "Unknown theme '$requested'" }
        val stored = availableThemes().first { it.equals(requested, ignoreCase = true) }
        val values = themeValues(stored)
        values.iconAssets.keys.forEach { packageName ->
            val icon = store.themeIcon(stored, packageName)
            require(icon.isFile) { "Theme icon for $packageName is missing from the saved bundle; no theme changes were applied." }
            ImageAssetValidation.validate(icon)
        }
        val wallpaperFile = values.wallpaperAsset?.let { wallpaperName ->
            store.wallpaper(wallpaperName).also { wallpaper ->
                require(wallpaper.isFile) { "Theme wallpaper '$wallpaperName' is missing from the private asset library." }
                ImageAssetValidation.validate(wallpaper)
            }
        }
        if (!theme().equals(stored, ignoreCase = true)) recordThemeForRollback()
        wallpaperFile?.let { assets.setLauncherWallpaper(it).getOrThrow() }
        prefs.edit().putString("theme", stored).putString("style", values.style).apply()
        setLayout(values.layout)
        values.iconPackPackage?.let { prefs.edit().putString("installed_icon_pack", it).apply() }
    }

    fun setStyle(value: String) {
        val normalized = value.trim().lowercase()
        require(normalized in ThemeSpec.styles) { "Unsupported card style '$value'" }
        prefs.edit().putString("style", normalized).apply()
    }

    fun setLayout(value: String) {
        val normalized = value.trim().lowercase()
        require(normalized in setOf("grid", "compact", "dense", "wide")) { "Unsupported layout '$value'" }
        val defaultColumns = when (normalized) { "dense", "compact" -> 5; "wide" -> 3; else -> 4 }
        prefs.edit().putString("layout", normalized).putInt("home_columns", defaultColumns).apply()
    }

    fun setAppVisible(packageName: String, visible: Boolean) {
        require(packageName.isNotBlank()) { "App package is empty" }
        if (visible) removePackages("hidden", JSONArray().put(packageName)) else addHidden(packageName)
    }

    fun setInstalledIconPack(value: String) { prefs.edit().putString("installed_icon_pack", value).apply() }
    fun setShowLabels(value: Boolean) { prefs.edit().putBoolean("show_labels", value).apply() }
    fun setDockCount(value: Int) { prefs.edit().putInt("dock_count", value.coerceIn(3, 6)).apply() }
    fun setHomeRows(value: Int) { prefs.edit().putInt("home_rows", value.coerceIn(3, 8)).apply() }
    fun setHomeColumns(value: Int) { prefs.edit().putInt("home_columns", value.coerceIn(3, 7)).apply() }
    fun setIconSize(value: Int) { prefs.edit().putInt("icon_size", value.coerceIn(32, 72)).apply() }
    fun setDockVisible(value: Boolean) { prefs.edit().putBoolean("dock_visible", value).apply() }
    fun setHomePage(value: Int) { prefs.edit().putInt("home_page", value.coerceIn(0, 2)).apply() }
    fun setDrawerSort(value: String) {
        require(value in setOf("A–Z", "Z–A", "Package")) { "Unsupported app drawer sort order." }
        prefs.edit().putString("drawer_sort", value).apply()
    }
    fun setDrawerSearchVisible(value: Boolean) { prefs.edit().putBoolean("drawer_search", value).apply() }
    fun setDrawerLabels(value: Boolean) { prefs.edit().putBoolean("drawer_labels", value).apply() }

    fun hasThemeRollback(): Boolean = !prefs.getString("theme_rollback_snapshot", null).isNullOrBlank()
    fun rollbackTheme(): Boolean {
        val raw = prefs.getString("theme_rollback_snapshot", null) ?: return false
        return runCatching {
            val snapshot = JSONObject(raw)
            val name = snapshot.getString("name")
            val values = ThemeSpec.validate(
                name = name,
                background = snapshot.getString("bg"),
                accent = snapshot.getString("accent"),
                accent2 = snapshot.getString("accent2"),
                card = snapshot.getString("card"),
                style = snapshot.getString("style"),
                typography = snapshot.getString("typography"),
                iconStyle = snapshot.getString("iconStyle"),
                backgroundStyle = snapshot.getString("backgroundStyle"),
                layout = snapshot.optString("layout", ThemeSpec.DEFAULT_LAYOUT),
                wallpaperAsset = snapshot.optString("wallpaperAsset").takeIf { it.isNotBlank() }
                    ?: if (snapshot.optBoolean("paletteOnly", false)) null else ThemeSpec.bundledWallpaperAsset(name),
                iconAssets = snapshot.optJSONObject("iconAssets")?.let { objectValue ->
                    buildMap { objectValue.keys().forEach { key -> put(key, objectValue.optString(key)) } }
                } ?: emptyMap(),
                iconPackPackage = snapshot.optString("iconPackPackage").takeIf { it.isNotBlank() }
            )
            val customFile = store.themeFile(name)
            if (snapshot.optBoolean("custom", false)) customFile.writeText(themeJson(values).toString())
            else if (customFile.isFile) customFile.delete()
            val editor = prefs.edit().remove("theme_rollback_snapshot").putString("theme", name)
                .putString("style", snapshot.optString("activeStyle", values.style))
                .putString("layout", snapshot.optString("activeLayout", values.layout))
            if (snapshot.has("activeWallpaper")) editor.putString("active_wallpaper", snapshot.optString("activeWallpaper"))
            else editor.remove("active_wallpaper")
            if (snapshot.has("activeIconPack")) editor.putString("installed_icon_pack", snapshot.optString("activeIconPack"))
            else editor.remove("installed_icon_pack")
            editor.apply()
            true
        }.getOrDefault(false)
    }

    private fun recordThemeForRollback() {
        val current = theme()
        val values = runCatching { themeValues(current) }.getOrNull() ?: return
        val snapshot = themeJson(values)
            .put("custom", store.themeFile(current).isFile)
            .put("activeStyle", style())
            .put("activeLayout", layout())
            .put("activeWallpaper", prefs.getString("active_wallpaper", "") ?: "")
            .put("activeIconPack", installedIconPack())
        prefs.edit().putString("theme_rollback_snapshot", snapshot.toString()).apply()
    }

    private fun themeJson(values: ThemeSpec.Values) = JSONObject()
        .put("name", values.name)
        .put("bg", values.background)
        .put("accent", values.accent)
        .put("accent2", values.accent2)
        .put("card", values.card)
        .put("style", values.style)
        .put("typography", values.typography)
        .put("iconStyle", values.iconStyle)
        .put("backgroundStyle", values.backgroundStyle)
        .put("layout", values.layout)
        .put("wallpaperAsset", values.wallpaperAsset ?: "")
        .put("paletteOnly", values.wallpaperAsset == null)
        .put("iconPackPackage", values.iconPackPackage ?: "")
        .put("iconAssets", JSONObject().apply { values.iconAssets.forEach { (pkg, asset) -> put(pkg, asset) } })

    fun themeValues(name: String = theme()): ThemeSpec.Values {
        val customFile = store.themeFile(name)
        if (customFile.isFile) return try {
            val json = JSONObject(customFile.readText())
            val storedName = json.optString("name", name)
            val wallpaperAsset = json.optString("wallpaperAsset").takeIf { it.isNotBlank() }
                ?: if (json.optBoolean("paletteOnly", false)) null else ThemeSpec.bundledWallpaperAsset(storedName)
            ThemeSpec.validate(
                name = storedName,
                background = json.optString("bg", ThemeSpec.DEFAULT_BACKGROUND),
                accent = json.optString("accent", ThemeSpec.DEFAULT_ACCENT),
                accent2 = json.optString("accent2", ThemeSpec.DEFAULT_ACCENT2),
                card = json.optString("card", ThemeSpec.DEFAULT_CARD),
                style = json.optString("style", ThemeSpec.DEFAULT_STYLE),
                typography = json.optString("typography", ThemeSpec.DEFAULT_TYPOGRAPHY),
                iconStyle = json.optString("iconStyle", ThemeSpec.DEFAULT_ICON_STYLE),
                backgroundStyle = json.optString("backgroundStyle", ThemeSpec.DEFAULT_BACKGROUND_STYLE),
                layout = json.optString("layout", ThemeSpec.DEFAULT_LAYOUT),
                wallpaperAsset = wallpaperAsset,
                iconAssets = json.optJSONObject("iconAssets")?.let { objectValue ->
                    buildMap { objectValue.keys().forEach { key -> put(key, objectValue.optString(key)) } }
                } ?: emptyMap(),
                iconPackPackage = json.optString("iconPackPackage").takeIf { it.isNotBlank() }
            )
        } catch (error: Exception) {
            throw IllegalArgumentException("Saved theme '$name' is damaged; its bundle was not applied.", error)
        }
        return ThemeSpec.builtIn(name) ?: ThemeSpec.validate(name)
    }

    fun themeState(): ThemeState {
        val values = runCatching { themeValues() }.getOrElse { ThemeSpec.validate("default") }
        return ThemeState(
            Color.parseColor(values.background), Color.parseColor(values.accent), Color.parseColor(values.accent2),
            Color.parseColor(values.card), prefs.getString("style", null)?.takeIf { it in ThemeSpec.styles } ?: values.style,
            values.typography, values.iconStyle, values.backgroundStyle
        )
    }

    fun stateSummary(): String {
        val labels = apps.listLaunchableApps().take(40).joinToString(",") { it.label }
        return "theme=${theme()}, layout=${layout()}, style=${style()}, iconPack=${iconPack()}, hiddenCount=${hiddenPackages().size}, apps=[$labels]"
    }
}
