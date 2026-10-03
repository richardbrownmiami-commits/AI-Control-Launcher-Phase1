package com.aicontrol.launcher.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.*
import com.aicontrol.launcher.actions.ActionEngine
import com.aicontrol.launcher.ai.AiLauncherAction
import com.aicontrol.launcher.ai.AiPlanDecision
import com.aicontrol.launcher.ai.AiPlanValidator
import com.aicontrol.launcher.ai.AiThemeOperation
import com.aicontrol.launcher.ai.AiTurn
import com.aicontrol.launcher.ai.createProvider
import com.aicontrol.launcher.apps.AppRepository
import com.aicontrol.launcher.data.SettingsStore
import com.aicontrol.launcher.icons.IconPackManager
import com.aicontrol.launcher.icons.IconPackStore
import com.aicontrol.launcher.nlp.AppTarget
import com.aicontrol.launcher.nlp.LauncherCommand
import com.aicontrol.launcher.nlp.LocalPromptInterpreter
import com.aicontrol.launcher.nlp.PromptInterpretation
import com.aicontrol.launcher.theme.ThemeSpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Persistent, review-first assistant UI. Local attachments are never read or uploaded. */
@SuppressLint("SetTextI18n")
class AssistantActivity : Activity() {
    companion object { private const val REQUEST_PICK_ATTACHMENT = 8101 }

    private data class Entry(val role: String, val content: String, val attachment: String? = null, val time: Long = System.currentTimeMillis())

    private lateinit var engine: ActionEngine
    private lateinit var appRepository: AppRepository
    private lateinit var iconPacks: IconPackManager
    private lateinit var transcript: LinearLayout
    private lateinit var transcriptScroll: ScrollView
    private lateinit var composer: EditText
    private lateinit var status: TextView
    private lateinit var attachmentChip: TextView
    private lateinit var sendButton: Button
    private lateinit var providerCaption: TextView
    private lateinit var providerDescription: TextView
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val entries = mutableListOf<Entry>()
    private var attachmentUri: Uri? = null
    private var attachmentName: String? = null
    private var busy = false
    private var pendingPlan: com.aicontrol.launcher.ai.AiLauncherPlan? = null
    private var pendingOffline: PromptInterpretation.Ready? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        engine = ActionEngine(this)
        appRepository = AppRepository(this)
        iconPacks = IconPackManager(this)
        UiTheme.bind(engine)
        loadThread()
        buildUi()
        renderTranscript()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        if (::providerCaption.isInitialized) updateProviderCard()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(10))
            background = UiTheme.background()
        }
        val toolbar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        toolbar.addView(iconButton("‹", "Back to launcher") { finish() }, LinearLayout.LayoutParams(dp(48), dp(48)))
        toolbar.addView(LinearLayout(this@AssistantActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
            addView(TextView(this@AssistantActivity).apply {
                text = "Launcher assistant"
                textSize = 19f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(UiTheme.textPrimary)
            })
            addView(TextView(this@AssistantActivity).apply {
                text = "Themes · licensed wallpaper · installed icon packs"
                textSize = 11f
                setTextColor(UiTheme.textMuted)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(iconButton("History", "Show recent prompts") { showPromptHistory() }, LinearLayout.LayoutParams(-2, dp(48)))
        toolbar.addView(iconButton("⋮", "Assistant options") { showOptions() }, LinearLayout.LayoutParams(dp(48), dp(48)).apply { leftMargin = dp(4) })
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(52)))

        val providerCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            UiTheme.styleCard(this, UiTheme.card, false)
        }
        providerCaption = TextView(this).apply {
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        providerDescription = TextView(this).apply {
            textSize = 11f
            setTextColor(UiTheme.textMuted)
            setPadding(0, dp(4), 0, 0)
        }
        providerCard.addView(providerCaption)
        providerCard.addView(providerDescription)
        updateProviderCard()
        root.addView(providerCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5); bottomMargin = dp(8) })

        transcriptScroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            overScrollMode = ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS
            setPadding(0, dp(2), 0, dp(8))
        }
        transcript = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        transcriptScroll.addView(transcript, FrameLayout.LayoutParams(-1, -2))
        root.addView(transcriptScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        status = TextView(this).apply {
            textSize = 11f
            setTextColor(UiTheme.accent)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            visibility = View.GONE
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2))

        attachmentChip = TextView(this).apply {
            textSize = 11f
            setTextColor(UiTheme.textPrimary)
            setPadding(dp(12), dp(7), dp(12), dp(7))
            background = UiTheme.rounded(UiTheme.card2, 18f, UiTheme.accent, 1)
            visibility = View.GONE
            setOnClickListener { clearAttachment() }
            contentDescription = "Tap to remove the selected local attachment"
        }
        root.addView(attachmentChip, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(5) })

        val inputRow = LinearLayout(this).apply {
            gravity = Gravity.BOTTOM
            setPadding(dp(6), dp(5), dp(6), dp(5))
            UiTheme.styleCard(this, UiTheme.card, false)
        }
        inputRow.addView(iconButton("＋", "Choose a local image or file") { pickAttachment() }, LinearLayout.LayoutParams(dp(44), dp(48)))
        composer = EditText(this).apply {
            hint = "Message your assistant…"
            textSize = 14f
            minLines = 1
            maxLines = 4
            setTextColor(UiTheme.textPrimary)
            setHintTextColor(UiTheme.textMuted)
            setPadding(dp(10), dp(8), dp(8), dp(8))
            background = UiTheme.rounded(Color.TRANSPARENT, 14f)
            minHeight = dp(40)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, action, _ ->
                if (action == android.view.inputmethod.EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
            }
        }
        inputRow.addView(composer, LinearLayout.LayoutParams(0, -2, 1f))
        sendButton = Button(this).apply {
            text = "Send"
            textSize = 12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(UiTheme.textOnAccent)
            background = UiTheme.gradient(18f)
            setOnClickListener { sendMessage() }
        }
        inputRow.addView(sendButton, LinearLayout.LayoutParams(dp(72), dp(48)).apply { leftMargin = dp(4) })
        root.addView(inputRow, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "Local attachments stay on this device and are not sent to the AI provider."
            textSize = 10f
            setTextColor(UiTheme.textMuted)
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun updateProviderCard() {
        if (!::providerCaption.isInitialized) return
        val settings = SettingsStore(this)
        val ready = settings.activeKey().isNotBlank()
        providerCaption.text = if (ready) "CONNECTED  ·  ${settings.provider.uppercase(Locale.ROOT)} / ${settings.model}"
            else "OFFLINE MODE  ·  no provider key configured"
        providerCaption.setTextColor(if (ready) UiTheme.success else UiTheme.accent)
        providerDescription.text = if (ready) "Continue naturally. Launcher changes are always previewed before you confirm."
            else "The offline theme helper is ready. Add a Gemini or OpenRouter key in Settings for open-ended conversation."
    }

    private fun iconButton(label: String, description: String, click: () -> Unit) = Button(this).apply {
        text = label
        textSize = if (label.length > 2) 10f else 19f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(UiTheme.textPrimary)
        contentDescription = description
        background = UiTheme.rounded(UiTheme.card2, 15f, UiTheme.accent, 1)
        setOnClickListener { click() }
    }

    private fun renderTranscript() {
        transcript.removeAllViews()
        if (entries.isEmpty()) {
            val welcome = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dp(22), dp(26), dp(22), dp(24))
                UiTheme.styleCard(this, UiTheme.card, false)
            }
            welcome.addView(TextView(this).apply {
                text = "What would you like to change?"
                textSize = 19f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setTextColor(UiTheme.textPrimary)
            })
            welcome.addView(TextView(this).apply {
                text = "Choose a ready-made theme, search reusable wallpapers, or manage a Nova-compatible installed icon pack."
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(UiTheme.textMuted)
                setPadding(0, dp(7), 0, dp(14))
            })
            val examples = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
            listOf("Presets", "Wallpaper", "Icons", "Help").forEach { label ->
                examples.addView(Button(this@AssistantActivity).apply {
                    text = label
                    textSize = 10f
                    setTextColor(UiTheme.textPrimary)
                    background = UiTheme.rounded(UiTheme.card2, 16f, UiTheme.accent, 1)
                    setOnClickListener {
                        when (label) {
                            "Presets" -> startActivity(Intent(this@AssistantActivity, ThemeGalleryActivity::class.java))
                            "Wallpaper" -> startActivity(Intent(this@AssistantActivity, AssetsActivity::class.java))
                            "Icons" -> startActivity(Intent(this@AssistantActivity, LauncherSettingsActivity::class.java))
                            else -> {
                                composer.setText("What can you help me with?")
                                composer.setSelection(composer.text.length)
                            }
                        }
                    }
                }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(2); rightMargin = dp(2) })
            }
            welcome.addView(examples)
            transcript.addView(welcome, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })
        }
        entries.forEach { entry ->
            transcript.addView(messageBubble(entry), LinearLayout.LayoutParams(-1, -2))
        }
        pendingPlan?.let { transcript.addView(planCard(it)) }
        pendingOffline?.let { transcript.addView(offlineCard(it)) }
        transcriptScroll.post { transcriptScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun messageBubble(entry: Entry): View {
        val isUser = entry.role == "user"
        val isStatus = entry.role == "status"
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (isUser) Gravity.END else Gravity.START
            setPadding(if (isUser) dp(44) else 0, dp(4), if (isUser) 0 else dp(44), dp(4))
        }
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(13), dp(9), dp(13), dp(8))
            background = UiTheme.rounded(if (isUser) UiTheme.accent2 else UiTheme.card2,
                18f, if (isStatus) UiTheme.danger else if (isUser) UiTheme.accent2 else UiTheme.accent, 1)
            elevation = dp(2).toFloat()
        }
        val sender = when { isStatus -> "STATUS"; isUser -> "YOU"; else -> "ASSISTANT" }
        val userForeground = UiTheme.contrasting(UiTheme.accent2)
        bubble.addView(TextView(this).apply {
            text = "$sender  ·  ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.time))}"
            textSize = 9f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(if (isStatus) UiTheme.warning else if (isUser) userForeground else UiTheme.accent)
        })
        bubble.addView(TextView(this).apply {
            text = entry.content
            textSize = 14f
            setTextColor(if (isUser) userForeground else UiTheme.textPrimary)
            setPadding(0, dp(3), 0, 0)
        })
        entry.attachment?.let { name ->
            bubble.addView(TextView(this).apply {
                text = "▧  $name\nLocal file · not uploaded"
                textSize = 10f
                setTextColor(if (isUser) userForeground else UiTheme.textMuted)
                setPadding(0, dp(7), 0, 0)
            })
        }
        outer.addView(bubble, LinearLayout.LayoutParams(-2, -2))
        return outer
    }

    private fun sendMessage() {
        if (busy) return
        val text = composer.text?.toString()?.trim().orEmpty()
        if (text.isEmpty() && attachmentName == null) {
            setStatus("Write a message or choose a local file first.", false)
            return
        }
        if (text.length > 2_000) {
            setStatus("Keep each message under 2,000 characters.", false)
            return
        }
        val prompt = text.ifBlank { "I selected a local file. Please help me decide what to do with it." }
        val localAttachment = attachmentName
        composer.text.clear()
        clearAttachment()
        append(Entry("user", prompt, localAttachment))
        val settings = SettingsStore(this)
        if (settings.activeKey().isBlank()) {
            runOffline(prompt)
            return
        }
        val provider = try {
            createProvider(settings)
        } catch (error: Exception) {
            appendStatus("Provider setup error: ${error.message ?: "Select a supported provider and model in AI Settings."}")
            return
        }
        busy = true
        sendButton.isEnabled = false
        setStatus("Thinking with ${settings.provider}…", true)
        val state = "Current appearance: theme=${engine.theme()}, layout=${engine.layout()}, style=${engine.style()}. " +
            "Available themes: ${engine.availableThemes().sorted().joinToString(", ")}. " +
            "Installed compatible icon packs: ${iconPacks.assistantChoices().keys.sorted().ifEmpty { listOf("none") }.joinToString(", ")}."
        val priorTurns = entries.dropLast(1).filter { it.role == "user" || it.role == "assistant" }
        val withoutUnansweredLastPrompt = if (priorTurns.lastOrNull()?.role == "user") priorTurns.dropLast(1) else priorTurns
        val history = withoutUnansweredLastPrompt.takeLast(8).dropWhile { it.role == "assistant" }
            .map { AiTurn(it.role, it.content) }
        scope.launch {
            try {
                val reply = withContext(Dispatchers.IO) { provider.chat(prompt, state, history).getOrThrow() }
                val decision = try {
                    AiPlanValidator.parse(reply.text, engine.availableThemes(), iconPacks.assistantChoices())
                } catch (error: Exception) {
                    appendStatus("The provider replied, but its plan could not be read: ${error.message ?: "Invalid response format."} Nothing was changed. Try rephrasing or asking for plain guidance.")
                    return@launch
                }
                when (decision) {
                    is AiPlanDecision.Conversation -> append(Entry("assistant", decision.response))
                    is AiPlanDecision.Clarification -> append(Entry("assistant", "${decision.response}\n\n${decision.question}"))
                    is AiPlanDecision.Review -> {
                        append(Entry("assistant", decision.plan.response))
                        pendingPlan = decision.plan
                        renderTranscript()
                    }
                }
                persistThread()
                setStatus("Ready · review any proposed launcher changes below.", true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                appendStatus("Could not reach the selected AI provider: ${error.message ?: "Check the connection and provider settings."} No launcher changes were made.")
            } finally {
                busy = false
                sendButton.isEnabled = true
            }
        }
    }

    private fun runOffline(prompt: String) {
        setStatus("Using the offline theme helper. Open AI Settings to enable full conversation.", true)
        val installed = appRepository.listLaunchableApps()
        when (val result = LocalPromptInterpreter.interpret(
            prompt,
            installed.map { AppTarget(it.label, it.packageName) },
            engine.availableThemes()
        )) {
            is PromptInterpretation.Ready -> {
                pendingOffline = result
                append(Entry("assistant", result.preview))
                renderTranscript()
                persistThread()
            }
            is PromptInterpretation.Help -> {
                append(Entry("assistant", result.message)); persistThread()
            }
            is PromptInterpretation.Clarification -> {
                append(Entry("assistant", result.message)); persistThread()
            }
            is PromptInterpretation.Unrecognized -> {
                append(Entry("assistant", "I can make validated launcher themes and handle a small set of offline launcher commands. ${result.message}\n\nFor open-ended conversation, configure Gemini or OpenRouter in AI Settings."))
                persistThread()
            }
        }
    }

    private fun planCard(plan: com.aicontrol.launcher.ai.AiLauncherPlan): View {
        val card = actionCard("Review your theme bundle", "No search, download, or launcher change runs until you confirm. New themes stay drafts while you review licensed assets.")
        when (val theme = plan.theme) {
            is AiThemeOperation.Create -> card.addView(themePreview(theme.values))
            is AiThemeOperation.Apply -> card.addView(themePreview(engine.themeValues(theme.name)))
            null -> Unit
        }
        val summary = buildList {
            when (val theme = plan.theme) {
                is AiThemeOperation.Create -> add("Create theme: ${theme.values.name}")
                is AiThemeOperation.Apply -> add("Apply theme: ${theme.name}")
                null -> Unit
            }
            plan.actions.forEach { action -> add(when (action) {
                is AiLauncherAction.SearchAssets -> "Build theme assets: search Commons for ‘${action.query}’, then match open-license app icons locally"
                AiLauncherAction.AddWidget -> "Open Android’s widget picker"
                is AiLauncherAction.SetLayout -> "Set layout to ${action.value}"
                is AiLauncherAction.SetStyle -> "Set card style to ${action.value}"
                is AiLauncherAction.ApplyInstalledIconPack -> "Apply installed icon pack ${action.label} (only mapped apps change)"
                AiLauncherAction.BrowseIconPacks -> "Open the official Google Play icon-pack search; installation remains under your control"
            }) }
            if (plan.theme is AiThemeOperation.Create && plan.actions.any { it is AiLauncherAction.SearchAssets }) {
                add("After this plan confirmation, one reuse-filtered wallpaper and suitable CC BY-SA OpenMoji app icons are selected automatically. You review the complete bundle once before Apply; there are no per-image approval prompts.")
            }
        }
        card.addView(TextView(this).apply {
            text = summary.joinToString("\n") { "• $it" }
            textSize = 12f
            setTextColor(UiTheme.textPrimary)
            setPadding(0, dp(10), 0, dp(10))
        })
        val draftValues = (plan.theme as? AiThemeOperation.Create)?.values
        if (draftValues != null && plan.actions.none { it is AiLauncherAction.SearchAssets || it === AiLauncherAction.AddWidget || it === AiLauncherAction.BrowseIconPacks }) {
            card.addView(Button(this).apply {
                text = "Build full theme bundle"
                textSize = 11f
                setTextColor(UiTheme.textOnAccent)
                background = UiTheme.gradient(16f)
                setOnClickListener {
                    runCatching {
                        engine.saveThemeDraft(draftValues)
                        pendingPlan = null
                        startActivity(Intent(this@AssistantActivity, AssetsActivity::class.java)
                            .putExtra(AssetsActivity.EXTRA_THEME_NAME, draftValues.name)
                            .putExtra(AssetsActivity.EXTRA_SUGGESTED_QUERY, ThemeSpec.suggestedWallpaperQuery(draftValues.name)))
                    }.onFailure { appendStatus("Could not start the bundle builder: ${it.message ?: "The draft was not saved."}") }
                }
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(6) })
        }
        if (draftValues != null && plan.actions.isEmpty()) {
            val values = draftValues
            card.addView(Button(this).apply {
                text = "Save as draft · don’t apply yet"
                textSize = 11f
                setTextColor(UiTheme.textPrimary)
                background = UiTheme.rounded(UiTheme.card, 16f, UiTheme.accent, 1)
                setOnClickListener {
                    runCatching { engine.saveThemeDraft(values) }
                        .onSuccess {
                            pendingPlan = null
                            append(Entry("assistant", "Saved ‘${values.name}’ as a draft. Add a wallpaper or app icons later from the theme gallery."))
                        }
                        .onFailure { appendStatus("Could not save the theme draft: ${it.message ?: "Storage was unavailable."}") }
                }
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(6) })
        }
        val buttons = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        buttons.addView(Button(this).apply {
            text = "Cancel"
            textSize = 11f
            setTextColor(UiTheme.textPrimary)
            background = UiTheme.rounded(UiTheme.card2, 16f)
            setOnClickListener { pendingPlan = null; renderTranscript() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        buttons.addView(Button(this).apply {
            text = when {
                plan.actions.any { it is AiLauncherAction.SearchAssets } && plan.theme is AiThemeOperation.Create -> "Confirm & build theme"
                plan.actions.any { it is AiLauncherAction.SearchAssets } -> "Confirm & find wallpaper"
                plan.actions.any { it === AiLauncherAction.BrowseIconPacks } -> "Confirm & browse packs"
                else -> "Confirm & apply"
            }
            textSize = 11f
            setTextColor(UiTheme.textOnAccent)
            background = UiTheme.gradient(16f)
            setOnClickListener { pendingPlan = null; executePlan(plan) }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) })
        card.addView(buttons)
        return card
    }

    private fun offlineCard(result: PromptInterpretation.Ready): View {
        val card = actionCard("Offline helper · review", result.preview)
        when (val command = result.command) {
            is LauncherCommand.CreateTheme -> card.addView(themePreview(command.values))
            is LauncherCommand.ApplyTheme -> card.addView(themePreview(engine.themeValues(command.name)))
            else -> Unit
        }
        (result.command as? LauncherCommand.CreateTheme)?.let { command ->
            card.addView(TextView(this).apply {
                text = "Optional complete build: find one reuse-cleared abstract wallpaper and matching OpenMoji icons, preview the bundle, then decide whether to apply it. Local Downloads are optional."
                textSize = 11f
                setTextColor(UiTheme.textMuted)
                setPadding(0, dp(7), 0, dp(7))
            })
            card.addView(Button(this).apply {
                text = "Build full theme bundle"
                textSize = 11f
                setTextColor(UiTheme.textOnAccent)
                background = UiTheme.gradient(16f)
                setOnClickListener {
                    runCatching {
                        engine.saveThemeDraft(command.values)
                        pendingOffline = null
                        startActivity(android.content.Intent(this@AssistantActivity, AssetsActivity::class.java)
                            .putExtra(AssetsActivity.EXTRA_THEME_NAME, command.values.name)
                            .putExtra(AssetsActivity.EXTRA_SUGGESTED_QUERY, ThemeSpec.suggestedWallpaperQuery(command.values.name)))
                    }.onFailure {
                        appendStatus("Could not start the bundle builder: ${it.message ?: "The draft was not saved."}")
                    }
                }
            }, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(8) })
        }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(Button(this).apply {
            text = "Cancel"
            setTextColor(UiTheme.textPrimary)
            background = UiTheme.rounded(UiTheme.card2, 16f)
            setOnClickListener { pendingOffline = null; renderTranscript() }
        }, LinearLayout.LayoutParams(0, dp(48), 1f))
        row.addView(Button(this).apply {
            text = if (result.command is LauncherCommand.LaunchApp) "Open app" else "Apply"
            setTextColor(UiTheme.textOnAccent)
            background = UiTheme.gradient(16f)
            setOnClickListener { pendingOffline = null; executeOffline(result.command) }
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(6) })
        card.addView(row)
        return card
    }

    private fun actionCard(title: String, subtitle: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(13), dp(14), dp(13))
        background = UiTheme.rounded(UiTheme.card2, 20f, UiTheme.accent, 1)
        elevation = dp(3).toFloat()
        addView(TextView(this@AssistantActivity).apply {
            text = title
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(UiTheme.accent)
        })
        addView(TextView(this@AssistantActivity).apply {
            text = subtitle
            textSize = 11f
            setTextColor(UiTheme.textMuted)
            setPadding(0, dp(4), 0, dp(3))
        })
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(7); bottomMargin = dp(8) }
    }

    private fun themePreview(values: ThemeSpec.Values) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = UiTheme.rounded(Color.parseColor(values.background), 16f, Color.parseColor(values.accent), 1)
        addView(TextView(this@AssistantActivity).apply {
            text = "${values.name}  ·  ${values.style.uppercase(Locale.ROOT)}"
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setTextColor(contrast(values.background))
        })
        addView(TextView(this@AssistantActivity).apply {
            text = "${values.typography} type · ${values.iconStyle} icons · ${values.backgroundStyle} background"
            textSize = 10f
            setTextColor(contrast(values.background))
            setPadding(0, dp(4), 0, dp(7))
        })
        val swatches = LinearLayout(this@AssistantActivity).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(values.accent, values.accent2, values.card).forEach { color ->
            swatches.addView(View(this@AssistantActivity).apply {
                background = UiTheme.rounded(Color.parseColor(color), 8f)
            }, LinearLayout.LayoutParams(0, dp(25), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
        }
        addView(swatches)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5); bottomMargin = dp(8) }
    }

    private fun contrast(hex: String): Int {
        val c = Color.parseColor(hex)
        val l = (0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)) / 255.0
        return if (l > 0.58) Color.rgb(26, 30, 38) else Color.WHITE
    }

    private fun executePlan(plan: com.aicontrol.launcher.ai.AiLauncherPlan) {
        try {
            var query: String? = null
            var addWidget = false
            var browseIconPacks = false
            var iconPackApplied = false
            plan.actions.forEach { action ->
                when (action) {
                    is AiLauncherAction.SearchAssets -> query = action.query
                    AiLauncherAction.AddWidget -> addWidget = true
                    is AiLauncherAction.SetLayout -> engine.setLayout(action.value)
                    is AiLauncherAction.SetStyle -> engine.setStyle(action.value)
                    is AiLauncherAction.ApplyInstalledIconPack -> {
                        check(iconPacks.installedPacks().any { it.packageName == action.packageName }) {
                            "That icon pack is no longer installed or compatible. No pack was applied."
                        }
                        engine.setInstalledIconPack(action.packageName)
                        iconPacks.clearCache()
                        iconPackApplied = true
                    }
                    AiLauncherAction.BrowseIconPacks -> browseIconPacks = true
                }
            }
            val draftName = (plan.theme as? AiThemeOperation.Create)?.values?.name
            when (val theme = plan.theme) {
                is AiThemeOperation.Create -> if (query != null) engine.saveThemeDraft(theme.values) else saveTheme(theme.values)
                is AiThemeOperation.Apply -> engine.setTheme(theme.name)
                null -> Unit
            }
            UiTheme.bind(engine)
            persistThread()
            renderTranscript()
            when {
                query != null -> startActivity(Intent(this, AssetsActivity::class.java)
                    .putExtra(AssetsActivity.EXTRA_SUGGESTED_QUERY, query)
                    .putExtra(AssetsActivity.EXTRA_THEME_NAME, draftName.orEmpty()))
                addWidget -> startActivity(Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    .putExtra(MainActivity.EXTRA_REQUEST_ADD_WIDGET, true))
                browseIconPacks -> IconPackStore.open(this).getOrThrow()
            }
            setStatus(when {
                query != null && draftName != null -> "Theme draft saved. Review the Commons license, choose an image, then preview and apply the complete bundle."
                query != null -> "Confirmed. Wallpaper search is open; review a result and its license before downloading."
                addWidget -> "Confirmed. Choose an installed widget in Android's picker."
                browseIconPacks -> "Confirmed. Google Play search is open; install a pack there, then select it in Launcher settings."
                iconPackApplied -> "Confirmed. The installed icon pack is selected and will render on the launcher when you return."
                else -> "Confirmed. The reviewed theme and layout changes were applied."
            }, true)
        } catch (error: Exception) {
            appendStatus("Could not apply the confirmed plan: ${error.message ?: "Launcher rejected the change."}")
        }
    }

    private fun executeOffline(command: LauncherCommand) {
        try {
            when (command) {
                is LauncherCommand.ApplyTheme -> engine.setTheme(command.name)
                is LauncherCommand.CreateTheme -> saveTheme(command.values)
                is LauncherCommand.SetLayout -> engine.setLayout(command.name)
                is LauncherCommand.SetStyle -> engine.setStyle(command.name)
                is LauncherCommand.SetAppVisible -> engine.setAppVisible(command.app.packageName, command.visible)
                is LauncherCommand.LaunchApp -> {
                    val app = appRepository.listLaunchableApps().firstOrNull { it.packageName == command.app.packageName }
                        ?: error("That app is no longer available")
                    appRepository.launch(app)
                }
            }
            UiTheme.bind(engine)
            append(Entry("assistant", "Done — the reviewed launcher change succeeded."))
            persistThread()
            renderTranscript()
        } catch (error: Exception) {
            appendStatus("Could not apply that change: ${error.message ?: "Launcher rejected it."}")
        }
    }

    private fun saveTheme(values: ThemeSpec.Values) {
        val action = JSONObject().put("action", "CREATE_THEME").put("name", values.name)
            .put("bg", values.background).put("accent", values.accent).put("accent2", values.accent2)
            .put("card", values.card).put("style", values.style).put("typography", values.typography)
            .put("iconStyle", values.iconStyle).put("backgroundStyle", values.backgroundStyle)
            .put("layout", values.layout).put("wallpaperAsset", values.wallpaperAsset ?: "")
            .put("iconPackPackage", values.iconPackPackage ?: "")
            .put("iconAssets", JSONObject().apply { values.iconAssets.forEach { (pkg, asset) -> put(pkg, asset) } })
        engine.applyJson(JSONObject().put("actions", JSONArray().put(action)).toString()).getOrThrow()
    }

    private fun pickAttachment() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("image/*", "text/plain", "application/pdf"))
            putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try { startActivityForResult(intent, REQUEST_PICK_ATTACHMENT) }
        catch (error: Exception) { appendStatus("No compatible file picker is available: ${error.message ?: "Try again."}") }
    }

    @Deprecated("Document picker result API is retained for this API-30-only launcher.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_PICK_ATTACHMENT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val name = try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) { null } ?: "Selected local file"
        attachmentUri = uri
        attachmentName = name.take(120)
        attachmentChip.text = "▧  ${attachmentName}  ·  tap to remove"
        attachmentChip.visibility = View.VISIBLE
        setStatus("Selected locally. The file contents will not be uploaded.", true)
    }

    private fun clearAttachment() {
        attachmentUri = null
        attachmentName = null
        if (::attachmentChip.isInitialized) attachmentChip.visibility = View.GONE
    }

    private fun append(entry: Entry) {
        entries += entry
        if (entries.size > 100) entries.removeAt(0)
        persistThread()
        renderTranscript()
    }

    private fun appendStatus(text: String) {
        entries += Entry("status", text)
        if (entries.size > 100) entries.removeAt(0)
        persistThread()
        renderTranscript()
        setStatus("There was a problem; see the inline message above.", false)
    }

    private fun setStatus(text: String, ok: Boolean) {
        status.text = text
        status.setTextColor(if (ok) UiTheme.textMuted else UiTheme.danger)
        status.visibility = View.VISIBLE
    }

    private fun loadThread() {
        val raw = getSharedPreferences("assistant_thread", MODE_PRIVATE).getString("entries", "[]") ?: "[]"
        try {
            val json = JSONArray(raw)
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                val role = item.optString("role")
                if (role !in setOf("user", "assistant", "status")) continue
                val text = item.optString("content").take(4_000)
                if (text.isNotBlank()) entries += Entry(role, text, item.optString("attachment").takeIf { it.isNotBlank() }, item.optLong("time", 0L))
            }
            if (entries.size > 100) entries.subList(0, entries.size - 100).clear()
        } catch (_: Exception) { entries.clear() }
    }

    private fun persistThread() {
        val json = JSONArray()
        entries.takeLast(100).forEach { entry ->
            json.put(JSONObject().put("role", entry.role).put("content", entry.content)
                .put("attachment", entry.attachment ?: "").put("time", entry.time))
        }
        getSharedPreferences("assistant_thread", MODE_PRIVATE).edit().putString("entries", json.toString()).apply()
    }

    private fun showPromptHistory() {
        val prompts = entries.filter { it.role == "user" }.asReversed().take(30)
        if (prompts.isEmpty()) { setStatus("Your prompts will appear here after your first message.", true); return }
        AlertDialog.Builder(this)
            .setTitle("Recent prompts")
            .setItems(prompts.map { it.content.take(160) }.toTypedArray()) { _, index ->
                composer.setText(prompts[index].content)
                composer.setSelection(composer.text.length)
                composer.requestFocus()
            }
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showOptions() {
        AlertDialog.Builder(this)
            .setTitle("Assistant")
            .setItems(arrayOf("AI provider settings", "Clear conversation")) { _, which ->
                if (which == 0) startActivity(Intent(this, SettingsActivity::class.java)) else confirmClearThread()
            }
            .show()
    }

    private fun confirmClearThread() {
        AlertDialog.Builder(this)
            .setTitle("Clear this conversation?")
            .setMessage("This permanently removes the locally saved assistant thread from this device.")
            .setNegativeButton("Keep conversation", null)
            .setPositiveButton("Clear thread") { _, _ ->
                entries.clear(); pendingPlan = null; pendingOffline = null; persistThread(); renderTranscript()
                setStatus("Conversation cleared.", true)
            }
            .show()
    }
}
