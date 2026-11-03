package com.runline.app.ai

import com.runline.app.core.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class SmartRunAiInputTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val config = AiConfig(enabled = true)
    private fun run(id: String, day: LocalDate = today.minusDays(1)): RunSession {
        val start = day.atTime(8, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return RunSession(id, start, start + 1_800_000, 1_800_000,
            (0..300).map { RunPoint(40.123456 + it * 10.0 / 111_195.0802, -3.123456, start + it * 6_000, 3f, 100.0) })
    }
    private fun rest() = JSONObject().put("goal_type", "REST").put("effort", "RECOVERY")
        .put("target_distance_km", JSONObject.NULL).put("min_distance_km", JSONObject.NULL).put("max_distance_km", JSONObject.NULL)
        .put("pace_min_seconds_per_km", JSONObject.NULL).put("pace_max_seconds_per_km", JSONObject.NULL)
        .put("duration_minutes", JSONObject.NULL).put("rationale", "Keep recovery comfortable today.")
    private fun response(body: String) = AiHttpResponse(200, JSONObject().put("stop_reason", "end_turn")
        .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", body))).toString())
    private fun failure(reason: AiFailure.Reason, operation: () -> Unit) {
        try { operation(); fail("Expected $reason") } catch (error: AiFailure) { assertEquals(reason, error.reason) }
    }

    @Test fun explicitRequestUsesSelectedModelAndCoordinateFreeConsentBoundedContext() {
        val runs = listOf(run("one"))
        val profile = RunnerProfile(RunningExperience.RETURNING, "PRIVATE preference", "PRIVATE health")
        val details: (String) -> ActivityDetails = { ActivityDetails("PRIVATE title", "PRIVATE notes", 8, photoIds = listOf("PRIVATE.jpg")) }
        val input = SmartRunAiInput.build(today, runs, details, profile = profile, goal = RunnerGoal("Half marathon in April"))
        var calls = 0
        val provider = AnthropicProvider(AiTransport { method, path, _, body, _ ->
            calls++; assertEquals("POST", method); assertEquals("/v1/messages", path)
            val request = JSONObject(body!!)
            assertEquals(config.reviewModel, request.getString("model"))
            val schema = request.getJSONObject("output_config").getJSONObject("format").getJSONObject("schema")
            val names = setOf("goal_type", "effort", "target_distance_km", "min_distance_km", "max_distance_km",
                "pace_min_seconds_per_km", "pace_max_seconds_per_km", "duration_minutes", "rationale")
            assertFalse(schema.getBoolean("additionalProperties"))
            assertEquals(names, schema.getJSONObject("properties").keys().asSequence().toSet())
            assertEquals(names, schema.getJSONArray("required").let { array -> (0 until array.length()).map { array.getString(it) }.toSet() })
            val properties = schema.getJSONObject("properties")
            assertEquals("string", properties.getJSONObject("effort").getString("type"))
            assertEquals(listOf("EASY", "RECOVERY", "STEADY"), properties.getJSONObject("effort").getJSONArray("enum")
                .let { array -> (0 until array.length()).map { array.getString(it) } })
            assertEquals(listOf("integer", "null"), properties.getJSONObject("pace_min_seconds_per_km").getJSONArray("type")
                .let { array -> (0 until array.length()).map { array.getString(it) } })
            assertFalse(schema.toString().contains("Half marathon"))
            assertFalse(schema.toString().contains("minimum")); assertFalse(schema.toString().contains("maxLength"))
            assertTrue(request.getString("system").contains("For REST use effort EASY"))
            assertTrue(request.getString("system").contains("Recorded history may be incomplete"))
            assertTrue(request.getString("system").contains("Do not postpone, reschedule or redefine the user's stated distance or timing"))
            assertTrue(request.getString("system").contains("leave the user's ambition and timing unchanged"))
            assertFalse(body.contains("PRIVATE")); assertFalse(body.contains("40.123456")); assertFalse(body.contains("-3.123456"))
            assertTrue(body.contains("Half marathon in April"))
            response(rest().toString())
        })
        val suggestion = SmartRunAiInput.request(provider, "fake-key", config, "English", input,
            SmartRunAiInput.history(runs, details), today)
        assertEquals(1, calls); assertEquals(SmartRunGoalType.REST, suggestion.goalType)
        assertFalse(input.toString().contains("training_plan"))
        assertTrue(input.getJSONObject("allowed_goal").getBoolean("rest_recommended"))
        assertTrue(input.getJSONObject("allowed_goal").has("max_time_only_duration_minutes"))
    }

    @Test fun providerDefaultMethodPreservesExistingFakesAndLocalValidation() {
        var calls = 0
        val provider = object : CoachAiProvider {
            override fun checkModel(key: String, model: String) = Unit
            override fun message(key: String, model: String, system: String, data: JSONObject, maxTokens: Int, timeoutMs: Int): String {
                calls++; assertEquals(2048, maxTokens); assertEquals(60_000, timeoutMs)
                return rest().put("effort", "EASY").toString()
            }
        }
        assertEquals(SmartRunGoalType.REST, SmartRunAiInput.request(provider, "fake-key", config, "English",
            JSONObject(), emptyList(), today).goalType)
        assertEquals(1, calls)
    }

    @Test fun observedExtraFieldAndNullRestEffortRemainRejected() {
        val established = listOf(1, 5, 8, 12).map { days -> SmartRunHistoryRun(today.minusDays(days.toLong()),
            8.0, "run-$days", 3, 48 * 60_000) }
        val extraField = """{"goal_type":"DISTANCE","effort":"EASY","target_distance_km":6,"min_distance_km":4,"max_distance_km":8,"pace_min_seconds_per_km":360,"pace_max_seconds_per_km":420,"duration_minutes":null,"rationale":"You ran an easy 5 km yesterday and recent runs have all felt easy. A relaxed 6 km at a comfortable pace fits your half marathon aim and stays well within your recent running and load capacity.","rest_recommended":null}"""
        val nullEffort = """{"goal_type":"REST","effort":null,"target_distance_km":null,"min_distance_km":null,"max_distance_km":null,"pace_min_seconds_per_km":null,"pace_max_seconds_per_km":null,"duration_minutes":null,"rationale":"Ayer corriste 8,4 km con esfuerzo 8/10, bastante más duro que tus salidas habituales (3/10). Hoy descansar encaja bien y mantiene tu objetivo de media maratón con comodidad."}"""
        for (observed in listOf(extraField, nullEffort)) {
            var calls = 0
            val provider = AnthropicProvider(AiTransport { _, _, _, _, _ -> calls++; response(observed) })
            failure(AiFailure.Reason.RESPONSE) {
                SmartRunAiInput.request(provider, "fake-key", config, "English", JSONObject(), established, today)
            }
            assertEquals(1, calls)
        }
        val validRest = JSONObject(nullEffort).put("effort", "EASY").toString()
        assertEquals(SmartRunGoalType.REST, SmartRunAiInput.parse(validRest, established, today).goalType)
    }

    @Test fun schemaCompliantShapeStillCannotBypassNumericCaps() {
        var calls = 0
        val tooFar = rest().put("goal_type", "DISTANCE").put("effort", "EASY")
            .put("target_distance_km", 100).put("min_distance_km", 90).put("max_distance_km", 110)
        val provider = AnthropicProvider(AiTransport { _, _, _, body, _ ->
            calls++; assertTrue(JSONObject(body!!).has("output_config")); response(tooFar.toString())
        })
        failure(AiFailure.Reason.RESPONSE) {
            SmartRunAiInput.request(provider, "fake-key", config, "English", JSONObject(), emptyList(), today)
        }
        assertEquals(1, calls)
    }

    @Test fun profileAndNotesRequireSeparateConsentAndRemainBounded() {
        val profile = RunnerProfile(RunningExperience.REGULAR, "p".repeat(900), "h".repeat(900), true)
        val input = SmartRunAiInput.build(today, listOf(run("one")),
            { ActivityDetails("t".repeat(900), "n".repeat(900), photoIds = listOf("DO_NOT_SEND_PHOTO")) }, true, profile)
        val context = input.getJSONObject("coaching_context")
        assertEquals(600, context.getJSONObject("runner_profile").getString("self_reported_health_notes").length)
        assertEquals(500, context.getJSONArray("recent_runs").getJSONObject(0).getString("user_notes").length)
        assertFalse(input.toString().contains("DO_NOT_SEND_PHOTO"))
    }

    @Test fun disabledOrAlreadyRevokedRequestNeverCallsProvider() {
        var calls = 0
        val provider = AnthropicProvider(AiTransport { _, _, _, _, _ -> calls++; response(rest().toString()) })
        failure(AiFailure.Reason.DISABLED) { SmartRunAiInput.request(provider, "fake-key", AiConfig(), "English", JSONObject(), emptyList(), today) }
        failure(AiFailure.Reason.DISABLED) { SmartRunAiInput.request(provider, "fake-key", config, "English", JSONObject(), emptyList(), today) { false } }
        assertEquals(0, calls)
    }

    @Test fun inFlightPrivacyOrHistoryRevocationRejectsReturnedSuggestion() {
        var current = true
        var calls = 0
        val provider = AnthropicProvider(AiTransport { _, _, _, _, _ -> calls++; current = false; response(rest().toString()) })
        failure(AiFailure.Reason.DISABLED) {
            SmartRunAiInput.request(provider, "fake-key", config, "English", JSONObject(), emptyList(), today) { current }
        }
        assertEquals(1, calls)
    }

    @Test fun unsafeStructuredOutputCannotSmuggleTargetsOrWorkoutPhases() {
        failure(AiFailure.Reason.RESPONSE) { SmartRunAiInput.parse(rest().put("phases", JSONArray()).toString(), emptyList(), today) }
        failure(AiFailure.Reason.RESPONSE) { SmartRunAiInput.parse(rest().put("target_distance_km", 10).toString(), emptyList(), today) }
        failure(AiFailure.Reason.RESPONSE) { SmartRunAiInput.parse(rest().put("effort", "ALL_OUT").toString(), emptyList(), today) }
        failure(AiFailure.Reason.RESPONSE) { SmartRunAiInput.parse(rest().put("duration_minutes", "20").toString(), emptyList(), today) }
        failure(AiFailure.Reason.RESPONSE) { SmartRunAiInput.parse(rest().put("rationale", "x".repeat(241)).toString(), emptyList(), today) }
    }

    @Test fun missingPaceEvidenceRejectsInventedBandAndDistanceDeadline() {
        val time = rest().put("goal_type", "TIME").put("effort", "EASY").put("duration_minutes", 15)
        assertEquals(SmartRunGoalType.TIME, SmartRunAiInput.parse(time.toString(), emptyList(), today).goalType)
        failure(AiFailure.Reason.RESPONSE) { SmartRunAiInput.parse(time.put("pace_min_seconds_per_km", 330)
            .put("pace_max_seconds_per_km", 360).toString(), emptyList(), today) }
        val mixed = rest().put("goal_type", "DISTANCE_TIME").put("effort", "EASY").put("duration_minutes", 15)
            .put("target_distance_km", 2).put("min_distance_km", 1.5).put("max_distance_km", 2.5)
        failure(AiFailure.Reason.RESPONSE) { SmartRunAiInput.parse(mixed.toString(), emptyList(), today) }
    }

    @Test fun malformedTruncatedAndFractionalPaceRepliesFailWithoutRetry() {
        failure(AiFailure.Reason.RESPONSE) { SmartRunAiInput.parse("{", emptyList(), today) }
        failure(AiFailure.Reason.RESPONSE) { SmartRunAiInput.parse(rest().put("pace_min_seconds_per_km", 300.5).toString(), emptyList(), today) }
        var calls = 0
        val provider = AnthropicProvider(AiTransport { _, _, _, _, _ -> calls++; AiHttpResponse(200,
            """{"stop_reason":"max_tokens","content":[{"type":"text","text":"{}"}]}""") })
        failure(AiFailure.Reason.RESPONSE) { SmartRunAiInput.request(provider, "fake-key", config, "English", JSONObject(), emptyList(), today) }
        assertEquals(1, calls)
    }

    @Test fun fingerprintIsStableForOrderingButChangesWithRelevantEffortGoalAndType() {
        val runs = listOf(run("one"), run("two", today.minusDays(3)))
        fun key(list: List<RunSession> = runs, effort: Int? = null, goal: String = "", type: ActivityType? = null) =
            SmartRunAiInput.key(SmartRunAiInput.build(today, list, { ActivityDetails(effort = effort, activityType = type) }, goal = RunnerGoal(goal)))
        val original = key()
        assertEquals(original, key(runs.reversed()))
        assertNotEquals(original, key(effort = 8)); assertNotEquals(original, key(goal = "Run a half marathon"))
        assertNotEquals(original, key(type = ActivityType.WALK)); assertNotEquals(original, key(list = emptyList()))
        assertTrue(original.matches(Regex("[0-9a-f]{64}")))
    }

    @Test fun setupFailureReleasesGateAndConcurrentActionsDoNotQueueCalls() {
        val gate = SmartRunRequestGate()
        try { gate.start { throw IllegalStateException("Synthetic setup failure") }; fail() }
        catch (_: IllegalStateException) { }
        var submissions = 0
        assertTrue(gate.start { submissions++ })
        assertFalse(gate.start { submissions++ }); assertEquals(1, submissions)
        gate.finished()
        assertTrue(gate.start { submissions++ }); assertEquals(2, submissions)
        gate.finished()
    }
}
