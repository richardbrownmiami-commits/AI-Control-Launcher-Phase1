package com.aicontrol.launcher.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonsSearchQueryTest {
    @Test
    fun keepsExactWallpaperQueryFirstAndThenUsesRelevantAdjacentPairs() {
        val original = "abstract ocean blue gradient wallpaper"
        val variants = commonsSearchQueryVariants(original)
        assertEquals(listOf(original, "abstract ocean", "ocean blue"), variants)
        assertTrue(variants.size <= 3)
    }

    @Test
    fun doesNotReplaceShortSpecificSearchesWithAnUnrelatedGenericQuery() {
        assertEquals(listOf("blue wallpaper"), commonsSearchQueryVariants("blue wallpaper"))
    }
}
