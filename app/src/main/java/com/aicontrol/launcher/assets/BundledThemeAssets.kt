package com.aicontrol.launcher.assets

import com.aicontrol.launcher.R

/** Verified Commons imagery shipped locally so a usable theme remains available offline. */
object BundledThemeAssets {
    data class Wallpaper(
        val assetName: String,
        val title: String,
        val creator: String,
        val license: String,
        val licenseUrl: String,
        val sourceUrl: String,
        val imageUrl: String,
        val width: Int,
        val height: Int,
        val resourceId: Int
    ) {
        fun attribution() = AssetAttribution(
            type = "wallpaper",
            name = assetName,
            title = title,
            creator = creator,
            license = license,
            licenseUrl = licenseUrl,
            sourceUrl = sourceUrl
        )

        fun candidate() = WallpaperCandidate(
            title = title,
            imageUrl = imageUrl,
            pageUrl = sourceUrl,
            creator = creator,
            license = license,
            licenseUrl = licenseUrl,
            width = width,
            height = height
        )
    }

    private const val CC0_URL = "https://creativecommons.org/publicdomain/zero/1.0/deed.en"

    val wallpapers = listOf(
        Wallpaper(
            assetName = "commons_abstract_blue.jpg",
            title = "Abstract modern painting in contrasting colour shapes · no. 5.088",
            creator = "Fons Heijnsbroek",
            license = "CC0",
            licenseUrl = CC0_URL,
            sourceUrl = "https://commons.wikimedia.org/wiki/File:Abstract_modern_painting_art_in_contrasting_color_shapes_on_canvas_with_oil_paint_-_title,_%27no._5.088%27_-_painted_in_1999_by_artist_Fons_Heijnsbroek_-_free_download_art_image_in_high_resolution.jpg",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/2/20/Abstract_modern_painting_art_in_contrasting_color_shapes_on_canvas_with_oil_paint_-_title%2C_%27no._5.088%27_-_painted_in_1999_by_artist_Fons_Heijnsbroek_-_free_download_art_image_in_high_resolution.jpg",
            width = 960,
            height = 1152,
            resourceId = R.drawable.commons_abstract_blue
        ),
        Wallpaper(
            assetName = "commons_abstract_warm.jpg",
            title = "Abstract modern collage in orange and blue · no. 5.097",
            creator = "Fons Heijnsbroek",
            license = "CC0",
            licenseUrl = CC0_URL,
            sourceUrl = "https://commons.wikimedia.org/wiki/File:Abstract_modern_collage_art_in_strong_contrasting_shapes_in_orange_and_blue_colors_-_on_canvas_and_oil_paint_-_title,_%27no._5.097%27_-_painted_in_1999_by_Dutch_artist_Fons_Heijnsbroek_-_free_download_art_image_in_high_resolution.tif",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/b/bb/Abstract_modern_collage_art_in_strong_contrasting_shapes_in_orange_and_blue_colors_-_on_canvas_and_oil_paint_-_title%2C_%27no._5.097%27_-_painted_in_1999_by_Dutch_artist_Fons_Heijnsbroek_-_free_download_art_image_in_high_resolution.tif",
            width = 960,
            height = 1280,
            resourceId = R.drawable.commons_abstract_warm
        ),
        Wallpaper(
            assetName = "commons_abstract_sage.jpg",
            title = "Abstract modern collage in contrasting colour shapes · no. 5.103",
            creator = "Fons Heijnsbroek",
            license = "CC0",
            licenseUrl = CC0_URL,
            sourceUrl = "https://commons.wikimedia.org/wiki/File:Abstract_modern_collage_art_in_composition_of_contrasting_color_shapes,_created_with_oil_paint_on_canvas._Title_%27no._5.103%27_-_painted_in_2000_by_Dutch_artist_Fons_Heijnsbroek_-_free_download_art_image_CC0_in_high_resolution_TIFF.tif",
            imageUrl = "https://upload.wikimedia.org/wikipedia/commons/2/28/Abstract_modern_collage_art_in_composition_of_contrasting_color_shapes%2C_created_with_oil_paint_on_canvas._Title_%27no._5.103%27_-_painted_in_2000_by_Dutch_artist_Fons_Heijnsbroek_-_free_download_art_image_CC0_in_high_resolution_TIFF.tif",
            width = 960,
            height = 1109,
            resourceId = R.drawable.commons_abstract_sage
        )
    )

    fun forTheme(name: String): Wallpaper = when {
        name.contains("ember", ignoreCase = true) || name.contains("sunset", ignoreCase = true) -> wallpapers[1]
        name.contains("sage", ignoreCase = true) || name.contains("paper", ignoreCase = true) -> wallpapers[2]
        else -> wallpapers[0]
    }
}
