package com.runline.app.ai

import com.runline.app.core.RunPoint
import com.runline.app.core.RunSession
import com.runline.app.core.TerrainModel
import com.runline.app.core.TerrainSource
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

internal class SyntheticRoute(
    place: JSONObject,
) {
    private val latitude = place.getDouble("lat")
    private val longitude = place.getDouble("lon")
    private val shape = place.optString("route", "line")
    private val length = place.optDouble("length_m", 0.0)
    private val base = place.optDouble("base_m", 0.0)
    private val grades = place.optJSONArray("grades").pairs()
    val terrainModel: TerrainModel = TerrainModel.valueOf(place.optString("terrain_model", TerrainModel.MAPZEN.name))

    private fun position(distance: Double): Double =
        when (shape) {
            LOOP -> distance.mod(length)
            OUT_AND_BACK -> if (returning(distance)) length - distance.mod(length) else distance.mod(length)
            else -> distance
        }

    private fun returning(distance: Double) = shape == OUT_AND_BACK && (distance / length).toInt() % 2 == 1

    fun grade(distance: Double): Double {
        val outward = grades.lastOrNull { it.first <= position(distance) }?.second ?: 0.0
        return if (returning(distance)) -outward else outward
    }

    fun altitude(distance: Double): Double {
        val at = position(distance)
        return base +
            grades.indices.sumOf { i ->
                val (start, grade) = grades[i]
                val end = grades.getOrNull(i + 1)?.first ?: Double.MAX_VALUE
                (minOf(at, end) - start).coerceAtLeast(0.0) * grade / PERCENT
            }
    }

    fun coordinates(
        distance: Double,
        east: Double,
        north: Double,
    ): Pair<Double, Double> {
        val (x, y) =
            if (shape == LOOP) {
                val radius = length / (2 * PI)
                val angle = 2 * PI * position(distance) / length
                radius * sin(angle) to radius * (1 - cos(angle))
            } else {
                position(distance) to 0.0
            }
        val lat = latitude + (y + north) / METERS_PER_DEGREE
        return lat to longitude + (x + east) / (METERS_PER_DEGREE * cos(Math.toRadians(latitude)))
    }

    private companion object {
        const val LOOP = "loop"
        const val OUT_AND_BACK = "out_and_back"
        const val PERCENT = 100.0
        const val METERS_PER_DEGREE = 111_195.0802

        fun JSONArray?.pairs(): List<Pair<Double, Double>> {
            val array = this ?: return emptyList()
            return (0 until array.length()).map { array.getJSONArray(it) }.map { it.getDouble(0) to it.getDouble(1) }
        }
    }
}

internal data class SyntheticBlock(
    val seconds: Double?,
    val meters: Double?,
    val pace: Double,
    val paceEnd: Double?,
) {
    fun paceAt(
        elapsed: Double,
        covered: Double,
    ): Double {
        val end = paceEnd ?: return pace
        val progress = (meters?.let { covered / it } ?: seconds?.let { elapsed / it } ?: 0.0).coerceIn(0.0, 1.0)
        return pace + (end - pace) * progress
    }

    fun done(
        elapsed: Double,
        covered: Double,
    ) = meters?.let { covered >= it } ?: (elapsed >= (seconds ?: 0.0))

    companion object {
        fun expand(plan: JSONArray): List<SyntheticBlock> =
            (0 until plan.length()).flatMap { index ->
                val item = plan.getJSONObject(index)
                val blocks = item.optJSONArray("blocks")
                if (blocks == null) listOf(of(item)) else List(item.optInt("repeat", 1)) { expand(blocks) }.flatten()
            }

        private fun of(item: JSONObject) =
            SyntheticBlock(
                item.optDouble("seconds").takeIf { it.isFinite() },
                item.optDouble("meters").takeIf { it.isFinite() },
                item.getDouble("pace"),
                item.optDouble("pace_end").takeIf { it.isFinite() },
            )
    }
}

internal data class SyntheticSample(
    val clockSeconds: Long,
    val recordSeconds: Long,
    val segment: Int,
    val distance: Double,
)

internal class SyntheticMotion(
    private val route: SyntheticRoute,
    private val random: Random,
    stops: JSONArray?,
    pauses: JSONArray?,
) {
    private val stops = ArrayDeque(stops.events())
    private val pauses = ArrayDeque(pauses.events())
    private val samples = mutableListOf(SyntheticSample(0, 0, 0, 0.0))
    private var clock = 0L
    private var record = 0L
    private var segment = 0
    private var distance = 0.0
    private var drift = 0.0

    fun run(blocks: List<SyntheticBlock>): List<SyntheticSample> {
        blocks.forEach { block ->
            var elapsed = 0.0
            var covered = 0.0
            while (!block.done(elapsed, covered)) {
                interrupt()
                val step = speed(block.paceAt(elapsed, covered))
                distance += step
                covered += step
                elapsed += 1
                tick()
            }
        }
        return samples
    }

    private fun speed(pace: Double): Double {
        val grade = route.grade(distance)
        val effort = if (grade > 0) 1 + grade * UPHILL_COST else 1 + grade * DOWNHILL_GAIN
        drift = drift * SPEED_MEMORY + random.gaussian() * SPEED_NOISE * sqrt(1 - SPEED_MEMORY * SPEED_MEMORY)
        return METERS_PER_KM / (pace * effort) * (1 + drift)
    }

    private fun interrupt() {
        stops.firstOrNull()?.takeIf { record >= it.first }?.let { (_, seconds) ->
            stops.removeFirst()
            repeat(seconds.toInt()) { tick() }
        }
        pauses.firstOrNull()?.takeIf { record >= it.first }?.let { (_, seconds) ->
            pauses.removeFirst()
            clock += seconds
            segment++
        }
    }

    private fun tick() {
        clock++
        record++
        samples += SyntheticSample(clock, record, segment, distance)
    }

    private companion object {
        const val METERS_PER_KM = 1000.0
        const val UPHILL_COST = 0.035
        const val DOWNHILL_GAIN = 0.018
        const val SPEED_MEMORY = 0.95
        const val SPEED_NOISE = 0.03

        fun JSONArray?.events(): List<Pair<Long, Long>> =
            if (this == null) {
                emptyList()
            } else {
                (0 until length()).map { getJSONObject(it).let { e -> e.getLong("at_s") to e.getLong("seconds") } }.sortedBy { it.first }
            }
    }
}

internal fun Random.gaussian(): Double = sqrt(-2 * ln(nextDouble().coerceAtLeast(Double.MIN_VALUE))) * cos(2 * PI * nextDouble())

internal class SyntheticGps(
    gps: JSONObject?,
    private val random: Random,
) {
    private val interval = gps?.optLong("interval_s", 1L)?.coerceAtLeast(1L) ?: 1L
    private val noise = gps?.optDouble("noise_m", DEFAULT_NOISE) ?: DEFAULT_NOISE
    private val accuracy = gps?.optDouble("accuracy_m", DEFAULT_ACCURACY) ?: DEFAULT_ACCURACY
    private val dropouts = windows(gps?.optJSONArray("dropouts"))
    private var east = 0.0
    private var north = 0.0
    private var skipped = false

    fun points(
        samples: List<SyntheticSample>,
        route: SyntheticRoute,
        startMs: Long,
    ): List<RunPoint> =
        samples.mapNotNull { sample ->
            east = wander(east)
            north = wander(north)
            if (sample.recordSeconds % interval != 0L || dropouts.any { sample.recordSeconds in it } || late()) return@mapNotNull null
            val (lat, lon) = route.coordinates(sample.distance, east, north)
            RunPoint(
                lat,
                lon,
                startMs + sample.clockSeconds * MILLIS,
                (accuracy * (1 + (random.nextDouble() - HALF) * ACCURACY_SPREAD)).toFloat(),
                route.altitude(sample.distance),
                sample.segment,
                terrainModel = route.terrainModel,
            )
        }

    private fun late(): Boolean {
        skipped = interval == 1L && !skipped && random.nextDouble() < LATE_FIX_CHANCE
        return skipped
    }

    private fun windows(list: JSONArray?): List<LongRange> {
        val array = list ?: return emptyList()
        val events = (0 until array.length()).map { array.getJSONObject(it) }
        return events.map { it.getLong("at_s") until it.getLong("at_s") + it.getLong("seconds") }
    }

    private fun wander(value: Double) = value * GPS_MEMORY + random.gaussian() * noise * sqrt(1 - GPS_MEMORY * GPS_MEMORY)

    private companion object {
        const val DEFAULT_NOISE = 3.0
        const val DEFAULT_ACCURACY = 5.0
        const val GPS_MEMORY = 0.995
        const val LATE_FIX_CHANCE = 0.2
        const val HALF = 0.5
        const val ACCURACY_SPREAD = 0.4
        const val MILLIS = 1000L
    }
}

object SyntheticRuns {
    private const val HISTORY_SEED_STRIDE = 31
    private const val METERS_PER_KM = 1000.0

    fun catalog(): List<JSONObject> {
        val text = requireNotNull(SyntheticRuns::class.java.classLoader?.getResource("coach-eval/scenarios.json")).readText()
        val scenarios = JSONObject(text).getJSONArray("scenarios")
        return (0 until scenarios.length()).map(scenarios::getJSONObject)
    }

    fun session(spec: JSONObject): JSONObject =
        JSONObject()
            .put("id", spec.getString("id"))
            .put("language", spec.getString("language"))
            .put("goal", spec.getJSONObject("goal"))
            .put("aspiration", spec.optString("aspiration"))
            .put("tags", spec.getJSONArray("tags"))
            .put("summary", spec.optString("summary"))
            .put("scenario", spec)

    fun run(spec: JSONObject): RunSession {
        val plan = SyntheticBlock.expand(spec.getJSONArray("plan"))
        return build(spec.getString("id"), spec, Instant.parse(spec.getString("start")), plan, spec.getLong("seed"))
    }

    fun history(spec: JSONObject): List<RunSession> {
        val start = Instant.parse(spec.getString("start"))
        val earlier = spec.optJSONArray("history") ?: JSONArray()
        return (0 until earlier.length()).map { index ->
            val run = earlier.getJSONObject(index)
            val plan = listOf(SyntheticBlock(null, run.getDouble("km") * METERS_PER_KM, run.getDouble("pace"), null))
            val begins = start.minus(run.getLong("days_before"), ChronoUnit.DAYS)
            build("${spec.getString("id")}-h$index", spec, begins, plan, spec.getLong("seed") * HISTORY_SEED_STRIDE + index)
        }
    }

    private fun build(
        id: String,
        spec: JSONObject,
        start: Instant,
        plan: List<SyntheticBlock>,
        seed: Long,
    ): RunSession {
        val random = Random(seed)
        val route = SyntheticRoute(spec.getJSONObject("place"))
        val isHistory = id != spec.getString("id")
        val stops = spec.optJSONArray("stops").takeUnless { isHistory }
        val pauses = spec.optJSONArray("pauses").takeUnless { isHistory }
        val samples = SyntheticMotion(route, random, stops, pauses).run(plan)
        val points = SyntheticGps(spec.optJSONObject("gps").takeUnless { isHistory }, random).points(samples, route, start.toEpochMilli())
        val active = points.zipWithNext().filter { (a, b) -> a.segment == b.segment }.sumOf { (a, b) -> b.timestampMs - a.timestampMs }
        val ended = points.last().timestampMs
        return RunSession(id, points.first().timestampMs, ended, active, points, elevationSource = TerrainSource.TERRAIN_MAP)
    }
}
