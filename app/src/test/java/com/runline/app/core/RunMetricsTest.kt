package com.runline.app.core

import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.xml.sax.InputSource

class RunMetricsTest {
    private fun point(
        longitude: Double,
        time: Long,
        segment: Int = 0,
        latitude: Double = 0.0,
        altitude: Double? = null,
    ) = RunPoint(latitude, longitude, time, 5f, altitude, segment)

    private fun run(points: List<RunPoint>, id: String = "test-run") = RunSession(
        id = id,
        startedAtMs = points.firstOrNull()?.timestampMs ?: 0L,
        endedAtMs = points.lastOrNull()?.timestampMs ?: 0L,
        activeDurationMs = 1_000_000L,
        points = points,
    )

    @Test fun equatorialTrackHasExpectedDistanceAndPace() {
        val distance = RunMetrics.distanceMeters(listOf(point(0.0, 0), point(0.01, 360_000)))
        assertEquals(1111.9508, distance, 0.01)
        assertEquals(300.0, RunMetrics.paceSecondsPerKm(2000.0, 600_000)!!, 0.0001)
    }

    @Test fun pauseDoesNotAddRelocationDistanceOrPausedTime() {
        val activity = run(listOf(
            point(0.0, 0),
            point(0.005, 180_000),
            point(0.5, 780_000, segment = 1),
            point(0.505, 960_000, segment = 1),
        ))
        assertEquals(1111.9508, RunMetrics.distanceMeters(activity.points), 0.01)
        val splits = RunMetrics.splits(activity)
        assertEquals(2, splits.size)
        assertEquals(360_000L, splits.sumOf { it.durationMs })
        assertEquals(1000.0, splits[0].distanceMeters, 0.0001)
        assertEquals(111.9508, splits[1].distanceMeters, 0.01)
        assertEquals(1, splits[0].index)
        assertEquals(2, splits[1].index)
    }

    @Test fun aLongGpsIntervalIsInterpolatedAcrossEveryKilometreBoundary() {
        val splits = RunMetrics.splits(run(listOf(point(0.0, 0), point(0.03, 1_000_000))))
        assertEquals(4, splits.size)
        for (split in splits.take(3)) {
            assertEquals(1000.0, split.distanceMeters, 0.0001)
            assertEquals(299_773.0, split.durationMs.toDouble(), 2.0)
        }
        assertEquals(335.8524, splits.last().distanceMeters, 0.02)
        assertEquals(1_000_000L, splits.sumOf { it.durationMs })
    }

    @Test fun aShortRunKeepsItsPartialSplit() {
        val splits = RunMetrics.splits(run(listOf(point(0.0, 0), point(0.001, 40_000))))
        assertEquals(1, splits.size)
        assertEquals(111.19508, splits[0].distanceMeters, 0.001)
        assertEquals(40_000L, splits[0].durationMs)
    }

    @Test fun stationaryTimeCountsUntilTheRunnerExplicitlyPauses() {
        val splits = RunMetrics.splits(run(listOf(
            point(0.0, 0), point(0.001, 60_000),
            point(0.001, 90_000), point(0.002, 150_000),
        )))
        assertEquals(150_000L, splits.sumOf { it.durationMs })
        assertEquals(222.39016, splits.sumOf { it.distanceMeters }, 0.001)
    }

    @Test fun emptyAndStationaryRunsHaveNoPhantomSplitOrPace() {
        assertEquals(0.0, RunMetrics.distanceMeters(emptyList()), 0.0)
        assertTrue(RunMetrics.splits(run(emptyList())).isEmpty())
        assertTrue(RunMetrics.splits(run(listOf(point(0.0, 0)))).isEmpty())
        assertTrue(RunMetrics.splits(run(listOf(point(0.0, 0), point(0.0, 60_000)))).isEmpty())
        assertNull(RunMetrics.paceSecondsPerKm(0.0, 60_000))
        assertNull(RunMetrics.paceSecondsPerKm(1000.0, 0))
        assertNull(RunMetrics.paceSecondsPerKm(Double.NaN, 60_000))
        assertNull(RunMetrics.paceSecondsPerKm(Double.POSITIVE_INFINITY, 60_000))
        assertNull(RunMetrics.paceSecondsPerKm(-1000.0, 60_000))
    }

    @Test fun aClockRegressionCannotCreateNegativeSplitsOrExtraDistance() {
        val activity = run(listOf(point(0.0, 0), point(0.001, 60_000), point(0.002, 30_000)))
        assertEquals(111.19508, RunMetrics.distanceMeters(activity.points), 0.001)
        val splits = RunMetrics.splits(activity)
        assertEquals(1, splits.size)
        assertEquals(60_000L, splits[0].durationMs)
    }

    @Test fun invalidCoordinatesBreakTheTrackInsteadOfConnectingAcrossMissingData() {
        val activity = run(listOf(
            point(0.0, 0), point(Double.NaN, 60_000),
            point(0.002, 120_000), point(0.003, 180_000),
            point(0.004, 240_000, latitude = 91.0),
        ))
        assertEquals(111.19508, RunMetrics.distanceMeters(activity.points), 0.001)
        assertEquals(60_000L, RunMetrics.splits(activity).sumOf { it.durationMs })
    }

    @Test fun elevationIsUnavailableWhenTheRecorderHasNoAltitude() {
        assertNull(RunMetrics.elevationGainMeters(emptyList()))
        assertNull(RunMetrics.elevationGainMeters(listOf(point(0.0, 0), point(0.001, 60_000))))
    }

    @Test fun gpxIsValidXmlAndRetainsSegmentsCoordinatesAltitudeAndTime() {
        val activity = run(listOf(
            point(-3.5987654321, 0, latitude = 37.1765432109, altitude = 700.25),
            point(-3.5977654321, 60_000, latitude = 37.1775432109, altitude = 701.5),
            point(-3.5967654321, 180_000, segment = 1, latitude = 37.1785432109),
        ), id = "run <morning> & \"friends\"")
        val xml = GpxExporter.export(activity)
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        assertEquals("gpx", document.documentElement.localName)
        assertEquals("http://www.topografix.com/GPX/1/1", document.documentElement.namespaceURI)
        assertEquals(2, document.getElementsByTagNameNS("*", "trkseg").length)
        val points = document.getElementsByTagNameNS("*", "trkpt")
        assertEquals(3, points.length)
        val first = points.item(0) as org.w3c.dom.Element
        assertEquals(37.1765432109, first.getAttribute("lat").toDouble(), 1e-10)
        assertEquals(-3.5987654321, first.getAttribute("lon").toDouble(), 1e-10)
        assertEquals("700.25", first.getElementsByTagNameNS("*", "ele").item(0).textContent)
        assertEquals("1970-01-01T00:00:00Z", first.getElementsByTagNameNS("*", "time").item(0).textContent)
        assertTrue(document.documentElement.textContent.contains(activity.id))
        assertFalse(xml.contains("NaN"))
        assertNotNull(document.getElementsByTagNameNS("*", "trk").item(0))
    }
}
