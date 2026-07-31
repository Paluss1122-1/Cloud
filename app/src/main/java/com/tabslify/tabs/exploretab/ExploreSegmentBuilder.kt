package com.tabslify.tabs.exploretab

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import com.tabslify.core.objects.Config
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.time.LocalDate
import java.util.Locale

object ExploreSegmentBuilder {

    private const val MIN_SEGMENT_DURATION_MS = 90_000L
    private const val MIN_SEGMENT_DISTANCE_M = 50f
    private const val STAY_MIN_DURATION_MS = 8 * 60_000L
    private const val HOME_RADIUS_M = 100f

    private class Run(val mode: String, val points: MutableList<RawPoint>)

    fun tuningSnapshot(): Map<String, Any> = mapOf(
        "minSegmentDurationMs" to MIN_SEGMENT_DURATION_MS,
        "minSegmentDistanceMeters" to MIN_SEGMENT_DISTANCE_M,
        "stayMinDurationMs" to STAY_MIN_DURATION_MS,
        "homeRadiusMeters" to HOME_RADIUS_M
    )

    suspend fun rebuildDay(context: Context, day: LocalDate, force: Boolean = false) {
        val repo = ExploreRepository(context)
        val (dayStart, dayEnd) = dayBounds(day)

        if (!force && day != LocalDate.now() && repo.hasSegmentsForDay(dayStart, dayEnd)) {
            return
        }

        val points = repo.pointsForDay(dayStart, dayEnd)
        if (points.isEmpty()) {
            repo.replaceDayResults(dayStart, dayEnd, emptyList(), emptyList())
            return
        }

        val previousStays = repo.existingStaysForDay(dayStart, dayEnd)
        val (segments, unnamedStays) = build(points)
        val stays = unnamedStays.map { stay ->
            val matched = previousStays.firstOrNull {
                it.startTime == stay.startTime && it.endTime == stay.endTime && it.placeName != null
            }
            val placeName = matched?.placeName
                ?: if (stay.isHome) "Zuhause" else reverseGeocode(context, stay.centerLat, stay.centerLon)
            stay.copy(placeName = placeName)
        }

        repo.replaceDayResults(dayStart, dayEnd, segments, stays)
    }

    fun build(points: List<RawPoint>): Pair<List<Segment>, List<Stay>> {
        if (points.isEmpty()) return emptyList<Segment>() to emptyList()

        val runs = groupRuns(points)
        mergeMicroRuns(runs)
        mergeShortStillRuns(runs)

        val segments = mutableListOf<Segment>()
        val stays = mutableListOf<Stay>()

        for (run in runs) {
            val first = run.points.first()
            val last = run.points.last()
            val duration = last.timestamp - first.timestamp

            if (run.mode == "STILL" && duration >= STAY_MIN_DURATION_MS) {
                val centerLat = run.points.map { it.lat }.average()
                val centerLon = run.points.map { it.lon }.average()
                stays += Stay(
                    centerLat = centerLat,
                    centerLon = centerLon,
                    startTime = first.timestamp,
                    endTime = last.timestamp,
                    placeName = null,
                    isHome = isNearHome(centerLat, centerLon)
                )
            } else {
                segments += Segment(
                    mode = run.mode,
                    startTime = first.timestamp,
                    endTime = last.timestamp,
                    startLat = first.lat,
                    startLon = first.lon,
                    endLat = last.lat,
                    endLon = last.lon,
                    distanceMeters = runDistance(run),
                    pointCount = run.points.size
                )
            }
        }

        return segments to stays
    }

    private fun groupRuns(points: List<RawPoint>): MutableList<Run> {
        val runs = mutableListOf<Run>()
        for (point in points) {
            val last = runs.lastOrNull()
            if (last != null && last.mode == point.activityType) {
                last.points.add(point)
            } else {
                runs.add(Run(point.activityType, mutableListOf(point)))
            }
        }
        return runs
    }

    private fun runDuration(run: Run): Long = run.points.last().timestamp - run.points.first().timestamp

    private fun runDistance(run: Run): Float {
        var total = 0f
        for (i in 1 until run.points.size) {
            total += distanceBetween(run.points[i - 1], run.points[i])
        }
        return total
    }

    private fun distanceBetween(a: RawPoint, b: RawPoint): Float {
        val results = FloatArray(1)
        Location.distanceBetween(a.lat, a.lon, b.lat, b.lon, results)
        return results[0]
    }

    private fun isNearHome(lat: Double, lon: Double): Boolean {
        val results = FloatArray(1)
        Location.distanceBetween(lat, lon, Config.LAT, Config.LON, results)
        return results[0] <= HOME_RADIUS_M
    }

    private fun mergeAt(runs: MutableList<Run>, index: Int): Boolean {
        val run = runs[index]
        val prev = runs.getOrNull(index - 1)
        val next = runs.getOrNull(index + 1)
        return when {
            prev == null && next == null -> false
            prev == null -> {
                next!!.points.addAll(0, run.points)
                runs.removeAt(index)
                true
            }
            next == null -> {
                prev.points.addAll(run.points)
                runs.removeAt(index)
                true
            }
            runDuration(prev) >= runDuration(next) -> {
                prev.points.addAll(run.points)
                runs.removeAt(index)
                true
            }
            else -> {
                next.points.addAll(0, run.points)
                runs.removeAt(index)
                true
            }
        }
    }

    private fun mergeMicroRuns(runs: MutableList<Run>) {
        var changed = true
        while (changed && runs.size > 1) {
            changed = false
            var i = 0
            while (i < runs.size) {
                val run = runs[i]
                if (runDuration(run) < MIN_SEGMENT_DURATION_MS && runDistance(run) < MIN_SEGMENT_DISTANCE_M) {
                    if (mergeAt(runs, i)) {
                        changed = true
                        continue
                    }
                }
                i++
            }
        }
    }

    private fun mergeShortStillRuns(runs: MutableList<Run>) {
        var changed = true
        while (changed && runs.size > 1) {
            changed = false
            var i = 0
            while (i < runs.size) {
                val run = runs[i]
                if (run.mode == "STILL" && runDuration(run) < STAY_MIN_DURATION_MS) {
                    if (mergeAt(runs, i)) {
                        changed = true
                        continue
                    }
                }
                i++
            }
        }
    }

    private suspend fun reverseGeocode(context: Context, lat: Double, lon: Double): String {
        val fallback = "Unbekannter Ort"
        return try {
            suspendCancellableCoroutine { cont ->
                try {
                    Geocoder(context, Locale.GERMANY).getFromLocation(lat, lon, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) {
                            val address = addresses.firstOrNull()
                            val name = address?.featureName ?: address?.thoroughfare
                            val locality = address?.locality
                            val label = when {
                                name != null && locality != null -> "$name, $locality"
                                name != null -> name
                                locality != null -> locality
                                else -> fallback
                            }
                            if (cont.isActive) cont.resume(label)
                        }

                        override fun onError(errorMessage: String?) {
                            if (cont.isActive) cont.resume(fallback)
                        }
                    })
                } catch (_: Exception) {
                    if (cont.isActive) cont.resume(fallback)
                }
            }
        } catch (_: Exception) {
            fallback
        }
    }
}
