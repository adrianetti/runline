package com.runline.app.ai

import com.runline.app.core.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Backtest of the voice coach on recorded activities: replays each one through the local coach,
 * the shared speech schedule and the live coach (calling the real provider), writes what the
 * runner would have heard, and has a stronger model grade every line against the run's data.
 * Skipped unless RUNLINE_BACKTEST_DIR and ANTHROPIC_API_KEY are set, so it never runs in CI.
 *
 *   RUNLINE_BACKTEST_DIR=/path/flow RUNLINE_BACKTEST_VARIANT=v3 RUNLINE_BACKTEST_STEP=replay \
 *   ./gradlew testDebugUnitTest --tests '*LiveCoachBacktest*'
 *
 * The directory holds sessions.json: [{"id", "file" (points.csv from Export data, or a GPX),
 * "language", "goal": {"type", "minutes", "km", "effort"}, "history": [ids], "aspiration"}].
 * RUNLINE_BACKTEST_STEP is replay or judge; RUNLINE_BACKTEST_MODE is live (default) or local
 * (the built-in coach alone); RUNLINE_BACKTEST_ONLY limits it to some session ids.
 */
class LiveCoachBacktest {
    private val dir get() = System.getenv("RUNLINE_BACKTEST_DIR")
    private val key get() = System.getenv("ANTHROPIC_API_KEY")
    private val variant get() = System.getenv("RUNLINE_BACKTEST_VARIANT") ?: "work"
    private fun step(name: String) = !dir.isNullOrBlank() && !key.isNullOrBlank() && System.getenv("RUNLINE_BACKTEST_STEP") == name
    private fun sessions(): List<JSONObject> {
        val only = System.getenv("RUNLINE_BACKTEST_ONLY")?.split(',')?.filter { it.isNotBlank() }
        val all = JSONArray(File(dir, "sessions.json").readText())
        return (0 until all.length()).map { all.getJSONObject(it) }.filter { only == null || it.getString("id") in only }
    }
    private fun all(): Map<String, JSONObject> = JSONArray(File(dir, "sessions.json").readText()).let { a -> (0 until a.length()).associate { a.getJSONObject(it).getString("id") to a.getJSONObject(it) } }

    @Test fun replay() {
        assumeTrue(step("replay"))
        val live = (System.getenv("RUNLINE_BACKTEST_MODE") ?: "live") == "live"
        val out = File(dir, variant).apply { mkdirs() }
        val catalog = all()
        sessions().parallelStream().forEach { session ->
            val id = session.getString("id")
            val result = replay(session, catalog, live)
            File(out, "$id.json").writeText(result.toString(1))
            File(out, "$id.txt").writeText(transcript(result))
        }
    }

    private fun load(session: JSONObject): RunSession {
        val file = File(session.getString("file"))
        val points = if (file.extension == "csv") file.readLines().drop(1).filter { it.isNotBlank() }.map { it.split(',') }.map { c ->
            RunPoint(c[3].toDouble(), c[4].toDouble(), Instant.parse(c[0]).toEpochMilli(), c[5].toFloat(), c[8].toDoubleOrNull(), c[2].toInt(),
                terrainModel = runCatching { TerrainModel.valueOf(c[9]) }.getOrNull())
        } else {
            var segment = -1
            Regex("<trkseg>|<trkpt lat=\"([^\"]+)\" lon=\"([^\"]+)\">(.*?)</trkpt>", RegexOption.DOT_MATCHES_ALL).findAll(file.readText()).mapNotNull { m ->
                if (m.value == "<trkseg>") { segment++; return@mapNotNull null }
                val body = m.groupValues[3]
                val time = Regex("<time>([^<]+)</time>").find(body)?.groupValues?.get(1) ?: return@mapNotNull null
                val accuracy = Regex("horizontalAccuracyMeters>([^<]+)<").find(body)?.groupValues?.get(1)?.toFloatOrNull() ?: 5f
                RunPoint(m.groupValues[1].toDouble(), m.groupValues[2].toDouble(), Instant.parse(time).toEpochMilli(), accuracy, null, segment.coerceAtLeast(0))
            }.toList()
        }
        val terrain = points.any { it.terrainModel != null }
        return RunSession(session.getString("id"), points.first().timestampMs, points.last().timestampMs,
            points.last().timestampMs - points.first().timestampMs, points, elevationSource = if (terrain) TerrainSource.TERRAIN_MAP else TerrainSource.GPS)
    }

    private fun config(session: JSONObject): CoachConfig {
        val goal = session.optJSONObject("goal") ?: JSONObject()
        val effort = runCatching { SmartRunEffort.valueOf(goal.optString("effort")) }.getOrNull()
        return when (goal.optString("type", "NONE")) {
            "TIME" -> CoachConfig(enabled = true, goal = CoachGoal.TIME, targetMinutes = goal.getDouble("minutes"), effortIntent = effort)
            "DISTANCE" -> CoachConfig(enabled = true, goal = CoachGoal.DISTANCE, distanceKm = goal.getDouble("km"), effortIntent = effort)
            "DISTANCE_TIME" -> CoachConfig(enabled = true, goal = CoachGoal.DISTANCE_TIME, distanceKm = goal.getDouble("km"), targetMinutes = goal.getDouble("minutes"), effortIntent = effort)
            else -> CoachConfig(enabled = true, effortIntent = effort)
        }
    }

    private fun replay(session: JSONObject, catalog: Map<String, JSONObject>, live: Boolean): JSONObject {
        val full = load(session)
        val points = full.points; val start = full.startedAtMs
        val language = session.optString("language", "Spanish")
        val voice = LocalVoice(if (language == "Spanish") "es" else "en", System.getenv("RUNLINE_BACKTEST_LAP_PERCENT")?.toDoubleOrNull() ?: 3.0)
        val config = config(session)
        val history = session.optJSONArray("history")?.let { h -> (0 until h.length()).map { load(catalog.getValue(h.getString(it))) } }.orEmpty()
        val aspiration = RunnerGoal(session.optString("aspiration", ""))
        val today = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate()
        val interval = System.getenv("RUNLINE_BACKTEST_INTERVAL")?.toIntOrNull() ?: AiConfig.DEFAULT_INTERVAL_MINUTES
        val model = System.getenv("RUNLINE_BACKTEST_MODEL") ?: AiProviderCatalog.DEFAULT_LIVE_MODEL
        val voiceMilestones = System.getenv("RUNLINE_BACKTEST_VOICE_MILESTONES") != "0"
        val engine = CoachEngine(config, null, terrainPoint = { if (full.elevationSource == TerrainSource.TERRAIN_MAP) it else it.copy(altitudeMeters = null, verticalAccuracyMeters = null) })
        val schedule = CoachSpeechSchedule()
        val plan = LiveCoachPlan()
        val provider = AnthropicProvider()
        val said = mutableListOf<AiSpokenFeedback>(); val used = mutableListOf<String>()
        val lines = JSONArray(); val events = JSONArray(); val latencies = mutableListOf<Long>()
        var clock = 0L; var speakingUntil: Long? = null
        class Waiting(val text: String, val readyAt: Long, val label: String, val latency: Long, val facts: JSONObject, val askedAt: Long, val point: LivePoint? = null)
        var waiting: Waiting? = null
        val meta = mutableMapOf<String, Waiting>()
        class Voicing(val cue: CoachCue, val line: Waiting?, val readyAt: Long)
        var voicing: Voicing? = null
        fun priority(cue: CoachCue) = when (cue) {
            is CoachCue.GoalReached -> CoachSpeechPriority.GOAL
            is CoachCue.Kilometre, CoachCue.OneKilometreRemaining, is CoachCue.LapCompleted -> CoachSpeechPriority.MILESTONE
            is CoachCue.Terrain, CoachCue.EasingAfterHill -> CoachSpeechPriority.TERRAIN
            is CoachCue.Encouragement -> CoachSpeechPriority.MORALE
            else -> CoachSpeechPriority.PACE
        }
        fun ask(run: RunSession, now: Long, call: LiveCall, milestone: JSONObject?): Waiting? {
            val label = call.trigger.name + (call.moment?.let { ":$it" } ?: if (call.trigger == LiveTrigger.CADENCE) ":${call.focus}" else "")
            val summary = AiRunSummary.of(run)
            val context = AiCoachingContext.build(today, activeGoal = config, activeRun = run, history = history, spoken = said.toList(), nowElapsedMs = now, runnerGoal = aspiration)
            val began = System.nanoTime()
            val answer = runCatching { LiveAiAdvice.compose(provider, key!!, model, language, JSONObject().put("run", summary).put("coaching_context", context), call, used.toList(), milestone) }
            val latency = (System.nanoTime() - began) / 1_000_000; latencies += latency
            answer.onFailure { plan.failed(now); events.put(JSONObject().put("at_s", now / 1000).put("event", "failed").put("label", label).put("reason", it.message)) }
            val line = answer.getOrNull()
            if (answer.isSuccess) { plan.answered(now, line != null); if (line == null) events.put(JSONObject().put("at_s", now / 1000).put("event", "silent").put("label", label)) }
            return line?.let { used += it.pointId
                Waiting(it.text, now + latency, "$label ${it.pointId}", latency, LiveFacts.of(summary, context).put("terrain", context.opt("recent_terrain") ?: JSONObject.NULL), now, it.point) }
        }
        points.indices.forEach { index ->
            clock = points[index].timestampMs - start
            val now = clock
            val run = RunSession(full.id, start, start, now, points.subList(0, index + 1), elevationSource = full.elevationSource)
            val cue = engine.update(run, recording = true, gpsFresh = true)
            if (!engine.situation.eligible) schedule.invalidateCoaching()
            speakingUntil?.let { if (now >= it) { schedule.finished(it); speakingUntil = null } }
            val leftToLive = live && plan.healthy && cue is CoachCue.Encouragement && cue.kind !in setOf(EncouragementKind.HALFWAY, EncouragementKind.FINAL_STRETCH)
            if (cue != null && !leftToLive) {
                // The live coach announces milestones itself; the built-in line is the fallback after a short wait.
                val moment = LiveMoment.of(cue); val milestone = LiveMilestone.of(cue)
                val call = if (live && voiceMilestones && moment != null && milestone != null && voicing == null && waiting == null) plan.milestone(now, moment) else null
                if (call == null) schedule.offer(CoachSpeechRequest(cue.toString(), priority(cue), cue = cue, isValid = { engine.situation.allows(cue) }), now)
                else ask(run, now, call, milestone).let { line ->
                    voicing = if (line != null && line.latency <= AiRunCoach.VOICE_WITHIN_MS) Voicing(cue, line, line.readyAt)
                    else Voicing(cue, null, now + minOf(line?.latency ?: AiRunCoach.VOICE_WITHIN_MS, AiRunCoach.VOICE_WITHIN_MS))
                }
            }
            voicing?.takeIf { now >= it.readyAt }?.let { v ->
                if (schedule.offer(CoachSpeechRequest(v.cue.toString(), priority(v.cue), cue = v.cue, advice = v.line?.text, isValid = { engine.situation.allows(v.cue) }), now)) v.line?.let { meta[it.text] = it }
                voicing = null
            }
            waiting?.takeIf { now >= it.readyAt }?.let { w ->
                val offered = schedule.offer(CoachSpeechRequest("ai", CoachSpeechPriority.AI, advice = w.text,
                    isValid = { engine.situation.eligible && clock - w.askedAt <= AiRunCoach.SPEAK_WITHIN_MS }), now)
                if (offered) meta[w.text] = w else events.put(JSONObject().put("at_s", now / 1000).put("event", "rejected").put("label", w.label))
                waiting = null
            }
            if (speakingUntil == null) schedule.takeReady(now, true)?.takeIf { runCatching(it.isValid).getOrDefault(false) }?.let { request ->
                val local = request.advice == null
                val text = request.advice ?: request.cue?.let(voice::text).orEmpty()
                speakingUntil = now + 500 + text.length * 65L
                said += AiSpokenFeedback(text, if (local) "LOCAL" else "AI", now)
                plan.spoken(now, ai = !local)
                val line = JSONObject().put("at_s", now / 1000).put("source", if (local) "LOCAL" else "AI").put("text", text)
                    .put("kind", request.cue?.toString()?.substringBefore('(') ?: meta[text]?.label.orEmpty())
                if (request.cue != null) line.put("announces", request.cue.toString().take(400))
                if (!local) meta[text]?.let { line.put("latency_ms", it.latency).put("data", it.facts).put("waited_s", (now - it.askedAt) / 1000)
                    it.point?.let { p -> line.put("point", JSONObject().put("id", p.id).put("point", p.point).put("numbers", JSONObject(p.numbers)).put("hint", p.hint ?: JSONObject.NULL)) } }
                lines.put(line)
            }
            if (!live || waiting != null || voicing != null || !engine.situation.eligible) return@forEach
            val call = plan.next(now, interval) ?: return@forEach
            waiting = ask(run, now, call, null)
        }
        val whole = AiRunSummary.of(full)
        return JSONObject().put("id", full.id).put("variant", variant).put("mode", if (live) "live" else "local").put("language", language).put("model", model)
            .put("date", today.toString()).put("minutes", full.activeDurationMs / 60_000.0).put("distance_km", whole.optDouble("distance_km"))
            .put("goal", session.optJSONObject("goal") ?: JSONObject.NULL).put("aspiration", aspiration.text)
            .put("final_facts", LiveFacts.of(whole, AiCoachingContext.build(today, activeGoal = config, activeRun = full, history = history, runnerGoal = aspiration)))
            .put("requests", plan.attempts).put("latencies_ms", JSONArray(latencies)).put("events", events).put("lines", lines)
    }

    private fun transcript(result: JSONObject): String = buildString {
        val lines = result.getJSONArray("lines")
        (0 until lines.length()).map { lines.getJSONObject(it) }.forEach { l ->
            val at = l.getLong("at_s")
            append("%2d:%02d  %-5s [%s%s]\n        %s\n".format(at / 60, at % 60, l.getString("source"), l.getString("kind"),
                if (l.has("latency_ms")) ", ${l.getLong("latency_ms")} ms" else "", l.getString("text")))
        }
        val events = result.getJSONArray("events")
        (0 until events.length()).map { events.getJSONObject(it) }.forEach { append("   -- ${it.getLong("at_s") / 60}:${"%02d".format(it.getLong("at_s") % 60)} ${it.getString("event")} ${it.getString("label")} ${it.optString("reason")}\n") }
        append("${lines.length()} lines in ${"%.1f".format(result.getDouble("minutes"))} min, ${result.getInt("requests")} requests\n")
    }

    @Test fun judge() {
        assumeTrue(step("judge"))
        val out = File(dir, variant)
        val judge = System.getenv("RUNLINE_BACKTEST_JUDGE_MODEL") ?: AiProviderCatalog.DEFAULT_REVIEW_MODEL
        val reps = System.getenv("RUNLINE_BACKTEST_JUDGE_REPS")?.toIntOrNull() ?: 2
        sessions().flatMap { s -> (0 until reps).map { s to it } }.parallelStream().forEach { (session, rep) ->
            val id = session.getString("id")
            val result = JSONObject(File(out, "$id.json").readText())
            val lines = result.getJSONArray("lines")
            val input = JSONObject().put("language", result.getString("language")).put("run_date", result.optString("date")).put("run_minutes", result.getDouble("minutes"))
                .put("run_distance_km", result.getDouble("distance_km")).put("goal", result.opt("goal")).put("aspiration", result.optString("aspiration"))
                .put("whole_run_facts", result.getJSONObject("final_facts"))
                .put("lines", JSONArray().also { a -> (0 until lines.length()).forEach { i -> val l = lines.getJSONObject(i)
                    a.put(JSONObject().put("index", i).put("minute", "%d:%02d".format(l.getLong("at_s") / 60, l.getLong("at_s") % 60))
                        .put("spoken_by", if (l.getString("source") == "AI") "COACH" else "APP").put("text", l.getString("text")).put("data_at_that_moment", l.opt("data") ?: JSONObject.NULL)
                        .put("app_event_announced", l.opt("announces") ?: JSONObject.NULL)
                        .put("message_the_coach_was_asked_to_voice", l.optJSONObject("point")?.let { JSONObject().put("point", it.opt("point")).put("numbers", it.opt("numbers")) } ?: JSONObject.NULL)) } })
            val item = JSONObject().put("type", "object").put("additionalProperties", false)
                .put("required", JSONArray(listOf("index", "accurate", "clear", "specific", "value", "voice", "fresh", "asks_reply", "note")))
                .put("properties", JSONObject().put("index", JSONObject().put("type", "integer")).put("accurate", JSONObject().put("type", "boolean"))
                    .put("clear", JSONObject().put("type", "boolean")).put("specific", JSONObject().put("type", "integer")).put("value", JSONObject().put("type", "integer"))
                    .put("voice", JSONObject().put("type", "integer")).put("fresh", JSONObject().put("type", "integer")).put("asks_reply", JSONObject().put("type", "boolean"))
                    .put("note", JSONObject().put("type", "string")))
            val schema = JSONObject().put("type", "object").put("additionalProperties", false)
                .put("required", JSONArray(listOf("lines", "coach_feel", "too_quiet", "too_chatty", "problems")))
                .put("properties", JSONObject().put("lines", JSONObject().put("type", "array").put("items", item))
                    .put("coach_feel", JSONObject().put("type", "integer")).put("too_quiet", JSONObject().put("type", "boolean"))
                    .put("too_chatty", JSONObject().put("type", "boolean")).put("problems", JSONObject().put("type", "array").put("items", JSONObject().put("type", "string"))))
            val raw = AnthropicProvider().structuredMessage(key!!, judge, JUDGE, input, schema, 16_000, 240_000)
            File(out, "$id.grade.$rep.json").writeText(JSONObject(raw).put("judge", judge).toString(1))
        }
    }

    /** The app's own wording for local cues, read from the string resources so the backtest hears the real lines. */
    private class LocalVoice(private val language: String, private val lapPercent: Double) {
        private val strings = mutableMapOf<String, String>(); private val plurals = mutableMapOf<String, Map<String, String>>(); private val arrays = mutableMapOf<String, List<String>>()
        private val phrases = PhraseRotation(7)
        private val locale = if (language == "es") Locale("es", "ES") else Locale.UK
        init {
            val folder = File(if (language == "es") "src/main/res/values-es" else "src/main/res/values")
            fun clean(text: String) = text.trim().replace("\\'", "'").replace("\\\"", "\"").replace("&amp;", "&").replace("\\n", " ")
            folder.listFiles { f -> f.extension == "xml" }.orEmpty().forEach { file ->
                val xml = file.readText()
                Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { strings[it.groupValues[1]] = clean(it.groupValues[2]) }
                Regex("<plurals name=\"([^\"]+)\">(.*?)</plurals>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { p ->
                    plurals[p.groupValues[1]] = Regex("<item quantity=\"([^\"]+)\">(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(p.groupValues[2]).associate { it.groupValues[1] to clean(it.groupValues[2]) } }
                Regex("<string-array name=\"([^\"]+)\">(.*?)</string-array>", RegexOption.DOT_MATCHES_ALL).findAll(xml).forEach { a ->
                    arrays[a.groupValues[1]] = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL).findAll(a.groupValues[2]).map { clean(it.groupValues[1]) }.toList() }
            }
        }
        private fun tr(name: String, vararg args: Any) = String.format(locale, strings.getValue(name), *args)
        private fun plural(name: String, count: Int) = String.format(locale, plurals.getValue(name).let { it[if (count == 1) "one" else "other"] ?: it.getValue("other") }, count)
        private fun phrase(name: String) = phrases.choose(name, arrays.getValue(name))
        private fun pace(seconds: Double) = seconds.roundToInt().let { tr("v17_coach_pace", it / 60, it % 60) }
        private fun km(meters: Double) = tr("v17_coach_km", String.format(locale, "%.1f", meters / 1000))
        private fun duration(ms: Long): String {
            val minutes = ((ms + 30_000) / 60_000).toInt(); val hours = minutes / 60; val rest = minutes % 60
            return when {
                hours == 0 -> plural("v17_coach_minutes", minutes.coerceAtLeast(1))
                rest == 0 -> plural("v17_coach_hours", hours)
                else -> plural("v17_coach_hours", hours) + " " + plural("v17_coach_minutes", rest)
            }
        }
        fun text(cue: CoachCue): String = when (cue) {
            is CoachCue.Kilometre -> {
                val km = plural("coach_voice_kilometres", cue.completed); val split = cue.splitPaceSeconds?.roundToInt(); val pace = cue.averagePaceSeconds?.roundToInt()
                when {
                    split != null -> buildString {
                        append(km).append(' ').append(tr("v17_coach_split", split / 60, split % 60))
                        when (val change = CoachNarration.splitChange(cue.splitPaceSeconds, cue.previousSplitPaceSeconds)) {
                            null -> Unit
                            0 -> append(' ').append(tr("v17_coach_split_even"))
                            else -> append(' ').append(tr(if (change < 0) "v17_coach_split_faster" else "v17_coach_split_slower", kotlin.math.abs(change)))
                        }
                    }
                    pace == null -> km
                    else -> km + " " + tr("coach_voice_average_pace", pace / 60, pace % 60)
                }
            }
            CoachCue.OneKilometreRemaining -> tr("coach_voice_one_remaining")
            is CoachCue.GoalReached -> listOfNotNull(tr(when (cue.withinTime) { true -> "coach_voice_goal_in_time"; false -> "coach_voice_goal_over_time"; null -> "coach_voice_goal_reached" }),
                cue.facts?.let { f -> f.averagePaceSeconds?.let { tr("v17_coach_summary", km(f.distanceMeters), duration(f.movingMs), pace(it)) } }).joinToString(" ")
            CoachCue.SlowerThanTarget -> phrase("coach_phrase_slower")
            CoachCue.FasterThanTarget -> phrase("coach_phrase_faster")
            CoachCue.BackOnPace -> phrase("coach_phrase_on_pace")
            is CoachCue.Encouragement -> cue.facts?.let { measured(cue.kind, it) } ?: phrase(when (cue.kind) {
                EncouragementKind.SETTLING_IN -> "coach_phrase_settling"; EncouragementKind.STEADY -> "coach_phrase_steady"
                EncouragementKind.HALFWAY -> "coach_phrase_halfway"; EncouragementKind.FINAL_STRETCH -> "coach_phrase_final"
                EncouragementKind.ENDURANCE -> "coach_phrase_endurance"; EncouragementKind.UPHILL -> "coach_v12_uphill_support"
                EncouragementKind.DOWNHILL -> "coach_v12_downhill_support"; EncouragementKind.ON_TARGET -> "coach_v12_on_target"
                EncouragementKind.CONSISTENT -> "coach_v12_consistent"; EncouragementKind.PROGRESS -> "coach_phrase_steady"
            })
            is CoachCue.Terrain -> phrase(if (cue.kind == CoachTerrain.UPHILL) "coach_phrase_uphill" else "coach_phrase_downhill")
            CoachCue.EasingAfterHill -> phrase("coach_phrase_after_hill")
            is CoachCue.LapCompleted -> buildString {
                append(tr("coach_lap_complete", cue.number))
                val comparison = cue.comparison; val sector = comparison?.slowerSector
                if (sector != null) append(" ").append(if (arrays.containsKey("v22_coach_lap_quarters")) tr("coach_lap_sector", arrays.getValue("v22_coach_lap_quarters")[(sector.number - 1).coerceIn(0, 3)]) else tr("coach_lap_sector", sector.number))
                else if (comparison != null) append(" ").append(tr(when {
                    comparison.paceChangePercent >= lapPercent -> "coach_lap_slower"
                    comparison.paceChangePercent <= -lapPercent -> "coach_lap_faster"
                    else -> "coach_lap_even"
                }))
            }
        }
        private fun measured(kind: EncouragementKind, facts: CoachFacts): String? = when (kind) {
            EncouragementKind.HALFWAY -> when {
                facts.projectedMeters != null -> tr(if (facts.pastHalfway) "v22_coach_past_halfway_time" else "v17_coach_halfway_time", duration(facts.movingMs), km(facts.distanceMeters), km(facts.projectedMeters))
                facts.projectedMs != null -> tr(if (facts.pastHalfway) "v22_coach_past_halfway_distance" else "v17_coach_halfway_distance", km(facts.distanceMeters), duration(facts.movingMs), duration(facts.projectedMs))
                else -> null
            }
            EncouragementKind.FINAL_STRETCH -> when {
                facts.remainingMs != null && facts.remainingMs >= 60_000 -> tr("v17_coach_final_time", duration(facts.remainingMs), km(facts.distanceMeters))
                facts.remainingMeters != null && facts.remainingMeters >= 50 -> tr("v17_coach_final_distance", ((facts.remainingMeters / 50).roundToInt() * 50))
                else -> null
            }
            EncouragementKind.PROGRESS -> {
                val recent = facts.recentPaceSeconds; val trend = CoachNarration.trend(recent, facts.averagePaceSeconds)
                if (recent == null || trend == null) null
                else tr("v17_coach_progress", duration(facts.movingMs), km(facts.distanceMeters)) + " " + tr(when (trend) {
                    CoachNarration.Trend.FASTER -> "v17_coach_trend_faster"; CoachNarration.Trend.SLOWER -> "v17_coach_trend_slower"; CoachNarration.Trend.STEADY -> "v17_coach_trend_steady"
                }, pace(recent))
            }
            else -> null
        }
    }

    private companion object {
        val JUDGE = """
You are an experienced running coach and a demanding editor, reviewing what a voice coach app said into a runner's headphones during one recorded run. Lines marked APP are fixed announcements from the app; lines marked COACH were written live by an AI model. Grade every line, in order, as the runner would have heard it: once, by ear, over music, while running.

Reading the data: run_date is the day of this run, so "today" for any date arithmetic. Paces and times are minutes:seconds; a LOWER pace is FASTER. In data_at_that_moment, any "..._vs_..." change is new minus old, so a NEGATIVE change means FASTER and a positive one slower; pace_last_2_minutes is the pace over the two minutes before the line, not over kilometres; repeated_lap_times are durations of one loop of a circuit, not paces. For COACH lines, message_the_coach_was_asked_to_voice is what the app worked out and asked the model to say: check both that it agrees with the data and that the line says it without distorting it or adding claims. APP announcements are triggered a few seconds after the event they report.

For each line:
- accurate: true only if every factual claim agrees with data_at_that_moment (and with whole_run_facts for what had happened by then). Allow rounding of a few seconds of pace and 0.1 km. General technique or effort cues are not factual claims. A claim about the runner's body the data cannot support (pulse, tiredness, how they look) is inaccurate. If the sentence is so muddled that its claim cannot be pinned down, it is inaccurate.
- clear: true only if it is natural, grammatical speech in the given language that a tired runner would understand at once. Awkward, garbled, overlong or written-style sentences are not clear.
- specific: 0 generic filler that fits any run; 1 loosely tied to this run; 2 anchored in a concrete fact of this run or this runner.
- value: 0 tells the runner nothing useful; 1 mildly useful; 2 a real insight, a well-timed cue or encouragement the runner would be glad to hear right then.
- voice: 0 robotic, report-like or stiff; 1 acceptable; 2 sounds like a warm, lively human coach who enjoys being there.
- fresh: compared with every earlier line. 0 repeats an earlier fact, phrasing or opening; 1 partly new; 2 new content and new wording.
- asks_reply: true if it asks the runner something that expects a spoken or typed answer. A rhetorical question or a self-check the runner answers in their head is false.
- note: at most 12 words on the main flaw, or "ok".

Then for the whole run:
- coach_feel: 0 to 100. How close is this to having an excellent human coach in your ear for this run? Weigh: true and specific insights, real encouragement, variety, good moments chosen, enough presence without nagging, nothing confusing or wrong. 20 = a talking stopwatch. 50 = pleasant but generic or error-prone. 80 = clearly a good coach with minor flaws. 95 = hard to tell from a great human coach.
- too_quiet: long stretches where a coach would have spoken. too_chatty: speaks so often or at such length it would annoy.
- problems: the three most important things to fix, most important first, each under 20 words.
Be strict and consistent. Do not reward length or enthusiasm on its own.""".trim()
    }
}
