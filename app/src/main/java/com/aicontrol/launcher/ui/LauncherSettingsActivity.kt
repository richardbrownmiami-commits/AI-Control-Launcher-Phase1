package com.aicontrol.launcher.ui

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import com.aicontrol.launcher.actions.ActionEngine
import com.aicontrol.launcher.icons.IconPackManager

class LauncherSettingsActivity : Activity() {
    private lateinit var engine: ActionEngine
    private lateinit var packManager: IconPackManager
    private lateinit var packSpinner: Spinner
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        engine = ActionEngine(this)
        packManager = IconPackManager(this)
        UiTheme.bind(engine)
        build()
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
            text = "Lawnchair-style customization without the Lawnchair app dependency"
            textSize = 12f
            setTextColor(UiTheme.textMuted)
            setPadding(0, dp(4), 0, dp(18))
        })

        root.addView(section("Appearance"))
        val builtInThemes = listOf("default", "midnight", "ocean", "ember")
        val themeOptions = builtInThemes.filter { builtIn -> engine.availableThemes().any { it.equals(builtIn, true) } } +
            engine.availableThemes().filterNot { available -> builtInThemes.any { it.equals(available, true) } }.sorted()
        val theme = Spinner(this)
        theme.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            themeOptions)
        theme.setSelection(themeOptions.indexOfFirst { it.equals(engine.theme(), true) }.coerceAtLeast(0))
        root.addView(row("Theme", theme))

        val style = Spinner(this)
        style.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("glass", "flat", "neon"))
        style.setSelection(listOf("glass", "flat", "neon").indexOf(engine.style()).coerceAtLeast(0))
        root.addView(row("Card style", style))

        val layout = Spinner(this)
        layout.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("grid", "compact", "dense", "wide"))
        layout.setSelection(listOf("grid", "compact", "dense", "wide").indexOf(engine.layout()).coerceAtLeast(0))
        root.addView(row("App grid", layout))

        root.addView(section("Icons"))
        val packs = listOf(InstalledIconPackItem("", "System icons")) +
            packManager.installedPacks().map { InstalledIconPackItem(it.packageName, it.label) }
        packSpinner = Spinner(this)
        packSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, packs.map { it.label })
        val current = packs.indexOfFirst { it.packageName == engine.installedIconPack() }
        packSpinner.setSelection(if (current >= 0) current else 0)
        root.addView(row("Icon pack", packSpinner))

        root.addView(TextView(this).apply {
            text = "Installed icon packs are read through the standard ADW/Nova icon-pack interface. No icon-pack APK is modified or installed."
            textSize = 11f
            setTextColor(UiTheme.textMuted)
            setPadding(0, dp(8), 0, dp(12))
        })

        root.addView(section("Launcher behavior"))
        val labels = CheckBox(this).apply {
            text = "Show app labels"
            setTextColor(UiTheme.textPrimary)
            isChecked = engine.showLabels()
        }
        root.addView(labels)
        val dockCount = Spinner(this)
        dockCount.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            listOf("3", "4", "5", "6"))
        dockCount.setSelection((engine.dockCount() - 3).coerceIn(0, 3))
        root.addView(row("Dock slots", dockCount))

        root.addView(Button(this).apply {
            text = "Apply"
            textSize = 14f
            setTextColor(Color.WHITE)
            background = UiTheme.gradient(22f)
            setOnClickListener {
                engine.setTheme(theme.selectedItem.toString())
                engine.setStyle(style.selectedItem.toString())
                engine.setLayout(layout.selectedItem.toString())
                engine.setShowLabels(labels.isChecked)
                engine.setDockCount(dockCount.selectedItem.toString().toInt())
                val selected = packs[packSpinner.selectedItemPosition]
                engine.setInstalledIconPack(selected.packageName)
                Toast.makeText(this@LauncherSettingsActivity, "Launcher settings applied", Toast.LENGTH_SHORT).show()
                finish()
            }
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun section(text: String) = TextView(this).apply {
        this.text = text.uppercase()
        textSize = 12f
        setTextColor(UiTheme.accent)
        setPadding(0, dp(18), 0, dp(8))
    }

    private fun row(label: String, control: android.view.View) =
        LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(this@LauncherSettingsActivity).apply {
                text = label
                textSize = 14f
                setTextColor(UiTheme.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            addView(control, LinearLayout.LayoutParams(dp(190), dp(48)))
            setPadding(dp(12), dp(4), dp(6), dp(4))
            UiTheme.styleCard(this, UiTheme.card, true)
        }

    private data class InstalledIconPackItem(val packageName: String, val label: String)
}
