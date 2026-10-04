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
import com.aicontrol.launcher.apps.AppRepository
import com.aicontrol.launcher.assets.AssetAttributionStore
import com.aicontrol.launcher.icons.IconPackCompatibility
import com.aicontrol.launcher.icons.IconPackManager
import com.aicontrol.launcher.icons.IconPackStore
import com.aicontrol.launcher.theme.ThemeSpec

@SuppressLint("SetTextI18n")
class ThemeGalleryActivity : Activity() {
    companion object { const val EXTRA_SELECT_ICON_PACK_FOR_THEME = "select_icon_pack_for_theme" }
    private lateinit var engine: ActionEngine
    private lateinit var iconPacks: IconPackManager
    private lateinit var previewAssets: ThemePreviewAssets
    private lateinit var list: LinearLayout
    private lateinit var screenRoot: LinearLayout
    private lateinit var undoButton: Button
    private var selectPackOnResume: String? = null
    private var installReturnThemeName: String? = null
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        engine = ActionEngine(this)
        iconPacks = IconPackManager(this)
        previewAssets = ThemePreviewAssets(this, engine, AppRepository(this))
        selectPackOnResume = intent.getStringExtra(EXTRA_SELECT_ICON_PACK_FOR_THEME)
        UiTheme.bind(engine)
        build()
    }

    override fun onResume() {
        super.onResume()
        if (::list.isInitialized) {
            iconPacks.clearCache()
            render()
            val requested = selectPackOnResume
            if (requested != null) {
                selectPackOnResume = null
                chooseThemeIconPack(requested)
                return
            }
            val waitingForInstall = installReturnThemeName
            if (waitingForInstall != null) {
                installReturnThemeName = null
                if (iconPacks.installedPacks().any { it.packageName == IconPackCompatibility.APPSTRACT_PACKAGE }) {
                    Toast.makeText(this, "Appstract detected. Choose it for this theme to apply its real app icons.", Toast.LENGTH_LONG).show()
                    chooseThemeIconPack(waitingForInstall)
                } else {
                    Toast.makeText(this, "Appstract is not installed yet. Return after the user-controlled F-Droid install, then choose the pack here.", Toast.LENGTH_LONG).show()
                }
            }
        }
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
        screenRoot.addView(Button(this).apply {
            text = "Asset sources & licenses"
            textSize = 10f
            setTextColor(UiTheme.textPrimary)
            background = UiTheme.rounded(UiTheme.card2, 14f)
            setOnClickListener { AssetSourcesDialog.show(this@ThemeGalleryActivity) }
        }, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(5) })
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
            text = "EXPLORE  ·  ${ThemeSpec.builtInNames.size} ORIGINAL PRESETS"
            textSize = 10f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(UiTheme.textMuted)
            letterSpacing = 0.06f
            setPadding(dp(3), dp(13), 0, dp(8))
        })
        val screenWidthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        val columns = if (screenWidthDp >= 380f && resources.configuration.fontScale < 1.25f) 2 else 1
        var galleryRow: LinearLayout? = null
        names.forEachIndexed { index, name ->
            if (index % columns == 0) {
                galleryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                list.addView(galleryRow, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(3) })
            }
            val values = runCatching { engine.themeValues(name) }.getOrElse { error ->
                list.addView(TextView(this).apply {
                    text = "$name could not be read: ${error.message ?: "saved bundle damaged"}"
                    textSize = 11f
                    setTextColor(UiTheme.textMuted)
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                })
                return@forEachIndexed
            }
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
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
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
                setPadding(dp(8), dp(8), dp(8), dp(8))
                background = UiTheme.rounded(themeBg, 17f)
            }
            val sampleTop = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            sampleTop.addView(TextView(this@ThemeGalleryActivity).apply {
                text = "9:41"
                textSize = 16f
                typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
                setTextColor(contrast(values.background))
            }, LinearLayout.LayoutParams(0, -2, 1f))
            sampleTop.addView(TextView(this@ThemeGalleryActivity).apply {
                text = "●   ●   ●"
                textSize = 8f
                setTextColor(contrast(values.background))
            })
            preview.addView(sampleTop)
            val wallpaperPreview = previewAssets.wallpaper(values, 62)
            if (wallpaperPreview != null) preview.addView(wallpaperPreview, LinearLayout.LayoutParams(-1, dp(62)).apply { topMargin = dp(5) })
            else if (values.wallpaperAsset != null) preview.addView(TextView(this).apply {
                text = "Wallpaper file unavailable · re-build this theme before applying"
                textSize = 9f
                setTextColor(UiTheme.textMuted)
            }, LinearLayout.LayoutParams(-1, dp(34)).apply { topMargin = dp(5) })
            val sample = LinearLayout(this).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(5), dp(5), dp(5), dp(5))
                background = UiTheme.rounded(themeCard, 14f)
            }
            sample.addView(previewAssets.iconStrip(values, maxIcons = 4, iconSizeDp = 25),
                LinearLayout.LayoutParams(-1, -2))
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
                text = "${values.layout.replaceFirstChar { it.uppercase() }} layout · ${values.iconStyle} shape · ${previewAssets.packStatus(values)}"
                textSize = 9f
                typeface = fontFor(values.typography)
                setTextColor(UiTheme.textMuted)
                maxLines = 2
                setPadding(dp(2), dp(2), dp(2), dp(5))
            })
            val actions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            actions.addView(Button(this).apply {
                text = "Preview"
                textSize = 11f
                isAllCaps = false
                setTextColor(contrast(values.accent))
                background = UiTheme.rounded(themeAccent, 15f)
                setOnClickListener { previewTheme(values) }
            }, LinearLayout.LayoutParams(-1, dp(44)))
            actions.addView(Button(this).apply {
                text = if (values.iconPackPackage.isNullOrBlank()) "Choose app icon pack" else "Change app icon pack"
                textSize = 10f
                isAllCaps = false
                setTextColor(UiTheme.textPrimary)
                background = UiTheme.rounded(UiTheme.card2, 15f)
                setOnClickListener { chooseThemeIconPack(name) }
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(5) })
            if (engine.isCustomTheme(name)) actions.addView(Button(this).apply {
                text = "Edit assets"
                textSize = 10f
                isAllCaps = false
                setTextColor(UiTheme.textPrimary)
                background = UiTheme.rounded(UiTheme.card2, 15f)
                setOnClickListener {
                    startActivity(android.content.Intent(this@ThemeGalleryActivity, AssetsActivity::class.java)
                        .putExtra(AssetsActivity.EXTRA_THEME_NAME, name))
                }
            }, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(5) })
            card.addView(actions)
            galleryRow?.addView(card, LinearLayout.LayoutParams(0, -2, 1f).apply {
                leftMargin = dp(4)
                rightMargin = dp(4)
                bottomMargin = dp(7)
            })
            if (columns == 2 && index == names.lastIndex && index % columns == 0) {
                galleryRow?.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f).apply {
                    leftMargin = dp(4)
                    rightMargin = dp(4)
                })
            }
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
        val wallpaperPreview = previewAssets.wallpaper(values, 150)
        if (wallpaperPreview != null) preview.addView(wallpaperPreview, LinearLayout.LayoutParams(-1, dp(150)).apply { topMargin = dp(9) })
        else if (values.wallpaperAsset != null) preview.addView(TextView(this).apply {
            text = "Wallpaper file unavailable. Rebuild the theme bundle before applying."
            setTextColor(UiTheme.textMuted)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7) })
        preview.addView(TextView(this).apply {
            text = "${values.layout} layout · ${values.typography} typography · ${values.iconStyle} shape · ${values.backgroundStyle} background · " +
                "${values.iconAssets.keys.count { !engine.isOpenMojiIllustration(values.name, it) }} custom image overrides · ${previewAssets.packStatus(values)}"
            textSize = 11f
            typeface = fontFor(values.typography)
            setTextColor(primary)
            setPadding(0, dp(4), 0, dp(12))
        })
        preview.addView(previewAssets.iconStrip(values, maxIcons = 6, iconSizeDp = 42),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        preview.addView(previewAssets.illustrationStrip(22),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
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
            values.iconPackPackage?.let { attributionStore.forAnyAsset(it)?.let(::add) }
            values.iconAssets.values.distinct().forEach { asset ->
                attributionStore.forAnyAsset(asset)?.let { record -> if (record !in this) add(record) }
            }
            attributionStore.forAnyAsset("openmoji_1f4f7.png")?.let { record -> if (record !in this) add(record) }
        }
        preview.addView(TextView(this).apply {
            text = if (attribution.isEmpty()) {
                "No remote attribution recorded. Real app icons come from the selected installed pack when mapped; otherwise the original installed app icons remain."
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

    private fun chooseThemeIconPack(themeName: String) {
        val packs = iconPacks.installedPacks()
        if (packs.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Install a real Android app icon pack")
                .setMessage("No compatible pack is installed. Appstract is an open-source Nova/ADW/Apex/Lawnchair icon pack. Open its official F-Droid page; Android/F-Droid controls installation. Return here afterwards and this theme's pack chooser will refresh.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Open Appstract on F-Droid") { _, _ ->
                    installReturnThemeName = themeName
                    IconPackStore.openAppstract(this).onFailure {
                        installReturnThemeName = null
                        Toast.makeText(this, it.message ?: "Could not open the F-Droid page", Toast.LENGTH_LONG).show()
                    }
                }
                .show()
            return
        }
        val options = listOf("Use system app icons") + packs.map { "${it.label} · ${it.packageName}" }
        val active = runCatching { engine.themeValues(themeName).iconPackPackage }.getOrNull()
            ?: engine.installedIconPack().takeIf { it.isNotBlank() }
        val preferred = active?.takeIf { value -> packs.any { it.packageName == value } }
            ?: packs.firstOrNull { it.packageName == IconPackCompatibility.APPSTRACT_PACKAGE }?.packageName
            ?: packs.first().packageName
        var selected = packs.indexOfFirst { it.packageName == preferred } + 1
        AlertDialog.Builder(this)
            .setTitle("App icon pack · $themeName")
            .setSingleChoiceItems(options.toTypedArray(), selected) { _, which -> selected = which }
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save for theme") { _, _ ->
                val packageName = if (selected == 0) null else packs.getOrNull(selected - 1)?.packageName
                runCatching {
                    engine.setThemeIconPack(themeName, packageName)
                    if (engine.theme().equals(themeName, ignoreCase = true)) engine.setTheme(themeName)
                    iconPacks.clearCache()
                    UiTheme.bind(engine)
                    render()
                    Toast.makeText(
                        this,
                        if (packageName == null) "System app icons selected for $themeName" else "${packs.first { it.packageName == packageName }.label} saved for $themeName",
                        Toast.LENGTH_LONG
                    ).show()
                }.onFailure { Toast.makeText(this, it.message ?: "Could not save the icon-pack selection", Toast.LENGTH_LONG).show() }
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
