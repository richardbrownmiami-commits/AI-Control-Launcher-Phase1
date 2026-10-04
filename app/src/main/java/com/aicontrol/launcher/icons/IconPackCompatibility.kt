package com.aicontrol.launcher.icons

/** Intent filters used by real Android icon-pack apps and common Nova-compatible pack dashboards. */
internal data class IconPackIntentQuery(val action: String, val category: String? = null)

internal object IconPackCompatibility {
    const val APPSTRACT_PACKAGE = "dev.appstract.iconpack"
    const val APPSTRACT_FDROID_URL = "https://f-droid.org/en/packages/dev.appstract.iconpack/"
    const val APPSTRACT_SOURCE_URL = "https://github.com/yangchoo/Appstract"
    const val APPSTRACT_LICENSE_URL = "https://www.apache.org/licenses/LICENSE-2.0"

    val queries = listOf(
        IconPackIntentQuery("com.novalauncher.THEME"),
        IconPackIntentQuery("org.adw.launcher.THEMES"),
        IconPackIntentQuery("org.adw.ActivityStarter.THEMES"),
        IconPackIntentQuery("android.intent.action.MAIN", "com.anddoes.launcher.THEME"),
        IconPackIntentQuery("ch.deletescape.lawnchair.ICONPACK", "ch.deletescape.lawnchair.PICK_ICON")
    )

    fun matchesFilter(action: String, categories: Set<String> = emptySet()): Boolean = queries.any { query ->
        query.action == action && (query.category == null || query.category in categories)
    }
}

internal data class IconPackMappingEntry(val component: String, val drawable: String)

/** Parses the ComponentInfo form used by Appstract and resolves exact-activity then package mappings. */
internal object IconPackMappingResolver {
    fun normalizeComponent(raw: String): String {
        var value = raw.trim()
        if (value.startsWith("ComponentInfo{") && value.endsWith("}")) {
            value = value.substring("ComponentInfo{".length, value.length - 1)
        }
        val slash = value.indexOf('/')
        if (slash < 0) return value
        val packageName = value.substring(0, slash)
        var activityName = value.substring(slash + 1)
        if (activityName.startsWith('.')) activityName = packageName + activityName
        return "$packageName/$activityName"
    }

    fun index(entries: Iterable<IconPackMappingEntry>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        entries.forEach { entry ->
            val drawable = entry.drawable.trim()
            if (drawable.isEmpty()) return@forEach
            val component = normalizeComponent(entry.component)
            if (component.isEmpty()) return@forEach
            result[component] = drawable
            val packageName = component.substringBefore('/')
            if (packageName.contains('.') && !packageName.startsWith(':')) {
                result.putIfAbsent(packageName, drawable)
            }
        }
        return result
    }

    fun drawableFor(index: Map<String, String>, packageName: String, activityName: String): String? =
        index[normalizeComponent("$packageName/$activityName")] ?: index[packageName]
}

/** Shared policy for default choice and strict preflight before a saved theme activates a pack. */
internal object ThemeIconPackPolicy {
    fun preferredPackage(installedPackages: Collection<String>, currentPackage: String? = null): String? {
        val installed = installedPackages.toSet()
        return currentPackage?.takeIf { it in installed }
            ?: IconPackCompatibility.APPSTRACT_PACKAGE.takeIf { it in installed }
            ?: installed.sorted().firstOrNull()
    }

    fun requireInstalled(packageName: String?, installedPackages: Set<String>): String? {
        val requested = packageName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        require(requested in installedPackages) {
            "Saved app icon pack '$requested' is not installed or no longer compatible. Install it from F-Droid or choose an installed pack."
        }
        return requested
    }
}
