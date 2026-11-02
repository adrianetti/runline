package com.runline.app.training

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.round
import kotlin.math.roundToInt

enum class TrainingGoalType {
    FIVE_K, TEN_K, HALF_MARATHON, PERSONAL_PROGRESS, CUSTOM;
    companion object {
        fun forDistance(distanceKm: Double) = when (distanceKm) { 5.0 -> FIVE_K; 10.0 -> TEN_K; 21.1 -> HALF_MARATHON; else -> CUSTOM }
    }
}
enum class TrainingTiming { EXACT_DAY, TARGET_MONTH, ROLLING }

/** raceDate remains the exclusive scheduling boundary for constructor compatibility.
 * Only EXACT_DAY is a chosen race date; TARGET_MONTH retains month precision and
 * PERSONAL_PROGRESS uses a bounded rolling horizon, with no distance/race target. */
data class TrainingGoal(
    val distanceKm: Double, val raceDate: LocalDate, val daysPerWeek: Int, val weeklyKm: Double,
    val goalType: TrainingGoalType = TrainingGoalType.forDistance(distanceKm),
    val timing: TrainingTiming = TrainingTiming.EXACT_DAY,
) {
    val targetMonth: YearMonth? get() = if (timing == TrainingTiming.TARGET_MONTH) YearMonth.from(raceDate) else null
}
data class TrainingHistoryRun(val date: LocalDate, val distanceKm: Double)
data class TrainingBaseline(
    val weeklyKm: Double, val daysPerWeek: Int, val observedWeeklyKm: Double,
    val observedRuns: Int, val windowDays: Int, val established: Boolean,
)

object TrainingBaselineEstimator {
    /** Recorded volume is evidence, not a fitness assessment. Walks must be excluded by the caller. */
    fun infer(runs: List<TrainingHistoryRun>, today: LocalDate): TrainingBaseline {
        val recent = runs.filter { !it.date.isBefore(today.minusDays(27)) && !it.date.isAfter(today) && it.distanceKm.isFinite() && it.distanceKm > 0 }
        val weeks = recent.map { ChronoUnit.DAYS.between(it.date, today) / 7 }.distinct().size
        val established = recent.size >= 4 && weeks >= 3
        val lastWeek = recent.filter { !it.date.isBefore(today.minusDays(6)) }
        val window = if (established || lastWeek.isEmpty()) 28 else 7
        val observed = if (window == 7) lastWeek else recent
        val average = observed.sumOf { it.distanceKm } / (window / 7.0)
        return TrainingBaseline(
            if (established) (round(average * 10) / 10).coerceIn(5.0, 100.0) else 5.0,
            if (established) (recent.size / 4.0).roundToInt().coerceIn(2, 5) else 2,
            average, observed.size, window, established,
        )
    }
}
enum class SessionState { PLANNED, DONE, SKIPPED }
data class TrainingSession(val date: LocalDate, val distanceKm: Double, val kind: String, val note: String, val state: SessionState = SessionState.PLANNED, val activityId: String? = null)
data class TrainingPlan(val goal: TrainingGoal, val startsOn: LocalDate, val sessions: List<TrainingSession>, val summary: String, val updatedAtMs: Long, val source: String = "local", val lastAutomaticDate: String = "")

object TrainingPlanEngine {
    fun validateGoal(goal: TrainingGoal, today: LocalDate): Boolean {
        if (!goal.distanceKm.isFinite() || goal.daysPerWeek !in 2..5 || !goal.weeklyKm.isFinite() || goal.weeklyKm !in 5.0..100.0) return false
        val duration = ChronoUnit.DAYS.between(today, goal.raceDate)
        if (goal.goalType == TrainingGoalType.PERSONAL_PROGRESS) return goal.distanceKm == 0.0 && goal.timing == TrainingTiming.ROLLING && duration == 56L
        if (duration !in 28..364 || goal.distanceKm !in 1.0..50.0 || goal.timing == TrainingTiming.ROLLING) return false
        if (goal.timing == TrainingTiming.TARGET_MONTH && goal.raceDate != YearMonth.from(goal.raceDate).atEndOfMonth()) return false
        return when (goal.goalType) {
            TrainingGoalType.FIVE_K -> goal.distanceKm == 5.0
            TrainingGoalType.TEN_K -> goal.distanceKm == 10.0
            TrainingGoalType.HALF_MARATHON -> goal.distanceKm == 21.1
            else -> true
        }
    }

    /** A conservative envelope, not a guarantee of readiness or an injury-prevention formula. */
    fun create(goal: TrainingGoal, today: LocalDate, spanish: Boolean = false): TrainingPlan {
        require(validateGoal(goal, today))
        val sessions = mutableListOf<TrainingSession>()
        val days = when (goal.daysPerWeek) { 2 -> listOf(1, 5); 3 -> listOf(0, 2, 5); 4 -> listOf(0, 2, 4, 6); else -> listOf(0, 1, 3, 4, 6) }
        val rolling = goal.goalType == TrainingGoalType.PERSONAL_PROGRESS
        var week = 0
        while (today.plusWeeks(week.toLong()).isBefore(goal.raceDate)) {
            val remaining = ChronoUnit.DAYS.between(today.plusWeeks(week.toLong()), goal.raceDate)
            val growth = Math.pow(if (rolling) 1.03 else 1.06, (week - week / 4).toDouble()).coerceAtMost(if (rolling) 1.3 else 2.0)
            val cutback = if (week % 4 == 3) .8 else 1.0
            val taper = if (rolling) 1.0 else if (remaining < 8) .5 else if (remaining < 15) .7 else 1.0
            val total = (goal.weeklyKm * growth * cutback * taper).coerceAtMost(100.0)
            days.forEachIndexed { index, day ->
                val date = today.plusWeeks(week.toLong()).plusDays(day.toLong())
                if (!date.isBefore(if (rolling) goal.raceDate else goal.raceDate.minusDays(1))) return@forEachIndexed
                val long = index == days.lastIndex && (rolling || remaining >= 8)
                val fraction = if (long) (if (days.size == 2) .55 else .36) else if (rolling || remaining >= 8) (if (days.size == 2) .45 else .64 / (days.size - 1)) else 1.0 / days.size
                val km = round((total * fraction).coerceAtMost(if (rolling) 50.0 else goal.distanceKm * .85) * 10) / 10
                sessions += TrainingSession(date, km.coerceAtLeast(1.0), if (long) "long" else "easy",
                    if (spanish) "Esfuerzo cómodo. Ajusta o descansa si lo necesitas." else "Comfortable effort. Adjust or rest when needed.")
            }
            week++
        }
        return TrainingPlan(goal, today, sessions,
            if (rolling) { if (spanish) "Ocho semanas para ganar constancia, con semanas suaves. Ajusta o descansa cuando lo necesites." else "Eight weeks to build consistency, with lighter weeks. Adjust or rest when needed." }
            else if (spanish) "Base gradual con semanas suaves y descarga final. El objetivo no garantiza estar preparado para esa distancia." else "Gradual base with lighter weeks and a taper. The goal does not guarantee readiness for that distance.",
            System.currentTimeMillis())
    }

    /** Missed sessions stay missed; a completed activity is linked once, never to several sessions. */
    fun reconcile(plan: TrainingPlan, today: LocalDate, activities: List<Triple<String, LocalDate, Double>>): TrainingPlan {
        val assigned = plan.sessions.mapNotNull { it.activityId }.toMutableSet()
        val sessions = plan.sessions.map { session ->
            if (session.state != SessionState.PLANNED) session
            else {
                val match = activities.firstOrNull { it.first !in assigned && it.second == session.date && it.third >= session.distanceKm * .5 }
                if (match != null) { assigned += match.first; session.copy(state = SessionState.DONE, activityId = match.first) }
                else if (session.date.isBefore(today)) session.copy(state = SessionState.SKIPPED) else session
            }
        }
        return plan.copy(sessions = sessions)
    }

    fun json(plan: TrainingPlan): JSONObject = JSONObject().put("version", 2).put("distance_km", plan.goal.distanceKm)
        .put("goal_type", plan.goal.goalType.name).put("timing", plan.goal.timing.name).put("plan_end_date", plan.goal.raceDate.toString())
        .put("race_date", if (plan.goal.timing == TrainingTiming.EXACT_DAY) plan.goal.raceDate.toString() else JSONObject.NULL)
        .put("target_month", plan.goal.targetMonth?.toString() ?: JSONObject.NULL)
        .put("days_per_week", plan.goal.daysPerWeek).put("weekly_km", plan.goal.weeklyKm).put("starts_on", plan.startsOn.toString())
        .put("summary", plan.summary).put("updated_at", plan.updatedAtMs).put("source", plan.source).put("automatic_date", plan.lastAutomaticDate)
        .put("sessions", JSONArray().also { a -> plan.sessions.forEach { s -> a.put(JSONObject().put("date", s.date.toString()).put("distance_km", s.distanceKm)
            .put("kind", s.kind).put("note", s.note).put("state", s.state.name).put("activity_id", s.activityId ?: JSONObject.NULL)) } })

    fun parse(json: JSONObject): TrainingPlan {
        val version = json.getInt("version"); require(version in 1..2)
        val distance = json.getDouble("distance_km")
        val timing = if (version == 1) TrainingTiming.EXACT_DAY else TrainingTiming.valueOf(json.getString("timing"))
        val goal = TrainingGoal(distance, LocalDate.parse(json.getString(if (version == 1) "race_date" else "plan_end_date")),
            json.getInt("days_per_week"), json.getDouble("weekly_km"),
            if (version == 1) TrainingGoalType.forDistance(distance) else TrainingGoalType.valueOf(json.getString("goal_type")), timing)
        if (version == 2) {
            if (timing == TrainingTiming.TARGET_MONTH) require(YearMonth.parse(json.getString("target_month")) == goal.targetMonth) else require(json.isNull("target_month"))
            if (timing == TrainingTiming.EXACT_DAY) require(LocalDate.parse(json.getString("race_date")) == goal.raceDate) else require(json.isNull("race_date"))
        }
        val starts = LocalDate.parse(json.getString("starts_on")); require(validateGoal(goal, starts))
        val array = json.getJSONArray("sessions"); require(array.length() <= 260)
        val sessions = (0 until array.length()).map { index -> array.getJSONObject(index).let { s ->
            TrainingSession(LocalDate.parse(s.getString("date")), s.getDouble("distance_km"), s.getString("kind").take(30), s.getString("note").take(600),
                SessionState.valueOf(s.getString("state")), if (s.isNull("activity_id")) null else s.getString("activity_id")) } }
        require(sessions.map { it.date }.distinct().size == sessions.size)
        require(sessions.all { it.distanceKm.isFinite() && it.distanceKm in 0.5..50.0 && !it.date.isBefore(starts) && it.date.isBefore(goal.raceDate) })
        return TrainingPlan(goal, starts, sessions, json.getString("summary").take(2400), json.getLong("updated_at"), json.optString("source", "local"), json.optString("automatic_date", ""))
    }

    /** AI may personalize only future sessions inside the original local envelope; status/history are immutable. */
    fun applyAdvice(plan: TrainingPlan, response: String, today: LocalDate, spanish: Boolean = false): TrainingPlan {
        val json = JSONObject(response.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        val summary = json.getString("summary"); require(summary.length in 5..2400)
        val array = json.getJSONArray("sessions")
        val future = plan.sessions.filter { it.state == SessionState.PLANNED && !it.date.isBefore(today) }
        require(array.length() == future.size)
        val envelope = create(plan.goal, plan.startsOn, spanish).sessions.associateBy { it.date }
        val seen = mutableSetOf<LocalDate>()
        val updated = (0 until array.length()).associate { index -> array.getJSONObject(index).let { s ->
            val date = LocalDate.parse(s.getString("date")); require(seen.add(date)); val original = future.single { it.date == date }
            val cap = minOf(envelope.getValue(date).distanceKm, original.distanceKm * 1.1)
            val distance = s.getDouble("distance_km"); require(distance.isFinite() && distance in 0.5..cap)
            val kind = s.getString("kind"); require(kind in setOf("easy", "long", "recovery", "steady"))
            val note = s.getString("note"); require(note.length <= 600)
            date to original.copy(distanceKm = distance, kind = kind, note = note)
        } }
        return plan.copy(sessions = plan.sessions.map { updated[it.date] ?: it }, summary = summary, updatedAtMs = System.currentTimeMillis(), source = "anthropic")
    }
}
