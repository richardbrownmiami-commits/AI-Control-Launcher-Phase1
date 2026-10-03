package com.aicontrol.launcher.assets

import android.content.Context
import com.aicontrol.launcher.actions.ActionEngine
import com.aicontrol.launcher.apps.AppInfo
import java.util.Locale
import java.util.concurrent.CancellationException

/** Result of the confirmed, bounded, on-device theme asset build. */
data class ThemeBundleBuildResult(
    val wallpaperFile: java.io.File,
    val wallpaper: WallpaperCandidate,
    val iconMappings: List<AppIconAssignment>,
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
    private val openMoji = OpenMojiIconLibrary(appContext)

    fun build(
        themeName: String,
        wallpaperQuery: String,
        apps: List<AppInfo>,
        shouldContinue: () -> Boolean,
        onProgress: (String) -> Unit
    ): ThemeBundleBuildResult {
        if (!shouldContinue()) throw CancellationException("Theme asset search canceled.")
        onProgress("Searching Wikimedia Commons for reuse-filtered wallpaper…")
        val candidates = CreativeCommonsAssetSearch().search(wallpaperQuery, limit = 12)
        val wallpaperCandidate = ThemeWallpaperSelector.choose(candidates, wallpaperQuery)
            ?: error("No safe, reuse-filtered wallpaper with suitable image dimensions was found. The theme is still saved as a draft; try a broader abstract query.")
        if (!shouldContinue()) throw CancellationException("Theme asset search canceled.")
        onProgress("Downloading ‘${wallpaperCandidate.title}’ (${wallpaperCandidate.license})…")
        val wallpaper = assets.downloadLicensedWallpaper(
            wallpaperCandidate,
            shouldContinue = shouldContinue,
            onProgress = { downloaded, total ->
                val percent = if (total > 0) " ${downloaded * 100 / total}%" else ""
                onProgress("Saving licensed wallpaper$percent…")
            }
        ).getOrThrow()
        engine.linkThemeWallpaper(themeName, wallpaper)

        val assignments = mutableListOf<AppIconAssignment>()
        val warnings = mutableListOf<String>()
        try {
            onProgress("Finding matching, attribution-preserving OpenMoji icons on this device…")
            val glyphs = openMoji.catalog()
            val matches = OpenMojiIconMatcher.assign(
                glyphs,
                apps.map { it.packageName to it.label },
                themeName
            ).take(40)
            if (matches.isEmpty()) {
                warnings += "No installed app labels matched OpenMoji's generic icon categories; unassigned apps will keep their current icons."
            }
            matches.forEachIndexed { index, match ->
                if (!shouldContinue()) throw CancellationException("Theme asset search canceled.")
                try {
                    onProgress("Adding matching icon ${index + 1} of ${matches.size}: ${match.appLabel} · ${match.glyph.annotation}…")
                    val file = openMoji.download(match.glyph, shouldContinue) { onProgress(it) }
                    engine.linkThemeIcon(themeName, match.packageName, file)
                    assignments += match
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    warnings += "Skipped the ${match.appLabel} icon (${error.message ?: "download or image validation failed"})."
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            warnings += "OpenMoji icons could not be added (${error.message ?: "catalog unavailable"}); unmapped apps keep their current icons."
        }
        return ThemeBundleBuildResult(wallpaper, wallpaperCandidate, assignments, warnings.distinct())
    }

}
