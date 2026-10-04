package com.aicontrol.launcher.icons

import com.aicontrol.launcher.theme.ThemeConfigCodec
import com.aicontrol.launcher.theme.ThemeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IconPackThemeIntegrationTest {
    @Test
    fun discoversOfficialNovaAdwApexAndLawnchairFilters() {
        assertTrue(IconPackCompatibility.matchesFilter("com.novalauncher.THEME"))
        assertTrue(IconPackCompatibility.matchesFilter("org.adw.launcher.THEMES"))
        assertTrue(IconPackCompatibility.matchesFilter("org.adw.ActivityStarter.THEMES"))
        assertTrue(IconPackCompatibility.matchesFilter("android.intent.action.MAIN", setOf("com.anddoes.launcher.THEME")))
        assertTrue(IconPackCompatibility.matchesFilter("ch.deletescape.lawnchair.ICONPACK", setOf("ch.deletescape.lawnchair.PICK_ICON")))
        assertFalse(IconPackCompatibility.matchesFilter("android.intent.action.MAIN", setOf("android.intent.category.LAUNCHER")))
        assertEquals("dev.appstract.iconpack", IconPackCompatibility.APPSTRACT_PACKAGE)
    }

    @Test
    fun resolvesAppstractComponentInfoAndShortActivityNames() {
        val mapping = IconPackMappingResolver.index(
            listOf(
                IconPackMappingEntry(
                    "ComponentInfo{com.android.chrome/com.google.android.apps.chrome.Main}",
                    "chrome_icon"
                ),
                IconPackMappingEntry("com.google.android.apps.maps/.MapsActivity", "maps_icon")
            )
        )

        assertEquals("chrome_icon", IconPackMappingResolver.drawableFor(
            mapping, "com.android.chrome", "com.google.android.apps.chrome.Main"
        ))
        assertEquals("maps_icon", IconPackMappingResolver.drawableFor(
            mapping, "com.google.android.apps.maps", "com.google.android.apps.maps.MapsActivity"
        ))
        assertEquals("chrome_icon", IconPackMappingResolver.drawableFor(
            mapping, "com.android.chrome", "com.android.chrome.UnlistedActivity"
        ))
    }

    @Test
    fun selectionKeepsCurrentThenPrefersAppstractThenStablePackageOrder() {
        assertEquals(
            "org.example.current",
            ThemeIconPackPolicy.preferredPackage(
                listOf("dev.appstract.iconpack", "org.example.current"),
                "org.example.current"
            )
        )
        assertEquals(
            IconPackCompatibility.APPSTRACT_PACKAGE,
            ThemeIconPackPolicy.preferredPackage(listOf("org.example.other", IconPackCompatibility.APPSTRACT_PACKAGE))
        )
        assertEquals("org.example.alpha", ThemeIconPackPolicy.preferredPackage(listOf("org.example.zeta", "org.example.alpha")))
        assertNull(ThemeIconPackPolicy.preferredPackage(emptyList()))
    }

    @Test
    fun themeApplicationRequiresSelectedPackToBeCurrentlyInstalled() {
        assertEquals(
            "dev.appstract.iconpack",
            ThemeIconPackPolicy.requireInstalled("dev.appstract.iconpack", setOf("dev.appstract.iconpack"))
        )
        assertNull(ThemeIconPackPolicy.requireInstalled(null, emptySet()))
        val error = runCatching {
            ThemeIconPackPolicy.requireInstalled("dev.appstract.iconpack", emptySet())
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("not installed"))
    }

    @Test
    fun savedThemeJsonRoundTripsRealInstalledPackPackageIdentity() {
        val original = ThemeSpec.validate(
            name = "Appstract Theme",
            wallpaperAsset = "cc0-blue-geometry.jpg",
            iconPackPackage = "dev.appstract.iconpack"
        )

        val encoded = ThemeConfigCodec.encode(original)
        val restored = ThemeConfigCodec.decode(encoded, "fallback name")

        assertEquals("dev.appstract.iconpack", encoded.getString("iconPackPackage"))
        assertEquals(original.name, restored.name)
        assertEquals(original.wallpaperAsset, restored.wallpaperAsset)
        assertEquals(original.iconPackPackage, restored.iconPackPackage)
    }

    @Test
    fun missingPackIdentityRemainsDistinctFromOptionalOpenMojiArtwork() {
        val original = ThemeSpec.validate(
            name = "Palette",
            wallpaperAsset = null,
            iconAssets = mapOf("com.example.app" to "openmoji_1f4f1.png")
        )
        val restored = ThemeConfigCodec.decode(ThemeConfigCodec.encode(original), "fallback")

        assertNull(restored.iconPackPackage)
        assertEquals("openmoji_1f4f1.png", restored.iconAssets["com.example.app"])
    }
}
