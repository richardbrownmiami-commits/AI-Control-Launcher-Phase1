package com.aicontrol.launcher.ai

import com.aicontrol.launcher.data.SettingsStore
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

fun createProvider(settings: SettingsStore): AiProvider {
    require(settings.provider == SettingsStore.PROVIDER_OPENROUTER || settings.provider == SettingsStore.PROVIDER_GEMINI) {
        "Select a supported AI provider in AI Settings."
    }
    require(settings.model in FreeModels.forProvider(settings.provider)) {
        "Select one of the models listed for the current provider."
    }
    return if (settings.provider == SettingsStore.PROVIDER_GEMINI) {
        GeminiProvider(settings.model, settings.geminiKey)
    } else {
        OpenRouterProvider(settings.model, settings.openRouterKey)
    }
}

private fun errorBody(response: String): String = response.replace(Regex("\\s+"), " ").trim().take(200)
private fun readResponse(conn: HttpURLConnection): String {
    val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
    if (stream == null) return ""
    val bytes = stream.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= 256 * 1024) { "AI provider response exceeded the safe size limit." }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
    return String(bytes, Charsets.UTF_8)
}
private fun openRouterContent(message: JSONObject): String {
    val content = message.opt("content")
    if (content is String) return content
    if (content is JSONArray) {
        return (0 until content.length()).mapNotNull { index ->
            content.optJSONObject(index)?.optString("text")?.takeIf { it.isNotBlank() }
        }.joinToString("\n")
    }
    throw IllegalArgumentException("OpenRouter returned no readable assistant text. Check the selected model and provider response format.")
}

class OpenRouterProvider(private val model: String, private val apiKey: String) : AiProvider {
    override suspend fun chat(userMessage: String, launcherState: String, history: List<AiTurn>): Result<AiReply> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(apiKey.isNotBlank()) { "OpenRouter API key is empty" }
                require(model.isNotBlank()) { "OpenRouter model is empty" }
                val messages = JSONArray()
                    .put(JSONObject().put("role", "system").put("content", AiPlanner.SYSTEM_PROMPT))
                history.takeLast(8).forEach { turn ->
                    val role = if (turn.role == "assistant") "assistant" else "user"
                    messages.put(JSONObject().put("role", role).put("content", turn.content))
                }
                val prompt = "Launcher state (read-only context):\n$launcherState\n\nCurrent user message:\n$userMessage"
                messages.put(JSONObject().put("role", "user").put("content", prompt))
                val body = JSONObject().apply {
                    put("model", model)
                    put("temperature", 0.4)
                    put("messages", messages)
                }.toString()
                val conn = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                    requestMethod="POST"; connectTimeout=15000; readTimeout=30000; doOutput=true
                    setRequestProperty("Authorization","Bearer "+apiKey)
                    setRequestProperty("HTTP-Referer","https://github.com/richardbrownmiami-commits/AI-Control-Launcher-Phase1")
                    setRequestProperty("X-Title","AI Control Launcher"); setRequestProperty("Content-Type","application/json")
                }
                try {
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val response=readResponse(conn)
                    if(conn.responseCode !in 200..299) error("OpenRouter HTTP "+conn.responseCode+": "+errorBody(response))
                    val root = JSONObject(response)
                    val choices = root.optJSONArray("choices")
                        ?: throw IllegalArgumentException("OpenRouter returned an unexpected response. ${errorBody(response)}")
                    require(choices.length() > 0) { "OpenRouter returned no response choices. ${errorBody(response)}" }
                    val content = openRouterContent(choices.optJSONObject(0)?.optJSONObject("message")
                        ?: throw IllegalArgumentException("OpenRouter response was missing its assistant message."))
                    require(content.length <= 16_000) { "AI response is too large to review safely." }
                    AiReply(content)
                } finally { conn.disconnect() }
            }
        }
}

class GeminiProvider(private val model: String, private val apiKey: String) : AiProvider {
    override suspend fun chat(userMessage: String, launcherState: String, history: List<AiTurn>): Result<AiReply> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(apiKey.isNotBlank()) { "Gemini API key is empty" }
                require(model.isNotBlank()) { "Gemini model is empty" }
                val contents=JSONArray()
                history.takeLast(8).forEach {
                    contents.put(JSONObject().put("role",if(it.role=="assistant")"model" else "user")
                        .put("parts",JSONArray().put(JSONObject().put("text",it.content))))
                }
                contents.put(JSONObject().put("role","user").put("parts",JSONArray().put(JSONObject().put("text",
                    "Launcher state:\n"+launcherState+"\n\nCurrent user message:\n"+userMessage))))
                val body=JSONObject()
                    .put("system_instruction",JSONObject().put("parts",JSONArray().put(JSONObject().put("text",AiPlanner.SYSTEM_PROMPT))))
                    .put("contents",contents).put("generationConfig",JSONObject().put("temperature",0.4)).toString()
                val endpoint="https://generativelanguage.googleapis.com/v1beta/models/"+model+":generateContent"
                val conn=(URL(endpoint).openConnection() as HttpURLConnection).apply{
                    requestMethod="POST";connectTimeout=15000;readTimeout=30000;doOutput=true
                    setRequestProperty("Content-Type","application/json")
                    setRequestProperty("x-goog-api-key",apiKey)
                }
                try{
                    conn.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))}
                    val response=readResponse(conn)
                    if(conn.responseCode !in 200..299) error("Gemini HTTP "+conn.responseCode+": "+errorBody(response))
                    val root = JSONObject(response)
                    val candidates = root.optJSONArray("candidates")
                        ?: throw IllegalArgumentException("Gemini returned no response candidates. ${errorBody(response)}")
                    require(candidates.length() > 0) {
                        val reason = root.optJSONObject("promptFeedback")?.optString("blockReason").orEmpty()
                        if (reason.isNotBlank()) "Gemini did not return a reply (provider block reason: $reason)." else "Gemini returned no reply candidates."
                    }
                    val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                        ?: throw IllegalArgumentException("Gemini response had no readable text part. ${errorBody(response)}")
                    val content = (0 until parts.length()).mapNotNull { parts.optJSONObject(it)?.optString("text")?.takeIf(String::isNotBlank) }
                        .joinToString("\n")
                    require(content.isNotBlank()) { "Gemini returned an empty assistant reply." }
                    require(content.length <= 16_000) { "AI response is too large to review safely." }
                    AiReply(content)
                }finally{conn.disconnect()}
            }
        }
}
