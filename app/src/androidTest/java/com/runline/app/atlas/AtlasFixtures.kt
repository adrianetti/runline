package com.runline.app.atlas

import android.content.Context
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import com.runline.app.ai.AiReviewStore
import com.runline.app.ai.ReviewCard
import com.runline.app.ai.RunAiReview
import com.runline.app.core.ActivityDetails
import com.runline.app.core.RoutineType
import com.runline.app.core.RunPoint
import com.runline.app.core.RunSession
import com.runline.app.data.RunStore
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal class SyntheticLap(
    private val startMs: Long,
    private val speed: Double = EASY_SPEED,
) {
    fun point(second: Int): RunPoint {
        val angle = second * speed / MEAN_RADIUS_METERS
        val north = NORTH_RADIUS_METERS * cos(angle)
        val east = EAST_RADIUS_METERS * sin(angle)
        return RunPoint(
            latitude = CENTER_LATITUDE + north / METERS_PER_DEGREE,
            longitude = CENTER_LONGITUDE + east / (METERS_PER_DEGREE * cos(CENTER_LATITUDE * PI / HALF_TURN)),
            timestampMs = startMs + second * SECOND_MS,
            accuracyMeters = ACCURACY_METERS,
            altitudeMeters = BASE_ALTITUDE + CLIMB_METERS * sin(angle),
            verticalAccuracyMeters = VERTICAL_ACCURACY_METERS,
        )
    }

    fun session(
        id: String,
        seconds: Int,
        step: Int = 1,
    ) = RunSession(id, startMs, startMs + seconds * SECOND_MS, seconds * SECOND_MS, (0..seconds step step).map(::point))

    companion object {
        const val SECOND_MS = 1_000L
        const val EASY_SPEED = 3.03
        const val STEADY_SPEED = 3.33
        private const val CENTER_LATITUDE = 40.4189
        private const val CENTER_LONGITUDE = -3.7332
        private const val NORTH_RADIUS_METERS = 170.0
        private const val EAST_RADIUS_METERS = 215.0
        private const val MEAN_RADIUS_METERS = 193.0
        private const val METERS_PER_DEGREE = 111_195.0
        private const val HALF_TURN = 180.0
        private const val ACCURACY_METERS = 4f
        private const val BASE_ALTITUDE = 652.0
        private const val CLIMB_METERS = 7.0
        private const val VERTICAL_ACCURACY_METERS = 3f
    }
}

internal class AtlasFixtures(
    private val context: Context,
    private val nowMs: Long,
) {
    private val store = RunStore(context)
    private val locations = context.getSystemService(LocationManager::class.java)
    private var live: SyntheticLap? = null
    private var liveSecond = 0

    fun installHistory() {
        removeHistory()
        val long = ActivityDetails("Sunday long run", routine = RoutineType.LONG)
        val tempo = ActivityDetails("Tempo laps", effort = TEMPO_EFFORT)
        save("atlas-long", DAY_MS * LONG_DAYS_AGO, LONG_SECONDS, SyntheticLap.EASY_SPEED, long)
        save("atlas-tempo", DAY_MS * TEMPO_DAYS_AGO, TEMPO_SECONDS, SyntheticLap.STEADY_SPEED, tempo)
        save("atlas-easy", DAY_MS, EASY_SECONDS, SyntheticLap.EASY_SPEED, ActivityDetails("Easy loop", routine = RoutineType.EASY))
        AiReviewStore(context).save(
            "atlas-easy",
            RunAiReview(
                "",
                "atlas",
                nowMs,
                ReviewCard(
                    "Steady from the first lap",
                    "Every kilometre landed within five seconds.",
                    "Hold form on the climb.",
                    "40 easy minutes.",
                ),
            ),
        )
    }

    fun removeHistory() {
        RUN_IDS.forEach { id ->
            runCatching { store.delete(id) }
            runCatching { AiReviewStore(context).delete(id) }
        }
    }

    fun beginRecording(): Boolean {
        if (store.loadDraftRecord() != null) return false
        val lap = SyntheticLap(nowMs - LIVE_SECONDS * SyntheticLap.SECOND_MS)
        store.beginDraft(lap.session(LIVE_ID, LIVE_SECONDS).copy(points = emptyList()))
        (0..LIVE_SECONDS step DRAFT_STEP).forEach { store.appendPoint(lap.point(it)) }
        live = lap
        liveSecond = LIVE_SECONDS
        locations.addTestProvider(
            LocationManager.GPS_PROVIDER,
            false,
            false,
            false,
            false,
            true,
            true,
            true,
            powerUsage(),
            accuracy(),
        )
        locations.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
        return true
    }

    fun fix() {
        val lap = live ?: return
        liveSecond++
        val point = lap.point(liveSecond)
        locations.setTestProviderLocation(
            LocationManager.GPS_PROVIDER,
            Location(LocationManager.GPS_PROVIDER).apply {
                latitude = point.latitude
                longitude = point.longitude
                altitude = point.altitudeMeters ?: 0.0
                accuracy = point.accuracyMeters
                verticalAccuracyMeters = point.verticalAccuracyMeters ?: 0f
                speed = SyntheticLap.EASY_SPEED.toFloat()
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            },
        )
    }

    fun endRecording() {
        live = null
        runCatching { locations.removeTestProvider(LocationManager.GPS_PROVIDER) }
    }

    private fun powerUsage() = if (Build.VERSION.SDK_INT >= PROVIDER_API) ProviderProperties.POWER_USAGE_LOW else Criteria.POWER_LOW

    private fun accuracy() = if (Build.VERSION.SDK_INT >= PROVIDER_API) ProviderProperties.ACCURACY_FINE else Criteria.ACCURACY_FINE

    private fun save(
        id: String,
        agoMs: Long,
        seconds: Int,
        speed: Double,
        details: ActivityDetails,
    ) {
        store.save(SyntheticLap(nowMs - agoMs, speed).session(id, seconds))
        store.saveDetails(id, details)
    }

    companion object {
        const val LIVE_ID = "atlas-live"
        val RUN_IDS = listOf("atlas-long", "atlas-tempo", "atlas-easy")
        private const val DAY_MS = 86_400_000L
        private const val LONG_DAYS_AGO = 9
        private const val TEMPO_DAYS_AGO = 4
        private const val LONG_SECONDS = 3_960
        private const val TEMPO_SECONDS = 1_800
        private const val EASY_SECONDS = 1_716
        private const val TEMPO_EFFORT = 7
        private const val LIVE_SECONDS = 1_548
        private const val DRAFT_STEP = 3
        private const val PROVIDER_API = 31
    }
}
