package com.aicontrol.launcher.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeWallpaperSelectorTest {
    private fun candidate(title: String, url: String, license: String = "CC0", width: Int = 1080, height: Int = 1920) =
        WallpaperCandidate(title, url, "https://commons.wikimedia.org/wiki/File:$title", "Artist", license, "https://creativecommons.org/", width, height)

    @Test
    fun choosesRelevantDimensionedReusableCommonsImage() {
        val generic = candidate("Abstract Wallpaper", "https://thumb.wikimedia.org/wikipedia/commons/thumb/a/ab/generic.jpg/1200px-generic.jpg")
        val matching = candidate("Red Blue Geometric Pattern", "https://upload.wikimedia.org/wikipedia/commons/a/ab/red-blue.jpg", "CC BY 4.0")
        val chosen = ThemeWallpaperSelector.choose(listOf(generic, matching), "abstract red blue geometric web wallpaper")
        assertNotNull(chosen)
        assertEquals(matching.title, chosen?.title)
    }

    @Test
    fun rejectsNonReusableArtworkProtectedCharacterTitlesUnsafeHostsAndUnsuitableImages() {
        val candidates = listOf(
            candidate("Red Blue CC BY-NC wallpaper", "https://upload.wikimedia.org/wikipedia/commons/a/a1/nc.jpg", "CC BY-NC 4.0"),
            candidate("Spider-Man fan art wallpaper", "https://upload.wikimedia.org/wikipedia/commons/a/a1/hero.jpg", "CC0"),
            candidate("Red blue wallpaper", "https://example.org/wikipedia/commons/a/a1/unsafe.jpg", "CC0"),
            candidate("Red blue panorama wallpaper", "https://upload.wikimedia.org/wikipedia/commons/a/a1/panorama.jpg", "CC0", 30_000, 3_000)
        )
        assertNull(ThemeWallpaperSelector.choose(candidates, "abstract red blue wallpaper"))
    }

    @Test
    fun fallbackQueriesBroadenColorMotifsWithoutReusingCharacterNames() {
        val variants = commonsSearchQueryVariants("abstract red blue geometric web pattern wallpaper")
        assertEquals(4, variants.size)
        assertTrue(variants.contains("abstract wallpaper"))
        assertTrue(variants.any { it.contains("red") && it.contains("blue") })
        AssetSearchPolicy.validate(variants.joinToString(" "))
    }
}
