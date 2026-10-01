package com.aicontrol.launcher.ai

interface AiProvider {
    suspend fun generatePlan(userCommand: String, launcherState: String): Result<String>
}
