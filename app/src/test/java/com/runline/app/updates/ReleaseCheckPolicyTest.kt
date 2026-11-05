package com.runline.app.updates

import org.junit.Assert.*
import org.junit.Test

class ReleaseCheckPolicyTest {
    private val now = 1_800_000_000_000L
    @Test fun successesCacheForSixHoursAndExplicitRefreshCanRecheck() {
        assertFalse(ReleaseCheckPolicy.shouldCheck(now, now - 1000, 0, true, false, false))
        assertTrue(ReleaseCheckPolicy.shouldCheck(now, now - 6 * 3_600_000, 0, true, false, false))
        assertTrue(ReleaseCheckPolicy.shouldCheck(now, now - 1000, 0, true, true, false))
        assertTrue(ReleaseCheckPolicy.shouldCheck(now, now - 1000, 0, false, false, false))
    }
    @Test fun manualRefreshCannotBypassSingleFlightOrBackoff() {
        for (force in listOf(false, true)) {
            assertFalse(ReleaseCheckPolicy.shouldCheck(now, 0, 0, false, force, true))
            assertFalse(ReleaseCheckPolicy.shouldCheck(now, 0, now + 1, false, force, false))
        }
    }
    @Test fun offlineErrorsKeepCooldownAndServerBackoffIsRespected() {
        assertEquals(now + 60_000, ReleaseCheckPolicy.retryAt(now, 500, null, null))
        assertEquals(now + 900_000, ReleaseCheckPolicy.retryAt(now, 403, null, null))
        assertEquals(now + 3_600_000, ReleaseCheckPolicy.retryAt(now, 429, "3600", null))
        assertEquals(now + 7_200_000, ReleaseCheckPolicy.retryAt(now, 403, "3600", "${now / 1000 + 7200}"))
    }
    @Test fun MalformedAndOversizedHeadersCannotOverflowOrCreateUnboundedDelay() {
        assertEquals(now + 900_000, ReleaseCheckPolicy.retryAt(now, 429, "invalid", "-999"))
        assertEquals(now + 86_400_000, ReleaseCheckPolicy.retryAt(now, 429, Long.MAX_VALUE.toString(), Long.MAX_VALUE.toString()))
    }
}
