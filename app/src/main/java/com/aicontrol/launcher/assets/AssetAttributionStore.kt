package com.aicontrol.launcher.assets

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import org.json.JSONArray
import org.json.JSONObject

data class AssetAttribution(
    val type: String,
    val name: String,
    val title: String,
    val creator: String,
    val license: String,
    val licenseUrl: String,
    val sourceUrl: String
)

class AssetAttributionStore(context: Context) {
    private val file = File(AssetStore(context.applicationContext).root, "asset-attributions.json")

    init {
        val values = readAll().distinctBy { it.type to it.name }.toMutableList()
        val originalCount = values.size
        val known = values.map { it.type to it.name }.toMutableSet()
        val bundled = BundledThemeAssets.wallpapers.map { it.attribution() } +
            BundledOpenMojiCatalog.glyphs.map { glyph ->
                AssetAttribution(
                    type = "image",
                    name = "openmoji_${glyph.code.lowercase()}.png",
                    title = "${glyph.annotation} · OpenMoji",
                    creator = "OpenMoji project and contributors",
                    license = "CC BY-SA 4.0",
                    licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/",
                    sourceUrl = glyph.attributionPage()
                )
            }
        bundled.forEach { if (known.add(it.type to it.name)) values += it }
        if (values.size != originalCount || !file.isFile) persist(values)
    }

    @Synchronized
    fun record(attribution: AssetAttribution) {
        val values = readAll().filterNot { it.type == attribution.type && it.name == attribution.name }.toMutableList()
        values += attribution
        persist(values)
    }

    @Synchronized
    fun forAsset(type: String, name: String): AssetAttribution? =
        readAll().firstOrNull { it.type == type && it.name == name }

    @Synchronized
    fun forAnyAsset(name: String): AssetAttribution? = readAll().firstOrNull { it.name == name }

    @Synchronized
    fun remove(type: String, name: String) {
        persist(readAll().filterNot { it.type == type && it.name == name })
    }

    private fun persist(values: List<AssetAttribution>) {
        file.parentFile?.mkdirs()
        val json = JSONArray()
        values.forEach { item ->
            json.put(JSONObject()
                .put("type", item.type).put("name", item.name).put("title", item.title)
                .put("creator", item.creator).put("license", item.license)
                .put("licenseUrl", item.licenseUrl).put("sourceUrl", item.sourceUrl))
        }
        val temporary = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(json.toString().toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        if (!temporary.renameTo(file)) temporary.copyTo(file, overwrite = true)
        temporary.delete()
    }

    private fun readAll(): List<AssetAttribution> = runCatching {
        if (!file.isFile) return emptyList()
        val json = JSONArray(file.readText())
        (0 until json.length()).map { index ->
            val item = json.getJSONObject(index)
            AssetAttribution(
                item.optString("type"), item.optString("name"), item.optString("title"),
                item.optString("creator"), item.optString("license"),
                item.optString("licenseUrl"), item.optString("sourceUrl")
            )
        }
    }.getOrDefault(emptyList())
}
