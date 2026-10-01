package com.aicontrol.launcher.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.aicontrol.launcher.actions.ActionEngine
import com.aicontrol.launcher.ai.AiTurn
import com.aicontrol.launcher.ai.createProvider
import com.aicontrol.launcher.apps.AppInfo
import com.aicontrol.launcher.apps.AppRepository
import com.aicontrol.launcher.assets.LauncherAssetManager
import com.aicontrol.launcher.data.SettingsStore
import kotlinx.coroutines.*

private const val NATIVE_ABI_GUARD="launcherabi"

class MainActivity:Activity(){
 private lateinit var settings:SettingsStore;private lateinit var apps:AppRepository;private lateinit var engine:ActionEngine;private lateinit var assets:LauncherAssetManager
 private lateinit var root:LinearLayout;private lateinit var grid:GridLayout;private lateinit var search:EditText;private lateinit var input:EditText;private lateinit var chat:LinearLayout;private lateinit var status:TextView
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main);private val history=ArrayDeque<AiTurn>()
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 override fun onCreate(b:Bundle?){super.onCreate(b);runCatching{System.loadLibrary(NATIVE_ABI_GUARD)};settings=SettingsStore(this);apps=AppRepository(this);engine=ActionEngine(this);assets=LauncherAssetManager(this);buildUi()}
 override fun onResume(){super.onResume();if(::root.isInitialized){root.background=UiTheme.gradient(0f);renderApps();updateStatus()}}
 private fun buildUi(){
  root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(16),dp(12),dp(16),dp(12));background=UiTheme.gradient(0f)}
  val bar=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
  bar.addView(TextView(this).apply{text="AI Control";textSize=23f;setTextColor(Color.WHITE);layoutParams=LinearLayout.LayoutParams(0,-2,1f)})
  bar.addView(btn("API"){startActivity(Intent(this@MainActivity,SettingsActivity::class.java))});bar.addView(btn("Assets"){startActivity(Intent(this@MainActivity,AssetsActivity::class.java))});bar.addView(btn("Theme"){val n=if(engine.theme()=="default")"midnight" else "default";engine.applyJson(org.json.JSONObject().put("actions",org.json.JSONArray().put(org.json.JSONObject().put("action","SET_THEME").put("theme",n))).toString());renderApps()})
  root.addView(bar,LinearLayout.LayoutParams(-1,dp(56)))
  search=EditText(this).apply{hint="Search apps";setSingleLine();setTextColor(Color.WHITE);setHintTextColor(UiTheme.textMuted);setPadding(dp(14),0,dp(14),0);background=UiTheme.rounded(UiTheme.card,22f,UiTheme.accent,1)}
  root.addView(search,LinearLayout.LayoutParams(-1,dp(48)).apply{bottomMargin=dp(8)});search.addTextChangedListener(W{renderApps()})
  val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10));UiTheme.styleCard(this,UiTheme.card,true)}
  val head=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};head.addView(TextView(this).apply{text="AI ASSISTANT";textSize=12f;setTextColor(UiTheme.accent);layoutParams=LinearLayout.LayoutParams(0,-2,1f)});status=TextView(this).apply{textSize=11f};head.addView(status);panel.addView(head)
  chat=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};panel.addView(ScrollView(this).apply{addView(chat)},LinearLayout.LayoutParams(-1,dp(110)))
  val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};input=EditText(this).apply{hint="Talk to your launcher…";setSingleLine();setTextColor(Color.WHITE);setHintTextColor(UiTheme.textMuted);background=UiTheme.rounded(UiTheme.card2,18f);setPadding(dp(12),0,dp(12),0);layoutParams=LinearLayout.LayoutParams(0,dp(44),1f)};row.addView(input)
  row.addView(Button(this).apply{text="Send";setTextColor(Color.WHITE);background=UiTheme.gradient(18f);setOnClickListener{askAi()};layoutParams=LinearLayout.LayoutParams(dp(72),dp(44)).apply{leftMargin=dp(6)}});panel.addView(row)
  root.addView(panel,LinearLayout.LayoutParams(-1,dp(210)).apply{bottomMargin=dp(8)})
  grid=GridLayout(this).apply{columnCount=columns(engine.layout());useDefaultMargins=true};root.addView(ScrollView(this).apply{addView(grid)},LinearLayout.LayoutParams(-1,0,1f));setContentView(root);updateStatus();renderApps()
 }
 private fun btn(t:String,f:()->Unit)=Button(this).apply{text=t;textSize=10f;setTextColor(Color.WHITE);background=UiTheme.rounded(UiTheme.card,17f,UiTheme.accent,1);setOnClickListener{f()};layoutParams=LinearLayout.LayoutParams(dp(60),dp(40)).apply{leftMargin=dp(3)}}
 private fun updateStatus(){status.text=if(settings.activeKey().isBlank())"● key needed" else "● AI ready";status.setTextColor(if(settings.activeKey().isBlank())UiTheme.warning else UiTheme.success)}
 private fun renderApps(){if(!::grid.isInitialized)return;grid.removeAllViews();grid.columnCount=columns(engine.layout());val q=search.text?.toString()?.trim()?.lowercase()?:"";val h=engine.hiddenPackages();apps.listLaunchableApps().filterNot{h.contains(it.packageName)}.filter{q.isEmpty()||it.label.lowercase().contains(q)}.forEach{addApp(it)}}
 private fun addApp(a:AppInfo){val v=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(5),dp(7),dp(5),dp(7));UiTheme.styleCard(this,UiTheme.card2);setOnClickListener{apps.launch(a)}};v.addView(ImageView(this).apply{setImageDrawable(assets.iconDrawable(a.packageName)?:a.icon);contentDescription=a.label},LinearLayout.LayoutParams(dp(48),dp(48)));v.addView(TextView(this).apply{text=a.label;textSize=11f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);maxLines=2});grid.addView(v,GridLayout.LayoutParams().apply{width=0;columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f)})}
 private fun askAi(){val q=input.text?.toString()?.trim().orEmpty();if(q.isEmpty()){bubble("AI","Tell me what you want to do.");return};if(settings.activeKey().isBlank()){bubble("AI","Add your API key in API first.");return};bubble("You",q);history.addLast(AiTurn("user",q));while(history.size>6)history.removeFirst();input.text.clear();status.text="● Thinking…";status.setTextColor(UiTheme.accent);scope.launch{createProvider(settings).chat(q,engine.stateSummary(),history.toList().dropLast(1)).onSuccess{r->bubble("AI",r.message);history.addLast(AiTurn("assistant",r.message));while(history.size>6)history.removeFirst();if(r.actionsJson!=null)engine.applyJson(r.actionsJson).onSuccess{n->bubble("System","Applied $n action(s).");renderApps()}.onFailure{bubble("System","Action error: "+it.message)};updateStatus()}.onFailure{bubble("AI","Connection error: "+it.message);updateStatus()}}}
 private fun bubble(w:String,m:String){chat.addView(TextView(this).apply{text="$w  $m";textSize=14f;setTextColor(Color.WHITE);setPadding(dp(11),dp(7),dp(11),dp(7));background=UiTheme.rounded(if(w=="You")UiTheme.card2 else UiTheme.card,17f);layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(5)}});(chat.parent as? ScrollView)?.post{(chat.parent as ScrollView).fullScroll(ScrollView.FOCUS_DOWN)}}
 private fun columns(l:String)=when(l.lowercase()){"dense","compact"->5;"wide"->3;else->4}
 override fun onDestroy(){scope.cancel();super.onDestroy()}
}
private class W(val f:()->Unit):android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,a:Int,c:Int,d:Int)=Unit;override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int)=f();override fun afterTextChanged(e:android.text.Editable?)=Unit}
