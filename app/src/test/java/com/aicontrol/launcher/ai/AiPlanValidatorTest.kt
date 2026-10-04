package com.aicontrol.launcher.ai

import com.aicontrol.launcher.theme.ThemeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPlanValidatorTest {
    private val themes = setOf("default", "ocean", "Midnight")

    @Test
    fun validatesThemeAndWhitelistedAssetWidgetActions() {
        val response = """{
            "message":"Here is a blue palette and a wallpaper search.",
            "clarification":null,
            "theme":{"operation":"CREATE","name":"Ocean night","background":"#061827","accent":"cyan","accent2":"#9B5CFF","card":"#141723","style":"neon"},
            "actions":[{"type":"SEARCH_ASSETS","query":"abstract ocean blue wallpaper"},{"type":"SET_LAYOUT","value":"dense"}]
        }"""
        val decision = AiPlanValidator.parse(response, themes)
        assertTrue(decision is AiPlanDecision.Review)
        val plan = (decision as AiPlanDecision.Review).plan
        val created = plan.theme as AiThemeOperation.Create
        assertEquals("Ocean night", created.values.name)
        assertEquals("#46D2FF", created.values.accent)
        assertEquals(2, plan.actions.size)
        assertEquals("dense", (plan.actions[1] as AiLauncherAction.SetLayout).value)
    }

    @Test
    fun allowsAbstractSpiderInspiredWallpaperSearch() {
        val decision = AiPlanValidator.parse(
            """{"message":"A red and blue geometric web theme","theme":{"operation":"CREATE","name":"Spider-inspired"},"actions":[{"type":"SEARCH_ASSETS","query":"abstract red blue geometric web pattern wallpaper"}]}""",
            themes
        ) as AiPlanDecision.Review
        assertTrue(decision.plan.actions.single() is AiLauncherAction.SearchAssets)
    }

    @Test
    fun copyrightedThemeNamesBecomeAccuratelyLabeledInspiredPalettes() {
        val decision = AiPlanValidator.parse(
            """{"message":"An abstract superhero palette","theme":{"operation":"CREATE","name":"Spider-Man"},"actions":[]}""",
            themes
        ) as AiPlanDecision.Review
        val created = decision.plan.theme as AiThemeOperation.Create
        assertEquals("Spider-Inspired", created.values.name)
        val search = decision.plan.actions.single() as AiLauncherAction.SearchAssets
        assertFalse(search.query.contains("Spider-Man", ignoreCase = true))
        assertTrue(search.query.contains("abstract", ignoreCase = true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsSearchQueriesThatNameCharacterArt() {
        AiPlanValidator.parse(
            """{"message":"Looking for a character image","theme":null,"actions":[{"type":"SEARCH_ASSETS","query":"Spider-Man wallpaper"}]}""",
            themes
        )
    }

    @Test
    fun acceptsMarkdownAndProseWrappersAroundAValidJsonObject() {
        val decision = AiPlanValidator.parse(
            "Here is the reviewed plan:\n```json\n{\"message\":\"A calm theme is ready\",\"theme\":{\"operation\":\"CREATE\",\"name\":\"Calm blue\"},\"actions\":[]}\n```\n",
            themes
        ) as AiPlanDecision.Review
        assertEquals("Calm blue", (decision.plan.theme as AiThemeOperation.Create).values.name)
    }

    @Test
    fun acceptsStringifiedJsonForKnownTypedFieldsWithoutExpandingTheAllowlist() {
        val decision = AiPlanValidator.parse(
            """{"message":"Searching a licensed wallpaper","theme":"{\"operation\":\"CREATE\",\"name\":\"Soft dusk\"}","actions":"[{\"type\":\"SEARCH_ASSETS\",\"query\":\"soft dusk abstract wallpaper\"}]"}""",
            themes
        ) as AiPlanDecision.Review
        assertEquals("Soft dusk", (decision.plan.theme as AiThemeOperation.Create).values.name)
        assertTrue(decision.plan.actions.single() is AiLauncherAction.SearchAssets)
    }

    @Test
    fun unwrapsSerializedResponseAndProviderContentEnvelopes() {
        val serialized = """{"response":"{\"message\":\"A preset is ready\",\"theme\":{\"operation\":\"APPLY\",\"name\":\"ocean\"},\"actions\":[]}"}"""
        val first = AiPlanValidator.parse(serialized, themes) as AiPlanDecision.Review
        assertEquals("ocean", (first.plan.theme as AiThemeOperation.Apply).name)

        val contentEnvelope = """{"choices":[{"message":{"content":"The plan:\n```json\n{\"message\":\"Wallpaper search\",\"theme\":null,\"actions\":[{\"type\":\"SEARCH_ASSETS\",\"query\":\"soft blue abstract wallpaper\"}]}\n```"}}]}"""
        val second = AiPlanValidator.parse(contentEnvelope, themes) as AiPlanDecision.Review
        assertEquals("Wallpaper search", second.plan.response)
        assertTrue(second.plan.actions.single() is AiLauncherAction.SearchAssets)
    }

    @Test
    fun appliesOnlyAnExactInstalledCompatibleIconPack() {
        val decision = AiPlanValidator.parse(
            """{"message":"Applying your icons","theme":null,"actions":[{"type":"APPLY_ICON_PACK","name":"Mono Minimal"}]}""",
            themes,
            mapOf("Mono Minimal" to "org.example.monominimal")
        ) as AiPlanDecision.Review
        val action = decision.plan.actions.single() as AiLauncherAction.ApplyInstalledIconPack
        assertEquals("Mono Minimal", action.label)
        assertEquals("org.example.monominimal", action.packageName)
    }

    @Test
    fun iconPackStoreActionIsFixedAndHasNoCallerControlledUrl() {
        val decision = AiPlanValidator.parse(
            """{"message":"Find compatible packs","theme":null,"actions":[{"type":"BROWSE_ICON_PACKS"}]}""",
            themes
        ) as AiPlanDecision.Review
        assertTrue(decision.plan.actions.single() === AiLauncherAction.BrowseIconPacks)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsIconPackPackageNotPresentInInstalledChoices() {
        AiPlanValidator.parse(
            """{"message":"Apply this pack","theme":null,"actions":[{"type":"APPLY_ICON_PACK","name":"unknown","package":"org.attacker.pack"}]}""",
            themes,
            mapOf("Mono Minimal" to "org.example.monominimal")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun wrapperToleranceDoesNotPermitUnsupportedActions() {
        AiPlanValidator.parse(
            "Response: ```json\n{\"message\":\"Run code\",\"actions\":[{\"type\":\"EXECUTE_CODE\",\"code\":\"anything\"}]}\n```",
            themes
        )
    }

    @Test
    fun validatesPresetDisplayAttributesForAiCreatedThemes() {
        val decision = AiPlanValidator.parse(
            """{"message":"A calm preset","theme":{"operation":"CREATE","name":"Sage AI","typography":"serif","iconStyle":"squircle","backgroundStyle":"aurora","layout":"wide","iconPack":"Mono Minimal"},"actions":[]}""",
            themes,
            mapOf("Mono Minimal" to "org.example.monominimal")
        ) as AiPlanDecision.Review
        val values = (decision.plan.theme as AiThemeOperation.Create).values
        assertEquals("serif", values.typography)
        assertEquals("squircle", values.iconStyle)
        assertEquals("aurora", values.backgroundStyle)
        assertEquals("wide", values.layout)
        assertEquals("org.example.monominimal", values.iconPackPackage)
    }

    @Test
    fun createThemeWithoutAssetActionGetsAConservativeBundleSearch() {
        val decision = AiPlanValidator.parse(
            """{"message":"A complete ocean theme","theme":{"operation":"CREATE","name":"Ocean night"},"actions":[]}""",
            themes
        ) as AiPlanDecision.Review
        val created = decision.plan.theme as AiThemeOperation.Create
        assertEquals("commons_abstract_blue.jpg", created.values.wallpaperAsset)
        val search = decision.plan.actions.single() as AiLauncherAction.SearchAssets
        assertTrue(search.query.contains("abstract"))
    }

    @Test
    fun generatedThemeDefaultsToInstalledRealAppIconPackWhenAvailable() {
        val decision = AiPlanValidator.parse(
            """{"message":"A complete theme","theme":{"operation":"CREATE","name":"Blue glass"},"actions":[]}""",
            themes,
            mapOf("Other pack" to "org.example.other", "Appstract" to "dev.appstract.iconpack")
        ) as AiPlanDecision.Review
        val created = decision.plan.theme as AiThemeOperation.Create
        assertEquals("dev.appstract.iconpack", created.values.iconPackPackage)
    }

    @Test
    fun explicitColorsOnlyRequestHasNoWallpaperOrSearchSideEffect() {
        val decision = AiPlanValidator.parse(
            """{"message":"Palette only","theme":{"operation":"CREATE","name":"Soft dusk"},"actions":[]}""",
            themes,
            availableIconPacks = mapOf("Appstract" to "dev.appstract.iconpack"),
            promptContext = "Make a palette only, colors only, no wallpaper"
        ) as AiPlanDecision.Review
        val created = decision.plan.theme as AiThemeOperation.Create
        assertNull(created.values.wallpaperAsset)
        assertNull(created.values.iconPackPackage)
        assertTrue(decision.plan.actions.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsThemeIconPackUnlessItIsInstalledAndCompatible() {
        AiPlanValidator.parse(
            """{"message":"A theme","theme":{"operation":"CREATE","name":"Untrusted","iconPack":"Not installed"},"actions":[]}""",
            themes
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnvalidatedThemeDisplayAttributes() {
        AiPlanValidator.parse(
            """{"message":"Invalid","theme":{"operation":"CREATE","name":"Unsafe","iconStyle":"remote"},"actions":[]}""",
            themes
        )
    }

    @Test
    fun returnsClarificationWithoutAnyActionPlan() {
        val decision = AiPlanValidator.parse(
            """{"message":"I can do that.","clarification":"Which palette name should I use?","theme":null,"actions":[]}""",
            themes
        )
        assertTrue(decision is AiPlanDecision.Clarification)
        assertEquals("Which palette name should I use?", (decision as AiPlanDecision.Clarification).question)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedDownloadToolAndUrl() {
        AiPlanValidator.parse(
            """{"message":"Downloading now","theme":null,"actions":[{"type":"DOWNLOAD_WALLPAPER","url":"https://example.invalid/image.jpg"}]}""",
            themes
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUrlOrUnexpectedFieldsOnAllowedSearchAction() {
        AiPlanValidator.parse(
            """{"message":"Search","theme":null,"actions":[{"type":"SEARCH_ASSETS","query":"abstract blue wallpaper","url":"https://example.invalid/image.jpg"}]}""",
            themes
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnlistedThemeAndUnsafeThemeFields() {
        AiPlanValidator.parse(
            """{"message":"Apply it","theme":{"operation":"APPLY","name":"not installed","code":"run()"},"actions":[]}""",
            themes
        )
    }

    @Test
    fun confirmationGateDoesNotReleaseAPlanUntilConfirmAndIsSingleUse() {
        val plan = AiLauncherPlan(
            "Ready for review",
            AiThemeOperation.Create(ThemeSpec.validate("Ocean")),
            listOf(AiLauncherAction.AddWidget)
        )
        val gate = AiPlanConfirmationGate()
        gate.stage(plan)
        assertTrue(gate.hasPending())
        assertEquals(plan, gate.confirm())
        assertFalse(gate.hasPending())
        assertNull(gate.confirm())

        gate.stage(plan)
        gate.cancel()
        assertNull(gate.confirm())
    }
}
