package com.runline.app.ai

import org.json.JSONObject

object EvalUsage {
    fun of(metrics: List<AiRequestMetric>): JSONObject =
        JSONObject()
            .put("model", metrics.map { it.model }.distinct().joinToString(","))
            .put("requests", metrics.size)
            .put("failed_requests", metrics.count { it.failure != null })
            .put("input_tokens", metrics.sumOf { it.inputTokens ?: 0L })
            .put("output_tokens", metrics.sumOf { it.outputTokens ?: 0L })
            .put("cache_read_input_tokens", metrics.sumOf { it.cacheReadTokens ?: 0L })
            .put("cache_creation_input_tokens", metrics.sumOf { it.cacheWriteTokens ?: 0L })
            .put("cost_usd", metrics.sumOf { it.estimatedUsd ?: 0.0 })
}
