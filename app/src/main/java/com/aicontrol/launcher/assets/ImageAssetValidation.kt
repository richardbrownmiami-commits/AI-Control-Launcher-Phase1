package com.aicontrol.launcher.assets

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Validates decoded dimensions before allocating a bounded bitmap. */
object ImageAssetValidation {
    private const val MAX_SOURCE_DIMENSION = 12_000
    private const val MAX_SOURCE_PIXELS = 40_000_000L

    fun decodeSampled(file: File, maxDimension: Int = 2048): Bitmap? {
        if (!file.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width !in 1..MAX_SOURCE_DIMENSION || height !in 1..MAX_SOURCE_DIMENSION ||
            width.toLong() * height.toLong() > MAX_SOURCE_PIXELS) return null
        val target = maxDimension.coerceIn(1, MAX_SOURCE_DIMENSION)
        var sample = 1
        while (maxOf(width / sample, height / sample) > target) sample *= 2
        return BitmapFactory.decodeFile(
            file.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample }
        )
    }

    fun validate(file: File) {
        val bitmap = decodeSampled(file) ?: throw IllegalArgumentException("Image is invalid or exceeds safe dimensions.")
        bitmap.recycle()
    }
}
