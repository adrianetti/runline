package com.runline.app.music

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent
import com.runline.app.core.CoachConfig
import com.runline.app.core.RunSession
import com.runline.app.core.TerrainDirection
import com.runline.app.core.TerrainEstimate
import com.runline.app.core.TerrainSource
import com.runline.app.core.TerrainTrend
import com.runline.app.terrain.TerrainRepository
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** What the recording screen shows. [title] is empty while only the phone's media keys are in use. */
data class NowPlaying(val title: String, val artist: String, val playing: Boolean, val problem: MusicProblem? = null,
                      /** Opens this track in Spotify, and its cover; both only with a Spotify connection. */
                      val trackUri: String? = null, val coverUrl: String? = null,
                      /** No Spotify connection: play state comes from the phone, not from this snapshot. */
                      val keysOnly: Boolean = false)

enum class MusicProblem { OPEN_SPOTIFY, PREMIUM, RECONNECT }

/**
 * Music that follows the recording. Without a Spotify connection it sends the play and pause keys
 * a headset would, which any player understands. With one it also starts and steers the chosen
 * playlists. Everything runs on its own thread: the recorder never waits for it or fails with it.
 */
class RunMusic(context: Context) {
    private val app = context.applicationContext
    private val audio = app.getSystemService(AudioManager::class.java)
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val intensity = MusicIntensity()
    private val trend = TerrainTrend(TerrainSource.TERRAIN_MAP)
    private var trendRun: String? = null
    private var trendCount = 0
    private var estimate = TerrainEstimate()
    private var ticks = 0
    @Volatile private var facts = MusicFacts()
    @Volatile private var recording = false

    // Everything below is touched only on the executor.
    private var config = MusicConfig()
    private var api: SpotifyApi? = null
    private var dj = RunDj()
    private var runId: String? = null
    private var pausedByUs = false
    private var blocked: MusicProblem? = null
    private var failures = 0
    private var retryAt = 0L
    /** Until when a missing Spotify device may still start the music once it shows up. */
    private var startUntil = 0L
    private var poll: ScheduledFuture<*>? = null

    init { live = this }

    /** Recording started or resumed. */
    fun onRecording(run: RunSession) { recording = true; val id = run.id; executor.execute { guarded { begin(id) } } }
    /** The user paused the run. */
    fun onPaused() { executor.execute { guarded { pauseMusic() } } }
    /** Recording stopped for any reason: stop looking, change nothing. */
    fun idle() { recording = false; executor.execute { poll?.cancel(false); poll = null } }
    fun close() { if (live === this) { live = null; nowPlaying = null }; idle(); executor.shutdown() }

    /** Called once a second on the recorder thread; cheap, and never touches the network. */
    fun update(run: RunSession, coach: CoachConfig, stopped: Boolean, gpsFresh: Boolean) {
        if (ticks++ % 5 != 0) return
        facts = MusicFacts.from(run, coach, uphill(run), stopped, gpsFresh)
    }

    private fun uphill(run: RunSession): Boolean {
        if (run.id != trendRun || run.points.size < trendCount) {
            trend.reset(); estimate = TerrainEstimate(); trendRun = run.id; trendCount = (run.points.size - 180).coerceAtLeast(0)
        }
        val snapshot = TerrainRepository.get(app).snapshot
        for (index in trendCount until run.points.size) estimate = trend.update(snapshot.point(run.points[index]))
        trendCount = run.points.size
        return estimate.direction == TerrainDirection.UPHILL
    }

    private fun load() {
        config = MusicSettings.load(app)
        api = if (config.enabled && SpotifyAccount.isConnected(app)) SpotifyAccount.api(app) else null
    }

    private fun begin(id: String) {
        load()
        val resumed = id == runId
        runId = id
        if (!resumed) { dj = RunDj(); intensity.reset(); blocked = null; failures = 0; retryAt = 0; startUntil = 0 }
        if (!config.enabled) { nowPlaying = null; return }
        val spotify = api?.takeIf { blocked == null }
        if (spotify == null) {
            // Any player: the keys a headset sends. Only resume what this app paused itself.
            if (if (resumed) pausedByUs else config.startWithRun && !audio.isMusicActive) key(KeyEvent.KEYCODE_MEDIA_PLAY)
            pausedByUs = false
            nowPlaying = NowPlaying("", "", true, blocked, keysOnly = true)
            return
        }
        if (resumed) { if (pausedByUs) resume(spotify) } else if (config.startWithRun) start(spotify)
        pausedByUs = false
        schedule(1_500)
    }

    private fun start(spotify: SpotifyApi, wake: Boolean = true) {
        val current = spotify.playback()
        if (current?.playing == true) { startUntil = 0; return } // The user's music is already on; leave the choice alone.
        var device = phone(listOfNotNull(current?.device)) ?: phone(spotify.devices())
        if (device == null && wake) {
            // Spotify is not running. The play key restarts the last player; then look for it again.
            if (!audio.isMusicActive) key(KeyEvent.KEYCODE_MEDIA_PLAY)
            repeat(4) { if (device == null) { Thread.sleep(2_000); device = phone(spotify.devices()) } }
        }
        val target = device ?: run {
            // Opening Spotify within the next minutes still starts the music.
            if (wake) startUntil = SystemClock.elapsedRealtime() + START_WINDOW_MS
            publish(current, MusicProblem.OPEN_SPOTIFY); return
        }
        startUntil = 0
        val context = config.playlist(MusicMood.of(intensity.update(facts, SystemClock.elapsedRealtime()).intensity))?.uri
        if (context != null && config.adaptive) {
            runCatching { spotify.shuffle(true, target.id) }
            spotify.play(target.id, contextUri = context)
            dj.started(SystemClock.elapsedRealtime())
        } else spotify.play(target.id)
    }

    /** Never a speaker or a TV that happens to be online: the active device, or this account's phone. */
    private fun phone(devices: List<SpotifyDevice>): SpotifyDevice? = devices.filter { !it.restricted }.let { usable ->
        usable.firstOrNull { it.active } ?: usable.firstOrNull { it.type.equals("Smartphone", ignoreCase = true) }
    }

    private fun resume(spotify: SpotifyApi) {
        try { spotify.play() }
        catch (error: SpotifyFailure) {
            if (error.reason != SpotifyFailure.Reason.NO_DEVICE) throw error
            phone(spotify.devices())?.let { spotify.play(it.id) }
        }
    }

    private fun pauseMusic() {
        if (!config.enabled || !config.pauseWithRun) return
        val spotify = api?.takeIf { blocked == null }
        if (spotify == null) {
            if (audio.isMusicActive) { key(KeyEvent.KEYCODE_MEDIA_PAUSE); pausedByUs = true }
            return
        }
        startUntil = 0
        val current = spotify.playback()
        if (current?.playing == true) { spotify.pause(); pausedByUs = true }
        publish(current?.copy(playing = false))
    }

    private fun tick() {
        poll = null
        val spotify = api?.takeIf { blocked == null } ?: return
        if (!recording) return
        var delay = RunDj.POLL_MS
        if (SystemClock.elapsedRealtime() >= retryAt) guarded {
            val now = SystemClock.elapsedRealtime()
            if (now < startUntil) start(spotify, wake = false)
            val playback = spotify.playback()
            publish(playback, if (playback == null && config.startWithRun) MusicProblem.OPEN_SPOTIFY else blocked)
            dj.decide(playback, intensity.update(facts, now), config, now)?.let { playlist ->
                runCatching { spotify.shuffle(true) }; spotify.play(contextUri = playlist)
            }
            failures = 0
            delay = dj.pollInMs
        }
        schedule(delay)
    }

    private fun schedule(delayMs: Long) {
        poll?.cancel(false)
        if (recording && api != null && blocked == null) poll = executor.schedule({ tick() }, delayMs, TimeUnit.MILLISECONDS)
    }

    /** The strip's own buttons work while paused too, when nothing is polling. */
    private fun togglePlayback() {
        pausedByUs = false; startUntil = 0
        val spotify = api?.takeIf { blocked == null }
        if (spotify == null) { key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE); return }
        val current = spotify.playback()
        if (current?.playing == true) spotify.pause() else resume(spotify)
        publish(current?.copy(playing = current.playing != true))
    }

    private fun skipTrack() {
        val spotify = api?.takeIf { blocked == null }
        if (spotify == null) { key(KeyEvent.KEYCODE_MEDIA_NEXT); return }
        spotify.next()
        Thread.sleep(700)
        publish(spotify.playback())
    }

    private fun publish(playback: SpotifyPlayback?, problem: MusicProblem? = blocked) {
        val track = playback?.track
        nowPlaying = NowPlaying(track?.name.orEmpty(), track?.artist.orEmpty(), playback?.playing == true, problem, track?.uri, track?.coverUrl)
    }

    /** Optional audio must never throw into the recorder, and a refused account must not be asked again and again. */
    private fun guarded(block: () -> Unit) {
        try { block() } catch (error: SpotifyFailure) {
            when (error.reason) {
                SpotifyFailure.Reason.PREMIUM -> stop(MusicProblem.PREMIUM)
                SpotifyFailure.Reason.AUTH, SpotifyFailure.Reason.NOT_ALLOWED -> stop(MusicProblem.RECONNECT)
                SpotifyFailure.Reason.RATE_LIMIT -> retryAt = SystemClock.elapsedRealtime() + (error.retryAfterSeconds ?: 30).coerceIn(5, 300) * 1000L
                SpotifyFailure.Reason.NETWORK -> retryAt = SystemClock.elapsedRealtime() + (10_000L shl failures++.coerceAtMost(3)).coerceAtMost(60_000)
                SpotifyFailure.Reason.NO_DEVICE -> nowPlaying = nowPlaying?.copy(playing = false, problem = MusicProblem.OPEN_SPOTIFY)
                SpotifyFailure.Reason.RESPONSE -> Unit
            }
        } catch (_: Exception) { }
    }

    private fun stop(problem: MusicProblem) {
        blocked = problem; poll?.cancel(false); poll = null
        nowPlaying = NowPlaying("", "", false, problem, keysOnly = true)
    }

    private fun key(code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    companion object {
        private const val START_WINDOW_MS = 3 * 60_000L
        /** Null while music control is off or nothing is being recorded. */
        @Volatile var nowPlaying: NowPlaying? = null
            private set
        @Volatile private var live: RunMusic? = null
        fun toggle() { live?.let { music -> music.executor.execute { music.guarded { music.togglePlayback() } } } }
        fun skip() { live?.let { music -> music.executor.execute { music.guarded { music.skipTrack() } } } }
        /** Settings changed while a run is open: use them from the next look. */
        fun reload() { live?.let { music -> music.executor.execute { music.guarded { music.load(); if (!music.config.enabled) nowPlaying = null else music.schedule(1_000) } } } }
    }
}
