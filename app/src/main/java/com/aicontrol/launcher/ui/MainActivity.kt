package com.aicontrol.launcher.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.widget.*
import com.aicontrol.launcher.actions.ActionEngine
import com.aicontrol.launcher.ai.createProvider
import com.aicontrol.launcher.apps.AppInfo
import com.aicontrol.launcher.apps.AppRepository
import com.aicontrol.launcher.assets.LauncherAssetManager
import com.aicontrol.launcher.data.SettingsStore
import kotlinx.coroutines.*

private const val NATIVE_ABI_GUARD="launcherabi"

class MainActivity:Activity(){
    private lateinit var settings:SettingsStore
    private lateinit var apps:AppRepository
    private lateinit var engine:ActionEngine
    private lateinit var assetManager:LauncherAssetManager
    private lateinit var root:LinearLayout
    private lateinit var grid:GridLayout
    private lateinit var search:EditText
    private lateinit var status:TextView
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private var downY=0f

    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        runCatching{System.loadLibrary(NATIVE_ABI_GUARD)}
        settings=SettingsStore(this); apps=AppRepository(this); engine=ActionEngine(this); assetManager=LauncherAssetManager(this); buildUi()
    }

    override fun onResume(){
        super.onResume()
        if(::root.isInitialized){ root.setBackgroundColor(engine.themeBackground()); renderApps(); updateStatus() }
    }

    private fun buildUi(){
        root=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL; setPadding(18,18,18,18); setBackgroundColor(engine.themeBackground())
            setOnTouchListener{_,e->when(e.actionMasked){
                MotionEvent.ACTION_DOWN->{downY=e.rawY;false}
                MotionEvent.ACTION_UP->{if(e.rawY-downY < -120){search.requestFocus();true}else false}
                else->false
            }}
        }
        val header=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
        header.addView(TextView(this).apply{
            text="AI Control Launcher";textSize=22f;setTextColor(Color.WHITE);layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        })
        header.addView(Button(this).apply{text="API";setOnClickListener{startActivity(Intent(this@MainActivity,SettingsActivity::class.java))}})
        header.addView(Button(this).apply{text="Assets";setOnClickListener{startActivity(Intent(this@MainActivity,AssetsActivity::class.java))}})
        header.addView(Button(this).apply{
            text="Theme"
            setOnClickListener{
                val next=if(engine.theme()=="default")"midnight"else"default"
                engine.applyJson("{"actions":[{"action":"SET_THEME","theme":""+next+""}]}")
                root.setBackgroundColor(engine.themeBackground()); updateStatus()
            }
        })
        root.addView(header)
        search=EditText(this).apply{
            hint="Search apps or type an AI command…";setSingleLine(true);setTextColor(Color.WHITE);setHintTextColor(Color.GRAY)
        }
        root.addView(search); search.addTextChangedListener(SimpleTextWatcher{renderApps()})
        root.addView(Button(this).apply{text="Ask AI";setOnClickListener{askAi()}})
        status=TextView(this).apply{setTextColor(Color.LTGRAY);setPadding(4,8,4,8)}
        root.addView(status)
        grid=GridLayout(this).apply{alignmentMode=GridLayout.ALIGN_BOUNDS;useDefaultMargins=true;columnCount=columnsFor(engine.layout())}
        root.addView(ScrollView(this).apply{addView(grid)},LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root);updateStatus();renderApps()
    }

    private fun updateStatus(){
        status.text=if(settings.activeKey().isBlank())"AI not configured • tap API"
        else settings.provider+" • "+settings.model+" • "+engine.layout()+" • "+engine.theme()
    }

    private fun renderApps(){
        if(!::grid.isInitialized)return
        grid.removeAllViews();grid.columnCount=columnsFor(engine.layout())
        val query=search.text?.toString()?.trim()?.lowercase()?:""
        val hidden=engine.hiddenPackages()
        apps.listLaunchableApps().filterNot{hidden.contains(it.packageName)}
            .filter{query.isEmpty()||it.label.lowercase().contains(query)}
            .forEach{addApp(it)}
    }

    private fun addApp(app:AppInfo){
        val cell=LinearLayout(this).apply{
            orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER;setPadding(6,8,6,8);setOnClickListener{apps.launch(app)}
        }
        val icon=ImageView(this).apply{
            setImageDrawable(assetManager.iconDrawable(app.packageName)?:app.icon);contentDescription=app.label
        }
        val size=(resources.displayMetrics.density*52).toInt()
        cell.addView(icon,LinearLayout.LayoutParams(size,size))
        cell.addView(TextView(this).apply{text=app.label;textSize=11f;gravity=Gravity.CENTER;setTextColor(Color.WHITE);maxLines=2})
        grid.addView(cell,GridLayout.LayoutParams().apply{width=0;columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f)})
    }

    private fun askAi(){
        val command=search.text?.toString()?.trim().orEmpty()
        if(command.isEmpty()){status.text="Type an AI request, then tap Ask AI.";return}
        if(settings.activeKey().isBlank()){status.text="Add an API key in API settings first.";return}
        status.text="AI is planning…"
        scope.launch{
            createProvider(settings).generatePlan(command,engine.stateSummary()).onSuccess{json->
                engine.applyJson(json).onSuccess{n->
                    status.text="Applied "+n+" action(s)";root.setBackgroundColor(engine.themeBackground());renderApps()
                }.onFailure{status.text="Plan rejected: "+it.message}
            }.onFailure{status.text="AI error: "+it.message}
        }
    }

    private fun columnsFor(layout:String)=when(layout.lowercase()){"dense","compact"->5;"wide"->3;else->4}
    override fun onDestroy(){scope.cancel();super.onDestroy()}
}

private class SimpleTextWatcher(private val changed:()->Unit):android.text.TextWatcher{
    override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int)=Unit
    override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int)=changed()
    override fun afterTextChanged(s:android.text.Editable?)=Unit
}