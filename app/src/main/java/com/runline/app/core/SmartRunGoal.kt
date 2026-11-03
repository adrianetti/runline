package com.runline.app.core

import java.time.LocalDate

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

/** AI chooses training load. This boundary checks shape and compatibility with manual goals only. */
object SmartRunGoal {
    fun validate(suggestion: SmartRunSuggestion): Boolean {
        if (suggestion.rationale.trim().length !in 1..240) return false
        val distances = listOf(suggestion.minDistanceKm, suggestion.targetDistanceKm, suggestion.maxDistanceKm)
        val hasDistance = suggestion.goalType in setOf(SmartRunGoalType.DISTANCE, SmartRunGoalType.DISTANCE_TIME)
        val hasTime = suggestion.goalType in setOf(SmartRunGoalType.TIME, SmartRunGoalType.DISTANCE_TIME)
        if (suggestion.goalType == SmartRunGoalType.REST) return distances.all { it == null } && suggestion.durationMinutes == null &&
            suggestion.paceMinSecondsPerKm == null && suggestion.paceMaxSecondsPerKm == null && suggestion.effort != SmartRunEffort.STEADY
        if (hasDistance) {
            if (distances.any { it == null || !it.isFinite() || it !in CoachConfig.DISTANCE_KM_RANGE }) return false
            if (suggestion.targetDistanceKm!! !in suggestion.minDistanceKm!!..suggestion.maxDistanceKm!!) return false
        } else if (distances.any { it != null }) return false
        if (hasTime) {
            val minutes = suggestion.durationMinutes ?: return false
            if (!minutes.isFinite() || minutes !in CoachConfig.DURATION_MINUTES_RANGE) return false
        } else if (suggestion.durationMinutes != null) return false
        val low = suggestion.paceMinSecondsPerKm; val high = suggestion.paceMaxSecondsPerKm
        if ((low == null) != (high == null)) return false
        if (low != null && high != null && (suggestion.goalType == SmartRunGoalType.DISTANCE_TIME ||
                low !in CoachConfig.PACE_SECONDS_RANGE || high !in low..CoachConfig.PACE_SECONDS_RANGE.last)) return false
        if (suggestion.goalType == SmartRunGoalType.DISTANCE_TIME) {
            val pace = suggestion.durationMinutes!! * 60.0 / suggestion.targetDistanceKm!!
            if (!pace.isFinite() || pace < CoachConfig.PACE_SECONDS_RANGE.first || pace > CoachConfig.PACE_SECONDS_RANGE.last) return false
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

}
