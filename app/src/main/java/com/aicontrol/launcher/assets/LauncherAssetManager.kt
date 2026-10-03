package com.aicontrol.launcher.assets

import android.app.WallpaperManager
import android.content.Context
import android.graphics.drawable.Drawable
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class LauncherAssetManager(private val context: Context) {
    private val store = AssetStore(context)
    private val attributions = AssetAttributionStore(context)
    val downloader = ImageDownloader()

    fun downloadWallpaper(url: String, name: String = "current"): Result<File> =
        downloader.download(url, store.wallpaper(name)).map { it.file }

    fun downloadLicensedWallpaper(
        candidate: WallpaperCandidate,
        shouldContinue: () -> Boolean = { true },
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): Result<File> = runCatching {
        require(AssetLicensePolicy.isReusable(candidate.license)) { "This wallpaper's license is not approved for reuse." }
        require(CommonsAssetPolicy.isImageUrl(candidate.imageUrl)) {
            "Wallpaper image must come from Wikimedia Commons."
        }
        val base = candidate.title.substringAfterLast('/').substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9_-]"), "_").take(60).ifBlank { "commons_wallpaper" }
        val extension = candidate.imageUrl.substringBefore('?').substringAfterLast('.', "jpg")
            .lowercase().takeIf { it in setOf("jpg", "jpeg", "png", "webp") } ?: "jpg"
        val file = store.wallpaper("${base}_${System.currentTimeMillis()}.$extension")
        val downloaded = downloader.download(
            candidate.imageUrl,
            file,
            allowedHosts = CommonsAssetPolicy.imageHosts,
            shouldContinue = shouldContinue,
            onProgress = onProgress
        ).getOrThrow().file
        attributions.record(AssetAttribution(
            type = "wallpaper", name = downloaded.name, title = candidate.title,
            creator = candidate.creator, license = candidate.license,
            licenseUrl = candidate.licenseUrl, sourceUrl = candidate.pageUrl
        ))
        downloaded
    }

    /** Applies only inside this launcher; the system-wide wallpaper is left unchanged. */
    fun setLauncherWallpaper(file: File): Result<Unit> = runCatching {
        require(file.isFile && file.canonicalPath.startsWith(store.wallpapers.canonicalPath + File.separator)) {
            "Choose a wallpaper stored in this launcher's private asset library."
        }
        ImageAssetValidation.validate(file)
        check(context.getSharedPreferences("launcher_state", Context.MODE_PRIVATE)
            .edit().putString("active_wallpaper", file.name).commit()) {
            "The wallpaper is valid, but the launcher could not save it as the active background."
        }
    }

    fun applyWallpaper(file: File): Result<Unit> = runCatching {
        require(file.exists()) { "Wallpaper file not found" }
        val bitmap = ImageAssetValidation.decodeSampled(file, 4096) ?: error("Invalid wallpaper image")
        try {
            WallpaperManager.getInstance(context).setBitmap(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    fun activeWallpaper(): File? {
        val name = context.getSharedPreferences("launcher_state", Context.MODE_PRIVATE)
            .getString("active_wallpaper", "") ?: ""
        if (name.isBlank()) return null
        return store.wallpaper(name).takeIf { it.isFile }
    }

    fun downloadImage(url: String, name: String): Result<File> =
        downloader.download(url, store.image(name)).map { it.file }

    fun downloadIcon(url: String, packageName: String): Result<File> =
        downloader.download(url, store.icon(packageName)).map { it.file }

    fun iconOverride(packageName: String): File? = store.icon(packageName).takeIf { it.exists() }
    fun iconDrawable(packageName: String): Drawable? = iconOverride(packageName)?.let { Drawable.createFromPath(it.absolutePath) }
    fun clearIconOverride(packageName: String) { store.icon(packageName).delete() }

    fun clearIconPack(name: String): Result<Unit> = runCatching {
        val dir = File(store.iconPacks, name)
        if (dir.exists()) dir.deleteRecursively()
    }

    fun clearAllIconOverrides() {
        store.icons.listFiles()?.forEach { if (it.isFile) it.delete() }
    }

    fun deleteAsset(type: String, name: String): Result<Unit> = runCatching {
        val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val target = when (type.lowercase()) {
            "wallpaper" -> store.wallpaper(safe)
            "image" -> store.image(safe)
            "icon" -> store.icon(safe.removeSuffix(".png"))
            "icon_pack" -> File(store.iconPacks, safe)
            "theme" -> store.themeFile(safe.removeSuffix(".json"))
            "style" -> File(store.root, "styles/$safe")
            else -> error("Unknown asset type")
        }
        require(target.canonicalPath.startsWith(store.root.canonicalPath + File.separator)) { "Invalid asset path" }
        if (target.exists()) require(target.deleteRecursively()) { "Unable to delete asset" }
    }

    fun createIconPack(name: String, icons: List<Pair<String, String>>): Result<File> = runCatching {
        val dir = store.iconPackDir(name)
        val manifest = JSONObject().put("name", name).put("icons", JSONArray())
        val array = manifest.getJSONArray("icons")
        icons.forEach { (pkg, url) ->
            require(url.startsWith("https://")) { "Only HTTPS icon URLs are allowed" }
            val file = File(dir, pkg.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".png")
            downloader.download(url, file).getOrThrow()
            array.put(JSONObject().put("package", pkg).put("file", file.name))
        }
        File(dir, "manifest.json").writeText(manifest.toString())
        dir
    }

    fun applyIconPack(name: String): Result<Int> = runCatching {
        val dir = File(store.iconPacks, name)
        require(dir.isDirectory)
        val manifest = JSONObject(File(dir, "manifest.json").readText())
        val icons = manifest.getJSONArray("icons")
        var count = 0
        for (i in 0 until icons.length()) {
            val item = icons.getJSONObject(i)
            val pkg = item.getString("package")
            val source = File(dir, item.getString("file"))
            require(source.isFile)
            source.copyTo(store.icon(pkg), overwrite = true)
            count++
        }
        count
    }
}
