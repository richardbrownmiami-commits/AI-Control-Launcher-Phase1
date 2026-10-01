package com.aicontrol.launcher.ui

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.text.InputType
import android.widget.*
import com.aicontrol.launcher.ai.FreeModels
import com.aicontrol.launcher.data.SettingsStore

class SettingsActivity : Activity() {
    private lateinit var settings: SettingsStore
    private lateinit var models: Spinner
    private lateinit var openKey: EditText
    private lateinit var geminiKey: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = SettingsStore(this)
        buildUi()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22,22,22,22)
            setBackgroundColor(Color.rgb(16,18,24))
        }
        root.addView(TextView(this).apply {
            text="AI Provider"; textSize=24f; setTextColor(Color.WHITE); setPadding(0,0,0,14)
        })
        val radios = RadioGroup(this).apply { orientation=RadioGroup.HORIZONTAL }
        val open = RadioButton(this).apply { text="OpenRouter"; setTextColor(Color.WHITE); id=1001 }
        val gem = RadioButton(this).apply { text="Gemini"; setTextColor(Color.WHITE); id=1002 }
        radios.addView(open); radios.addView(gem)
        radios.check(if (settings.provider == SettingsStore.PROVIDER_GEMINI) 1002 else 1001)
        root.addView(radios)
        models=Spinner(this)
        root.addView(models)
        root.addView(TextView(this).apply {
            text="Free models only.\nOpenRouter keys: openrouter.ai/keys\nGemini keys: aistudio.google.com"
            setTextColor(Color.LTGRAY); setPadding(0,8,0,12)
        })
        openKey=keyField("OpenRouter API key")
        geminiKey=keyField("Gemini API key")
        root.addView(openKey); root.addView(geminiKey)
        openKey.setText(settings.openRouterKey)
        geminiKey.setText(settings.geminiKey)

        fun refresh(provider:String) {
            val list=FreeModels.forProvider(provider)
            models.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,list)
            models.setSelection(list.indexOf(settings.model).coerceAtLeast(0))
        }
        refresh(settings.provider)
        radios.setOnCheckedChangeListener { _, id ->
            refresh(if (id == 1002) SettingsStore.PROVIDER_GEMINI else SettingsStore.PROVIDER_OPENROUTER)
        }
        root.addView(Button(this).apply {
            text="Save"
            setOnClickListener {
                settings.provider=if (radios.checkedRadioButtonId == 1002) SettingsStore.PROVIDER_GEMINI else SettingsStore.PROVIDER_OPENROUTER
                settings.model=models.selectedItem.toString()
                settings.openRouterKey=openKey.text.toString().trim()
                settings.geminiKey=geminiKey.text.toString().trim()
                Toast.makeText(this@SettingsActivity,"AI settings saved",Toast.LENGTH_SHORT).show()
                finish()
            }
        })
        root.addView(TextView(this).apply {
            text="Keys are stored in app-private SharedPreferences for this prototype. Android Keystore is future work."
            setTextColor(Color.GRAY); setPadding(0,16,0,0)
        })
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun keyField(hint:String)=EditText(this).apply {
        this.hint=hint; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
        inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }
}