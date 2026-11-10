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
        assertFalse(request!!.has("output_config"))
        assertEquals("{\"distance\":5}", request!!.getJSONArray("messages").getJSONObject(0).getString("content"))
    }
    @Test fun structuredMessageAddsSchemaWithoutChangingGenericRequests() {
        val schema = JSONObject().put("type", "object").put("properties", JSONObject()
            .put("goal_type", JSONObject().put("type", "string")))
            .put("required", org.json.JSONArray(listOf("goal_type"))).put("additionalProperties", false)
        var calls = 0
        val provider = AnthropicProvider(AiTransport { method, path, key, body, timeout ->
            calls++; assertEquals("POST", method); assertEquals("/v1/messages", path)
            assertEquals("fake-key", key); assertEquals(60_000, timeout)
            val request = JSONObject(body!!)
            assertEquals("claude-sonnet-5-5", request.getString("model"))
            assertEquals(2048, request.getInt("max_tokens")); assertFalse(request.getBoolean("stream"))
            assertEquals("system", request.getString("system"))
            val format = request.getJSONObject("output_config").getJSONObject("format")
            assertEquals("json_schema", format.getString("type"))
            assertEquals(schema.toString(), format.getJSONObject("schema").toString())
            assertFalse(request.has("output_format")); assertFalse(request.has("tools"))
            AiHttpResponse(200, """{"stop_reason":"end_turn","content":[{"type":"thinking","thinking":"private"},{"type":"text","text":"{\"goal_type\":\"REST\"}"}]}""")
        })
        assertEquals("""{"goal_type":"REST"}""", provider.structuredMessage("fake-key", "claude-sonnet-5-5",
            "system", JSONObject().put("distance", 5), schema, 2048, 60_000))
        assertEquals(1, calls)
    }
    @Test fun structuredRefusalAndTruncationAreRejectedWithoutRetry() {
        for (reason in listOf("refusal", "max_tokens", "model_context_window_exceeded", "")) {
            var calls = 0
            val provider = AnthropicProvider(AiTransport { _, _, _, _, _ ->
                calls++
                AiHttpResponse(200, JSONObject().put("stop_reason", reason).put("content", org.json.JSONArray()
                    .put(JSONObject().put("type", "text").put("text", "{\"goal_type\":\"REST\"}"))).toString())
            })
            try { provider.structuredMessage("fake-key", "claude-sonnet-5-5", "", JSONObject(), JSONObject(), 500); fail() }
            catch (error: AiFailure) { assertEquals(AiFailure.Reason.RESPONSE, error.reason) }
            assertEquals(1, calls)
        }
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
    @Test fun unsafeLongOrStructuredSpeechIsDropped() {
        assertNull(AiRunCoach.cleanAdvice("x".repeat(LiveAiAdvice.MAX_CHARS + 1))); assertNull(AiRunCoach.cleanAdvice("{\"message\":\"hello\"}"))
        assertEquals("You have found a steady rhythm.", AiRunCoach.cleanAdvice("  You have found a steady rhythm.  "))
    }
}
