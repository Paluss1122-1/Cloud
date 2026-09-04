package com.tabslify.tabs.fitnesstab.data

import android.content.Context
import android.location.Location
import com.tabslify.tabs.exploretab.ExploreBikeClassifier
import com.tabslify.tabs.exploretab.ExploreRepository
import com.tabslify.tabs.exploretab.ExploreSegmentBuilder
import com.tabslify.tabs.exploretab.RawPoint
import com.tabslify.tabs.exploretab.Segment
import com.tabslify.tabs.exploretab.dayBounds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.ceil

object BikeRideRepository {

    private const val MIN_DURATION_MS = 120_000L
    private const val MIN_DISTANCE_METERS = 150f
    private const val MAX_ROUTE_POINTS = 400
    private const val MOVING_SPEED_MPS = 1.5
    private const val MAX_SAMPLE_GAP_S = 90.0
    private const val MAX_PLAUSIBLE_SPEED_MPS = 16.0
    private const val ELEVATION_THRESHOLD_M = 2.0

    suspend fun ridesForLastDays(
        context: Context,
        days: Int,
        calorieFactor: Float
    ): List<BikeRide> = withContext(Dispatchers.Default) {
        val appContext = context.applicationContext
        val repo = ExploreRepository(appContext)
        val snapshot = BikeModeOverrides.snapshot(appContext)
        val today = LocalDate.now()
        val rides = mutableListOf<BikeRide>()

        for (offset in 0 until days) {
            val day = today.minusDays(offset.toLong())
            val (dayStart, dayEnd) = dayBounds(day)
            val points = repo.pointsForDay(dayStart, dayEnd)
            val segments = if (points.isNotEmpty()) {
                ExploreSegmentBuilder.build(points).first
            } else {
                repo.segmentsBetween(dayStart, dayEnd)
            }
            segments.filter { ExploreBikeClassifier.isBikeMode(it.mode) }.forEach { segment ->
                val segmentPoints = points.filter { it.timestamp in segment.startTime..segment.endTime }
                buildRide(segment, segmentPoints, snapshot, calorieFactor)?.let { rides += it }
            }
        }

        rides.sortedByDescending { it.startTime }
    }

    fun caloriesFor(mode: String, distanceKm: Float, durationMs: Long, calorieFactor: Float): Float =
        ExploreActivityBridge.caloriesFor(
            exerciseId = EXERCISE_ID_CYCLING,
            distanceKm = distanceKm,
            durationMs = durationMs,
            mode = mode,
            calorieFactor = calorieFactor
        )

    fun statsFor(rides: List<BikeRide>): BikeStats = BikeStats(
        rides = rides.size,
        bikeRides = rides.count { !it.isEbike },
        ebikeRides = rides.count { it.isEbike },
        totalKm = rides.sumOf { it.distanceKm.toDouble() }.toFloat(),
        totalMinutes = rides.sumOf { it.durationMinutes },
        totalCalories = rides.sumOf { it.calories.toDouble() }.toFloat(),
        totalElevationM = rides.sumOf { it.elevationGainM.toDouble() }.toFloat(),
        longestKm = rides.maxOfOrNull { it.distanceKm } ?: 0f,
        fastestKmh = rides.maxOfOrNull { it.avgSpeedKmh } ?: 0f
    )

    private fun buildRide(
        segment: Segment,
        points: List<RawPoint>,
        snapshot: BikeModeSnapshot,
        calorieFactor: Float
    ): BikeRide? {
        val durationMs = segment.endTime - segment.startTime
        if (durationMs < MIN_DURATION_MS || segment.distanceMeters < MIN_DISTANCE_METERS) return null

        val classification = if (points.size >= 2) ExploreBikeClassifier.classify(points) else null
        val mode = snapshot.modeFor(segment.startTime, segment.mode)
        val km = segment.distanceMeters / 1000f
        val hours = durationMs / 3_600_000f
        val metrics = routeMetrics(points)

        return BikeRide(
            rideId = BikeModeOverrides.rideId(segment.startTime),
            mode = mode,
            autoMode = segment.mode,
            overridden = snapshot.isOverridden(segment.startTime),
            classifierScore = classification?.score ?: 0.0,
            classifierDecided = classification?.decided == true,
            startTime = segment.startTime,
            endTime = segment.endTime,
            distanceKm = km,
            movingSeconds = metrics.movingSeconds,
            avgSpeedKmh = if (hours > 0f) km / hours else 0f,
            maxSpeedKmh = metrics.maxSpeedKmh,
            elevationGainM = metrics.elevationGainM,
            elevationLossM = metrics.elevationLossM,
            calories = caloriesFor(mode, km, durationMs, calorieFactor),
            route = metrics.route
        )
    }

    private class RouteMetrics(
        val route: List<BikeRoutePoint>,
        val maxSpeedKmh: Float,
        val movingSeconds: Int,
        val elevationGainM: Float,
        val elevationLossM: Float
    )

    private fun routeMetrics(points: List<RawPoint>): RouteMetrics {
        if (points.isEmpty()) return RouteMetrics(emptyList(), 0f, 0, 0f, 0f)

        val distance = FloatArray(1)
        val speeds = ArrayList<Float>(points.size)
        var movingSeconds = 0.0

        for (index in points.indices) {
            val current = points[index]
            val previous = points.getOrNull(index - 1)
            var mps = current.speed?.toDouble() ?: -1.0
            var seconds = 0.0

            if (previous != null) {
                seconds = (current.timestamp - previous.timestamp) / 1000.0
                if (mps < 0.0 || mps > MAX_PLAUSIBLE_SPEED_MPS) {
                    Location.distanceBetween(previous.lat, previous.lon, current.lat, current.lon, distance)
                    mps = if (seconds > 0.0) distance[0] / seconds else 0.0
                }
            }
            if (mps < 0.0 || mps > MAX_PLAUSIBLE_SPEED_MPS) mps = 0.0

            speeds += (mps * 3.6).toFloat()
            if (seconds in 0.0..MAX_SAMPLE_GAP_S && mps >= MOVING_SPEED_MPS) movingSeconds += seconds
        }

        var gain = 0.0
        var loss = 0.0
        var reference: Double? = null
        points.forEach { point ->
            val altitude = point.altitude ?: return@forEach
            val previousAltitude = reference
            if (previousAltitude == null) {
                reference = altitude
                return@forEach
            }
            val diff = altitude - previousAltitude
            if (diff >= ELEVATION_THRESHOLD_M) {
                gain += diff
                reference = altitude
            } else if (diff <= -ELEVATION_THRESHOLD_M) {
                loss += -diff
                reference = altitude
            }
        }

        val route = downsample(
            points.mapIndexed { index, point ->
                BikeRoutePoint(
                    lat = point.lat,
                    lon = point.lon,
                    timestamp = point.timestamp,
                    speedKmh = speeds[index],
                    altitude = point.altitude
                )
            }
        )

        return RouteMetrics(
            route = route,
            maxSpeedKmh = speeds.maxOrNull() ?: 0f,
            movingSeconds = movingSeconds.toInt(),
            elevationGainM = gain.toFloat(),
            elevationLossM = loss.toFloat()
        )
    }

    private fun downsample(route: List<BikeRoutePoint>): List<BikeRoutePoint> {
        if (route.size <= MAX_ROUTE_POINTS) return route
        val step = ceil(route.size.toDouble() / MAX_ROUTE_POINTS).toInt().coerceAtLeast(2)
        val reduced = route.filterIndexed { index, _ -> index % step == 0 }.toMutableList()
        val last = route.last()
        if (reduced.lastOrNull()?.timestamp != last.timestamp) reduced += last
        return reduced
    }
}
