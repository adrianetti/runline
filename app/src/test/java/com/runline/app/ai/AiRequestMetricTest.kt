package com.runline.app.ai

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiRequestMetricTest {
    @Test fun exactReportedUsageAndKnownPricesExcludePromptBodies() {
        val response = AiHttpResponse(200, """{"usage":{"input_tokens":1000,"output_tokens":100,"cache_read_input_tokens":500,"cache_creation_input_tokens":0}}""")
        val m = AiRequestMetric.from("claude-sonnet-5-5", "POST", response, 42, 123, null)
        assertEquals(1000L, m.inputTokens); assertEquals(100L, m.outputTokens)
        assertEquals(.00305, m.estimatedUsd!!, .00000001)
        assertNull(AiRequestMetric.from("claude-custom", "POST", response, 42, 123, null).estimatedUsd)
    }
    @Test fun missingUsageFailureAndUnknownCacheCostStayUnknown() {
        val unknown = AiRequestMetric.from("claude-haiku-5-5", "POST", AiHttpResponse(200, "{}"), 0, 0, null)
        assertNull(unknown.inputTokens); assertNull(unknown.estimatedUsd)
        val fail = AiRequestMetric.from("claude-haiku-5-5", "POST", null, 0, 12, "NETWORK")
        assertNull(fail.status); assertEquals("NETWORK", fail.failure)
        val r = AiHttpResponse(200, """{"usage":{"input_tokens":100,"output_tokens":50,"cache_creation_input_tokens":100}}""")
        assertNull(AiRequestMetric.from("claude-sonnet-5-5", "POST", r, 0, 0, null).estimatedUsd)
    }
    @Test fun highContextHaikuAndCacheTtlsUseDifferentRates() {
        val r = AiHttpResponse(200, """{"usage":{"input_tokens":100001,"output_tokens":100,"cache_creation_input_tokens":1000,"cache_creation":{"ephemeral_5m_input_tokens":500,"ephemeral_1h_input_tokens":500}}}""")
        assertEquals(.051063, AiRequestMetric.from("claude-haiku-5-5", "POST", r, 0, 0, null).estimatedUsd!!, .000001)
    }
    @Test fun telemetryCannotBreakRequestsAndIncludesHttpFailures() {
        var metric: AiRequestMetric? = null
        val provider = AnthropicProvider(AiTransport { _, _, _, _, _ -> AiHttpResponse(429, "private error body") }, onMetric = { metric = it })
        assertThrows(AiFailure::class.java) { provider.message("private-key", "claude-haiku-5-5", "private prompt", JSONObject(), 100) }
        assertEquals(429, metric!!.status); assertFalse(metric.toString().contains("private"))
        val ok = AnthropicProvider(AiTransport { _, _, _, _, _ -> AiHttpResponse(200, """{"content":[{"type":"text","text":"NONE"}]}""") }, onMetric = { error("storage unavailable") })
        assertEquals("NONE", ok.message("key", "claude-haiku-5-5", "prompt", JSONObject(), 100))
    }
}
