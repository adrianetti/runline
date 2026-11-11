package com.runline.app.core

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max

sealed interface CoachCue {
    /** [splitPaceSeconds] covers only the kilometre just completed, in moving time. */
    data class Kilometre(val completed: Int, val averagePaceSeconds: Double?, val splitPaceSeconds: Double? = null,
                         val previousSplitPaceSeconds: Double? = null) : CoachCue
    data object OneKilometreRemaining : CoachCue
    data class GoalReached(val withinTime: Boolean?, val facts: CoachFacts? = null) : CoachCue
    data object SlowerThanTarget : CoachCue
    data object FasterThanTarget : CoachCue
    data object BackOnPace : CoachCue
    data class Encouragement(val kind: EncouragementKind, val facts: CoachFacts? = null) : CoachCue
    data class Terrain(val kind: CoachTerrain) : CoachCue
    data object EasingAfterHill : CoachCue
    data class LapCompleted(val number: Int, val comparison: LapComparison?) : CoachCue
}
enum class EncouragementKind { SETTLING_IN, STEADY, HALFWAY, FINAL_STRETCH, ENDURANCE, UPHILL, DOWNHILL, ON_TARGET, CONSISTENT, PROGRESS }

/**
 * Measured numbers a cue may speak, captured when it is queued. Moving time excludes
 * detected stops when auto-pause is on. Projections assume the current average holds.
 */
data class CoachFacts(
    val distanceMeters: Double,
    val movingMs: Long,
    val averagePaceSeconds: Double? = null,
    val recentPaceSeconds: Double? = null,
    val remainingMs: Long? = null,
    val remainingMeters: Double? = null,
    val projectedMeters: Double? = null,
    val projectedMs: Long? = null,
) {
    /** More than 55% of a time or distance goal is done: "halfway" would no longer be true. */
    val pastHalfway: Boolean get() = (remainingMs?.let { movingMs.toDouble() / (movingMs + it) }
        ?: remainingMeters?.let { distanceMeters / (distanceMeters + it) })?.let { it > .55 } == true
}
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
    /** Only after a real climb, never after a gentle downhill or a GPS wobble. */
    val climbRecovery: Boolean = false,
    val stopped: Boolean = false,
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
            CoachCue.EasingAfterHill -> terrain == CoachTerrain.LEVEL && climbRecovery
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
                  private val terrainRevision: () -> Long = { 0L },
                  /** Exclude detected standstills from goal time, kilometre splits and averages. */
                  private val autoPause: Boolean = true) {
    private var runId: String? = null
    private var count = 0
    private var lastPoint: RunPoint? = null
    private var distance = 0.0
    private val recent = ArrayDeque<Pair<RunPoint, Double>>()
    private var laps = LapTracker()
    private var lapCount = 0
    private var lastLapSpoken = -10
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
    private val structureGuard = TerrainStructureGuard.Live()
    private var terrainEstimate = TerrainEstimate()
    private var mapRevision = Long.MIN_VALUE
    private var lastHillMs: Long? = null
    private var hillRecoveryAnnounced = false
    private var paceAnchor: Double? = null
    private var steadySince = 0L
    private var lastEnduranceMs = Long.MIN_VALUE / 2
    private var climbMeters = 0.0
    private var climbMaxGrade = 0.0
    private var climbLastDistance: Double? = null
    private var climbQualified = false
    private var lastClimbMs: Long? = null
    private var wasStopped = false
    private var resumedAtMs = Long.MIN_VALUE
    private val kilometreClock = mutableListOf<Long?>(0L)
    var situation = CoachSituation()
        private set

    init { if (initialRun != null) rebase(initialRun) }
    fun pause() {
        recent.clear(); paceAnchor = null; steadySince = 0; previousPaceBand = null; candidateBand = null
        terrainCandidate = CoachTerrain.UNKNOWN; announcedTerrain = CoachTerrain.UNKNOWN
        terrainTrend.reset(); structureGuard.reset(); terrainEstimate = TerrainEstimate(); situation = CoachSituation()
    }

    /** [stoppedSinceMs] is the recorder's live standstill signal, in GPS wall-clock time. */
    fun update(run: RunSession, recording: Boolean, gpsFresh: Boolean, stoppedSinceMs: Long? = null): CoachCue? {
        if (runId != run.id || count > run.points.size || (count > 0 && run.points[count - 1] !== lastPoint)) {
            rebase(run); return null
        }
        if (mapRevision != terrainRevision()) {
            mapRevision = terrainRevision(); terrainTrend.reset(); structureGuard.reset(); terrainEstimate = TerrainEstimate()
            TerrainStructureGuard.apply(run.points.take(count).takeLast(180).map(terrainPoint)).forEach { terrainEstimate = terrainTrend.update(it) }
            structureGuard.reset()
            terrainCandidate = CoachTerrain.UNKNOWN; announcedTerrain = CoachTerrain.UNKNOWN
            lastHillMs = null; hillRecoveryAnnounced = false
        }
        append(run.points)
        if (!recording || !config.enabled || !config.isValid()) {
            recent.clear(); candidateBand = null; terrainCandidate = CoachTerrain.UNKNOWN
            terrainTrend.reset(); structureGuard.reset(); terrainEstimate = TerrainEstimate(); situation = CoachSituation()
            return null
        }
        val now = clock(run, stoppedSinceMs)
        val goal = config.distanceGoalMeters
        // Standing at a light or tying a shoe: say nothing, drop what was queued, and judge
        // pace again only from fresh movement once running resumes.
        if (stoppedSinceMs != null) {
            if (!wasStopped) { wasStopped = true; recent.clear(); candidateBand = null; paceAnchor = null }
            situation = CoachSituation(eligible = false, distanceMeters = distance, activeMs = now, stopped = true)
            return null
        }
        if (wasStopped) { wasStopped = false; resumedAtMs = now }
        val settled = resumedAtMs == Long.MIN_VALUE || now - resumedAtMs >= 20_000
        val timeGoal = config.durationGoalMs
        if (!gpsFresh) {
            recent.clear(); candidateBand = null; terrainCandidate = CoachTerrain.UNKNOWN
            terrainTrend.reset(); structureGuard.reset(); terrainEstimate = TerrainEstimate()
            val crossedTime = timeGoal != null && !goalAnnounced && now >= timeGoal
            if (crossedTime) goalAnnounced = true
            situation = CoachSituation(eligible = timeGoal != null, distanceMeters = distance,
                goalReached = goalAnnounced, gpsFresh = false, timeGoal = timeGoal != null)
            return if (crossedTime) emit(CoachCue.GoalReached(null), now) else null
        }
        val newKm = floor(distance / 1000.0).toInt()
        val crossedKm = newKm > crossedKilometre
        while (kilometreClock.size <= newKm) kilometreClock += if (kilometreClock.size == newKm) now else null
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
        if (terrain == CoachTerrain.UPHILL || terrain == CoachTerrain.DOWNHILL) lastHillMs = now
        // A meaningful climb: uphill distance actually measured as uphill (unknown gaps add
        // nothing), at least 60 m reaching 3%, or 120 m of steady climbing. Only that earns a
        // recovery cue; gentle 2% stretches and descents never do.
        if (terrain == CoachTerrain.UPHILL) {
            climbLastDistance?.let { climbMeters += (distance - it).coerceAtLeast(0.0) }
            climbLastDistance = distance
            climbMaxGrade = maxOf(climbMaxGrade, terrainEstimate.gradePercent ?: 0.0)
            if ((climbMeters >= 60.0 && climbMaxGrade >= 3.0) || climbMeters >= 120.0) { climbQualified = true; lastClimbMs = now; hillRecoveryAnnounced = false }
        } else {
            climbLastDistance = null
            if (terrain != CoachTerrain.UNKNOWN) { climbMeters = 0.0; climbMaxGrade = 0.0 }
        }
        val hillRecovery = lastHillMs?.let { now - it < 120_000 } == true
        val climbRecovery = climbQualified && lastClimbMs?.let { now - it < 120_000 } == true
        if (pace == null || paceAnchor == null || abs(pace - paceAnchor!!) > paceAnchor!! * .06 || terrain != CoachTerrain.LEVEL) {
            paceAnchor = pace; steadySince = now
        }
        val progress = goal?.let { distance / it } ?: timeGoal?.let { now.toDouble() / it }
        val steady = pace != null && now - steadySince >= 90_000
        situation = CoachSituation(true, distance, goal, pace, config.targetPaceSeconds, terrain,
            hillRecovery, goalAnnounced, lapCount, config.paceTargetRange?.start, config.paceTargetRange?.endInclusive,
            gpsFresh = true, timeGoal = timeGoal != null, allowSpeedUp = config.effortIntent != SmartRunEffort.RECOVERY,
            gradePercent = terrainEstimate.gradePercent, goalProgress = progress, steadyPace = steady, activeMs = now, terrainSource = terrainEstimate.source,
            climbRecovery = climbRecovery)
        if (crossedGoal) return emit(CoachCue.GoalReached(
            if (config.goal == CoachGoal.DISTANCE_TIME) now <= config.targetMinutes * 60_000 else null, facts(now, pace)), now)
        // Delivery, deferral and deduplication belong to the shared local/AI speech schedule.
        if (crossedRemaining) return emit(CoachCue.OneKilometreRemaining, now)
        if (crossedKm && config.kilometreAnnouncements) return emit(CoachCue.Kilometre(newKm, RunMetrics.paceSecondsPerKm(distance, now),
            splitPace(newKm), splitPace(newKm - 1)), now)
        if (newLap) {
            // A circuit of about a kilometre ends seconds away from a kilometre announcement that
            // already gives the time and the change. The lap is then spoken only for what it alone
            // knows: which part of the circuit cost more this time.
            val nearKilometre = config.kilometreAnnouncements && report?.laps?.lastOrNull()?.let { abs(it.distanceMeters - 1000.0) <= 150.0 } == true
            // And not lap after lap: the same remark about a quarter of the circuit soon stops being news.
            if (!nearKilometre || (report?.latestComparison?.slowerSector != null && lapCount - lastLapSpoken >= 3)) {
                lastLapSpoken = lapCount
                return emit(CoachCue.LapCompleted(lapCount, report?.latestComparison), now)
            }
        }
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
        if (config.terrainFeedback && terrain == CoachTerrain.LEVEL && climbRecovery && !hillRecoveryAnnounced &&
            now - terrainSinceMs >= 30_000 && canSpeak(now, 85_000)) {
            hillRecoveryAnnounced = true; climbQualified = false
            return emit(CoachCue.EasingAfterHill, now)
        }
        if (!settled) return null
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
                // Prefer a short measured check-in over a generic line, but not right before or
                // after a kilometre announcement that already gives the numbers.
                distance >= 500 && distance % 1000 in 150.0..850.0 -> EncouragementKind.PROGRESS
                // A long-run nudge stays occasional; it must not replace the measured check-ins.
                now >= 30 * 60_000 && now - lastEnduranceMs >= 15 * 60_000 -> { lastEnduranceMs = now; EncouragementKind.ENDURANCE }
                distance >= 500 -> return null
                else -> EncouragementKind.STEADY
            }
            lastMoraleMs = now
            val withFacts = kind in setOf(EncouragementKind.HALFWAY, EncouragementKind.FINAL_STRETCH, EncouragementKind.PROGRESS)
            return emit(CoachCue.Encouragement(kind, if (withFacts) facts(now, pace) else null), now)
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
    private fun clock(run: RunSession, stoppedSinceMs: Long?): Long =
        if (!autoPause) run.activeDurationMs
        else StopDetection.movingDurationMs(run, StopDetection.detect(run.points, run.activityType), stoppedSinceMs, run.endedAtMs)

    /** Moving-time pace of one completed kilometre, when both of its boundaries were observed. */
    private fun splitPace(kilometre: Int): Double? {
        if (kilometre < 1) return null
        val end = kilometreClock.getOrNull(kilometre) ?: return null
        val start = kilometreClock.getOrNull(kilometre - 1) ?: return null
        return ((end - start) / 1000.0).takeIf { it in 60.0..3600.0 }
    }

    private fun facts(now: Long, pace: Double?): CoachFacts {
        val average = RunMetrics.paceSecondsPerKm(distance, now)
        val distanceGoal = config.distanceGoalMeters
        val timeGoal = config.durationGoalMs
        return CoachFacts(distance, now, average, pace,
            remainingMs = timeGoal?.let { (it - now).coerceAtLeast(0) },
            remainingMeters = distanceGoal?.let { (it - distance).coerceAtLeast(0.0) },
            projectedMeters = timeGoal?.takeIf { now >= 60_000 && distance > 0 }?.let { distance / now * it },
            projectedMs = distanceGoal?.takeIf { distance >= 200 && now > 0 }?.let { (now / distance * it).toLong() })
    }

    private fun canSpeak(now: Long, interval: Long) = lastSpeechMs == Long.MIN_VALUE || now - lastSpeechMs >= interval
    private fun emit(cue: CoachCue, now: Long): CoachCue { lastSpeechMs = now; return cue }
    private fun rebase(run: RunSession) {
        runId = run.id; count = 0; lastPoint = null; distance = 0.0; recent.clear()
        terrainTrend.reset(); structureGuard.reset(); terrainEstimate = TerrainEstimate(); lastHillMs = null; hillRecoveryAnnounced = false; situation = CoachSituation()
        append(run.points); laps = LapTracker().apply { update(run.points) }; lapCount = laps.report().laps.size
        val moving = clock(run, null)
        recent.clear(); terrainTrend.reset(); structureGuard.reset(); terrainEstimate = TerrainEstimate(); baselineActiveMs = moving
        kilometreClock.clear(); kilometreClock += 0L
        while (kilometreClock.size <= floor(distance / 1000.0).toInt()) kilometreClock += null
        climbMeters = 0.0; climbMaxGrade = 0.0; climbLastDistance = null; climbQualified = false; lastClimbMs = null
        wasStopped = false; resumedAtMs = Long.MIN_VALUE
        crossedKilometre = floor(distance / 1000.0).toInt()
        remainingAnnounced = config.distanceGoalMeters?.let { distance >= it - 1000 } ?: false
        goalAnnounced = config.distanceGoalMeters?.let { distance >= it } == true ||
            config.durationGoalMs?.let { moving >= it } == true
        halfway = config.distanceGoalMeters?.let { distance >= it * .5 } ?: config.durationGoalMs?.let { moving >= it * .5 } ?: false
        finalStretch = config.distanceGoalMeters?.let { distance >= it * .8 } ?: config.durationGoalMs?.let { moving >= it * .8 } ?: false
        lastSpeechMs = Long.MIN_VALUE; lastPaceSpeechMs = Long.MIN_VALUE; lastTerrainMs = Long.MIN_VALUE
        lastMoraleMs = moving; startedEncouragement = moving > 180_000
        previousPaceBand = null; candidateBand = null; terrainCandidate = CoachTerrain.UNKNOWN; announcedTerrain = CoachTerrain.UNKNOWN
    }
    private fun append(points: List<RunPoint>) {
        for (index in count until points.size) {
            val point = points[index]
            terrainEstimate = terrainTrend.update(structureGuard.apply(terrainPoint(point)))
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

/** Pure wording decisions, separate from Android resources so they stay testable. */
object CoachNarration {
    enum class Trend { FASTER, STEADY, SLOWER }

    /** Signed whole seconds versus the previous split; 0 within a 4 s noise band; null when unknown. */
    fun splitChange(split: Double?, previous: Double?): Int? {
        if (split == null || previous == null) return null
        val change = (split - previous).roundToIntSafely()
        return if (kotlin.math.abs(change) < 5) 0 else change
    }

    /** Recent moving pace compared with the run average. Lower seconds per km is faster. */
    fun trend(recent: Double?, average: Double?): Trend? {
        if (recent == null || average == null) return null
        return when {
            recent <= average - 8 -> Trend.FASTER
            recent >= average + 12 -> Trend.SLOWER
            else -> Trend.STEADY
        }
    }

    private fun Double.roundToIntSafely() = if (isFinite()) kotlin.math.round(this).toInt() else 0
}
