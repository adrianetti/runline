package com.runline.app.core

enum class RunEventKind { STOP, WALK, PAUSE, GPS_GAP, FAST }

data class RunEvent(
    val kind: RunEventKind,
    val startMs: Long,
    val endMs: Long,
    val meters: Double = 0.0,
    val ongoing: Boolean = false,
    val startMeters: Double = 0.0,
    val referencePaceSecondsPerKm: Double? = null,
    val paceSinceSecondsPerKm: Double? = null,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
    val paceSecondsPerKm: Double? get() = RunMetrics.paceSecondsPerKm(meters, durationMs)
}

data class RunEventReport(
    val events: List<RunEvent>,
    val endMs: Long?,
    val usualPaceSecondsPerKm: Double? = null,
    val stillSinceMs: Long? = null,
)

object RunEvents {
    const val MIN_STOP_MS = 20_000L
    const val MIN_PAUSE_MS = 30_000L
    const val MIN_GPS_GAP_MS = 30_000L
    private const val CONTINUOUS_STEP_MS = 15_000L
    private const val STILL_NOW_MS = 10_000L
    private const val STILL_NOW_RADIUS_METERS = 8.0
    private const val MIN_SINCE_MS = 30_000L

    fun detect(run: RunSession): RunEventReport {
        val segments = continuousSegments(run.points)
        val end = segments.lastOrNull()?.lastOrNull()?.timestampMs ?: return RunEventReport(emptyList(), null)
        val track = RunTrack(segments)
        val stretches = segments.flatMap(::stretches)
        val stops = stretches.flatMap { Standstills.find(it, end) }
        val windows = MotionWindows.of(stretches, stops)
        val blocks = if (run.activityType == ActivityType.RUN) PaceBlocks(windows, end) else null
        val found = pauses(segments) + gaps(segments) + stops + blocks?.walks().orEmpty() + blocks?.fasts().orEmpty()
        val events =
            found.sortedBy { it.startMs }.map { event ->
                event.copy(startMeters = track.metersAt(event.startMs), paceSinceSecondsPerKm = paceSince(event, windows))
            }
        return RunEventReport(events, end, blocks?.usualPaceSecondsPerKm(), stillSince(stretches.last()))
    }

    private fun stretches(segment: List<RunPoint>): List<List<RunPoint>> {
        val result = mutableListOf<List<RunPoint>>()
        var current = mutableListOf<RunPoint>()
        segment.forEach { point ->
            if (current.isNotEmpty() && point.timestampMs - current.last().timestampMs > CONTINUOUS_STEP_MS) {
                result += current
                current = mutableListOf()
            }
            current += point
        }
        if (current.isNotEmpty()) result += current
        return result
    }

    private fun pauses(segments: List<List<RunPoint>>): List<RunEvent> =
        segments
            .zipWithNext()
            .filter { (a, b) -> a.last().segment != b.first().segment && b.first().timestampMs - a.last().timestampMs >= MIN_PAUSE_MS }
            .map { (a, b) -> RunEvent(RunEventKind.PAUSE, a.last().timestampMs, b.first().timestampMs) }

    private fun gaps(segments: List<List<RunPoint>>): List<RunEvent> =
        segments.flatMap { segment ->
            segment
                .zipWithNext()
                .filter { (a, b) -> b.timestampMs - a.timestampMs >= MIN_GPS_GAP_MS }
                .map { (a, b) -> RunEvent(RunEventKind.GPS_GAP, a.timestampMs, b.timestampMs, RunMetrics.distanceMeters(listOf(a, b))) }
        }

    private fun stillSince(stretch: List<RunPoint>): Long? {
        val last = stretch.last()
        val still = stretch.takeLastWhile { RunMetrics.distanceMeters(listOf(it, last)) <= STILL_NOW_RADIUS_METERS }
        val since = still.first().timestampMs
        return since.takeIf { last.timestampMs - it >= STILL_NOW_MS }
    }

    private fun paceSince(
        event: RunEvent,
        windows: List<MotionWindow>,
    ): Double? {
        if (event.ongoing) return null
        val after = windows.filter { it.startMs >= event.endMs }
        val duration = after.sumOf { it.durationMs }
        return RunMetrics.paceSecondsPerKm(after.sumOf { it.meters }, duration).takeIf { duration >= MIN_SINCE_MS }
    }
}

internal class RunTrack(
    segments: List<List<RunPoint>>,
) {
    private val times: List<Long>
    private val meters: List<Double>

    init {
        val t = mutableListOf<Long>()
        val m = mutableListOf<Double>()
        var total = 0.0
        segments.forEach { segment ->
            segment.forEachIndexed { index, point ->
                if (index > 0) total += RunMetrics.distanceMeters(listOf(segment[index - 1], point))
                t += point.timestampMs
                m += total
            }
        }
        times = t
        meters = m
    }

    fun metersAt(timeMs: Long): Double {
        val index = times.indexOfLast { it <= timeMs }
        return if (index < 0) 0.0 else meters[index]
    }
}

internal object Standstills {
    private const val MIN_RADIUS_METERS = 8.0
    private const val MAX_RADIUS_METERS = 12.0

    fun find(
        stretch: List<RunPoint>,
        endMs: Long,
    ): List<RunEvent> {
        val found = mutableListOf<RunEvent>()
        var start = 0
        while (start < stretch.size) {
            val last = reach(stretch, start)
            val from = stretch[start].timestampMs
            val to = stretch[last].timestampMs
            if (to - from >= RunEvents.MIN_STOP_MS) {
                found += RunEvent(RunEventKind.STOP, from, to, ongoing = to == endMs)
                start = last + 1
            } else {
                start++
            }
        }
        return found
    }

    private fun reach(
        stretch: List<RunPoint>,
        start: Int,
    ): Int {
        val anchor = stretch[start]
        val radius = anchor.accuracyMeters.toDouble().coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
        var last = start
        while (last + 1 < stretch.size && RunMetrics.distanceMeters(listOf(anchor, stretch[last + 1])) <= radius) last++
        return last
    }
}

internal data class MotionWindow(
    val startMs: Long,
    val endMs: Long,
    val meters: Double,
    val span: Int,
) {
    val durationMs: Long get() = endMs - startMs
    val speed: Double get() = meters * MILLIS_PER_SECOND / durationMs
    val pace: Double get() = durationMs / meters.coerceAtLeast(MIN_METERS)

    private companion object {
        const val MILLIS_PER_SECOND = 1_000.0
        const val MIN_METERS = 0.001
    }
}

internal object MotionWindows {
    private const val WINDOW_MS = 10_000L
    private const val MIN_WINDOW_MS = 6_000L
    private const val STRIDE_MS = 5_000L

    fun of(
        stretches: List<List<RunPoint>>,
        stops: List<RunEvent>,
    ): List<MotionWindow> =
        stretches
            .flatMap { stretch -> moving(stretch, stops) }
            .flatMapIndexed { span, points -> cut(points, span) }

    private fun moving(
        stretch: List<RunPoint>,
        stops: List<RunEvent>,
    ): List<List<RunPoint>> {
        val parts = mutableListOf<List<RunPoint>>()
        var current = mutableListOf<RunPoint>()
        stretch.forEach { point ->
            val stop = stops.firstOrNull { point.timestampMs in it.startMs..it.endMs }
            when (point.timestampMs) {
                stop?.startMs -> {
                    current += point
                    parts += current
                    current = mutableListOf()
                }

                stop?.endMs -> {
                    current = mutableListOf(point)
                }

                else -> {
                    if (stop == null) current += point
                }
            }
        }
        parts += current
        return parts.filter { it.size > 1 }
    }

    private fun cut(
        points: List<RunPoint>,
        span: Int,
    ): List<MotionWindow> {
        val windows = mutableListOf<MotionWindow>()
        var start = 0
        for (index in 1 until points.size) {
            if (points[index].timestampMs - points[start].timestampMs >= WINDOW_MS) {
                windows += window(points.subList(start, index + 1), span)
                start = index
            }
        }
        val tail = points.subList(start, points.size)
        if (tail.last().timestampMs - tail.first().timestampMs >= MIN_WINDOW_MS) windows += window(tail, span)
        return windows
    }

    private fun window(
        points: List<RunPoint>,
        span: Int,
    ): MotionWindow {
        var anchor = points.first()
        var meters = 0.0
        points.drop(1).forEach { point ->
            if (point.timestampMs - anchor.timestampMs >= STRIDE_MS || point === points.last()) {
                meters += RunMetrics.distanceMeters(listOf(anchor, point))
                anchor = point
            }
        }
        return MotionWindow(points.first().timestampMs, points.last().timestampMs, meters, span)
    }
}

internal class PaceBlocks(
    private val windows: List<MotionWindow>,
    private val endMs: Long,
) {
    private val walking: BooleanArray
    private val fast = BooleanArray(windows.size)

    init {
        val speeds = windows.map { it.speed }.sorted()
        val reference = speeds.getOrNull(((speeds.size - 1) * RUN_REFERENCE_QUANTILE).toInt())
        val ceiling = reference?.let { minOf(WALK_MAX_SPEED, it * WALK_SHARE) } ?: 0.0
        walking = BooleanArray(windows.size) { windows[it].speed < ceiling }
        var index = 0
        while (index < windows.size) index = block(index)
    }

    fun walks(): List<RunEvent> =
        runs(walking)
            .filter { (a, b) -> windows[b].endMs - windows[a].startMs >= MIN_WALK_MS }
            .map { event(RunEventKind.WALK, it, null) }

    fun fasts(): List<RunEvent> = runs(fast).map { event(RunEventKind.FAST, it, reference(it.first)) }

    fun usualPaceSecondsPerKm(): Double? = median(windows.indices.filter { !walking[it] && !fast[it] }.map { windows[it].pace })

    private fun block(start: Int): Int {
        val reference = reference(start)
        if (reference == null || !quick(start, reference)) return start + 1
        val last = extent(start, reference)
        val chosen = windows.subList(start, last + 1)
        val duration = chosen.sumOf { it.durationMs }
        val pace = duration / chosen.sumOf { it.meters }.coerceAtLeast(MIN_METERS)
        val clear = pace <= reference * (1 - FAST_SHARE) && pace <= reference - FAST_MIN_GAIN_SECONDS
        if (clear && windows[last].endMs - windows[start].startMs >= MIN_FAST_MS) fast.fill(true, start, last + 1)
        return last + 1
    }

    private fun quick(
        index: Int,
        reference: Double,
    ): Boolean = !walking[index] && windows[index].pace <= reference * (1 - FAST_OPEN_SHARE)

    private fun extent(
        start: Int,
        reference: Double,
    ): Int {
        var last = start
        var next = start + 1
        while (next < windows.size && next - last <= MAX_MISSES + 1 && windows[next].span == windows[start].span) {
            if (quick(next, reference)) last = next
            next++
        }
        return last
    }

    private fun reference(index: Int): Double? {
        val earlier = (0 until index).filter { !walking[it] && !fast[it] }
        val from = windows[index].startMs - REFERENCE_MS
        val recent = earlier.filter { windows[it].endMs > from }
        if (recent.sumOf { windows[it].durationMs } < MIN_REFERENCE_MS) return null
        return listOfNotNull(median(recent.map { windows[it].pace }), median(earlier.map { windows[it].pace })).min()
    }

    private fun runs(flags: BooleanArray): List<Pair<Int, Int>> {
        val found = mutableListOf<Pair<Int, Int>>()
        var start = -1
        flags.indices.forEach { index ->
            val joins = start >= 0 && flags[index] && windows[index].span == windows[index - 1].span
            if (flags[index] && !joins) {
                if (start >= 0) found += start to index - 1
                start = index
            } else if (!flags[index] && start >= 0) {
                found += start to index - 1
                start = -1
            }
        }
        if (start >= 0) found += start to flags.lastIndex
        return found
    }

    private fun event(
        kind: RunEventKind,
        range: Pair<Int, Int>,
        reference: Double?,
    ): RunEvent {
        val (first, last) = range
        val chosen = windows.subList(first, last + 1)
        val end = windows[last].endMs
        val ongoing = last == windows.lastIndex && endMs - end <= ONGOING_MS
        return RunEvent(kind, windows[first].startMs, end, chosen.sumOf { it.meters }, ongoing, referencePaceSecondsPerKm = reference)
    }

    private fun median(values: List<Double>): Double? {
        val sorted = values.sorted()
        return if (sorted.isEmpty()) null else (sorted[(sorted.size - 1) / 2] + sorted[sorted.size / 2]) / 2
    }

    private companion object {
        const val RUN_REFERENCE_QUANTILE = 0.7
        const val WALK_MAX_SPEED = 2.0
        const val WALK_SHARE = 0.75
        const val MIN_WALK_MS = 45_000L
        const val FAST_OPEN_SHARE = 0.06
        const val FAST_SHARE = 0.12
        const val FAST_MIN_GAIN_SECONDS = 25.0
        const val MIN_FAST_MS = 60_000L
        const val MAX_MISSES = 1
        const val REFERENCE_MS = 300_000L
        const val MIN_REFERENCE_MS = 60_000L
        const val ONGOING_MS = 15_000L
        const val MIN_METERS = 0.001
    }
}
