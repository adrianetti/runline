package com.runline.app.music

import com.runline.app.core.ActivityType
import com.runline.app.core.CoachConfig
import com.runline.app.core.CoachGoal
import com.runline.app.core.RunPoint
import com.runline.app.core.RunSession
import com.runline.app.core.SmartRunEffort
import org.junit.Assert.*
import org.junit.Test

class MusicIntensityTest {
    private val minute = 60_000L
    private fun running(activeMin: Int = 10, progress: Double? = null, pace: Double? = null, paceMax: Double? = null,
                        uphill: Boolean = false, effort: SmartRunEffort? = null, moving: Boolean = true, activity: ActivityType = ActivityType.RUN) =
        MusicFacts(activity, activeMin * minute, progress, pace, paceMax, uphill, effort, moving)

    @Test fun warmUpIsCalmerThanTheRunThatFollows() {
        val engine = MusicIntensity()
        assertEquals(MusicTarget(2), engine.update(running(activeMin = 1), 0))
        // The new level must hold before it counts.
        assertEquals(2, engine.update(running(activeMin = 5), 4 * minute).intensity)
        assertEquals(3, engine.update(running(activeMin = 6), 4 * minute + MusicIntensity.SETTLE_MS).intensity)
    }
    @Test fun easyDaysStayCalmEvenWhenSlow() {
        val engine = MusicIntensity()
        assertEquals(2, engine.update(running(effort = SmartRunEffort.EASY, pace = 420.0, paceMax = 360.0), 0).intensity)
        assertEquals(2, engine.update(running(effort = SmartRunEffort.EASY, pace = 420.0, paceMax = 360.0), 5 * minute).intensity)
        assertEquals(2, MusicIntensity().update(running(activity = ActivityType.HIKE), 0).intensity)
    }
    @Test fun fallingBehindThePaceGoalRaisesEnergyOnlyAfterItLasts() {
        val engine = MusicIntensity()
        assertEquals(3, engine.update(running(pace = 380.0, paceMax = 360.0), 0).intensity)
        assertEquals(3, engine.update(running(pace = 380.0, paceMax = 360.0), 30_000).intensity)
        engine.update(running(pace = 380.0, paceMax = 360.0), MusicIntensity.BEHIND_MS)
        assertEquals(MusicTarget(4), engine.update(running(pace = 380.0, paceMax = 360.0), MusicIntensity.BEHIND_MS + MusicIntensity.SETTLE_MS))
        // A few seconds over the goal is on pace.
        assertEquals(3, MusicIntensity().also { it.update(running(pace = 366.0, paceMax = 360.0), 0) }.update(running(pace = 366.0, paceMax = 360.0), 10 * minute).intensity)
    }
    @Test fun aSustainedClimbRaisesEnergyAndAShortBumpDoesNot() {
        val engine = MusicIntensity()
        engine.update(running(uphill = true), 0)
        engine.update(running(uphill = false), 10_000)
        assertEquals(3, engine.update(running(uphill = false), 60_000).intensity)
        engine.update(running(uphill = true), 70_000)
        engine.update(running(uphill = true), 70_000 + MusicIntensity.CLIMB_MS)
        assertEquals(4, engine.update(running(uphill = true), 70_000 + MusicIntensity.CLIMB_MS + MusicIntensity.SETTLE_MS).intensity)
    }
    @Test fun theFinalStretchIsUrgentAndTheGoalCoolsDown() {
        val engine = MusicIntensity()
        assertEquals(3, engine.update(running(progress = .5), 0).intensity)
        assertEquals(MusicTarget(5, urgent = true), engine.update(running(progress = .86), 1_000))
        assertEquals(5, engine.update(running(progress = .99), 2_000).intensity)
        engine.update(running(progress = 1.0), 3_000)
        assertEquals(MusicTarget(2), engine.update(running(progress = 1.02), 3_000 + MusicIntensity.SETTLE_MS))
        // An easy run ends a little brighter, never all out and never by cutting a song.
        assertEquals(MusicTarget(3), MusicIntensity().also { it.update(running(effort = SmartRunEffort.EASY, progress = .9), 0) }
            .update(running(effort = SmartRunEffort.EASY, progress = .9), minute))
    }
    @Test fun standingStillOrLosingGpsHoldsTheLastLevel() {
        val engine = MusicIntensity()
        engine.update(running(progress = .9), 0)
        assertEquals(MusicTarget(5), engine.update(running(progress = .9, moving = false), 5 * minute))
        engine.reset()
        assertEquals(3, engine.update(running(), 0).intensity)
    }
    @Test fun factsComeFromTheRecordingAndTheAppliedGoal() {
        // 2.5 m/s north for two minutes: 300 m, 6:40 per km.
        val points = (0..120).map { RunPoint(37.0 + it * 2.5 / 111_320.0, -3.6, 1_000_000L + it * 1000L, 5f) }
        val run = RunSession("run", 1_000_000L, 1_120_000L, 120_000L, points)
        val distance = MusicFacts.from(run, CoachConfig(goal = CoachGoal.DISTANCE, distanceKm = 3.0), uphill = true, stopped = false, gpsFresh = true)
        assertEquals(0.1, distance.goalProgress!!, 0.005); assertEquals(400.0, distance.paceSeconds!!, 4.0)
        assertTrue(distance.uphill); assertTrue(distance.moving); assertNull(distance.paceMaxSeconds)
        val timed = MusicFacts.from(run, CoachConfig(goal = CoachGoal.TIME, targetMinutes = 20.0, paceMinSecondsPerKm = 330, paceMaxSecondsPerKm = 360,
            effortIntent = SmartRunEffort.STEADY), uphill = false, stopped = true, gpsFresh = true)
        assertEquals(0.1, timed.goalProgress!!, 1e-9); assertEquals(360.0, timed.paceMaxSeconds!!, 1e-9)
        assertFalse(timed.moving); assertEquals(SmartRunEffort.STEADY, timed.effort)
        assertNull(MusicFacts.from(run, CoachConfig(), false, false, false).goalProgress)
    }
    @Test fun recentPaceNeedsEnoughMovementInOneSegment() {
        val moving = (0..40).map { RunPoint(37.0 + it * 3.0 / 111_320.0, -3.6, it * 1000L, 5f) }
        assertEquals(333.3, MusicFacts.recentPace(moving)!!, 4.0)
        assertNull(MusicFacts.recentPace(moving.take(10)))
        assertNull(MusicFacts.recentPace(emptyList()))
        // Only the stretch after the pause counts, and ten seconds of it is too little.
        val resumed = moving + (41..50).map { RunPoint(37.0 + it * 3.0 / 111_320.0, -3.6, it * 1000L, 5f, segment = 1) }
        assertNull(MusicFacts.recentPace(resumed))
    }
    @Test fun moodsAndPlaylistsFallBackToWhatTheUserChose() {
        assertEquals(MusicMood.CALM, MusicMood.of(1)); assertEquals(MusicMood.CALM, MusicMood.of(2))
        assertEquals(MusicMood.STEADY, MusicMood.of(3)); assertEquals(MusicMood.PUSH, MusicMood.of(4)); assertEquals(MusicMood.PUSH, MusicMood.of(5))
        val steady = PlaylistRef("37i9dQZF1DX76Wlfdnj7AP", "Run"); val push = PlaylistRef("37i9dQZF1DWZq91oLsHZvy", "Fast")
        val one = MusicConfig(steady = steady)
        assertEquals(steady, one.playlist(MusicMood.CALM)); assertEquals(steady, one.playlist(MusicMood.PUSH))
        val two = MusicConfig(steady = steady, push = push)
        assertEquals(push, two.playlist(MusicMood.PUSH)); assertEquals(steady, two.playlist(MusicMood.CALM)); assertEquals(2, two.playlists.size)
        assertNull(MusicConfig().playlist(MusicMood.STEADY)); assertEquals("spotify:playlist:37i9dQZF1DX76Wlfdnj7AP", steady.uri)
        assertTrue(two.valid()); assertFalse(MusicConfig(calm = PlaylistRef("bad id", "x")).valid())
    }
}
