package com.aicontrol.launcher.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import com.aicontrol.launcher.actions.ActionEngine
import com.aicontrol.launcher.theme.ThemeSpec

object UiTheme {
    var bg = Color.rgb(8, 10, 18); private set
    var card = Color.rgb(20, 23, 35); private set
    var card2 = Color.rgb(27, 29, 46); private set
    var accent = Color.rgb(70, 210, 255); private set
    var accent2 = Color.rgb(155, 92, 255); private set
    var typography = ThemeSpec.DEFAULT_TYPOGRAPHY; private set
    var iconStyle = ThemeSpec.DEFAULT_ICON_STYLE; private set
    var backgroundStyle = ThemeSpec.DEFAULT_BACKGROUND_STYLE; private set
    var textPrimary = Color.WHITE; private set
    var textMuted = Color.rgb(170, 178, 198); private set
    val success = Color.rgb(75, 220, 145)
    val warning = Color.rgb(255, 190, 75)
    val danger = Color.rgb(255, 88, 105)

    fun bind(engine: ActionEngine) {
        val t = engine.themeState()
        bg = t.bg; card = t.card; card2 = blend(t.card, t.bg, 0.35f); accent = t.accent; accent2 = t.accent2
        typography = t.typography; iconStyle = t.iconStyle; backgroundStyle = t.backgroundStyle
        val light = luminance(bg) > 0.56
        textPrimary = if (light) Color.rgb(24, 29, 38) else Color.WHITE
        textMuted = if (light) Color.rgb(83, 91, 104) else Color.rgb(170, 178, 198)
    }

    fun font(): Typeface = when (typography) {
        "compact" -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        "serif" -> Typeface.create("serif", Typeface.NORMAL)
        else -> Typeface.create("sans-serif", Typeface.NORMAL)
    }

    fun iconRadiusDp(size: Int): Float = when (iconStyle) {
        "circle" -> size / 2f
        "squircle" -> size * 0.28f
        else -> size * 0.20f
    }

    fun homeBackground(): GradientDrawable {
        if (backgroundStyle == "solid") return GradientDrawable().apply { setColor(bg); cornerRadius = 0f }
        val colors = when (backgroundStyle) {
            "aurora" -> intArrayOf(bg, blend(bg, accent2, 0.45f), blend(bg, accent, 0.82f))
            "warm" -> intArrayOf(bg, blend(bg, accent2, 0.36f), blend(bg, accent, 0.78f))
            else -> intArrayOf(bg, blend(bg, accent2, 0.82f))
        }
        return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply { cornerRadius = 0f }
    }

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

    fun rounded(color: Int, radiusDp: Float, strokeColor: Int? = null, strokeDp: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = radiusDp
        if (strokeColor != null && strokeDp > 0) setStroke(strokeDp, strokeColor)
    }

    fun gradient(radiusDp: Float = 24f) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR, intArrayOf(accent2, accent)
    ).apply { cornerRadius = radiusDp }

    fun background() = homeBackground()

    fun styleCard(view: View, color: Int = card, stroke: Boolean = false) {
        view.background = rounded(color, 24f, if (stroke) accent else null, if (stroke) 1 else 0)
        view.elevation = 6f
    }
}
