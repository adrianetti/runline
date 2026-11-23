package com.runline.app.ai

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EarlierRunLinesTest {
    private fun event(
        event: String,
        source: String,
        text: String,
    ) = JSONObject().put("event", event).put("source", source).put("text", text)

    @Test fun `only coach lines that started playing on the two latest runs are remembered, once each`() {
        val latest =
            listOf(
                event("queued", "AI", "Solo en cola."),
                event("started", "AI", " Dicho. "),
                event("started", "AI", "Dicho."),
                event("started", "LOCAL", "Aviso de la app."),
                event("started", "AI", ""),
            )
        val before = listOf(event("started", "AI", "Antes."))
        val oldest = listOf(event("started", "AI", "Muy antiguo."))
        assertEquals(listOf("Dicho.", "Antes."), EarlierRunLines.of(listOf(latest, before, oldest)))
        val many = (1..50).map { event("started", "AI", "Línea $it.") }
        assertEquals(EarlierRunLines.LIMIT, EarlierRunLines.of(listOf(many)).size)
    }

    @Test fun `remembered lines are read back trimmed, without blanks and capped`() {
        val data = JSONObject().put(EarlierRunLines.DATA_KEY, JSONArray(listOf(" Uno. ", "", "Dos.")))
        assertEquals(listOf("Uno.", "Dos."), EarlierRunLines.from(data))
        assertTrue(EarlierRunLines.from(JSONObject()).isEmpty())
        val many = JSONObject().put(EarlierRunLines.DATA_KEY, JSONArray((1..50).map { "Línea $it." }))
        assertEquals(EarlierRunLines.LIMIT, EarlierRunLines.from(many).size)
    }
}
