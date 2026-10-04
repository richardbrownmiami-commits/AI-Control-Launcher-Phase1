package com.aicontrol.launcher.assets

import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import com.aicontrol.launcher.R
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

/** Small, locally bundled OpenMoji subset for common launcher labels; images are unmodified. */
object BundledOpenMojiCatalog {
    val glyphs = listOf(
        OpenMojiGlyph("1F4F1", "mobile phone", "cell communication mobile phone telephone", ""),
        OpenMojiGlyph("1F5E8", "left speech bubble", "balloon bubble dialog left speech message sms talk text", ""),
        OpenMojiGlyph("1F4F7", "camera", "camera photo selfie snap video", ""),
        OpenMojiGlyph("1F4C5", "calendar", "calendar date schedule", ""),
        OpenMojiGlyph("23F0", "alarm clock", "alarm clock time", ""),
        OpenMojiGlyph("1F4E7", "e-mail", "email mail letter", ""),
        OpenMojiGlyph("1F310", "globe with meridians", "earth globe internet web world", ""),
        OpenMojiGlyph("1F5FA", "world map", "map world navigation", ""),
        OpenMojiGlyph("1F464", "bust in silhouette", "contacts people silhouette", ""),
        OpenMojiGlyph("2699", "gear", "cog cogwheel settings tool", ""),
        OpenMojiGlyph("1F4C1", "file folder", "file folder storage", ""),
        OpenMojiGlyph("1F3B5", "musical note", "audio music musical note sound", ""),
        OpenMojiGlyph("1F6CD", "shopping bags", "market shop store shopping", ""),
        OpenMojiGlyph("1F324", "sun behind small cloud", "cloud forecast sun weather", ""),
        OpenMojiGlyph("1F9EE", "abacus", "calculation calculator numbers", ""),
        OpenMojiGlyph("1F4DD", "memo", "memo notes notepad pencil", ""),
        OpenMojiGlyph("1F578", "spider web", "abstract browser web", "")
    )
}

/** Fetches a bounded official catalog and original, unmodified 72px PNGs with attribution. */
class OpenMojiIconLibrary(context: android.content.Context) {
    private val appContext = context.applicationContext
    private val store = AssetStore(context)
    private val downloader = ImageDownloader()
    private val attributionStore = AssetAttributionStore(context)
    private val catalogFile = File(store.cache, "openmoji-catalog.json")
    private val catalogUrl = "https://raw.githubusercontent.com/hfg-gmuend/openmoji/master/data/openmoji.json"
    private val licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/"
    private val bundledResourceIds = mapOf(
        "1F4F1" to R.drawable.openmoji_1f4f1,
        "1F5E8" to R.drawable.openmoji_1f5e8,
        "1F4F7" to R.drawable.openmoji_1f4f7,
        "1F4C5" to R.drawable.openmoji_1f4c5,
        "23F0" to R.drawable.openmoji_23f0,
        "1F4E7" to R.drawable.openmoji_1f4e7,
        "1F310" to R.drawable.openmoji_1f310,
        "1F5FA" to R.drawable.openmoji_1f5fa,
        "1F464" to R.drawable.openmoji_1f464,
        "2699" to R.drawable.openmoji_2699,
        "1F4C1" to R.drawable.openmoji_1f4c1,
        "1F3B5" to R.drawable.openmoji_1f3b5,
        "1F6CD" to R.drawable.openmoji_1f6cd,
        "1F324" to R.drawable.openmoji_1f324,
        "1F9EE" to R.drawable.openmoji_1f9ee,
        "1F4DD" to R.drawable.openmoji_1f4dd,
        "1F578" to R.drawable.openmoji_1f578
    )

    fun bundledCatalog(): List<OpenMojiGlyph> = BundledOpenMojiCatalog.glyphs

    fun catalog(): List<OpenMojiGlyph> {
        val remote = runCatching { loadRemoteCatalog() }.getOrDefault(emptyList())
        return (remote + bundledCatalog()).distinctBy { it.code }
    }

    private fun loadRemoteCatalog(): List<OpenMojiGlyph> {
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
            target.delete()
            val bundledId = bundledResourceIds[glyph.code.uppercase(Locale.ROOT)]
            if (bundledId != null) {
                if (!shouldContinue()) throw java.util.concurrent.CancellationException("Icon copy canceled.")
                val temporary = File(target.parentFile, target.name + ".tmp")
                temporary.delete()
                appContext.resources.openRawResource(bundledId).use { input -> temporary.outputStream().use(input::copyTo) }
                require(temporary.length() in 1..512L * 1024L) { "Bundled OpenMoji image exceeded the local asset limit." }
                ImageAssetValidation.validate(temporary)
                if (!temporary.renameTo(target)) temporary.copyTo(target, overwrite = true)
                temporary.delete()
            } else {
                onProgress("Downloading ${glyph.annotation} icon from OpenMoji…")
                downloader.download(
                    glyph.imageUrl(), target, maxBytes = 512 * 1024,
                    allowedHosts = setOf("raw.githubusercontent.com"), shouldContinue = shouldContinue
                ).getOrThrow()
            }
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
