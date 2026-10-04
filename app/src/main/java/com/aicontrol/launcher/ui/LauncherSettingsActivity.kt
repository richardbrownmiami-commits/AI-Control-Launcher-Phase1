package com.aicontrol.launcher.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.aicontrol.launcher.actions.ActionEngine
import com.aicontrol.launcher.icons.IconPackManager
import com.aicontrol.launcher.icons.IconPackStore

@SuppressLint("SetTextI18n")
class LauncherSettingsActivity : Activity() {
    private lateinit var engine: ActionEngine
    private lateinit var packManager: IconPackManager
    private var installedPackOptions: List<InstalledIconPackItem> = emptyList()
    private lateinit var themeSpinner: Spinner
    private lateinit var styleSpinner: Spinner
    private lateinit var layoutSpinner: Spinner
    private lateinit var packSpinner: Spinner
    private lateinit var rowsSpinner: Spinner
    private lateinit var columnsSpinner: Spinner
    private lateinit var iconSizeSpinner: Spinner
    private lateinit var dockCountSpinner: Spinner
    private lateinit var pageSpinner: Spinner
    private lateinit var sortSpinner: Spinner
    private lateinit var labels: CheckBox
    private lateinit var dockVisible: CheckBox
    private lateinit var drawerSearch: CheckBox
    private lateinit var drawerLabels: CheckBox
    private val categoryTargets = linkedMapOf<String, Int>()
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        engine = ActionEngine(this)
        packManager = IconPackManager(this)
        UiTheme.bind(engine)
        build()
    }

    override fun onResume() {
        super.onResume()
        if (::packSpinner.isInitialized) refreshInstalledPackSpinner()
    }

    private fun <T> spinner(items: List<T>, selected: T): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@LauncherSettingsActivity, android.R.layout.simple_spinner_dropdown_item, items)
        setSelection(items.indexOf(selected).coerceAtLeast(0))
        background = UiTheme.rounded(UiTheme.card2, 13f)
        setPadding(dp(7), 0, dp(7), 0)
    }

    private fun build() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(10), dp(16), dp(8))
            background = UiTheme.background()
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(Button(this).apply {
            text = "‹"
            textSize = 25f
            minWidth = 0
            minHeight = 0
            setTextColor(UiTheme.textPrimary)
            background = UiTheme.rounded(UiTheme.card2, 16f)
            contentDescription = "Back to home"
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(TextView(this@LauncherSettingsActivity).apply {
                text = "Launcher settings"
                textSize = 20f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setTextColor(UiTheme.textPrimary)
            })
            addView(TextView(this@LauncherSettingsActivity).apply {
                text = "Make your home screen feel like yours"
                textSize = 11f
                setTextColor(UiTheme.textMuted)
            })
        })
        root.addView(header, LinearLayout.LayoutParams(-1, dp(52)))

        val scroll = ScrollView(this).apply { clipToPadding = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(12))
        }
        val themes = engine.availableThemes().sortedBy { it.lowercase() }
        themeSpinner = spinner(themes, themes.firstOrNull { it.equals(engine.theme(), true) } ?: themes.first())
        styleSpinner = spinner(listOf("glass", "flat", "neon"), engine.style())
        layoutSpinner = spinner(listOf("grid", "compact", "dense", "wide"), engine.layout())
        content.addView(sectionCard("Appearance", "Theme, wallpaper, and visual style", "◈").apply {
            addView(row("Active theme", themeSpinner))
            addView(row("Card style", styleSpinner))
            addView(actionButton("Browse theme gallery") {
                startActivity(Intent(this@LauncherSettingsActivity, ThemeGalleryActivity::class.java))
            })
            addView(actionButton("Wallpaper & asset library") {
                startActivity(Intent(this@LauncherSettingsActivity, AssetsActivity::class.java))
            })
        })

        rowsSpinner = spinner((3..8).toList(), engine.homeRows())
        columnsSpinner = spinner((3..7).toList(), engine.homeColumns())
        iconSizeSpinner = spinner(listOf(36, 44, 48, 56, 64, 72), engine.iconSize())
        labels = checkbox("Show app names under icons", engine.showLabels())
        content.addView(sectionCard("Home screen", "Choose how apps fit on each page", "⌂").apply {
            addView(row("Rows per page", rowsSpinner, "rows"))
            addView(row("Columns per page", columnsSpinner, "columns"))
            addView(row("Shortcut icon size", iconSizeSpinner, "dp"))
            addView(labels)
            addView(row("Grid preset", layoutSpinner))
        })

        dockCountSpinner = spinner((3..6).toList(), engine.dockCount())
        dockVisible = checkbox("Show the dock", engine.dockVisible())
        content.addView(sectionCard("Dock", "Quick access to your most-used apps", "▤").apply {
            addView(row("App shortcut slots", dockCountSpinner))
            addView(dockVisible)
        })

        pageSpinner = spinner(listOf("Left", "Center", "Right"), listOf("Left", "Center", "Right")[engine.homePage()])
        sortSpinner = spinner(listOf("A–Z", "Z–A", "Package"), engine.drawerSort())
        drawerSearch = checkbox("Show search in the app drawer", engine.drawerSearchVisible())
        drawerLabels = checkbox("Show app names in the drawer", engine.drawerLabels())
        content.addView(sectionCard("App drawer", "Find and launch installed apps", "▦").apply {
            addView(row("Start on page", pageSpinner))
            addView(row("App sorting", sortSpinner))
            addView(drawerSearch)
            addView(drawerLabels)
        })

        installedPackOptions = currentPackOptions()
        packSpinner = spinner(installedPackOptions.map { it.label }, installedPackOptions.firstOrNull { it.packageName == engine.installedIconPack() }?.label ?: installedPackOptions.first().label)
        content.addView(sectionCard("Icon packs", "Use compatible packs already installed on Android", "✦").apply {
            addView(row("Active icon pack", packSpinner))
            addView(TextView(this@LauncherSettingsActivity).apply {
                text = "Nova, ADW, Apex and Lawnchair appfilter mappings are supported. Appstract is installed by the user through F-Droid; this launcher never downloads or installs APKs."
                textSize = 11f
                setTextColor(UiTheme.textMuted)
                setPadding(dp(4), dp(4), dp(4), dp(4))
            })
            addView(actionButton("Appstract open-source pack on F-Droid") {
                IconPackStore.openAppstract(this@LauncherSettingsActivity).onFailure {
                    Toast.makeText(this@LauncherSettingsActivity, it.message ?: "Could not open the F-Droid page", Toast.LENGTH_LONG).show()
                }
            })
        })

        content.addView(sectionCard("Assistant", "AI is optional; offline theme tools remain available", "✧").apply {
            addView(actionButton("AI provider settings") {
                startActivity(Intent(this@LauncherSettingsActivity, SettingsActivity::class.java))
            })
        })
        scroll.addView(content)
        val categoryBar = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding(dp(12), dp(6), dp(12), dp(8))
        }
        categoryBar.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            listOf("Appearance", "Home screen", "Dock", "App drawer", "Icon packs", "Assistant").forEach { label ->
                addView(Button(this@LauncherSettingsActivity).apply {
                    text = label
                    textSize = 11f
                    isAllCaps = false
                    minWidth = 0
                    minHeight = 0
                    setPadding(dp(12), 0, dp(12), 0)
                    setTextColor(UiTheme.textPrimary)
                    background = UiTheme.rounded(UiTheme.card2, 16f)
                    contentDescription = "Jump to $label settings"
                    setOnClickListener {
                        val targetId = categoryTargets[label] ?: return@setOnClickListener
                        val target = findViewById<View>(targetId)
                        scroll.post { scroll.smoothScrollTo(0, target.top) }
                    }
                }, LinearLayout.LayoutParams(-2, dp(44)).apply { rightMargin = dp(6) })
            }
        })
        root.addView(categoryBar, LinearLayout.LayoutParams(-1, dp(56)))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply {
            text = "Save changes"
            textSize = 14f
            isAllCaps = false
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(UiTheme.textOnAccent)
            background = UiTheme.gradient(18f)
            setOnClickListener { saveSettings() }
        }, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(8) })
        setContentView(root)
    }

    private fun sectionCard(title: String, subtitle: String, glyph: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(13))
        UiTheme.styleCard(this, UiTheme.card, false)
        id = View.generateViewId()
        categoryTargets[title] = id
        val heading = LinearLayout(this@LauncherSettingsActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@LauncherSettingsActivity).apply {
                text = glyph
                textSize = 18f
                gravity = Gravity.CENTER
                setTextColor(UiTheme.accent)
                background = UiTheme.rounded(UiTheme.card2, 13f)
            }, LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(11) })
            addView(LinearLayout(this@LauncherSettingsActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@LauncherSettingsActivity).apply {
                    text = title
                    textSize = 15f
                    typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                    setTextColor(UiTheme.textPrimary)
                })
                addView(TextView(this@LauncherSettingsActivity).apply {
                    text = subtitle
                    textSize = 11f
                    setTextColor(UiTheme.textMuted)
                    maxLines = 2
                })
            })
        }
        addView(heading, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(9) }
    }

    private fun checkbox(textValue: String, checked: Boolean) = CheckBox(this).apply {
        text = textValue
        textSize = 13f
        setTextColor(UiTheme.textPrimary)
        buttonTintList = ColorStateList.valueOf(UiTheme.accent)
        isChecked = checked
        setPadding(dp(3), dp(2), dp(3), dp(2))
        minHeight = dp(48)
    }

    private fun row(label: String, control: android.view.View, suffix: String = "") =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@LauncherSettingsActivity).apply {
                text = label
                textSize = 12f
                setTextColor(UiTheme.textPrimary)
                maxLines = 2
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            addView(control, LinearLayout.LayoutParams(dp(126), dp(48)))
            if (suffix.isNotBlank()) addView(TextView(this@LauncherSettingsActivity).apply {
                text = suffix
                textSize = 10f
                setTextColor(UiTheme.textMuted)
                setPadding(dp(4), 0, 0, 0)
            })
            setPadding(dp(2), dp(2), dp(2), dp(2))
        }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 12f
        isAllCaps = false
        setTextColor(UiTheme.textPrimary)
        background = UiTheme.rounded(UiTheme.card2, 15f)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(4) }
    }

    private fun currentPackOptions() = listOf(InstalledIconPackItem("", "System icons")) +
        packManager.installedPacks().map { InstalledIconPackItem(it.packageName, it.label) }

    private fun refreshInstalledPackSpinner() {
        packManager.clearCache()
        val previous = installedPackOptions.getOrNull(packSpinner.selectedItemPosition)?.packageName
        installedPackOptions = currentPackOptions()
        packSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, installedPackOptions.map { it.label })
        val wanted = previous?.takeIf { value -> installedPackOptions.any { it.packageName == value } } ?: engine.installedIconPack()
        packSpinner.setSelection(installedPackOptions.indexOfFirst { it.packageName == wanted }.coerceAtLeast(0))
    }

    private fun saveSettings() {
        engine.setTheme(themeSpinner.selectedItem.toString())
        engine.setStyle(styleSpinner.selectedItem.toString())
        engine.setLayout(layoutSpinner.selectedItem.toString())
        engine.setHomeRows(rowsSpinner.selectedItem.toString().toInt())
        engine.setHomeColumns(columnsSpinner.selectedItem.toString().toInt())
        engine.setIconSize(iconSizeSpinner.selectedItem.toString().toInt())
        engine.setShowLabels(labels.isChecked)
        engine.setDockCount(dockCountSpinner.selectedItem.toString().toInt())
        engine.setDockVisible(dockVisible.isChecked)
        engine.setHomePage(listOf("Left", "Center", "Right").indexOf(pageSpinner.selectedItem.toString()))
        engine.setDrawerSort(sortSpinner.selectedItem.toString())
        engine.setDrawerSearchVisible(drawerSearch.isChecked)
        engine.setDrawerLabels(drawerLabels.isChecked)
        engine.setInstalledIconPack(installedPackOptions[packSpinner.selectedItemPosition].packageName)
        Toast.makeText(this, "Settings saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private data class InstalledIconPackItem(val packageName: String, val label: String)
}
