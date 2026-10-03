package com.aicontrol.launcher.ai

import com.aicontrol.launcher.theme.ThemeSpec
import com.aicontrol.launcher.assets.AssetSearchPolicy
import org.json.JSONArray
import org.json.JSONObject

sealed class AiThemeOperation {
    data class Create(val values: ThemeSpec.Values) : AiThemeOperation()
    data class Apply(val name: String) : AiThemeOperation()
}

sealed class AiLauncherAction {
    data class SearchAssets(val query: String) : AiLauncherAction()
    object AddWidget : AiLauncherAction()
    data class SetLayout(val value: String) : AiLauncherAction()
    data class SetStyle(val value: String) : AiLauncherAction()
    data class ApplyInstalledIconPack(val label: String, val packageName: String) : AiLauncherAction()
    object BrowseIconPacks : AiLauncherAction()
}

data class AiLauncherPlan(
    val response: String,
    val theme: AiThemeOperation?,
    val actions: List<AiLauncherAction>
)

sealed class AiPlanDecision {
    data class Conversation(val response: String) : AiPlanDecision()
    data class Clarification(val response: String, val question: String) : AiPlanDecision()
    data class Review(val plan: AiLauncherPlan) : AiPlanDecision()
}

/**
 * Parses untrusted model text into the small set of typed operations supported by the app.
 * No model output is passed to an interpreter, shell, URL downloader, or generic action engine.
 */
object AiPlanValidator {
    private const val MAX_RESPONSE_CHARS = 16_000
    private const val MAX_MESSAGE_CHARS = 1_200
    private const val MAX_CLARIFICATION_CHARS = 500
    private val supportedLayouts = setOf("grid", "compact", "dense", "wide")

    fun parse(
        raw: String,
        availableThemes: Set<String>,
        availableIconPacks: Map<String, String> = emptyMap()
    ): AiPlanDecision {
        require(raw.length <= MAX_RESPONSE_CHARS) { "AI response is too large to review safely." }
        val root = parseResponseObject(raw)
        requireOnlyKeys(root, setOf("message", "clarification", "theme", "actions"), setOf("message"), "response")
        val message = requiredString(root, "message", MAX_MESSAGE_CHARS)
        val clarification = optionalString(root, "clarification", MAX_CLARIFICATION_CHARS)
        val themeObject = optionalObject(root, "theme")
        val actionArray = optionalArray(root, "actions")

        if (clarification != null) {
            require(themeObject == null && (actionArray == null || actionArray.length() == 0)) {
                "A clarification response cannot also request launcher changes."
            }
            return AiPlanDecision.Clarification(message, clarification)
        }

        val theme = themeObject?.let { parseTheme(it, availableThemes, availableIconPacks) }
        val actions = parseActions(actionArray, availableIconPacks)
        if (theme == null && actions.isEmpty()) return AiPlanDecision.Conversation(message)
        return AiPlanDecision.Review(AiLauncherPlan(message, theme, actions))
    }

    private fun parseTheme(
        value: JSONObject,
        availableThemes: Set<String>,
        availableIconPacks: Map<String, String>
    ): AiThemeOperation {
        val operation = requiredString(value, "operation", 20).uppercase()
        return when (operation) {
            "CREATE" -> {
                requireOnlyKeys(
                    value,
                    setOf("operation", "name", "background", "accent", "accent2", "card", "style", "typography", "iconStyle", "backgroundStyle", "layout", "iconPack"),
                    setOf("operation", "name"),
                    "theme"
                )
                val name = requiredString(value, "name", 40)
                val suggested = ThemeSpec.suggestedPalette(name)
                AiThemeOperation.Create(
                    ThemeSpec.validate(
                        name = name,
                        background = optionalString(value, "background", 20) ?: suggested.background,
                        accent = optionalString(value, "accent", 20) ?: suggested.accent,
                        accent2 = optionalString(value, "accent2", 20) ?: suggested.accent2,
                        card = optionalString(value, "card", 20) ?: suggested.card,
                        style = optionalString(value, "style", 20) ?: suggested.style,
                        typography = optionalString(value, "typography", 20) ?: suggested.typography,
                        iconStyle = optionalString(value, "iconStyle", 20) ?: suggested.iconStyle,
                        backgroundStyle = optionalString(value, "backgroundStyle", 20) ?: suggested.backgroundStyle,
                        layout = optionalString(value, "layout", 20) ?: suggested.layout,
                        iconPackPackage = optionalString(value, "iconPack", 120)?.let { requestedPack ->
                            availableIconPacks.entries.firstOrNull {
                                it.key.equals(requestedPack, ignoreCase = true) || it.value.equals(requestedPack, ignoreCase = true)
                            }?.value ?: throw IllegalArgumentException(
                                "The requested icon pack is not installed or compatible. Install it from Google Play, then try again."
                            )
                        }
                    )
                )
            }
            "APPLY" -> {
                requireOnlyKeys(value, setOf("operation", "name"), setOf("operation", "name"), "theme")
                val requested = requiredString(value, "name", 40)
                val match = availableThemes.firstOrNull { it.equals(requested, ignoreCase = true) }
                    ?: throw IllegalArgumentException("The requested theme is not in the launcher's available theme list.")
                AiThemeOperation.Apply(match)
            }
            else -> throw IllegalArgumentException("Unsupported theme operation.")
        }
    }

    private fun parseActions(actions: JSONArray?, availableIconPacks: Map<String, String>): List<AiLauncherAction> {
        if (actions == null) return emptyList()
        require(actions.length() <= 3) { "An AI plan may contain at most three supported actions." }
        val result = mutableListOf<AiLauncherAction>()
        val seen = mutableSetOf<String>()
        for (index in 0 until actions.length()) {
            val item = actions.opt(index) as? JSONObject
                ?: throw IllegalArgumentException("Each planned action must be an object.")
            val type = requiredString(item, "type", 32).uppercase()
            require(seen.add(type)) { "An AI plan cannot repeat the same action." }
            val action = when (type) {
                "SEARCH_ASSETS" -> {
                    requireOnlyKeys(item, setOf("type", "query"), setOf("type", "query"), "asset-search action")
                    val query = requiredString(item, "query", 120)
                    require(query.length in 3..120 && query.none { it.isISOControl() }) {
                        "Asset search phrases must contain 3–120 printable characters."
                    }
                    AssetSearchPolicy.validate(query)
                    AiLauncherAction.SearchAssets(query)
                }
                "ADD_WIDGET" -> {
                    requireOnlyKeys(item, setOf("type"), setOf("type"), "widget action")
                    AiLauncherAction.AddWidget
                }
                "SET_LAYOUT" -> {
                    requireOnlyKeys(item, setOf("type", "value"), setOf("type", "value"), "layout action")
                    val layout = requiredString(item, "value", 20).lowercase()
                    require(layout in supportedLayouts) { "Unsupported launcher layout." }
                    AiLauncherAction.SetLayout(layout)
                }
                "SET_STYLE" -> {
                    requireOnlyKeys(item, setOf("type", "value"), setOf("type", "value"), "style action")
                    val style = requiredString(item, "value", 20).lowercase()
                    require(style in ThemeSpec.styles) { "Unsupported card style." }
                    AiLauncherAction.SetStyle(style)
                }
                "APPLY_ICON_PACK" -> {
                    requireOnlyKeys(item, setOf("type", "name"), setOf("type", "name"), "icon-pack action")
                    val requested = requiredString(item, "name", 120)
                    val match = availableIconPacks.entries.firstOrNull { it.key.equals(requested, ignoreCase = true) }
                        ?: throw IllegalArgumentException("That icon pack is not installed or is not Nova/ADW-compatible. Install it from Google Play, then try again.")
                    AiLauncherAction.ApplyInstalledIconPack(match.key, match.value)
                }
                "BROWSE_ICON_PACKS" -> {
                    requireOnlyKeys(item, setOf("type"), setOf("type"), "icon-pack store action")
                    AiLauncherAction.BrowseIconPacks
                }
                else -> throw IllegalArgumentException("Unsupported AI action '$type'. No changes were applied.")
            }
            result += action
        }
        require(result.count {
            it is AiLauncherAction.SearchAssets || it === AiLauncherAction.AddWidget || it === AiLauncherAction.BrowseIconPacks
        } <= 1) {
            "A plan may open only one external search, store, or Android widget-picker flow at a time."
        }
        return result
    }

    private fun requiredString(value: JSONObject, key: String, maxLength: Int): String {
        val raw = value.opt(key)
        require(raw is String) { "'$key' must be a string." }
        val normalized = raw.trim()
        require(normalized.isNotEmpty() && normalized.length <= maxLength && normalized.none { it.isISOControl() }) {
            "'$key' is empty, too long, or contains unsupported control characters."
        }
        return normalized
    }

    private fun optionalString(value: JSONObject, key: String, maxLength: Int): String? {
        if (!value.has(key) || value.isNull(key)) return null
        return requiredString(value, key, maxLength)
    }

    private fun optionalObject(value: JSONObject, key: String): JSONObject? {
        if (!value.has(key) || value.isNull(key)) return null
        return when (val nested = value.opt(key)) {
            is JSONObject -> nested
            is String -> runCatching { JSONObject(nested.trim()) }.getOrElse {
                throw IllegalArgumentException("'$key' must contain a valid JSON object or null.")
            }
            else -> throw IllegalArgumentException("'$key' must be an object or null.")
        }
    }

    private fun optionalArray(value: JSONObject, key: String): JSONArray? {
        if (!value.has(key) || value.isNull(key)) return null
        return when (val nested = value.opt(key)) {
            is JSONArray -> nested
            is String -> runCatching { JSONArray(nested.trim()) }.getOrElse {
                throw IllegalArgumentException("'$key' must contain a valid JSON array or null.")
            }
            else -> throw IllegalArgumentException("'$key' must be an array or null.")
        }
    }

    /** Accept common model wrappers without relaxing the typed plan schema. */
    private fun parseResponseObject(raw: String): JSONObject {
        val text = raw.trim().removePrefix("\uFEFF").trim()
        if (text.isEmpty()) throw IllegalArgumentException("The provider returned an empty reply; no launcher changes were made.")
        findPlanObject(text, 0)?.let { return it }
        throw IllegalArgumentException(
            "Could not find a launcher plan in the provider reply. Return an object with a string 'message' and optional 'clarification', 'theme', and 'actions'. JSON fences, prose, and response/output/content/text wrappers are supported."
        )
    }

    private val wrapperKeys = listOf("response", "result", "output", "content", "text", "json", "data", "payload", "choices", "candidate")

    private fun findPlanObject(text: String, depth: Int): JSONObject? {
        if (depth > 6 || text.length > MAX_RESPONSE_CHARS) return null
        val wholeObject = runCatching { JSONObject(text) }.getOrNull()
        if (wholeObject != null) {
            if (looksLikePlan(wholeObject)) return wholeObject
            unwrap(wholeObject, depth)?.let { return it }
        }
        val wholeArray = runCatching { JSONArray(text) }.getOrNull()
        if (wholeArray != null) unwrap(wholeArray, depth)?.let { return it }

        // Search balanced JSON values embedded in prose or Markdown, respecting quoted braces.
        var index = 0
        while (index < text.length) {
            if (text[index] != '{' && text[index] != '[') {
                index++
                continue
            }
            val end = balancedJsonEnd(text, index)
            if (end == null) {
                index++
                continue
            }
            val candidate = text.substring(index, end)
            val objectValue = runCatching { JSONObject(candidate) }.getOrNull()
            if (objectValue != null) {
                if (looksLikePlan(objectValue)) return objectValue
                unwrap(objectValue, depth)?.let { return it }
            } else {
                val arrayValue = runCatching { JSONArray(candidate) }.getOrNull()
                if (arrayValue != null) unwrap(arrayValue, depth)?.let { return it }
            }
            index = end
        }

        // Some APIs return a JSON string whose value is itself serialized plan JSON.
        val decoded = runCatching { org.json.JSONTokener(text).nextValue() as? String }.getOrNull()
        if (decoded != null && decoded != text) return findPlanObject(decoded, depth + 1)
        return null
    }

    private fun looksLikePlan(value: JSONObject): Boolean =
        value.opt("message") is String || value.has("theme") || value.has("actions") || value.has("clarification")

    private fun unwrap(value: JSONObject, depth: Int): JSONObject? {
        for (key in wrapperKeys) {
            if (!value.has(key) || value.isNull(key)) continue
            nestedPlan(value.opt(key), depth + 1)?.let { return it }
        }
        // A few model adapters place text under message.content rather than a top-level content key.
        val message = value.optJSONObject("message") ?: return null
        for (key in listOf("content", "text", "response", "output")) {
            nestedPlan(message.opt(key), depth + 1)?.let { return it }
        }
        return null
    }

    private fun unwrap(value: JSONArray, depth: Int): JSONObject? {
        if (depth > 6) return null
        for (index in 0 until value.length()) {
            val item = value.opt(index)
            if (item is JSONObject && looksLikePlan(item)) return item
            nestedPlan(item, depth + 1)?.let { return it }
        }
        return null
    }

    private fun nestedPlan(value: Any?, depth: Int): JSONObject? = when (value) {
        is JSONObject -> if (looksLikePlan(value)) value else unwrap(value, depth)
        is JSONArray -> unwrap(value, depth)
        is String -> findPlanObject(value.trim(), depth)
        else -> null
    }

    private fun balancedJsonEnd(text: String, start: Int): Int? {
        val opening = text[start]
        val closing = if (opening == '{') '}' else ']'
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                opening -> depth++
                closing -> {
                    depth--
                    if (depth == 0) return index + 1
                    if (depth < 0) return null
                }
            }
        }
        return null
    }

    private fun requireOnlyKeys(value: JSONObject, allowed: Set<String>, required: Set<String>, label: String) {
        val keys = mutableSetOf<String>()
        val iterator = value.keys()
        while (iterator.hasNext()) keys += iterator.next()
        val unsupported = (keys - allowed).sorted()
        val missing = (required - keys).sorted()
        require(unsupported.isEmpty() && missing.isEmpty()) {
            buildString {
                append("The $label is invalid.")
                if (missing.isNotEmpty()) append(" Missing required field(s): ${missing.joinToString(", ")}.")
                if (unsupported.isNotEmpty()) append(" Unsupported field(s): ${unsupported.joinToString(", ")}.")
            }
        }
    }
}

/** The UI stages a validated plan here and can obtain it only from the explicit Confirm button. */
class AiPlanConfirmationGate {
    private var pending: AiLauncherPlan? = null

    fun stage(plan: AiLauncherPlan) { pending = plan }
    fun hasPending(): Boolean = pending != null
    fun cancel() { pending = null }
    fun confirm(): AiLauncherPlan? = pending.also { pending = null }
}
