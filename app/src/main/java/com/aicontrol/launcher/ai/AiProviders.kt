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
    val fence = "```"
    return text.trim().removePrefix(fence + "json").removePrefix(fence).removeSuffix(fence).trim()
}

class OpenRouterProvider(private val model: String, private val apiKey: String) : AiProvider {
    override suspend fun generatePlan(userCommand: String, launcherState: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiKey.isNotBlank()) { "OpenRouter API key is empty" }
            val body = JSONObject().apply {
                put("model", model)
                put("temperature", 0.2)
                put("messages", JSONArray()
                    .put(JSONObject().put("role", "system").put("content", AiPlanner.SYSTEM_PROMPT))
                    .put(JSONObject().put("role", "user").put("content", "Command: " + userCommand + "
State: " + launcherState)))
            }.toString()
            val conn = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 15000; readTimeout = 30000; doOutput = true
                setRequestProperty("Authorization", "Bearer " + apiKey)
                setRequestProperty("HTTP-Referer", "https://github.com/richardbrownmiami-commits/AI-Control-Launcher-Phase1")
                setRequestProperty("X-Title", "AI Control Launcher")
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val response = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
            if (conn.responseCode !in 200..299) error("OpenRouter HTTP " + conn.responseCode + ": " + response)
            cleanJson(JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content"))
        }
    }
}

class GeminiProvider(private val model: String, private val apiKey: String) : AiProvider {
    override suspend fun generatePlan(userCommand: String, launcherState: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiKey.isNotBlank()) { "Gemini API key is empty" }
            val body = JSONObject().apply {
                put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(
                    JSONObject().put("text", AiPlanner.SYSTEM_PROMPT + "
Command: " + userCommand + "
State: " + launcherState)
                ))))
            }.toString()
            val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 15000; readTimeout = 30000; doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val response = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
            if (conn.responseCode !in 200..299) error("Gemini HTTP " + conn.responseCode + ": " + response)
            cleanJson(JSONObject(response).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text"))
        }
    }
}