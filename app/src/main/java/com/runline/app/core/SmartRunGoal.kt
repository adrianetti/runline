package com.runline.app.core

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor

enum class SmartRunGoalType { REST, DISTANCE, TIME, DISTANCE_TIME }
enum class SmartRunEffort { EASY, RECOVERY, STEADY }

data class SmartRunHistoryRun(
    val date: LocalDate, val distanceKm: Double, val id: String = "", val effort: Int? = null,
    val activeDurationMs: Long = 0, val elevationGainMeters: Double? = null, val startedAtMs: Long = 0,
)

data class SmartRunSuggestion(
    val goalType: SmartRunGoalType,
    val effort: SmartRunEffort = SmartRunEffort.EASY,
    val targetDistanceKm: Double? = null,
    val minDistanceKm: Double? = null,
    val maxDistanceKm: Double? = null,
    val paceMinSecondsPerKm: Int? = null,
    val paceMaxSecondsPerKm: Int? = null,
    val durationMinutes: Double? = null,
    val rationale: String = "",
)

data class SmartRunLimits(
    val minDistanceKm: Double,
    val maxDistanceKm: Double,
    val minDurationMinutes: Double,
    val maxDurationMinutes: Double,
    val fastestPaceSecondsPerKm: Int?,
    val slowestPaceSecondsPerKm: Int?,
    val allowSteady: Boolean,
    val recoveryOnly: Boolean,
    val restRecommended: Boolean,
    val observedWeeklyKm: Double,
    val availableWeeklyKm: Double,
    /** A time-only run can cover more distance than expected; use the faster observed pace to bound it. */
    val maxTimeOnlyDurationMinutes: Double = maxDurationMinutes,
)

/**
 * Optional session suggestions from recorded running only. These are conservative app
 * heuristics, not a fitness assessment, medical advice or an injury-prevention guarantee.
 * There is no calendar, race date, missed-run debt or automatic increase from a stated ambition.
 */
object SmartRunGoal {
    private fun clean(history: List<SmartRunHistoryRun>, today: LocalDate): List<SmartRunHistoryRun> = history
        .filter { it.date <= today && it.distanceKm.isFinite() && it.distanceKm > 0 && it.distanceKm <= 300 }
        .map { it.copy(effort = it.effort?.takeIf { n -> n in 1..10 }, activeDurationMs = it.activeDurationMs.coerceAtLeast(0),
            elevationGainMeters = it.elevationGainMeters?.takeIf { gain -> gain.isFinite() && gain >= 0 }) }
        .sortedWith(compareBy<SmartRunHistoryRun> { it.date }.thenBy { it.startedAtMs }.thenBy { it.id }.thenBy { it.distanceKm })
        .distinctBy { if (it.id.isNotEmpty()) "id:${it.id}" else "${it.date}:${it.startedAtMs}:${it.distanceKm}:${it.activeDurationMs}" }

    fun limits(history: List<SmartRunHistoryRun>, today: LocalDate): SmartRunLimits {
        val all = clean(history, today)
        val actualWeek = all.filter { it.date >= today.minusDays(6) }
        val weeklyKm = actualWeek.sumOf { it.distanceKm }
        val runs = all.filter { it.date >= today.minusDays(29) && (it.distanceKm >= .5 || it.activeDurationMs >= 300_000) }
        val latest = runs.lastOrNull()
        val longest = runs.maxOfOrNull { it.distanceKm }
        val typical = runs.map { it.distanceKm }.sorted().let { if (it.isEmpty()) null else it[it.size / 2] }
        val weeks = runs.map { ChronoUnit.DAYS.between(it.date, today) / 7 }.distinct().size
        val established = runs.size >= 4 && weeks >= 3
        val lastGap = latest?.let { ChronoUnit.DAYS.between(it.date, today) }
        val hard = latest?.let { (it.effort ?: 0) >= 7 && lastGap!! <= 3 } == true || actualWeek.count { (it.effort ?: 0) >= 7 } >= 2
        val prolonged = latest?.let { lastGap!! <= 2 && (it.activeDurationMs >= 120 * 60_000L ||
            it.elevationGainMeters?.let { gain -> gain >= 400 && gain / it.distanceKm >= 35 } == true) } == true
        val alreadyRan = actualWeek.any { it.date == today }
        val recovery = hard || prolonged || (lastGap != null && lastGap >= 7) || alreadyRan
        val recent28 = runs.filter { it.date >= today.minusDays(27) }
        val average28 = recent28.sumOf { it.distanceKm } / 4.0
        // Sparse evidence grows to at most three demonstrated-size days, never N times one outlier.
        val weeklyCeiling = when {
            longest == null -> 3.0
            established -> maxOf(average28 * 1.15, weeklyKm)
            else -> maxOf(longest * 1.65, (typical ?: longest) *
                (actualWeek.map { it.date }.distinct().size + 1).coerceIn(2, 3) * .9)
        }.coerceAtMost(100.0)
        var available = (weeklyCeiling - weeklyKm).coerceAtLeast(0.0)
        val comfortable = runs.filter { it.date >= today.minusDays(13) && it.effort != null && it.effort <= 4 }
        val allowSteady = established && comfortable.size >= 2 && !recovery && (lastGap ?: 99) <= 6
        var maximum = minOf(available, (longest ?: 3.0) * if (allowSteady) 1.05 else 1.0)
        if (recovery && longest != null) maximum = minOf(maximum, longest * when {
            lastGap != null && lastGap >= 14 -> .6
            hard || prolonged -> .65
            else -> .8
        })
        maximum = down(maximum.coerceAtMost(30.0))
        val usefulMinimum = if (longest == null) .5 else minOf(3.0, maxOf(.5, longest * .35))
        if (maximum < usefulMinimum) maximum = 0.0 // Rest instead of filling residual budget with scraps.
        val observedPaces = runs.mapNotNull { run ->
            (run.activeDurationMs / 1000.0 / run.distanceKm).takeIf { run.activeDurationMs > 0 && it in 120.0..1800.0 }
        }
        val easyPaces = comfortable.mapNotNull { run ->
            (run.activeDurationMs / 1000.0 / run.distanceKm).takeIf { run.activeDurationMs > 0 && it in 180.0..1200.0 &&
                (run.elevationGainMeters ?: 0.0) / run.distanceKm < 25.0 }
        }.sorted()
        val typicalEasy = easyPaces.takeIf { it.size >= 2 }?.let { it[it.size / 2] }
        val fastest = typicalEasy?.let { ceil(it * if (recovery) 1.08 else 1.0).toInt() }
        val slowest = typicalEasy?.let { ceil(it * if (recovery) 1.55 else 1.4).toInt().coerceAtMost(3600) }
        val measuredDuration = runs.maxOfOrNull { it.activeDurationMs / 60_000.0 }?.takeIf { it > 0 }
        val durationCap = if (maximum == 0.0) 0.0 else minOf(180.0,
            if (slowest != null) maximum * slowest / 60.0 else (measuredDuration ?: 20.0) * if (recovery) .7 else 1.0)
        val timeOnlyCap = if (maximum == 0.0) 0.0 else minOf(durationCap,
            observedPaces.minOrNull()?.let { maximum * it / 60.0 } ?: 20.0)
        if (maximum == 0.0) available = 0.0
        return SmartRunLimits(if (maximum > 0) down(minOf(maximum, usefulMinimum)) else 0.0,
            maximum, 5.0, down(durationCap), fastest, slowest, allowSteady, recovery,
            hard || prolonged || alreadyRan || maximum == 0.0, weeklyKm, down(available), down(timeOnlyCap))
    }

    fun suggest(history: List<SmartRunHistoryRun>, today: LocalDate, spanish: Boolean = false): SmartRunSuggestion {
        val limits = limits(history, today)
        if (limits.restRecommended) return SmartRunSuggestion(SmartRunGoalType.REST,
            rationale = if (spanish) "Ya has entrenado o el esfuerzo y volumen recientes aconsejan descansar. No hay kilómetros pendientes que compensar."
                else "You have already trained or recent effort and volume favour rest. There is no missed mileage to make up.")
        val runs = clean(history, today).filter { it.date >= today.minusDays(29) && it.distanceKm >= .5 }
        if (runs.isEmpty()) return SmartRunSuggestion(SmartRunGoalType.TIME, durationMinutes = minOf(15.0, limits.maxTimeOnlyDurationMinutes),
            rationale = if (spanish) "Hay pocas carreras recientes. Haz una sesión breve y cómoda; no se supone ningún ritmo."
                else "Recent running history is limited. Keep this short and comfortable; no pace is assumed.")
        val typical = runs.map { it.distanceKm }.sorted().let { it[it.size / 2] }
        val target = down(minOf(limits.maxDistanceKm, typical * if (limits.recoveryOnly) .65 else .85)).coerceAtLeast(limits.minDistanceKm)
        return SmartRunSuggestion(SmartRunGoalType.DISTANCE, if (limits.recoveryOnly) SmartRunEffort.RECOVERY else SmartRunEffort.EASY,
            target, down(maxOf(limits.minDistanceKm, target * .8)), down(minOf(limits.maxDistanceKm, maxOf(target, target * 1.1))),
            limits.fastestPaceSecondsPerKm?.let { ceil(it * 1.03).toInt().coerceAtMost(limits.slowestPaceSecondsPerKm!!) },
            limits.slowestPaceSecondsPerKm,
            rationale = if (limits.recoveryOnly) {
                if (spanish) "Una carrera más corta y cómoda respeta tu esfuerzo reciente o la pausa. Acorta o descansa si lo necesitas."
                else "A shorter comfortable run respects your recent effort or time away. Shorten or rest if needed."
            } else if (spanish) "Se basa en tus distancias recientes y el margen de carga semanal. Mantén un esfuerzo cómodo; el rango es opcional."
                else "This uses your recent run distances and remaining weekly load. Keep the effort comfortable; the range is optional.")
    }

    fun validate(suggestion: SmartRunSuggestion, history: List<SmartRunHistoryRun>, today: LocalDate): Boolean {
        val limits = limits(history, today)
        if (suggestion.rationale.trim().length !in 1..500) return false
        val distances = listOf(suggestion.minDistanceKm, suggestion.targetDistanceKm, suggestion.maxDistanceKm)
        val hasDistance = suggestion.goalType in setOf(SmartRunGoalType.DISTANCE, SmartRunGoalType.DISTANCE_TIME)
        val hasTime = suggestion.goalType in setOf(SmartRunGoalType.TIME, SmartRunGoalType.DISTANCE_TIME)
        if (suggestion.goalType == SmartRunGoalType.REST) return distances.all { it == null } && suggestion.durationMinutes == null &&
            suggestion.paceMinSecondsPerKm == null && suggestion.paceMaxSecondsPerKm == null && suggestion.effort != SmartRunEffort.STEADY
        if (limits.maxDistanceKm <= 0 || (limits.recoveryOnly && suggestion.effort != SmartRunEffort.RECOVERY)) return false
        if (suggestion.effort == SmartRunEffort.STEADY && !limits.allowSteady) return false
        if (hasDistance) {
            if (distances.any { it == null || !it.isFinite() }) return false
            if (suggestion.minDistanceKm!! < limits.minDistanceKm || suggestion.maxDistanceKm!! > limits.maxDistanceKm ||
                suggestion.targetDistanceKm!! !in suggestion.minDistanceKm..suggestion.maxDistanceKm) return false
        } else if (distances.any { it != null }) return false
        if (hasTime) {
            val minutes = suggestion.durationMinutes ?: return false
            val cap = if (suggestion.goalType == SmartRunGoalType.TIME) limits.maxTimeOnlyDurationMinutes else limits.maxDurationMinutes
            if (!minutes.isFinite() || minutes !in limits.minDurationMinutes..cap) return false
        } else if (suggestion.durationMinutes != null) return false
        val low = suggestion.paceMinSecondsPerKm; val high = suggestion.paceMaxSecondsPerKm
        if ((low == null) != (high == null)) return false
        if (low != null && high != null) {
            val fastest = limits.fastestPaceSecondsPerKm ?: return false
            val slowest = limits.slowestPaceSecondsPerKm ?: return false
            if (suggestion.goalType == SmartRunGoalType.DISTANCE_TIME || low !in fastest..slowest || high !in low..slowest) return false
        }
        if (suggestion.goalType == SmartRunGoalType.DISTANCE_TIME) {
            val fastest = limits.fastestPaceSecondsPerKm ?: return false
            val slowest = limits.slowestPaceSecondsPerKm ?: return false
            val requiredPace = suggestion.durationMinutes!! * 60 / suggestion.targetDistanceKm!!
            if (requiredPace !in fastest.toDouble()..slowest.toDouble()) return false
        }
        return true
    }

    /** Pure mapping. Only the explicit UI Use action enables/saves this configuration. */
    fun toCoachConfig(suggestion: SmartRunSuggestion, base: CoachConfig): CoachConfig {
        val type = when (suggestion.goalType) {
            SmartRunGoalType.REST -> CoachGoal.NONE
            SmartRunGoalType.DISTANCE -> CoachGoal.DISTANCE
            SmartRunGoalType.TIME -> CoachGoal.TIME
            SmartRunGoalType.DISTANCE_TIME -> CoachGoal.DISTANCE_TIME
        }
        val result = base.copy(goal = type, distanceKm = suggestion.targetDistanceKm ?: base.distanceKm,
            targetMinutes = suggestion.durationMinutes ?: base.targetMinutes,
            paceMinSecondsPerKm = suggestion.paceMinSecondsPerKm, paceMaxSecondsPerKm = suggestion.paceMaxSecondsPerKm,
            effortIntent = suggestion.effort.takeUnless { type == CoachGoal.NONE })
        require(result.isValid())
        return result
    }

    private fun down(value: Double) = floor((value + 1e-9) * 10) / 10
}
