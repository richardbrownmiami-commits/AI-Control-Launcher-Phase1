package com.aicontrol.launcher.assets

import android.content.Context
import java.io.File

class AssetStore(context: Context) {
    private val root = File(context.filesDir, "launcher-assets").apply { mkdirs() }
    val wallpapers = File(root, "wallpapers").apply { mkdirs() }
    val icons = File(root, "icons").apply { mkdirs() }
    val images = File(root, "images").apply { mkdirs() }
    val cache = File(root, "cache").apply { mkdirs() }

    fun wallpaper(name: String) = safeFile(wallpapers, name)
    fun icon(packageName: String) = safeFile(icons, "$packageName.png")
    fun image(name: String) = safeFile(images, name)

    private fun safeFile(dir: File, name: String): File {
        val clean = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return File(dir, clean)
    }
}
