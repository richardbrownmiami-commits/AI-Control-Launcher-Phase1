package com.aicontrol.launcher.ui

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import com.aicontrol.launcher.assets.AssetCatalog
import com.aicontrol.launcher.assets.LauncherAssetManager
import java.io.File

class AssetsActivity : Activity() {
    private lateinit var catalog: AssetCatalog
    private lateinit var manager: LauncherAssetManager
    private lateinit var list: LinearLayout
    private fun dp(v:Int)=(v*resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        catalog=AssetCatalog(this); manager=LauncherAssetManager(this); buildUi()
    }

    private fun buildUi() {
        val root=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(18,18,18,18); background=UiTheme.gradient(0f)
        }
        root.addView(TextView(this).apply { text="Launcher Assets"; textSize=24f; setTextColor(Color.WHITE) })
        root.addView(TextView(this).apply {
            text="Private downloaded assets"
            setTextColor(UiTheme.textMuted); setPadding(0,6,0,12)
        })
        list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) },LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root); render()
    }

    override fun onResume(){ super.onResume(); if(::list.isInitialized) render() }

    private fun render() {
        list.removeAllViews()
        section("Wallpapers",catalog.listWallpapers(),true)
        section("Images",catalog.listImages(),false)
        section("Icon overrides",catalog.listIconOverrides(),false)
        section("Icon packs",catalog.listIconPacks(),true)
        section("Custom themes",catalog.listThemes(),false)
        section("Styles",catalog.listStyles(),false)
    }

    private fun section(title:String,files:List<File>,canApply:Boolean) {
        list.addView(TextView(this).apply {
            text=title+" ("+files.size+")"; textSize=18f; setTextColor(UiTheme.accent); setPadding(0,14,0,6)
        })
        files.forEach { file ->
            val line=LinearLayout(this).apply { gravity=Gravity.CENTER_VERTICAL; setPadding(dp(8),dp(6),dp(6),dp(6)); UiTheme.styleCard(this,UiTheme.card,true) }
            line.addView(TextView(this).apply {
                text=file.name; setTextColor(UiTheme.textMuted); layoutParams=LinearLayout.LayoutParams(0,-2,1f)
            })
            if(canApply) line.addView(Button(this).apply {
                text="Apply"
                setOnClickListener {
                    val result=if(title=="Wallpapers") manager.applyWallpaper(file) else manager.applyIconPack(file.name)
                    result.onFailure { Toast.makeText(this@AssetsActivity,it.message,Toast.LENGTH_SHORT).show() }
                    render()
                }
            })
            line.addView(Button(this).apply {
                text="Delete"
                setOnClickListener {
                    val ok=when(title){
                        "Wallpapers"->catalog.deleteWallpaper(file.name)
                        "Images"->catalog.deleteImage(file.name)
                        "Icon overrides"->catalog.deleteIconOverride(file.name)
                        "Icon packs"->catalog.deleteIconPack(file.name)
                        "Styles"->catalog.deleteStyle(file.name)
                        else->catalog.deleteTheme(file.name)
                    }
                    if(!ok) Toast.makeText(this@AssetsActivity,"Delete failed",Toast.LENGTH_SHORT).show()
                    render()
                }
            })
            list.addView(line)
        }
    }
}