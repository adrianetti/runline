package com.runline.app.ai

import com.runline.app.core.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DailySuggestionTest {
    private val today = LocalDate.of(2026, 11, 5)
    private val now = 1_800_000_000_000L
    private val answer = SmartRunSuggestion(SmartRunGoalType.DISTANCE, targetDistanceKm = 7.0,
        minDistanceKm = 5.0, maxDistanceKm = 9.0, paceMinSecondsPerKm = 360, paceMaxSecondsPerKm = 400, rationale = "Fits recent training.")
    private var disk: DailySuggestion? = null
    private var calls = 0
    private fun attempt(day: LocalDate = today, time: Long = now, zone: String = "Europe/Madrid", key: String = "history-v1",
                        explicit: Boolean = false, current: () -> Boolean = { true },
                        before: () -> Unit = {}, request: () -> SmartRunSuggestion = { answer }): SmartRunAiState =
        DailySuggestionWork.execute(day, time, zone, key, explicit, disk, { disk = DailySuggestion.parse(it.json().toString()) },
            before, { calls++; request() }, current)

    @Test fun reopeningAndSerializedDuplicateCallsReuseDurableResultAndIdentity() {
        val first = attempt()
        repeat(20) { assertEquals(first, attempt()) }
        assertEquals(1, calls); assertNotNull(first.id); assertEquals(answer, disk!!.suggestion)
        assertEquals(answer.paceMaxSecondsPerKm, first.suggestion!!.paceMaxSecondsPerKm)
        assertEquals(now, first.preparedAtMs)
        assertEquals(now, attempt(time = now + 3_600_000).preparedAtMs)
        assertEquals(1, calls)
    }
    @Test fun killedOrFailedProviderLeavesReservationAndNeedsExplicitRetry() {
        try { attempt(request = { throw AiFailure(AiFailure.Reason.NETWORK) }); fail() } catch (_: AiFailure) { }
        assertNull(disk!!.suggestion); assertEquals(today.toString(), disk!!.day)
        assertEquals(AiFailure.Reason.RESPONSE, attempt().failure); assertEquals(1, calls)
        assertNotNull(attempt(time = now + 60_001, explicit = true).suggestion); assertEquals(2, calls)
    }
    @Test fun offlineBeforeProviderDoesNotCreatePaidReservation() {
        try { attempt(before = { throw AiFailure(AiFailure.Reason.NETWORK) }); fail() } catch (_: AiFailure) { }
        assertNull(disk); assertEquals(0, calls)
        assertNotNull(attempt().suggestion)
    }
    @Test fun materialHistoryAndConsentChangesInvalidateWithoutAutomaticSecondRequest() {
        attempt()
        val stale = attempt(key = "edited-history-or-consent-v2")
        assertTrue(stale.changed); assertNull(stale.suggestion); assertEquals(1, calls)
        assertNotNull(attempt(key = "edited-history-or-consent-v2", explicit = true, time = now + 61_000).suggestion)
        assertEquals(2, calls)
    }
    @Test fun staleProviderCompletionCannotBeSavedOrApplied() {
        var active = true
        try { attempt(current = { active }, request = { active = false; answer }); fail() }
        catch (e: AiFailure) { assertEquals(AiFailure.Reason.DISABLED, e.reason) }
        assertNull(disk!!.suggestion); assertNull(attempt().suggestion); assertEquals(1, calls)
    }
    @Test fun cannotCallProviderWithoutDurableReservation() {
        try {
            DailySuggestionWork.execute(today, now, "UTC", "key", false, null,
                { throw java.io.IOException("disk full") }, {}, { calls++; answer }, { true })
            fail()
        } catch (_: java.io.IOException) { }
        assertEquals(0, calls)
    }
    @Test fun failedResultWriteStillKeepsReservationAndDoesNotSilentlyRetry() {
        var writes = 0
        try {
            DailySuggestionWork.execute(today, now, "Europe/Madrid", "history-v1", false, null,
                { if (++writes == 2) throw java.io.IOException("disk full") else disk = it }, {}, { calls++; answer }, { true })
            fail()
        } catch (_: java.io.IOException) { }
        assertNull(disk!!.suggestion); assertNull(attempt().suggestion); assertEquals(1, calls)
    }
    @Test fun nextLocalDayMayGenerateButBackwardClocksCannotReuseOrMultiplyCalls() {
        attempt()
        assertNull(attempt(time = now - 1).suggestion)
        assertNull(attempt(day = today.minusDays(1)).suggestion)
        assertNotNull(attempt(day = today.plusDays(1), time = now + 86_400_000).suggestion)
        assertEquals(2, calls)
        assertNull(attempt(time = now + 87_000_000).suggestion); assertEquals(2, calls)
    }
    @Test fun timezoneChangeCannotCreateAnImmediateNewPaidDay() {
        attempt()
        assertNull(attempt(day = today.plusDays(1), time = now + 60_000, zone = "Pacific/Auckland").suggestion)
        assertEquals(1, calls)
        assertNotNull(attempt(day = today.plusDays(1), time = now + 21 * 3_600_000L, zone = "Pacific/Auckland").suggestion)
        assertEquals(2, calls)
    }
    @Test fun explicitRetriesHaveCooldownAndAreStillCancelable() {
        attempt()
        try { attempt(explicit = true, time = now + 59_999); fail() }
        catch (e: AiFailure) { assertEquals(AiFailure.Reason.RATE_LIMIT, e.reason) }
        try { attempt(explicit = true, time = now + 61_000, current = { false }); fail() }
        catch (e: AiFailure) { assertEquals(AiFailure.Reason.DISABLED, e.reason) }
        assertEquals(1, calls)
    }
    @Test fun restAndPaceFreeTimeGoalsPersistWithoutInventedTargets() {
        val rest = SmartRunSuggestion(SmartRunGoalType.REST, rationale = "Rest today.")
        assertEquals(rest, attempt(request = { rest }).suggestion)
        val timed = SmartRunSuggestion(SmartRunGoalType.TIME, durationMinutes = 40.0, rationale = "An easy outing.")
        assertEquals(timed, attempt(day = today.plusDays(1), time = now + 86_400_000, request = { timed }).suggestion)
        assertNull(disk!!.suggestion!!.paceMinSecondsPerKm); assertNull(disk!!.suggestion!!.targetDistanceKm)
    }
    @Test fun invalidOrFutureSchemaCacheFailsClosed() {
        attempt()
        for (broken in listOf(disk!!.json().put("version", 2).toString(), disk!!.json().put("day", "invalid").toString(), "x".repeat(16001))) {
            try { DailySuggestion.parse(broken); fail() } catch (_: Exception) { }
        }
    }
}
