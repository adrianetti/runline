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
                paceMinSecondsPerKm !in PACE_SECONDS_RANGE || paceMaxSecondsPerKm !in paceMinSecondsPerKm..PACE_SECONDS_RANGE.last)) return false
        return when (goal) {
        CoachGoal.NONE -> true
        CoachGoal.DISTANCE -> validDistance()
        CoachGoal.PACE -> paceMinSecondsPerKm != null || paceSecondsPerKm in PACE_SECONDS_RANGE
        CoachGoal.TIME -> targetMinutes.isFinite() && targetMinutes in DURATION_MINUTES_RANGE
        CoachGoal.DISTANCE_TIME -> validDistance() && targetMinutes.isFinite() && targetMinutes in DURATION_MINUTES_RANGE &&
            targetMinutes * 60.0 / distanceKm in PACE_SECONDS_RANGE.first.toDouble()..PACE_SECONDS_RANGE.last.toDouble()
        }
    }

    companion object {
        // Shared input support, not a recommended workout or a history-derived training ceiling.
        val DISTANCE_KM_RANGE = 0.1..500.0
        val DURATION_MINUTES_RANGE = 1.0..1440.0
        val PACE_SECONDS_RANGE = 60..3600
    }

    private fun validDistance() = distanceKm.isFinite() && distanceKm in DISTANCE_KM_RANGE
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
