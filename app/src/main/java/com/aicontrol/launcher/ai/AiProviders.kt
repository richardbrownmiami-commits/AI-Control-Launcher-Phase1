package com.aicontrol.launcher.ai

import com.aicontrol.launcher.data.SettingsStore
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

fun createProvider(settings: SettingsStore): AiProvider =
    if (settings.provider == SettingsStore.PROVIDER_GEMINI) GeminiProvider(settings.model, settings.geminiKey)
    else OpenRouterProvider(settings.model, settings.openRouterKey)

private fun cleanJson(text: String): String {
    val fence = "\u0060\u0060\u0060"
    return text.trim().removePrefix(fence + "json").removePrefix(fence).removeSuffix(fence).trim()
}

private fun parseReply(raw: String): AiReply {
    val root = JSONObject(cleanJson(raw))
    val message = root.optString("message", "").trim()
    val actions = root.optJSONArray("actions")
    val normalized = JSONObject().put("actions", actions ?: JSONArray()).toString()
    val fallback = if (message.isNotEmpty()) message else if (actions != null && actions.length() > 0) "Done." else "I'm here."
    return AiReply(fallback, if (actions != null && actions.length() > 0) normalized else null)
}

private fun errorBody(response: String): String = response.replace(Regex("\\s+"), " ").trim().take(200)
private fun historyText(history: List<AiTurn>): String = history.takeLast(6).joinToString("\n") { it.role + ": " + it.content }

class OpenRouterProvider(private val model: String, private val apiKey: String) : AiProvider {
    override suspend fun chat(userMessage: String, launcherState: String, history: List<AiTurn>): Result<AiReply> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiKey.isNotBlank()) { "OpenRouter API key is empty" }
            require(model.isNotBlank()) { "OpenRouter model is empty" }
            val prompt = "Conversation:\n" + historyText(history) + "\nUser: " + userMessage + "\nLauncher state: " + launcherState
            val body = JSONObject().apply {
                put("model", model); put("temperature", 0.2)
                put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", AiPlanner.SYSTEM_PROMPT))
                    .put(JSONObject().put("role", "user").put("content", prompt)))
            }.toString()
            val conn = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 15000; readTimeout = 30000; doOutput = true
                setRequestProperty("Authorization", "Bearer " + apiKey)
                setRequestProperty("HTTP-Referer", "https://github.com/richardbrownmiami-commits/AI-Control-Launcher-Phase1")
                setRequestProperty("X-Title", "AI Control Launcher"); setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val response = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
            if (conn.responseCode !in 200..299) error("OpenRouter HTTP " + conn.responseCode + ": " + errorBody(response))
            parseReply(JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"))
        }
    }
}

class GeminiProvider(private val model: String, private val apiKey: String) : AiProvider {
    override suspend fun chat(userMessage: String, launcherState: String, history: List<AiTurn>): Result<AiReply> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiKey.isNotBlank()) { "Gemini API key is empty" }
            require(model.isNotBlank()) { "Gemini model is empty" }
            val prompt = AiPlanner.SYSTEM_PROMPT + "\nConversation:\n" + historyText(history) + "\nUser: " + userMessage + "\nLauncher state: " + launcherState
            val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", prompt))))).toString()
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 15000; readTimeout = 30000; doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val response = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
            if (conn.responseCode !in 200..299) error("Gemini HTTP " + conn.responseCode + ": " + errorBody(response))
            parseReply(JSONObject(response).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text"))
        }
    }
}
