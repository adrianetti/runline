package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class SpeechLeaseTest {
    private class Output : SpeechOutput {
        var grants = true
        var speakWorks = true
        var requested = 0
        var abandoned = 0
        var stopped = 0
        var spoken = 0
        override fun requestFocus(): Boolean { requested++; return grants }
        override fun abandonFocus() { abandoned++ }
        override fun speak(text: String, id: String): Boolean { spoken++; return speakWorks }
        override fun stopSpeech() { stopped++ }
    }
    @Test fun `denied focus never speaks or abandons somebody elses lease`() {
        val output = Output().apply { grants = false }; val lease = SpeechLease(output)
        assertFalse(lease.begin("test", "a")); lease.stop()
        assertEquals(0, output.spoken); assertEquals(0, output.abandoned)
    }
    @Test fun `done releases once and duplicate callbacks cannot affect a newer prompt`() {
        val output = Output(); val lease = SpeechLease(output)
        assertTrue(lease.begin("first", "a")); lease.finish("a"); lease.finish("a")
        assertEquals(1, output.abandoned)
        assertTrue(lease.begin("second", "b")); lease.finish("a"); lease.timeout("a")
        assertTrue(lease.isBusy); assertEquals(1, output.abandoned)
        lease.finish("b"); assertEquals(2, output.abandoned)
    }
    @Test fun `pause focus loss and timeout stop speech and restore music focus`() {
        val output = Output(); val lease = SpeechLease(output)
        assertTrue(lease.begin("first", "a")); assertFalse(lease.begin("overlap", "b"))
        assertEquals(1, output.requested)
        lease.timeout("a"); assertFalse(lease.isBusy); assertEquals(1, output.abandoned)
        assertTrue(lease.begin("second", "b")); lease.stop(); lease.finish("b")
        assertEquals(2, output.abandoned); assertEquals(2, output.stopped)
    }
    @Test fun `a TTS failure releases granted focus immediately`() {
        val output = Output().apply { speakWorks = false }; val lease = SpeechLease(output)
        assertFalse(lease.begin("broken", "a")); assertFalse(lease.isBusy)
        assertEquals(1, output.abandoned); assertEquals(1, output.stopped)
    }
}
