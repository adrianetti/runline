package com.runline.app.core

/** Small testable ownership boundary: every granted focus is released exactly once. */
interface SpeechOutput {
    fun requestFocus(): Boolean
    fun abandonFocus()
    fun speak(text: String, id: String): Boolean
    fun stopSpeech()
}

class SpeechLease(private val output: SpeechOutput) {
    private var activeId: String? = null
    val isBusy: Boolean get() = activeId != null
    fun begin(text: String, id: String): Boolean {
        if (isBusy || !runCatching { output.requestFocus() }.getOrDefault(false)) return false
        activeId = id
        if (runCatching { output.speak(text, id) }.getOrDefault(false)) return true
        stop()
        return false
    }
    fun finish(id: String) {
        if (activeId != id) return
        activeId = null
        runCatching { output.abandonFocus() }
    }
    fun timeout(id: String) { if (activeId == id) stop() }
    fun stop() {
        val held = activeId != null
        activeId = null
        runCatching { output.stopSpeech() }
        if (held) runCatching { output.abandonFocus() }
    }
}
