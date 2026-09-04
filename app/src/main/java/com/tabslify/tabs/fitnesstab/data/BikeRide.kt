package com.tabslify.tabs.fitnesstab.data

import android.content.Context
import androidx.core.content.edit
import com.tabslify.tabs.exploretab.ExploreBikeClassifier
import org.json.JSONObject

const val BIKE_MODE_AUTO = "AUTO"

data class BikeRoutePoint(
    val lat: Double,
    val lon: Double,
    val timestamp: Long,
    val speedKmh: Float,
    val altitude: Double?
)

data class BikeRide(
    val rideId: String,
    val mode: String,
    val autoMode: String,
    val overridden: Boolean,
    val classifierScore: Double,
    val classifierDecided: Boolean,
    val startTime: Long,
    val endTime: Long,
    val distanceKm: Float,
    val movingSeconds: Int,
    val avgSpeedKmh: Float,
    val maxSpeedKmh: Float,
    val elevationGainM: Float,
    val elevationLossM: Float,
    val calories: Float,
    val route: List<BikeRoutePoint>
) {
    val durationMs: Long get() = endTime - startTime
    val durationMinutes: Int get() = (durationMs / 60_000L).toInt()
    val isEbike: Boolean get() = mode == ExploreBikeClassifier.MODE_EBIKE
    val movingShare: Float
        get() = if (durationMs <= 0L) 0f else (movingSeconds * 1000f / durationMs).coerceIn(0f, 1f)
}

data class BikeStats(
    val rides: Int = 0,
    val bikeRides: Int = 0,
    val ebikeRides: Int = 0,
    val totalKm: Float = 0f,
    val totalMinutes: Int = 0,
    val totalCalories: Float = 0f,
    val totalElevationM: Float = 0f,
    val longestKm: Float = 0f,
    val fastestKmh: Float = 0f
)

class BikeModeSnapshot(
    private val defaultMode: String,
    private val perRide: Map<String, String>
) {
    fun modeFor(startTime: Long, autoMode: String): String {
        if (!ExploreBikeClassifier.isBikeMode(autoMode)) return autoMode
        perRide[BikeModeOverrides.rideId(startTime)]?.let { return it }
        return if (defaultMode == BIKE_MODE_AUTO) autoMode else defaultMode
    }

    fun isOverridden(startTime: Long): Boolean =
        perRide.containsKey(BikeModeOverrides.rideId(startTime))
}

object BikeModeOverrides {

    private const val PREFS_NAME = "fitness_bike_modes"
    private const val KEY_RIDE_MODES = "ride_modes_json"
    private const val KEY_DEFAULT_MODE = "default_mode"

    fun rideId(startTime: Long): String = "bike_$startTime"

    fun snapshot(context: Context): BikeModeSnapshot =
        BikeModeSnapshot(defaultMode(context), rideModes(context))

    fun defaultMode(context: Context): String =
        prefs(context).getString(KEY_DEFAULT_MODE, BIKE_MODE_AUTO) ?: BIKE_MODE_AUTO

    fun setDefaultMode(context: Context, mode: String) {
        prefs(context).edit { putString(KEY_DEFAULT_MODE, mode) }
    }

    fun setMode(context: Context, rideId: String, mode: String?) {
        val current = rideModes(context).toMutableMap()
        if (mode == null) current.remove(rideId) else current[rideId] = mode
        val json = JSONObject()
        current.forEach { (key, value) -> json.put(key, value) }
        prefs(context).edit { putString(KEY_RIDE_MODES, json.toString()) }
    }

    fun rideModes(context: Context): Map<String, String> {
        val raw = prefs(context).getString(KEY_RIDE_MODES, null) ?: return emptyMap()
        return runCatching {
            val json = JSONObject(raw)
            val result = mutableMapOf<String, String>()
            json.keys().forEach { key -> result[key] = json.getString(key) }
            result.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
