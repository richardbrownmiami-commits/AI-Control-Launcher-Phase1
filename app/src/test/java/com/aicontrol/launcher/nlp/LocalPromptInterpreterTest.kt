package com.aicontrol.launcher.nlp

import com.aicontrol.launcher.theme.ThemeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPromptInterpreterTest {
    private val apps = listOf(
        AppTarget("Calculator", "com.example.calculator"),
        AppTarget("Camera", "com.example.camera"),
        AppTarget("Maps Lite", "com.example.maps.lite"),
        AppTarget("Maps Pro", "com.example.maps.pro")
    )
    private val themes = setOf("default", "midnight", "ocean", "ember", "Sunset")

    @Test fun parsesNaturalThemeRequestAndNamedColors() {
        val result = LocalPromptInterpreter.interpret(
            "Create a theme called Ocean with blue background and cyan accent",
            apps,
            themes
        )
        assertTrue(result is PromptInterpretation.Ready)
        val command = (result as PromptInterpretation.Ready).command as LauncherCommand.CreateTheme
        assertEquals("Ocean", command.values.name)
        assertEquals("#174EA6", command.values.background)
        assertEquals("#46D2FF", command.values.accent)
    }

    @Test fun createsNamedThemeFromShortConversationalRequest() {
        val result = LocalPromptInterpreter.interpret("Make a Spider-Man theme", apps, themes)
        assertTrue(result is PromptInterpretation.Ready)
        val values = (result as PromptInterpretation.Ready).command.let { (it as LauncherCommand.CreateTheme).values }
        assertEquals("Spider-Inspired", values.name)
        assertEquals("#E62429", values.accent)
        assertEquals("#1E5AA8", values.accent2)
        assertEquals("neon", values.style)
    }

    @Test fun offlineFallbackStillCreatesAThemeWithoutAnyProvider() {
        val result = LocalPromptInterpreter.interpret("Make a Coral reef theme", apps, themes)
        assertTrue(result is PromptInterpretation.Ready)
        val values = ((result as PromptInterpretation.Ready).command as LauncherCommand.CreateTheme).values
        assertEquals("Coral reef", values.name)
        assertEquals(ThemeSpec.DEFAULT_ACCENT, values.accent)
    }

    @Test fun explicitColorsOnlyRequestDoesNotAttachWallpaperOffline() {
        val result = LocalPromptInterpreter.interpret("Make a theme called Ocean, colors only", apps, themes)
        assertTrue(result is PromptInterpretation.Ready)
        val values = ((result as PromptInterpretation.Ready).command as LauncherCommand.CreateTheme).values
        assertEquals("Ocean", values.name)
        assertNull(values.wallpaperAsset)
    }

    @Test fun resolvesPresetThemeWithoutCreatingAFile() {
        val result = LocalPromptInterpreter.interpret("Make a midnight theme", apps, themes)
        assertTrue(result is PromptInterpretation.Ready)
        assertEquals(LauncherCommand.ApplyTheme("midnight"), (result as PromptInterpretation.Ready).command)
    }

    @Test fun resolvesCustomThemeBeforeCaseInsensitiveBuiltInCollision() {
        val available = linkedSetOf("Ocean", "ocean", "default")
        val result = LocalPromptInterpreter.interpret("set theme to ocean", apps, available)
        assertEquals(LauncherCommand.ApplyTheme("Ocean"), (result as PromptInterpretation.Ready).command)
    }

    @Test fun parsesLayoutChange() {
        val result = LocalPromptInterpreter.interpret("set layout to dense", apps, themes)
        assertEquals(LauncherCommand.SetLayout("dense"), (result as PromptInterpretation.Ready).command)
    }

    @Test fun parsesCardStyleChange() {
        val result = LocalPromptInterpreter.interpret("set card style to neon", apps, themes)
        assertEquals(LauncherCommand.SetStyle("neon"), (result as PromptInterpretation.Ready).command)
    }

    @Test fun hidesOnlyAnUnambiguousResolvedApp() {
        val result = LocalPromptInterpreter.interpret("hide Calculator", apps, themes)
        assertTrue(result is PromptInterpretation.Ready)
        val ready = result as PromptInterpretation.Ready
        val command = ready.command as LauncherCommand.SetAppVisible
        assertEquals("com.example.calculator", command.app.packageName)
        assertEquals(false, command.visible)
        assertTrue(ready.preview.contains(command.app.packageName))
    }

    @Test fun resolvesAnExplicitAppLaunch() {
        val result = LocalPromptInterpreter.interpret("open Camera", apps, themes)
        assertEquals(LauncherCommand.LaunchApp(AppTarget("Camera", "com.example.camera")),
            (result as PromptInterpretation.Ready).command)
    }

    @Test fun asksForMoreDetailWhenAppNameIsAmbiguous() {
        val result = LocalPromptInterpreter.interpret("hide Maps", apps, themes)
        assertTrue(result is PromptInterpretation.Clarification)
        assertTrue((result as PromptInterpretation.Clarification).message.contains("Maps Lite"))
    }

    @Test fun validatesPersistedThemeValues() {
        val values = ThemeSpec.validate("Sunset", "dark blue", "cyan", style = "neon")
        assertEquals("#081A2E", values.background)
        assertEquals("#46D2FF", values.accent)
        assertEquals("neon", values.style)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedThemeColor() {
        ThemeSpec.normalizeColor("chartreuse-ish")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedCardStyle() {
        ThemeSpec.validate("Sunset", style = "arbitrary-style")
    }
}
