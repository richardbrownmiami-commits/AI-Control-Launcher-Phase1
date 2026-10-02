package com.aicontrol.launcher.ai

data class AiTurn(val role: String, val content: String)
data class AiReply(val text: String)

interface AiProvider {
    suspend fun chat(userMessage: String, launcherState: String, history: List<AiTurn>): Result<AiReply>
}
