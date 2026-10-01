package com.aicontrol.launcher.ai

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class OpenAiCompatibleProvider(private val endpoint: String, private val apiKey: String, private val model: String): AiProvider {
    override suspend fun generatePlan(userCommand: String, launcherState: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(apiKey.isNotBlank()) { "API key is empty" }
            val body = JSONObject().apply {
                put("model", model)
                put("temperature", 0.2)
                put("messages", JSONArray().put(JSONObject().put("role", "system").put("content", "You control only this launcher. Return ONLY JSON with an actions array. Allowed actions: HIDE_APPS, SHOW_APPS, SET_THEME, SET_LAYOUT, CREATE_WORKSPACE." )).put(JSONObject().put("role", "user").put("content", "Command: $userCommand\nState: $launcherState")))
            }.toString()
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 15000; readTimeout = 30000; doOutput = true
                setRequestProperty("Authorization", "Bearer $apiKey"); setRequestProperty("Content-Type", "application/json")
            }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val text = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}: $text")
            val root = JSONObject(text)
            val content = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
                ?: error("AI response did not contain choices[0].message.content")
            content.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        }
    }
}
