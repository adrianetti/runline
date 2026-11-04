package com.runline.app.ai

import org.json.JSONArray
import org.json.JSONObject

/** Small, validated presentation contract; legacy plain-text reviews stay readable. */
data class ReviewCard(val headline: String, val win: String, val focus: String, val nextSession: String) {
    fun json() = JSONObject().put("headline", headline).put("win", win).put("focus", focus).put("next_session", nextSession)
    fun text() = listOf(headline, win, focus, nextSession).joinToString("\n\n")
    companion object {
        val schema: JSONObject get() = JSONObject().put("type", "object").put("additionalProperties", false)
            .put("required", JSONArray(listOf("headline", "win", "focus", "next_session")))
            .put("properties", JSONObject().apply { listOf("headline", "win", "focus", "next_session").forEach {
                put(it, JSONObject().put("type", "string")) } })
        fun parse(raw: String): ReviewCard {
            require(raw.length <= 4000)
            val data = JSONObject(raw)
            require(data.keys().asSequence().toSet() == setOf("headline", "win", "focus", "next_session"))
            fun field(name: String, max: Int): String {
                val value = data.get(name) as? String ?: error("Invalid field")
                val clean = value.trim()
                require(clean.length in 1..max && clean.none { it.isISOControl() } && !clean.contains("http", true) && !clean.contains('<'))
                return clean
            }
            return ReviewCard(field("headline", 75), field("win", 200), field("focus", 200), field("next_session", 260)).also {
                require(it.text().split(Regex("\\s+")).size <= 100)
            }
        }
    }
}
