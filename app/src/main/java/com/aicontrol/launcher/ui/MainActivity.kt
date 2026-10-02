package com.aicontrol.launcher.ui

import android.app.Activity
import android.app.AlertDialog
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import com.aicontrol.launcher.actions.ActionEngine
import com.aicontrol.launcher.apps.AppInfo
import com.aicontrol.launcher.apps.AppRepository
import com.aicontrol.launcher.assets.LauncherAssetManager
import com.aicontrol.launcher.assets.ImageAssetValidation
import com.aicontrol.launcher.ai.AiLauncherAction
import com.aicontrol.launcher.ai.AiPlanConfirmationGate
import com.aicontrol.launcher.ai.AiPlanDecision
import com.aicontrol.launcher.ai.AiPlanValidator
import com.aicontrol.launcher.ai.AiThemeOperation
import com.aicontrol.launcher.ai.AiTurn
import com.aicontrol.launcher.ai.createProvider
import com.aicontrol.launcher.data.SettingsStore
import com.aicontrol.launcher.icons.IconPackManager
import com.aicontrol.launcher.nlp.AppTarget
import com.aicontrol.launcher.nlp.LauncherCommand
import com.aicontrol.launcher.nlp.LocalPromptInterpreter
import com.aicontrol.launcher.nlp.PromptInterpretation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private const val NATIVE_ABI_GUARD = "launcherabi"
private const val WIDGET_HOST_ID = 7421
private const val REQUEST_PICK_WIDGET = 6301
private const val REQUEST_CONFIGURE_WIDGET = 6302

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
    private lateinit var widgetManager: AppWidgetManager
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var widgetContainer: LinearLayout
    private lateinit var assistantCaption: TextView
    private val aiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val aiHistory = mutableListOf<AiTurn>()
    private val aiConfirmation = AiPlanConfirmationGate()
    private var aiBusy = false
    private var pendingWidgetId = -1
    private var downY = 0f
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        runCatching { System.loadLibrary(NATIVE_ABI_GUARD) }
        apps = AppRepository(this)
        engine = ActionEngine(this)
        assets = LauncherAssetManager(this)
        iconPacks = IconPackManager(this)
        widgetManager = getSystemService(AppWidgetManager::class.java)
        widgetHost = AppWidgetHost(this, WIDGET_HOST_ID)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) {
            runCatching { widgetHost.startListening() }
            UiTheme.bind(engine)
            updateAssistantCaption()
            render()
        }
    }

    override fun onPause() {
        if (::widgetHost.isInitialized) runCatching { widgetHost.stopListening() }
        super.onPause()
    }

    override fun onDestroy() {
        aiScope.cancel()
        super.onDestroy()
    }

    private fun buildUi() {
        UiTheme.bind(engine)
        root = FrameLayout(this)

        val wallpaper = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            assets.activeWallpaper()?.let { setImageBitmap(ImageAssetValidation.decodeSampled(it, 2048)) }
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
                .setItems(arrayOf("Customize launcher", "Assets", "Add Android widget", "AI provider settings")) { _, which ->
                    when (which) {
                        0 -> startActivity(Intent(this@MainActivity, LauncherSettingsActivity::class.java))
                        1 -> startActivity(Intent(this@MainActivity, AssetsActivity::class.java))
                        2 -> addAndroidWidget()
                        3 -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                    }
                }
                .show()
        })
        content.addView(top, LinearLayout.LayoutParams(-1, dp(46)))

        assistantCaption = TextView(this).apply {
            textSize = 10f
            setTextColor(UiTheme.accent)
            setPadding(dp(4), dp(8), 0, dp(4))
        }
        content.addView(assistantCaption)
        content.addView(TextView(this).apply {
            text = "Configured AI sends your prompt, recent chat, and appearance settings to the selected provider. Local images are never uploaded. Asset searches go to Wikimedia only after confirmation."
            textSize = 10f
            setTextColor(UiTheme.textMuted)
            setPadding(dp(4), 0, dp(4), dp(6))
        })
        val commandRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        commandInput = EditText(this).apply {
            hint = "Ask about a theme, wallpaper search, or widget"
            setSingleLine()
            textSize = 14f
            setTextColor(Color.WHITE)
            setHintTextColor(UiTheme.textMuted)
            setPadding(dp(12), 0, dp(12), 0)
            background = UiTheme.rounded(UiTheme.card, 18f, UiTheme.accent, 1)
        }
        commandRow.addView(commandInput, LinearLayout.LayoutParams(0, dp(46), 1f))
        commandRow.addView(Button(this).apply {
            text = "Send"
            setTextColor(Color.WHITE)
            background = UiTheme.rounded(UiTheme.card, 18f, UiTheme.accent, 1)
            contentDescription = "Ask the AI assistant or use the offline helper"
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

        val widgetHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        widgetHeader.addView(TextView(this).apply {
            text = "WIDGETS"
            textSize = 10f
            setTextColor(UiTheme.accent)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        widgetHeader.addView(actionButton("+ Add") { addAndroidWidget() })
        content.addView(widgetHeader, LinearLayout.LayoutParams(-1, dp(38)))
        widgetContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(widgetContainer, LinearLayout.LayoutParams(-1, -2))

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

        root.setOnClickListener {
            search.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(search, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        root.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_DOWN) downY = event.y
            if (event.action == MotionEvent.ACTION_UP && downY - event.y > dp(100)) {
                view.performClick()
                true
            } else false
        }

        setContentView(root)
        render()
    }

    private fun updateAssistantCaption() {
        if (!::assistantCaption.isInitialized) return
        val settings = SettingsStore(this)
        assistantCaption.text = if (settings.activeKey().isBlank()) {
            "OFFLINE HELPER · no provider key configured"
        } else {
            "AI ASSISTANT · ${settings.provider} / ${settings.model}"
        }
    }

    private fun handlePrompt() {
        val prompt = commandInput.text?.toString().orEmpty().trim()
        if (prompt.isBlank()) {
            Toast.makeText(this, "Enter a message first", Toast.LENGTH_SHORT).show()
            return
        }
        if (prompt.length > 2_000) {
            Toast.makeText(this, "Please keep a message under 2,000 characters", Toast.LENGTH_LONG).show()
            return
        }
        val settings = SettingsStore(this)
        if (settings.activeKey().isBlank()) {
            runOfflinePrompt(prompt)
            return
        }
        if (aiBusy) return
        val provider = try {
            createProvider(settings)
        } catch (error: Exception) {
            showAiUnavailable(prompt, error)
            return
        }
        aiBusy = true
        updateAssistantCaption()
        val state = "Current appearance: theme=${engine.theme()}, layout=${engine.layout()}, style=${engine.style()}. " +
            "Available themes: ${engine.availableThemes().sorted().joinToString(", ")}."
        Toast.makeText(this, "Contacting ${settings.provider}…", Toast.LENGTH_SHORT).show()
        aiScope.launch {
            try {
                val reply = withContext(Dispatchers.IO) {
                    provider.chat(prompt, state, aiHistory.toList()).getOrThrow()
                }
                val decision = try {
                    AiPlanValidator.parse(reply.text, engine.availableThemes())
                } catch (error: Exception) {
                    showAiValidationFailure(reply.text, error)
                    return@launch
                }
                appendAiHistory(AiTurn("user", prompt))
                appendAiHistory(AiTurn("assistant", reply.text))
                when (decision) {
                    is AiPlanDecision.Conversation -> showAssistantMessage(decision.response)
                    is AiPlanDecision.Clarification -> showAssistantClarification(decision.response, decision.question)
                    is AiPlanDecision.Review -> reviewAiPlan(decision.plan)
                }
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                showAiUnavailable(prompt, error)
            } finally {
                aiBusy = false
                updateAssistantCaption()
            }
        }
    }

    private fun appendAiHistory(turn: AiTurn) {
        aiHistory += turn
        while (aiHistory.size > 8) aiHistory.removeAt(0)
    }

    private fun runOfflinePrompt(prompt: String) {
        val installed = apps.listLaunchableApps()
        val result = LocalPromptInterpreter.interpret(
            prompt,
            installed.map { AppTarget(it.label, it.packageName) },
            engine.availableThemes()
        )
        when (result) {
            is PromptInterpretation.Ready -> reviewPrompt(result, installed)
            is PromptInterpretation.Help -> AlertDialog.Builder(this)
                .setTitle("Offline helper")
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
                    .setTitle("Offline helper · please clarify")
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    private fun showAiUnavailable(prompt: String, error: Exception) {
        AlertDialog.Builder(this)
            .setTitle("AI provider unavailable")
            .setMessage("No launcher changes were made. ${error.message ?: "The request could not be completed."}\n\nYou can configure a provider in AI Settings or try the deterministic offline helper.")
            .setNegativeButton("Close", null)
            .setPositiveButton("Use offline helper") { _, _ -> runOfflinePrompt(prompt) }
            .show()
    }

    private fun showAiValidationFailure(rawResponse: String, error: Exception) {
        val safePreview = rawResponse.trim().take(1_200)
        AlertDialog.Builder(this)
            .setTitle("AI response not accepted")
            .setMessage("Nothing was changed because the response did not match the supported plan schema. ${error.message ?: "Please try again."}\n\nModel response:\n$safePreview")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showAssistantMessage(message: String) {
        AlertDialog.Builder(this)
            .setTitle("AI assistant")
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showAssistantClarification(response: String, question: String) {
        AlertDialog.Builder(this)
            .setTitle("Please clarify")
            .setMessage("$response\n\n$question\n\nReply in the assistant field to continue this conversation.")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun reviewAiPlan(plan: com.aicontrol.launcher.ai.AiLauncherPlan) {
        aiConfirmation.stage(plan)
        val details = mutableListOf<String>()
        when (val theme = plan.theme) {
            is AiThemeOperation.Create -> details += "Create theme ‘${theme.values.name}’ (${theme.values.background}, accent ${theme.values.accent}, ${theme.values.style} style)."
            is AiThemeOperation.Apply -> details += "Apply the ‘${theme.name}’ theme."
            null -> Unit
        }
        plan.actions.forEach { action ->
            details += when (action) {
                is AiLauncherAction.SearchAssets -> "Search Wikimedia Commons for ‘${action.query}’. The search phrase will be sent only after confirmation; each download needs a separate confirmation."
                AiLauncherAction.AddWidget -> "Open Android’s widget picker so you can choose an installed widget."
                is AiLauncherAction.SetLayout -> "Set launcher layout to ‘${action.value}’."
                is AiLauncherAction.SetStyle -> "Set card style to ‘${action.value}’."
            }
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(12))
            addView(TextView(this@MainActivity).apply {
                text = "Model response\n${plan.response}"
                textSize = 15f
                setTextColor(UiTheme.textPrimary)
                setPadding(0, 0, 0, dp(12))
            })
            if (plan.theme is AiThemeOperation.Create) {
                addView(themePreview((plan.theme as AiThemeOperation.Create).values))
            }
            addView(TextView(this@MainActivity).apply {
                text = "Plan to confirm\n• " + details.joinToString("\n• ")
                textSize = 13f
                setTextColor(UiTheme.textPrimary)
                setPadding(0, dp(12), 0, 0)
            })
        }
        val continuation = plan.actions.any { it is AiLauncherAction.SearchAssets || it === AiLauncherAction.AddWidget }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Review AI plan")
            .setView(ScrollView(this).apply { addView(body) })
            .setNegativeButton("Cancel") { _, _ -> aiConfirmation.cancel() }
            .setPositiveButton(if (continuation) "Confirm & continue" else "Confirm & apply") { _, _ ->
                val confirmed = aiConfirmation.confirm() ?: return@setPositiveButton
                try {
                    executeAiPlan(confirmed)
                } catch (error: Exception) {
                    Toast.makeText(this, error.message ?: "Unable to apply the confirmed plan", Toast.LENGTH_LONG).show()
                }
            }
            .setOnDismissListener { aiConfirmation.cancel() }
            .create()
        dialog.show()
    }

    private fun executeAiPlan(plan: com.aicontrol.launcher.ai.AiLauncherPlan) {
        when (val theme = plan.theme) {
            is AiThemeOperation.Create -> saveTheme(theme.values)
            is AiThemeOperation.Apply -> engine.setTheme(theme.name)
            null -> Unit
        }
        var assetQuery: String? = null
        var chooseWidget = false
        plan.actions.forEach { action ->
            when (action) {
                is AiLauncherAction.SearchAssets -> assetQuery = action.query
                AiLauncherAction.AddWidget -> chooseWidget = true
                is AiLauncherAction.SetLayout -> engine.setLayout(action.value)
                is AiLauncherAction.SetStyle -> engine.setStyle(action.value)
            }
        }
        commandInput.text.clear()
        render()
        when {
            assetQuery != null -> startActivity(Intent(this, AssetsActivity::class.java)
                .putExtra(AssetsActivity.EXTRA_SUGGESTED_QUERY, assetQuery))
            chooseWidget -> addAndroidWidget()
        }
        Toast.makeText(this, "Confirmed plan applied", Toast.LENGTH_SHORT).show()
    }

    private fun reviewPrompt(result: PromptInterpretation.Ready, installed: List<AppInfo>) {
        val command = result.command
        val isLaunch = command is LauncherCommand.LaunchApp
        val builder = AlertDialog.Builder(this).setTitle(
            if (command is LauncherCommand.CreateTheme) "Preview ${command.values.name}" else "Review command"
        )
        if (command is LauncherCommand.CreateTheme) {
            builder.setView(themePreview(command.values))
            builder.setNeutralButton("Apply + find wallpaper") { _, _ ->
                try {
                    saveTheme(command.values)
                    commandInput.text.clear()
                    render()
                    startActivity(Intent(this, AssetsActivity::class.java).putExtra(
                        AssetsActivity.EXTRA_SUGGESTED_QUERY,
                        com.aicontrol.launcher.theme.ThemeSpec.suggestedWallpaperQuery(command.values.name)
                    ))
                } catch (error: Exception) {
                    Toast.makeText(this, error.message ?: "Unable to apply theme", Toast.LENGTH_LONG).show()
                }
            }
        } else builder.setMessage(result.preview)
        builder
            .setNegativeButton("Cancel", null)
            .setPositiveButton(if (isLaunch) "Open" else "Apply") { _, _ ->
                try {
                    when (command) {
                        is LauncherCommand.ApplyTheme -> engine.setTheme(command.name)
                        is LauncherCommand.CreateTheme -> saveTheme(command.values)
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

    private fun themePreview(values: com.aicontrol.launcher.theme.ThemeSpec.Values): LinearLayout {
        val background = Color.parseColor(values.background)
        val accent = Color.parseColor(values.accent)
        val accent2 = Color.parseColor(values.accent2)
        val card = Color.parseColor(values.card)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            this.background = UiTheme.rounded(background, 22f, accent, 1)
            addView(TextView(this@MainActivity).apply {
                text = "${values.name}  ·  ${values.style.uppercase(Locale.getDefault())}"
                textSize = 18f
                setTextColor(Color.WHITE)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Palette preview. Optional wallpaper art can be imported locally or searched under an open license."
                textSize = 11f
                setTextColor(Color.LTGRAY)
                setPadding(0, dp(3), 0, dp(12))
            })
            val samples = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            listOf("Accent" to accent, "Secondary" to accent2, "Cards" to card).forEach { (label, color) ->
                samples.addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    addView(View(this@MainActivity).apply {
                        setBackgroundColor(color)
                        layoutParams = LinearLayout.LayoutParams(-1, dp(38))
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = label
                        textSize = 10f
                        setTextColor(Color.WHITE)
                    })
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            addView(samples)
            addView(TextView(this@MainActivity).apply {
                text = "Background ${values.background} · Accent ${values.accent}\nSecondary ${values.accent2} · Card ${values.card}"
                textSize = 10f
                setTextColor(Color.LTGRAY)
                setPadding(dp(4), dp(2), dp(4), 0)
            })
            addView(LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(10), dp(8), dp(10), dp(8))
                this.background = UiTheme.rounded(card, 14f, accent, 1)
                addView(View(this@MainActivity).apply {
                    setBackgroundColor(accent)
                    layoutParams = LinearLayout.LayoutParams(dp(30), dp(30))
                })
                addView(TextView(this@MainActivity).apply {
                    text = "  App icons     Search     Dock"
                    textSize = 12f
                    setTextColor(Color.WHITE)
                })
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        }
    }

    private fun saveTheme(values: com.aicontrol.launcher.theme.ThemeSpec.Values) {
        engine.applyJson(
            org.json.JSONObject().put("actions", org.json.JSONArray().put(
                org.json.JSONObject()
                    .put("action", "CREATE_THEME")
                    .put("name", values.name)
                    .put("bg", values.background)
                    .put("accent", values.accent)
                    .put("accent2", values.accent2)
                    .put("card", values.card)
                    .put("style", values.style)
            )).toString()
        ).getOrThrow()
    }

    private fun render() {
        UiTheme.bind(engine)
        (root.getChildAt(0) as? ImageView)?.setImageBitmap(
            assets.activeWallpaper()?.let { ImageAssetValidation.decodeSampled(it, 2048) }
        )
        (root.getChildAt(1) as? FrameLayout)?.setBackgroundColor(
            Color.argb(150, Color.red(UiTheme.bg), Color.green(UiTheme.bg), Color.blue(UiTheme.bg))
        )
        renderApps()
        renderDock()
        renderWidgets()
    }

    private fun addAndroidWidget() {
        pendingWidgetId = widgetHost.allocateAppWidgetId()
        try {
            startActivityForResult(
                Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId),
                REQUEST_PICK_WIDGET
            )
        } catch (error: Exception) {
            widgetHost.deleteAppWidgetId(pendingWidgetId)
            pendingWidgetId = -1
            Toast.makeText(this, "Android widget picker is unavailable", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("System widget picker result API is retained for this API-30-only launcher.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_PICK_WIDGET) {
            val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId) ?: pendingWidgetId
            if (resultCode != RESULT_OK || id < 0) {
                if (pendingWidgetId >= 0) widgetHost.deleteAppWidgetId(pendingWidgetId)
                pendingWidgetId = -1
                return
            }
            pendingWidgetId = id
            val info = widgetManager.getAppWidgetInfo(id)
            if (info == null) {
                widgetHost.deleteAppWidgetId(id)
                pendingWidgetId = -1
                Toast.makeText(this, "The selected widget is no longer available", Toast.LENGTH_LONG).show()
                return
            }
            if (info.configure != null) {
                try {
                    startActivityForResult(
                        Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                            .setComponent(info.configure)
                            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                        REQUEST_CONFIGURE_WIDGET
                    )
                } catch (error: Exception) {
                    widgetHost.deleteAppWidgetId(id)
                    pendingWidgetId = -1
                    Toast.makeText(this, "Widget configuration could not be opened", Toast.LENGTH_LONG).show()
                }
            } else finishAddingWidget(id)
            return
        }
        if (requestCode == REQUEST_CONFIGURE_WIDGET) {
            val id = pendingWidgetId
            if (resultCode == RESULT_OK && id >= 0) finishAddingWidget(id)
            else if (id >= 0) widgetHost.deleteAppWidgetId(id)
            pendingWidgetId = -1
        }
    }

    private fun finishAddingWidget(id: Int) {
        val values = widgetIds().toMutableSet().apply { add(id) }
        getSharedPreferences("launcher_state", MODE_PRIVATE).edit()
            .putStringSet("app_widget_ids", values.map { it.toString() }.toSet()).apply()
        pendingWidgetId = -1
        renderWidgets()
        Toast.makeText(this, "Widget added", Toast.LENGTH_SHORT).show()
    }

    private fun widgetIds(): Set<Int> = getSharedPreferences("launcher_state", MODE_PRIVATE)
        .getStringSet("app_widget_ids", emptySet())?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

    private fun renderWidgets() {
        if (!::widgetContainer.isInitialized || !::widgetHost.isInitialized || !::widgetManager.isInitialized) return
        widgetContainer.removeAllViews()
        val ids = widgetIds().sorted()
        widgetContainer.visibility = if (ids.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        ids.forEach { id ->
            val info = widgetManager.getAppWidgetInfo(id) ?: return@forEach
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(4), dp(8), dp(6))
                UiTheme.styleCard(this, UiTheme.card, true)
            }
            val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            header.addView(TextView(this).apply {
                text = info.loadLabel(packageManager)
                textSize = 11f
                setTextColor(UiTheme.textMuted)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            header.addView(actionButton("Remove") { confirmRemoveWidget(id, info.loadLabel(packageManager).toString()) })
            card.addView(header)
            val hostView = widgetHost.createView(this, id, info).apply {
                setAppWidget(id, info)
                contentDescription = "${info.loadLabel(packageManager)} home-screen widget"
            }
            card.addView(hostView, LinearLayout.LayoutParams(-1, dp(info.minHeight.coerceAtLeast(72))))
            widgetContainer.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(6) })
        }
    }

    private fun confirmRemoveWidget(id: Int, label: String) {
        AlertDialog.Builder(this)
            .setTitle("Remove widget?")
            .setMessage("Remove $label from this launcher's home screen?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                val remaining = widgetIds().toMutableSet().apply { remove(id) }
                getSharedPreferences("launcher_state", MODE_PRIVATE).edit()
                    .putStringSet("app_widget_ids", remaining.map { it.toString() }.toSet()).apply()
                widgetHost.deleteAppWidgetId(id)
                renderWidgets()
            }
            .show()
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
