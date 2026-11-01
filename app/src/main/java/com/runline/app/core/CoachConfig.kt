package com.runline.app.core

/** Goals use active recording time; explicit pauses are excluded. */
enum class CoachGoal { NONE, DISTANCE, PACE, DISTANCE_TIME }

data class CoachConfig(
    val enabled: Boolean = false,
    val goal: CoachGoal = CoachGoal.NONE,
    val distanceKm: Double = 5.0,
    val paceSecondsPerKm: Int = 360,
    val targetMinutes: Double = 30.0,
    val kilometreAnnouncements: Boolean = true,
) {
    fun isValid(): Boolean = when (goal) {
        CoachGoal.NONE -> true
        CoachGoal.DISTANCE -> validDistance()
        CoachGoal.PACE -> paceSecondsPerKm in 60..3600
        CoachGoal.DISTANCE_TIME -> validDistance() && targetMinutes.isFinite() && targetMinutes in 1.0..1440.0 &&
            targetMinutes * 60.0 / distanceKm in 60.0..3600.0
    }

    private fun validDistance() = distanceKm.isFinite() && distanceKm in 0.1..500.0
    val distanceGoalMeters: Double? get() = if (isValid() && goal in setOf(CoachGoal.DISTANCE, CoachGoal.DISTANCE_TIME)) distanceKm * 1000 else null
    val targetPaceSeconds: Double? get() = if (!isValid()) null else when (goal) {
        CoachGoal.PACE -> paceSecondsPerKm.toDouble()
        CoachGoal.DISTANCE_TIME -> targetMinutes * 60.0 / distanceKm
        else -> null
    }
}
