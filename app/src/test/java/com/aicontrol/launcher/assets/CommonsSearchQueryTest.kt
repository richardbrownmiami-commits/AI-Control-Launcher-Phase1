package com.aicontrol.launcher.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonsSearchQueryTest {
    @Test
    fun keepsExactWallpaperQueryFirstAndThenUsesRelevantAdjacentPairs() {
        val original = "abstract ocean blue gradient wallpaper"
        val variants = commonsSearchQueryVariants(original)
        assertEquals(original, variants.first())
        assertTrue(variants.contains("abstract ocean"))
        assertTrue(variants.contains("ocean blue"))
        assertEquals("abstract wallpaper", variants.last())
        assertTrue(variants.size <= 5)
    }

    @Test
    fun doesNotReplaceShortSpecificSearchesWithAnUnrelatedGenericQuery() {
        assertEquals(listOf("blue wallpaper"), commonsSearchQueryVariants("blue wallpaper"))
    }
}
