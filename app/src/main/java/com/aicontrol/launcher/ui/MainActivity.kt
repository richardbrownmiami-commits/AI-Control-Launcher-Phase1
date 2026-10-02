package com.aicontrol.launcher.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.widget.*
import com.aicontrol.launcher.actions.ActionEngine
import com.aicontrol.launcher.apps.AppInfo
import com.aicontrol.launcher.apps.AppRepository
import com.aicontrol.launcher.assets.LauncherAssetManager
import com.aicontrol.launcher.icons.IconPackManager
import com.aicontrol.launcher.nlp.AppTarget
import com.aicontrol.launcher.nlp.LauncherCommand
import com.aicontrol.launcher.nlp.LocalPromptInterpreter
import com.aicontrol.launcher.nlp.PromptInterpretation
import java.util.Locale

private const val NATIVE_ABI_GUARD = "launcherabi"

class MainActivity : Activity() {
    private lateinit var apps: AppRepository
    private lateinit var engine: ActionEngine
    private lateinit var assets: LauncherAssetManager
    private lateinit var iconPacks: IconPackManager
    private lateinit var root: FrameLayout
    private lateinit var content: LinearLayout
    private lateinit var grid: GridLayout
    private lateinit var search: EditText
    private lateinit var commandInput: EditText
    private lateinit var dock: LinearLayout
    private var downY = 0f
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        runCatching { System.loadLibrary(NATIVE_ABI_GUARD) }
        apps = AppRepository(this)
        engine = ActionEngine(this)
        assets = LauncherAssetManager(this)
        iconPacks = IconPackManager(this)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) {
            UiTheme.bind(engine)
            render()
        }
    }

    private fun buildUi() {
        UiTheme.bind(engine)
        root = FrameLayout(this)

        val wallpaper = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            assets.activeWallpaper()?.let { setImageBitmap(BitmapFactory.decodeFile(it.absolutePath)) }
        }
        root.addView(wallpaper, FrameLayout.LayoutParams(-1, -1))

        val shade = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(150, Color.red(UiTheme.bg), Color.green(UiTheme.bg), Color.blue(UiTheme.bg)))
        }
        root.addView(shade, FrameLayout.LayoutParams(-1, -1))

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(18), dp(14), dp(12))
        }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = "AI Control Launcher"
            textSize = 20f
            setTextColor(UiTheme.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        top.addView(actionButton("Menu") {
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Launcher")
                .setItems(arrayOf("Customize launcher", "Assets", "AI connection settings (test only)")) { _, which ->
                    when (which) {
                        0 -> startActivity(Intent(this@MainActivity, LauncherSettingsActivity::class.java))
                        1 -> startActivity(Intent(this@MainActivity, AssetsActivity::class.java))
                        2 -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    }
                }
                .show()
        })
        content.addView(top, LinearLayout.LayoutParams(-1, dp(46)))

        content.addView(TextView(this).apply {
            text = "LOCAL COMMANDS · works offline, no API key needed"
            textSize = 10f
            setTextColor(UiTheme.accent)
            setPadding(dp(4), dp(8), 0, dp(4))
        })
        val commandRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        commandInput = EditText(this).apply {
            hint = "Create an Ocean theme or hide Calculator"
            setSingleLine()
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(UiTheme.textMuted)
            setPadding(dp(12), 0, dp(12), 0)
            background = UiTheme.rounded(UiTheme.card, 18f, UiTheme.accent, 1)
        }
        commandRow.addView(commandInput, LinearLayout.LayoutParams(0, dp(46), 1f))
        commandRow.addView(Button(this).apply {
            text = "Go"
            setTextColor(Color.WHITE)
            background = UiTheme.rounded(UiTheme.card, 18f, UiTheme.accent, 1)
            contentDescription = "Review local launcher command"
            setOnClickListener { handlePrompt() }
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(46)).apply { leftMargin = dp(7) }
        })
        content.addView(commandRow, LinearLayout.LayoutParams(-1, dp(46)))

        content.addView(TextView(this).apply {
            text = "HOME"
            textSize = 10f
            setTextColor(UiTheme.accent)
            setPadding(dp(4), dp(10), 0, dp(4))
        })

        content.addView(TextView(this).apply {
            text = java.text.SimpleDateFormat("EEE, d MMM  •  HH:mm", Locale.getDefault()).format(java.util.Date())
            textSize = 13f
            setTextColor(UiTheme.textMuted)
            setPadding(dp(4), 0, 0, dp(8))
        })

        search = EditText(this).apply {
            hint = "Search apps"
            setSingleLine()
            textSize = 15f
            setTextColor(Color.WHITE)
            setHintTextColor(UiTheme.textMuted)
            setPadding(dp(14), 0, dp(14), 0)
            background = UiTheme.rounded(UiTheme.card, 22f, UiTheme.accent, 1)
        }
        content.addView(search, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(10) })
        search.addTextChangedListener(SearchWatcher { renderApps() })

        val appScroll = ScrollView(this).apply {
            overScrollMode = ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        grid = GridLayout(this).apply {
            columnCount = columns(engine.layout())
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        appScroll.addView(grid)
        content.addView(appScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(5))
            UiTheme.styleCard(this, UiTheme.card, true)
        }
        content.addView(dock, LinearLayout.LayoutParams(-1, dp(68)).apply { topMargin = dp(8) })

        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) downY = event.y
            if (event.action == MotionEvent.ACTION_UP && downY - event.y > dp(100)) {
                search.requestFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }
            false
        }

        setContentView(root)
        render()
    }

    private fun handlePrompt() {
        val prompt = commandInput.text?.toString().orEmpty()
        val installed = apps.listLaunchableApps()
        val result = LocalPromptInterpreter.interpret(
            prompt,
            installed.map { AppTarget(it.label, it.packageName) },
            engine.availableThemes()
        )
        when (result) {
            is PromptInterpretation.Ready -> reviewPrompt(result, installed)
            is PromptInterpretation.Help -> AlertDialog.Builder(this)
                .setTitle("Local command help")
                .setMessage(result.message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            is PromptInterpretation.Clarification,
            is PromptInterpretation.Unrecognized -> {
                val message = when (result) {
                    is PromptInterpretation.Clarification -> result.message
                    is PromptInterpretation.Unrecognized -> result.message
                    else -> "Please revise the command."
                }
                AlertDialog.Builder(this)
                    .setTitle("Please clarify")
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    private fun reviewPrompt(result: PromptInterpretation.Ready, installed: List<AppInfo>) {
        val command = result.command
        val isLaunch = command is LauncherCommand.LaunchApp
        AlertDialog.Builder(this)
            .setTitle("Review command")
            .setMessage(result.preview)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (isLaunch) "Open" else "Apply") { _, _ ->
                try {
                    when (command) {
                        is LauncherCommand.ApplyTheme -> engine.setTheme(command.name)
                        is LauncherCommand.CreateTheme -> engine.applyJson(
                            org.json.JSONObject().put(
                                "actions",
                                org.json.JSONArray().put(
                                    org.json.JSONObject()
                                        .put("action", "CREATE_THEME")
                                        .put("name", command.values.name)
                                        .put("bg", command.values.background)
                                        .put("accent", command.values.accent)
                                        .put("accent2", command.values.accent2)
                                        .put("card", command.values.card)
                                        .put("style", command.values.style)
                                )
                            ).toString()
                        ).getOrThrow()
                        is LauncherCommand.SetLayout -> engine.setLayout(command.name)
                        is LauncherCommand.SetStyle -> engine.setStyle(command.name)
                        is LauncherCommand.SetAppVisible -> engine.setAppVisible(command.app.packageName, command.visible)
                        is LauncherCommand.LaunchApp -> {
                            val app = installed.firstOrNull { it.packageName == command.app.packageName }
                                ?: error("That app is no longer available")
                            apps.launch(app)
                        }
                    }
                    commandInput.text.clear()
                    render()
                    Toast.makeText(this, "Command applied", Toast.LENGTH_SHORT).show()
                } catch (error: Exception) {
                    Toast.makeText(this, error.message ?: "Unable to apply command", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    private fun render() {
        UiTheme.bind(engine)
        (root.getChildAt(0) as? ImageView)?.setImageBitmap(
            assets.activeWallpaper()?.let { BitmapFactory.decodeFile(it.absolutePath) }
        )
        (root.getChildAt(1) as? FrameLayout)?.setBackgroundColor(
            Color.argb(150, Color.red(UiTheme.bg), Color.green(UiTheme.bg), Color.blue(UiTheme.bg))
        )
        renderApps()
        renderDock()
    }

    private fun renderApps() {
        if (!::grid.isInitialized) return
        grid.removeAllViews()
        grid.columnCount = columns(engine.layout())
        val q = search.text?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()
        val hidden = engine.hiddenPackages()
        apps.listLaunchableApps()
            .filterNot { hidden.contains(it.packageName) }
            .filter { q.isEmpty() || it.label.lowercase(Locale.getDefault()).contains(q) }
            .forEach { addApp(it) }
    }

    private fun addApp(app: AppInfo) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(5), dp(7), dp(5), dp(5))
            UiTheme.styleCard(this, UiTheme.card, false)
            setOnClickListener { apps.launch(app) }
            setOnLongClickListener { showAppMenu(app); true }
        }
        val pack = engine.installedIconPack()
        val packIcon = if (pack.isNotBlank()) {
            iconPacks.iconDrawable(pack, app.packageName, app.activityName)
        } else null
        card.addView(ImageView(this).apply {
            setImageDrawable(assets.iconDrawable(app.packageName) ?: packIcon ?: app.icon)
            contentDescription = app.label
        }, LinearLayout.LayoutParams(dp(48), dp(48)))

        if (engine.showLabels()) {
            card.addView(TextView(this).apply {
                text = app.label
                textSize = 11f
                gravity = Gravity.CENTER
                setTextColor(UiTheme.textPrimary)
                maxLines = 2
            })
        }

        grid.addView(card, GridLayout.LayoutParams().apply {
            width = 0
            height = GridLayout.LayoutParams.WRAP_CONTENT
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            setMargins(dp(3), dp(3), dp(3), dp(3))
        })
    }

    private fun showAppMenu(app: AppInfo) {
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(arrayOf("Launch", "Add to dock", "Hide app")) { _, which ->
                when (which) {
                    0 -> apps.launch(app)
                    1 -> {
                        applyLauncherAction(
                            org.json.JSONObject()
                                .put("action", "ADD_SHORTCUT")
                                .put("label", app.label)
                                .put("package", app.packageName)
                                .put("activity", app.activityName)
                        )
                        renderDock()
                    }
                    2 -> {
                        applyLauncherAction(
                            org.json.JSONObject()
                                .put("action", "HIDE_APPS")
                                .put("packages", org.json.JSONArray().put(app.packageName))
                        )
                        renderApps()
                    }
                }
            }
            .show()
    }

    private fun applyLauncherAction(action: org.json.JSONObject) {
        engine.applyJson(
            org.json.JSONObject()
                .put("actions", org.json.JSONArray().put(action))
                .toString()
        )
    }

    private fun renderDock() {
        dock.removeAllViews()
        val allApps = apps.listLaunchableApps()
        val items = engine.shortcuts().take(engine.dockCount())
        items.forEach { item ->
            val pkg = item.optString("package")
            val app = allApps.firstOrNull { it.packageName == pkg } ?: return@forEach
            val pack = engine.installedIconPack()
            val packIcon = if (pack.isNotBlank()) {
                iconPacks.iconDrawable(pack, app.packageName, app.activityName)
            } else null
            dock.addView(ImageButton(this).apply {
                setImageDrawable(assets.iconDrawable(app.packageName) ?: packIcon ?: app.icon)
                contentDescription = item.optString("label", app.label)
                background = UiTheme.rounded(Color.TRANSPARENT, 16f)
                setOnClickListener { apps.launch(app) }
                setOnLongClickListener {
                    applyLauncherAction(
                        org.json.JSONObject()
                            .put("action", "REMOVE_SHORTCUT")
                            .put("label", item.optString("label"))
                            .put("package", pkg)
                    )
                    renderDock()
                    true
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f).apply {
                    leftMargin = dp(2)
                    rightMargin = dp(2)
                }
            })
        }
        if (items.size < engine.dockCount()) {
            dock.addView(TextView(this).apply {
                text = "+"
                textSize = 26f
                gravity = Gravity.CENTER
                setTextColor(UiTheme.accent)
                setOnClickListener {
                    Toast.makeText(
                        this@MainActivity,
                        "Long-press an app to add it to the dock",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f)
            })
        }
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 10f
        setTextColor(Color.WHITE)
        background = UiTheme.rounded(UiTheme.card, 18f, UiTheme.accent, 1)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(dp(82), dp(40)).apply { leftMargin = dp(4) }
    }

    private fun columns(layout: String) = when (layout.lowercase(Locale.getDefault())) {
        "dense", "compact" -> 5
        "wide" -> 3
        else -> 4
    }
}

private class SearchWatcher(private val changed: () -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed()
    override fun afterTextChanged(s: android.text.Editable?) = Unit
}
