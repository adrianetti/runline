package com.runline.app.ai

import org.json.JSONObject

/** Metadata only: never credentials, prompt/response text, coordinates or profile fields. */
data class AiRequestMetric(
    val atMs: Long, val model: String, val method: String, val status: Int?, val durationMs: Long,
    val inputTokens: Long?, val outputTokens: Long?, val cacheReadTokens: Long?, val cacheWriteTokens: Long?,
    val estimatedUsd: Double?, val failure: String? = null,
) {
    companion object {
        fun from(model: String, method: String, response: AiHttpResponse?, at: Long, duration: Long, failure: String?): AiRequestMetric {
            val usage = response?.takeIf { it.status in 200..299 }?.let {
                runCatching { JSONObject(it.body).optJSONObject("usage") }.getOrNull()
            }
            fun count(name: String): Long? = (usage?.opt(name) as? Number)?.toLong()?.takeIf { it in 0..100_000_000 }
            val input = count("input_tokens"); val output = count("output_tokens")
            val read = count("cache_read_input_tokens"); val write = count("cache_creation_input_tokens")
            val cost = if (method == "GET" && response?.status in 200..299) 0.0 else
                estimateCost(model, input, output, read, write, usage?.optJSONObject("cache_creation"))
            return AiRequestMetric(at, model, method, response?.status, duration.coerceAtLeast(0), input, output, read, write, cost, failure)
        }
        // Standard global list prices verified 2026-11-04. An estimate, not an account invoice.
        private fun estimateCost(model: String, input: Long?, output: Long?, read: Long?, write: Long?, cache: JSONObject?): Double? {
            if (input == null || output == null) return null
            val cacheRead = read ?: 0; val cacheWrite = write ?: 0
            val rates = when (model) {
                "claude-haiku-5-5" -> if (input + cacheRead + cacheWrite <= 100_000) listOf(.10, .50, .01) else listOf(.50, 2.50, .05)
                "claude-sonnet-5-5" -> listOf(2.0, 10.0, .10)
                "claude-opus-5-5" -> listOf(4.0, 20.0, .20)
                else -> return null
            }
            // A cache write without a TTL breakdown cannot be priced accurately.
            val short = (cache?.opt("ephemeral_5m_input_tokens") as? Number)?.toLong() ?: 0
            val long = (cache?.opt("ephemeral_1h_input_tokens") as? Number)?.toLong() ?: 0
            if (short < 0 || long < 0 || short + long != cacheWrite) return null
            return (input * rates[0] + output * rates[1] + cacheRead * rates[2] + short * rates[0] * 1.25 + long * rates[0] * 2) / 1_000_000
        }
    }
}
