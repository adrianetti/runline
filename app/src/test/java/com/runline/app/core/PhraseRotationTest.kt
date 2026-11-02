package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class PhraseRotationTest {
    @Test fun `each phrase gets used and cycle boundary never repeats`() {
        val rotation = PhraseRotation(42); val options = listOf("one", "two", "three", "four")
        val chosen = (1..40).map { rotation.choose("pace", options) }
        assertTrue(chosen.zipWithNext().all { it.first != it.second })
        assertTrue(chosen.chunked(4).all { it.toSet() == options.toSet() })
    }
    @Test fun `language and cue categories have independent bags`() {
        val rotation = PhraseRotation(12)
        val en = listOf("steady", "relax"); val es = listOf("sigue", "relaja")
        assertTrue(rotation.choose("en:pace", en) in en)
        assertTrue(rotation.choose("es:pace", es) in es)
        assertEquals("single", rotation.choose("one", listOf("single")))
    }
}
