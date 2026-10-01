package com.aicontrol.launcher.assets

import android.content.Context
import java.io.File

class AssetStore(context: Context) {
    val root = File(context.filesDir, "launcher-assets").apply { mkdirs() }
    val wallpapers = File(root, "wallpapers").apply { mkdirs() }
    val icons = File(root, "icons").apply { mkdirs() }
    val images = File(root, "images").apply { mkdirs() }
    val cache = File(root, "cache").apply { mkdirs() }
    val iconPacks = File(root, "icon-packs").apply { mkdirs() }
    val themes = File(root, "themes").apply { mkdirs() }
    fun wallpaper(name: String) = safeFile(wallpapers, name)
    fun icon(packageName: String) = safeFile(icons, packageName + ".png")
    fun image(name: String) = safeFile(images, name)
    fun iconPackDir(name: String) = File(iconPacks, safeName(name)).apply { mkdirs() }
    fun themeFile(name: String) = safeFile(themes, safeName(name) + ".json")
    fun safeFile(dir: File, name: String): File = File(dir, safeName(name))
    private fun safeName(name: String): String = name.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "unnamed" }
}