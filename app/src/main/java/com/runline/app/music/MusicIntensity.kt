package com.runline.app.music

import com.runline.app.core.ActivityType
import com.runline.app.core.CoachConfig
import com.runline.app.core.RunMetrics
import com.runline.app.core.RunPoint
import com.runline.app.core.RunSession
import com.runline.app.core.SmartRunEffort

enum class MusicMood { CALM, STEADY, PUSH;
    companion object { fun of(intensity: Int) = when { intensity <= 2 -> CALM; intensity == 3 -> STEADY; else -> PUSH } }
}

data class PlaylistRef(val id: String, val name: String) {
    val uri: String get() = "spotify:playlist:$id"
    fun valid() = SpotifyApi.PLAYLIST_ID.matches(id) && name.length in 1..120
}

data class MusicConfig(
    val enabled: Boolean = false,
    val startWithRun: Boolean = true,
    val pauseWithRun: Boolean = true,
    /** Change playlist with the run. Needs a Spotify connection and at least two playlists. */
    val adaptive: Boolean = true,
    val calm: PlaylistRef? = null,
    val steady: PlaylistRef? = null,
    val push: PlaylistRef? = null,
) {
    /** A missing mood borrows its neighbour's playlist, so one playlist is enough to start. */
    fun playlist(mood: MusicMood): PlaylistRef? = when (mood) {
        MusicMood.CALM -> calm ?: steady ?: push
        MusicMood.STEADY -> steady ?: calm ?: push
        MusicMood.PUSH -> push ?: steady ?: calm
    }
    val playlists: List<PlaylistRef> get() = listOfNotNull(calm, steady, push).distinctBy { it.id }
    fun valid() = playlists.all { it.valid() }
}

/** What the music may know about the run. Measured facts only: no heart rate, no guessed fatigue. */
data class MusicFacts(
    val activity: ActivityType = ActivityType.RUN,
    val activeMs: Long = 0,
    /** Share of the distance or time goal already done; null without one. */
    val goalProgress: Double? = null,
    /** Pace over about the last minute of movement. */
    val paceSeconds: Double? = null,
    /** The slow edge of the applied pace goal. */
    val paceMaxSeconds: Double? = null,
    val uphill: Boolean = false,
    val effort: SmartRunEffort? = null,
    /** False while standing still or without a fresh GPS fix: nothing to react to. */
    val moving: Boolean = true,
) {
    companion object {
        fun from(run: RunSession, coach: CoachConfig, uphill: Boolean, stopped: Boolean, gpsFresh: Boolean): MusicFacts {
            val distance = RunMetrics.distanceMeters(run.points)
            val progress = coach.distanceGoalMeters?.let { distance / it } ?: coach.durationGoalMs?.let { run.activeDurationMs.toDouble() / it }
            return MusicFacts(run.activityType, run.activeDurationMs, progress?.takeIf { it.isFinite() }, recentPace(run.points),
                coach.paceTargetRange?.endInclusive, uphill, coach.effortIntent, !stopped && gpsFresh)
        }

        /** Seconds per kilometre over the last [windowMs] of one unbroken stretch, or null when too short to judge. */
        fun recentPace(points: List<RunPoint>, windowMs: Long = 60_000): Double? {
            val last = points.lastOrNull() ?: return null
            var start = points.lastIndex
            while (start > 0 && points[start - 1].segment == last.segment && last.timestampMs - points[start - 1].timestampMs <= windowMs) start--
            val window = points.subList(start, points.size)
            val elapsed = last.timestampMs - window.first().timestampMs
            val distance = RunMetrics.distanceMeters(window)
            if (elapsed < windowMs / 2 || distance < 30.0) return null
            return RunMetrics.paceSecondsPerKm(distance, elapsed)
        }
    }
}

/** [intensity] runs from 1 (very calm) to 5 (all out). [urgent] asks for the change now, not at the next track. */
data class MusicTarget(val intensity: Int, val urgent: Boolean = false)

/**
 * Turns the run into the energy the music should have. A change has to hold for a while before
 * it counts, so one slow corner or a short bump never flips the playlist.
 */
class MusicIntensity {
    private var held = 0
    private var candidate = 0
    private var candidateSince = 0L
    private var uphillSince: Long? = null
    private var behindSince: Long? = null

    fun reset() { held = 0; candidate = 0; uphillSince = null; behindSince = null }

    fun update(facts: MusicFacts, nowMs: Long): MusicTarget {
        if (!facts.moving && held > 0) return MusicTarget(held)
        val easy = facts.effort == SmartRunEffort.EASY || facts.effort == SmartRunEffort.RECOVERY
        var level = if (facts.activity != ActivityType.RUN || easy) 2 else 3
        val warmingUp = facts.activeMs < WARM_UP_MS
        if (warmingUp) level -= 1
        uphillSince = if (facts.uphill) uphillSince ?: nowMs else null
        if (uphillSince?.let { nowMs - it >= CLIMB_MS } == true) level += 1
        // On an easy day a slow minute is the plan, not a reason to push.
        val behind = !easy && !warmingUp && facts.paceSeconds != null && facts.paceMaxSeconds != null &&
            facts.paceSeconds > facts.paceMaxSeconds * BEHIND_RATIO
        behindSince = if (behind) behindSince ?: nowMs else null
        if (behindSince?.let { nowMs - it >= BEHIND_MS } == true) level += 1
        var urgent = false
        facts.goalProgress?.let { progress ->
            if (progress >= 1.0) level = 2
            else if (progress >= FINAL_STRETCH) { level = if (easy) 3 else 5; urgent = !easy }
        }
        level = level.coerceIn(1, 5)
        if (level != candidate) { candidate = level; candidateSince = nowMs }
        if (held == 0 || (level != held && (urgent || nowMs - candidateSince >= SETTLE_MS))) held = level
        return MusicTarget(held, urgent && held == level)
    }

    companion object {
        const val WARM_UP_MS = 4 * 60_000L
        const val CLIMB_MS = 20_000L
        const val BEHIND_MS = 45_000L
        const val BEHIND_RATIO = 1.04
        const val FINAL_STRETCH = 0.85
        const val SETTLE_MS = 20_000L
    }
}
