package com.aicontrol.launcher.ui

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.widget.*
import com.aicontrol.launcher.actions.ActionEngine
import com.aicontrol.launcher.ai.OpenAiCompatibleProvider
import com.aicontrol.launcher.apps.AppInfo
import com.aicontrol.launcher.apps.AppRepository
import com.aicontrol.launcher.data.SettingsStore
import kotlinx.coroutines.*

class MainActivity : Activity() {
    private lateinit var settings: SettingsStore
    private lateinit var apps: AppRepository
    private lateinit var engine: ActionEngine
    private lateinit var root: LinearLayout
    private lateinit var grid: GridLayout
    private lateinit var search: EditText
    private lateinit var status: TextView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var downY = 0f

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsStore(this)
        apps = AppRepository(this)
        engine = ActionEngine(this)
        buildUi()
    }

    private fun buildUi() {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 18)
            setBackgroundColor(themeColor())
            setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> { downY = event.rawY; false }
                    MotionEvent.ACTION_UP -> {
                        val delta = event.rawY - downY
                        if (delta < -120) { search.requestFocus(); true } else false
                    }
                    else -> false
                }
            }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "AI Launcher"
            textSize = 23f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        header.addView(title)
        header.addView(Button(this).apply {
            text = "Theme"
            setOnClickListener {
                val next = if (engine.theme() == "default") "midnight" else "default"
                engine.applyJson("{\"actions\":[{\"action\":\"SET_THEME\",\"theme\":\"" + next + "\"}]}")
                root.setBackgroundColor(themeColor())
                status.text = "Theme: $next"
            }
        })
        root.addView(header)
        root.addView(TextView(this).apply {
            text = "Dash • " + engine.layout() + " layout • " + engine.hiddenPackages().size + " hidden"
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(4, 4, 4, 10)
        })
        search = EditText(this).apply {
            hint = "Search apps…"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }
        root.addView(search)
        search.addTextChangedListener(SimpleTextWatcher { renderApps() })
        root.addView(Button(this).apply {
            text = "Ask AI"
            setOnClickListener { askAi() }
        })
        status = TextView(this).apply {
            setTextColor(Color.LTGRAY)
            setPadding(4, 8, 4, 8)
        }
        root.addView(status)
        grid = GridLayout(this).apply {
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = true
            columnCount = columnsFor(engine.layout())
        }
        root.addView(ScrollView(this).apply { addView(grid) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        renderApps()
    }

    private fun renderApps() {
        if (!::grid.isInitialized) return
        grid.removeAllViews()
        grid.columnCount = columnsFor(engine.layout())
        val query = search.text?.toString()?.trim()?.lowercase() ?: ""
        val hidden = engine.hiddenPackages()
        apps.listLaunchableApps()
            .filterNot { hidden.contains(it.packageName) }
            .filter { query.isEmpty() || it.label.lowercase().contains(query) }
            .forEach { addApp(it) }
    }

    private fun addApp(app: AppInfo) {
        val cell = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(6, 8, 6, 8)
            setOnClickListener { apps.launch(app) }
        }
        val icon = ImageView(this).apply {
            setImageDrawable(app.icon)
            contentDescription = app.label
        }
        val size = (resources.displayMetrics.density * 52).toInt()
        cell.addView(icon, LinearLayout.LayoutParams(size, size))
        cell.addView(TextView(this).apply {
            text = app.label
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            maxLines = 2
        })
        grid.addView(cell, GridLayout.LayoutParams().apply {
            width = 0
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        })
    }

    private fun askAi() {
        val command = search.text?.toString()?.trim().orEmpty()
        if (command.isEmpty()) {
            status.text = "Type an AI request in Search, then tap Ask AI."
            return
        }
        status.text = "AI is planning…"
        scope.launch {
            val provider = OpenAiCompatibleProvider(settings.endpoint, settings.apiKey, settings.model)
            val state = "theme=" + engine.theme() + ", layout=" + engine.layout() + ", hidden=" + engine.hiddenPackages()
            provider.generatePlan(command, state)
                .onSuccess { json ->
                    engine.applyJson(json)
                        .onSuccess { n ->
                            status.text = "Applied $n action(s)"
                            root.setBackgroundColor(themeColor())
                            renderApps()
                        }
                        .onFailure { status.text = "Plan rejected: ${it.message}" }
                }
                .onFailure { status.text = "AI error: ${it.message}" }
        }
    }

    private fun columnsFor(layout: String): Int =
        when (layout.lowercase()) {
            "dense", "compact" -> 5
            "wide" -> 3
            else -> 4
        }

    private fun themeColor(): Int =
        if (engine.theme().equals("midnight", true)) Color.rgb(5, 8, 14) else Color.rgb(16, 18, 24)

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

private class SimpleTextWatcher(private val changed: () -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed()
    override fun afterTextChanged(s: android.text.Editable?) = Unit
}