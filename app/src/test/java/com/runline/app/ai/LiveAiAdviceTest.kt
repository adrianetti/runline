package com.runline.app.ai

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LiveAiAdviceTest {
    private fun provider(vararg replies: String, onBody: (JSONObject) -> Unit = {}): AnthropicProvider {
        val queue = ArrayDeque(replies.toList())
        return AnthropicProvider(AiTransport { _, _, _, sent, _ ->
            onBody(JSONObject(sent!!))
            AiHttpResponse(200, JSONObject().put("stop_reason", "end_turn").put("content", JSONArray().put(JSONObject().put("type", "text").put("text", queue.removeFirst()))).toString())
        })
    }
    private val run = JSONObject().put("activity_type", "run").put("moving_seconds", 1380).put("distance_km", 3.02).put("average_pace_seconds_per_km", 457.0)
        .put("splits", JSONArray(listOf(466.3, 460.9, 441.6).mapIndexed { i, s -> JSONObject().put("number", i + 1).put("pace_seconds_per_km", s).put("is_full_kilometre", true) }))
    private fun data(vararg said: Pair<String, String>) = JSONObject().put("run", run).put("coaching_context", JSONObject().put("today", "2026-11-10")
        .put("runner_profile", JSONObject().put("self_reported_health_notes", "PRIVATE"))
        .put("recent_spoken_feedback", JSONArray(said.map { JSONObject().put("text", it.second).put("source", it.first).put("seconds_ago", 60) })))
    private val kilometre = LiveCall(LiveTrigger.MOMENT, LiveMoment.KILOMETRE, LiveFocus.SPLIT_DETAIL, 7)
    private val third = LiveMilestone.of(com.runline.app.core.CoachCue.Kilometre(3, 457.0, splitPaceSeconds = 441.6, previousSplitPaceSeconds = 460.9))

    @Test fun `only the chosen observation leaves the phone, and a milestone is asked for instantly`() {
        val bodies = mutableListOf<JSONObject>()
        val line = LiveAiAdvice.compose(provider("¡Tercer kilómetro en 7 minutos 22, y cada uno más rápido que el anterior!") { bodies += it }, "fake-key", "claude-haiku-5-5", "Spanish",
            data("LOCAL" to "Ritmo estable."), kilometre, milestone = third)!!
        assertEquals("moment/km:3", line.pointId); assertEquals("¡Tercer kilómetro en 7 minutos 22, y cada uno más rápido que el anterior!", line.text)
        val body = bodies.single()
        assertEquals("disabled", body.getJSONObject("thinking").getString("type")); assertEquals("medium", body.getJSONObject("output_config").getString("effort"))
        assertEquals(400, body.getInt("max_tokens")); assertFalse(body.has("temperature"))
        val input = JSONObject(body.getJSONArray("messages").getJSONObject(0).getString("content"))
        assertEquals(setOf("why_now", "point", "numbers", "hint", "intended_effort", "already_said", "avoid"), input.keys().asSequence().toSet())
        assertEquals("7 minutos 22", input.getJSONObject("numbers").getString("time_of_this_kilometre"))
        assertEquals("KILOMETRE", input.getJSONObject("why_now").getString("moment"))
        assertEquals("Ritmo estable.", input.getJSONArray("already_said").getJSONObject(0).getString("text"))
        assertFalse(body.toString().contains("PRIVATE")); assertFalse(body.toString().contains("splits"))
        assertTrue(body.getString("system").contains("Spanish as spoken in Spain"))
    }

    @Test fun `a check-in is given a moment to think`() {
        val bodies = mutableListOf<JSONObject>()
        val line = LiveAiAdvice.compose(provider("Your fastest kilometre today is the third, at 7 minutes 22.") { bodies += it }, "fake-key", "claude-haiku-5-5", "English",
            data(), LiveCall(LiveTrigger.CADENCE, null, LiveFocus.MORALE, 4))!!
        assertEquals("best/km:3", line.pointId)
        assertFalse(bodies.single().has("thinking")); assertEquals("low", bodies.single().getJSONObject("output_config").getString("effort")); assertEquals(2500, bodies.single().getInt("max_tokens"))
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

    @Test fun `a figure the coach was not given is never spoken`() {
        val sent = mutableListOf<JSONObject>()
        val fixed = LiveAiAdvice.compose(provider("¡Tercer kilómetro en 7 minutos 12, el mejor!", "¡Tercer kilómetro en 7 minutos 22, qué bien!") {
            sent += JSONObject(it.getJSONArray("messages").getJSONObject(0).getString("content")) }, "fake-key", "claude-haiku-5-5", "Spanish", data(), kilometre, milestone = third)!!
        assertEquals("¡Tercer kilómetro en 7 minutos 22, qué bien!", fixed.text)
        assertFalse(sent[0].has("rejected_draft")); assertTrue(sent[1].getJSONObject("rejected_draft").getString("problem").contains("12"))
        assertNull(LiveAiAdvice.compose(provider("Vas a 452 por kilómetro.", "Llevas 9 metros."), "fake-key", "claude-haiku-5-5", "Spanish", data(), kilometre, milestone = third))
        val point = LivePoint("x/y", "Kilometre 3 is done.", mapOf("time" to "7 minutos 22"))
        assertNull(LiveAiAdvice.figures("Kilómetro 3 en 7 minutos 22.", point)); assertNotNull(LiveAiAdvice.figures("Kilómetro 3 en 7 minutos 23.", point))
        assertNull(LiveAiAdvice.figures("Hombros sueltos.", point))
    }

    @Test fun `wrong language, questions for the runner, leaked instructions and gendered address are sent back`() {
        assertNotNull(LiveAiAdvice.language("Lap ten's the fastest of the loop, so ask how the legs feel.", spanish = true))
        assertNull(LiveAiAdvice.language("Hombros sueltos y a disfrutar del parque.", spanish = true))
        assertNotNull(LiveAiAdvice.language("Hombros sueltos y a disfrutar del parque, que ya lo tienes.", spanish = false))
        assertNotNull(LiveAiAdvice.asks("¿Y qué tal esas piernas? Dime.")); assertNotNull(LiveAiAdvice.asks("Tell me how that felt."))
        assertNull(LiveAiAdvice.asks("¿Podrías decir una frase entera sin quedarte sin aire?"))
        assertNotNull(LiveAiAdvice.leaks("Siente ese empuje. Usa solo las cifras permitidas: 7 minutos 2.")); assertNull(LiveAiAdvice.leaks("Pasos cortos y ligeros."))
        assertNotNull(LiveAiAdvice.gendered("¡Meta conseguida, campeón!")); assertNotNull(LiveAiAdvice.gendered("Ese cambio es normal, tío."))
        assertNull(LiveAiAdvice.gendered("¡Meta conseguida! Qué carrera."))
        // Sent back once; a second flawed answer is dropped rather than spoken.
        assertEquals("Hombros sueltos y a disfrutar.", LiveAiAdvice.compose(provider("Dime cómo vas.", "Hombros sueltos y a disfrutar."), "k".repeat(20), "claude-haiku-5-5", "Spanish",
            data(), LiveCall(LiveTrigger.CADENCE, null, LiveFocus.TECHNIQUE, 2))?.text)
        assertNull(LiveAiAdvice.compose(provider("Dime cómo vas.", "Cuéntame qué tal."), "k".repeat(20), "claude-haiku-5-5", "Spanish", data(), LiveCall(LiveTrigger.CADENCE, null, LiveFocus.TECHNIQUE, 2)))
    }

    @Test fun `an echo of an earlier line is asked for again once, then allowed`() {
        val earlier = listOf("Vas tranquilo y con buen ritmo.", "¡Ese kilómetro te ha salido redondo, casi sin enterarte!")
        assertNotNull(LiveAiAdvice.repeats("Vas genial, sigue.", earlier))
        assertNotNull(LiveAiAdvice.repeats("Otro que te ha salido redondo, qué bien.", earlier))
        assertNull(LiveAiAdvice.repeats("Hombros sueltos y manos blandas.", earlier))
        assertNull(LiveAiAdvice.repeats("7 minutos 41 por kilómetro, clavado.", listOf("7 minutos 41 por kilómetro llevas de media.")))
        val call = LiveCall(LiveTrigger.CADENCE, null, LiveFocus.TECHNIQUE, 2)
        assertEquals("Hombros sueltos y a disfrutar del parque.", LiveAiAdvice.compose(provider("Vas muy bien, de verdad.", "Hombros sueltos y a disfrutar del parque."), "fake-key", "claude-haiku-5-5", "Spanish",
            data("AI" to earlier[0]), call)?.text)
        assertEquals("Vas con los hombros sueltos.", LiveAiAdvice.compose(provider("Vas muy bien, de verdad.", "Vas con los hombros sueltos."), "fake-key", "claude-haiku-5-5", "Spanish",
            data("AI" to earlier[0]), call)?.text)
        val avoid = LiveAiAdvice.avoid(data("AI" to "Vas tranquilo, sin prisa.", "AI" to "Qué tranquila va la mañana.", "LOCAL" to "1 kilómetro completado.").getJSONObject("coaching_context").getJSONArray("recent_spoken_feedback"))
        assertTrue(avoid.getJSONArray("openings_already_used").toString().contains("vas tranquilo")); assertTrue(avoid.getBoolean("effort_level_mentioned_recently"))
        assertEquals("""["tranquilo"]""", avoid.getJSONArray("words_already_overused").toString())
    }

    @Test fun `the sentence shape follows what is being said`() {
        val call = LiveCall(LiveTrigger.CADENCE, null, LiveFocus.MORALE, 0)
        assertEquals(LiveStyle.CELEBRATE, LiveAiAdvice.style(LivePoint("moment/goal", ""), call))
        assertTrue(LiveAiAdvice.style(LivePoint("slower/pace:1", ""), call) in setOf(LiveStyle.WARM, LiveStyle.CALM, LiveStyle.CUE_FIRST))
        assertEquals(3, (0..5).map { LiveAiAdvice.style(LivePoint("faster/surge", ""), call.copy(number = it)) }.toSet().size)
    }

    @Test fun `written paces and units become spoken ones, and markup never reaches the voice`() {
        assertEquals("Vas a 7 minutos 5 por kilómetro, genial.", LiveAiAdvice.clean("Vas a 7:05/km, **genial**.", spanish = true))
        assertEquals("That one took 7 minutes 41, and 2.2 kilometres are done.", LiveAiAdvice.clean("That one took 7:41, and 2.2 km are done."))
        assertEquals("Ritmo de 6 minutos 30 por kilómetro.", LiveAiAdvice.clean("Ritmo de 6:30 por kilómetro.", spanish = true))
        assertNull(LiveAiAdvice.clean("NONE")); assertNull(LiveAiAdvice.clean("NONE. No pace yet."))
        assertNull(LiveAiAdvice.clean("word ".repeat(LiveAiAdvice.MAX_WORDS + 1))); assertNull(LiveAiAdvice.clean("See http://example.com now"))
        assertEquals("Qué bien vas hoy.", LiveAiAdvice.clean("Qué bien vas hoy. " + "palabra ".repeat(LiveAiAdvice.MAX_WORDS).trim() + ".", spanish = true))
        assertTrue(LiveAiAdvice.mentionsTerrain("Suelta las piernas en la bajada.")); assertFalse(LiveAiAdvice.mentionsTerrain("Hombros relajados y respira."))
    }
}
