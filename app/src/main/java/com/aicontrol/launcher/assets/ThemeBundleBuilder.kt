package com.aicontrol.launcher.assets

import android.content.Context
import com.aicontrol.launcher.actions.ActionEngine
import com.aicontrol.launcher.icons.IconPackCompatibility
import com.aicontrol.launcher.icons.IconPackManager
import com.aicontrol.launcher.icons.InstalledIconPack
import com.aicontrol.launcher.icons.ThemeIconPackPolicy
import java.util.Locale
import java.util.concurrent.CancellationException

/** Result of the confirmed, bounded, on-device theme asset build. */
data class ThemeBundleBuildResult(
    val wallpaperFile: java.io.File,
    val wallpaper: WallpaperCandidate,
    val iconPack: InstalledIconPack?,
    val warnings: List<String>
)

/** Deterministically selects one suitable open-license image from bounded Commons metadata. */
object ThemeWallpaperSelector {
    private val restrictedTitle = Regex("\\b(spider[ -]?man|marvel|avengers?|batman|superman|pokemon|pikachu|disney|mickey mouse|star wars)\\b", RegexOption.IGNORE_CASE)
    private val unsuitableTitle = Regex("\\b(dress|clothing|fashion|portrait|selfie|person|people|model|logo|flag|map|diagram|screenshot|poster|cover|costume|character|actor|comic)\\b", RegexOption.IGNORE_CASE)
    private val abstractArtwork = Regex("\\b(abstract|geometric|gradient|pattern|texture|fractal|wallpaper|background|design|wave|nebula|art)\\b", RegexOption.IGNORE_CASE)

    fun choose(candidates: List<WallpaperCandidate>, query: String): WallpaperCandidate? {
        val terms = query.lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length > 2 && it !in setOf("abstract", "wallpaper", "background", "pattern", "geometric", "image", "art") }
            .toSet()
        return candidates.asSequence()
            .filter { AssetLicensePolicy.isReusable(it.license) }
            .filterNot { restrictedTitle.containsMatchIn(it.title) }
            .filterNot { unsuitableTitle.containsMatchIn(it.title) }
            .filter { abstractArtwork.containsMatchIn(it.title) }
            .filter { CommonsAssetPolicy.isImageUrl(it.imageUrl) }
            .filter { candidate ->
                candidate.width in 640..12_000 && candidate.height in 400..12_000 &&
                    candidate.width.toLong() * candidate.height <= 120_000_000L &&
                    candidate.width.toDouble() / candidate.height.toDouble() in 0.42..2.8
            }
            .maxByOrNull { candidate ->
                val title = candidate.title.lowercase(Locale.ROOT)
                val termScore = terms.count { it in title } * 100
                val artStyleScore = if (abstractArtwork.containsMatchIn(title)) 35 else 0
                val resolutionScore = (kotlin.math.ln((candidate.width.toDouble() * candidate.height).coerceAtLeast(1.0)) * 2).toInt()
                val ratio = candidate.width.toDouble() / candidate.height.toDouble()
                val ratioScore = (40 - (kotlin.math.abs(ratio - 0.72) * 15).toInt()).coerceAtLeast(0)
                termScore + artStyleScore + resolutionScore + ratioScore
            }
    }
}

/**
 * Searches only the already-approved free/open catalogs. It downloads no APKs and never transmits
 * the installed-app list; the final apply action remains a separate user choice.
 */
class ThemeBundleBuilder(context: Context) {
    private val appContext = context.applicationContext
    private val assets = LauncherAssetManager(appContext)
    private val engine = ActionEngine(appContext)
    private val iconPacks = IconPackManager(appContext)

    fun build(
        themeName: String,
        wallpaperQuery: String,
        shouldContinue: () -> Boolean,
        onProgress: (String) -> Unit
    ): ThemeBundleBuildResult {
        if (!shouldContinue()) throw CancellationException("Theme asset search canceled.")
        require(wallpaperQuery.trim().length in 3..120) { "Enter a search phrase between 3 and 120 characters." }
        AssetSearchPolicy.validate(wallpaperQuery.trim())
        onProgress("Searching Wikimedia Commons for reuse-filtered wallpaper…")
        val warnings = mutableListOf<String>()
        val selected = try {
            val candidates = CreativeCommonsAssetSearch().search(wallpaperQuery, limit = 12)
            val candidate = ThemeWallpaperSelector.choose(candidates, wallpaperQuery)
                ?: error("Commons returned no safe, reusable wallpaper for this query.")
            if (!shouldContinue()) throw CancellationException("Theme asset search canceled.")
            onProgress("Downloading ‘${candidate.title}’ (${candidate.license})…")
            val file = assets.downloadLicensedWallpaper(
                candidate,
                shouldContinue = shouldContinue,
                onProgress = { downloaded, total ->
                    val percent = if (total > 0) " ${downloaded * 100 / total}%" else ""
                    onProgress("Saving licensed wallpaper$percent…")
                }
            ).getOrThrow()
            candidate to file
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            val bundled = BundledThemeAssets.forTheme(themeName)
            val localFile = AssetStore(appContext).wallpaper(bundled.assetName)
            require(localFile.isFile) {
                "Commons is unavailable and the bundled CC0 wallpaper could not be read; the theme remains a draft. Check storage and retry."
            }
            ImageAssetValidation.validate(localFile)
            onProgress("Commons is unavailable; using the included CC0 wallpaper and continuing offline…")
            val detail = error.message?.replace(Regex("[\\r\\n]+"), " ")?.take(100)
            warnings += "Commons search/download unavailable${detail?.let { ": $it" } ?: ""}; used the included ${bundled.license} abstract wallpaper."
            bundled.candidate() to localFile
        }
        val wallpaperCandidate = selected.first
        val wallpaper = selected.second
        engine.linkThemeWallpaper(themeName, wallpaper)
        if (!shouldContinue()) throw CancellationException("Theme asset search canceled.")
        onProgress("Checking compatible app icon packs installed on this device…")
        val availablePacks = iconPacks.installedPacks()
        val preferredPackage = ThemeIconPackPolicy.preferredPackage(
            availablePacks.map { it.packageName },
            engine.themeValues(themeName).iconPackPackage ?: engine.installedIconPack()
        )
        val selectedPack = availablePacks.firstOrNull { it.packageName == preferredPackage }
        if (selectedPack != null) {
            engine.setThemeIconPack(themeName, selectedPack.packageName)
            onProgress("Saved the installed ${selectedPack.label} app icon pack with the theme…")
        } else {
            warnings += "No compatible Android app icon pack is installed. App icons will remain their original installed icons; install Appstract from F-Droid and choose it from the theme gallery to include real pack mappings (${IconPackCompatibility.APPSTRACT_FDROID_URL})."
        }
        return ThemeBundleBuildResult(wallpaper, wallpaperCandidate, selectedPack, warnings.distinct())
    }

}
