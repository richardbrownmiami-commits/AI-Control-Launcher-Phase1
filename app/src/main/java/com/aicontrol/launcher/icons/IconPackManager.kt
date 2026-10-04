package com.aicontrol.launcher.icons

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.util.concurrent.ConcurrentHashMap

data class InstalledIconPack(val packageName: String, val label: String)

class IconPackManager(private val context: Context) {
    private val pm = context.packageManager
    private val cache = ConcurrentHashMap<String, Map<String, String>>()

    fun installedPacks(): List<InstalledIconPack> {
        val found = linkedMapOf<String, InstalledIconPack>()
        IconPackCompatibility.queries.forEach { query ->
            val intent = Intent(query.action).apply { query.category?.let(::addCategory) }
            // Nova's documented theme filter may contain the action but no CATEGORY_DEFAULT.
            // MATCH_DEFAULT_ONLY silently hides those valid packs, so query the action directly.
            pm.queryIntentActivities(intent, 0).forEach { ri ->
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

    /** Human-readable choices mapped only to packages returned by the installed-pack query. */
    fun assistantChoices(): Map<String, String> {
        val packs = installedPacks()
        val duplicateLabels = packs.groupingBy { it.label.trim().lowercase() }.eachCount()
        return packs.associate { pack ->
            val choice = if ((duplicateLabels[pack.label.trim().lowercase()] ?: 0) > 1) {
                "${pack.label} (${pack.packageName})"
            } else pack.label
            choice to pack.packageName
        }
    }

    fun iconDrawable(packPackage: String, packageName: String, activityName: String): Drawable? {
        val map = mappings(packPackage)
        val drawableName = IconPackMappingResolver.drawableFor(map, packageName, activityName) ?: return null
        return runCatching {
            val resources = pm.getResourcesForApplication(packPackage)
            val id = resources.getIdentifier(drawableName, "drawable", packPackage)
            if (id == 0) null else resources.getDrawable(id, context.theme)
        }.getOrNull()
    }

    fun hasIconMapping(packPackage: String, packageName: String, activityName: String): Boolean =
        IconPackMappingResolver.drawableFor(mappings(packPackage), packageName, activityName) != null

    fun clearCache() { cache.clear() }

    private fun mappings(packPackage: String): Map<String, String> =
        cache.getOrPut(packPackage) { runCatching { loadMappings(packPackage) }.getOrDefault(emptyMap()) }

    private fun loadMappings(packPackage: String): Map<String, String> {
        val ai = pm.getApplicationInfo(packPackage, 0)
        val resources = pm.getResourcesForApplication(ai)
        val id = resources.getIdentifier("appfilter", "xml", packPackage)
        if (id != 0) {
            val parser = resources.getXml(id)
            return try { readMappings(parser) } finally { parser.close() }
        }
        val packContext = context.createPackageContext(packPackage, Context.CONTEXT_RESTRICTED)
        packContext.assets.open("appfilter.xml").use { input ->
            val parser = Xml.newPullParser()
            parser.setInput(input, "UTF-8")
            return readMappings(parser)
        }
    }

    private fun readMappings(parser: XmlPullParser): Map<String, String> {
        val entries = mutableListOf<IconPackMappingEntry>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component")
                val drawable = parser.getAttributeValue(null, "drawable")
                if (!component.isNullOrBlank() && !drawable.isNullOrBlank()) {
                    entries += IconPackMappingEntry(component, drawable)
                    require(entries.size <= 100_000) { "Icon-pack mapping file exceeds its entry limit." }
                }
            }
            event = parser.next()
        }
        return IconPackMappingResolver.index(entries)
    }
}
