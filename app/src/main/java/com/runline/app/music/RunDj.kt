package com.runline.app.music

class RunDj {
    private var lastSwitchAt: Long? = null

    var pollInMs: Long = POLL_MS
        private set

    fun started(nowMs: Long) { lastSwitchAt = nowMs }

    fun decide(playback: SpotifyPlayback?, target: MusicTarget, config: MusicConfig, nowMs: Long): String? {
        pollInMs = POLL_MS
        if (!config.adaptive || playback == null || !playback.playing) return null
        val current = playback.contextUri
        if (current == null || config.playlists.none { it.uri == current }) return null
        val wanted = config.playlist(MusicMood.of(target.intensity))?.uri ?: return null
        if (wanted == current) return null
        if (!target.urgent) {
            if (lastSwitchAt?.let { nowMs - it < HOLD_MS } == true) return null
            val remaining = playback.remainingMs ?: Long.MAX_VALUE
            if (playback.progressMs > TRACK_START_MS && remaining > TRACK_END_MS) {
                if (remaining <= POLL_MS + 4_000) pollInMs = (remaining + 1_200).coerceAtLeast(1_500)
                return null
            }
        }
        lastSwitchAt = nowMs
        return wanted
    }

    companion object {
        const val POLL_MS = 10_000L
        const val HOLD_MS = 3 * 60_000L
        const val TRACK_START_MS = 6_000L
        const val TRACK_END_MS = 3_000L
    }
}
