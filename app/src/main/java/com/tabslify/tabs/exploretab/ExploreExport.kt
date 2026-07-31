package com.tabslify.tabs.exploretab

import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import androidx.core.content.FileProvider
import com.tabslify.BuildConfig
import com.tabslify.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object ExploreExport {

    private const val EXPORT_VERSION = 1

    private val pointColumns = listOf("t", "lat", "lon", "acc", "spd", "brg", "act", "conf", "dt", "dist", "vImplied")
    private val eventColumns = listOf("t", "type", "mode", "conf", "detail")

    suspend fun export(context: Context, lastDay: LocalDate, days: Int): File = withContext(Dispatchers.IO) {
        val repo = ExploreRepository(context)
        val zone = ZoneId.systemDefault()
        val firstDay = lastDay.minusDays((days - 1).toLong())

        val root = JSONObject()
        root.put("exportVersion", EXPORT_VERSION)
        root.put("exportedAt", Instant.now().toString())
        root.put("timezone", zone.id)
        root.put("device", "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, SDK ${Build.VERSION.SDK_INT})")
        root.put("appVersion", BuildConfig.VERSION_NAME)
        root.put("rangeFrom", firstDay.toString())
        root.put("rangeTo", lastDay.toString())
        root.put("tracker", toJson(ExploreLocationTracker.tuningSnapshot()))
        root.put("segmentBuilder", toJson(ExploreSegmentBuilder.tuningSnapshot()))
        root.put("pointColumns", JSONArray(pointColumns))
        root.put("eventColumns", JSONArray(eventColumns))

        val daysArray = JSONArray()
        var day = firstDay
        while (!day.isAfter(lastDay)) {
            val (start, end) = dayBounds(day, zone)
            val points = repo.pointsForDay(start, end)
            val events = repo.trackerEventsBetween(start, end)
            val segments = repo.segmentsBetween(start, end)
            val stays = repo.existingStaysForDay(start, end)

            if (points.isNotEmpty() || events.isNotEmpty() || segments.isNotEmpty() || stays.isNotEmpty()) {
                daysArray.put(JSONObject().apply {
                    put("date", day.toString())
                    put("points", pointsToJson(points))
                    put("events", eventsToJson(events))
                    put("segments", segmentsToJson(segments))
                    put("stays", staysToJson(stays))
                })
            }
            day = day.plusDays(1)
        }
        root.put("days", daysArray)

        val name = if (days == 1) "explore_export_$lastDay.json" else "explore_export_${firstDay}_bis_$lastDay.json"
        val file = File(context.cacheDir, name)
        file.writeText(root.toString())
        file
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.teilen))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun pointsToJson(points: List<RawPoint>): JSONArray {
        val array = JSONArray()
        val distance = FloatArray(1)
        points.forEachIndexed { index, point ->
            val previous = points.getOrNull(index - 1)
            val deltaSeconds = if (previous != null) (point.timestamp - previous.timestamp) / 1000.0 else 0.0
            val meters = if (previous != null) {
                Location.distanceBetween(previous.lat, previous.lon, point.lat, point.lon, distance)
                distance[0].toDouble()
            } else 0.0
            val implied = if (deltaSeconds > 0) meters / deltaSeconds else 0.0

            array.put(JSONArray().apply {
                put(point.timestamp)
                put(round(point.lat, 6))
                put(round(point.lon, 6))
                put(round(point.accuracy.toDouble(), 1))
                put(point.speed?.let { round(it.toDouble(), 2) } ?: JSONObject.NULL)
                put(point.bearing?.let { round(it.toDouble(), 1) } ?: JSONObject.NULL)
                put(point.activityType)
                put(point.activityConfidence)
                put(round(deltaSeconds, 1))
                put(round(meters, 1))
                put(round(implied, 2))
            })
        }
        return array
    }

    private fun eventsToJson(events: List<TrackerEvent>): JSONArray {
        val array = JSONArray()
        events.forEach { event ->
            array.put(JSONArray().apply {
                put(event.timestamp)
                put(event.type)
                put(event.mode)
                put(event.confidence)
                put(event.detail ?: JSONObject.NULL)
            })
        }
        return array
    }

    private fun segmentsToJson(segments: List<Segment>): JSONArray {
        val array = JSONArray()
        segments.forEach { segment ->
            val durationSeconds = (segment.endTime - segment.startTime) / 1000.0
            array.put(JSONObject().apply {
                put("mode", segment.mode)
                put("startTime", segment.startTime)
                put("endTime", segment.endTime)
                put("durationSeconds", round(durationSeconds, 0))
                put("distanceMeters", round(segment.distanceMeters.toDouble(), 1))
                put("avgSpeedMps", if (durationSeconds > 0) round(segment.distanceMeters / durationSeconds, 2) else 0.0)
                put("pointCount", segment.pointCount)
                put("startLat", round(segment.startLat, 6))
                put("startLon", round(segment.startLon, 6))
                put("endLat", round(segment.endLat, 6))
                put("endLon", round(segment.endLon, 6))
            })
        }
        return array
    }

    private fun staysToJson(stays: List<Stay>): JSONArray {
        val array = JSONArray()
        stays.forEach { stay ->
            array.put(JSONObject().apply {
                put("startTime", stay.startTime)
                put("endTime", stay.endTime)
                put("durationSeconds", round((stay.endTime - stay.startTime) / 1000.0, 0))
                put("centerLat", round(stay.centerLat, 6))
                put("centerLon", round(stay.centerLon, 6))
                put("placeName", stay.placeName ?: JSONObject.NULL)
                put("isHome", stay.isHome)
            })
        }
        return array
    }

    private fun round(value: Double, digits: Int): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        var factor = 1.0
        repeat(digits) { factor *= 10 }
        return kotlin.math.round(value * factor) / factor
    }

    private fun toJson(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> JSONObject().also { obj ->
            value.forEach { (key, entry) -> obj.put(key.toString(), toJson(entry)) }
        }

        is List<*> -> JSONArray().also { array ->
            value.forEach { entry -> array.put(toJson(entry)) }
        }

        else -> value
    }
}
