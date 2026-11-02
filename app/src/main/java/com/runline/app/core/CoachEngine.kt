package com.runline.app.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

sealed interface CoachCue {
    data class Kilometre(val completed: Int, val averagePaceSeconds: Double?) : CoachCue
    data object OneKilometreRemaining : CoachCue
    data class GoalReached(val withinTime: Boolean?) : CoachCue
    data object SlowerThanTarget : CoachCue
    data object FasterThanTarget : CoachCue
    data object BackOnPace : CoachCue
    data class Encouragement(val kind: EncouragementKind) : CoachCue
    data class Terrain(val kind: CoachTerrain) : CoachCue
    data class LapCompleted(val number: Int, val comparison: LapComparison?) : CoachCue
}
enum class EncouragementKind { SETTLING_IN, STEADY, HALFWAY, FINAL_STRETCH, ENDURANCE }
enum class CoachTerrain { UNKNOWN, LEVEL, UPHILL, DOWNHILL }

/** Incremental, conservative decisions. Recovered history never becomes a backlog of cues. */
class CoachEngine(private val config: CoachConfig, initialRun: RunSession? = null) {
    private var runId: String? = null
    private var count = 0
    private var lastPoint: RunPoint? = null
    private var distance = 0.0
    private val recent = ArrayDeque<Pair<RunPoint, Double>>()
    private var laps = LapTracker()
    private var lapCount = 0
    private var crossedKilometre = 0
    private var remainingAnnounced = false
    private var goalAnnounced = false
    private var lastSpeechMs = Long.MIN_VALUE
    private var lastPaceSpeechMs = Long.MIN_VALUE
    private var lastMoraleMs = 0L
    private var lastTerrainMs = Long.MIN_VALUE
    private var baselineActiveMs = 0L
    private var previousPaceBand: Int? = null
    private var candidateBand: Int? = null
    private var candidateSinceMs = 0L
    private var terrainCandidate = CoachTerrain.UNKNOWN
    private var terrainSinceMs = 0L
    private var announcedTerrain = CoachTerrain.UNKNOWN
    private var halfway = false
    private var finalStretch = false
    private var startedEncouragement = false

    init { if (initialRun != null) rebase(initialRun) }
    fun pause() {
        recent.clear(); previousPaceBand = null; candidateBand = null
        terrainCandidate = CoachTerrain.UNKNOWN; announcedTerrain = CoachTerrain.UNKNOWN
    }

    fun update(run: RunSession, recording: Boolean, gpsFresh: Boolean): CoachCue? {
        if (runId != run.id || count > run.points.size || (count > 0 && run.points[count - 1] !== lastPoint)) {
            rebase(run); return null
        }
        append(run.points)
        if (!recording || !gpsFresh || !config.enabled || !config.isValid()) {
            recent.clear(); candidateBand = null; terrainCandidate = CoachTerrain.UNKNOWN
            return null
        }
        val now = run.activeDurationMs
        val goal = config.distanceGoalMeters
        val newKm = floor(distance / 1000.0).toInt()
        val crossedKm = newKm > crossedKilometre
        crossedKilometre = max(crossedKilometre, newKm)
        val crossedGoal = goal != null && !goalAnnounced && distance >= goal
        val crossedRemaining = goal != null && goal > 1000 && !remainingAnnounced && distance >= goal - 1000
        if (crossedGoal) goalAnnounced = true
        if (crossedRemaining) remainingAnnounced = true
        val report = if (config.lapFeedback) { laps.update(run.points); laps.report() } else null
        val newLap = report != null && report.laps.size > lapCount
        lapCount = report?.laps?.size ?: lapCount
        if (crossedGoal) return emit(CoachCue.GoalReached(
            if (config.goal == CoachGoal.DISTANCE_TIME) now <= config.targetMinutes * 60_000 else null), now)
        if (canSpeak(now, 25_000)) {
            if (crossedRemaining) return emit(CoachCue.OneKilometreRemaining, now)
            if (crossedKm && config.kilometreAnnouncements) return emit(CoachCue.Kilometre(newKm, RunMetrics.paceSecondsPerKm(distance, now)), now)
            if (newLap) return emit(CoachCue.LapCompleted(lapCount, report?.latestComparison), now)
        }
        val pace = currentPace()
        if (pace == null) { candidateBand = null; terrainCandidate = CoachTerrain.UNKNOWN; return null }
        val terrain = currentTerrain()
        if (terrain != terrainCandidate) { terrainCandidate = terrain; terrainSinceMs = now }
        // Uphill/downhill feedback replaces speed-up advice, even when its own switch is off.
        if (config.terrainFeedback && terrain in setOf(CoachTerrain.UPHILL, CoachTerrain.DOWNHILL) &&
            terrain != announcedTerrain && now - terrainSinceMs >= 20_000 && canSpeak(now, 40_000) &&
            (lastTerrainMs == Long.MIN_VALUE || now - lastTerrainMs >= 180_000)) {
            announcedTerrain = terrain; lastTerrainMs = now
            return emit(CoachCue.Terrain(terrain), now)
        }
        if (terrain == CoachTerrain.LEVEL && now - terrainSinceMs >= 30_000) announcedTerrain = CoachTerrain.UNKNOWN
        val paceCue = paceCue(now, pace, terrain)
        if (paceCue != null) return emit(paceCue, now)
        if (config.encouragement && !goalAnnounced && canSpeak(now, 60_000) && now - lastMoraleMs >= 180_000 &&
            now - baselineActiveMs >= 120_000) {
            val kind = when {
                goal != null && distance >= goal * .8 && !finalStretch -> { finalStretch = true; EncouragementKind.FINAL_STRETCH }
                goal != null && distance >= goal * .5 && !halfway -> { halfway = true; EncouragementKind.HALFWAY }
                !startedEncouragement -> { startedEncouragement = true; EncouragementKind.SETTLING_IN }
                now >= 30 * 60_000 -> EncouragementKind.ENDURANCE
                else -> EncouragementKind.STEADY
            }
            lastMoraleMs = now
            return emit(CoachCue.Encouragement(kind), now)
        }
        return null
    }

    private fun paceCue(now: Long, pace: Double, terrain: CoachTerrain): CoachCue? {
        val target = config.targetPaceSeconds ?: return null
        if (!config.paceFeedback || goalAnnounced || now - baselineActiveMs < 60_000) return null
        if (terrain == CoachTerrain.UPHILL || terrain == CoachTerrain.DOWNHILL) { candidateBand = null; return null }
        val tolerance = max(18.0, target * .06)
        // Return to target needs a narrower band than departure: GPS noise cannot flip labels repeatedly.
        val recovery = max(10.0, target * .035)
        val delta = pace - target
        val band = when {
            delta > tolerance -> 1
            delta < -tolerance -> -1
            abs(delta) <= recovery -> 0
            else -> candidateBand ?: previousPaceBand ?: 0
        }
        if (candidateBand != band) { candidateBand = band; candidateSinceMs = now; return null }
        if (now - candidateSinceMs < 25_000 || !canSpeak(now, 45_000) ||
            (lastPaceSpeechMs != Long.MIN_VALUE && now - lastPaceSpeechMs < 120_000)) return null
        if (band == 0 && previousPaceBand == null) { previousPaceBand = 0; return null }
        if (band == 0 && previousPaceBand == 0) return null
        previousPaceBand = band; lastPaceSpeechMs = now
        return when (band) { 1 -> CoachCue.SlowerThanTarget; -1 -> CoachCue.FasterThanTarget; else -> CoachCue.BackOnPace }
    }
    private fun canSpeak(now: Long, interval: Long) = lastSpeechMs == Long.MIN_VALUE || now - lastSpeechMs >= interval
    private fun emit(cue: CoachCue, now: Long): CoachCue { lastSpeechMs = now; return cue }
    private fun rebase(run: RunSession) {
        runId = run.id; count = 0; lastPoint = null; distance = 0.0; recent.clear()
        append(run.points); laps = LapTracker().apply { update(run.points) }; lapCount = laps.report().laps.size
        recent.clear(); baselineActiveMs = run.activeDurationMs
        crossedKilometre = floor(distance / 1000.0).toInt()
        remainingAnnounced = config.distanceGoalMeters?.let { distance >= it - 1000 } ?: false
        goalAnnounced = config.distanceGoalMeters?.let { distance >= it } ?: false
        halfway = config.distanceGoalMeters?.let { distance >= it * .5 } ?: false
        finalStretch = config.distanceGoalMeters?.let { distance >= it * .8 } ?: false
        lastSpeechMs = Long.MIN_VALUE; lastPaceSpeechMs = Long.MIN_VALUE; lastTerrainMs = Long.MIN_VALUE
        lastMoraleMs = run.activeDurationMs; startedEncouragement = run.activeDurationMs > 180_000
        previousPaceBand = null; candidateBand = null; terrainCandidate = CoachTerrain.UNKNOWN; announcedTerrain = CoachTerrain.UNKNOWN
    }
    private fun append(points: List<RunPoint>) {
        for (index in count until points.size) {
            val point = points[index]
            val previous = lastPoint
            val span = previous?.let { point.timestampMs - it.timestampMs } ?: 0
            val usable = point.hasValidCoordinates() && point.accuracyMeters.isFinite() && point.accuracyMeters in 0f..30f
            val connected = usable && previous != null && previous.hasValidCoordinates() && previous.accuracyMeters in 0f..30f &&
                point.segment == previous.segment && span in 1L..15_000L
            val moved = if (connected) RunMetrics.distanceMeters(listOf(previous!!, point)) else 0.0
            val plausible = moved <= 15.0 * span / 1000 + 15
            if (connected && plausible) distance += moved else recent.clear()
            if (usable) {
                recent.addLast(point to distance)
                while (recent.size > 2 && point.timestampMs - recent.elementAt(1).first.timestampMs >= 40_000) recent.removeFirst()
            }
            lastPoint = point
        }
        count = points.size
    }
    private fun currentPace(): Double? {
        if (recent.size < 2) return null
        val a = recent.first(); val b = recent.last(); val span = b.first.timestampMs - a.first.timestampMs; val covered = b.second - a.second
        if (span !in 30_000..55_000 || covered < 20.0) return null
        return (span / covered).takeIf { it.isFinite() && it in 60.0..3600.0 }
    }
    private fun currentTerrain(): CoachTerrain {
        if (recent.size < 7 || recent.last().second - recent.first().second < 50) return CoachTerrain.UNKNOWN
        val heights = recent.map { it.first.altitudeMeters?.takeIf(Double::isFinite) }
        if (heights.any { it == null }) return CoachTerrain.UNKNOWN
        val first = heights.take(3).filterNotNull().sorted()[1]
        val last = heights.takeLast(3).filterNotNull().sorted()[1]
        val change = last - first
        val distance = recent.last().second - recent.first().second
        if (heights.filterNotNull().zipWithNext().any { abs(it.second - it.first) > 10 }) return CoachTerrain.UNKNOWN
        return when {
            change >= 4 && change / distance >= .03 -> CoachTerrain.UPHILL
            change <= -4 && change / distance <= -.03 -> CoachTerrain.DOWNHILL
            abs(change) <= 2 -> CoachTerrain.LEVEL
            else -> CoachTerrain.UNKNOWN
        }
    }
}
