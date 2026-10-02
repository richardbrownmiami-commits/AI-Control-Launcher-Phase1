package com.aicontrol.launcher.assets

import android.content.Context
import java.io.File
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
    private val file = File(AssetStore(context).root, "asset-attributions.json")

    @Synchronized
    fun record(attribution: AssetAttribution) {
        val values = readAll().filterNot { it.type == attribution.type && it.name == attribution.name }.toMutableList()
        values += attribution
        val json = JSONArray()
        values.forEach { item ->
            json.put(JSONObject()
                .put("type", item.type).put("name", item.name).put("title", item.title)
                .put("creator", item.creator).put("license", item.license)
                .put("licenseUrl", item.licenseUrl).put("sourceUrl", item.sourceUrl))
        }
        file.writeText(json.toString())
    }

    @Synchronized
    fun forAsset(type: String, name: String): AssetAttribution? =
        readAll().firstOrNull { it.type == type && it.name == name }

    @Synchronized
    fun remove(type: String, name: String) {
        val values = readAll().filterNot { it.type == type && it.name == name }
        val json = JSONArray()
        values.forEach { item ->
            json.put(JSONObject()
                .put("type", item.type).put("name", item.name).put("title", item.title)
                .put("creator", item.creator).put("license", item.license)
                .put("licenseUrl", item.licenseUrl).put("sourceUrl", item.sourceUrl))
        }
        file.writeText(json.toString())
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
