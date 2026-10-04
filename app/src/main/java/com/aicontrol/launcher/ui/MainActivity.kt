package com.aicontrol.launcher.ui

import android.annotation.SuppressLint
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
import com.aicontrol.launcher.assets.AssetStore
import com.aicontrol.launcher.assets.ImageAssetValidation
import com.aicontrol.launcher.ai.AiLauncherAction
import com.aicontrol.launcher.ai.AiPlanConfirmationGate
import com.aicontrol.launcher.ai.AiPlanDecision
import com.aicontrol.launcher.ai.AiPlanValidator
import com.aicontrol.launcher.ai.AiThemeOperation
import com.aicontrol.launcher.ai.AiTurn
import com.aicontrol.launcher.ai.createProvider
import com.aicontrol.launcher.data.SettingsStore
import com.aicontrol.launcher.data.WorkspaceStore
import com.aicontrol.launcher.icons.IconPackManager
import com.aicontrol.launcher.icons.IconPackStore
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
import kotlin.math.abs

private const val NATIVE_ABI_GUARD = "launcherabi"
private const val WIDGET_HOST_ID = 7421
private const val REQUEST_PICK_WIDGET = 6301
private const val REQUEST_CONFIGURE_WIDGET = 6302

@SuppressLint("SetTextI18n")
class MainActivity : Activity() {
    companion object { const val EXTRA_REQUEST_ADD_WIDGET = "request_add_widget_after_ai_confirmation" }
    private lateinit var apps: AppRepository
    private lateinit var engine: ActionEngine
    private lateinit var assets: LauncherAssetManager
    private lateinit var iconPacks: IconPackManager
    private lateinit var previewAssets: ThemePreviewAssets
    private lateinit var root: FrameLayout
    private lateinit var content: LinearLayout
    private lateinit var wallpaperImage: ImageView
    private var wallpaperBitmap: android.graphics.Bitmap? = null
    private lateinit var commandInput: EditText
    private lateinit var dock: LinearLayout
    private lateinit var widgetManager: AppWidgetManager
    private lateinit var widgetHost: AppWidgetHost
    private lateinit var assistantCaption: TextView
    private lateinit var workspace: FrameLayout
    private lateinit var drawerOverlay: LinearLayout
    private lateinit var drawerGrid: GridLayout
    private lateinit var drawerSearch: EditText
    private lateinit var pageTitle: TextView
    private lateinit var homeShortcutGrid: GridLayout
    private lateinit var appDrawerButton: Button
    private lateinit var pageIndicator: LinearLayout
    private val homePages = mutableListOf<LinearLayout>()
    private val homeShortcutGrids = mutableListOf<GridLayout>()
    private val pageWidgetContainers = mutableListOf<LinearLayout>()
    private lateinit var workspaceStore: WorkspaceStore
    private var currentPage = 1
    private var drawerShowing = false
    private val aiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val aiHistory = mutableListOf<AiTurn>()
    private val aiConfirmation = AiPlanConfirmationGate()
    private var aiBusy = false
    private var pendingWidgetId = -1
    private var pendingWidgetPage = 1
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        runCatching { System.loadLibrary(NATIVE_ABI_GUARD) }
        apps = AppRepository(this)
        engine = ActionEngine(this)
        assets = LauncherAssetManager(this)
        iconPacks = IconPackManager(this)
        previewAssets = ThemePreviewAssets(this, engine, apps)
        workspaceStore = WorkspaceStore(this)
        currentPage = engine.homePage()
        val activeWallpaper = assets.activeWallpaper()
        val activeWallpaperValid = activeWallpaper?.let {
            runCatching { ImageAssetValidation.validate(it) }.isSuccess
        } == true
        if (!activeWallpaperValid) {
            val bundled = runCatching { engine.themeValues().wallpaperAsset?.let { AssetStore(this).wallpaper(it) } }.getOrNull()
            if (bundled?.isFile == true) runCatching { assets.setLauncherWallpaper(bundled).getOrThrow() }
        }
        widgetManager = getSystemService(AppWidgetManager::class.java)
        widgetHost = AppWidgetHost(this, WIDGET_HOST_ID)
        buildUi()
    }

    override fun onResume() {
        super.onResume()
        if (::content.isInitialized) {
            runCatching { widgetHost.startListening() }
            UiTheme.bind(engine)
            currentPage = engine.homePage()
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
        wallpaperBitmap?.recycle()
        wallpaperBitmap = null
        super.onDestroy()
    }

    private fun buildUi() {
        UiTheme.bind(engine)
        root = FrameLayout(this)
        wallpaperImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = UiTheme.homeBackground()
            wallpaperBitmap = assets.activeWallpaper()?.let { ImageAssetValidation.decodeSampled(it, 2048) }
            setImageBitmap(wallpaperBitmap)
            contentDescription = "Launcher wallpaper"
        }
        root.addView(wallpaperImage, FrameLayout.LayoutParams(-1, -1))
        root.addView(FrameLayout(this).apply {
            setBackgroundColor(Color.argb(52, Color.red(UiTheme.bg), Color.green(UiTheme.bg), Color.blue(UiTheme.bg)))
        }, FrameLayout.LayoutParams(-1, -1))

        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(2), 0, 0, 0)
        }
        val brandMark = FrameLayout(this).apply {
            background = UiTheme.rounded(UiTheme.accent, 14f)
            addView(TextView(this@MainActivity).apply {
                text = "A"
                textSize = 18f
                typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
                gravity = Gravity.CENTER
                setTextColor(UiTheme.contrasting(UiTheme.accent))
            }, FrameLayout.LayoutParams(-1, -1))
            contentDescription = "AI Control Launcher"
        }
        top.addView(brandMark, LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(10) })
        top.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@MainActivity).apply {
                text = "AI Control"
                textSize = 15f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setTextColor(UiTheme.textPrimary)
            })
            addView(TextView(this@MainActivity).apply {
                text = "YOUR HOME SPACE"
                textSize = 9f
                letterSpacing = 0.09f
                setTextColor(UiTheme.textMuted)
            })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val toolsButton = Button(this).apply {
            text = "⋮"
            textSize = 22f
            minWidth = 0
            minHeight = 0
            isAllCaps = false
            setTextColor(UiTheme.textPrimary)
            background = UiTheme.rounded(UiTheme.card2, 16f)
            contentDescription = "Open launcher tools: assistant, themes, wallpapers, and settings"
            setOnClickListener { showLauncherMenu(this) }
        }
        top.addView(toolsButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        content.addView(top, LinearLayout.LayoutParams(-1, dp(54)))

        val pageHeader = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        pageTitle = TextView(this).apply {
            textSize = 10f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(UiTheme.textMuted)
            letterSpacing = 0.06f
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        pageHeader.addView(pageTitle)
        pageHeader.addView(smallControl("＋  Widget", 88) { addAndroidWidget() }.apply {
            contentDescription = "Add an Android widget to this page"
        })
        content.addView(pageHeader, LinearLayout.LayoutParams(-1, dp(44)))

        workspace = SwipeWorkspaceLayout(this) { towardRight ->
            setHomePage(currentPage + if (towardRight) -1 else 1)
        }.apply { clipChildren = true }
        repeat(3) { page ->
            homePages += createHomePage(page)
            workspace.addView(homePages.last(), FrameLayout.LayoutParams(-1, -1).apply { bottomMargin = dp(48) })
        }
        content.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))

        pageIndicator = LinearLayout(this).apply { gravity = Gravity.CENTER }
        repeat(3) { page ->
            pageIndicator.addView(FrameLayout(this).apply {
                contentDescription = "${listOf("Left", "Main", "Right")[page]} home page${if (page == currentPage) ", selected" else ""}"
                isClickable = true
                isFocusable = true
                setOnClickListener { setHomePage(page) }
                addView(View(this@MainActivity).apply {
                    tag = "page_indicator_dot"
                    background = UiTheme.rounded(if (page == currentPage) UiTheme.accent else UiTheme.textMuted, 4f)
                }, FrameLayout.LayoutParams(dp(if (page == currentPage) 18 else 6), dp(5), Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        workspace.addView(pageIndicator, FrameLayout.LayoutParams(-1, dp(48), Gravity.BOTTOM))

        val bottom = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(7), dp(4), dp(6), dp(4))
            background = UiTheme.rounded(UiTheme.card, 23f, UiTheme.textMuted, 1)
        }
        dock = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f)
        }
        bottom.addView(dock)
        appDrawerButton = Button(this).apply {
            text = "▦\nApps"
            textSize = 10f
            gravity = Gravity.CENTER
            isAllCaps = false
            setTextColor(UiTheme.textPrimary)
            contentDescription = "Open app drawer"
            background = UiTheme.rounded(UiTheme.card2, 17f)
            setOnClickListener { showDrawer() }
        }
        bottom.addView(appDrawerButton, LinearLayout.LayoutParams(dp(60), dp(54)).apply { leftMargin = dp(5) })
        content.addView(bottom, LinearLayout.LayoutParams(-1, dp(64)).apply { topMargin = dp(4) })

        drawerOverlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            background = UiTheme.homeBackground()
            visibility = View.GONE
        }
        val drawerTop = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        drawerTop.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            addView(TextView(this@MainActivity).apply {
                text = "All apps"
                textSize = 21f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setTextColor(UiTheme.textPrimary)
            })
            addView(TextView(this@MainActivity).apply {
                text = "Your installed apps"
                textSize = 11f
                setTextColor(UiTheme.textMuted)
            })
        })
        drawerTop.addView(smallControl("Home", 58) { hideDrawer() }.apply {
            contentDescription = "Close app drawer and return to home"
        })
        drawerOverlay.addView(drawerTop, LinearLayout.LayoutParams(-1, dp(52)))

        val drawerActions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        drawerActions.addView(TextView(this).apply {
            text = "Find and launch"
            textSize = 12f
            setTextColor(UiTheme.textMuted)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        })
        drawerActions.addView(smallControlWithView("Sort · ${engine.drawerSort()}", 104) {
            val choices = listOf("A–Z", "Z–A", "Package")
            val next = choices[(choices.indexOf(engine.drawerSort()).coerceAtLeast(0) + 1) % choices.size]
            engine.setDrawerSort(next)
            (it as Button).text = "Sort · $next"
            renderApps()
        })
        drawerOverlay.addView(drawerActions, LinearLayout.LayoutParams(-1, dp(44)))

        drawerSearch = EditText(this).apply {
            hint = "Search apps"
            setSingleLine()
            textSize = 14f
            typeface = UiTheme.font()
            setTextColor(UiTheme.textPrimary)
            setHintTextColor(UiTheme.textMuted)
            setPadding(dp(15), 0, dp(15), 0)
            background = UiTheme.rounded(UiTheme.card, 22f)
            contentDescription = "Search installed apps"
        }
        drawerOverlay.addView(drawerSearch, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(5); bottomMargin = dp(8) })
        drawerSearch.addTextChangedListener(SearchWatcher { renderApps() })
        val appScroll = ScrollView(this).apply { overScrollMode = ScrollView.OVER_SCROLL_IF_CONTENT_SCROLLS }
        drawerGrid = GridLayout(this).apply {
            columnCount = engine.homeColumns()
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        appScroll.addView(drawerGrid)
        drawerOverlay.addView(appScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        drawerOverlay.addView(TextView(this).apply {
            text = "Touch and hold an app to add it to Home or Dock, or hide it from this launcher."
            textSize = 11f
            typeface = UiTheme.font()
            setTextColor(UiTheme.textMuted)
            setPadding(dp(4), dp(8), dp(4), 0)
        })
        root.addView(drawerOverlay, FrameLayout.LayoutParams(-1, -1))

        setContentView(root)
        updateAssistantCaption()
        updateHomePageChrome()
        render()
        if (intent.getBooleanExtra(EXTRA_REQUEST_ADD_WIDGET, false)) {
            intent.removeExtra(EXTRA_REQUEST_ADD_WIDGET)
            root.post { addAndroidWidget() }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_REQUEST_ADD_WIDGET, false) && ::root.isInitialized) {
            intent.removeExtra(EXTRA_REQUEST_ADD_WIDGET)
            root.post { addAndroidWidget() }
        }
    }

    private fun createHomePage(page: Int): LinearLayout {
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (page == currentPage) View.VISIBLE else View.GONE
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; clipToPadding = false }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(2), dp(2), dp(2), dp(8))
        }
        if (page == 1) {
            val hero = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(3), dp(8), dp(14))
            }
            hero.addView(android.widget.TextClock(this).apply {
                format12Hour = "EEEE  ·  MMMM d"
                format24Hour = "EEEE  ·  MMMM d"
                textSize = 13f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setTextColor(UiTheme.textPrimary)
                alpha = 0.82f
                letterSpacing = 0.02f
                contentDescription = "Today's date"
            })
            hero.addView(android.widget.TextClock(this).apply {
                format12Hour = "h:mm"
                format24Hour = "HH:mm"
                textSize = 62f
                typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
                setTextColor(UiTheme.textPrimary)
                contentDescription = "Current time"
                includeFontPadding = false
            }, LinearLayout.LayoutParams(-1, dp(70)))
            body.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        }

        body.addView(TextView(this).apply {
            text = when (page) {
                1 -> "Favourites"
                0 -> "Left page"
                else -> "Right page"
            }
            textSize = 15f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(UiTheme.textPrimary)
            setPadding(dp(5), dp(9), 0, dp(7))
        })
        val shortcuts = GridLayout(this).apply {
            columnCount = engine.homeColumns()
            alignmentMode = GridLayout.ALIGN_BOUNDS
            useDefaultMargins = false
        }
        homeShortcutGrids += shortcuts
        body.addView(shortcuts, LinearLayout.LayoutParams(-1, -2))
        val widgets = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        pageWidgetContainers += widgets
        body.addView(widgets, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(5) })
        body.addView(TextView(this).apply {
            text = if (page == 1) "Touch and hold an app in Apps to pin it here." else "Swipe between pages · add apps or widgets from this space."
            textSize = 11f
            typeface = UiTheme.font()
            setTextColor(UiTheme.textMuted)
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(9), dp(12), dp(9))
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        scroll.addView(body)
        screen.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return screen
    }

    private fun smallControl(label: String, width: Int, action: () -> Unit): Button = Button(this).apply {
        text = label
        textSize = if (label.length > 8) 9f else 10f
        isAllCaps = false
        minWidth = 0
        minHeight = 0
        setTextColor(UiTheme.textPrimary)
        background = UiTheme.rounded(UiTheme.card2, 16f)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(dp(width), dp(44)).apply { leftMargin = dp(3); rightMargin = dp(2) }
    }

    private fun showLauncherMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, "AI assistant")
            menu.add(0, 2, 1, "Theme gallery")
            menu.add(0, 3, 2, "Wallpapers & assets")
            menu.add(0, 4, 3, "Launcher settings")
            setOnMenuItemClickListener { item ->
                val target = when (item.itemId) {
                    1 -> AssistantActivity::class.java
                    2 -> ThemeGalleryActivity::class.java
                    3 -> AssetsActivity::class.java
                    4 -> LauncherSettingsActivity::class.java
                    else -> return@setOnMenuItemClickListener false
                }
                startActivity(Intent(this@MainActivity, target))
                true
            }
        }.show()
    }

    private fun smallControlWithView(label: String, width: Int, action: (View) -> Unit): Button = Button(this).apply {
        text = label
        textSize = 9f
        isAllCaps = false
        minWidth = 0
        minHeight = 0
        setTextColor(UiTheme.textPrimary)
        background = UiTheme.rounded(UiTheme.card2, 16f)
        setOnClickListener { action(this) }
        layoutParams = LinearLayout.LayoutParams(dp(width), dp(44)).apply { leftMargin = dp(3); rightMargin = dp(2) }
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
            "Available themes: ${engine.availableThemes().sorted().joinToString(", ")}. " +
            "Installed compatible icon packs: ${iconPacks.assistantChoices().keys.sorted().ifEmpty { listOf("none") }.joinToString(", ")}."
        Toast.makeText(this, "Contacting ${settings.provider}…", Toast.LENGTH_SHORT).show()
        aiScope.launch {
            try {
                val reply = withContext(Dispatchers.IO) {
                    provider.chat(prompt, state, aiHistory.toList()).getOrThrow()
                }
                val decision = try {
                    AiPlanValidator.parse(reply.text, engine.availableThemes(), iconPacks.assistantChoices(), promptContext = prompt)
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
            is AiThemeOperation.Create -> details += "Build theme ‘${theme.values.name}’ (${theme.values.layout} layout, ${theme.values.style} cards, ${theme.values.iconStyle} icons)."
            is AiThemeOperation.Apply -> details += "Apply the ‘${theme.name}’ theme."
            null -> Unit
        }
        plan.actions.forEach { action ->
            details += when (action) {
                is AiLauncherAction.SearchAssets -> "Search Wikimedia Commons for ‘${action.query}’. For a new theme this is saved as a draft; choose a licensed image and preview it before applying."
                AiLauncherAction.AddWidget -> "Open Android’s widget picker so you can choose an installed widget."
                is AiLauncherAction.SetLayout -> "Set launcher layout to ‘${action.value}’."
                is AiLauncherAction.SetStyle -> "Set card style to ‘${action.value}’."
                is AiLauncherAction.ApplyInstalledIconPack -> "Apply installed icon pack ‘${action.label}’ (${action.packageName}); only matching icons change."
                AiLauncherAction.BrowseIconPacks -> "Open the official Google Play icon-pack search; installation remains under your control."
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
        val continuation = plan.actions.any {
            it is AiLauncherAction.SearchAssets || it === AiLauncherAction.AddWidget || it === AiLauncherAction.BrowseIconPacks
        }
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
        var assetQuery: String? = null
        var chooseWidget = false
        var browseIconPacks = false
        plan.actions.forEach { action ->
            when (action) {
                is AiLauncherAction.SearchAssets -> assetQuery = action.query
                AiLauncherAction.AddWidget -> chooseWidget = true
                is AiLauncherAction.SetLayout -> engine.setLayout(action.value)
                is AiLauncherAction.SetStyle -> engine.setStyle(action.value)
                is AiLauncherAction.ApplyInstalledIconPack -> {
                    check(iconPacks.installedPacks().any { it.packageName == action.packageName }) {
                        "That icon pack is no longer installed or compatible. No pack was applied."
                    }
                    engine.setInstalledIconPack(action.packageName)
                    iconPacks.clearCache()
                }
                AiLauncherAction.BrowseIconPacks -> browseIconPacks = true
            }
        }
        val themeDraftName = (plan.theme as? AiThemeOperation.Create)?.values?.name
        when (val theme = plan.theme) {
            is AiThemeOperation.Create -> if (assetQuery != null) engine.saveThemeDraft(theme.values) else saveTheme(theme.values)
            is AiThemeOperation.Apply -> engine.setTheme(theme.name)
            null -> Unit
        }
        commandInput.text.clear()
        render()
        when {
            assetQuery != null -> startActivity(Intent(this, AssetsActivity::class.java)
                .putExtra(AssetsActivity.EXTRA_SUGGESTED_QUERY, assetQuery)
                .putExtra(AssetsActivity.EXTRA_THEME_NAME, themeDraftName.orEmpty()))
            chooseWidget -> addAndroidWidget()
            browseIconPacks -> IconPackStore.open(this).getOrThrow()
        }
        val confirmation = if (assetQuery != null && themeDraftName != null) {
            "Theme draft saved. A licensed wallpaper and known-label icons will be bundled automatically for review before apply."
        } else "Confirmed plan applied. You can change installed icon packs in Launcher settings."
        Toast.makeText(this, confirmation, Toast.LENGTH_LONG).show()
    }

    private fun reviewPrompt(result: PromptInterpretation.Ready, installed: List<AppInfo>) {
        val command = result.command
        val isLaunch = command is LauncherCommand.LaunchApp
        val builder = AlertDialog.Builder(this).setTitle(
            if (command is LauncherCommand.CreateTheme) "Preview ${command.values.name}" else "Review command"
        )
        if (command is LauncherCommand.CreateTheme) {
            builder.setView(themePreview(command.values))
            builder.setNeutralButton("Save draft + find wallpaper") { _, _ ->
                try {
                    engine.saveThemeDraft(command.values)
                    commandInput.text.clear()
                    render()
                    startActivity(Intent(this, AssetsActivity::class.java).putExtra(
                        AssetsActivity.EXTRA_SUGGESTED_QUERY,
                        com.aicontrol.launcher.theme.ThemeSpec.suggestedWallpaperQuery(command.values.name)
                    ).putExtra(AssetsActivity.EXTRA_THEME_NAME, command.values.name))
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
        val backgroundText = UiTheme.contrasting(background)
        val wallpaperPreview = previewAssets.wallpaper(values, 92)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            this.background = UiTheme.rounded(background, 22f, accent, 1)
            addView(TextView(this@MainActivity).apply {
                text = "${values.name}  ·  ${values.style.uppercase(Locale.getDefault())}"
                textSize = 18f
                setTextColor(backgroundText)
            })
            wallpaperPreview?.let { addView(it, LinearLayout.LayoutParams(-1, dp(92)).apply { topMargin = dp(7) }) }
            if (values.wallpaperAsset != null && wallpaperPreview == null) addView(TextView(this@MainActivity).apply {
                text = "Wallpaper file unavailable · it must be rebuilt before apply"
                textSize = 10f
                setTextColor(UiTheme.accent)
            })
            addView(TextView(this@MainActivity).apply {
                text = "${values.layout} layout · ${values.typography} type · ${values.iconStyle} icons · ${values.backgroundStyle} background. " +
                    (if (values.iconAssets.isEmpty()) "Known app labels auto-map to OpenMoji." else "${values.iconAssets.size} saved app-icon mappings.")
                textSize = 11f
                setTextColor(backgroundText)
                setPadding(0, dp(3), 0, dp(12))
            })
            val samples = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            listOf("Accent" to accent, "Secondary" to accent2, "Cards" to card).forEach { (label, color) ->
                samples.addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(4), dp(4), dp(4), dp(4))
                    addView(View(this@MainActivity).apply {
                        setBackground(UiTheme.rounded(color, 8f))
                        layoutParams = LinearLayout.LayoutParams(-1, dp(38))
                    })
                    addView(TextView(this@MainActivity).apply {
                        text = label
                        textSize = 10f
                        setTextColor(UiTheme.contrasting(color))
                    })
                }, LinearLayout.LayoutParams(0, -2, 1f))
            }
            addView(samples)
            addView(TextView(this@MainActivity).apply {
                text = "Background ${values.background} · Accent ${values.accent}\nSecondary ${values.accent2} · Card ${values.card}"
                textSize = 10f
                setTextColor(backgroundText)
                setPadding(dp(4), dp(2), dp(4), 0)
            })
            addView(TextView(this@MainActivity).apply {
                text = "KNOWN APP LABELS · LOCAL OPENMOJI MAPPING"
                textSize = 9f
                setTextColor(backgroundText)
                setPadding(0, dp(8), 0, dp(2))
            })
            addView(previewAssets.iconStrip(values, maxIcons = 4, iconSizeDp = 30),
                LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(2) })
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
                    .put("typography", values.typography)
                    .put("iconStyle", values.iconStyle)
                    .put("backgroundStyle", values.backgroundStyle)
                    .put("layout", values.layout)
                    .put("wallpaperAsset", values.wallpaperAsset ?: "")
                    .put("iconPackPackage", values.iconPackPackage ?: "")
                    .put("iconAssets", org.json.JSONObject().apply { values.iconAssets.forEach { (pkg, asset) -> put(pkg, asset) } })
            )).toString()
        ).getOrThrow()
    }

    @Deprecated("Back dispatch is retained for this API-30-only launcher.")
    override fun onBackPressed() {
        if (drawerShowing) hideDrawer() else super.onBackPressed()
    }

    private fun setHomePage(requested: Int) {
        val target = requested.coerceIn(0, 2)
        if (target == currentPage) return
        val previous = currentPage
        currentPage = target
        engine.setHomePage(target)
        homePages.forEach { page ->
            page.animate().cancel()
            page.alpha = 1f
            page.translationX = 0f
        }
        val outgoing = homePages[previous]
        val incoming = homePages[target]
        homePages.forEachIndexed { index, page ->
            if (index != previous && index != target) page.visibility = View.GONE
        }
        incoming.visibility = View.VISIBLE
        incoming.alpha = 0f
        val direction = if (target > previous) 1 else -1
        val transitionDistance = maxOf(dp(48).toFloat(), workspace.width * 0.22f)
        incoming.translationX = direction * transitionDistance
        outgoing.animate().alpha(0f).translationX(-direction * transitionDistance).setDuration(200).withEndAction {
            outgoing.visibility = View.GONE
            outgoing.alpha = 1f
            outgoing.translationX = 0f
        }.start()
        incoming.animate().alpha(1f).translationX(0f).setDuration(230).start()
        updateHomePageChrome()
        renderHomeShortcuts()
        renderWidgets()
    }

    private fun updateHomePageChrome() {
        if (::pageTitle.isInitialized) pageTitle.text = "HOME  ·  ${listOf("LEFT PAGE", "MAIN PAGE", "RIGHT PAGE")[currentPage]}"
        if (::pageIndicator.isInitialized) {
            for (index in 0 until pageIndicator.childCount) {
                val target = pageIndicator.getChildAt(index) as? FrameLayout ?: continue
                val dot = target.getChildAt(0) ?: continue
                dot.background = UiTheme.rounded(if (index == currentPage) UiTheme.accent else UiTheme.textMuted, 4f)
                val params = dot.layoutParams as? FrameLayout.LayoutParams ?: continue
                val targetWidth = dp(if (index == currentPage) 18 else 6)
                if (params.width != targetWidth) {
                    params.width = targetWidth
                    dot.layoutParams = params
                }
                target.contentDescription = "${listOf("Left", "Main", "Right")[index]} home page${if (index == currentPage) ", selected" else ""}"
            }
        }
        homePages.forEachIndexed { index, page ->
            if (index != currentPage && page.visibility != View.VISIBLE) page.visibility = View.GONE
            else if (index == currentPage) page.visibility = View.VISIBLE
        }
    }

    private fun showDrawer() {
        if (!::drawerOverlay.isInitialized) return
        drawerShowing = true
        drawerOverlay.visibility = View.VISIBLE
        renderApps()
    }

    private fun hideDrawer() {
        if (!::drawerOverlay.isInitialized) return
        drawerShowing = false
        drawerOverlay.visibility = View.GONE
    }

    private fun render() {
        if (!::root.isInitialized) return
        UiTheme.bind(engine)
        wallpaperImage.background = UiTheme.homeBackground()
        val nextWallpaper = assets.activeWallpaper()?.let { ImageAssetValidation.decodeSampled(it, 2048) }
        wallpaperImage.setImageBitmap(nextWallpaper)
        wallpaperBitmap?.takeIf { it !== nextWallpaper }?.recycle()
        wallpaperBitmap = nextWallpaper
        (root.getChildAt(1) as? FrameLayout)?.setBackgroundColor(
            Color.argb(72, Color.red(UiTheme.bg), Color.green(UiTheme.bg), Color.blue(UiTheme.bg))
        )
        if (::drawerOverlay.isInitialized) drawerOverlay.background = UiTheme.homeBackground()
        updateAssistantCaption()
        updateHomePageChrome()
        renderHomeShortcuts()
        renderApps()
        renderDock()
        renderWidgets()
    }

    private fun addAndroidWidget() {
        pendingWidgetPage = currentPage
        hideDrawer()
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
        workspaceStore.addWidget(id, pendingWidgetPage)
        pendingWidgetId = -1
        currentPage = pendingWidgetPage
        engine.setHomePage(currentPage)
        updateHomePageChrome()
        renderWidgets()
        Toast.makeText(this, "Widget added to the ${listOf("left", "main", "right")[currentPage]} page", Toast.LENGTH_SHORT).show()
    }

    private fun renderWidgets() {
        if (!::widgetHost.isInitialized || !::widgetManager.isInitialized || pageWidgetContainers.size != 3) return
        pageWidgetContainers.forEach { it.removeAllViews() }
        workspaceStore.widgetIds().sorted().forEach { id ->
            val page = workspaceStore.widgetPage(id)
            val container = pageWidgetContainers.getOrNull(page) ?: return@forEach
            val info = widgetManager.getAppWidgetInfo(id) ?: return@forEach
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(8), dp(5), dp(8), dp(7))
                UiTheme.styleCard(this, UiTheme.card, true)
            }
            val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            header.addView(TextView(this).apply {
                text = info.loadLabel(packageManager)
                textSize = 11f
                typeface = UiTheme.font()
                setTextColor(UiTheme.textMuted)
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            })
            header.addView(smallControl("Remove", 64) { confirmRemoveWidget(id, info.loadLabel(packageManager).toString()) })
            card.addView(header)
            val hostView = widgetHost.createView(this, id, info).apply {
                setAppWidget(id, info)
                contentDescription = "${info.loadLabel(packageManager)} home-screen widget"
            }
            card.addView(hostView, LinearLayout.LayoutParams(-1, info.minHeight.coerceAtLeast(dp(72))))
            container.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(7) })
        }
    }

    private fun confirmRemoveWidget(id: Int, label: String) {
        AlertDialog.Builder(this)
            .setTitle("Remove widget?")
            .setMessage("Remove $label from this launcher's home screen?")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Remove") { _, _ ->
                workspaceStore.removeWidget(id)
                widgetHost.deleteAppWidgetId(id)
                renderWidgets()
            }
            .show()
    }

    private fun renderHomeShortcuts() {
        if (homeShortcutGrids.size != 3) return
        val allApps = apps.listLaunchableApps()
        val columns = engine.homeColumns()
        homeShortcutGrids.forEachIndexed { page, pageGrid ->
            pageGrid.removeAllViews()
            pageGrid.columnCount = columns
            workspaceStore.shortcuts(page).forEach { placement ->
                val app = allApps.firstOrNull { it.packageName == placement.packageName && it.activityName == placement.activityName }
                    ?: allApps.firstOrNull { it.packageName == placement.packageName } ?: return@forEach
                pageGrid.addView(createAppTile(app, engine.showLabels()) {
                    apps.launch(app)
                }.apply {
                    setOnLongClickListener { showHomeShortcutMenu(placement, app); true }
                }, GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                })
            }
            pageGrid.invalidate()
        }
    }

    private fun renderApps() {
        if (!::drawerGrid.isInitialized) return
        drawerSearch.visibility = if (engine.drawerSearchVisible()) View.VISIBLE else View.GONE
        drawerGrid.removeAllViews()
        drawerGrid.columnCount = engine.homeColumns()
        val q = if (engine.drawerSearchVisible()) drawerSearch.text?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty() else ""
        val hidden = engine.hiddenPackages()
        val candidates = apps.listLaunchableApps().filterNot { hidden.contains(it.packageName) }
            .filter { q.isEmpty() || it.label.lowercase(Locale.getDefault()).contains(q) || it.packageName.lowercase(Locale.getDefault()).contains(q) }
        val visible = when (engine.drawerSort()) {
            "Z–A" -> candidates.sortedByDescending { it.label.lowercase(Locale.getDefault()) }
            "Package" -> candidates.sortedWith(compareBy<AppInfo> { it.packageName.lowercase(Locale.getDefault()) }.thenBy { it.label.lowercase(Locale.getDefault()) })
            else -> candidates.sortedWith(compareBy<AppInfo> { it.label.lowercase(Locale.getDefault()) }.thenBy { it.packageName })
        }
        visible.forEach { app ->
            drawerGrid.addView(createAppTile(app, engine.drawerLabels()) { apps.launch(app) }.apply {
                setOnLongClickListener { showAppMenu(app); true }
            }, GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
    }

    private fun createAppTile(app: AppInfo, showLabel: Boolean, click: () -> Unit): LinearLayout {
        val tile = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(7), dp(4), dp(5))
            setOnClickListener { click() }
        }
        val size = dp(engine.iconSize())
        val pack = engine.installedIconPack().takeIf { it.isNotBlank() }?.let {
            iconPacks.iconDrawable(it, app.packageName, app.activityName)
        }
        val themeIcon = engine.themeIconFile(engine.theme(), app.packageName)?.let {
            android.graphics.drawable.Drawable.createFromPath(it.absolutePath)
        }
        val automaticIcon = previewAssets.bundledIconFile(app.label, engine.theme())?.let {
            android.graphics.drawable.Drawable.createFromPath(it.absolutePath)
        }
        val icon = ImageView(this).apply {
            setImageDrawable(themeIcon ?: assets.iconDrawable(app.packageName) ?: pack ?: automaticIcon ?: app.icon)
            contentDescription = app.label
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = UiTheme.rounded(UiTheme.card2, UiTheme.iconRadiusDp(size))
            clipToOutline = true
        }
        tile.addView(icon, LinearLayout.LayoutParams(size, size))
        if (showLabel) tile.addView(TextView(this).apply {
            text = app.label
            textSize = 10.5f
            typeface = UiTheme.font()
            gravity = Gravity.CENTER
            setTextColor(UiTheme.textPrimary)
            maxLines = 2
            setPadding(0, dp(3), 0, 0)
        }, LinearLayout.LayoutParams(-1, -2))
        return tile
    }

    private fun showAppMenu(app: AppInfo) {
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(arrayOf("Launch", "Add shortcut to home", "Add to dock", "Hide app")) { _, which ->
                when (which) {
                    0 -> apps.launch(app)
                    1 -> chooseHomePage(app)
                    2 -> { addDockShortcut(app); renderDock() }
                    3 -> {
                        engine.setAppVisible(app.packageName, false)
                        renderApps()
                    }
                }
            }
            .show()
    }

    private fun showHomeShortcutMenu(placement: WorkspaceStore.Shortcut, app: AppInfo) {
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(arrayOf("Launch", "Move to another page", "Move earlier", "Move later", "Add to dock", "Remove from home")) { _, which ->
                when (which) {
                    0 -> apps.launch(app)
                    1 -> chooseHomePage(app, placement.page)
                    2 -> { workspaceStore.shift(placement.packageName, placement.activityName, -1); renderHomeShortcuts() }
                    3 -> { workspaceStore.shift(placement.packageName, placement.activityName, 1); renderHomeShortcuts() }
                    4 -> { addDockShortcut(app); renderDock() }
                    5 -> {
                        workspaceStore.remove(placement.packageName, placement.activityName)
                        renderHomeShortcuts()
                    }
                }
            }
            .show()
    }

    private fun chooseHomePage(app: AppInfo, current: Int? = null) {
        val names = arrayOf("Left page", "Main page", "Right page")
        AlertDialog.Builder(this)
            .setTitle(if (current == null) "Add ${app.label} to…" else "Move ${app.label} to…")
            .setItems(names) { _, page ->
                val alreadyPlaced = workspaceStore.shortcuts().any { it.packageName == app.packageName && it.activityName == app.activityName }
                val count = workspaceStore.shortcuts(page).size
                if (!alreadyPlaced && count >= engine.homeRows() * engine.homeColumns()) {
                    Toast.makeText(this, "That page is full. Increase its grid capacity in Settings.", Toast.LENGTH_LONG).show()
                    return@setItems
                }
                workspaceStore.addOrMove(app.label, app.packageName, app.activityName, page)
                renderHomeShortcuts()
                setHomePage(page)
                Toast.makeText(this, "Shortcut placed on the ${names[page].lowercase()}", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun addDockShortcut(app: AppInfo) {
        applyLauncherAction(
            org.json.JSONObject().put("action", "ADD_SHORTCUT").put("label", app.label)
                .put("package", app.packageName).put("activity", app.activityName)
        )
        Toast.makeText(this, "Added to dock", Toast.LENGTH_SHORT).show()
    }

    private fun applyLauncherAction(action: org.json.JSONObject) {
        engine.applyJson(org.json.JSONObject().put("actions", org.json.JSONArray().put(action)).toString())
    }

    private fun renderDock() {
        if (!::dock.isInitialized) return
        dock.removeAllViews()
        dock.visibility = if (engine.dockVisible()) View.VISIBLE else View.GONE
        val allApps = apps.listLaunchableApps()
        val items = engine.shortcuts().take(engine.dockCount())
        items.forEach { item ->
            val pkg = item.optString("package")
            val activity = item.optString("activity")
            val app = allApps.firstOrNull { it.packageName == pkg && (activity.isBlank() || it.activityName == activity) }
                ?: allApps.firstOrNull { it.packageName == pkg } ?: return@forEach
            val pack = engine.installedIconPack().takeIf { it.isNotBlank() }?.let {
                iconPacks.iconDrawable(it, app.packageName, app.activityName)
            }
            val themeIcon = engine.themeIconFile(engine.theme(), app.packageName)?.let {
                android.graphics.drawable.Drawable.createFromPath(it.absolutePath)
            }
            val automaticIcon = previewAssets.bundledIconFile(app.label, engine.theme())?.let {
                android.graphics.drawable.Drawable.createFromPath(it.absolutePath)
            }
            dock.addView(ImageButton(this).apply {
                setImageDrawable(themeIcon ?: assets.iconDrawable(app.packageName) ?: pack ?: automaticIcon ?: app.icon)
                contentDescription = item.optString("label", app.label)
                background = UiTheme.rounded(Color.TRANSPARENT, 16f)
                setPadding(dp(6), dp(6), dp(6), dp(6))
                setOnClickListener { apps.launch(app) }
                setOnLongClickListener {
                    AlertDialog.Builder(this@MainActivity).setTitle(app.label)
                        .setMessage("Remove this app shortcut from the dock?")
                        .setNegativeButton("Cancel", null)
                        .setPositiveButton("Remove") { _, _ ->
                            applyLauncherAction(org.json.JSONObject().put("action", "REMOVE_SHORTCUT")
                                .put("label", item.optString("label")).put("package", pkg))
                            renderDock()
                        }.show()
                    true
                }
                layoutParams = LinearLayout.LayoutParams(0, dp(54), 1f).apply {
                    leftMargin = dp(2); rightMargin = dp(2)
                }
            })
        }
        if (engine.dockVisible() && items.size < engine.dockCount()) {
            dock.addView(TextView(this).apply {
                text = "+"
                textSize = 24f
                gravity = Gravity.CENTER
                setTextColor(UiTheme.accent)
                contentDescription = "Add an app shortcut to the dock"
                setOnClickListener { showDrawer() }
                layoutParams = LinearLayout.LayoutParams(0, dp(54), 1f)
            })
        }
    }

}

private class SearchWatcher(private val changed: () -> Unit) : android.text.TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = changed()
    override fun afterTextChanged(s: android.text.Editable?) = Unit
}

@SuppressLint("ViewConstructor")
private class SwipeWorkspaceLayout(
    context: android.content.Context,
    private val onSwipe: (towardRight: Boolean) -> Unit
) : FrameLayout(context) {
    private val gesture = WorkspaceSwipeGesture(
        android.view.ViewConfiguration.get(context).scaledTouchSlop,
        resources.displayMetrics.density
    )
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var pendingDirection: WorkspaceSwipeDirection? = null

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(event.actionIndex)
                pendingDirection = null
                gesture.begin(event.getX(event.actionIndex), event.getY(event.actionIndex))
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) {
                    resetGesture()
                } else {
                    pendingDirection = gesture.move(event.getX(index), event.getY(index))
                    if (pendingDirection != null) return true
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointerId) {
                    val replacement = if (event.actionIndex == 0) 1 else 0
                    if (replacement < event.pointerCount) {
                        activePointerId = event.getPointerId(replacement)
                        gesture.begin(event.getX(replacement), event.getY(replacement))
                        pendingDirection = null
                    } else resetGesture()
                }
            }
            MotionEvent.ACTION_UP -> {
                val index = event.findPointerIndex(activePointerId).takeIf { it >= 0 } ?: event.actionIndex
                pendingDirection = gesture.finish(event.getX(index), event.getY(index))
                activePointerId = MotionEvent.INVALID_POINTER_ID
                if (pendingDirection != null) return true
            }
            MotionEvent.ACTION_CANCEL -> resetGesture()
        }
        return super.onInterceptTouchEvent(event)
    }

    /** Keep observing child sequences, but only take over after decisive horizontal intent. */
    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        super.requestDisallowInterceptTouchEvent(false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_UP -> {
                val index = event.findPointerIndex(activePointerId).takeIf { it >= 0 } ?: event.actionIndex
                val direction = pendingDirection ?: gesture.finish(event.getX(index), event.getY(index))
                if (direction != null) onSwipe(direction == WorkspaceSwipeDirection.RIGHT)
                else performClick()
                resetGesture()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                resetGesture()
                return true
            }
        }
        return true
    }

    private fun resetGesture() {
        gesture.cancel()
        activePointerId = MotionEvent.INVALID_POINTER_ID
        pendingDirection = null
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
