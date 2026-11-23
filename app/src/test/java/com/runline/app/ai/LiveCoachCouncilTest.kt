package com.runline.app.ai

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LiveCoachCouncilTest {
    @get:Rule val folder = TemporaryFolder()

    private fun result(id: String) =
        JSONObject()
            .put("id", id)
            .put("language", "Spanish")
            .put("date", "2026-11-19")
            .put("minutes", 20.0)
            .put("distance_km", 3.2)
            .put("goal", JSONObject().put("type", "DISTANCE").put("km", 3))
            .put("summary", "Easy 3 km")
            .put("tags", JSONArray(listOf("beginner", "slow")))
            .put("final_facts", JSONObject().put("average_pace", "6:15"))
            .put("latencies_ms", JSONArray(listOf(800, 1200, 3000)))
            .put(
                "usage",
                JSONObject()
                    .put("model", "claude-haiku-5-5")
                    .put("requests", 3)
                    .put("input_tokens", 9000)
                    .put("output_tokens", 300)
                    .put("cost_usd", 0.001),
            ).put(
                "lines",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("at_s", 42)
                            .put("source", "AI")
                            .put("kind", "CADENCE:OPENING")
                            .put("text", "¡Vamos!"),
                    ).put(
                        JSONObject()
                            .put("at_s", 402)
                            .put("source", "LOCAL")
                            .put("kind", "Kilometre")
                            .put("text", "1 kilómetro."),
                    ),
            )

    private fun verdict(
        overall: Int,
        keep: Boolean,
    ) = JSONObject()
        .put("helped", 6)
        .put("trust", 8)
        .put("motivation", 7)
        .put("variety", 5)
        .put("timing", 6)
        .put("keep_on", keep)
        .put("overall", overall)
        .put("best_line", 0)
        .put("worst_line", 1)
        .put("annoyances", JSONArray())
        .put("fix", "Say more about the run.")

    private fun fake(
        status: Int,
        reply: JSONObject,
    ): ((AiRequestMetric) -> Unit) -> CoachAiProvider =
        { onMetric ->
            AnthropicProvider(
                AiTransport { _, _, _, _, _ ->
                    val body =
                        JSONObject()
                            .put("model", "claude-sonnet-5-5")
                            .put("stop_reason", "end_turn")
                            .put("usage", JSONObject().put("input_tokens", 5000).put("output_tokens", 400))
                            .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", reply.toString())))
                    AiHttpResponse(status, body.toString())
                },
                onMetric,
            )
        }

    @Test fun `a council member's verdict is saved with what it cost, and a failed call goes to the errors file instead`() {
        val out = folder.newFolder("v1")
        val persona = CouncilBrief.PERSONAS.first()
        CouncilJudge("k".repeat(20), "claude-sonnet-5-5", fake(200, verdict(55, true))).grade(out, result("s1"), persona)
        val saved = JSONObject(File(out, "s1.council.${persona.id}.json").readText())
        assertEquals(55, saved.getInt("overall"))
        assertEquals(5000L, saved.getJSONObject("usage").getLong("input_tokens"))
        assertTrue(saved.getJSONObject("usage").getDouble("cost_usd") > 0)
        CouncilJudge("k".repeat(20), "claude-sonnet-5-5", fake(429, JSONObject())).grade(out, result("s2"), persona)
        assertFalse(File(out, "s2.council.${persona.id}.json").exists())
        assertEquals("RATE_LIMIT", JSONObject(File(out, "errors.jsonl").readLines().single()).getString("class"))
    }

    @Test fun `each run becomes one row with the council's means, the line judge's grades and the full cost`() {
        val out = folder.newFolder("v2")
        File(out, "s1.json").writeText(result("s1").toString())
        val council = listOf(verdict(40, true), verdict(60, false))
        council.forEachIndexed { i, v ->
            val usage =
                JSONObject()
                    .put("model", "claude-sonnet-5-5")
                    .put("input_tokens", 5000)
                    .put("output_tokens", 400)
                    .put("cost_usd", 0.014)
            File(out, "s1.council.${CouncilBrief.PERSONAS[i].id}.json").writeText(v.put("usage", usage).toString())
        }
        val lines =
            JSONArray()
                .put(
                    JSONObject()
                        .put("accurate", true)
                        .put("clear", true)
                        .put("specific", 2)
                        .put("value", 2)
                        .put("voice", 2)
                        .put("fresh", 2)
                        .put("asks_reply", false),
                ).put(
                    JSONObject()
                        .put("accurate", false)
                        .put("clear", true)
                        .put("specific", 2)
                        .put("value", 2)
                        .put("voice", 2)
                        .put("fresh", 2)
                        .put("asks_reply", false),
                )
        File(out, "s1.grade.0.json").writeText(JSONObject().put("coach_feel", 52).put("lines", lines).toString())
        File(out, "s2.json").writeText(result("s2").toString())
        CouncilRows.write(out)
        val row = JSONObject(File(out, "results.jsonl").readLines().single())
        val grade = row.getJSONObject("grade")
        assertEquals("s1", row.getString("prompt_id"))
        assertEquals(50.0, grade.getDouble("council"), 1e-9)
        assertEquals(50.0, grade.getDouble("keep"), 1e-9)
        assertEquals(5.0, grade.getDouble("line"), 1e-9)
        assertEquals(1.0, grade.getDouble("wrong"), 1e-9)
        assertEquals(52.0, grade.getDouble("coach_feel"), 1e-9)
        assertEquals(1.0, grade.getDouble("lines_per_10min"), 1e-9)
        assertEquals(0.029, row.getDouble("cost_usd"), 1e-9)
        assertEquals(1.2, row.getDouble("latency_s"), 1e-9)
        assertEquals("beginner", row.getJSONArray("tags").getString(0))
        assertTrue(File(out, "traces/s1_rep0.json").readText().contains("¡Vamos!"))
    }

    @Test fun `the council sees each line once, numbered, with who said it`() {
        val input = CouncilBrief.input(result("s1"))
        val timeline = input.getJSONArray("timeline")
        assertEquals(2, timeline.length())
        assertEquals("COACH", timeline.getJSONObject(0).getString("spoken_by"))
        assertEquals("6:42", timeline.getJSONObject(1).getString("minute"))
        assertEquals("Easy 3 km", input.getJSONObject("run_facts").getString("run_summary"))
        assertTrue(CouncilBrief.SYSTEM.contains("{PROFILE}"))
        assertEquals(
            5,
            CouncilBrief.PERSONAS
                .map { it.id }
                .distinct()
                .size,
        )
    }
}
