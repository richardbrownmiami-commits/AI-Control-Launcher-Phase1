package com.aicontrol.launcher.assets

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONArray

data class OpenMojiGlyph(
    val code: String,
    val annotation: String,
    val tags: String,
    val openMojiTags: String
) {
    fun searchableText() = "$annotation $tags $openMojiTags".lowercase(Locale.ROOT)
    fun imageUrl() = "https://raw.githubusercontent.com/hfg-gmuend/openmoji/master/color/72x72/$code.png"
    fun attributionPage() = "https://github.com/hfg-gmuend/openmoji/blob/master/color/72x72/$code.png"
}

data class AppIconAssignment(val packageName: String, val appLabel: String, val glyph: OpenMojiGlyph)

/** Local-only mapping from app labels to generic OpenMoji symbols; no installed-app inventory is sent online. */
object OpenMojiIconMatcher {
    private data class Slot(
        val labelPattern: Regex,
        val preferredCodes: List<String>,
        val annotationHints: List<String>
    )

    private val slots = listOf(
        Slot(Regex("\\b(phone|dialer|telephone|calls?)\\b", RegexOption.IGNORE_CASE), listOf("1F4F1", "260E"), listOf("mobile phone", "telephone")),
        Slot(Regex("\\b(messages?|sms|chat|messaging|signal|telegram|whatsapp)\\b", RegexOption.IGNORE_CASE), listOf("1F5E8", "1F4AC"), listOf("left speech bubble", "speech balloon")),
        Slot(Regex("\\b(camera|photos?|gallery|photography)\\b", RegexOption.IGNORE_CASE), listOf("1F4F7", "1F3A5"), listOf("camera", "movie camera")),
        Slot(Regex("\\b(calendar|agenda|schedule)\\b", RegexOption.IGNORE_CASE), listOf("1F4C5", "1F4C6"), listOf("calendar", "tear-off calendar")),
        Slot(Regex("\\b(clock|alarm|timer|stopwatch)\\b", RegexOption.IGNORE_CASE), listOf("23F0", "23F1", "231A"), listOf("alarm clock", "stopwatch")),
        Slot(Regex("\\b(mail|email|e-mail|gmail|outlook)\\b", RegexOption.IGNORE_CASE), listOf("1F4E7", "2709"), listOf("e-mail", "envelope")),
        Slot(Regex("\\b(browser|chrome|firefox|internet|safari|web)\\b", RegexOption.IGNORE_CASE), listOf("1F310", "E051"), listOf("globe with meridians", "safari")),
        Slot(Regex("\\b(maps?|navigation|directions)\\b", RegexOption.IGNORE_CASE), listOf("1F5FA", "1F9ED"), listOf("world map", "compass")),
        Slot(Regex("\\b(contacts?|address book|people)\\b", RegexOption.IGNORE_CASE), listOf("1F464", "1F465"), listOf("bust in silhouette", "busts in silhouette")),
        Slot(Regex("\\b(settings?|configuration|preferences)\\b", RegexOption.IGNORE_CASE), listOf("2699"), listOf("gear")),
        Slot(Regex("\\b(files?|file manager|storage|drive)\\b", RegexOption.IGNORE_CASE), listOf("1F4C1", "1F4C2"), listOf("file folder", "open file folder")),
        Slot(Regex("\\b(music|audio|player|spotify)\\b", RegexOption.IGNORE_CASE), listOf("1F3B5", "1F3A7"), listOf("musical notes", "headphone")),
        Slot(Regex("\\b(store|shop|market|play store)\\b", RegexOption.IGNORE_CASE), listOf("1F6CD", "1F6D2"), listOf("shopping bags", "shopping cart")),
        Slot(Regex("\\b(weather|forecast)\\b", RegexOption.IGNORE_CASE), listOf("1F324", "2600"), listOf("sun behind small cloud", "sun")),
        Slot(Regex("\\b(calculator|calc)\\b", RegexOption.IGNORE_CASE), listOf("1F9EE", "1F522"), listOf("abacus", "input numbers")),
        Slot(Regex("\\b(notes?|memo|notepad)\\b", RegexOption.IGNORE_CASE), listOf("1F4DD", "1F5D2"), listOf("memo", "spiral notepad"))
    )

    fun assign(glyphs: List<OpenMojiGlyph>, apps: List<Pair<String, String>>, themeName: String): List<AppIconAssignment> {
        val available = glyphs.filter { it.code.matches(Regex("[0-9A-F-]{4,40}")) }
            .filterNot { it.code.contains("-1F3FB") || it.code.contains("-1F3FC") || it.code.contains("-1F3FD") || it.code.contains("-1F3FE") || it.code.contains("-1F3FF") }
            .associateBy { it.code }
        val chosen = linkedMapOf<String, AppIconAssignment>()
        apps.distinctBy { it.first }.forEach { (packageName, label) ->
            val slot = slots.firstOrNull { it.labelPattern.containsMatchIn(label) } ?: return@forEach
            val isWebCategory = slot.labelPattern.containsMatchIn("browser") || slot.labelPattern.containsMatchIn("web")
            val preferred = if (themeName.contains("spider", ignoreCase = true) && isWebCategory) listOf("1F578") + slot.preferredCodes else slot.preferredCodes
            val glyph = preferred.firstNotNullOfOrNull { code -> available[code] }
                ?: available.values.firstOrNull { candidate ->
                    slot.annotationHints.any { hint -> candidate.annotation.equals(hint, ignoreCase = true) || hint in candidate.searchableText() }
                }
                ?: return@forEach
            chosen[packageName] = AppIconAssignment(packageName, label, glyph)
        }
        return chosen.values.toList()
    }
}

/** Fetches a bounded official catalog and original, unmodified 72px PNGs with attribution. */
class OpenMojiIconLibrary(context: android.content.Context) {
    private val store = AssetStore(context)
    private val downloader = ImageDownloader()
    private val attributionStore = AssetAttributionStore(context)
    private val catalogFile = File(store.cache, "openmoji-catalog.json")
    private val catalogUrl = "https://raw.githubusercontent.com/hfg-gmuend/openmoji/master/data/openmoji.json"
    private val licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/"

    fun catalog(): List<OpenMojiGlyph> {
        if (!catalogFile.isFile || System.currentTimeMillis() - catalogFile.lastModified() > 7L * 24 * 60 * 60 * 1000) {
            val source = URL(catalogUrl)
            require(source.protocol == "https" && source.host == "raw.githubusercontent.com") { "OpenMoji catalog host is not approved." }
            val connection = (source.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 25_000
                setRequestProperty("User-Agent", "AI-Control-Launcher/0.4 (OpenMoji CC BY-SA 4.0 assets)")
                setRequestProperty("Accept", "application/json")
            }
            try {
                require(connection.responseCode in 200..299) { "OpenMoji catalog returned HTTP ${connection.responseCode}." }
                require(connection.url.protocol == "https" && connection.url.host == "raw.githubusercontent.com") {
                    "OpenMoji catalog redirected outside the approved source host."
                }
                val bytes = connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= 6 * 1024 * 1024) { "OpenMoji catalog exceeds the 6 MB metadata limit." }
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                require(bytes.isNotEmpty()) { "OpenMoji catalog was empty." }
                catalogFile.parentFile?.mkdirs()
                catalogFile.writeBytes(bytes)
            } finally {
                connection.disconnect()
            }
        }
        val json = JSONArray(catalogFile.readText())
        return (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            val code = item.optString("hexcode").uppercase(Locale.ROOT)
            if (!code.matches(Regex("[0-9A-F-]{4,40}"))) return@mapNotNull null
            if (item.optString("skintone").isNotBlank()) return@mapNotNull null
            OpenMojiGlyph(
                code,
                item.optString("annotation").take(120),
                item.optString("tags").take(600),
                item.optString("openmoji_tags").take(300)
            )
        }
    }

    fun download(glyph: OpenMojiGlyph, shouldContinue: () -> Boolean = { true }, onProgress: (String) -> Unit = {}): File {
        val fileName = "openmoji_${glyph.code}.png"
        val target = store.image(fileName)
        val cachedValid = target.isFile && target.length() <= 512 * 1024 &&
            runCatching { ImageAssetValidation.validate(target) }.isSuccess
        if (!cachedValid) {
            onProgress("Downloading ${glyph.annotation} icon from OpenMoji…")
            target.delete()
            downloader.download(
                glyph.imageUrl(), target, maxBytes = 512 * 1024,
                allowedHosts = setOf("raw.githubusercontent.com"), shouldContinue = shouldContinue
            ).getOrThrow()
        }
        attributionStore.record(AssetAttribution(
            type = "image",
            name = target.name,
            title = "${glyph.annotation} · OpenMoji",
            creator = "OpenMoji project and contributors",
            license = "CC BY-SA 4.0",
            licenseUrl = licenseUrl,
            sourceUrl = glyph.attributionPage()
        ))
        return target
    }
}
