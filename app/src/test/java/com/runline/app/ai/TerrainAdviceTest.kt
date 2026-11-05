package com.runline.app.ai

import com.runline.app.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class TerrainAdviceTest {
    private fun facts(direction: String = "UPHILL", allowed: Boolean = true, source: String = "TERRAIN_MAP") =
        JSONObject().put("direction", direction).put("hill_advice_allowed", allowed).put("source", source)
    @Test fun unsupportedEnglishAndSpanishTerrainClaimsAreSilenced() {
        for (text in listOf("Relax on this downhill.", "Keep steady on the climb.", "Take the slope gently.",
                "Vas subiendo, mantén el esfuerzo.", "Relájate en la bajada.", "Aprovecha el terreno llano.", "Esta cuesta pide calma.")) {
            assertFalse(LiveAiAdvice.terrainCompatible(text, null))
            assertFalse(LiveAiAdvice.terrainCompatible(text, facts(allowed = false)))
            assertFalse(LiveAiAdvice.terrainCompatible(text, facts(source = "GPS")))
        }
        assertTrue(LiveAiAdvice.terrainCompatible("You are halfway to your distance goal.", null))
    }
    @Test fun directionMustMatchIndependentContext() {
        assertTrue(LiveAiAdvice.terrainCompatible("Take the climb at a comfortable effort.", facts()))
        assertFalse(LiveAiAdvice.terrainCompatible("Relájate en la bajada.", facts()))
        assertTrue(LiveAiAdvice.terrainCompatible("Relájate en la bajada.", facts("DOWNHILL")))
        assertFalse(LiveAiAdvice.terrainCompatible("You are on flat ground.", facts("DOWNHILL")))
    }
    @Test fun aiPayloadWithholdsRawHeightButKeepsPaceAndDistance() {
        val points = (0..100).map { RunPoint(it * 3.0 / 111195.08, 0.0, it * 1000L, 3f, 100.0 - it * .45) }
        val run = RunSession("synthetic-ai-terrain", 0, 100000, 100000, points)
        val summary = AiRunSummary.of(run)
        assertTrue(summary.isNull("ascent_m")); assertTrue(summary.isNull("descent_m"))
        assertTrue(summary.getDouble("distance_km") > .29)
        val recent = AiCoachingContext.build(LocalDate.of(2026, 1, 1), activeRun = run).getJSONObject("recent_terrain")
        assertEquals("UNKNOWN", recent.getString("direction")); assertFalse(recent.getBoolean("hill_advice_allowed"))
    }
}
