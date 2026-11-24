package com.runline.app.ai

internal enum class LiveStage { SETTLE, MIDDLE, CLOSE, EXTRA }

internal class LiveStretch(
    progress: Double?,
    moving: Double,
    private val easy: Boolean,
) {
    val stage: LiveStage =
        when {
            progress != null && progress >= 1 -> LiveStage.EXTRA
            progress != null && progress >= CLOSE_SHARE -> LiveStage.CLOSE
            moving < SETTLE_SECONDS && (progress ?: 0.0) < MIDDLE_SHARE -> LiveStage.SETTLE
            else -> LiveStage.MIDDLE
        }

    fun next(
        turn: Int,
        numbers: MutableMap<String, String>,
        left: String?,
    ): String {
        val counted = left?.takeIf { stage == LiveStage.CLOSE }
        counted?.let { numbers[LEFT] = it }
        val jobs = (if (easy) EASY_JOBS else JOBS).getValue(stage)
        return jobs[turn.mod(jobs.size)] + if (counted != null) COUNTDOWN else ""
    }

    companion object {
        const val LEFT = "left_of_todays_goal"
        private const val STEADY = "steady"
        private const val STEADY_NAMED = "Steadiness was already named on this run: no praise for it, just the time in passing."
        private const val STEADY_FIRST = "Name the steadiness once, in a few plain words, not as praise."
        private const val SETTLE_SECONDS = 900.0
        private const val MIDDLE_SHARE = .25
        private const val CLOSE_SHARE = .7
        private const val COUNTDOWN = " Give what is left of today's goal as the countdown."

        private val EXTRA =
            listOf(
                "Next: today's goal is already done, so this is extra. Keep it easy; they can stop whenever they like.",
                "Next: bonus distance with nothing left to prove. A relaxed cool-down rhythm is perfect.",
            )
        private val JOBS =
            mapOf(
                LiveStage.SETTLE to
                    listOf(
                        "Next: settling in. Let the rhythm come to them; nothing needs winning this early.",
                        "Next: the early kilometres are an investment. Ask for patience now, not speed.",
                        "Next: easy breathing and loose shoulders while the run finds its rhythm.",
                    ),
                LiveStage.MIDDLE to
                    listOf(
                        "Next: the middle of the run, where patience wins. The same effort, smooth and economical.",
                        "Next: one kilometre at a time. Stay smooth; the run is not decided here.",
                        "Next: keep the effort honest but comfortable, and save any push for the end.",
                    ),
                LiveStage.CLOSE to
                    listOf(
                        "Next: the closing part. One job: hold the rhythm with tall, relaxed form.",
                        "Next: this is where today's goal is won. Keep the rhythm; do not chase time.",
                        "Next: quick, light steps carry the rhythm home.",
                    ),
                LiveStage.EXTRA to EXTRA,
            )
        private val EASY_JOBS =
            mapOf(
                LiveStage.SETTLE to
                    listOf(
                        "Next: settle into an easy rhythm; today nothing needs winning.",
                        "Next: easy and unhurried; let the run warm up on its own.",
                        "Next: soft shoulders and easy breathing; today is time on feet, not speed.",
                    ),
                LiveStage.MIDDLE to
                    listOf(
                        "Next: the same comfortable effort. On an easy day the middle is for enjoying it.",
                        "Next: keep it conversational; easy days build the base.",
                        "Next: stay relaxed and let the kilometres pass; no need to speed up.",
                    ),
                LiveStage.CLOSE to
                    listOf(
                        "Next: the same easy effort all the way, with no push at the end.",
                        "Next: finish as relaxed as they started.",
                        "Next: keep it light and easy to the end.",
                    ),
                LiveStage.EXTRA to EXTRA,
            )

        fun steadiness(used: List<String>): String = if (used.any { LivePoint.themeOf(it) == STEADY }) STEADY_NAMED else STEADY_FIRST
    }
}
