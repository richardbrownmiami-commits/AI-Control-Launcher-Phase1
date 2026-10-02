package com.aicontrol.launcher.actions

import android.content.Context
import android.graphics.Color
import com.aicontrol.launcher.apps.AppRepository
import com.aicontrol.launcher.assets.AssetStore
import com.aicontrol.launcher.assets.LauncherAssetManager
import org.json.JSONArray
import org.json.JSONObject

data class ThemeState(val bg:Int,val accent:Int,val accent2:Int,val card:Int,val style:String)

class ActionEngine(context:Context){
 private val prefs=context.getSharedPreferences("launcher_state",Context.MODE_PRIVATE)
 private val assets=LauncherAssetManager(context);private val store=AssetStore(context);private val apps=AppRepository(context)

 fun applyJson(json: String): kotlin.Result<Int> = runCatching {
  val actions=JSONObject(json).optJSONArray("actions")?:JSONArray();var applied=0
  for(i in 0 until actions.length()){val a=actions.getJSONObject(i);when(a.optString("action")){
   "HIDE_APPS"->{setPackages("hidden",a.optJSONArray("packages"));applied++}
   "SHOW_APPS"->{removePackages("hidden",a.optJSONArray("packages"));applied++}
   "HIDE_APPS_BY_NAME"->{val names=a.optJSONArray("names")?:JSONArray();val targets=apps.listLaunchableApps();for(j in 0 until names.length()){val wanted=names.optString(j).trim().lowercase();targets.filter{it.label.lowercase()==wanted}.forEach{addHidden(it.packageName)}};applied++}
   "SET_THEME"->{prefs.edit().putString("theme",a.optString("theme","default")).apply();applied++}
   "CREATE_THEME"->{val name=a.optString("name").trim();require(name.isNotEmpty()){"Theme name is empty"};val data=JSONObject().put("name",name).put("bg",a.optString("bg","#101218")).put("accent",a.optString("accent","#46D2FF")).put("accent2",a.optString("accent2","#9B5CFF")).put("card",a.optString("card","#141723")).put("style",a.optString("style","glass"));store.themeFile(name).writeText(data.toString());prefs.edit().putString("theme",name).apply();applied++}
   "SET_LAYOUT"->{prefs.edit().putString("layout",a.optString("layout","grid")).apply();applied++}
   "SET_STYLE"->{prefs.edit().putString("style",a.optString("style","glass")).apply();applied++}
   "CREATE_WORKSPACE"->{val name=a.optString("name").trim();require(name.isNotEmpty()){"Workspace name is empty"};prefs.edit().putString("workspace:"+name,a.optJSONArray("packages")?.toString()?:"[]").apply();applied++}
   "DOWNLOAD_WALLPAPER"->{val url=a.optString("url");require(url.startsWith("https://")){"Wallpaper URL must use HTTPS"};val file=assets.downloadWallpaper(url,a.optString("name","wallpaper")).getOrThrow();if(a.optBoolean("apply",true))assets.applyWallpaper(file).getOrThrow();applied++}
   "DOWNLOAD_IMAGE"->{val url=a.optString("url");require(url.startsWith("https://")){"Image URL must use HTTPS"};assets.downloadImage(url,a.optString("name","downloaded-image")).getOrThrow();applied++}
   "DOWNLOAD_ICON"->{val url=a.optString("url");val pkg=a.optString("package").trim();require(url.startsWith("https://")){"Icon URL must use HTTPS"};require(pkg.isNotEmpty()){"Icon package is empty"};assets.downloadIcon(url,pkg).getOrThrow();applied++}
   "CREATE_ICON_PACK"->{val name=a.optString("name").trim();require(name.isNotEmpty()){"Icon pack name is empty"};val icons=a.optJSONArray("icons")?:JSONArray();val items=mutableListOf<Pair<String,String>>();for(j in 0 until icons.length()){val item=icons.getJSONObject(j);items+=item.optString("package") to item.optString("url")};assets.createIconPack(name,items).getOrThrow();assets.applyIconPack(name).getOrThrow();prefs.edit().putString("icon_pack",name).apply();applied++}
   "APPLY_ICON_PACK"->{val name=a.optString("name").trim();assets.applyIconPack(name).getOrThrow();prefs.edit().putString("icon_pack",name).apply();applied++}
   "CLEAR_ICON_OVERRIDE"->{assets.clearIconOverride(a.optString("package"));applied++}
   "CLEAR_ICON_PACK"->{val name=a.optString("name").trim();if(name.isNotEmpty())assets.clearIconPack(name).getOrThrow() else assets.clearAllIconOverrides();prefs.edit().remove("icon_pack").apply();applied++}
   "DELETE_ASSET"->{assets.deleteAsset(a.optString("type"),a.optString("name")).getOrThrow();applied++}
   "ADD_SHORTCUT"->{addShortcut(a.optString("label"),a.optString("package"),a.optString("activity"));applied++}
   "REMOVE_SHORTCUT"->{removeShortcut(a.optString("label"),a.optString("package"));applied++}
  }}
  applied
 }

 private fun addHidden(pkg:String){val set=hiddenPackages().toMutableSet();set.add(pkg);prefs.edit().putStringSet("hidden",set).apply()}
 private fun setPackages(key:String,arr:JSONArray?){val current=prefs.getStringSet(key,emptySet())!!.toMutableSet();if(arr!=null)for(i in 0 until arr.length())current.add(arr.getString(i));prefs.edit().putStringSet(key,current).apply()}
 private fun removePackages(key:String,arr:JSONArray?){val current=prefs.getStringSet(key,emptySet())!!.toMutableSet();if(arr!=null)for(i in 0 until arr.length())current.remove(arr.getString(i));prefs.edit().putStringSet(key,current).apply()}
 private fun addShortcut(label:String,pkg:String,activity:String){require(label.isNotBlank()&&pkg.isNotBlank()){"Shortcut label/package required"};val arr=JSONArray(prefs.getString("shortcuts","[]")?:"[]");arr.put(JSONObject().put("label",label).put("package",pkg).put("activity",activity));prefs.edit().putString("shortcuts",arr.toString()).apply()}
 private fun removeShortcut(label:String,pkg:String){val old=JSONArray(prefs.getString("shortcuts","[]")?:"[]");val out=JSONArray();for(i in 0 until old.length()){val item=old.getJSONObject(i);if((label.isNotBlank()&&item.optString("label")==label)||(pkg.isNotBlank()&&item.optString("package")==pkg))continue;out.put(item)};prefs.edit().putString("shortcuts",out.toString()).apply()}
 fun shortcuts():List<JSONObject>{val a=JSONArray(prefs.getString("shortcuts","[]")?:"[]");return(0 until a.length()).map{a.getJSONObject(it)}}
 fun hiddenPackages(): Set<String> = prefs.getStringSet("hidden",emptySet())?:emptySet()
 fun theme():String=prefs.getString("theme","default")?:"default"
 fun layout():String=prefs.getString("layout","grid")?:"grid"
 fun style():String=prefs.getString("style",themeState().style)?:"glass"
 fun iconPack():String=prefs.getString("icon_pack","")?:""
 fun installedIconPack():String=prefs.getString("installed_icon_pack","")?:""
 fun showLabels():Boolean=prefs.getBoolean("show_labels",true)
 fun dockCount():Int=prefs.getInt("dock_count",5)
 fun setTheme(value:String){prefs.edit().putString("theme",value).apply()}
 fun setStyle(value:String){prefs.edit().putString("style",value).apply()}
 fun setLayout(value:String){prefs.edit().putString("layout",value).apply()}
 fun setInstalledIconPack(value:String){prefs.edit().putString("installed_icon_pack",value).apply()}
 fun setShowLabels(value:Boolean){prefs.edit().putBoolean("show_labels",value).apply()}
 fun setDockCount(value:Int){prefs.edit().putInt("dock_count",value.coerceIn(3,6)).apply()}

 fun themeState():ThemeState{
  val fallback=ThemeState(Color.rgb(8,10,18),Color.rgb(70,210,255),Color.rgb(155,92,255),Color.rgb(20,23,35),prefs.getString("style","glass")?:"glass")
  return when(theme().lowercase()){
   "midnight"->fallback.copy(bg=Color.rgb(5,8,14))
   "ocean"->fallback.copy(bg=Color.rgb(6,24,38),accent=Color.rgb(64,214,255))
   "ember"->fallback.copy(bg=Color.rgb(38,16,12),accent=Color.rgb(255,130,70),accent2=Color.rgb(255,70,150))
   "default"->fallback
   else->runCatching{val j=JSONObject(store.themeFile(theme()).readText());ThemeState(Color.parseColor(j.optString("bg","#080A12")),Color.parseColor(j.optString("accent","#46D2FF")),Color.parseColor(j.optString("accent2","#9B5CFF")),Color.parseColor(j.optString("card","#141723")),j.optString("style",prefs.getString("style","glass")?:"glass"))}.getOrDefault(fallback)
  }
 }
 fun stateSummary():String{
  val labels=apps.listLaunchableApps().take(40).joinToString(","){it.label}
  return "theme="+theme()+", layout="+layout()+", style="+style()+", iconPack="+iconPack()+", hiddenCount="+hiddenPackages().size+", apps=["+labels+"]"
 }
}