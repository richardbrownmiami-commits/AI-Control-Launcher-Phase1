package com.aicontrol.launcher.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import com.aicontrol.launcher.actions.ActionEngine
import com.aicontrol.launcher.icons.IconPackManager
import com.aicontrol.launcher.icons.IconPackStore

@SuppressLint("SetTextI18n")
class LauncherSettingsActivity : Activity() {
    private lateinit var engine: ActionEngine
    private lateinit var packManager: IconPackManager
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
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        engine = ActionEngine(this)
        packManager = IconPackManager(this)
        UiTheme.bind(engine)
        build()
    }

    private fun <T> spinner(items: List<T>, selected: T): Spinner = Spinner(this).apply {
        adapter = ArrayAdapter(this@LauncherSettingsActivity, android.R.layout.simple_spinner_dropdown_item, items)
        setSelection(items.indexOf(selected).coerceAtLeast(0))
    }

    private fun build() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = UiTheme.background()
        }
        root.addView(TextView(this).apply {
            text = "Launcher settings"
            textSize = 27f
            setTextColor(UiTheme.textPrimary)
        })
        root.addView(TextView(this).apply {
            text = "Your home screen, app drawer, and appearance"
            textSize = 12f
            setTextColor(UiTheme.textMuted)
            setPadding(0, dp(4), 0, dp(10))
        })

        root.addView(section("Appearance & themes"))
        val themes = engine.availableThemes().sortedBy { it.lowercase() }
        themeSpinner = spinner(themes, themes.firstOrNull { it.equals(engine.theme(), true) } ?: themes.first())
        root.addView(row("Active theme", themeSpinner))
        styleSpinner = spinner(listOf("glass", "flat", "neon"), engine.style())
        root.addView(row("Card style", styleSpinner))
        root.addView(actionButton("Open theme gallery") {
            startActivity(Intent(this, ThemeGalleryActivity::class.java))
        })
        root.addView(actionButton("Wallpaper & asset library") {
            startActivity(Intent(this, AssetsActivity::class.java))
        })

        root.addView(section("Home screen grid"))
        rowsSpinner = spinner((3..8).toList(), engine.homeRows())
        columnsSpinner = spinner((3..7).toList(), engine.homeColumns())
        iconSizeSpinner = spinner(listOf(36, 44, 48, 56, 64, 72), engine.iconSize())
        root.addView(row("Rows per page", rowsSpinner, " rows"))
        root.addView(row("Columns per page", columnsSpinner, " columns"))
        root.addView(row("Shortcut icon size", iconSizeSpinner, " dp"))
        labels = checkbox("Show shortcut labels", engine.showLabels())
        root.addView(labels)
        val layoutNames = listOf("grid", "compact", "dense", "wide")
        layoutSpinner = spinner(layoutNames, engine.layout())
        root.addView(row("Grid preset", layoutSpinner))

        root.addView(section("Dock"))
        dockCountSpinner = spinner((3..6).toList(), engine.dockCount())
        root.addView(row("App shortcut slots", dockCountSpinner))
        dockVisible = checkbox("Show dock", engine.dockVisible())
        root.addView(dockVisible)

        root.addView(section("App drawer"))
        pageSpinner = spinner(listOf("Left", "Center", "Right"), listOf("Left", "Center", "Right")[engine.homePage()])
        root.addView(row("Startup home page", pageSpinner))
        sortSpinner = spinner(listOf("A–Z", "Z–A", "Package"), engine.drawerSort())
        root.addView(row("App sorting", sortSpinner))
        drawerSearch = checkbox("Show drawer search", engine.drawerSearchVisible())
        drawerLabels = checkbox("Show app labels in drawer", engine.drawerLabels())
        root.addView(drawerSearch)
        root.addView(drawerLabels)

        root.addView(section("Installed icon packs"))
        val packs = listOf(InstalledIconPackItem("", "System icons")) +
            packManager.installedPacks().map { InstalledIconPackItem(it.packageName, it.label) }
        packSpinner = spinner(packs.map { it.label }, packs.firstOrNull { it.packageName == engine.installedIconPack() }?.label ?: packs.first().label)
        root.addView(row("Icon pack", packSpinner))
        root.addView(TextView(this).apply {
            text = "Installed packs using Nova's documented com.novalauncher.THEME + res/xml/appfilter.xml format (or the compatible ADW theme action) can replace matching app icons. Private Nova backups and other proprietary formats are not supported. Packs are not downloaded or installed here."
            textSize = 11f
            setTextColor(UiTheme.textMuted)
            setPadding(dp(4), dp(6), dp(4), dp(8))
        })
        root.addView(actionButton("Find icon packs on Google Play") {
            IconPackStore.open(this).onFailure {
                Toast.makeText(this, it.message ?: "Could not open Google Play search", Toast.LENGTH_LONG).show()
            }
        })

        root.addView(section("Assistant"))
        root.addView(actionButton("AI provider settings") { startActivity(Intent(this, SettingsActivity::class.java)) })
        root.addView(TextView(this).apply {
            text = "The assistant uses the existing safe theme-plan flow. Provider setup is optional; the offline helper remains available."
            textSize = 11f
            setTextColor(UiTheme.textMuted)
            setPadding(dp(4), dp(6), dp(4), dp(8))
        })

        root.addView(Button(this).apply {
            text = "Save launcher settings"
            textSize = 15f
            setTextColor(Color.WHITE)
            background = UiTheme.gradient(22f)
            setOnClickListener {
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
                val selected = packs[packSpinner.selectedItemPosition]
                engine.setInstalledIconPack(selected.packageName)
                Toast.makeText(this@LauncherSettingsActivity, "Settings saved", Toast.LENGTH_SHORT).show()
                finish()
            }
        }.apply { layoutParams = LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(16) } })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun section(text: String) = TextView(this).apply {
        this.text = text.uppercase()
        textSize = 12f
        setTextColor(UiTheme.accent)
        setPadding(dp(2), dp(18), 0, dp(8))
    }

    private fun checkbox(textValue: String, checked: Boolean) = CheckBox(this).apply {
        text = textValue
        textSize = 14f
        setTextColor(UiTheme.textPrimary)
        isChecked = checked
        setPadding(dp(4), dp(3), dp(4), dp(3))
    }

    private fun row(label: String, control: android.view.View, suffix: String = "") =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@LauncherSettingsActivity).apply {
                text = label
                textSize = 14f
                setTextColor(UiTheme.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            addView(control, LinearLayout.LayoutParams(dp(150), dp(48)))
            if (suffix.isNotBlank()) addView(TextView(this@LauncherSettingsActivity).apply {
                text = suffix
                textSize = 11f
                setTextColor(UiTheme.textMuted)
            })
            setPadding(dp(12), dp(4), dp(6), dp(4))
            UiTheme.styleCard(this, UiTheme.card, true)
        }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 13f
        setTextColor(UiTheme.textPrimary)
        background = UiTheme.rounded(UiTheme.card, 18f, UiTheme.accent, 1)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(5) }
    }

    private data class InstalledIconPackItem(val packageName: String, val label: String)
}
