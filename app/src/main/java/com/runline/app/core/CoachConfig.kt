package com.runline.app.core

/** Goals use active recording time; explicit pauses are excluded. */
enum class CoachGoal { NONE, DISTANCE, PACE, TIME, DISTANCE_TIME }

data class CoachConfig(
    val enabled: Boolean = false,
    val goal: CoachGoal = CoachGoal.NONE,
    val distanceKm: Double = 5.0,
    val paceSecondsPerKm: Int = 360,
    val targetMinutes: Double = 30.0,
    val kilometreAnnouncements: Boolean = true,
    val paceFeedback: Boolean = true,
    val encouragement: Boolean = true,
    val terrainFeedback: Boolean = true,
    val lapFeedback: Boolean = true,
    val paceMinSecondsPerKm: Int? = null,
    val paceMaxSecondsPerKm: Int? = null,
    val effortIntent: SmartRunEffort? = null,
) {
    fun isValid(): Boolean {
        if ((paceMinSecondsPerKm == null) != (paceMaxSecondsPerKm == null)) return false
        if (paceMinSecondsPerKm != null && paceMaxSecondsPerKm != null &&
            (goal !in setOf(CoachGoal.DISTANCE, CoachGoal.PACE, CoachGoal.TIME) ||
                paceMinSecondsPerKm !in 60..3600 || paceMaxSecondsPerKm !in paceMinSecondsPerKm..3600)) return false
        return when (goal) {
        CoachGoal.NONE -> true
        CoachGoal.DISTANCE -> validDistance()
        CoachGoal.PACE -> paceMinSecondsPerKm != null || paceSecondsPerKm in 60..3600
        CoachGoal.TIME -> targetMinutes.isFinite() && targetMinutes in 1.0..1440.0
        CoachGoal.DISTANCE_TIME -> validDistance() && targetMinutes.isFinite() && targetMinutes in 1.0..1440.0 &&
            targetMinutes * 60.0 / distanceKm in 60.0..3600.0
        }
    }

    private fun validDistance() = distanceKm.isFinite() && distanceKm in 0.1..500.0
    val distanceGoalMeters: Double? get() = if (isValid() && goal in setOf(CoachGoal.DISTANCE, CoachGoal.DISTANCE_TIME)) distanceKm * 1000 else null
    val durationGoalMs: Long? get() = if (isValid() && goal == CoachGoal.TIME) (targetMinutes * 60_000).toLong() else null
    val targetPaceSeconds: Double? get() = paceTargetRange?.let { (it.start + it.endInclusive) / 2 }
    val paceTargetRange: ClosedFloatingPointRange<Double>? get() {
        if (!isValid()) return null
        if (paceMinSecondsPerKm != null && paceMaxSecondsPerKm != null) return paceMinSecondsPerKm.toDouble()..paceMaxSecondsPerKm.toDouble()
        val target = when (goal) {
            CoachGoal.PACE -> paceSecondsPerKm.toDouble()
            CoachGoal.DISTANCE_TIME -> targetMinutes * 60.0 / distanceKm
            else -> return null
        }
        return target..target
    }
}
