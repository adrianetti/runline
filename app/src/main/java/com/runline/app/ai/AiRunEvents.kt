package com.runline.app.ai

import com.runline.app.core.RunEvent
import com.runline.app.core.RunEventKind
import com.runline.app.core.RunEvents
import com.runline.app.core.RunSession
import org.json.JSONArray
import org.json.JSONObject

internal object AiRunEvents {
    const val KEY = "in_run_events"
    private const val MAX_ITEMS = 12
    private const val MILLIS_PER_SECOND = 1_000.0
    private const val METRES_PER_KILOMETRE = 1_000.0
    private val INTERRUPTIONS = setOf(RunEventKind.STOP, RunEventKind.WALK, RunEventKind.PAUSE)
    private val PACED = setOf(RunEventKind.WALK)
    private val SPOKEN = setOf(RunEventKind.STOP, RunEventKind.WALK, RunEventKind.PAUSE, RunEventKind.GPS_GAP)
    private const val NOTE =
        "Detected on the phone from GPS fixes. STOP: standing still for 20 seconds or more. " +
            "WALK: moving at walking speed for 45 seconds or more. PAUSE: recording paused. " +
            "GPS_GAP: no fixes for 30 seconds or more; distance across it is a straight-line estimate. " +
            "Seconds ago are counted back from the latest fix."

    fun of(run: RunSession): JSONObject {
        val report = RunEvents.detect(run)
        val end = report.endMs ?: 0L
        val events = report.events.filter { it.kind in SPOKEN }
        val counts = JSONObject()
        SPOKEN.forEach { kind -> counts.put(kind.name, events.count { it.kind == kind }) }
        val numbered = events.mapIndexed { index, event -> event to events.take(index + 1).count { it.kind == event.kind } }
        return JSONObject()
            .put("usual_pace_seconds_per_km", report.usualPaceSecondsPerKm ?: JSONObject.NULL)
            .put("interrupted", events.any { it.kind in INTERRUPTIONS })
            .put("stopped_now_seconds", report.stillSinceMs?.let { (end - it) / MILLIS_PER_SECOND } ?: JSONObject.NULL)
            .put("counts", counts)
            .put("items", JSONArray(numbered.takeLast(MAX_ITEMS).map { (event, number) -> item(event, number, end) }))
            .put("note", NOTE)
    }

    private fun item(
        event: RunEvent,
        number: Int,
        end: Long,
    ): JSONObject =
        JSONObject()
            .put("kind", event.kind.name)
            .put("number", number)
            .put("ongoing", event.ongoing)
            .put("started_seconds_ago", (end - event.startMs) / MILLIS_PER_SECOND)
            .put("ended_seconds_ago", (end - event.endMs) / MILLIS_PER_SECOND)
            .put("duration_seconds", event.durationMs / MILLIS_PER_SECOND)
            .put("distance_m", event.meters)
            .put("kilometre", kilometre(event.startMeters))
            .put("to_kilometre", kilometre(event.startMeters + event.meters))
            .put("pace_seconds_per_km", event.paceSecondsPerKm.takeIf { event.kind in PACED } ?: JSONObject.NULL)
            .put("reference_pace_seconds_per_km", event.referencePaceSecondsPerKm ?: JSONObject.NULL)
            .put("pace_since_seconds_per_km", event.paceSinceSecondsPerKm ?: JSONObject.NULL)

    private fun kilometre(meters: Double): Int = (meters / METRES_PER_KILOMETRE).toInt() + 1
}
