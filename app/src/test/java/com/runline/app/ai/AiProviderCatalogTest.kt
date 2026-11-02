package com.runline.app.ai

import org.junit.Assert.*
import org.junit.Test

class AiProviderCatalogTest {
    @Test fun freshConfigurationIsOfflineUntilExplicitlyEnabled() {
        val config = AiConfig()
        assertFalse(config.enabled)
        assertFalse(config.liveEnabled)
        assertFalse(config.automaticReviews)
        assertFalse(config.automaticPlanUpdates)
        assertFalse(config.includeNotes)
        assertFalse(config.isReady(hasKey = true))
    }

    @Test fun readinessRequiresBothEnablementAndStoredKey() {
        val config = AiConfig(enabled = true)
        assertFalse(config.isReady(hasKey = false))
        assertTrue(config.isReady(hasKey = true))
        assertFalse(config.copy(provider = "unsupported").isReady(hasKey = true))
    }

    @Test fun currentPresetsAndLegacyCustomModelsRemainDistinct() {
        val provider = AiProviderCatalog.find(AiProviderCatalog.ANTHROPIC)!!
        assertTrue(provider.presetIndex(AiConfig().liveModel) >= 0)
        assertTrue(provider.presetIndex(AiConfig().reviewModel) >= 0)
        val legacy = "claude-haiku-4-5-20251001"
        assertEquals(-1, provider.presetIndex(legacy))
        assertTrue(AiConfig(liveModel = legacy).valid())
        assertEquals(legacy, AiConfig(liveModel = legacy).liveModel)
        assertNull(AiProviderCatalog.find("openai"))
    }

    @Test fun invalidCustomIdsAndIntervalsCannotBecomeReady() {
        for (model in listOf("", "claude-model?key=bad", "https://example.com", "claude-" + "a".repeat(101))) {
            assertFalse(AiConfig(enabled = true, liveModel = model).isReady(hasKey = true))
        }
        assertFalse(AiConfig(enabled = true, intervalMinutes = 2).isReady(hasKey = true))
        assertFalse(AiConfig(enabled = true, intervalMinutes = 11).isReady(hasKey = true))
    }
}
