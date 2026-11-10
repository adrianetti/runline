package com.runline.app.ai

import com.runline.app.core.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.LocalDate

/**
 * Replays an exported activity (points.csv from "Export data") through the local coach and the
 * live coach, calling the real provider, and prints what the runner would have heard. Skipped
 * unless RUNLINE_REPLAY_POINTS and ANTHROPIC_API_KEY are set, so it never runs in CI:
 *
 *   RUNLINE_REPLAY_POINTS=/path/points.csv RUNLINE_REPLAY_OUT=/path/transcript.txt \
 *   ./gradlew testDebugUnitTest --tests '*LiveCoachReplayTest*'
 *
 * Optional: RUNLINE_REPLAY_LANGUAGE (Spanish/English), RUNLINE_REPLAY_GOAL_MINUTES,
 * RUNLINE_REPLAY_ASPIRATION, RUNLINE_REPLAY_INTERVAL (minutes), RUNLINE_REPLAY_MODEL.
 */
class LiveCoachReplayTest {
    @Test fun replay() {
        val path = System.getenv("RUNLINE_REPLAY_POINTS"); val key = System.getenv("ANTHROPIC_API_KEY")
        assumeTrue(!path.isNullOrBlank() && !key.isNullOrBlank())
        val language = System.getenv("RUNLINE_REPLAY_LANGUAGE") ?: "Spanish"
        val model = System.getenv("RUNLINE_REPLAY_MODEL") ?: AiProviderCatalog.DEFAULT_LIVE_MODEL
        val interval = System.getenv("RUNLINE_REPLAY_INTERVAL")?.toIntOrNull() ?: AiConfig.DEFAULT_INTERVAL_MINUTES
        val config = CoachConfig(enabled = true, goal = CoachGoal.TIME,
            targetMinutes = System.getenv("RUNLINE_REPLAY_GOAL_MINUTES")?.toDoubleOrNull() ?: 30.0, effortIntent = SmartRunEffort.EASY)
        val aspiration = RunnerGoal(System.getenv("RUNLINE_REPLAY_ASPIRATION") ?: "")
        val rows = File(path!!).readLines().drop(1).filter { it.isNotBlank() }.map { it.split(',') }
        val points = rows.map { c -> RunPoint(c[3].toDouble(), c[4].toDouble(), Instant.parse(c[0]).toEpochMilli(), c[5].toFloat(),
            c[8].toDoubleOrNull(), c[2].toInt(), terrainModel = runCatching { TerrainModel.valueOf(c[9]) }.getOrNull()) }
        val start = points.first().timestampMs
        val engine = CoachEngine(config, null, terrainPoint = { it })
        val plan = LiveCoachPlan()
        val provider = AnthropicProvider()
        val said = mutableListOf<AiSpokenFeedback>()
        val out = StringBuilder()
        var busyUntil = 0L; var lastFinished = Long.MIN_VALUE / 2
        var waiting: Triple<String, Long, String>? = null
        var aiLines = 0; var localLines = 0; val latencies = mutableListOf<Long>()
        fun stamp(ms: Long) = "%2d:%02d".format(ms / 60_000, ms / 1000 % 60)
        fun say(now: Long, source: String, text: String, label: String) {
            said += AiSpokenFeedback(text, source, now); busyUntil = now + 2_000 + text.length * 60L; lastFinished = busyUntil
            out.append("${stamp(now)}  $source  [$label]\n        $text\n")
        }
        points.indices.forEach { index ->
            val now = points[index].timestampMs - start
            val run = RunSession("replay", start, start, now, points.subList(0, index + 1), elevationSource = TerrainSource.TERRAIN_MAP)
            val cue = engine.update(run, recording = true, gpsFresh = true)
            if (cue != null && !(cue is CoachCue.Encouragement && cue.kind !in setOf(EncouragementKind.HALFWAY, EncouragementKind.FINAL_STRETCH) && plan.healthy)) {
                say(now, "LOCAL", localText(cue), cue.toString().substringBefore('('))
                plan.spoken(now, ai = false, moment = LiveMoment.of(cue.toString())); localLines++
            }
            waiting?.let { (text, readyAt, label) ->
                if (now - readyAt > CoachSpeechPriority.AI.lifetimeMs) { out.append("${stamp(now)}  --  dropped late: $label\n"); waiting = null }
                else if (now >= readyAt && now >= busyUntil && now - lastFinished >= CoachSpeechPriority.AI.silenceMs) {
                    say(now, "AI   ", text, label); plan.spoken(now, ai = true, moment = null); aiLines++; waiting = null
                }
            }
            if (waiting != null) return@forEach
            val call = plan.next(now, interval) ?: return@forEach
            val label = call.trigger.name + (call.moment?.let { ":$it" } ?: if (call.trigger == LiveTrigger.CADENCE) ":${call.focus}" else "")
            val input = org.json.JSONObject().put("run", AiRunSummary.of(run)).put("coaching_context", AiCoachingContext.build(
                LocalDate.parse(rows.first()[0].take(10)), activeGoal = config, activeRun = run, spoken = said.toList(), nowElapsedMs = now, runnerGoal = aspiration))
            val began = System.nanoTime()
            val answer = runCatching { LiveAiAdvice.request(provider, key!!, model, language, input, call) }
            val latency = (System.nanoTime() - began) / 1_000_000; latencies += latency
            answer.onFailure { plan.failed(now); out.append("${stamp(now)}  --  failed: $label ${it.message}\n") }
            answer.onSuccess { text ->
                plan.answered(now, text != null)
                if (text == null) out.append("${stamp(now)}  --  silent: $label (${latency} ms)\n") else waiting = Triple(text, now + latency, "$label, ${latency} ms")
            }
        }
        val minutes = (points.last().timestampMs - start) / 60_000.0
        out.append("\n${aiLines} live lines + ${localLines} local lines in ${"%.1f".format(minutes)} min; ${plan.attempts} requests; " +
            "latency median ${latencies.sorted().getOrNull(latencies.size / 2)} ms, max ${latencies.maxOrNull()} ms\n")
        println(out)
        System.getenv("RUNLINE_REPLAY_OUT")?.let { File(it).writeText(out.toString()) }
    }

    /** Close to the app's Spanish wording, enough for the live coach to know what was announced. */
    private fun localText(cue: CoachCue): String = when (cue) {
        is CoachCue.Kilometre -> "${cue.completed} kilómetros completados." + (cue.splitPaceSeconds?.let { " Este ha sido en ${(it / 60).toInt()} minutos ${(it % 60).toInt()} segundos." } ?: "")
        is CoachCue.LapCompleted -> "Parece la vuelta ${cue.number} por el mismo recorrido."
        is CoachCue.Encouragement -> when (cue.kind) {
            EncouragementKind.HALFWAY -> "Mitad del objetivo. Llevas ${cue.facts?.let { it.movingMs / 60_000 }} minutos."
            EncouragementKind.FINAL_STRETCH -> "Último tramo del objetivo."
            else -> "Ánimo."
        }
        is CoachCue.GoalReached -> "Objetivo conseguido."
        CoachCue.OneKilometreRemaining -> "Queda un kilómetro."
        else -> cue.toString()
    }
}
