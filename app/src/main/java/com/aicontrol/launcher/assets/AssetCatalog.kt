package com.aicontrol.launcher.assets

import android.content.Context
import java.io.File

class AssetCatalog(context: Context) {
    private val store = AssetStore(context)
    fun listWallpapers(): List<File> = files(store.wallpapers)
    fun listImages(): List<File> = files(store.images)
    fun listIconOverrides(): List<File> = files(store.icons)
    fun listIconPacks(): List<File> = store.iconPacks.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
    fun listThemes(): List<File> = files(store.themes)
    fun listStyles(): List<File> = files(store.styles)
    fun deleteStyle(name: String): Boolean = store.safeFile(store.styles, name).delete()
    fun deleteWallpaper(name: String): Boolean = store.wallpaper(name).delete()
    fun deleteImage(name: String): Boolean = store.image(name).delete()
    fun deleteIconOverride(packageName: String): Boolean = store.icon(packageName.removeSuffix(".png")).delete()
    fun deleteIconPack(name: String): Boolean = deleteRecursively(File(store.iconPacks, name))
    fun deleteTheme(name: String): Boolean = store.themeFile(name.removeSuffix(".json")).delete()
    private fun files(dir: File): List<File> = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) file.listFiles()?.forEach { deleteRecursively(it) }
        return file.delete()
    }
}