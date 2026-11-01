package com.runline.app.core

import kotlin.math.floor
import kotlin.math.max

sealed interface CoachCue {
    data class Kilometre(val completed: Int, val averagePaceSeconds: Double?) : CoachCue
    data object OneKilometreRemaining : CoachCue
    data class GoalReached(val withinTime: Boolean?) : CoachCue
    data object SlowerThanTarget : CoachCue
    data object FasterThanTarget : CoachCue
    data object BackOnPace : CoachCue
}

/**
 * Pure, incremental decisions. New/recovered/changed configurations baseline existing
 * distance, so enabling midway or restarting never speaks a backlog of old milestones.
 */
class CoachEngine(private val config: CoachConfig, initialRun: RunSession? = null) {
    private var runId: String? = null
    private var count = 0
    private var lastPoint: RunPoint? = null
    private var distance = 0.0
    private val recent = ArrayDeque<Pair<RunPoint, Double>>()
    private var crossedKilometre = 0
    private var remainingAnnounced = false
    private var goalAnnounced = false
    private var lastSpeechMs = Long.MIN_VALUE
    private var lastPaceSpeechMs = Long.MIN_VALUE
    private var baselineActiveMs = 0L
    private var previousPaceBand: Int? = null
    private var candidateBand: Int? = null
    private var candidateSinceMs = 0L

    init { if (initialRun != null) rebase(initialRun) }

    fun pause() {
        recent.clear(); previousPaceBand = null; candidateBand = null
    }

    fun update(run: RunSession, recording: Boolean, gpsFresh: Boolean): CoachCue? {
        if (runId != run.id || count > run.points.size || (count > 0 && run.points[count - 1] !== lastPoint)) {
            rebase(run)
            return null
        }
        append(run.points)
        if (!recording || !gpsFresh || !config.enabled || !config.isValid()) {
            recent.clear(); candidateBand = null
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
        if (crossedGoal) return emit(CoachCue.GoalReached(
            if (config.goal == CoachGoal.DISTANCE_TIME) now <= config.targetMinutes * 60_000 else null), now)
        // Goals and kilometre boundaries often coincide. Prefer the useful remaining cue.
        if (canSpeak(now, 20_000)) {
            if (crossedRemaining) return emit(CoachCue.OneKilometreRemaining, now)
            if (crossedKm && config.kilometreAnnouncements) return emit(CoachCue.Kilometre(newKm,
                RunMetrics.paceSecondsPerKm(distance, now)), now)
        }
        val target = config.targetPaceSeconds ?: return null
        if (goalAnnounced || now - baselineActiveMs < 60_000) return null
        val recentPace = currentPace()
        if (recentPace == null) { candidateBand = null; return null }
        val tolerance = max(15.0, target * 0.05)
        val band = when {
            recentPace > target + tolerance -> 1
            recentPace < target - tolerance -> -1
            else -> 0
        }
        if (candidateBand != band) { candidateBand = band; candidateSinceMs = now; return null }
        if (now - candidateSinceMs < 15_000 || !canSpeak(now, 30_000) ||
            (lastPaceSpeechMs != Long.MIN_VALUE && now - lastPaceSpeechMs < 120_000)) return null
        if (band == 0 && previousPaceBand == null) { previousPaceBand = 0; return null }
        if (band == 0 && previousPaceBand == 0) return null
        previousPaceBand = band; lastPaceSpeechMs = now
        return emit(when (band) { 1 -> CoachCue.SlowerThanTarget; -1 -> CoachCue.FasterThanTarget; else -> CoachCue.BackOnPace }, now)
    }

    private fun canSpeak(now: Long, interval: Long) = lastSpeechMs == Long.MIN_VALUE || now - lastSpeechMs >= interval
    private fun emit(cue: CoachCue, now: Long): CoachCue { lastSpeechMs = now; return cue }

    private fun rebase(run: RunSession) {
        runId = run.id; count = 0; lastPoint = null; distance = 0.0; recent.clear()
        append(run.points)
        // Recorded history is useful for total distance, not for fresh pace after recovery.
        recent.clear(); baselineActiveMs = run.activeDurationMs
        crossedKilometre = floor(distance / 1000.0).toInt()
        remainingAnnounced = config.distanceGoalMeters?.let { distance >= it - 1000 } ?: false
        goalAnnounced = config.distanceGoalMeters?.let { distance >= it } ?: false
        lastSpeechMs = Long.MIN_VALUE; lastPaceSpeechMs = Long.MIN_VALUE
        previousPaceBand = null; candidateBand = null
    }

    private fun append(points: List<RunPoint>) {
        for (index in count until points.size) {
            val point = points[index]
            val previous = lastPoint
            val span = previous?.let { point.timestampMs - it.timestampMs } ?: 0
            val usable = point.hasValidCoordinates() && point.accuracyMeters.isFinite() && point.accuracyMeters in 0f..30f
            val connected = usable && previous != null && previous.hasValidCoordinates() &&
                point.segment == previous.segment && span in 1L..15_000L
            if (connected) distance += RunMetrics.distanceMeters(listOf(previous!!, point)) else recent.clear()
            if (usable) {
                recent.addLast(point to distance)
                // Keep the first sample preceding the 30-second boundary for stable pace.
                while (recent.size > 2 && point.timestampMs - recent.elementAt(1).first.timestampMs >= 30_000) recent.removeFirst()
            }
            lastPoint = point
        }
        count = points.size
    }

    private fun currentPace(): Double? {
        if (recent.size < 2) return null
        val a = recent.first(); val b = recent.last()
        val span = b.first.timestampMs - a.first.timestampMs
        val covered = b.second - a.second
        if (span !in 25_000..45_000 || covered < 15.0) return null
        return (span / covered).takeIf { it.isFinite() && it in 60.0..3600.0 }
    }
}
