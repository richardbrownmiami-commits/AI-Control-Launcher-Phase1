package com.aicontrol.launcher.nlp

import com.aicontrol.launcher.theme.ThemeSpec

/** Minimal app identity used by the local interpreter; no package metadata leaves the device. */
data class AppTarget(val label: String, val packageName: String)

sealed class LauncherCommand {
    data class ApplyTheme(val name: String) : LauncherCommand()
    data class CreateTheme(val values: ThemeSpec.Values) : LauncherCommand()
    data class SetLayout(val name: String) : LauncherCommand()
    data class SetStyle(val name: String) : LauncherCommand()
    data class SetAppVisible(val app: AppTarget, val visible: Boolean) : LauncherCommand()
    data class LaunchApp(val app: AppTarget) : LauncherCommand()
}

sealed class PromptInterpretation {
    data class Ready(val command: LauncherCommand, val preview: String) : PromptInterpretation()
    data class Clarification(val message: String) : PromptInterpretation()
    data class Unrecognized(val message: String) : PromptInterpretation()
    data class Help(val message: String) : PromptInterpretation()
}

/**
 * An intentionally small local command parser. It does not call a model, infer hidden intent,
 * execute code, or transmit the prompt/app inventory. The UI must review every Ready command.
 */
object LocalPromptInterpreter {
    private val builtInThemes = setOf("default", "midnight", "ocean", "ember")
    private val layouts = setOf("grid", "compact", "dense", "wide")

    fun interpret(
        input: String,
        apps: List<AppTarget>,
        availableThemes: Set<String>
    ): PromptInterpretation {
        val text = input.trim().trimEnd('.', '!', '?').trim()
        if (text.isBlank()) return PromptInterpretation.Unrecognized("Enter a command or ask for examples.")
        val lower = text.lowercase()
        if (lower in setOf("help", "what can you do", "commands", "show examples")) {
            return PromptInterpretation.Help(helpText())
        }

        val creatingTheme = lower.matches(Regex("^(please\\s+)?(create|make|design|build)\\b.*\\btheme\\b.*$"))
        val explicitColors = hasColorAssignment(lower)
        val explicitName = extractNamedTheme(text)
        val inferredName = inferredThemeName(text)
        if (creatingTheme && explicitName == null && !explicitColors && inferredName != null) {
            availableThemes.firstOrNull { it.equals(inferredName, ignoreCase = true) }?.let { existing ->
                return PromptInterpretation.Ready(LauncherCommand.ApplyTheme(existing), "Use the '$existing' theme.")
            }
        }
        if (creatingTheme && (explicitName != null || explicitColors || inferredName != null)) {
            return createTheme(text, explicitName)
        }

        themeTarget(text)?.let { requested ->
            val match = availableThemes.firstOrNull { it.equals(requested, ignoreCase = true) }
            return if (match == null) {
                PromptInterpretation.Clarification(
                    "I couldn't find the '$requested' theme. Available themes: ${availableThemes.sorted().joinToString()} ."
                )
            } else {
                PromptInterpretation.Ready(LauncherCommand.ApplyTheme(match), "Use the '$match' theme.")
            }
        }

        presetTheme(text)?.let { name ->
            return PromptInterpretation.Ready(LauncherCommand.ApplyTheme(name), "Use the '$name' theme.")
        }

        val layout = Regex("^(?:set|change|switch to|use)\\s+(?:the\\s+)?(?:layout\\s+(?:to\\s+)?)?([a-z]+)(?:\\s+layout)?$", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.lowercase()
        if (layout != null && layout in layouts && (lower.contains("layout") || lower.startsWith("use "))) {
            return PromptInterpretation.Ready(LauncherCommand.SetLayout(layout), "Change the app grid layout to '$layout'.")
        }

        val style = Regex("^(?:set|change|switch to|use)\\s+(?:the\\s+)?(?:card\\s+)?style\\s+(?:to\\s+)?([a-z]+)$", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.lowercase()
            ?: Regex("^(?:use|set)\\s+(glass|flat|neon)(?:\\s+cards?)?$", RegexOption.IGNORE_CASE)
                .find(text)?.groupValues?.get(1)?.lowercase()
        if (style != null) {
            return if (style in ThemeSpec.styles) {
                PromptInterpretation.Ready(LauncherCommand.SetStyle(style), "Change the card style to '$style'.")
            } else {
                PromptInterpretation.Clarification("Card style must be one of: ${ThemeSpec.styles.sorted().joinToString()}.")
            }
        }

        val appCommand = Regex("^(hide|show|unhide|restore|open|launch|start)\\s+(?:(?:the)\\s+)?(?:app\\s+)?(.+)$", RegexOption.IGNORE_CASE)
            .find(text)
        if (appCommand != null) {
            val verb = appCommand.groupValues[1].lowercase()
            val query = appCommand.groupValues[2].trim().removeSurrounding("\"", "\'")
            if (query.isBlank()) return PromptInterpretation.Clarification("Name the app you want to $verb.")
            val matches = resolveApps(query, apps)
            if (matches.size > 1) {
                return PromptInterpretation.Clarification(
                    "'$query' matches more than one app: ${matches.joinToString { "${it.label} (${it.packageName})" }}. Type a fuller app name."
                )
            }
            val app = matches.singleOrNull()
                ?: return PromptInterpretation.Clarification("I couldn't find '$query' among launchable apps. Try the exact app label.")
            return when (verb) {
                "hide" -> PromptInterpretation.Ready(LauncherCommand.SetAppVisible(app, false), "Hide '${app.label}' (${app.packageName}) from this launcher. You can restore it later with ‘show ${app.label}’.")
                "show", "unhide", "restore" -> PromptInterpretation.Ready(LauncherCommand.SetAppVisible(app, true), "Show '${app.label}' (${app.packageName}) in this launcher again.")
                else -> PromptInterpretation.Ready(LauncherCommand.LaunchApp(app), "Open '${app.label}' (${app.packageName}).")
            }
        }

        return PromptInterpretation.Unrecognized(
            "I don't recognize that command yet. Try a theme, layout, style, hide/show app, or open-app command."
        )
    }

    private fun createTheme(text: String, explicitName: String?): PromptInterpretation {
        val name = explicitName ?: inferredThemeName(text) ?: Regex(
            "^(?:please\\s+)?(?:create|make|design|build)\\s+(?:me\\s+)?(?:a\\s+)?([A-Za-z0-9][A-Za-z0-9 _.-]{0,39}?)\\s+theme(?:\\s+with\\b.*)?$",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.get(1)?.trim()
            ?: return PromptInterpretation.Clarification("Name the theme, for example: ‘Create a theme called Ocean with blue background and cyan accent.’")

        return try {
            val palette = ThemeSpec.suggestedPalette(name)
            val values = ThemeSpec.validate(
                name = name,
                background = colorAfter(text, listOf("background", "bg")) ?: palette.background,
                accent = colorAfter(text, listOf("accent")) ?: palette.accent,
                accent2 = colorAfter(text, listOf("secondary accent", "accent2")) ?: palette.accent2,
                card = colorAfter(text, listOf("card")) ?: palette.card,
                style = styleAfter(text) ?: palette.style
            )
            val preview = "Create '${values.name}' — background ${values.background}, accent ${values.accent}, secondary accent ${values.accent2}, card ${values.card}, ${values.style} style."
            PromptInterpretation.Ready(LauncherCommand.CreateTheme(values), preview)
        } catch (error: IllegalArgumentException) {
            PromptInterpretation.Clarification(error.message ?: "Please check the theme name and colors.")
        }
    }

    private fun inferredThemeName(text: String): String? = Regex(
        "^(?:please\\s+)?(?:create|make|design|build)\\s+(?:me\\s+)?(?:a\\s+)?([A-Za-z0-9][A-Za-z0-9 _.-]{0,39}?)\\s+theme(?:\\s+with\\b.*)?$",
        RegexOption.IGNORE_CASE
    ).find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }

    private fun extractNamedTheme(text: String): String? = Regex(
        "\\btheme\\s+(?:called|named)\\s+([A-Za-z0-9][A-Za-z0-9 _.-]{0,39}?)(?=\\s+(?:with|and)\\b|[,;]|$)",
        RegexOption.IGNORE_CASE
    ).find(text)?.groupValues?.get(1)?.trim()

    private fun hasColorAssignment(text: String): Boolean =
        Regex("\\b(background|bg|accent|secondary accent|accent2|card)\\b").containsMatchIn(text)

    private fun colorAfter(text: String, labels: List<String>): String? {
        val escaped = labels.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) }
        val markers = Regex("\\b(?:$escaped)\\b(?:\\s+color)?\\s*(?:to|is|=)?\\s*", RegexOption.IGNORE_CASE)
            .findAll(text)
        val marker = markers.firstOrNull {
            labels != listOf("accent") ||
                !text.substring(0, it.range.first).trimEnd().endsWith("secondary", ignoreCase = true)
        } ?: return null
        val before = text.substring(0, marker.range.first).trimEnd()
        val after = text.substring(marker.range.last + 1).trimStart()
        colorTokenAtStart(after)?.let { return it }
        colorTokenAtEnd(before)?.let { return it }
        return null
    }

    private fun colorTokenAtStart(text: String): String? {
        Regex("^#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?").find(text)?.value?.let { return it }
        for ((colorName, value) in ThemeSpec.namedColors.entries.sortedByDescending { it.key.length }) {
            if (text.startsWith(colorName, ignoreCase = true) &&
                (text.length == colorName.length || !text[colorName.length].isLetterOrDigit())) {
                return value
            }
        }
        return null
    }

    private fun colorTokenAtEnd(text: String): String? {
        Regex("#[0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?$").find(text)?.value?.let { return it }
        for ((colorName, value) in ThemeSpec.namedColors.entries.sortedByDescending { it.key.length }) {
            if (text.endsWith(colorName, ignoreCase = true)) {
                val start = text.length - colorName.length
                if (start == 0 || !text[start - 1].isLetterOrDigit()) return value
            }
        }
        return null
    }

    private fun styleAfter(text: String): String? =
        Regex("\\bstyle\\s+(?:to|is|=)?\\s*(glass|flat|neon)\\b", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.lowercase()

    private fun presetTheme(text: String): String? {
        val match = Regex(
            "^(?:(?:please\\s+)?(?:make|use|apply)\\s+(?:a\\s+)?|(?:set|change)\\s+(?:the\\s+)?theme\\s+(?:to\\s+)?|switch\\s+(?:the\\s+)?theme\\s+to\\s+)?(default|midnight|ocean|ember)(?:\\s+theme)?$",
            RegexOption.IGNORE_CASE
        ).find(text) ?: return null
        return match.groupValues[1].lowercase().takeIf { it in builtInThemes }
    }

    private fun themeTarget(text: String): String? =
        Regex("^(?:set|change|switch)\\s+(?:the\\s+)?theme\\s+(?:to\\s+)?(.+)$", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim()?.removeSuffix(" theme")
            ?: Regex("^(?:apply|use)\\s+theme\\s+(.+)$", RegexOption.IGNORE_CASE)
                .find(text)?.groupValues?.get(1)?.trim()

    private fun resolveApps(query: String, apps: List<AppTarget>): List<AppTarget> {
        val exact = apps.filter { it.label.equals(query, true) || it.packageName.equals(query, true) }
            .distinctBy { it.packageName }
        if (exact.isNotEmpty()) return exact
        return apps.filter {
            it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
        }.distinctBy { it.packageName }
    }

    private fun helpText(): String =
        "The offline launcher helper understands bounded commands; it is not a free-form AI chatbot and sends nothing to a model. Try ‘make a Spider-Man theme’, ‘create a theme called Ocean with blue background and cyan accent’, ‘set layout to dense’, ‘hide Calculator’, or ‘open Camera’. Theme requests get an editable palette preview. Choose Assets to import an image from Downloads or search reusable, attributed wallpaper. Installed Android widgets can be added from the launcher menu. Every change is reviewed before it is applied."
}
