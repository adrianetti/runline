package com.runline.app.ai

/** Only TTS onStart events enter this memory-only, per-activity history. */
data class AiSpokenFeedback(val text: String, val source: String, val elapsedRealtimeMs: Long)

object AiSpokenHistory {
    private val runs = linkedMapOf<String, ArrayDeque<AiSpokenFeedback>>()
    @Synchronized fun record(runId: String, text: String, source: String, elapsedRealtimeMs: Long) {
        if (text.isBlank() || elapsedRealtimeMs < 0 || source !in setOf("LOCAL", "AI")) return
        val items = runs.getOrPut(runId) { ArrayDeque() }
        items.addLast(AiSpokenFeedback(text.take(LiveAiAdvice.MAX_CHARS), source, elapsedRealtimeMs))
        while (items.size > LIMIT) items.removeFirst()
        while (runs.size > 4) runs.remove(runs.keys.first())
    }
    /** Enough of the run for the live coach to avoid repeating itself. */
    const val LIMIT = 16
    const val WINDOW_MS = 2_700_000L
    @Synchronized fun recent(runId: String, nowElapsedMs: Long): List<AiSpokenFeedback> =
        runs[runId].orEmpty().filter { nowElapsedMs - it.elapsedRealtimeMs in 0..WINDOW_MS }
    @Synchronized fun clear(runId: String) { runs.remove(runId) }
    @Synchronized fun clearAll() { runs.clear() }
}
