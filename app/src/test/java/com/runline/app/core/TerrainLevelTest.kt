package com.runline.app.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class TerrainLevelTest {
    private val start = RunPoint(37.18, -3.61, 0, 3f)
    private val origin = SpainTerrainProjection.project(start.latitude, start.longitude)!!
    private val key = TerrainTileKey.at(start)!!

    private fun map(height: (Double, Double) -> Double): TerrainMapSnapshot {
        val grid =
            TerrainGrid.parse(
                buildString {
                    append("ncols 220\nnrows 220\nxllcorner ${key.west}\nyllcorner ${key.south}\ncellsize 5\n")
                    repeat(220) { row ->
                        append(
                            List(220) { col ->
                                val east = key.west + (col + .5) * 5 - origin.first
                                val north = key.south + (220 - row - .5) * 5 - origin.second
                                "%.3f".format(java.util.Locale.ROOT, height(east, north))
                            }.joinToString(" "),
                        )
                        append('\n')
                    }
                },
            )
        return TerrainMapSnapshot(1, true, mapOf(key to grid))
    }

    private fun run(count: Int = 150) =
        RunSession(
            "levels",
            0,
            count * 1000L,
            count * 1000L,
            (0 until count).map { RunPoint(start.latitude + it * 3.0 / 111195.08, start.longitude, it * 1000L, 3f, 700.0, verticalAccuracyMeters = 3f) },
        )

    private fun north(point: RunPoint) = SpainTerrainProjection.project(point.latitude, point.longitude)!!.second - origin.second

    private fun band(
        north: Double,
        centre: Double,
        half: Double,
        sides: Double,
        inside: Double,
        outside: Double,
    ): Double {
        val distance = abs(north - centre)
        return when {
            distance <= half -> inside
            distance >= half + sides -> outside
            else -> inside + (outside - inside) * (distance - half) / sides
        }
    }

    @Test fun `a deck over a lower road keeps the runner on the deck`() {
        val terrain = map { _, n -> band(n, 200.0, 6.0, 6.0, 652.0, 658.0) }
        val projected = terrain.project(run()).points
        val crossing = projected.filter { north(it) in 170.0..230.0 }
        assertTrue(crossing.all { it.altitudeMeters != null })
        assertTrue(crossing.all { abs(it.altitudeMeters!! - 658.0) < .3 })
        assertTrue(projected.any { it.terrainLevel == TerrainLevel.ABOVE })
        assertTrue(projected.none { it.terrainModel == TerrainModel.STRUCTURE })
    }

    @Test fun `a passage under an embankment keeps the runner on the lower road`() {
        val terrain = map { _, n -> band(n, 200.0, 8.0, 6.0, 658.0, 652.0) }
        val projected = terrain.project(run()).points
        assertTrue(projected.all { it.altitudeMeters != null && abs(it.altitudeMeters!! - 652.0) < .3 })
        assertTrue(projected.any { it.terrainLevel == TerrainLevel.BELOW })
        assertTrue(projected.zipWithNext().all { (a, b) -> canJoinAltitude(a, b) })
    }

    @Test fun `a runnable ramp beside a wall keeps the model's own heights`() {
        val terrain =
            map { e, n ->
                val ramp = 650.0 + (n - 180.0).coerceIn(0.0, 50.0) * .08
                if (abs(e) > 6.0 && n in 150.0..260.0) ramp - 10.0 else ramp
            }
        val walled = run().let { r -> r.copy(points = r.points.map { if (north(it) in 140.0..270.0) it.copy(accuracyMeters = 10f) else it }) }
        val projected = terrain.project(walled).points
        assertTrue(projected.count { it.terrainLevel != null } > 20)
        val foot = projected.first { abs(north(it) - 180.0) < 1.6 }
        assertEquals(TerrainLevel.GROUND, foot.terrainLevel)
        assertEquals(650.0, foot.altitudeMeters!!, .2)
        assertTrue(projected.all { it.altitudeMeters != null })
    }

    @Test fun `without clean heights on both sides or over a long span the height stays unknown`() {
        val openEnd = map { _, n -> band(n, 445.0, 6.0, 6.0, 652.0, 658.0) }.project(run()).points
        assertTrue(openEnd.takeLast(3).all { it.altitudeMeters == null && it.terrainModel == TerrainModel.STRUCTURE })
        val long =
            run().points.mapIndexed { index, point ->
                if (index in 20..120) {
                    point.copy(altitudeMeters = null, terrainModel = TerrainModel.STRUCTURE)
                } else {
                    point.copy(altitudeMeters = 655.0, terrainModel = TerrainModel.IGN_MDT05)
                }
            }
        val resolved = TerrainStructureGuard.resolve(long) { 640.0 }
        assertTrue((20..120).all { resolved[it].altitudeMeters == null && resolved[it].terrainLevel == null })
        val short = long.mapIndexed { index, point -> if (index in 20..120 && index !in 40..60) point.copy(altitudeMeters = 655.0, terrainModel = TerrainModel.IGN_MDT05) else point }
        val joined = TerrainStructureGuard.resolve(short) { 640.0 }
        assertTrue((40..60).all { joined[it].altitudeMeters == 655.0 && joined[it].terrainLevel == TerrainLevel.ABOVE })
    }

    @Test fun `a path across or up an even hillside keeps the model's heights`() {
        val terrain = map { _, n -> 700.0 + n * .4 }
        val across =
            RunSession(
                "contour",
                0,
                150_000,
                150_000,
                (0 until 150).map {
                    RunPoint(start.latitude, start.longitude + it * 3.0 / (111320.0 * kotlin.math.cos(Math.toRadians(start.latitude))), it * 1000L, 10f)
                },
            )
        val contour = terrain.project(across).points.filter { TerrainTileKey.at(it) == key }
        assertTrue(contour.size > 40)
        assertTrue(contour.all { it.terrainModel == TerrainModel.IGN_MDT05 && abs(it.altitudeMeters!! - (700.0 + north(it) * .4)) < .3 })
        val up = terrain.project(run().let { r -> r.copy(points = r.points.map { it.copy(accuracyMeters = 10f) }) }).points
        assertTrue(up.all { it.terrainModel == TerrainModel.IGN_MDT05 && abs(it.altitudeMeters!! - (700.0 + north(it) * .4)) < .3 })
    }

    @Test fun `live projection fills a stretch once the clean side arrives and matches the saved view`() {
        val terrain = map { _, n -> band(n, 200.0, 6.0, 6.0, 652.0, 658.0) }
        val full = run()
        val projector = TerrainProjector()
        val partial = projector.project(full.copy(points = full.points.take(70)), terrain).points
        assertNull(partial.last().altitudeMeters)
        assertEquals(terrain.project(full).points, projector.project(full, terrain).points)
        assertTrue(full.points.all { it.altitudeMeters == 700.0 && it.terrainLevel == null })
    }
}
