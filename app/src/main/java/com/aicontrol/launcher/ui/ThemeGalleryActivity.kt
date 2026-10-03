package com.aicontrol.launcher.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.aicontrol.launcher.actions.ActionEngine
import com.aicontrol.launcher.assets.AssetAttributionStore
import com.aicontrol.launcher.assets.AssetStore
import com.aicontrol.launcher.assets.ImageAssetValidation
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
            textSize = 21f
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
        }, LinearLayout.LayoutParams(dp(62), dp(48)))
        screenRoot.addView(heading)
        screenRoot.addView(TextView(this).apply {
            text = "Ready-made palettes and saved theme bundles. Preview layout, wallpaper, app icons, attribution and colors before applying."
            textSize = 11f
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
        toolbar.addView(undoButton, LinearLayout.LayoutParams(-2, dp(48)))
        toolbar.addView(Button(this).apply {
            text = "Wallpaper library"
            textSize = 10f
            setTextColor(UiTheme.textPrimary)
            background = UiTheme.rounded(UiTheme.card, 16f, UiTheme.accent, 1)
            setOnClickListener { startActivity(android.content.Intent(this@ThemeGalleryActivity, AssetsActivity::class.java)) }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(7) })
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
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(UiTheme.textMuted)
            letterSpacing = 0.06f
            setPadding(dp(3), dp(13), 0, dp(8))
        })
        names.forEach { name ->
            val values = engine.themeValues(name)
            val themeBg = Color.parseColor(values.background)
            val themeAccent = Color.parseColor(values.accent)
            val themeAccent2 = Color.parseColor(values.accent2)
            val themeCard = Color.parseColor(values.card)
            val isActive = name.equals(engine.theme(), true)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(13), dp(12), dp(13), dp(12))
                background = UiTheme.rounded(UiTheme.card, 20f, if (isActive) themeAccent else UiTheme.textMuted, 1)
                elevation = dp(1).toFloat()
            }
            val heading = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            heading.addView(TextView(this).apply {
                text = name
                textSize = 15f
                typeface = fontFor(values.typography)
                setTextColor(UiTheme.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            if (isActive) heading.addView(TextView(this).apply {
                text = "ACTIVE"
                textSize = 9f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setTextColor(themeAccent)
                background = UiTheme.rounded(UiTheme.card2, 12f)
                setPadding(dp(8), dp(5), dp(8), dp(5))
            })
            card.addView(heading)

            val preview = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(11), dp(10), dp(11), dp(10))
                background = UiTheme.rounded(themeBg, 17f)
            }
            val sampleTop = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            sampleTop.addView(TextView(this@ThemeGalleryActivity).apply {
                text = "9:41"
                textSize = 18f
                typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
                setTextColor(contrast(values.background))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            sampleTop.addView(TextView(this@ThemeGalleryActivity).apply {
                text = "●   ●   ●"
                textSize = 8f
                setTextColor(contrast(values.background))
            })
            preview.addView(sampleTop)
            val wallpaper = values.wallpaperAsset?.let { AssetStore(this).wallpaper(it) }
            val bitmap = wallpaper?.takeIf { it.isFile }?.let { ImageAssetValidation.decodeSampled(it, 800) }
            if (bitmap != null) preview.addView(ImageView(this).apply {
                setImageBitmap(bitmap)
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = "${name} wallpaper preview"
                clipToOutline = true
                background = UiTheme.rounded(themeCard, 12f)
            }, LinearLayout.LayoutParams(-1, dp(72)).apply { topMargin = dp(5) })
            val sample = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(7), dp(7), dp(7), dp(7))
                background = UiTheme.rounded(themeCard, 14f)
            }
            listOf("●", "●", "●", "●").forEachIndexed { index, dot ->
                sample.addView(TextView(this@ThemeGalleryActivity).apply {
                    text = dot
                    textSize = 15f
                    gravity = Gravity.CENTER
                    background = UiTheme.rounded(if (index % 2 == 0) themeAccent else themeAccent2, 14f)
                    setTextColor(contrast(if (index % 2 == 0) values.accent else values.accent2))
                }, LinearLayout.LayoutParams(dp(30), dp(30)).apply { rightMargin = dp(7) })
            }
            sample.addView(TextView(this@ThemeGalleryActivity).apply {
                text = "Apps     Home     Dock"
                textSize = 10f
                setTextColor(contrast(values.card))
            })
            preview.addView(sample, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
            card.addView(preview, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(9) })

            val swatches = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(4))
            }
            listOf(values.background, values.accent, values.accent2, values.card).forEach { color ->
                swatches.addView(View(this).apply {
                    setBackground(UiTheme.rounded(Color.parseColor(color), 7f))
                    setContentDescription("Theme color $color")
                }, LinearLayout.LayoutParams(0, dp(20), 1f).apply { leftMargin = dp(2); rightMargin = dp(2) })
            }
            card.addView(swatches)
            card.addView(TextView(this).apply {
                text = "${values.layout.replaceFirstChar { it.uppercase() }} layout · ${values.iconStyle} icons · ${values.iconAssets.size} custom app icons" +
                    (if (values.iconPackPackage != null) " · installed pack" else "")
                textSize = 10f
                typeface = fontFor(values.typography)
                setTextColor(UiTheme.textMuted)
                setPadding(dp(2), dp(2), dp(2), dp(7))
            })
            val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            actions.addView(Button(this).apply {
                text = "Preview & apply"
                textSize = 12f
                isAllCaps = false
                setTextColor(contrast(values.accent))
                background = UiTheme.rounded(themeAccent, 15f)
                setOnClickListener { previewTheme(values) }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
            if (engine.isCustomTheme(name)) actions.addView(Button(this).apply {
                text = "Edit assets"
                textSize = 11f
                isAllCaps = false
                setTextColor(UiTheme.textPrimary)
                background = UiTheme.rounded(UiTheme.card2, 15f)
                setOnClickListener {
                    startActivity(android.content.Intent(this@ThemeGalleryActivity, AssetsActivity::class.java)
                        .putExtra(AssetsActivity.EXTRA_THEME_NAME, name))
                }
            }, LinearLayout.LayoutParams(dp(92), dp(48)).apply { leftMargin = dp(7) })
            card.addView(actions)
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
        val wallpaper = values.wallpaperAsset?.let { AssetStore(this).wallpaper(it) }
        val wallpaperBitmap = wallpaper?.takeIf { it.isFile }?.let { ImageAssetValidation.decodeSampled(it, 1000) }
        if (wallpaperBitmap != null) preview.addView(ImageView(this).apply {
            setImageBitmap(wallpaperBitmap)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "${values.name} wallpaper preview"
        }, LinearLayout.LayoutParams(-1, dp(150)).apply { topMargin = dp(9) })
        preview.addView(TextView(this).apply {
            text = "${values.layout} layout · ${values.typography} typography · ${values.iconStyle} icons · ${values.backgroundStyle} background · ${values.iconAssets.size} mapped apps" +
                (values.iconPackPackage?.let { " · installed pack: $it" } ?: "")
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
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
        }
        preview.addView(row)
        val attributionStore = AssetAttributionStore(this)
        val attribution = buildList {
            values.wallpaperAsset?.let { attributionStore.forAsset("wallpaper", it)?.let(::add) }
            values.iconAssets.values.distinct().forEach { asset ->
                attributionStore.forAnyAsset(asset)?.let { record -> if (record !in this) add(record) }
            }
        }
        preview.addView(TextView(this).apply {
            text = if (attribution.isEmpty()) {
                "No remote attribution recorded. Unmapped apps keep their installed icons."
            } else attribution.joinToString("\n") { "${it.title} · ${it.creator} · ${it.license}\n${it.sourceUrl}" } +
                (if (attribution.any { it.title.contains("OpenMoji", true) }) "\nOpenMoji attribution: All emojis designed by OpenMoji – the open-source emoji and icon project. License: CC BY-SA 4.0." else "")
            textSize = 9f
            setTextColor(UiTheme.textMuted)
            setPadding(dp(4), dp(8), dp(4), dp(2))
        })
        val previewScroll = ScrollView(this).apply { addView(preview) }
        AlertDialog.Builder(this)
            .setTitle("Review theme · ${values.name}")
            .setView(previewScroll)
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
