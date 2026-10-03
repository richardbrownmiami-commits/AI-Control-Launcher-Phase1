package com.aicontrol.launcher.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import com.aicontrol.launcher.actions.ActionEngine
import com.aicontrol.launcher.theme.ThemeSpec

/** Shared, theme-aware design tokens for the native launcher screens. */
object UiTheme {
    var bg = Color.rgb(16, 19, 25); private set
    var card = Color.rgb(27, 31, 39); private set
    var card2 = Color.rgb(36, 41, 50); private set
    var accent = Color.rgb(187, 200, 255); private set
    var accent2 = Color.rgb(169, 220, 206); private set
    var typography = ThemeSpec.DEFAULT_TYPOGRAPHY; private set
    var iconStyle = ThemeSpec.DEFAULT_ICON_STYLE; private set
    var backgroundStyle = ThemeSpec.DEFAULT_BACKGROUND_STYLE; private set
    var textPrimary = Color.rgb(244, 245, 248); private set
    var textMuted = Color.rgb(169, 176, 188); private set
    var textOnAccent = Color.rgb(27, 31, 39); private set
    val success = Color.rgb(129, 210, 169)
    val warning = Color.rgb(239, 192, 115)
    val danger = Color.rgb(235, 121, 133)

    fun bind(engine: ActionEngine) {
        val t = engine.themeState()
        bg = t.bg
        card = t.card
        card2 = blend(t.card, t.bg, 0.30f)
        accent = t.accent
        accent2 = t.accent2
        typography = t.typography
        iconStyle = t.iconStyle
        backgroundStyle = t.backgroundStyle
        val light = luminance(bg) > 0.56
        textPrimary = if (light) Color.rgb(31, 36, 45) else Color.rgb(244, 245, 248)
        textMuted = if (light) Color.rgb(92, 99, 111) else Color.rgb(169, 176, 188)
        textOnAccent = contrasting(blend(accent, accent2, 0.5f))
    }

    fun font(): Typeface = when (typography) {
        "compact" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        "serif" -> Typeface.create("serif", Typeface.NORMAL)
        else -> Typeface.create("sans-serif", Typeface.NORMAL)
    }

    fun iconRadiusDp(size: Int): Float = when (iconStyle) {
        "circle" -> size / 2f
        "squircle" -> size * 0.28f
        else -> size * 0.22f
    }

    fun homeBackground(): GradientDrawable {
        val colors = when (backgroundStyle) {
            "solid" -> intArrayOf(bg, bg)
            "aurora" -> intArrayOf(blend(bg, accent2, 0.22f), blend(bg, accent, 0.12f), bg)
            "warm" -> intArrayOf(blend(bg, accent2, 0.20f), blend(bg, accent, 0.10f), bg)
            else -> intArrayOf(blend(bg, accent2, 0.10f), bg)
        }
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = 0f }
    }

    fun rounded(color: Int, radiusDp: Float, strokeColor: Int? = null, strokeDp: Int = 0) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp
        if (strokeColor != null && strokeDp > 0) setStroke(strokeDp, withAlpha(strokeColor, 0x5A))
    }

    fun gradient(radiusDp: Float = 20f) = GradientDrawable(
        GradientDrawable.Orientation.LEFT_RIGHT,
        intArrayOf(blend(accent, Color.WHITE, 0.04f), blend(accent2, accent, 0.22f))
    ).apply { cornerRadius = radiusDp }

    fun background() = homeBackground()

    fun contrasting(color: Int): Int = if (luminance(color) > 0.56) Color.rgb(27, 31, 39) else Color.WHITE

    fun styleCard(view: View, color: Int = card, stroke: Boolean = false) {
        val outline = if (stroke) blend(color, textMuted, 0.15f) else null
        view.background = rounded(color, 22f, outline, if (stroke) 1 else 0)
        view.elevation = 1.5f
    }

    private fun withAlpha(color: Int, alpha: Int): Int = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun luminance(color: Int): Double {
        fun channel(value: Int): Double {
            val x = value / 255.0
            return if (x <= 0.04045) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(Color.red(color)) + 0.7152 * channel(Color.green(color)) + 0.0722 * channel(Color.blue(color))
    }

    private fun blend(a: Int, b: Int, f: Float): Int = Color.rgb(
        (Color.red(a) * (1 - f) + Color.red(b) * f).toInt(),
        (Color.green(a) * (1 - f) + Color.green(b) * f).toInt(),
        (Color.blue(a) * (1 - f) + Color.blue(b) * f).toInt()
    )
}
