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
    var cleaned = text.trim()
    if (cleaned.startsWith(fence)) {
        cleaned = cleaned.removePrefix(fence + "json").removePrefix(fence)
        cleaned = cleaned.removeSuffix(fence).trim()
    }
    return cleaned
}

private fun parseReply(raw: String): AiReply {
    val cleaned = cleanJson(raw)
    val root = try {
        JSONObject(cleaned)
    } catch (ex: Exception) {
        throw IllegalStateException("AI JSON parse error: "+ex.message+"; raw="+cleaned.take(200))
    }
    val hasMessage = root.has("message")
    val message = root.optString("message", "").trim()
    val actions = root.optJSONArray("actions")
    if (!hasMessage && actions == null) {
        throw IllegalStateException("AI response missing message/actions; raw="+cleaned.take(200))
    }
    val normalized = JSONObject().put("actions", actions ?: JSONArray()).toString()
    val reply = if (hasMessage) message else if (actions != null && actions.length() > 0) "Done." else ""
    if (reply.isBlank() && (actions == null || actions.length() == 0)) {
        throw IllegalStateException("AI response contained no message and no actions; raw="+cleaned.take(200))
    }
    return AiReply(reply, if (actions != null && actions.length() > 0) normalized else null)
}

private fun errorBody(response: String): String = response.replace(Regex("\\s+"), " ").trim().take(200)
private fun readResponse(conn: HttpURLConnection): String {
    val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
    return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
}
private fun historyText(history: List<AiTurn>): String =
    history.takeLast(8).joinToString("\n") { it.role + ": " + it.content }

class OpenRouterProvider(private val model: String, private val apiKey: String) : AiProvider {
    override suspend fun chat(userMessage: String, launcherState: String, history: List<AiTurn>): Result<AiReply> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(apiKey.isNotBlank()) { "OpenRouter API key is empty" }
                require(model.isNotBlank()) { "OpenRouter model is empty" }
                val prompt = buildString {
                    append("Launcher state:\n"); append(launcherState)
                    append("\nConversation history:\n"); append(historyText(history))
                    append("\nCurrent user message:\n"); append(userMessage)
                }
                val body = JSONObject().apply {
                    put("model", model)
                    put("temperature", 0.4)
                    put("messages", JSONArray()
                        .put(JSONObject().put("role", "system").put("content", AiPlanner.SYSTEM_PROMPT))
                        .put(JSONObject().put("role", "user").put("content", prompt)))
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
                    val content=JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
                    parseReply(content)
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
                    val content=JSONObject(response).getJSONArray("candidates").getJSONObject(0).getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                    parseReply(content)
                }finally{conn.disconnect()}
            }
        }
}
