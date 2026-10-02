package com.aicontrol.launcher.theme

/** Validated, platform-independent theme values shared by prompt parsing and persistence. */
object ThemeSpec {
    const val DEFAULT_BACKGROUND = "#080A12"
    const val DEFAULT_ACCENT = "#46D2FF"
    const val DEFAULT_ACCENT2 = "#9B5CFF"
    const val DEFAULT_CARD = "#141723"
    const val DEFAULT_STYLE = "glass"

    val styles = setOf("glass", "flat", "neon")

    val namedColors = linkedMapOf(
        "black" to "#000000",
        "white" to "#FFFFFF",
        "gray" to "#808080",
        "grey" to "#808080",
        "red" to "#D32F2F",
        "orange" to "#FF8A3D",
        "yellow" to "#FBC02D",
        "green" to "#2E7D32",
        "teal" to "#0B5960",
        "cyan" to "#46D2FF",
        "blue" to "#174EA6",
        "indigo" to "#3949AB",
        "purple" to "#9B5CFF",
        "violet" to "#8E44AD",
        "pink" to "#FF5C9A",
        "navy" to "#08111F",
        "dark blue" to "#081A2E",
        "midnight blue" to "#060A12",
        "deep navy" to "#08111F",
        "ocean blue" to "#061827"
    )

    data class Values(
        val name: String,
        val background: String,
        val accent: String,
        val accent2: String,
        val card: String,
        val style: String
    )

    fun normalizeColor(value: String): String {
        val input = value.trim()
        if (input.matches(Regex("#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?"))) {
            return input.uppercase()
        }
        return namedColors[input.lowercase()]
            ?: throw IllegalArgumentException("Unsupported color '$input'. Use a named color or #RRGGBB.")
    }

    fun validate(
        name: String,
        background: String = DEFAULT_BACKGROUND,
        accent: String = DEFAULT_ACCENT,
        accent2: String = DEFAULT_ACCENT2,
        card: String = DEFAULT_CARD,
        style: String = DEFAULT_STYLE
    ): Values {
        val normalizedName = name.trim()
        require(normalizedName.matches(Regex("[A-Za-z0-9][A-Za-z0-9 _.-]{0,39}"))) {
            "Theme name must be 1–40 characters and use letters, numbers, spaces, dots, underscores, or hyphens."
        }
        val normalizedStyle = style.trim().lowercase()
        require(normalizedStyle in styles) { "Unsupported card style '$style'." }
        return Values(
            name = normalizedName,
            background = normalizeColor(background),
            accent = normalizeColor(accent),
            accent2 = normalizeColor(accent2),
            card = normalizeColor(card),
            style = normalizedStyle
        )
    }

    /** A simple offline palette hint; this chooses colors only and never supplies character art. */
    fun suggestedPalette(name: String): Values {
        val normalized = name.trim().lowercase()
        return when {
            "spider" in normalized || "web hero" in normalized -> validate(
                name, "#0B1020", "#E62429", "#1E5AA8", "#14213D", "neon"
            )
            else -> validate(name)
        }
    }

    /** Search only for a matching abstract palette, not copyrighted character imagery. */
    fun suggestedWallpaperQuery(name: String): String {
        val normalized = name.lowercase()
        return when {
            "spider" in normalized || "web hero" in normalized -> "abstract red blue geometric wallpaper"
            "ocean" in normalized -> "abstract ocean blue gradient wallpaper"
            "ember" in normalized -> "abstract orange red gradient wallpaper"
            else -> "abstract colorful geometric wallpaper"
        }
    }
}
