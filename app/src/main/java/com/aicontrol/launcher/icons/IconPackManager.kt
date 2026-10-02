package com.aicontrol.launcher.icons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ConcurrentHashMap

data class InstalledIconPack(val packageName: String, val label: String)

class IconPackManager(private val context: Context) {
    private val pm = context.packageManager
    private val cache = ConcurrentHashMap<String, Map<String, Int>>()

    fun installedPacks(): List<InstalledIconPack> {
        val actions = listOf("org.adw.ActivityStarter.THEMES", "com.novalauncher.THEME")
        val found = linkedMapOf<String, InstalledIconPack>()
        actions.forEach { action ->
            val intent = Intent(action)
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).forEach { ri ->
                val ai = ri.activityInfo.applicationInfo
                if (ai.packageName == context.packageName) return@forEach
                found[ai.packageName] = InstalledIconPack(
                    ai.packageName,
                    ai.loadLabel(pm).toString()
                )
            }
        }
        return found.values.sortedBy { it.label.lowercase() }
    }

    fun iconDrawable(packPackage: String, packageName: String, activityName: String): Drawable? {
        val map = cache.getOrPut(packPackage) { loadMappings(packPackage) }
        val key = componentKey(packageName, activityName)
        val drawableName = map[key] ?: map[packageName] ?: return null
        return runCatching {
            val resources = pm.getResourcesForApplication(packPackage)
            val id = resources.getIdentifier(drawableName, "drawable", packPackage)
            if (id == 0) null else resources.getDrawable(id, context.theme)
        }.getOrNull()
    }

    fun clearCache() { cache.clear() }

    private fun loadMappings(packPackage: String): Map<String, Int> {
        val result = linkedMapOf<String, Int>()
        val ai = pm.getApplicationInfo(packPackage, 0)
        val resources = pm.getResourcesForApplication(ai)
        val id = resources.getIdentifier("appfilter", "xml", packPackage)
        if (id == 0) return result.mapValues { 0 }
        val parser = resources.getXml(id)
        parser.use {
            var event = it.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && it.name == "item") {
                    val component = it.getAttributeValue(null, "component")
                    val drawable = it.getAttributeValue(null, "drawable")
                    if (!component.isNullOrBlank() && !drawable.isNullOrBlank()) {
                        val normalized = normalizeComponent(component)
                        result[normalized] = resources.getIdentifier(drawable, "drawable", packPackage)
                        val pkg = normalized.substringBefore("/")
                        if (pkg.isNotBlank()) result.putIfAbsent(pkg, resources.getIdentifier(drawable, "drawable", packPackage))
                    }
                }
                event = it.next()
            }
        }
        return result
    }

    private fun componentKey(packageName: String, activityName: String) =
        packageName + "/" + activityName

    private fun normalizeComponent(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("ComponentInfo{") && s.endsWith("}")) s = s.substring(14, s.length - 1)
        val slash = s.indexOf('/')
        if (slash < 0) return s
        val pkg = s.substring(0, slash)
        var cls = s.substring(slash + 1)
        if (cls.startsWith(".")) cls = pkg + cls
        return pkg + "/" + cls
    }
}
