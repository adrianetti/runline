package com.runline.app.ai

data class AiModelPreset(val id: String, val name: String)

data class AiProviderOption(val id: String, val name: String, val models: List<AiModelPreset>) {
    /** An unknown saved ID remains a custom choice rather than silently changing the user's model. */
    fun presetIndex(modelId: String): Int = models.indexOfFirst { it.id == modelId }
}

/**
 * Local choices only: opening settings never fetches models or contacts a provider.
 * Verified 2026-11-02 against https://platform.claude.com/docs/en/models/overview.
 * These two presets suit short live prompts and longer reviews; other IDs remain available as custom.
 */
object AiProviderCatalog {
    const val ANTHROPIC = "anthropic"
    const val DEFAULT_LIVE_MODEL = "claude-haiku-5-5"
    const val DEFAULT_REVIEW_MODEL = "claude-sonnet-5-5"
    val providers = listOf(AiProviderOption(ANTHROPIC, "Anthropic", listOf(
        AiModelPreset(DEFAULT_LIVE_MODEL, "Haiku 5.5"),
        AiModelPreset(DEFAULT_REVIEW_MODEL, "Sonnet 5.5"),
    )))

    fun find(id: String): AiProviderOption? = providers.firstOrNull { it.id == id }
}
