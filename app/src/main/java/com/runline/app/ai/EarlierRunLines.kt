package com.runline.app.ai

import org.json.JSONArray
import org.json.JSONObject

object EarlierRunLines {
    const val DATA_KEY = "lines_from_earlier_runs"
    const val PROMPT_KEY = "heard_on_earlier_runs"
    const val RUNS = 2
    const val LIMIT = 30

    fun of(logs: List<List<JSONObject>>): List<String> =
        logs
            .take(RUNS)
            .flatMap { events ->
                events
                    .filter { it.optString("event") == "started" && it.optString("source") == "AI" }
                    .map { it.optString("text").trim() }
                    .filter { it.isNotEmpty() }
            }.distinct()
            .take(LIMIT)

    fun from(data: JSONObject): List<String> {
        val lines = data.optJSONArray(DATA_KEY) ?: JSONArray()
        return (0 until lines.length()).mapNotNull { lines.optString(it).trim().takeIf(String::isNotEmpty) }.take(LIMIT)
    }
}
