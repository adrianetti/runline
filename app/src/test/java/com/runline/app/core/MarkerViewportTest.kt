package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test

class MarkerViewportTest {
    @Test fun projectionUsesRealPositionWithoutClampingOffscreenCoordinates() {
        assertEquals(MarkerViewport.Placement(79f, 179f), MarkerViewport.place(100f, 200f, 400, 800, 42))
        assertEquals(MarkerViewport.Placement(-21f, -21f), MarkerViewport.place(0f, 0f, 400, 800, 42))
        assertNull(MarkerViewport.place(-1f, 200f, 400, 800, 42))
        assertNull(MarkerViewport.place(401f, 200f, 400, 800, 42))
        assertNull(MarkerViewport.place(200f, 801f, 400, 800, 42))
        assertNull(MarkerViewport.place(Float.NaN, 0f, 400, 800, 42))
        assertNull(MarkerViewport.place(100f, 200f, 0, 800, 42))
    }
}
