package com.aicontrol.launcher.assets

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class LauncherAssetManager(private val context: Context) {
    private val store = AssetStore(context)
    val downloader = ImageDownloader()

    fun downloadWallpaper(url: String, name: String = "current"): Result<File> =
        downloader.download(url, store.wallpaper(name)).map { it.file }

    fun applyWallpaper(file: File): Result<Unit> = runCatching {
        require(file.exists()) { "Wallpaper file not found" }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: error("Invalid wallpaper image")
        WallpaperManager.getInstance(context).setBitmap(bitmap)
        bitmap.recycle()
    }

    fun downloadImage(url: String, name: String): Result<File> =
        downloader.download(url, store.image(name)).map { it.file }

    fun downloadIcon(url: String, packageName: String): Result<File> =
        downloader.download(url, store.icon(packageName)).map { it.file }

    fun iconOverride(packageName: String): File? = store.icon(packageName).takeIf { it.exists() }
    fun iconDrawable(packageName: String): Drawable? = iconOverride(packageName)?.let { Drawable.createFromPath(it.absolutePath) }
    fun clearIconOverride(packageName: String) { store.icon(packageName).delete() }

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