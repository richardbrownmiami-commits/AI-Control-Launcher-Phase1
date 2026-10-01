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
import org.json.JSONArray
import org.json.JSONObject

private const val NATIVE_ABI_GUARD="launcherabi"
private const val HISTORY_PREFS="ai_chat_history"

class MainActivity:Activity(){
 private lateinit var settings:SettingsStore;private lateinit var apps:AppRepository;private lateinit var engine:ActionEngine;private lateinit var assets:LauncherAssetManager
 private lateinit var root:LinearLayout;private lateinit var grid:GridLayout;private lateinit var search:EditText;private lateinit var input:EditText;private lateinit var chat:LinearLayout;private lateinit var chatScroll:ScrollView;private lateinit var status:TextView;private lateinit var shortcuts:LinearLayout
 private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main);private val history=ArrayDeque<AiTurn>()
 private val historyPrefs by lazy{getSharedPreferences(HISTORY_PREFS,MODE_PRIVATE)}
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()

 override fun onCreate(b:Bundle?){super.onCreate(b);runCatching{System.loadLibrary(NATIVE_ABI_GUARD)};settings=SettingsStore(this);apps=AppRepository(this);engine=ActionEngine(this);assets=LauncherAssetManager(this);loadHistory();buildUi()}
 override fun onResume(){super.onResume();if(::root.isInitialized){UiTheme.bind(engine);root.background=UiTheme.background();renderApps();renderShortcuts();updateStatus()}}
 private fun buildUi(){
  UiTheme.bind(engine)
  root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(14),dp(10),dp(14),dp(10));background=UiTheme.background()}
  val bar=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
  bar.addView(TextView(this).apply{text="AI Control";textSize=23f;setTextColor(UiTheme.textPrimary);layoutParams=LinearLayout.LayoutParams(0,-2,1f)})
  bar.addView(btn("API"){startActivity(Intent(this@MainActivity,SettingsActivity::class.java))})
  bar.addView(btn("Assets"){startActivity(Intent(this@MainActivity,AssetsActivity::class.java))})
  bar.addView(btn("Theme"){val n=if(engine.theme()=="default")"midnight" else "default";val j=JSONObject().put("actions",JSONArray().put(JSONObject().put("action","SET_THEME").put("theme",n)));engine.applyJson(j.toString());UiTheme.bind(engine);root.background=UiTheme.background();renderApps()})
  root.addView(bar,LinearLayout.LayoutParams(-1,dp(52)))

  search=EditText(this).apply{hint="Search apps";setSingleLine();setTextColor(Color.WHITE);setHintTextColor(UiTheme.textMuted);setPadding(dp(14),0,dp(14),0);background=UiTheme.rounded(UiTheme.card,22f,UiTheme.accent,1)}
  root.addView(search,LinearLayout.LayoutParams(-1,dp(46)).apply{bottomMargin=dp(8)});search.addTextChangedListener(W{renderApps()})

  val panel=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(12),dp(10),dp(12),dp(10));UiTheme.styleCard(this,UiTheme.card,true)}
  val head=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
  head.addView(TextView(this).apply{text="AI ASSISTANT";textSize=12f;setTextColor(UiTheme.accent);layoutParams=LinearLayout.LayoutParams(0,-2,1f)})
  status=TextView(this).apply{textSize=11f;setPadding(dp(8),dp(4),dp(8),dp(4));background=UiTheme.rounded(UiTheme.card2,14f)};head.addView(status)
  head.addView(Button(this).apply{text="Clear";textSize=10f;setTextColor(UiTheme.textPrimary);background=UiTheme.rounded(UiTheme.card2,14f,UiTheme.accent,1);setOnClickListener{clearChat()}})
  panel.addView(head)
  chat=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}
  chatScroll=ScrollView(this).apply{addView(chat);isFillViewport=true}
  panel.addView(chatScroll,LinearLayout.LayoutParams(-1,0,1f))
  val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL}
  input=EditText(this).apply{hint="Talk to your launcher…";setSingleLine();setTextColor(Color.WHITE);setHintTextColor(UiTheme.textMuted);background=UiTheme.rounded(UiTheme.card2,18f);setPadding(dp(12),0,dp(12),0);layoutParams=LinearLayout.LayoutParams(0,dp(46),1f)}
  row.addView(input)
  row.addView(Button(this).apply{text="Send";textSize=12f;setTextColor(Color.WHITE);background=UiTheme.gradient(18f);setOnClickListener{askAi()};layoutParams=LinearLayout.LayoutParams(dp(74),dp(46)).apply{leftMargin=dp(6)}})
  panel.addView(row,LinearLayout.LayoutParams(-1,dp(52)))
  root.addView(panel,LinearLayout.LayoutParams(-1,0,0.42f).apply{bottomMargin=dp(8)})

  shortcuts=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL};root.addView(shortcuts,LinearLayout.LayoutParams(-1,dp(52)).apply{bottomMargin=dp(6)})
  grid=GridLayout(this).apply{columnCount=columns(engine.layout());useDefaultMargins=true}
  root.addView(ScrollView(this).apply{addView(grid)},LinearLayout.LayoutParams(-1,0,0.58f))
  setContentView(root);restoreBubbles();updateStatus();renderApps();renderShortcuts()
 }

 private fun btn(t:String,f:()->Unit)=Button(this).apply{text=t;textSize=10f;setTextColor(Color.WHITE);background=UiTheme.rounded(UiTheme.card,17f,UiTheme.accent,1);setOnClickListener{f()};layoutParams=LinearLayout.LayoutParams(dp(60),dp(38)).apply{leftMargin=dp(3)}}
 private fun updateStatus(){val missing=settings.activeKey().isBlank();status.text=if(missing)"● key needed" else "● AI ready";status.setTextColor(if(missing)UiTheme.warning else UiTheme.success)}
 private fun renderApps(){if(!::grid.isInitialized)return;grid.removeAllViews();grid.columnCount=columns(engine.layout());val q=search.text?.toString()?.trim()?.lowercase()?:"";val h=engine.hiddenPackages();apps.listLaunchableApps().filterNot{h.contains(it.packageName)}.filter{q.isEmpty()||it.label.lowercase().contains(q)}.forEach{addApp(it)}}
 private fun addApp(a:AppInfo){val v=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(dp(5),dp(7),dp(5),dp(7));UiTheme.styleCard(this,UiTheme.card2);setOnClickListener{apps.launch(a)}};v.addView(ImageView(this).apply{setImageDrawable(assets.iconDrawable(a.packageName)?:a.icon);contentDescription=a.label},LinearLayout.LayoutParams(dp(46),dp(46)));v.addView(TextView(this).apply{text=a.label;textSize=11f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);maxLines=2});grid.addView(v,GridLayout.LayoutParams().apply{width=0;columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f)})}
 private fun renderShortcuts(){if(!::shortcuts.isInitialized)return;shortcuts.removeAllViews();engine.shortcuts().take(5).forEach{j->shortcuts.addView(Button(this).apply{text=j.optString("label");textSize=10f;setTextColor(Color.WHITE);background=UiTheme.rounded(UiTheme.card2,16f,UiTheme.accent,1);setOnClickListener{val pkg=j.optString("package");apps.listLaunchableApps().firstOrNull{it.packageName==pkg}?.let{apps.launch(it)}};layoutParams=LinearLayout.LayoutParams(0,dp(44),1f).apply{leftMargin=dp(3);rightMargin=dp(3)}})}}
 private fun askAi(){
  val q=input.text?.toString()?.trim().orEmpty()
  if(q.isEmpty()){bubble("AI","Tell me what you want to do.");return}
  if(settings.activeKey().isBlank()){bubble("AI","Add your API key in API first.");return}
  bubble("You",q);history.addLast(AiTurn("user",q));trimHistory();persistHistory();input.text.clear()
  status.text="● Thinking…";status.setTextColor(UiTheme.accent)
  val prior=history.toList().dropLast(1);val state=engine.stateSummary()
  scope.launch{
   createProvider(settings).chat(q,state,prior).onSuccess{r->
    bubble("AI",r.message);history.addLast(AiTurn("assistant",r.message));trimHistory();persistHistory()
    if(r.actionsJson!=null){engine.applyJson(r.actionsJson).onSuccess{n->bubble("System","Applied $n action(s).");UiTheme.bind(engine);root.background=UiTheme.background();renderApps();renderShortcuts()}.onFailure{bubble("System","Action error: "+it.message)}}
    updateStatus()
   }.onFailure{bubble("System","AI error: "+it.message);updateStatus()}
  }
 }
 private fun trimHistory(){while(history.size>8)history.removeFirst()}
 private fun persistHistory(){val a=JSONArray();history.forEach{a.put(JSONObject().put("role",it.role).put("content",it.content))};historyPrefs.edit().putString("turns",a.toString()).apply()}
 private fun loadHistory(){val a=JSONArray(historyPrefs.getString("turns","[]")?:"[]");for(i in 0 until a.length()){val j=a.getJSONObject(i);history.addLast(AiTurn(j.optString("role"),j.optString("content")))};trimHistory()}
 private fun restoreBubbles(){history.forEach{bubble(it.role.replaceFirstChar{c->c.uppercase()},it.content)}}
 private fun clearChat(){history.clear();historyPrefs.edit().remove("turns").apply();chat.removeAllViews();bubble("AI","Chat cleared. What would you like to create?")}
 private fun bubble(w:String,m:String){val color=when(w){"You"->UiTheme.card2;"System"->UiTheme.card;else->UiTheme.card};chat.addView(TextView(this).apply{text=w+"  "+m;textSize=14f;setTextColor(Color.WHITE);setPadding(dp(12),dp(8),dp(12),dp(8));background=UiTheme.rounded(color,17f);layoutParams=LinearLayout.LayoutParams(-1,-2).apply{bottomMargin=dp(6)}});chatScroll.post{chatScroll.fullScroll(ScrollView.FOCUS_DOWN)}}
 private fun columns(l:String)=when(l.lowercase()){"dense","compact"->5;"wide"->3;else->4}
 override fun onDestroy(){scope.cancel();super.onDestroy()}
}
private class W(val f:()->Unit):android.text.TextWatcher{override fun beforeTextChanged(s:CharSequence?,a:Int,c:Int,d:Int)=Unit;override fun onTextChanged(s:CharSequence?,a:Int,b:Int,c:Int)=f();override fun afterTextChanged(e:android.text.Editable?)=Unit}
