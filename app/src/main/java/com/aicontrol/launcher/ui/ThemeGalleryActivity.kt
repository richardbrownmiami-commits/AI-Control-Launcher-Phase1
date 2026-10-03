package com.aicontrol.launcher.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.aicontrol.launcher.actions.ActionEngine
import com.aicontrol.launcher.theme.ThemeSpec

@SuppressLint("SetTextI18n")
class ThemeGalleryActivity : Activity() {
    private lateinit var engine: ActionEngine
    private lateinit var list: LinearLayout
    private lateinit var screenRoot: LinearLayout
    private lateinit var undoButton: Button
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        engine = ActionEngine(this)
        UiTheme.bind(engine)
        build()
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) render()
    }

    private fun build() {
        screenRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(10))
            background = UiTheme.background()
        }
        val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        heading.addView(TextView(this).apply {
            text = "Theme gallery"
            textSize = 27f
            typeface = UiTheme.font()
            setTextColor(UiTheme.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        heading.addView(Button(this).apply {
            text = "Back"
            textSize = 11f
            setTextColor(UiTheme.textPrimary)
            background = UiTheme.rounded(UiTheme.card, 15f, UiTheme.accent, 1)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(62), dp(40)))
        screenRoot.addView(heading)
        screenRoot.addView(TextView(this).apply {
            text = "Ready-made presets and your saved themes. Preview the full home-screen style before applying; wallpaper and Android icon packs are managed separately."
            textSize = 12f
            typeface = UiTheme.font()
            setTextColor(UiTheme.textMuted)
            setPadding(0, dp(5), 0, dp(10))
        })
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        undoButton = Button(this).apply {
            text = "Undo last theme"
            setTextColor(UiTheme.textPrimary)
            background = UiTheme.rounded(UiTheme.card, 18f, UiTheme.accent, 1)
            setOnClickListener {
                if (engine.rollbackTheme()) {
                    UiTheme.bind(engine)
                    Toast.makeText(this@ThemeGalleryActivity, "Previous theme restored", Toast.LENGTH_SHORT).show()
                    render()
                }
            }
            visibility = if (engine.hasThemeRollback()) android.view.View.VISIBLE else android.view.View.GONE
        }
        toolbar.addView(undoButton, LinearLayout.LayoutParams(-2, dp(42)))
        toolbar.addView(Button(this).apply {
            text = "Wallpaper library"
            textSize = 10f
            setTextColor(UiTheme.textPrimary)
            background = UiTheme.rounded(UiTheme.card, 16f, UiTheme.accent, 1)
            setOnClickListener { startActivity(android.content.Intent(this@ThemeGalleryActivity, AssetsActivity::class.java)) }
        }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(7) })
        screenRoot.addView(toolbar)
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { clipToPadding = false; isFillViewport = false }
        scroll.addView(list)
        screenRoot.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(screenRoot)
        render()
    }

    private fun render() {
        if (!::list.isInitialized) return
        UiTheme.bind(engine)
        screenRoot.background = UiTheme.background()
        undoButton.visibility = if (engine.hasThemeRollback()) android.view.View.VISIBLE else android.view.View.GONE
        list.removeAllViews()
        val names = (ThemeSpec.builtInNames + engine.availableThemes()).distinctBy { it.lowercase() }
        list.addView(TextView(this).apply {
            text = "READY-MADE THEMES  ·  ${ThemeSpec.builtInNames.size} PRESETS"
            textSize = 10f
            typeface = UiTheme.font()
            setTextColor(UiTheme.textMuted)
            setPadding(dp(3), dp(11), 0, dp(6))
        })
        names.forEach { name ->
            val values = engine.themeValues(name)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(11))
                background = UiTheme.rounded(Color.parseColor(values.card), 20f, Color.parseColor(values.accent), 1)
            }
            val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            heading.addView(TextView(this).apply {
                text = name + if (name.equals(engine.theme(), true)) "  ·  ACTIVE" else ""
                textSize = 16f
                typeface = fontFor(values.typography)
                setTextColor(contrast(values.background))
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            heading.addView(TextView(this).apply {
                text = values.style.uppercase()
                textSize = 10f
                setTextColor(Color.parseColor(values.accent))
            })
            card.addView(heading)
            val swatches = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, dp(5)) }
            listOf(values.background, values.accent, values.accent2, values.card).forEach { color ->
                swatches.addView(android.view.View(this).apply {
                    background = UiTheme.rounded(Color.parseColor(color), dp(6).toFloat())
                    contentDescription = "Theme color $color"
                }, LinearLayout.LayoutParams(0, dp(30), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
            }
            card.addView(swatches)
            card.addView(TextView(this).apply {
                text = "${values.typography} type · ${values.iconStyle} icons · ${values.backgroundStyle} background"
                textSize = 11f
                typeface = fontFor(values.typography)
                setTextColor(contrast(values.background))
            })
            val sample = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(5), dp(7), dp(5), dp(2))
            }
            listOf("Home", "Apps", "Dock").forEachIndexed { index, label ->
                val fill = listOf(values.accent, values.accent2, values.card)[index]
                sample.addView(TextView(this).apply {
                    text = label
                    textSize = 10f
                    gravity = Gravity.CENTER
                    setTextColor(contrast(fill))
                    background = UiTheme.rounded(Color.parseColor(fill), 12f)
                }, LinearLayout.LayoutParams(0, dp(34), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
            }
            card.addView(sample)
            val previewButton = Button(this).apply {
                text = "Preview & apply"
                setTextColor(contrast(values.background))
                background = UiTheme.rounded(Color.parseColor(values.background), 14f, Color.parseColor(values.accent), 1)
                setOnClickListener { previewTheme(values) }
            }
            card.addView(previewButton, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(8) })
            list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) })
        }
    }

    private fun previewTheme(values: ThemeSpec.Values) {
        val background = Color.parseColor(values.background)
        val primary = contrast(values.background)
        val preview = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            this.background = UiTheme.rounded(background, 22f, Color.parseColor(values.accent), 1)
        }
        preview.addView(TextView(this).apply {
            text = "${values.name} home preview"
            textSize = 18f
            typeface = fontFor(values.typography)
            setTextColor(primary)
        })
        preview.addView(TextView(this).apply {
            text = "${values.typography} typography · ${values.iconStyle} icons · ${values.backgroundStyle} background"
            textSize = 11f
            typeface = fontFor(values.typography)
            setTextColor(primary)
            setPadding(0, dp(4), 0, dp(12))
        })
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        listOf("Apps", "Widget", "Dock").forEachIndexed { index, label ->
            val fill = listOf(values.accent, values.accent2, values.card)[index]
            row.addView(TextView(this).apply {
                text = label
                textSize = 11f
                typeface = fontFor(values.typography)
                gravity = Gravity.CENTER
                setTextColor(contrast(fill))
                this.background = UiTheme.rounded(Color.parseColor(fill), dp(12).toFloat())
            }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
        }
        preview.addView(row)
        AlertDialog.Builder(this)
            .setTitle("Review theme · ${values.name}")
            .setView(preview)
            .setMessage("The current theme stays active unless you choose Apply. You can restore the previous theme from this gallery.")
            .setNegativeButton("Keep current", null)
            .setPositiveButton("Apply theme") { _, _ ->
                runCatching {
                    engine.setTheme(values.name)
                    UiTheme.bind(engine)
                    Toast.makeText(this, "${values.name} applied", Toast.LENGTH_SHORT).show()
                    render()
                }.onFailure { Toast.makeText(this, it.message ?: "Could not apply theme", Toast.LENGTH_LONG).show() }
            }
            .show()
    }

    private fun fontFor(style: String) = when (style) {
        "serif" -> android.graphics.Typeface.create("serif", android.graphics.Typeface.NORMAL)
        "compact" -> android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL)
        else -> android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.NORMAL)
    }

    private fun contrast(hex: String): Int {
        val color = Color.parseColor(hex)
        val luminance = (0.2126 * Color.red(color) + 0.7152 * Color.green(color) + 0.0722 * Color.blue(color)) / 255.0
        return if (luminance > 0.58) Color.rgb(26, 30, 38) else Color.WHITE
    }
}
