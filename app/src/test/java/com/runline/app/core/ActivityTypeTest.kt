package com.runline.app.core

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.xml.sax.InputSource

class ActivityTypeTest {
    @Test fun missingOrUnknownStoredActivityDefaultsToLegacyRunning() {
        assertEquals(ActivityType.RUN, ActivityType.fromStorage(null))
        assertEquals(ActivityType.RUN, ActivityType.fromStorage(""))
        assertEquals(ActivityType.RUN, ActivityType.fromStorage("new-sport"))
        assertEquals(ActivityType.WALK, ActivityType.fromStorage("walk"))
        assertEquals(ActivityType.HIKE, ActivityType.fromStorage("HIKE"))
    }

    @Test fun gpxPreservesActivityTypeAndOriginalTrackForEverySupportedActivity() {
        val points = listOf(
            RunPoint(37.17, -3.59, 1000, 4f, 700.0),
            RunPoint(37.1701, -3.59, 2000, 4f, 703.0),
            RunPoint(37.1702, -3.59, 3000, 4f, 699.0, segment = 1),
        )
        val expectedTypes = mapOf(ActivityType.RUN to "running", ActivityType.WALK to "walking", ActivityType.HIKE to "hiking")
        for ((activityType, gpxType) in expectedTypes) {
            val run = RunSession("morning & evening", 1000, 3000, 2000, points, activityType)
            val document = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
                .newDocumentBuilder().parse(InputSource(StringReader(GpxExporter.export(run))))
            assertEquals(gpxType, document.getElementsByTagNameNS("*", "type").item(0).textContent)
            assertEquals("${activityType.gpxName} morning & evening", document.getElementsByTagNameNS("*", "name").item(0).textContent)
            assertEquals(3, document.getElementsByTagNameNS("*", "trkpt").length)
            assertEquals(2, document.getElementsByTagNameNS("*", "trkseg").length)
            assertEquals("703", document.getElementsByTagNameNS("*", "ele").item(1).textContent)
        }
    }
}
