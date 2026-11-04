package com.runline.app.ai

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class ReviewQueueTest {
    @Test fun pendingSurvivesReopenAndOnlyClaimsOnceWhenDue() {
        val folder = Files.createTempDirectory("review").toFile()
        try {
            val a = ReviewQueue(folder)
            assertTrue(a.enqueue("run", 1000)); assertFalse(a.enqueue("run", 1001))
            val b = ReviewQueue(folder)
            assertFalse(b.claim("run", 90_999)); assertTrue(b.claim("run", 91_000)); assertFalse(a.claim("run", 100_000))
            assertEquals("running", ReviewQueue(folder).get("run")!!.state)
            b.finish("run", "INTERRUPTED")
            assertFalse(a.enqueue("run", 200_000)); assertFalse(a.claim("run", 200_000))
            assertEquals("INTERRUPTED", a.get("run")!!.error)
        } finally { folder.deleteRecursively() }
    }
    @Test fun deletionDoesNotResurrectWorkFromLateCallback() {
        val folder = Files.createTempDirectory("review").toFile()
        try {
            val q = ReviewQueue(folder); q.enqueue("run", 0); q.claim("run", 100_000)
            q.delete("run"); q.finish("run"); assertNull(q.get("run"))
            assertThrows(IllegalArgumentException::class.java) { q.enqueue("../escape", 0) }
        } finally { folder.deleteRecursively() }
    }
}
