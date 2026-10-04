package com.aicontrol.launcher.assets

import android.content.Context
import java.io.File

class AssetStore(context: Context) {
    private val appContext = context.applicationContext
    val root = File(appContext.filesDir, "launcher-assets").apply { mkdirs() }
    val wallpapers = File(root, "wallpapers").apply { mkdirs() }
    val icons = File(root, "icons").apply { mkdirs() }
    val images = File(root, "images").apply { mkdirs() }
    val cache = File(root, "cache").apply { mkdirs() }
    val iconPacks = File(root, "icon-packs").apply { mkdirs() }
    val themes = File(root, "themes").apply { mkdirs() }
    val styles = File(root, "styles").apply { mkdirs() }

    init {
        installBundledWallpapers()
    }

    fun wallpaper(name: String) = safeFile(wallpapers, name)
    fun icon(packageName: String) = safeFile(icons, packageName + ".png")
    fun image(name: String) = safeFile(images, name)
    fun iconPackDir(name: String) = File(iconPacks, safeName(name)).apply { mkdirs() }
    fun themeFile(name: String) = safeFile(themes, safeName(name) + ".json")
    fun themeAssetDir(name: String) = File(themes, safeName(name) + ".assets")

    fun themeIcon(name: String, packageName: String): File {
        val dir = File(themeAssetDir(name), "icons").apply { mkdirs() }
        val safePackage = packageName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(dir, "$safePackage.png")
    }

    fun safeFile(dir: File, name: String): File = File(dir, safeName(name))

    private fun installBundledWallpapers() {
        BundledThemeAssets.wallpapers.forEach { wallpaper ->
            val target = wallpaper(wallpaper.assetName)
            if (target.isFile && runCatching { ImageAssetValidation.validate(target) }.isSuccess) return@forEach
            target.delete()
            val temporary = File(target.parentFile, target.name + ".tmp")
            runCatching {
                temporary.delete()
                appContext.resources.openRawResource(wallpaper.resourceId).use { input ->
                    temporary.outputStream().use(input::copyTo)
                }
                ImageAssetValidation.validate(temporary)
                if (!temporary.renameTo(target)) temporary.copyTo(target, overwrite = true)
            }
            temporary.delete()
        }
    }

    private fun safeName(name: String): String =
        name.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "unnamed" }
}
