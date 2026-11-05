package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.xml.sax.InputSource

class GpxAccuracyTest {
    @Test fun exportsOriginalAltitudeAndReportedAccuracyWithoutInventingLegacyPrecision() {
        val points = listOf(RunPoint(0.0, 0.0, 1000, 3f, 100.0),
            RunPoint(.0001, 0.0, 2000, 4f, 88.0, verticalAccuracyMeters = 20f))
        val xml = GpxExporter.export(RunSession("synthetic-accuracy", 1000, 2000, 1000, points))
        val doc = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
            .newDocumentBuilder().parse(InputSource(StringReader(xml)))
        val ns = "https://github.com/adrianetti/runline/xmlns/1"
        assertEquals(2, doc.getElementsByTagNameNS(ns, "horizontalAccuracyMeters").length)
        val vertical = doc.getElementsByTagNameNS(ns, "verticalAccuracyMeters")
        assertEquals(1, vertical.length); assertEquals("20", vertical.item(0).textContent)
        assertEquals("88", doc.getElementsByTagNameNS("http://www.topografix.com/GPX/1/1", "ele").item(1).textContent)
    }
}
