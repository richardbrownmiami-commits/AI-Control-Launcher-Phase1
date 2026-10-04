package com.aicontrol.launcher.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMojiIconMatcherTest {
    private val catalog = listOf(
        OpenMojiGlyph("1F4F7", "camera", "photo picture", "camera"),
        OpenMojiGlyph("1F4E7", "e-mail", "mail", "envelope"),
        OpenMojiGlyph("1F310", "globe with meridians", "internet web", "web"),
        OpenMojiGlyph("1F578", "spider web", "web pattern", "spider web"),
        OpenMojiGlyph("2699", "gear", "settings", "gear")
    )

    @Test
    fun mapsOnlyRecognizedInstalledAppCategoriesAndNeverUploadsTheirNames() {
        val result = OpenMojiIconMatcher.assign(
            catalog,
            listOf("org.example.camera" to "Camera", "org.example.mail" to "Email", "org.example.unknown" to "Mystery App"),
            "Midnight"
        )
        assertEquals(2, result.size)
        assertEquals("1F4F7", result.first { it.packageName == "org.example.camera" }.glyph.code)
        assertEquals("1F4E7", result.first { it.packageName == "org.example.mail" }.glyph.code)
        assertFalse(result.any { it.packageName == "org.example.unknown" })
    }

    @Test
    fun aSpiderInspiredThemeChoosesAbstractWebGlyphForWebApps() {
        val result = OpenMojiIconMatcher.assign(catalog, listOf("org.example.browser" to "Web Browser"), "Spider-Man")
        assertEquals("1F578", result.single().glyph.code)
        assertTrue(result.single().glyph.annotation.contains("web", ignoreCase = true))
    }

    @Test
    fun doesNotUseSkinToneVariantRecords() {
        val result = OpenMojiIconMatcher.assign(
            catalog + OpenMojiGlyph("1F44B-1F3FD", "waving hand", "hand", "wave"),
            listOf("org.example.camera" to "Camera"), "Theme"
        )
        assertEquals("1F4F7", result.single().glyph.code)
    }

    @Test
    fun bundledCatalogAutomaticallyCoversCommonLauncherLabelsOffline() {
        val labels = listOf("Phone", "Messages", "Camera", "Calendar", "Clock", "Gmail", "Chrome", "Maps",
            "Contacts", "Settings", "Files", "Music", "Play Store", "Weather", "Calculator", "Notes")
        val mapped = OpenMojiIconMatcher.assign(
            BundledOpenMojiCatalog.glyphs,
            labels.mapIndexed { index, label -> "org.example.app$index" to label },
            "Ocean"
        )
        assertEquals(labels.size, mapped.size)
        assertEquals("1F4E7", mapped.first { it.appLabel == "Gmail" }.glyph.code)
        assertEquals("2699", mapped.first { it.appLabel == "Settings" }.glyph.code)
        assertTrue(mapped.all { it.glyph in BundledOpenMojiCatalog.glyphs })
    }
}
