package com.runline.app.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ClockFormatTest {
    @Test fun aPairPadsTheSecondPart() {
        assertEquals("5:07", ClockFormat.pair(307, Locale.ROOT))
        assertEquals("0:00", ClockFormat.pair(0, Locale.ROOT))
        assertEquals("2:58", ClockFormat.pair(178, Locale.ROOT))
    }

    @Test fun aDurationGainsHoursAfterAnHour() {
        assertEquals("59:59", ClockFormat.duration(3_599, Locale.ROOT))
        assertEquals("1:00:00", ClockFormat.duration(3_600, Locale.ROOT))
        assertEquals("1:06:05", ClockFormat.duration(3_965, Locale.ROOT))
    }
}
