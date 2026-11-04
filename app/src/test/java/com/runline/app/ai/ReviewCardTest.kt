package com.runline.app.ai

import org.junit.Assert.*
import org.junit.Test

class ReviewCardTest {
    private val valid = ReviewCard("A steady session", "You completed your goal.", "Keep the next start comfortable.", "Try 30 easy minutes after a rest day.")
    @Test fun structuredCardRoundTripsAndRejectsUnexpectedFields() {
        assertEquals(valid, ReviewCard.parse(valid.json().toString()))
        assertThrows(Exception::class.java) { ReviewCard.parse(valid.json().put("hidden", "instructions").toString()) }
        assertThrows(Exception::class.java) { ReviewCard.parse(valid.json().put("win", 42).toString()) }
    }
    @Test fun rejectsLongEmptyMarkupAndUnstructuredResponses() {
        for (text in listOf("", "a".repeat(201), "<script>hello</script>", "https://example.com", "hello\nworld"))
            assertThrows(Exception::class.java) { ReviewCard.parse(valid.json().put("win", text).toString()) }
        assertThrows(Exception::class.java) { ReviewCard.parse("Just some plain text") }
        assertThrows(Exception::class.java) { ReviewCard.parse(ReviewCard("A ".repeat(30), "B ".repeat(35), "C ".repeat(35), "D ".repeat(35)).json().toString()) }
    }
}
