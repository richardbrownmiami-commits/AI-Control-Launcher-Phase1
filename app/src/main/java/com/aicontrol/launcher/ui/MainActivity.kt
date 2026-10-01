package com.aicontrol.launcher.ui

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import com.aicontrol.launcher.ai.OpenAiCompatibleProvider
import com.aicontrol.launcher.apps.AppRepository
import com.aicontrol.launcher.data.SettingsStore
import com.aicontrol.launcher.actions.ActionEngine
import kotlinx.coroutines.*

class MainActivity : Activity() {
    private lateinit var settings: SettingsStore
    private lateinit var apps: AppRepository
    private lateinit var engine: ActionEngine
    private lateinit var root: LinearLayout
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); settings=SettingsStore(this); apps=AppRepository(this); engine=ActionEngine(this); buildUi() }
    private fun buildUi() {
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(24,24,24,24); setBackgroundColor(Color.rgb(16,18,24)) }
        val title=TextView(this).apply { text="AI Control Launcher • Phase 1"; textSize=24f; setTextColor(Color.WHITE) }
        root.addView(title)
        val key=EditText(this).apply { hint="API key"; setSingleLine(); setText(settings.apiKey); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY); inputType=0x81 }
        root.addView(key)
        val model=EditText(this).apply { hint="Model (e.g. gpt-4o-mini)"; setSingleLine(); setText(settings.model); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        root.addView(model)
        val endpoint=EditText(this).apply { hint="OpenAI-compatible endpoint"; setSingleLine(); setText(settings.endpoint); setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        root.addView(endpoint)
        val command=EditText(this).apply { hint="Tell AI what to change..."; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY) }
        root.addView(command)
        val button=Button(this).apply { text="Save + Ask AI" }
        root.addView(button)
        val status=TextView(this).apply { setTextColor(Color.LTGRAY); setPadding(0,16,0,16) }; root.addView(status)
        val scroll=ScrollView(this); val grid=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }; scroll.addView(grid); root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        button.setOnClickListener { settings.apiKey=key.text.toString().trim(); settings.model=model.text.toString().trim(); settings.endpoint=endpoint.text.toString().trim(); status.text="Saved. Calling AI..."; scope.launch { val provider=OpenAiCompatibleProvider(settings.endpoint,settings.apiKey,settings.model); val state="theme=${engine.theme()}, layout=${engine.layout()}, hidden=${engine.hiddenPackages()}"; val result=provider.generatePlan(command.text.toString(),state); result.onSuccess { json -> engine.applyJson(json).onSuccess { n -> status.text="AI plan applied: $n action(s)\n$json"; renderApps(grid) }.onFailure { status.text="Plan rejected: ${it.message}\n$json" } }.onFailure { status.text="AI error: ${it.message}" } } }
        renderApps(grid); setContentView(root)
    }
    private fun renderApps(container: LinearLayout) { container.removeAllViews(); val hidden=engine.hiddenPackages(); apps.listLaunchableApps().filterNot { hidden.contains(it.packageName) }.forEach { app -> val b=Button(this).apply { text=app.label; gravity=Gravity.START; setOnClickListener { apps.launch(app) } }; container.addView(b) } }
    override fun onDestroy(){ scope.cancel(); super.onDestroy() }
}
