package com.runline.app.core

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import javax.xml.parsers.SAXParserFactory

/** What a GPX file offers as a route to follow. */
data class GpxRoute(
    val name: String?,
    val description: String?,
    val points: List<Triple<Double, Double, Double?>>,
    val waypoints: List<RouteWaypoint>,
)

/** Bounded, namespace-aware GPX import. Extensions and all external entities are ignored/refused. */
object GpxRouteReader {
    const val MAX_BYTES = 15 * 1024 * 1024
    private const val MAX_WAYPOINTS = 500
    private const val MAX_TEXT = 500
    private val namespaces = setOf("", "http://www.topografix.com/GPX/1/0", "http://www.topografix.com/GPX/1/1")
    class InvalidGpx(message: String) : IllegalArgumentException(message)

    fun read(bytes: ByteArray): GpxRoute {
        if (bytes.size > MAX_BYTES) throw InvalidGpx("too large")
        return read(String(bytes, Charsets.UTF_8))
    }

    fun read(source: String): GpxRoute {
        if (source.length > MAX_BYTES) throw InvalidGpx("too large")
        val text = source.removePrefix("\uFEFF")
        // Do not depend on parser-specific feature support for the no-DTD boundary.
        if (text.contains("<!DOCTYPE", true) || text.contains("<!ENTITY", true)) throw InvalidGpx("DOCTYPE is not allowed")
        val handler = Reader()
        try {
            val reader = SAXParserFactory.newInstance().apply { isNamespaceAware = true }.newSAXParser().xmlReader
            // Android and desktop parsers differ in supported features. The resolver below is mandatory.
            listOf("http://xml.org/sax/features/external-general-entities", "http://xml.org/sax/features/external-parameter-entities",
                "http://apache.org/xml/features/nonvalidating/load-external-dtd").forEach { feature ->
                runCatching { reader.setFeature(feature, false) }
            }
            reader.entityResolver = org.xml.sax.EntityResolver { _, _ -> throw SAXException("external entity") }
            reader.contentHandler = handler
            reader.errorHandler = handler
            reader.parse(InputSource(StringReader(text)))
        } catch (_: Exception) { throw InvalidGpx("invalid GPX") }
        return handler.result()
    }

    private class Reader : DefaultHandler() {
        private val path = ArrayList<String>()
        private val track = ArrayList<Triple<Double, Double, Double?>>()
        private val route = ArrayList<Triple<Double, Double, Double?>>()
        private val waypoints = ArrayList<RouteWaypoint>()
        private val buffer = StringBuilder()
        private var namespace = ""
        private var metaName: String? = null; private var metaDesc: String? = null
        private var trackName: String? = null; private var routeName: String? = null
        private var lat = Double.NaN; private var lon = Double.NaN; private var ele: Double? = null
        private var waypointName: String? = null; private var waypointDesc: String? = null
        private var pointCount = 0
        private fun isPath(vararg parts: String) = path.size == parts.size && path.indices.all { path[it] == parts[it] }
        private fun trackPoint() = isPath("gpx", "trk", "trkseg", "trkpt")
        private fun routePoint() = isPath("gpx", "rte", "rtept")
        private fun waypoint() = isPath("gpx", "wpt")

        override fun startElement(uri: String, localName: String, qName: String, attrs: Attributes) {
            if (path.isEmpty()) {
                if (localName != "gpx" || uri !in namespaces) throw SAXException("not GPX")
                namespace = uri
            }
            if (path.size >= 32) throw SAXException("too deep")
            path += if (uri == namespace) localName else "#extension"
            buffer.setLength(0)
            if (trackPoint() || routePoint() || waypoint()) {
                if (++pointCount > HikingRoute.MAX_POINTS) throw SAXException("too many points")
                lat = attrs.getValue("lat")?.toDoubleOrNull() ?: Double.NaN
                lon = attrs.getValue("lon")?.toDoubleOrNull() ?: Double.NaN
                ele = null; waypointName = null; waypointDesc = null
            }
        }

        override fun characters(chars: CharArray, start: Int, length: Int) {
            if (buffer.length < MAX_TEXT) buffer.append(chars, start, minOf(length, MAX_TEXT - buffer.length))
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            val value = buffer.toString().replace(Regex("\\s+"), " ").filterNot { it.isISOControl() }.trim().ifEmpty { null }
            when {
                isPath("gpx", "metadata", "name") || isPath("gpx", "name") -> if (metaName == null) metaName = value
                isPath("gpx", "metadata", "desc") || isPath("gpx", "desc") -> if (metaDesc == null) metaDesc = value
                isPath("gpx", "trk", "name") -> if (trackName == null) trackName = value
                isPath("gpx", "rte", "name") -> if (routeName == null) routeName = value
                isPath("gpx", "wpt", "name") -> waypointName = value
                isPath("gpx", "wpt", "desc") -> waypointDesc = value
                isPath("gpx", "trk", "trkseg", "trkpt", "ele") || isPath("gpx", "rte", "rtept", "ele") || isPath("gpx", "wpt", "ele") -> ele = value?.toDoubleOrNull()
                lat in -90.0..90.0 && lon in -180.0..180.0 -> when {
                    trackPoint() -> track += Triple(lat, lon, ele)
                    routePoint() -> route += Triple(lat, lon, ele)
                    waypoint() && waypoints.size < MAX_WAYPOINTS -> waypoints += RouteWaypoint(lat, lon, waypointName.orEmpty(), waypointDesc, ele)
                }
            }
            path.removeAt(path.lastIndex)
            buffer.setLength(0)
        }

        override fun error(e: org.xml.sax.SAXParseException) { throw e }
        override fun fatalError(e: org.xml.sax.SAXParseException) { throw e }

        fun result(): GpxRoute {
            val points = if (track.size >= 2) track else route
            if (points.size < 2) throw InvalidGpx("no track")
            return GpxRoute(metaName ?: trackName ?: routeName, metaDesc, points, waypoints)
        }
    }
}
