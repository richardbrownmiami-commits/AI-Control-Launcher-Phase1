package com.aicontrol.launcher.assets

import android.app.WallpaperManager
import android.content.Context
import android.graphics.BitmapFactory
import java.io.File

class LauncherAssetManager(private val context: Context) {
    private val store = AssetStore(context)
    private val downloader = ImageDownloader()

    fun downloadWallpaper(url: String, name: String = "current"): Result<File> =
        downloader.download(url, store.wallpaper(name)).map { it.file }

    fun applyWallpaper(file: File): Result<Unit> = runCatching {
        require(file.exists()) { "Wallpaper file not found" }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
            ?: error("Invalid wallpaper image")
        WallpaperManager.getInstance(context).setBitmap(bitmap)
        bitmap.recycle()
    }

    fun downloadImage(url: String, name: String): Result<File> =
        downloader.download(url, store.image(name)).map { it.file }

    fun downloadIcon(url: String, packageName: String): Result<File> =
        downloader.download(url, store.icon(packageName)).map { it.file }

    fun iconOverride(packageName: String): File? =
        store.icon(packageName).takeIf { it.exists() }

    fun clearIconOverride(packageName: String) {
        store.icon(packageName).delete()
    }
}
