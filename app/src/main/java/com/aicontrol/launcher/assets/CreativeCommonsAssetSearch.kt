package com.aicontrol.launcher.assets

import android.text.Html
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

/** A Commons bitmap candidate with the attribution data needed for lawful reuse. */
data class WallpaperCandidate(
    val title: String,
    val imageUrl: String,
    val pageUrl: String,
    val creator: String,
    val license: String,
    val licenseUrl: String
)

/** Searches Wikimedia Commons only after the user asks; no account or paid API is used. */
class CreativeCommonsAssetSearch {
    fun search(query: String, limit: Int = 8): List<WallpaperCandidate> {
        require(query.trim().length in 3..120) { "Enter a search phrase between 3 and 120 characters." }
        val parameters = listOf(
            "action=query", "generator=search", "gsrnamespace=6",
            "gsrsearch=${encode(query.trim())}", "gsrlimit=${limit.coerceIn(1, 12)}",
            "prop=imageinfo", "iiprop=url|mime|extmetadata", "iiurlwidth=1600", "format=json"
        ).joinToString("&")
        val connection = (URL("https://commons.wikimedia.org/w/api.php?$parameters")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "AI-Control-Launcher/0.4 (open-license wallpaper search)")
        }
        try {
            require(connection.responseCode in 200..299) { "Commons search returned HTTP ${connection.responseCode}." }
            val payload = connection.inputStream.bufferedReader().use { it.readText() }
            val pages = JSONObject(payload).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
            val candidates = mutableListOf<WallpaperCandidate>()
            val keys = pages.keys()
            while (keys.hasNext()) {
                val page = pages.getJSONObject(keys.next())
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val mime = info.optString("mime")
                if (mime !in setOf("image/jpeg", "image/png", "image/webp")) continue
                val imageUrl = info.optString("url")
                if (!imageUrl.startsWith("https://upload.wikimedia.org/wikipedia/commons/")) continue
                val metadata = info.optJSONObject("extmetadata") ?: continue
                val license = metadata.value("LicenseShortName") ?: continue
                if (!AssetLicensePolicy.isReusable(license)) continue
                val title = page.optString("title").removePrefix("File:")
                if (title.isBlank()) continue
                val creator = metadata.value("Artist")?.ifBlank { "Creator not specified" } ?: "Creator not specified"
                val licenseUrl = metadata.value("LicenseUrl").orEmpty()
                val pageUrl = "https://commons.wikimedia.org/wiki/${encode("File:${title.replace(' ', '_')}").replace("%3A", ":") }"
                candidates += WallpaperCandidate(title, imageUrl, pageUrl, creator, license, licenseUrl)
            }
            return candidates
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.value(key: String): String? {
        val html = optJSONObject(key)?.optString("value")?.takeIf { it.isNotBlank() } ?: return null
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
}
