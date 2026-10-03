package com.aicontrol.launcher.assets

import android.text.Html
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

object AssetSearchPolicy {
    private val restrictedCharacterOrFranchise = Regex(
        "\\b(spider[ -]?man|marvel|avengers?|batman|superman|pokemon|pikachu|disney|mickey mouse|star wars)\\b",
        RegexOption.IGNORE_CASE
    )

    fun validate(query: String) {
        require(!restrictedCharacterOrFranchise.containsMatchIn(query)) {
            "Search only for an abstract color, shape, or pattern. Character/franchise names are not sent to the asset catalog."
        }
    }
}

/** A Commons bitmap candidate with the attribution data needed for lawful reuse. */
data class WallpaperCandidate(
    val title: String,
    val imageUrl: String,
    val pageUrl: String,
    val creator: String,
    val license: String,
    val licenseUrl: String,
    val width: Int = 0,
    val height: Int = 0
)

internal fun commonsSearchQueryVariants(query: String): List<String> {
    val generic = setOf("wallpaper", "wallpapers", "background", "backgrounds", "image", "images", "photo", "photos", "desktop", "phone", "mobile", "for", "a", "the", "with", "and")
    val words = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+" )).filter { it.isNotBlank() && it !in generic }.distinct()
    if (words.size <= 1) return listOf(query)
    val colors = words.filter { it in setOf("red", "blue", "green", "teal", "cyan", "orange", "yellow", "purple", "violet", "pink", "black", "white", "navy") }
    val motifWords = words.filter { it in setOf("abstract", "geometric", "gradient", "pattern", "web", "wave", "ocean", "nebula", "texture", "minimal") }
    val variants = buildList {
        add(query)
        if (colors.size >= 2) {
            add((colors.take(2) + listOf("abstract")).joinToString(" "))
            if (motifWords.isNotEmpty()) add(motifWords.take(3).joinToString(" "))
        } else {
            val pairs = words.zipWithNext().map { (left, right) -> "$left $right" }
            addAll(pairs.take(3))
            if (colors.size == 1 && "abstract" !in words) add("${colors.first()} abstract")
        }
        if ("abstract" in words) add("abstract wallpaper") else add("wallpaper")
    }
    return variants.map { it.trim() }.filter { it.length in 3..120 }.distinct().take(5)
}

/** Searches Wikimedia Commons only after the user asks; no account or paid API is used. */
class CreativeCommonsAssetSearch {
    fun search(query: String, limit: Int = 8): List<WallpaperCandidate> {
        val normalized = query.trim()
        require(normalized.length in 3..120) { "Enter a search phrase between 3 and 120 characters." }
        AssetSearchPolicy.validate(normalized)
        val requested = limit.coerceIn(1, 12)
        val results = linkedMapOf<String, WallpaperCandidate>()
        for (searchQuery in commonsSearchQueryVariants(normalized)) {
            searchOnce(searchQuery, 12).forEach { results.putIfAbsent(it.pageUrl, it) }
            if (results.size >= requested) break
        }
        if (results.isEmpty() && normalized.contains(Regex("\\bwallpapers?\\b", RegexOption.IGNORE_CASE))) {
            searchOnce("wallpaper", 12).forEach { results.putIfAbsent(it.pageUrl, it) }
        }
        return results.values.take(requested)
    }

    private fun searchOnce(query: String, limit: Int): List<WallpaperCandidate> {
        val parameters = listOf(
            "action=query", "generator=search", "gsrnamespace=6",
            "gsrsearch=${encode(query)}", "gsrlimit=${limit.coerceIn(1, 12)}",
            "prop=imageinfo", "iiprop=url|mime|size|extmetadata", "iiurlwidth=1600", "format=json"
        ).joinToString("&")
        val connection = (URL("https://commons.wikimedia.org/w/api.php?$parameters")
            .openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "AI-Control-Launcher/0.4 (open-license wallpaper search)")
        }
        try {
            require(connection.responseCode in 200..299) { "Commons search returned HTTP ${connection.responseCode}." }
            val payload = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= 2 * 1024 * 1024) { "Commons search metadata exceeds the 2 MB response limit." }
                    output.write(buffer, 0, count)
                }
                output.toString(Charsets.UTF_8.name())
            }
            val pages = JSONObject(payload).optJSONObject("query")?.optJSONObject("pages") ?: return emptyList()
            val candidates = mutableListOf<WallpaperCandidate>()
            val keys = pages.keys()
            while (keys.hasNext()) {
                val page = pages.getJSONObject(keys.next())
                val info = page.optJSONArray("imageinfo")?.optJSONObject(0) ?: continue
                val mime = info.optString("mime")
                // iiurlwidth asks Commons for a device-ready thumbnail; avoid fetching enormous originals.
                val imageUrl = info.optString("thumburl").ifBlank { info.optString("url") }
                val thumbnailExtension = imageUrl.substringBefore('?').substringAfterLast('.', "").lowercase()
                if (mime !in setOf("image/jpeg", "image/png", "image/webp") &&
                    !(mime == "image/tiff" && thumbnailExtension in setOf("jpg", "jpeg", "png", "webp"))) continue
                if (!CommonsAssetPolicy.isImageUrl(imageUrl)) continue
                val metadata = info.optJSONObject("extmetadata") ?: continue
                val license = metadata.value("LicenseShortName") ?: continue
                if (!AssetLicensePolicy.isReusable(license)) continue
                val title = page.optString("title").removePrefix("File:")
                if (title.isBlank()) continue
                val creator = metadata.value("Artist")?.ifBlank { "Creator not specified" } ?: "Creator not specified"
                val licenseUrl = metadata.value("LicenseUrl").orEmpty()
                val pageUrl = "https://commons.wikimedia.org/wiki/${encode("File:${title.replace(' ', '_')}").replace("%3A", ":") }"
                candidates += WallpaperCandidate(
                    title, imageUrl, pageUrl, creator, license, licenseUrl,
                    info.optInt("width", 0), info.optInt("height", 0)
                )
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
