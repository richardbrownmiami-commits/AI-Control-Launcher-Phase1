package com.aicontrol.launcher.theme

/** Validated, platform-independent theme values shared by prompt parsing, presets, and persistence. */
object ThemeSpec {
    const val DEFAULT_BACKGROUND = "#101319"
    const val DEFAULT_ACCENT = "#BBC8FF"
    const val DEFAULT_ACCENT2 = "#A9DCCE"
    const val DEFAULT_CARD = "#1B1F27"
    const val DEFAULT_STYLE = "glass"
    const val DEFAULT_TYPOGRAPHY = "system"
    const val DEFAULT_ICON_STYLE = "rounded"
    const val DEFAULT_BACKGROUND_STYLE = "solid"
    const val DEFAULT_LAYOUT = "grid"

    val styles = setOf("glass", "flat", "neon")
    val typographies = setOf("system", "compact", "serif")
    val iconStyles = setOf("rounded", "circle", "squircle")
    val backgroundStyles = setOf("gradient", "solid", "aurora", "warm")
    val layouts = setOf("grid", "compact", "dense", "wide")
    private val paletteOnlyPrompt = Regex(
        "\\b(?:(?:colors?|colours?)[\\s-]+only|palette[\\s-]+only|(?:only|just)\\s+(?:the\\s+)?(?:colors?|colours?|palette)|(?:no|without)\\s+(?:a\\s+)?wallpaper)\\b",
        RegexOption.IGNORE_CASE
    )

    val namedColors = linkedMapOf(
        "black" to "#000000", "white" to "#FFFFFF", "gray" to "#808080", "grey" to "#808080",
        "red" to "#D32F2F", "orange" to "#FF8A3D", "yellow" to "#FBC02D", "green" to "#2E7D32",
        "teal" to "#0B5960", "cyan" to "#46D2FF", "blue" to "#174EA6", "indigo" to "#3949AB",
        "purple" to "#9B5CFF", "violet" to "#8E44AD", "pink" to "#FF5C9A", "navy" to "#08111F",
        "dark blue" to "#081A2E", "midnight blue" to "#060A12", "deep navy" to "#08111F",
        "ocean blue" to "#061827"
    )

    val builtInNames = listOf("default", "midnight", "ocean", "ember", "aurora", "sunset", "sage", "paper", "graphite")

    /** A user-owned bundle. Asset references are safe filenames inside this app's private library. */
    data class Values(
        val name: String,
        val background: String,
        val accent: String,
        val accent2: String,
        val card: String,
        val style: String,
        val typography: String = DEFAULT_TYPOGRAPHY,
        val iconStyle: String = DEFAULT_ICON_STYLE,
        val backgroundStyle: String = DEFAULT_BACKGROUND_STYLE,
        val layout: String = DEFAULT_LAYOUT,
        val wallpaperAsset: String? = null,
        val iconAssets: Map<String, String> = emptyMap(),
        val iconPackPackage: String? = null
    )

    fun normalizeColor(value: String): String {
        val input = value.trim()
        if (input.matches(Regex("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?"))) return input.uppercase()
        return namedColors[input.lowercase()]
            ?: throw IllegalArgumentException("Unsupported color '$input'. Use a named color or #RRGGBB.")
    }

    fun isPaletteOnlyRequest(prompt: String): Boolean = paletteOnlyPrompt.containsMatchIn(prompt)

    fun validate(
        name: String,
        background: String = DEFAULT_BACKGROUND,
        accent: String = DEFAULT_ACCENT,
        accent2: String = DEFAULT_ACCENT2,
        card: String = DEFAULT_CARD,
        style: String = DEFAULT_STYLE,
        typography: String = DEFAULT_TYPOGRAPHY,
        iconStyle: String = DEFAULT_ICON_STYLE,
        backgroundStyle: String = DEFAULT_BACKGROUND_STYLE,
        layout: String = DEFAULT_LAYOUT,
        wallpaperAsset: String? = null,
        iconAssets: Map<String, String> = emptyMap(),
        iconPackPackage: String? = null
    ): Values {
        val normalizedName = name.trim()
        require(normalizedName.matches(Regex("[A-Za-z0-9][A-Za-z0-9 _.-]{0,39}"))) {
            "Theme name must be 1–40 characters and use letters, numbers, spaces, dots, underscores, or hyphens."
        }
        val normalizedStyle = style.trim().lowercase()
        val normalizedTypography = typography.trim().lowercase()
        val normalizedIconStyle = iconStyle.trim().lowercase()
        val normalizedBackgroundStyle = backgroundStyle.trim().lowercase()
        val normalizedLayout = layout.trim().lowercase()
        require(normalizedStyle in styles) { "Unsupported card style '$style'." }
        require(normalizedTypography in typographies) { "Unsupported typography '$typography'." }
        require(normalizedIconStyle in iconStyles) { "Unsupported icon style '$iconStyle'." }
        require(normalizedBackgroundStyle in backgroundStyles) { "Unsupported background style '$backgroundStyle'." }
        require(normalizedLayout in layouts) { "Unsupported home-screen layout '$layout'." }
        val wallpaper = wallpaperAsset?.trim()?.takeIf { it.isNotEmpty() }?.also(::requireSafeAssetName)
        require(iconAssets.size <= 100) { "A theme can include at most 100 app-icon mappings." }
        val normalizedIcons = iconAssets.map { (packageName, assetName) ->
            require(packageName.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) {
                "Invalid app package in theme icon mappings."
            }
            requireSafeAssetName(assetName)
            packageName to assetName.trim()
        }.toMap()
        val normalizedPack = iconPackPackage?.trim()?.takeIf { it.isNotEmpty() }?.also {
            require(it.matches(Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+"))) { "Invalid installed icon-pack package." }
        }
        return Values(
            normalizedName, normalizeColor(background), normalizeColor(accent), normalizeColor(accent2),
            normalizeColor(card), normalizedStyle, normalizedTypography, normalizedIconStyle,
            normalizedBackgroundStyle, normalizedLayout, wallpaper, normalizedIcons, normalizedPack
        )
    }

    private fun requireSafeAssetName(value: String) {
        require(value.trim().matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,119}")) && ".." !in value) {
            "Theme assets must use a safe library filename, not a path or URL."
        }
    }

    fun builtIn(name: String): Values? {
        val values = when (name.trim().lowercase()) {
            "default" -> validate("default")
            "midnight" -> validate("midnight", "#05080E", "#74D7FF", "#AB83FF", "#111725", "glass", "compact", "squircle", "gradient", "compact")
            "ocean" -> validate("ocean", "#061826", "#46D2FF", "#63E6BE", "#102B3C", "glass", "system", "circle", "aurora", "wide")
            "ember" -> validate("ember", "#26100C", "#FF8246", "#FF5C9A", "#3A1915", "neon", "compact", "rounded", "warm", "dense")
            "aurora" -> validate("aurora", "#07131B", "#77F2C4", "#91A3FF", "#10252B", "glass", "system", "squircle", "aurora", "grid")
            "sunset" -> validate("sunset", "#211126", "#FFB36B", "#FF6B9A", "#351B34", "neon", "serif", "rounded", "warm", "wide")
            "sage" -> validate("sage", "#101A16", "#A9D6A3", "#E1C58A", "#1D2A22", "flat", "system", "squircle", "gradient", "compact")
            "paper" -> validate("paper", "#F2EEE5", "#315A74", "#A45B42", "#FFF9EE", "flat", "serif", "rounded", "solid", "grid")
            "graphite" -> validate("graphite", "#15171B", "#E0E4EA", "#8EA7C1", "#24272D", "flat", "compact", "circle", "solid", "dense")
            else -> null
        } ?: return null
        return values.copy(wallpaperAsset = bundledWallpaperAsset(values.name))
    }

    /** Theme suggestions use abstract color and geometry, never bundled character artwork. */
    fun suggestedPalette(name: String): Values {
        val normalized = name.trim().lowercase()
        val displayName = safeThemeDisplayName(name)
        val values = when {
            "spider" in normalized || "web hero" in normalized -> validate(displayName, "#0B1020", "#E62429", "#1E5AA8", "#14213D", "neon", "compact", "squircle", "gradient", "dense")
            "ocean" in normalized -> builtIn("ocean")!!.copy(name = displayName)
            "sunset" in normalized || "ember" in normalized -> builtIn("sunset")!!.copy(name = displayName)
            "forest" in normalized || "sage" in normalized -> builtIn("sage")!!.copy(name = displayName)
            else -> validate(displayName)
        }
        return values.copy(wallpaperAsset = bundledWallpaperAsset(displayName))
    }

    fun safeThemeDisplayName(name: String): String {
        val normalized = name.trim()
        return when {
            Regex("\\bspider[ -]?man\\b", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> "Spider-Inspired"
            Regex("\\b(marvel|avengers?|batman|superman)\\b", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> "Superhero-Inspired"
            Regex("\\b(pokemon|pikachu)\\b", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> "Creature-Inspired"
            Regex("\\b(disney|mickey(?:\\s+mouse)?)\\b", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> "Storybook-Inspired"
            Regex("\\bstar[ -]?wars\\b", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> "Space-Inspired"
            Regex("\\bweb hero\\b", RegexOption.IGNORE_CASE).containsMatchIn(normalized) -> "Web-Inspired"
            else -> normalized
        }
    }

    fun bundledWallpaperAsset(name: String): String = when {
        "ember" in name.lowercase() || "sunset" in name.lowercase() -> "commons_abstract_warm.jpg"
        "sage" in name.lowercase() || "paper" in name.lowercase() -> "commons_abstract_sage.jpg"
        else -> "commons_abstract_blue.jpg"
    }

    fun suggestedWallpaperQuery(name: String): String = when {
        "spider" in name.lowercase() || "web hero" in name.lowercase() -> "abstract red blue geometric web pattern wallpaper"
        "ocean" in name.lowercase() -> "abstract ocean blue gradient wallpaper"
        "ember" in name.lowercase() -> "abstract orange red gradient wallpaper"
        else -> "abstract colorful geometric wallpaper"
    }
}
