package com.aicontrol.launcher.assets

import android.content.Context
import java.io.File

class AssetCatalog(context: Context) {
    private val store = AssetStore(context)

    fun listWallpapers(): List<File> = store.wallpapers.listFiles()?.filter { it.isFile } ?: emptyList()
    fun listImages(): List<File> = store.images.listFiles()?.filter { it.isFile } ?: emptyList()
    fun listIconOverrides(): List<File> = store.icons.listFiles()?.filter { it.isFile } ?: emptyList()

    fun deleteWallpaper(name: String): Boolean = store.wallpaper(name).delete()
    fun deleteImage(name: String): Boolean = store.image(name).delete()
    fun deleteIconOverride(packageName: String): Boolean = store.icon(packageName).delete()
}
