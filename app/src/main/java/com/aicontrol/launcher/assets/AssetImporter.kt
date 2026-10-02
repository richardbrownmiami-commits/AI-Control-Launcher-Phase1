package com.aicontrol.launcher.assets

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

class AssetImporter(private val context: Context) {
    private val store = AssetStore(context)

    fun importWallpaper(uri: Uri, maxBytes: Long = 10L * 1024L * 1024L): File {
        val mime = context.contentResolver.getType(uri)?.substringBefore(';')?.lowercase()
        require(mime in setOf("image/jpeg", "image/png", "image/webp")) {
            "Choose a JPEG, PNG, or WebP image from Downloads or another file provider."
        }
        val displayName = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?.takeIf { !it.isNullOrBlank() } ?: "local-wallpaper"
        val extension = when (mime) { "image/jpeg" -> ".jpg"; "image/png" -> ".png"; else -> ".webp" }
        val base = displayName.substringBeforeLast('.', displayName).replace(Regex("[^A-Za-z0-9_-]"), "_")
            .take(60).ifBlank { "local-wallpaper" }
        var target = store.wallpaper("$base$extension")
        if (target.exists()) target = store.wallpaper("${base}_${System.currentTimeMillis()}$extension")
        try {
            var total = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= maxBytes) { "Image exceeds the 10 MB import limit." }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("Could not open the selected image.")
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.absolutePath, bounds)
            require(bounds.outWidth in 1..12_000 && bounds.outHeight in 1..12_000 &&
                bounds.outWidth.toLong() * bounds.outHeight <= 40_000_000L) { "Image dimensions are too large or invalid." }
            var sample = 1
            while (bounds.outWidth.toLong() / sample * (bounds.outHeight.toLong() / sample) > 2_000_000L) sample *= 2
            val validation = BitmapFactory.decodeFile(target.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            require(validation != null) { "The selected file is not a valid image." }
            validation.recycle()
            return target
        } catch (error: Exception) {
            target.delete()
            throw error
        }
    }
}
