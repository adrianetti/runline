package com.runline.app.ai

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LiveCoachPlanTest {
    private fun tick(plan: LiveCoachPlan, fromMs: Long, toMs: Long, interval: Int = 2): List<Pair<Long, LiveCall>> =
        (fromMs..toMs step 1_000).mapNotNull { at -> plan.next(at, interval)?.let { at to it } }

    @Test fun `first line comes within the first minute, not after a full interval`() {
        val plan = LiveCoachPlan()
        assertNull(plan.next(10_000, 5))
        val calls = tick(plan, 11_000, 60_000, 5)
        assertEquals(1, calls.size); assertEquals(LiveTrigger.OPENING, calls[0].second.trigger); assertEquals(40_000, calls[0].first)
    }

    @Test fun `a recovered recording skips the welcome`() {
        val plan = LiveCoachPlan()
        val calls = tick(plan, 600_000, 700_000)
        assertEquals(LiveTrigger.CADENCE, calls.first().second.trigger)
        assertTrue(calls.none { it.second.trigger == LiveTrigger.OPENING })
    }

    @Test fun `a spoken kilometre gets its own follow-up at once`() {
        val plan = LiveCoachPlan()
        tick(plan, 0, 45_000).also { plan.answered(41_000, true); plan.spoken(45_000, ai = true, moment = null) }
        plan.spoken(100_000, ai = false, moment = LiveMoment.of("Kilometre(completed=1, averagePaceSeconds=464.1)"))
        val call = plan.next(100_500, 2)!!
        assertEquals(LiveTrigger.MOMENT, call.trigger); assertEquals(LiveMoment.KILOMETRE, call.moment); assertEquals(LiveFocus.SPLIT_DETAIL, call.focus)
        // A local line that is not a milestone (pace, terrain, route) does not trigger one.
        plan.answered(102_000, true)
        plan.spoken(130_000, ai = false, moment = LiveMoment.of("SlowerThanTarget"))
        assertNull(plan.next(131_000, 2))
    }

    @Test fun `a milestone waits out the minimum gap and expires rather than arriving late`() {
        val plan = LiveCoachPlan()
        tick(plan, 0, 40_000)
        plan.spoken(45_000, ai = false, moment = LiveMoment.HALFWAY)
        assertNull(plan.next(50_000, 2))                       // 10 s after the opening request
        assertEquals(LiveMoment.HALFWAY, plan.next(60_000, 2)?.moment)
        plan.spoken(200_000, ai = false, moment = LiveMoment.KILOMETRE)
        assertNotEquals(LiveTrigger.MOMENT, plan.next(230_000, 2)?.trigger)
    }

    @Test fun `a lap waits for the kilometre that usually follows it, so both get one follow-up`() {
        val plan = LiveCoachPlan()
        plan.next(40_000, 2); plan.answered(41_000, true); plan.spoken(48_000, ai = true, moment = null)
        plan.spoken(300_000, ai = false, moment = LiveMoment.LAP)
        assertNull(plan.next(301_000, 2)); assertNull(plan.next(325_000, 2))
        plan.spoken(328_000, ai = false, moment = LiveMoment.KILOMETRE)
        assertEquals(LiveMoment.KILOMETRE, plan.next(329_000, 2)?.moment)
        assertNull(plan.next(360_000, 2))
        // A lap with no kilometre close by still gets its line, a little later.
        plan.answered(330_000, true); plan.spoken(338_000, ai = true, moment = null)
        plan.spoken(500_000, ai = false, moment = LiveMoment.LAP)
        assertNull(plan.next(539_000, 2)); assertEquals(LiveMoment.LAP, plan.next(540_000, 2)?.moment)
    }

    @Test fun `an echo of an earlier line is caught and asked for again once`() {
        val earlier = listOf("Vas tranquilo y con buen ritmo.", "¡Ese kilómetro te ha salido redondo, casi sin enterarte!")
        assertNotNull(LiveAiAdvice.repeats("Vas genial, sigue.", earlier))
        assertNotNull(LiveAiAdvice.repeats("Otro que te ha salido redondo, qué bien.", earlier))
        assertNull(LiveAiAdvice.repeats("Hombros sueltos y manos blandas.", earlier))
        assertNull(LiveAiAdvice.repeats("7 minutos 41 por kilómetro, clavado.", listOf("7 minutos 41 por kilómetro llevas de media.")))
        val replies = ArrayDeque(listOf("Vas muy bien, de verdad.", "Hombros sueltos y a disfrutar del parque."))
        val sent = mutableListOf<JSONObject>()
        val provider = AnthropicProvider(AiTransport { _, _, _, body, _ ->
            sent += JSONObject(JSONObject(body!!).getJSONArray("messages").getJSONObject(0).getString("content"))
            AiHttpResponse(200, JSONObject().put("stop_reason", "end_turn").put("content", JSONArray().put(JSONObject().put("type", "text").put("text", replies.removeFirst()))).toString())
        })
        val data = JSONObject().put("run", JSONObject()).put("coaching_context", JSONObject().put("recent_spoken_feedback",
            JSONArray().put(JSONObject().put("text", earlier[0]).put("source", "AI").put("seconds_ago", 120))))
        assertEquals("Hombros sueltos y a disfrutar del parque.", LiveAiAdvice.request(provider, "fake-key", "claude-haiku-5-5", "Spanish", data))
        assertEquals(2, sent.size); assertFalse(sent[0].has("rejected_draft"))
        assertEquals("Vas muy bien, de verdad.", sent[1].getJSONObject("rejected_draft").getString("text"))
        assertTrue(sent[0].getJSONObject("avoid").getJSONArray("openings_already_used").toString().contains("vas tranquilo"))
    }

    @Test fun `an answer that runs long keeps the sentences that fit`() {
        val long = "Qué bien vas hoy. " + "palabra ".repeat(LiveAiAdvice.MAX_WORDS).trim() + "."
        assertEquals("Qué bien vas hoy.", LiveAiAdvice.clean(long, spanish = true))
    }

    @Test fun `check-ins follow the chosen quiet time after the coach last spoke and rotate their angle`() {
        val plan = LiveCoachPlan()
        var at = 40_000L
        val focuses = mutableListOf<LiveFocus>()
        plan.next(at, 2); plan.answered(at + 1_000, true); plan.spoken(at + 9_000, ai = true, moment = null)
        repeat(8) {
            val spokeAt = at + 9_000
            assertNull(plan.next(spokeAt + 119_000, 2))
            at = spokeAt + 120_000
            val call = plan.next(at, 2)!!
            assertEquals(LiveTrigger.CADENCE, call.trigger); focuses += call.focus
            plan.answered(at + 1_000, true); plan.spoken(at + 9_000, ai = true, moment = null)
        }
        assertEquals(LiveFocus.entries.toList(), focuses)
    }

    @Test fun `a check-in does not start while something was just said`() {
        val plan = LiveCoachPlan()
        plan.next(40_000, 1); plan.answered(41_000, true); plan.spoken(48_000, ai = true, moment = null)
        plan.spoken(100_000, ai = false, moment = null)
        assertNull(plan.next(110_000, 1))
        assertNotNull(plan.next(120_000, 1))
    }

    @Test fun `silence is retried soon, failures back off, and the local coach takes over`() {
        val plan = LiveCoachPlan()
        plan.next(40_000, 5); plan.answered(41_000, spoke = false)
        assertNull(plan.next(80_000, 5)); assertNotNull(plan.next(86_000, 5))
        assertTrue(plan.healthy)
        plan.failed(87_000); assertTrue(plan.healthy)
        assertNull(plan.next(110_000, 5)); assertNotNull(plan.next(117_000, 5))
        plan.failed(118_000); assertFalse(plan.healthy)
        assertNull(plan.next(170_000, 5)); assertNotNull(plan.next(178_000, 5))
        plan.answered(179_000, true); assertTrue(plan.healthy)
    }

    @Test fun `requests stop at the per-activity ceiling`() {
        val plan = LiveCoachPlan(LiveCoachPlan.MAX_ATTEMPTS - 1)
        assertEquals(1, tick(plan, 0, 3_600_000, 1).size)
        assertFalse(plan.healthy)
        assertEquals(LiveCoachPlan.MAX_ATTEMPTS, LiveCoachPlan(10_000).attempts)
    }

    @Test fun `a 23 minute run with kilometres every seven and a half minutes hears about a dozen live lines`() {
        val plan = LiveCoachPlan(); var lines = 0
        (0..1_397_000L step 1_000).forEach { at ->
            if (at > 0 && at % 450_000 == 0L) plan.spoken(at, ai = false, moment = LiveMoment.KILOMETRE)
            plan.next(at, 2)?.let { plan.answered(at + 1_000, true); plan.spoken(at + 9_000, ai = true, moment = null); lines++ }
        }
        assertTrue("$lines", lines in 11..14)
    }

    @Test fun `facts are worked out as speakable times with faster as negative`() {
        val run = JSONObject().put("activity_type", "run").put("moving_seconds", 1012.2).put("distance_km", 2.2056)
            .put("average_pace_seconds_per_km", 458.9).put("recent_pace_seconds_per_km", 414.7)
            .put("recent_pace_comparison", JSONObject().put("trend", "FASTER").put("recent_pace_seconds_per_km", 414.7).put("previous_pace_seconds_per_km", 448.0))
            .put("splits", JSONArray().put(split(1, 1000.0, 466.3)).put(split(2, 1000.0, 460.9)).put(split(3, 205.6, 85.0)))
            .put("estimated_repeated_laps", JSONArray().put(JSONObject().put("duration_seconds", 450)).put(JSONObject().put("duration_seconds", 435)))
        val context = JSONObject().put("today", "2026-11-10")
            .put("active_goal", JSONObject().put("type", "TIME").put("duration_goal_seconds", 1800).put("intended_effort", "EASY"))
            .put("recent_runs", JSONArray()
                .put(JSONObject().put("date", "2026-11-08").put("activity_type", "run").put("distance_km", 6.0).put("average_pace_seconds_per_km", 440.0).put("self_reported_effort_1_to_10", 7))
                .put(JSONObject().put("date", "2026-11-06").put("activity_type", "walk").put("distance_km", 9.0).put("average_pace_seconds_per_km", 700.0))
                .put(JSONObject().put("date", "2026-10-01").put("activity_type", "run").put("distance_km", 12.0).put("average_pace_seconds_per_km", 470.0)))
        val facts = LiveFacts.of(run, context)
        assertEquals("16:52", facts.getString("elapsed")); assertEquals(2.21, facts.getDouble("distance_km"), 1e-9)
        assertEquals("7:39", facts.getString("average_pace")); assertEquals("6:55", facts.getString("pace_last_2_minutes"))
        assertEquals(-33, facts.getJSONObject("last_2_minutes_vs_the_2_before").getInt("change_seconds_per_km"))
        // The partial third kilometre is not a split.
        assertEquals("""["7:46","7:41"]""", facts.getJSONArray("kilometre_splits").toString())
        assertEquals(-5, facts.getInt("last_kilometre_vs_previous_seconds")); assertEquals(2, facts.getJSONObject("fastest_kilometre").getInt("number"))
        assertEquals(-15, facts.getInt("last_lap_vs_previous_seconds"))
        val goal = facts.getJSONObject("goal")
        assertEquals("13:08", goal.getString("remaining_time")); assertEquals(56, goal.getInt("progress_percent"))
        assertEquals(3.92, goal.getDouble("projected_distance_km_at_average_pace"), 1e-9); assertEquals("EASY", goal.getString("intended_effort"))
        val history = facts.getJSONObject("history")
        assertEquals(2, history.getJSONObject("previous_run").getInt("days_ago")); assertEquals("7:20", history.getJSONObject("previous_run").getString("average_pace"))
        assertEquals(19, history.getInt("average_pace_vs_previous_run_seconds_per_km"))
        assertEquals(1, history.getInt("runs_last_7_days_before_this")); assertEquals(8.21, history.getDouble("km_last_7_days_including_this_run"), 1e-9)
        assertEquals(6.0, history.getDouble("longest_run_last_30_days_km"), 1e-9); assertFalse(history.has("this_is_now_the_longest_run_in_30_days"))
        // No goal and no history: those sections are simply absent, never invented.
        val bare = LiveFacts.of(run, JSONObject().put("today", "2026-11-10"))
        assertFalse(bare.has("goal")); assertFalse(bare.has("history"))
        assertEquals("1:02:05", LiveFacts.clock(3725.0))
    }
    private fun split(number: Int, meters: Double, seconds: Double) = JSONObject().put("number", number).put("distance_m", meters)
        .put("duration_seconds", seconds).put("pace_seconds_per_km", seconds / meters * 1000).put("is_full_kilometre", meters >= 999.999)

    @Test fun `the request asks for a quick answer and carries the call, the facts and what was said`() {
        var body = JSONObject()
        val provider = AnthropicProvider(AiTransport { _, _, _, sent, timeout ->
            body = JSONObject(sent!!); assertEquals(12_000, timeout)
            AiHttpResponse(200, """{"stop_reason":"end_turn","content":[{"type":"text","text":"¡Vas a 7:21/km, el más rápido de hoy!"}]}""")
        })
        val data = JSONObject().put("run", JSONObject().put("distance_km", 3.0).put("moving_seconds", 1380))
            .put("coaching_context", JSONObject().put("today", "2026-11-10").put("recent_spoken_feedback",
                JSONArray().put(JSONObject().put("text", "3 kilómetros completados.").put("source", "LOCAL").put("seconds_ago", 4))))
        val line = LiveAiAdvice.request(provider, "fake-key", "claude-haiku-5-5", "Spanish", data,
            LiveCall(LiveTrigger.MOMENT, LiveMoment.KILOMETRE, LiveFocus.SPLIT_DETAIL, 7))
        assertEquals("¡Vas a 7 minutos 21 por kilómetro, el más rápido de hoy!", line)
        assertEquals("disabled", body.getJSONObject("thinking").getString("type"))
        assertEquals("medium", body.getJSONObject("output_config").getString("effort"))
        assertFalse(body.has("temperature"))
        val input = JSONObject(body.getJSONArray("messages").getJSONObject(0).getString("content"))
        assertEquals("KILOMETRE", input.getJSONObject("call").getString("moment")); assertEquals("MOMENT", input.getJSONObject("call").getString("trigger"))
        assertEquals("23:00", input.getJSONObject("facts").getString("elapsed"))
        assertEquals("3 kilómetros completados.", input.getJSONArray("already_said").getJSONObject(0).getString("text"))
        assertTrue(body.getString("system").contains("Spanish")); assertFalse(input.getJSONObject("call").has("number")); assertTrue(body.getString("system").contains("7 minutos 41 por kilómetro"))
    }

    @Test fun `a custom model that rejects the quick options is asked again plainly`() {
        val bodies = mutableListOf<JSONObject>()
        val provider = AnthropicProvider(AiTransport { _, _, _, sent, _ ->
            bodies += JSONObject(sent!!)
            if (bodies.size == 1) AiHttpResponse(400, """{"error":{"type":"invalid_request_error"}}""")
            else AiHttpResponse(200, """{"stop_reason":"end_turn","content":[{"type":"text","text":"Lovely steady work, keep those shoulders loose."}]}""")
        })
        assertEquals("Lovely steady work, keep those shoulders loose.", provider.quickMessage("fake-key", "claude-custom-1", "s", JSONObject(), 400))
        assertTrue(bodies[0].has("thinking")); assertFalse(bodies[1].has("thinking")); assertFalse(bodies[1].has("output_config"))
        assertEquals(1536, bodies[1].getInt("max_tokens"))
    }

    @Test fun `written paces and units become spoken ones, and markup never reaches the voice`() {
        assertEquals("Vas a 7 minutos 5 por kilómetro, genial.", LiveAiAdvice.clean("Vas a 7:05/km, **genial**.", spanish = true))
        assertEquals("That one took 7 minutes 41, and 2.2 kilometres are done.", LiveAiAdvice.clean("That one took 7:41, and 2.2 km are done."))
        assertEquals("Ritmo de 6 minutos 30 por kilómetro.", LiveAiAdvice.clean("Ritmo de 6:30 por kilómetro.", spanish = true))
        assertNull(LiveAiAdvice.clean("NONE")); assertNull(LiveAiAdvice.clean("NONE. No pace yet."))
        assertNull(LiveAiAdvice.clean("word ".repeat(LiveAiAdvice.MAX_WORDS + 1))); assertNull(LiveAiAdvice.clean("See http://example.com now"))
        assertTrue(LiveAiAdvice.mentionsTerrain("Suelta las piernas en la bajada.")); assertFalse(LiveAiAdvice.mentionsTerrain("Hombros relajados y respira."))
    }

    @Test fun `milestones are recognised from the spoken cue name`() {
        assertEquals(LiveMoment.LAP, LiveMoment.of("LapCompleted(number=2, comparison=null)"))
        assertEquals(LiveMoment.HALFWAY, LiveMoment.of("Encouragement(kind=HALFWAY, facts=CoachFacts(distanceMeters=2205.6))"))
        assertEquals(LiveMoment.GOAL_REACHED, LiveMoment.of("GoalReached(withinTime=null, facts=null)"))
        assertEquals(LiveMoment.ONE_KM_LEFT, LiveMoment.of("OneKilometreRemaining"))
        assertNull(LiveMoment.of("Encouragement(kind=PROGRESS, facts=null)")); assertNull(LiveMoment.of("route-turn-120-now")); assertNull(LiveMoment.of("advice"))
    }
}
