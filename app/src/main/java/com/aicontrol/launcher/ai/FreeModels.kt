package com.aicontrol.launcher.ai

object FreeModels {
    val openRouter = listOf(
        "openrouter/free",
        "stealth/space-bunny-alpha",
        "nvidia/nemotron-3-ultra-550b-a55b:free",
        "nvidia/nemotron-3.5-lightning:free",
        "qwen/qwen3.8-27b:free",
        "google/gemma-4-31b-it:free",
        "google/gemma-4-26b-a4b-it:free",
        "poolside/laguna-s-2.1:free",
        "thinkingmachines/inkling:free",
        "inclusionai/ling-3.0-flash-sante:free",
        "dots-studio/dots-3-note-preview:free",
        "liquid/lfm-2.5-2.6b:free"
    )
    val gemini = listOf(
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite",
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-flash-latest",
        "gemini-flash-lite-latest"
    )
    fun forProvider(provider: String): List<String> = if (provider == "gemini") gemini else openRouter
}