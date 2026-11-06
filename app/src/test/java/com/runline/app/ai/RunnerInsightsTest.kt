package com.runline.app.ai

import com.runline.app.core.ActivityDetails
import com.runline.app.core.ActivityType
import com.runline.app.core.RunPoint
import com.runline.app.core.RunSession
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneOffset

class RunnerInsightsTest {
    private val day = 86_400_000L
    private val now = 1_800_000_000_000L

    private fun run(id: String, daysAgo: Int, km: Double, seconds: Long, type: ActivityType = ActivityType.RUN): RunSession {
        val start = now - daysAgo * day
        val steps = 100
        val points = (0..steps).map { RunPoint(37.17, -3.6 + km * 1000 / steps * it / 88_600.0, start + seconds * 1000 / steps * it, 4f) }
        return RunSession(id, start, start + seconds * 1000, seconds * 1000, points, type)
    }

    @Test fun `snapshot measures recent volume, longest run, typical moving pace and effort`() {
        val runs = listOf(run("a", 1, 10.0, 3720), run("b", 3, 4.7, 1800), run("c", 8, 3.0, 1080), run("d", 40, 12.0, 4800),
            run("walk", 2, 6.0, 4000, ActivityType.WALK))
        val efforts = mapOf("a" to 7, "b" to 5)
        val snapshot = TrainingSnapshot.of(runs, { ActivityDetails(effort = efforts[it]) }, now, ZoneOffset.UTC)
        assertEquals(3, snapshot.runs28Days)
        assertEquals((10.0 + 4.7 + 3.0) / 4, snapshot.kilometresPerWeek, .05)
        assertEquals(12.0, snapshot.longestKilometres90Days!!, .05)
        assertEquals(6.0, snapshot.averageEffort!!, 1e-9)
        assertEquals(4, snapshot.totalRuns)
        assertNotNull(snapshot.typicalPaceSeconds)
        // A walk retyped as a run in its details counts as a run.
        val retyped = TrainingSnapshot.of(runs, { if (it == "walk") ActivityDetails(activityType = ActivityType.RUN) else ActivityDetails() }, now, ZoneOffset.UTC)
        assertEquals(4, retyped.runs28Days)
    }

    @Test fun `strict parsing rejects extra keys, unknown levels, links and long text`() {
        val valid = JSONObject().put("experience", "REGULAR").put("summary", "Runs three times a week, about 6:20/km.").put("focus", "Add one longer easy run.")
        val parsed = RunnerInsights.parse(valid.toString(), now, 4, "model")
        assertEquals(RunningExperience.REGULAR, parsed.experience)
        assertEquals(parsed, RunnerInsights.stored(parsed.json()))
        listOf(JSONObject(valid.toString()).put("extra", "x"), JSONObject(valid.toString()).put("experience", "UNSPECIFIED"),
            JSONObject(valid.toString()).put("summary", "see https://example.com"), JSONObject(valid.toString()).put("focus", "x".repeat(200)))
            .forEach { bad -> assertThrows(Exception::class.java) { RunnerInsights.parse(bad.toString(), now, 4, "model") } }
    }

    @Test fun `updates follow new runs at most every six hours`() {
        val last = RunnerInsights(RunningExperience.REGULAR, "s", "f", now, 5, "m")
        assertTrue(ProfileAi.due(null, 1, now))
        assertFalse(ProfileAi.due(null, 0, now))
        assertFalse(ProfileAi.due(last, 5, now + 2 * day))
        assertFalse(ProfileAi.due(last, 6, now + 3_600_000))
        assertTrue(ProfileAi.due(last, 6, now + 7 * 3_600_000))
    }

    @Test fun `self report needs consent while the AI assessment needs management`() {
        val insights = RunnerInsights(RunningExperience.RETURNING, "Back after a break.", "Build slowly.", now, 3, "m")
        val private = RunnerProfile(RunningExperience.STARTING, healthNotes = "knee", includeInAi = false, aiManaged = true)
        val shared = private.forAi(insights)!!
        assertFalse(shared.has("self_reported_health_notes"))
        assertEquals("RETURNING", shared.getJSONObject("ai_maintained_profile").getString("experience"))
        assertNull(private.copy(aiManaged = false).forAi(insights))
        val consented = private.copy(includeInAi = true).forAi(insights)!!
        assertEquals("knee", consented.getString("self_reported_health_notes"))
        // With AI management, the AI's experience replaces the manual choice rather than contradicting it.
        assertFalse(consented.has("experience"))
        assertEquals(true, RunnerProfile.parse(private.json()).aiManaged)
        assertEquals(false, RunnerProfile.parse(JSONObject(private.json().toString()).apply { remove("ai_managed") }).aiManaged)
    }
}
