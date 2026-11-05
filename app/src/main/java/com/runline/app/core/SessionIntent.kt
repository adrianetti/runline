package com.runline.app.core

import org.json.JSONObject

/** Selected target, independent of speech preferences and original GPS samples. */
data class SessionIntent(val goal: CoachConfig, val source: String, val suggestionId: String? = null) {
    init { require(goal.isValid() && source in setOf("free", "manual", "smart")); require(suggestionId == null || suggestionId.length <= 100) }
    fun applyTo(preferences: CoachConfig) = preferences.copy(goal = goal.goal, distanceKm = goal.distanceKm,
        paceSecondsPerKm = goal.paceSecondsPerKm, targetMinutes = goal.targetMinutes,
        paceMinSecondsPerKm = goal.paceMinSecondsPerKm, paceMaxSecondsPerKm = goal.paceMaxSecondsPerKm, effortIntent = goal.effortIntent)
    fun json() = JSONObject().put("version", 1).put("source", source).put("suggestion_id", suggestionId ?: JSONObject.NULL)
        .put("goal", goal.goal.name).put("distance_km", goal.distanceKm).put("pace_seconds", goal.paceSecondsPerKm)
        .put("minutes", goal.targetMinutes).put("pace_min", goal.paceMinSecondsPerKm ?: JSONObject.NULL)
        .put("pace_max", goal.paceMaxSecondsPerKm ?: JSONObject.NULL).put("effort", goal.effortIntent?.name ?: JSONObject.NULL)
    companion object {
        fun free() = SessionIntent(CoachConfig(), "free")
        fun parse(text: String): SessionIntent {
            require(text.length <= 4000)
            val j = JSONObject(text); require(j.getInt("version") == 1)
            fun number(key: String) = if (j.isNull(key)) null else j.getInt(key)
            return SessionIntent(CoachConfig(goal = CoachGoal.valueOf(j.getString("goal")), distanceKm = j.getDouble("distance_km"),
                paceSecondsPerKm = j.getInt("pace_seconds"), targetMinutes = j.getDouble("minutes"),
                paceMinSecondsPerKm = number("pace_min"), paceMaxSecondsPerKm = number("pace_max"),
                effortIntent = if (j.isNull("effort")) null else SmartRunEffort.valueOf(j.getString("effort"))),
                j.getString("source"), if (j.isNull("suggestion_id")) null else j.getString("suggestion_id"))
        }
    }
}
