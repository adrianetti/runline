package com.runline.app.music

import org.junit.Assert.*
import org.junit.Test

class RunDjTest {
    private val calm = PlaylistRef("37i9dQZF1DX76Wlfdnj7AP", "Calm"); private val steady = PlaylistRef("37i9dQZF1DWZq91oLsHZvy", "Steady")
    private val push = PlaylistRef("37i9dQZF1DX0HRj9P7NxeE", "Push")
    private val config = MusicConfig(enabled = true, calm = calm, steady = steady, push = push)
    private fun playing(context: String?, progressMs: Long = 60_000, durationMs: Long = 200_000, playing: Boolean = true) =
        SpotifyPlayback(null, playing, SpotifyTrack("spotify:track:4uLU6hMCjMI75M1A2tKUQC", "Track", "Artist", durationMs), context, progressMs)
    private val minute = 60_000L

    @Test fun musicChosenOutsideTheMappedPlaylistsIsLeftAlone() {
        val dj = RunDj()
        assertNull(dj.decide(playing("spotify:album:4aawyAB9vmqN3uQ7FjRGTy"), MusicTarget(5, true), config, 0))
        assertNull(dj.decide(playing(null), MusicTarget(5, true), config, 0))
        assertNull(dj.decide(null, MusicTarget(5, true), config, 0))
        assertNull(dj.decide(playing(steady.uri, playing = false), MusicTarget(5, true), config, 0))
        assertNull(dj.decide(playing(steady.uri), MusicTarget(5, true), config.copy(adaptive = false), 0))
    }
    @Test fun playlistChangesWaitForATrackBoundary() {
        val dj = RunDj()
        assertNull(dj.decide(playing(steady.uri, progressMs = 60_000), MusicTarget(4), config, 0))
        assertEquals(RunDj.POLL_MS, dj.pollInMs)
        // Close to the end: look again just after the next song starts.
        assertNull(dj.decide(playing(steady.uri, progressMs = 190_000), MusicTarget(4), config, 10_000))
        assertEquals(11_200L, dj.pollInMs)
        assertEquals(push.uri, dj.decide(playing(steady.uri, progressMs = 1_500), MusicTarget(4), config, 21_000))
        assertNull(dj.decide(playing(push.uri, progressMs = 2_000), MusicTarget(4), config, 31_000))
        assertEquals(RunDj.POLL_MS, dj.pollInMs)
    }
    @Test fun theLastSecondsOfATrackAlsoCountAsABoundary() {
        assertEquals(calm.uri, RunDj().decide(playing(steady.uri, progressMs = 198_000), MusicTarget(2), config, 0))
    }
    @Test fun aPlaylistIsHeldBeforeTheNextCalmChangeButNotBeforeAnUrgentOne() {
        val dj = RunDj()
        dj.started(0)
        assertNull(dj.decide(playing(calm.uri, progressMs = 1_000), MusicTarget(3), config, minute))
        assertEquals(steady.uri, dj.decide(playing(calm.uri, progressMs = 1_000), MusicTarget(3), config, RunDj.HOLD_MS))
        // Final stretch: now, in the middle of a song, right after the last change.
        assertEquals(push.uri, dj.decide(playing(steady.uri, progressMs = 90_000), MusicTarget(5, true), config, RunDj.HOLD_MS + 5_000))
        assertNull(dj.decide(playing(push.uri, progressMs = 1_000), MusicTarget(2), config, RunDj.HOLD_MS + 20_000))
        assertEquals(calm.uri, dj.decide(playing(push.uri, progressMs = 1_000), MusicTarget(2), config, 2 * RunDj.HOLD_MS + 5_000))
    }
    @Test fun onePlaylistMeansNothingToSwitch() {
        val only = MusicConfig(enabled = true, steady = steady)
        assertNull(RunDj().decide(playing(steady.uri, progressMs = 1_000), MusicTarget(5, true), only, 0))
    }
    @Test fun aMissingMoodBorrowsItsNeighbour() {
        val two = MusicConfig(enabled = true, steady = steady, push = push)
        // Calm falls back to steady, which is already playing.
        assertNull(RunDj().decide(playing(steady.uri, progressMs = 1_000), MusicTarget(1), two, 0))
        assertEquals(steady.uri, RunDj().decide(playing(push.uri, progressMs = 1_000), MusicTarget(2), two, 0))
    }
}
