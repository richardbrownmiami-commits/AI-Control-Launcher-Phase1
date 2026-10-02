package com.aicontrol.launcher.ai

import com.aicontrol.launcher.theme.ThemeSpec
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

    fun parse(raw: String, availableThemes: Set<String>): AiPlanDecision {
        require(raw.length <= MAX_RESPONSE_CHARS) { "AI response is too large to review safely." }
        val root = JSONObject(raw.trim())
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

        val theme = themeObject?.let { parseTheme(it, availableThemes) }
        val actions = parseActions(actionArray)
        if (theme == null && actions.isEmpty()) return AiPlanDecision.Conversation(message)
        return AiPlanDecision.Review(AiLauncherPlan(message, theme, actions))
    }

    private fun parseTheme(value: JSONObject, availableThemes: Set<String>): AiThemeOperation {
        val operation = requiredString(value, "operation", 20).uppercase()
        return when (operation) {
            "CREATE" -> {
                requireOnlyKeys(
                    value,
                    setOf("operation", "name", "background", "accent", "accent2", "card", "style", "typography", "iconStyle", "backgroundStyle"),
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
                        backgroundStyle = optionalString(value, "backgroundStyle", 20) ?: suggested.backgroundStyle
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

    private fun parseActions(actions: JSONArray?): List<AiLauncherAction> {
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
                else -> throw IllegalArgumentException("Unsupported AI action '$type'. No changes were applied.")
            }
            result += action
        }
        require(result.count { it is AiLauncherAction.SearchAssets || it === AiLauncherAction.AddWidget } <= 1) {
            "A plan may open only one external search or Android widget-picker flow at a time."
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
        return value.opt(key) as? JSONObject ?: throw IllegalArgumentException("'$key' must be an object or null.")
    }

    private fun optionalArray(value: JSONObject, key: String): JSONArray? {
        if (!value.has(key) || value.isNull(key)) return null
        return value.opt(key) as? JSONArray ?: throw IllegalArgumentException("'$key' must be an array or null.")
    }

    private fun requireOnlyKeys(value: JSONObject, allowed: Set<String>, required: Set<String>, label: String) {
        val keys = mutableSetOf<String>()
        val iterator = value.keys()
        while (iterator.hasNext()) keys += iterator.next()
        require(keys.all { it in allowed } && keys.containsAll(required)) {
            "The $label contains missing or unsupported fields."
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
