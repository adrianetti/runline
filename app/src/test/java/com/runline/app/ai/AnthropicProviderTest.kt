package com.runline.app.ai

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AnthropicProviderTest {
    @Test fun messageUsesStructuredDataAndOnlyTextBlocks() {
        var request: JSONObject? = null
        val provider = AnthropicProvider(AiTransport { method, path, key, body, _ ->
            assertEquals("POST", method); assertEquals("/v1/messages", path); assertEquals("test-key", key)
            request = JSONObject(body!!)
            AiHttpResponse(200, """{"stop_reason":"end_turn","content":[{"type":"thinking","thinking":"private"},{"type":"text","text":"A useful review."}]}""")
        })
        assertEquals("A useful review.", provider.message("test-key", "claude-haiku-5-5", "system", JSONObject().put("distance", 5), 500))
        assertEquals(500, request!!.getInt("max_tokens")); assertFalse(request!!.getBoolean("stream"))
        assertEquals("{\"distance\":5}", request!!.getJSONArray("messages").getJSONObject(0).getString("content"))
    }
    @Test fun errorsNeverEchoServerBodyOrKey() {
        for ((status, reason) in listOf(401 to AiFailure.Reason.KEY, 429 to AiFailure.Reason.RATE_LIMIT, 500 to AiFailure.Reason.NETWORK)) {
            try { AnthropicProvider(AiTransport { _, _, _, _, _ -> AiHttpResponse(status, "secret-server-body") }).checkModel("secret-key", "claude-haiku-5-5"); fail() }
            catch (e: AiFailure) { assertEquals(reason, e.reason); assertFalse(e.toString().contains("secret")) }
        }
    }
    @Test fun truncatedAndEmptyResponsesAreRejected() {
        for (body in listOf("""{"stop_reason":"max_tokens","content":[{"type":"text","text":"partial"}]}""", """{"content":[]}""")) {
            try { AnthropicProvider(AiTransport { _, _, _, _, _ -> AiHttpResponse(200, body) }).message("key", "claude-haiku-5-5", "", JSONObject(), 500); fail() }
            catch (e: AiFailure) { assertEquals(AiFailure.Reason.RESPONSE, e.reason) }
        }
    }
    @Test fun scheduleDoesNotCatchUpOrExceed24Calls() {
        val schedule = LiveAiSchedule()
        assertFalse(schedule.due(60_000, 3)); assertFalse(schedule.due(239_999, 3)); assertTrue(schedule.due(240_000, 3))
        assertFalse(schedule.due(240_001, 3))
        repeat(23) { assertTrue(schedule.due(500_000L + it * 200_000L, 3)) }
        assertFalse(schedule.due(Long.MAX_VALUE / 2, 3))
    }
    @Test fun unsafeLongOrStructuredSpeechIsDropped() {
        assertNull(AiRunCoach.cleanAdvice("x".repeat(221))); assertNull(AiRunCoach.cleanAdvice("{\"message\":\"hello\"}"))
        assertEquals("You have found a steady rhythm.", AiRunCoach.cleanAdvice("  You have found a steady rhythm.  "))
    }
}
