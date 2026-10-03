package com.aicontrol.launcher.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import com.aicontrol.launcher.actions.ActionEngine
import com.aicontrol.launcher.ai.*
import com.aicontrol.launcher.data.SettingsStore
import kotlinx.coroutines.*

class SettingsActivity : Activity() {
    private lateinit var store: SettingsStore
    private lateinit var models: Spinner
    private lateinit var providers: RadioGroup
    private lateinit var openRouterKey: EditText
    private lateinit var geminiKey: EditText
    private var openRouterId = -1
    private var geminiId = -1
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        UiTheme.bind(ActionEngine(this))
        store = SettingsStore(this)
        buildUi()
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(18))
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
            contentDescription = "Back"
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(TextView(this@SettingsActivity).apply {
                text = "AI provider"
                textSize = 21f
                typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
                setTextColor(UiTheme.textPrimary)
            })
            addView(TextView(this@SettingsActivity).apply {
                text = "Choose how the assistant connects"
                textSize = 11f
                setTextColor(UiTheme.textMuted)
            })
        })
        page.addView(header, LinearLayout.LayoutParams(-1, dp(52)))

        val scroll = ScrollView(this).apply { clipToPadding = false }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, dp(10))
        }
        body.addView(TextView(this).apply {
            text = "CONNECTION"
            textSize = 10f
            letterSpacing = 0.08f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(UiTheme.textMuted)
            setPadding(dp(3), dp(3), 0, dp(7))
        })
        val providerCard = card()
        providerCard.addView(TextView(this).apply {
            text = "Provider"
            textSize = 14f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(UiTheme.textPrimary)
        })
        providers = RadioGroup(this).apply { orientation = RadioGroup.HORIZONTAL }
        openRouterId = View.generateViewId()
        geminiId = View.generateViewId()
        val openRouter = providerChoice("OpenRouter", openRouterId)
        val gemini = providerChoice("Gemini", geminiId)
        providers.addView(openRouter, RadioGroup.LayoutParams(0, dp(48), 1f))
        providers.addView(gemini, RadioGroup.LayoutParams(0, dp(48), 1f))
        providers.check(if (store.provider == SettingsStore.PROVIDER_GEMINI) geminiId else openRouterId)
        providerCard.addView(providers, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(6) })

        models = Spinner(this).apply {
            background = UiTheme.rounded(UiTheme.card2, 14f)
            setPadding(dp(8), 0, dp(8), 0)
        }
        providerCard.addView(TextView(this).apply {
            text = "Model"
            textSize = 12f
            setTextColor(UiTheme.textMuted)
            setPadding(dp(1), dp(12), 0, dp(4))
        })
        providerCard.addView(models, LinearLayout.LayoutParams(-1, dp(48)))
        fun refreshModels(provider: String) {
            val options = FreeModels.forProvider(provider)
            models.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
            val current = options.indexOf(store.model)
            models.setSelection(if (current >= 0) current else 0)
        }
        refreshModels(store.provider)
        providers.setOnCheckedChangeListener { _, checked ->
            refreshModels(if (checked == geminiId) SettingsStore.PROVIDER_GEMINI else SettingsStore.PROVIDER_OPENROUTER)
        }
        body.addView(providerCard)

        body.addView(TextView(this).apply {
            text = "API KEYS"
            textSize = 10f
            letterSpacing = 0.08f
            typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
            setTextColor(UiTheme.textMuted)
            setPadding(dp(3), dp(18), 0, dp(7))
        })
        val keyCard = card()
        keyCard.addView(TextView(this).apply {
            text = "Paste the key for the provider you selected."
            textSize = 12f
            setTextColor(UiTheme.textMuted)
            setPadding(0, 0, 0, dp(5))
        })
        openRouterKey = keyField("OpenRouter API key").apply { setText(store.openRouterKey) }
        geminiKey = keyField("Gemini API key").apply { setText(store.geminiKey) }
        keyCard.addView(openRouterKey, LinearLayout.LayoutParams(-1, dp(48)).apply { bottomMargin = dp(7) })
        keyCard.addView(geminiKey, LinearLayout.LayoutParams(-1, dp(48)))
        keyCard.addView(TextView(this).apply {
            text = "Keys are stored in app-private preferences. Requests include your prompt, recent chat, and launcher appearance; selected local files are never uploaded."
            textSize = 11f
            setTextColor(UiTheme.textMuted)
            setPadding(0, dp(10), 0, 0)
        })
        body.addView(keyCard)

        val actions = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        actions.addView(actionButton("Test connection", false) { testConnection() },
            LinearLayout.LayoutParams(0, dp(48), 1f))
        actions.addView(actionButton("Save", true) {
            save()
            Toast.makeText(this@SettingsActivity, "AI settings saved", Toast.LENGTH_SHORT).show()
            finish()
        }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { leftMargin = dp(8) })
        body.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        scroll.addView(body)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(page)
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(14), dp(15), dp(14))
        UiTheme.styleCard(this, UiTheme.card, false)
    }

    private fun providerChoice(label: String, viewId: Int) = RadioButton(this).apply {
        id = viewId
        text = label
        textSize = 13f
        setTextColor(UiTheme.textPrimary)
        buttonTintList = ColorStateList.valueOf(UiTheme.accent)
        background = UiTheme.rounded(UiTheme.card2, 14f)
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), 0, dp(6), 0)
        layoutParams = RadioGroup.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) }
    }

    private fun keyField(hintText: String) = EditText(this).apply {
        hint = hintText
        textSize = 13f
        setTextColor(UiTheme.textPrimary)
        setHintTextColor(UiTheme.textMuted)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        background = UiTheme.rounded(UiTheme.card2, 14f)
        setPadding(dp(13), 0, dp(13), 0)
        setSingleLine(true)
    }

    private fun actionButton(label: String, primary: Boolean, action: () -> Unit) = Button(this).apply {
        text = label
        textSize = 13f
        isAllCaps = false
        setTextColor(if (primary) UiTheme.textOnAccent else UiTheme.textPrimary)
        background = if (primary) UiTheme.gradient(16f) else UiTheme.rounded(UiTheme.card2, 16f)
        setOnClickListener { action() }
    }

    private fun save() {
        store.provider = if (providers.checkedRadioButtonId == geminiId) SettingsStore.PROVIDER_GEMINI else SettingsStore.PROVIDER_OPENROUTER
        store.model = models.selectedItem?.toString().orEmpty()
        store.openRouterKey = openRouterKey.text.toString().trim()
        store.geminiKey = geminiKey.text.toString().trim()
    }

    private fun testConnection() {
        save()
        val selectedModel = models.selectedItem?.toString().orEmpty()
        if (selectedModel.isBlank()) {
            Toast.makeText(this, "Select a model first", Toast.LENGTH_LONG).show()
            return
        }
        if (store.activeKey().isBlank()) {
            Toast.makeText(this, "Add an API key for ${store.provider}", Toast.LENGTH_LONG).show()
            return
        }
        val provider = if (store.provider == SettingsStore.PROVIDER_GEMINI) GeminiProvider(selectedModel, store.geminiKey)
            else OpenRouterProvider(selectedModel, store.openRouterKey)
        Toast.makeText(this, "Testing ${store.provider}…", Toast.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                provider.chat("Reply in the required JSON schema with message set to Connection OK, clarification null, theme null, and actions empty.", "connection test", emptyList())
            }
            result.onSuccess {
                Toast.makeText(this@SettingsActivity, it.text.take(350), Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(this@SettingsActivity, it.message ?: "Connection test failed", Toast.LENGTH_LONG).show()
            }
        }
    }
}
