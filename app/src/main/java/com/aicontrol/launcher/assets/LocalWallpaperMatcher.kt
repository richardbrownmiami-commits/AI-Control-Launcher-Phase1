package com.aicontrol.launcher.assets

import android.graphics.Color
import java.io.File
import kotlin.math.sqrt

/** Deterministic, offline scoring for images the user explicitly selected with the document picker. */
object LocalWallpaperMatcher {
    fun paletteMatchPercent(file: File, accentHex: String, accent2Hex: String): Int {
        val bitmap = ImageAssetValidation.decodeSampled(file, 96) ?: return 0
        try {
            val accents = intArrayOf(Color.parseColor(accentHex), Color.parseColor(accent2Hex))
            val strideX = (bitmap.width / 24).coerceAtLeast(1)
            val strideY = (bitmap.height / 24).coerceAtLeast(1)
            var total = 0
            var close = 0
            var distanceTotal = 0.0
            for (y in 0 until bitmap.height step strideY) for (x in 0 until bitmap.width step strideX) {
                val pixel = bitmap.getPixel(x, y)
                val nearest = accents.minOf { target -> colorDistance(pixel, target) }
                distanceTotal += nearest
                if (nearest < 150.0) close++
                total++
            }
            if (total == 0) return 0
            val averageCloseness = (100.0 * (1.0 - (distanceTotal / total) / 442.0)).toInt().coerceIn(0, 100)
            val accentCoverage = 100 * close / total
            return ((averageCloseness * 0.65) + (accentCoverage * 0.35)).toInt().coerceIn(0, 100)
        } finally {
            bitmap.recycle()
        }
    }

    private fun colorDistance(left: Int, right: Int): Double {
        val red = Color.red(left) - Color.red(right)
        val green = Color.green(left) - Color.green(right)
        val blue = Color.blue(left) - Color.blue(right)
        return sqrt((red * red + green * green + blue * blue).toDouble())
    }
}
