package com.aicontrol.launcher.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSpecTest {
    @Test
    fun defaultThemeUsesTheNewRestrainedLauncherPalette() {
        val values = ThemeSpec.builtIn("default")!!
        assertEquals("#101319", values.background)
        assertEquals("#BBC8FF", values.accent)
        assertEquals("#A9DCCE", values.accent2)
        assertEquals("#1B1F27", values.card)
        assertEquals("solid", values.backgroundStyle)
    }

    @Test
    fun everyBuiltInThemeHasACompleteValidatedBundle() {
        ThemeSpec.builtInNames.forEach { name ->
            val values = ThemeSpec.builtIn(name)
            assertNotNull("Missing built-in preset $name", values)
            assertEquals(name, values!!.name)
            assertTrue(values.background.startsWith("#"))
            assertTrue(values.accent.startsWith("#"))
            assertTrue(values.typography in ThemeSpec.typographies)
            assertTrue(values.iconStyle in ThemeSpec.iconStyles)
            assertTrue(values.backgroundStyle in ThemeSpec.backgroundStyles)
            assertTrue(values.layout in ThemeSpec.layouts)
            assertNull(values.wallpaperAsset)
        }
    }

    @Test
    fun spiderInspiredThemeUsesAnAbstractPaletteAndWallpaperSearchPhrase() {
        val values = ThemeSpec.suggestedPalette("Spider-Man")
        assertEquals("#E62429", values.accent)
        assertEquals("dense", values.layout)
        assertTrue(ThemeSpec.suggestedWallpaperQuery(values.name).contains("abstract"))
        assertTrue(ThemeSpec.suggestedWallpaperQuery(values.name).contains("geometric"))
    }

    @Test
    fun acceptsACompleteThemeBundle() {
        val values = ThemeSpec.validate(
            "Night", layout = "wide", wallpaperAsset = "wallpaper_1.webp",
            iconAssets = mapOf("com.example.mail" to "mail.png"), iconPackPackage = "com.example.icons"
        )
        assertEquals("wide", values.layout)
        assertEquals("wallpaper_1.webp", values.wallpaperAsset)
        assertEquals("mail.png", values.iconAssets["com.example.mail"])
        assertEquals("com.example.icons", values.iconPackPackage)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedTypography() {
        ThemeSpec.validate("Custom", typography = "comic")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedIconStyle() {
        ThemeSpec.validate("Custom", iconStyle = "character-art")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedBackgroundStyle() {
        ThemeSpec.validate("Custom", backgroundStyle = "download-remote-image")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedLayout() {
        ThemeSpec.validate("Custom", layout = "arbitrary-grid")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsAssetPaths() {
        ThemeSpec.validate("Custom", wallpaperAsset = "../private.jpg")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMalformedAppMappings() {
        ThemeSpec.validate("Custom", iconAssets = mapOf("../../app" to "icon.png"))
    }
}
