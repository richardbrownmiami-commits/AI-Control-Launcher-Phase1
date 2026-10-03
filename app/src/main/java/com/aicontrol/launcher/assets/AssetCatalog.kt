package com.aicontrol.launcher.assets

import android.content.Context
import java.io.File

class AssetCatalog(context: Context) {
    private val store = AssetStore(context)
    private val attributions = AssetAttributionStore(context)
    fun listWallpapers(): List<File> = files(store.wallpapers)
    fun listImages(): List<File> = files(store.images)
    fun listIconOverrides(): List<File> = files(store.icons)
    fun listIconPacks(): List<File> = store.iconPacks.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
    fun listThemes(): List<File> = files(store.themes)
    fun listStyles(): List<File> = files(store.styles)
    fun deleteStyle(name: String): Boolean = store.safeFile(store.styles, name).delete()
    fun deleteWallpaper(name: String): Boolean {
        val deleted = store.wallpaper(name).delete()
        if (deleted) attributions.remove("wallpaper", name)
        return deleted
    }
    fun wallpaperAttribution(name: String): AssetAttribution? = attributions.forAsset("wallpaper", name)
    fun deleteImage(name: String): Boolean {
        val deleted = store.image(name).delete()
        if (deleted) attributions.remove("image", name)
        return deleted
    }
    fun deleteIconOverride(packageName: String): Boolean = store.icon(packageName.removeSuffix(".png")).delete()
    fun deleteIconPack(name: String): Boolean = deleteRecursively(File(store.iconPacks, name))
    fun deleteTheme(name: String): Boolean {
        val themeName = name.removeSuffix(".json")
        val deleted = store.themeFile(themeName).delete()
        val themeAssetsDeleted = File(store.themes, "${themeName.replace(Regex("[^A-Za-z0-9._-]"), "_")}.assets").deleteRecursively()
        return deleted || themeAssetsDeleted
    }
    fun imageAttribution(name: String): AssetAttribution? = attributions.forAsset("image", name)
    fun anyAttribution(name: String): AssetAttribution? = attributions.forAnyAsset(name)
    private fun files(dir: File): List<File> = dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
    private fun deleteRecursively(file: File): Boolean {
        if (file.isDirectory) file.listFiles()?.forEach { deleteRecursively(it) }
        return file.delete()
    }
}
