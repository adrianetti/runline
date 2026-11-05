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
    data object EasingAfterHill : CoachCue
    data class LapCompleted(val number: Int, val comparison: LapComparison?) : CoachCue
}
enum class EncouragementKind { SETTLING_IN, STEADY, HALFWAY, FINAL_STRETCH, ENDURANCE, UPHILL, DOWNHILL, ON_TARGET, CONSISTENT }
enum class CoachTerrain { UNKNOWN, LEVEL, UPHILL, DOWNHILL }

/** Immutable delivery-time facts; a deferred prompt must still describe the current run. */
data class CoachSituation(
    val eligible: Boolean = false,
    val distanceMeters: Double = 0.0,
    val distanceGoalMeters: Double? = null,
    val paceSeconds: Double? = null,
    val targetPaceSeconds: Double? = null,
    val terrain: CoachTerrain = CoachTerrain.UNKNOWN,
    val hillRecovery: Boolean = false,
    val goalReached: Boolean = false,
    val completedLaps: Int = 0,
    val paceMinSeconds: Double? = null,
    val paceMaxSeconds: Double? = null,
    val gpsFresh: Boolean = true,
    val timeGoal: Boolean = false,
    val allowSpeedUp: Boolean = true,
    val gradePercent: Double? = null,
    val goalProgress: Double? = null,
    val steadyPace: Boolean = false,
    val activeMs: Long = 0L,
    val terrainSource: TerrainSource = TerrainSource.UNKNOWN,
) {
    fun allows(cue: CoachCue): Boolean {
        if (!eligible) return false
        if (!gpsFresh) return cue is CoachCue.GoalReached && timeGoal && goalReached
        return when (cue) {
            is CoachCue.GoalReached -> goalReached
            is CoachCue.Kilometre -> distanceMeters >= cue.completed * 1000.0 && distanceMeters < (cue.completed + 1) * 1000.0
            CoachCue.OneKilometreRemaining -> distanceGoalMeters?.let { it - distanceMeters in 0.0..1000.0 && !goalReached } == true
            is CoachCue.LapCompleted -> cue.number == completedLaps
            is CoachCue.Terrain -> terrain == cue.kind
            CoachCue.EasingAfterHill -> terrain == CoachTerrain.LEVEL && hillRecovery
            is CoachCue.Encouragement -> !goalReached && when (cue.kind) {
                EncouragementKind.UPHILL -> terrain == CoachTerrain.UPHILL
                EncouragementKind.DOWNHILL -> terrain == CoachTerrain.DOWNHILL
                EncouragementKind.ON_TARGET -> terrain == CoachTerrain.LEVEL && !hillRecovery && paceSeconds != null && targetPaceSeconds != null &&
                    CoachPaceBand.classify(paceSeconds, (paceMinSeconds ?: targetPaceSeconds)..(paceMaxSeconds ?: targetPaceSeconds)) == 0
                EncouragementKind.CONSISTENT -> steadyPace && terrain == CoachTerrain.LEVEL
                EncouragementKind.HALFWAY -> goalProgress?.let { it >= .5 && it < .8 } == true
                EncouragementKind.FINAL_STRETCH -> goalProgress?.let { it >= .8 && it < 1 } == true
                EncouragementKind.ENDURANCE -> activeMs >= 30 * 60_000
                else -> true
            }
            else -> {
                val pace = paceSeconds ?: return false
                val target = targetPaceSeconds ?: return false
                if (goalReached || hillRecovery || terrain in setOf(CoachTerrain.UPHILL, CoachTerrain.DOWNHILL)) return false
                val band = CoachPaceBand.classify(pace, (paceMinSeconds ?: target)..(paceMaxSeconds ?: target))
                when (cue) {
                    CoachCue.SlowerThanTarget -> band == 1 && allowSpeedUp
                    CoachCue.FasterThanTarget -> band == -1
                    CoachCue.BackOnPace -> band == 0
                    else -> false
                }
            }
        }
    }
}

/** A dead band around the chosen range prevents opposing instructions from GPS noise. */
internal object CoachPaceBand {
    fun classify(pace: Double, range: ClosedFloatingPointRange<Double>): Int? {
        val middle = (range.start + range.endInclusive) / 2
        val width = range.endInclusive - range.start
        val margin = if (width > 0) max(8.0, middle * .025) else max(18.0, middle * .06)
        if (pace > range.endInclusive + margin) return 1
        if (pace < range.start - margin) return -1
        val recovered = if (width > 0) {
            val inset = minOf(5.0, width * .15)
            pace in (range.start + inset)..(range.endInclusive - inset)
        } else abs(pace - middle) <= max(10.0, middle * .035)
        return if (recovered) 0 else null
    }
}

/** Incremental, conservative decisions. Recovered history never becomes a backlog of cues. */
class CoachEngine(private val config: CoachConfig, initialRun: RunSession? = null,
                  private val terrainPoint: (RunPoint) -> RunPoint = { it.copy(altitudeMeters = null, verticalAccuracyMeters = null) },
                  private val terrainRevision: () -> Long = { 0L }) {
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
    private val terrainTrend = TerrainTrend(TerrainSource.TERRAIN_MAP)
    private var terrainEstimate = TerrainEstimate()
    private var mapRevision = Long.MIN_VALUE
    private var lastHillMs: Long? = null
    private var hillRecoveryAnnounced = false
    private var paceAnchor: Double? = null
    private var steadySince = 0L
    var situation = CoachSituation()
        private set

    init { if (initialRun != null) rebase(initialRun) }
    fun pause() {
        recent.clear(); paceAnchor = null; steadySince = 0; previousPaceBand = null; candidateBand = null
        terrainCandidate = CoachTerrain.UNKNOWN; announcedTerrain = CoachTerrain.UNKNOWN
        terrainTrend.reset(); terrainEstimate = TerrainEstimate(); situation = CoachSituation()
    }

    fun update(run: RunSession, recording: Boolean, gpsFresh: Boolean): CoachCue? {
        if (runId != run.id || count > run.points.size || (count > 0 && run.points[count - 1] !== lastPoint)) {
            rebase(run); return null
        }
        if (mapRevision != terrainRevision()) {
            mapRevision = terrainRevision(); terrainTrend.reset(); terrainEstimate = TerrainEstimate()
            run.points.take(count).takeLast(180).forEach { terrainEstimate = terrainTrend.update(terrainPoint(it)) }
            terrainCandidate = CoachTerrain.UNKNOWN; announcedTerrain = CoachTerrain.UNKNOWN
            lastHillMs = null; hillRecoveryAnnounced = false
        }
        append(run.points)
        if (!recording || !config.enabled || !config.isValid()) {
            recent.clear(); candidateBand = null; terrainCandidate = CoachTerrain.UNKNOWN
            terrainTrend.reset(); terrainEstimate = TerrainEstimate(); situation = CoachSituation()
            return null
        }
        val now = run.activeDurationMs
        val goal = config.distanceGoalMeters
        val timeGoal = config.durationGoalMs
        if (!gpsFresh) {
            recent.clear(); candidateBand = null; terrainCandidate = CoachTerrain.UNKNOWN
            terrainTrend.reset(); terrainEstimate = TerrainEstimate()
            val crossedTime = timeGoal != null && !goalAnnounced && now >= timeGoal
            if (crossedTime) goalAnnounced = true
            situation = CoachSituation(eligible = timeGoal != null, distanceMeters = distance,
                goalReached = goalAnnounced, gpsFresh = false, timeGoal = timeGoal != null)
            return if (crossedTime) emit(CoachCue.GoalReached(null), now) else null
        }
        val newKm = floor(distance / 1000.0).toInt()
        val crossedKm = newKm > crossedKilometre
        crossedKilometre = max(crossedKilometre, newKm)
        val crossedGoal = !goalAnnounced && ((goal != null && distance >= goal) || (timeGoal != null && now >= timeGoal))
        val crossedRemaining = goal != null && goal > 1000 && !remainingAnnounced && distance >= goal - 1000
        if (crossedGoal) goalAnnounced = true
        if (crossedRemaining) remainingAnnounced = true
        val report = if (config.lapFeedback) { laps.update(run.points); laps.report() } else null
        val newLap = report != null && report.laps.size > lapCount
        lapCount = report?.laps?.size ?: lapCount
        val pace = currentPace()
        val terrain = currentTerrain()
        if (terrain == CoachTerrain.UPHILL || terrain == CoachTerrain.DOWNHILL) {
            lastHillMs = now; hillRecoveryAnnounced = false
        }
        val hillRecovery = lastHillMs?.let { now - it < 120_000 } == true
        if (pace == null || paceAnchor == null || abs(pace - paceAnchor!!) > paceAnchor!! * .06 || terrain != CoachTerrain.LEVEL) {
            paceAnchor = pace; steadySince = now
        }
        val progress = goal?.let { distance / it } ?: timeGoal?.let { now.toDouble() / it }
        val steady = pace != null && now - steadySince >= 90_000
        situation = CoachSituation(true, distance, goal, pace, config.targetPaceSeconds, terrain,
            hillRecovery, goalAnnounced, lapCount, config.paceTargetRange?.start, config.paceTargetRange?.endInclusive,
            gpsFresh = true, timeGoal = timeGoal != null, allowSpeedUp = config.effortIntent != SmartRunEffort.RECOVERY,
            gradePercent = terrainEstimate.gradePercent, goalProgress = progress, steadyPace = steady, activeMs = now, terrainSource = terrainEstimate.source)
        if (crossedGoal) return emit(CoachCue.GoalReached(
            if (config.goal == CoachGoal.DISTANCE_TIME) now <= config.targetMinutes * 60_000 else null), now)
        // Delivery, deferral and deduplication belong to the shared local/AI speech schedule.
        if (crossedRemaining) return emit(CoachCue.OneKilometreRemaining, now)
        if (crossedKm && config.kilometreAnnouncements) return emit(CoachCue.Kilometre(newKm, RunMetrics.paceSecondsPerKm(distance, now)), now)
        if (newLap) return emit(CoachCue.LapCompleted(lapCount, report?.latestComparison), now)
        if (pace == null) { candidateBand = null; terrainCandidate = CoachTerrain.UNKNOWN; return null }
        if (terrain != terrainCandidate) { terrainCandidate = terrain; terrainSinceMs = now }
        // Uphill/downhill feedback replaces speed-up advice, even when its own switch is off.
        if (config.terrainFeedback && terrain in setOf(CoachTerrain.UPHILL, CoachTerrain.DOWNHILL) &&
            terrain != announcedTerrain && now - terrainSinceMs >= 20_000 && canSpeak(now, 40_000) &&
            (lastTerrainMs == Long.MIN_VALUE || now - lastTerrainMs >= 180_000)) {
            announcedTerrain = terrain; lastTerrainMs = now
            return emit(CoachCue.Terrain(terrain), now)
        }
        if (terrain == CoachTerrain.LEVEL && now - terrainSinceMs >= 30_000) announcedTerrain = CoachTerrain.UNKNOWN
        if (config.terrainFeedback && terrain == CoachTerrain.LEVEL && hillRecovery && !hillRecoveryAnnounced &&
            now - terrainSinceMs >= 30_000 && canSpeak(now, 85_000)) {
            hillRecoveryAnnounced = true
            return emit(CoachCue.EasingAfterHill, now)
        }
        val paceCue = paceCue(now, pace, terrain)
        if (paceCue != null) return emit(paceCue, now)
        val progressMilestone = progress?.let { (!finalStretch && it >= .8) || (!halfway && it >= .5) } == true
        val moraleInterval = if (progressMilestone) 85_000 else 180_000
        if (config.encouragement && !goalAnnounced && canSpeak(now, 85_000) && now - lastMoraleMs >= moraleInterval &&
            now - baselineActiveMs >= 120_000) {
            val kind = when {
                progress != null && progress >= .8 && !finalStretch -> { finalStretch = true; EncouragementKind.FINAL_STRETCH }
                progress != null && progress >= .5 && !halfway -> { halfway = true; EncouragementKind.HALFWAY }
                !startedEncouragement -> { startedEncouragement = true; EncouragementKind.SETTLING_IN }
                terrain == CoachTerrain.UPHILL -> EncouragementKind.UPHILL
                terrain == CoachTerrain.DOWNHILL -> EncouragementKind.DOWNHILL
                terrain == CoachTerrain.LEVEL && config.paceTargetRange?.let { CoachPaceBand.classify(pace, it) == 0 } == true && !hillRecovery -> EncouragementKind.ON_TARGET
                steady -> EncouragementKind.CONSISTENT
                now >= 30 * 60_000 -> EncouragementKind.ENDURANCE
                else -> EncouragementKind.STEADY
            }
            lastMoraleMs = now
            return emit(CoachCue.Encouragement(kind), now)
        }
        return null
    }

    private fun paceCue(now: Long, pace: Double, terrain: CoachTerrain): CoachCue? {
        val range = config.paceTargetRange ?: return null
        if (!config.paceFeedback || goalAnnounced || now - baselineActiveMs < 60_000) return null
        if (terrain == CoachTerrain.UPHILL || terrain == CoachTerrain.DOWNHILL || situation.hillRecovery) { candidateBand = null; return null }
        val band = CoachPaceBand.classify(pace, range) ?: candidateBand ?: previousPaceBand ?: 0
        if (band == 1 && config.effortIntent == SmartRunEffort.RECOVERY) { candidateBand = null; return null }
        if (candidateBand != band) { candidateBand = band; candidateSinceMs = now; return null }
        val reminderInterval = if (band != 0 && band == previousPaceBand) 300_000 else 150_000
        if (now - candidateSinceMs < 25_000 || !canSpeak(now, 85_000) ||
            (lastPaceSpeechMs != Long.MIN_VALUE && now - lastPaceSpeechMs < reminderInterval)) return null
        if (band == 0 && previousPaceBand == null) { previousPaceBand = 0; return null }
        if (band == 0 && previousPaceBand == 0) return null
        previousPaceBand = band; lastPaceSpeechMs = now
        return when (band) { 1 -> CoachCue.SlowerThanTarget; -1 -> CoachCue.FasterThanTarget; else -> CoachCue.BackOnPace }
    }
    private fun canSpeak(now: Long, interval: Long) = lastSpeechMs == Long.MIN_VALUE || now - lastSpeechMs >= interval
    private fun emit(cue: CoachCue, now: Long): CoachCue { lastSpeechMs = now; return cue }
    private fun rebase(run: RunSession) {
        runId = run.id; count = 0; lastPoint = null; distance = 0.0; recent.clear()
        terrainTrend.reset(); terrainEstimate = TerrainEstimate(); lastHillMs = null; hillRecoveryAnnounced = false; situation = CoachSituation()
        append(run.points); laps = LapTracker().apply { update(run.points) }; lapCount = laps.report().laps.size
        recent.clear(); terrainTrend.reset(); terrainEstimate = TerrainEstimate(); baselineActiveMs = run.activeDurationMs
        crossedKilometre = floor(distance / 1000.0).toInt()
        remainingAnnounced = config.distanceGoalMeters?.let { distance >= it - 1000 } ?: false
        goalAnnounced = config.distanceGoalMeters?.let { distance >= it } == true ||
            config.durationGoalMs?.let { run.activeDurationMs >= it } == true
        halfway = config.distanceGoalMeters?.let { distance >= it * .5 } ?: config.durationGoalMs?.let { run.activeDurationMs >= it * .5 } ?: false
        finalStretch = config.distanceGoalMeters?.let { distance >= it * .8 } ?: config.durationGoalMs?.let { run.activeDurationMs >= it * .8 } ?: false
        lastSpeechMs = Long.MIN_VALUE; lastPaceSpeechMs = Long.MIN_VALUE; lastTerrainMs = Long.MIN_VALUE
        lastMoraleMs = run.activeDurationMs; startedEncouragement = run.activeDurationMs > 180_000
        previousPaceBand = null; candidateBand = null; terrainCandidate = CoachTerrain.UNKNOWN; announcedTerrain = CoachTerrain.UNKNOWN
    }
    private fun append(points: List<RunPoint>) {
        for (index in count until points.size) {
            val point = points[index]
            terrainEstimate = terrainTrend.update(terrainPoint(point))
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
        return when (terrainEstimate.direction) {
            TerrainDirection.UPHILL -> CoachTerrain.UPHILL
            TerrainDirection.DOWNHILL -> CoachTerrain.DOWNHILL
            TerrainDirection.FLAT -> CoachTerrain.LEVEL
            TerrainDirection.UNKNOWN -> CoachTerrain.UNKNOWN
        }
    }
}
