package com.runline.app.music

/**
 * Decides when the playlist changes. It only steers music that plays from one of the playlists
 * chosen in settings: when the user picks anything else in Spotify, that choice stands. Spotify's
 * own data never leaves the phone for this; the run decides the energy, the user's playlists hold the music.
 */
class RunDj {
    private var lastSwitchAt: Long? = null

    /** How soon to look again; shorter when a change waits for the end of a track. */
    var pollInMs: Long = POLL_MS
        private set

    /** Runline started the music itself, so that playlist is held for a while. */
    fun started(nowMs: Long) { lastSwitchAt = nowMs }

    /** The playlist to start now, or null to leave the music as it is. */
    fun decide(playback: SpotifyPlayback?, target: MusicTarget, config: MusicConfig, nowMs: Long): String? {
        pollInMs = POLL_MS
        if (!config.adaptive || playback == null || !playback.playing) return null
        val current = playback.contextUri
        if (current == null || config.playlists.none { it.uri == current }) return null
        val wanted = config.playlist(MusicMood.of(target.intensity))?.uri ?: return null
        if (wanted == current) return null
        if (!target.urgent) {
            if (lastSwitchAt?.let { nowMs - it < HOLD_MS } == true) return null
            // Change between two songs, not in the middle of one.
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
        /** A playlist stays at least this long, so the music does not chase every change of pace. */
        const val HOLD_MS = 3 * 60_000L
        const val TRACK_START_MS = 6_000L
        const val TRACK_END_MS = 3_000L
    }
}
