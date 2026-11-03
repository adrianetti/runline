package com.runline.app.ai

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LiveAdviceGateTest {
    private class Provider : CoachAiProvider {
        var calls = 0
        var prompt = ""
        override fun message(key: String, model: String, system: String, data: JSONObject, maxTokens: Int, timeoutMs: Int): String {
            calls++; prompt = system; return "NONE"
        }
        override fun checkModel(key: String, model: String) = Unit
    }
    private fun stable(): JSONObject = JSONObject().put("run", JSONObject()
        .put("activity_type", "run").put("distance_km", 3.4).put("active_seconds", 1326)
        .put("recent_pace_seconds_per_km", 390.0)
        .put("recent_pace_comparison", JSONObject().put("trend", "SIMILAR")))
        .put("coaching_context", JSONObject().put("today", "2026-11-03")
            .put("completed_training", JSONObject().put("last_7_days_km", 5.0)
                .put("last_28_days_weekly_average_km", 20.0).put("longest_run_last_30_days_km", 10.0))
            .put("active_goal", JSONObject().put("type", "DISTANCE").put("distance_goal_m", 6000)
                .put("intended_effort", "EASY").put("pace_min_seconds_per_km", 360).put("pace_max_seconds_per_km", 420))
            .put("recent_terrain", JSONObject().put("direction", "FLAT").put("grade_percent", 0.0))
            .put("recent_spoken_feedback", JSONArray())
            .put("recent_runs", JSONArray().put(prior("2026-11-02")).put(prior("2026-10-30"))))
    private fun prior(date: String) = JSONObject().put("date", date).put("activity_type", "run")
        .put("distance_km", 5.0).put("active_seconds", 1800.0).put("self_reported_effort_1_to_10", 3)
    private fun request(data: JSONObject): Provider = Provider().also {
        assertNull(LiveAiAdvice.request(it, "fake-key", "claude-haiku-5-5", "English", data))
    }

    @Test fun stableFlatInBandRunningDoesNotSpendARequestOnGenericEncouragement() {
        assertEquals(0, request(stable()).calls)
    }

    @Test fun actualRecentSpeechPreventsDuplicateAdviceEvenWhenOtherContextIsUseful() {
        for (source in listOf("LOCAL", "AI")) {
            val data = stable(); val context = data.getJSONObject("coaching_context")
            context.getJSONObject("active_goal").put("intended_effort", "RECOVERY")
            context.put("recent_spoken_feedback", JSONArray().put(JSONObject().put("source", source).put("seconds_ago", 10).put("text", "Three kilometres.")))
            assertEquals(0, request(data).calls)
        }
    }

    @Test fun oldFutureOrUnspokenEventsDoNotSilenceUsefulContext() {
        for ((source, seconds) in listOf("LOCAL" to 85, "AI" to -1, "GENERATED" to 10)) {
            val data = stable(); val context = data.getJSONObject("coaching_context")
            context.getJSONObject("active_goal").put("intended_effort", "RECOVERY")
            context.put("recent_spoken_feedback", JSONArray().put(JSONObject().put("source", source).put("seconds_ago", seconds)))
            assertEquals(1, request(data).calls)
        }
    }

    @Test fun unknownPaceTerrainHistoryOrEffortRemainEligible() {
        val cases = listOf<(JSONObject) -> Unit>(
            { it.getJSONObject("run").put("recent_pace_seconds_per_km", JSONObject.NULL) },
            { it.getJSONObject("run").put("recent_pace_comparison", JSONObject.NULL) },
            { it.getJSONObject("coaching_context").getJSONObject("recent_terrain").put("direction", "UNKNOWN").put("grade_percent", JSONObject.NULL) },
            { it.getJSONObject("coaching_context").put("recent_runs", JSONArray()) },
            { it.getJSONObject("coaching_context").getJSONArray("recent_runs").getJSONObject(0).put("self_reported_effort_1_to_10", JSONObject.NULL) },
        )
        cases.forEach { edit -> val data = stable(); edit(data); assertEquals(1, request(data).calls) }
    }

    @Test fun meaningfulChangeRecoveryGoalEndAndConsentedContextRemainEligible() {
        val cases = listOf<(JSONObject) -> Unit>(
            { it.getJSONObject("coaching_context").getJSONObject("recent_terrain").put("direction", "UPHILL").put("grade_percent", 8.0) },
            { it.getJSONObject("run").getJSONObject("recent_pace_comparison").put("trend", "SLOWER") },
            { it.getJSONObject("run").put("recent_pace_seconds_per_km", 450.0) },
            { it.getJSONObject("coaching_context").getJSONObject("active_goal").put("intended_effort", "RECOVERY") },
            { it.getJSONObject("coaching_context").getJSONArray("recent_runs").getJSONObject(0).put("self_reported_effort_1_to_10", 8) },
            { it.getJSONObject("run").put("distance_km", 5.6) },
            { it.getJSONObject("run").put("lap_comparison", JSONObject().put("pace_change_percent", 12)) },
            { it.getJSONObject("coaching_context").put("runner_profile", JSONObject().put("training_preferences", "Prefer easy running.")) },
        )
        cases.forEach { edit -> val data = stable(); edit(data); assertEquals(1, request(data).calls) }
    }

    @Test fun missingHistoricalDistanceDurationOrWeeklyMeasurementsRemainEligible() {
        for (field in listOf("distance_km", "active_seconds")) {
            val data = stable()
            data.getJSONObject("coaching_context").getJSONArray("recent_runs").getJSONObject(0).put(field, JSONObject.NULL)
            assertEquals(1, request(data).calls)
        }
        for (field in listOf("last_7_days_km", "last_28_days_weekly_average_km", "longest_run_last_30_days_km")) {
            val data = stable()
            data.getJSONObject("coaching_context").getJSONObject("completed_training").put(field, JSONObject.NULL)
            assertEquals(1, request(data).calls)
        }
    }

    @Test fun longRunDurationOrWeeklyLoadContextIsNotSilencedByStablePace() {
        val cases = listOf<(JSONObject) -> Unit>(
            { it.getJSONObject("run").put("distance_km", 12.0) },
            { it.getJSONObject("run").put("active_seconds", 2000.0) },
            { it.getJSONObject("coaching_context").getJSONObject("completed_training").put("last_7_days_km", 18.0) },
        )
        cases.forEach { edit ->
            val data = stable()
            data.getJSONObject("coaching_context").getJSONObject("active_goal").put("type", "PACE").remove("distance_goal_m")
            edit(data); assertEquals(1, request(data).calls)
        }
    }

    @Test fun pacePromptRequiresObservedComparisonAndWarnsAboutPartialSplits() {
        val data = stable(); data.getJSONObject("coaching_context").getJSONObject("recent_terrain").put("direction", "UNKNOWN")
        val provider = request(data)
        assertEquals(1, provider.calls)
        assertTrue(provider.prompt.contains("recent_pace_comparison.trend"))
        assertTrue(provider.prompt.contains("partial kilometre"))
    }
}
