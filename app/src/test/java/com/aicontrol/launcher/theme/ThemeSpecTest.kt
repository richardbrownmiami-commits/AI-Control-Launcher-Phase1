package com.aicontrol.launcher.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeSpecTest {
    @Test
    fun everyBuiltInThemeHasACompleteValidatedPalette() {
        ThemeSpec.builtInNames.forEach { name ->
            val values = ThemeSpec.builtIn(name)
            assertNotNull("Missing built-in preset $name", values)
            assertEquals(name, values!!.name)
            assertTrue(values.background.startsWith("#"))
            assertTrue(values.accent.startsWith("#"))
            assertTrue(values.typography in ThemeSpec.typographies)
            assertTrue(values.iconStyle in ThemeSpec.iconStyles)
            assertTrue(values.backgroundStyle in ThemeSpec.backgroundStyles)
        }
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
}
