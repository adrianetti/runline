package com.runline.app.ai

import com.runline.app.training.TrainingGoalType
import com.runline.app.training.TrainingPlan
import com.runline.app.training.TrainingTiming
import org.json.JSONObject

/** Optional aspiration, not a calendar prescription or a promise of readiness. */
data class RunnerGoal(val text: String = "") {
    fun bounded() = copy(text = text.trim().take(TEXT_LIMIT))
    fun json() = JSONObject().put("version", 1).put("configured", true).put("text", bounded().text)
    companion object {
        const val TEXT_LIMIT = 400
        fun parse(value: JSONObject): RunnerGoal {
            require(value.getInt("version") == 1 && value.getBoolean("configured"))
            return RunnerGoal(value.getString("text")).bounded()
        }
        /** Migrate the aspiration once, never the old generated schedule or private notes. */
        fun fromLegacy(plan: TrainingPlan?): RunnerGoal {
            val goal = plan?.goal ?: return RunnerGoal()
            val label = when (goal.goalType) {
                TrainingGoalType.FIVE_K -> "Run 5 km"
                TrainingGoalType.TEN_K -> "Run 10 km"
                TrainingGoalType.HALF_MARATHON -> "Run a half marathon (21.1 km)"
                TrainingGoalType.PERSONAL_PROGRESS -> "Build consistent running"
                TrainingGoalType.CUSTOM -> "Run ${goal.distanceKm} km"
            }
            val timing = when (goal.timing) {
                TrainingTiming.EXACT_DAY -> " on ${goal.raceDate}"
                TrainingTiming.TARGET_MONTH -> " in ${goal.targetMonth}"
                TrainingTiming.ROLLING -> ""
            }
            return RunnerGoal(label + timing).bounded()
        }
    }
}
