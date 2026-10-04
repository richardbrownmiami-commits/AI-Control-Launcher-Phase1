package com.aicontrol.launcher.theme

import org.json.JSONObject

/** Stable on-device representation of custom themes, including the selected installed icon-pack package. */
internal object ThemeConfigCodec {
    fun encode(values: ThemeSpec.Values): JSONObject = JSONObject()
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

    fun decode(json: JSONObject, fallbackName: String): ThemeSpec.Values {
        val storedName = json.optString("name", fallbackName)
        val wallpaperAsset = json.optString("wallpaperAsset").takeIf { it.isNotBlank() }
            ?: if (json.optBoolean("paletteOnly", false)) null else ThemeSpec.bundledWallpaperAsset(storedName)
        val iconAssets = json.optJSONObject("iconAssets")?.let { objectValue ->
            buildMap { objectValue.keys().forEach { key -> put(key, objectValue.optString(key)) } }
        } ?: emptyMap()
        return ThemeSpec.validate(
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
            iconAssets = iconAssets,
            iconPackPackage = json.optString("iconPackPackage").takeIf { it.isNotBlank() }
        )
    }
}
