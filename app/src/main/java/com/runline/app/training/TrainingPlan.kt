package com.runline.app.training

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.round

/** A goal is explicit user input, not inferred from a single run. */
data class TrainingGoal(val distanceKm: Double, val raceDate: LocalDate, val daysPerWeek: Int, val weeklyKm: Double)
enum class SessionState { PLANNED, DONE, SKIPPED }
data class TrainingSession(val date: LocalDate, val distanceKm: Double, val kind: String, val note: String, val state: SessionState = SessionState.PLANNED, val activityId: String? = null)
data class TrainingPlan(val goal: TrainingGoal, val startsOn: LocalDate, val sessions: List<TrainingSession>, val summary: String, val updatedAtMs: Long, val source: String = "local", val lastAutomaticDate: String = "")

object TrainingPlanEngine {
    fun validateGoal(goal: TrainingGoal, today: LocalDate): Boolean = goal.distanceKm.isFinite() && goal.distanceKm in 1.0..50.0 &&
        ChronoUnit.DAYS.between(today, goal.raceDate) in 28..364 && goal.daysPerWeek in 2..5 && goal.weeklyKm.isFinite() && goal.weeklyKm in 5.0..100.0

    /** A conservative envelope, not a guarantee of readiness or an injury-prevention formula. */
    fun create(goal: TrainingGoal, today: LocalDate, spanish: Boolean = false): TrainingPlan {
        require(validateGoal(goal, today))
        val sessions = mutableListOf<TrainingSession>()
        val days = when (goal.daysPerWeek) { 2 -> listOf(1, 5); 3 -> listOf(0, 2, 5); 4 -> listOf(0, 2, 4, 6); else -> listOf(0, 1, 3, 4, 6) }
        var week = 0
        while (today.plusWeeks(week.toLong()).isBefore(goal.raceDate)) {
            val remaining = ChronoUnit.DAYS.between(today.plusWeeks(week.toLong()), goal.raceDate)
            val growth = Math.pow(1.06, (week - week / 4).toDouble()).coerceAtMost(2.0)
            val cutback = if (week % 4 == 3) .8 else 1.0
            val taper = if (remaining < 8) .5 else if (remaining < 15) .7 else 1.0
            val total = (goal.weeklyKm * growth * cutback * taper).coerceAtMost(100.0)
            days.forEachIndexed { index, day ->
                val date = today.plusWeeks(week.toLong()).plusDays(day.toLong())
                if (!date.isBefore(goal.raceDate.minusDays(1))) return@forEachIndexed
                val long = index == days.lastIndex && remaining >= 8
                val fraction = if (long) (if (days.size == 2) .55 else .36) else if (remaining >= 8) (if (days.size == 2) .45 else .64 / (days.size - 1)) else 1.0 / days.size
                val km = round((total * fraction).coerceAtMost(goal.distanceKm * .85) * 10) / 10
                sessions += TrainingSession(date, km.coerceAtLeast(1.0), if (long) "long" else "easy",
                    if (spanish) "Esfuerzo cómodo. Ajusta o descansa si lo necesitas." else "Comfortable effort. Adjust or rest when needed.")
            }
            week++
        }
        return TrainingPlan(goal, today, sessions,
            if (spanish) "Base gradual con semanas suaves y descarga final. El objetivo no garantiza estar preparado para esa distancia." else "Gradual base with lighter weeks and a taper. The goal does not guarantee readiness for that distance.",
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

    fun json(plan: TrainingPlan): JSONObject = JSONObject().put("version", 1).put("distance_km", plan.goal.distanceKm).put("race_date", plan.goal.raceDate.toString())
        .put("days_per_week", plan.goal.daysPerWeek).put("weekly_km", plan.goal.weeklyKm).put("starts_on", plan.startsOn.toString())
        .put("summary", plan.summary).put("updated_at", plan.updatedAtMs).put("source", plan.source).put("automatic_date", plan.lastAutomaticDate)
        .put("sessions", JSONArray().also { a -> plan.sessions.forEach { s -> a.put(JSONObject().put("date", s.date.toString()).put("distance_km", s.distanceKm)
            .put("kind", s.kind).put("note", s.note).put("state", s.state.name).put("activity_id", s.activityId ?: JSONObject.NULL)) } })

    fun parse(json: JSONObject): TrainingPlan {
        require(json.getInt("version") == 1)
        val goal = TrainingGoal(json.getDouble("distance_km"), LocalDate.parse(json.getString("race_date")), json.getInt("days_per_week"), json.getDouble("weekly_km"))
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
