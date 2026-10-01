package com.aicontrol.launcher.ui
import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.widget.*
import com.aicontrol.launcher.ai.*
import com.aicontrol.launcher.data.SettingsStore
import kotlinx.coroutines.*
class SettingsActivity:Activity(){
 private lateinit var s:SettingsStore;private lateinit var models:Spinner;private lateinit var radios:RadioGroup;private lateinit var ok:EditText;private lateinit var gk:EditText
 private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()
 override fun onCreate(b:Bundle?){super.onCreate(b);UiTheme.bind(com.aicontrol.launcher.actions.ActionEngine(this));s=SettingsStore(this);ui()}
 private fun ui(){
  val r=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(18),dp(18),dp(18),dp(18));background=UiTheme.gradient(0f)}
  r.addView(TextView(this).apply{text="AI Settings";textSize=26f;setTextColor(UiTheme.textPrimary)});r.addView(label("PROVIDER"))
  radios=RadioGroup(this).apply{orientation=RadioGroup.HORIZONTAL};val o=RadioButton(this).apply{text="OpenRouter";setTextColor(UiTheme.textPrimary);id=1};val g=RadioButton(this).apply{text="Gemini";setTextColor(UiTheme.textPrimary);id=2};radios.addView(o);radios.addView(g);radios.check(if(s.provider==SettingsStore.PROVIDER_GEMINI)2 else 1);r.addView(radios)
  r.addView(label("FREE MODEL"));models=Spinner(this);r.addView(models);ok=key("OpenRouter API key");gk=key("Gemini API key");ok.setText(s.openRouterKey);gk.setText(s.geminiKey);r.addView(label("API KEYS"));r.addView(ok);r.addView(gk)
  fun refresh(p:String){val list=FreeModels.forProvider(p);models.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,list);val i=list.indexOf(s.model);models.setSelection(if(i>=0)i else 0)}
  refresh(s.provider);radios.setOnCheckedChangeListener{_,id->refresh(if(id==2)SettingsStore.PROVIDER_GEMINI else SettingsStore.PROVIDER_OPENROUTER)}
  r.addView(Button(this).apply{text="Save settings";setTextColor(UiTheme.textPrimary);background=UiTheme.gradient(20f);setOnClickListener{save();Toast.makeText(this@SettingsActivity,"AI settings saved",Toast.LENGTH_SHORT).show()}})
  r.addView(Button(this).apply{text="Test connection";setOnClickListener{test()}})
  r.addView(TextView(this).apply{text="Free models only. Keys are stored in app-private SharedPreferences for this prototype. Android Keystore is future work.";setTextColor(UiTheme.textMuted);setPadding(0,dp(12),0,0)})
  setContentView(ScrollView(this).apply{addView(r)})
 }
 private fun label(t:String)=TextView(this).apply{text=t;textSize=11f;setTextColor(UiTheme.accent);setPadding(0,dp(15),0,dp(6))}
 private fun key(h:String)=EditText(this).apply{hint=h;setTextColor(Color.WHITE);setHintTextColor(UiTheme.textMuted);inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;background=UiTheme.rounded(UiTheme.card,18f);setPadding(dp(12),0,dp(12),0);layoutParams=LinearLayout.LayoutParams(-1,dp(48)).apply{bottomMargin=dp(7)}}
 private fun save(){s.provider=if(radios.checkedRadioButtonId==2)SettingsStore.PROVIDER_GEMINI else SettingsStore.PROVIDER_OPENROUTER;s.model=models.selectedItem?.toString().orEmpty();s.openRouterKey=ok.text.toString().trim();s.geminiKey=gk.text.toString().trim()}
 private fun test(){save();val selectedModel=models.selectedItem?.toString().orEmpty();if(selectedModel.isBlank()){Toast.makeText(this,"Select a model first",Toast.LENGTH_LONG).show();return};if(s.activeKey().isBlank()){Toast.makeText(this,"API key is empty for "+s.provider,Toast.LENGTH_LONG).show();return};val p=if(s.provider==SettingsStore.PROVIDER_GEMINI)GeminiProvider(selectedModel,s.geminiKey)else OpenRouterProvider(selectedModel,s.openRouterKey);Toast.makeText(this,"Testing "+s.provider+" / "+selectedModel+"…",Toast.LENGTH_SHORT).show();CoroutineScope(Dispatchers.Main).launch{val result=withContext(Dispatchers.IO){p.chat("Reply with exactly JSON: {\"message\":\"Connection OK\",\"actions\":[]}. Do not add markdown.","connection test",emptyList())};result.onSuccess{Toast.makeText(this@SettingsActivity,it.message,Toast.LENGTH_LONG).show()}.onFailure{Toast.makeText(this@SettingsActivity,it.message ?: "Unknown connection error",Toast.LENGTH_LONG).show()}}}
}
