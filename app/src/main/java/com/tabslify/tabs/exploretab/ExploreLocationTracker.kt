package com.tabslify.tabs.exploretab

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import com.tabslify.core.objects.Config
import com.tabslify.quiethoursnotificationhelper.getHomeWifiStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

data class ExploreTrackerInfo(
    val homeLat: Double = 0.0,
    val homeLng: Double = 0.0,
    val lastLat: Double? = null,
    val lastLng: Double? = null,
    val lastUpdate: String? = null,
    val distanceToHomeMeters: Float? = null,
    val geofenceRegistered: Boolean = false,
    val geofenceError: String? = null,
    val lastWifiHome: Boolean? = null,
    val isNight: Boolean = false,
    val isEnabled: Boolean = false,
)

class ExploreGeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> ExploreLocationTracker.stop(context)
            Geofence.GEOFENCE_TRANSITION_EXIT -> ExploreLocationTracker.start(context)
        }
    }
}

object ExploreLocationTracker {

    private val _trackerStatus = MutableStateFlow("Inaktiv")
    val trackerStatus: StateFlow<String> = _trackerStatus.asStateFlow()

    private val _trackerInfo = MutableStateFlow(ExploreTrackerInfo())
    val trackerInfo: StateFlow<ExploreTrackerInfo> = _trackerInfo.asStateFlow()

    private const val GEOFENCE_ID = "HOME"
    private const val GEOFENCE_RADIUS = 100f
    const val HOME_WIFI_SSID = "FRITZ!Box 5590 XO"
    private const val NIGHT_START_HOUR = 0
    private const val NIGHT_END_HOUR = 5

    private var locationCallback: LocationCallback? = null
    private var lastLocation: Location? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var isEnabled = false

    private fun isNightTime(): Boolean {
        val hour = Instant.now().atZone(java.time.ZoneId.systemDefault()).hour
        return hour in NIGHT_START_HOUR until NIGHT_END_HOUR
    }

    private fun distanceToHome(lat: Double, lng: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat, lng, Config.LAT, Config.LON, results)
        return results[0]
    }

    private fun getClient(context: Context) =
        LocationServices.getFusedLocationProviderClient(context.applicationContext)

    private fun geofencePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ExploreGeofenceReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    private fun registerGeofence(context: Context) {
        val homeLat = Config.LAT
        val homeLng = Config.LON

        _trackerInfo.value = _trackerInfo.value.copy(
            homeLat = homeLat,
            homeLng = homeLng,
        )

        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(homeLat, homeLng, GEOFENCE_RADIUS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                        Geofence.GEOFENCE_TRANSITION_EXIT
            )
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(
                GeofencingRequest.INITIAL_TRIGGER_ENTER or
                    GeofencingRequest.INITIAL_TRIGGER_EXIT
            )
            .addGeofence(geofence)
            .build()

        LocationServices.getGeofencingClient(context)
            .addGeofences(request, geofencePendingIntent(context))
            .addOnSuccessListener {
                _trackerInfo.value = _trackerInfo.value.copy(
                    geofenceRegistered = true,
                    geofenceError = null,
                )
            }
            .addOnFailureListener { e ->
                _trackerInfo.value = _trackerInfo.value.copy(
                    geofenceRegistered = false,
                    geofenceError = e.message,
                )
            }
    }

    fun start(context: Context) {
        val appCtx = context.applicationContext

        if (isNightTime()) {
            _trackerStatus.value = "Pausiert (Nacht)"
            _trackerInfo.value = _trackerInfo.value.copy(isNight = true, isEnabled = false)
            ExploreNightRestartWorker.schedule(appCtx)
            return
        }
        ExploreNightRestartWorker.cancel(appCtx)
        if (isEnabled) {
            return
        }
        isEnabled = true
        _trackerStatus.value = "Startet..."
        _trackerInfo.value = _trackerInfo.value.copy(isNight = false, isEnabled = true)

        try {
            registerGeofence(appCtx)
        } catch (_: Exception) {
        }

        _trackerStatus.value = "Warte auf Geofence"
        _trackerInfo.value = _trackerInfo.value.copy(
            isNight = false,
            isEnabled = true,
            lastWifiHome = null,
        )
        evaluateCurrentLocation(appCtx)

        getHomeWifiStatus(appCtx, HOME_WIFI_SSID) { isHomeWifi ->
            _trackerInfo.value = _trackerInfo.value.copy(lastWifiHome = isHomeWifi)
            if (isHomeWifi) {
                _trackerStatus.value = "Gestoppt (Zuhause)"
                stop(appCtx)
                return@getHomeWifiStatus
            }
            if (isEnabled && !isNightTime()) {
                startLocationUpdates(appCtx)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun evaluateCurrentLocation(context: Context) {
        if (Config.LAT == 0.0 && Config.LON == 0.0) {
            _trackerStatus.value = "Warte auf Geofence"
            return
        }

        scope.launch {
            try {
                val request = CurrentLocationRequest.Builder()
                    .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                    .setMaxUpdateAgeMillis(30_000L)
                    .setDurationMillis(5_000L)
                    .build()

                val location = Tasks.await(getClient(context).getCurrentLocation(request, null))
                if (location == null) {
                    _trackerStatus.value = "Warte auf Geofence"
                    return@launch
                }

                val distanceHome = distanceToHome(location.latitude, location.longitude)
                _trackerInfo.value = _trackerInfo.value.copy(
                    lastLat = location.latitude,
                    lastLng = location.longitude,
                    lastUpdate = Instant.now().toString(),
                    distanceToHomeMeters = distanceHome,
                )

                _trackerStatus.value = if (distanceHome <= GEOFENCE_RADIUS) "Zuhause" else "Außerhalb"
            } catch (_: Exception) {
                _trackerStatus.value = "Warte auf Geofence"
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(context: Context) {
        if (locationCallback != null) {
            return
        }

        val repo = ExploreRepository(context)
        val client = getClient(context)

        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 30_000L)
            .setMinUpdateIntervalMillis(15_000L)
            .setMinUpdateDistanceMeters(25f)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!isEnabled || isNightTime()) {
                    stop(context)
                    return
                }
                val loc = result.lastLocation ?: return

                val distanceHome = distanceToHome(loc.latitude, loc.longitude)
                _trackerInfo.value = _trackerInfo.value.copy(
                    lastLat = loc.latitude,
                    lastLng = loc.longitude,
                    lastUpdate = Instant.now().toString(),
                    distanceToHomeMeters = distanceHome,
                )

                val distance = lastLocation?.distanceTo(loc) ?: Float.MAX_VALUE
                if (distance < 25f) {
                    return
                }

                lastLocation = loc
                scope.launch {
                    try {
                        repo.recordLocation(loc.latitude, loc.longitude)
                    } catch (_: Exception) {
                    }
                }
            }
        }

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnSuccessListener {}
                .addOnFailureListener {}
            locationCallback = callback
            _trackerStatus.value = "Läuft aktiv"
            ExploreWorker.schedule(context)
        } catch (e: Exception) {
            _trackerStatus.value = "Fehler: ${e.message}"
        }
    }

    fun stop(context: Context) {
        val appCtx = context.applicationContext
        val wasEnabled = isEnabled
        isEnabled = false
        _trackerInfo.value = _trackerInfo.value.copy(isEnabled = false)

        if (locationCallback != null) {
            getClient(appCtx).removeLocationUpdates(locationCallback!!)
                .addOnSuccessListener {}
                .addOnFailureListener {}
            locationCallback = null
            lastLocation = null
            ExploreWorker.cancel(appCtx)
        }

        if (isNightTime()) {
            _trackerStatus.value = "Pausiert (Nacht)"
            _trackerInfo.value = _trackerInfo.value.copy(isNight = true)
            ExploreNightRestartWorker.schedule(appCtx)
        } else if (_trackerStatus.value != "Gestoppt (Zuhause)") {
            _trackerStatus.value = "Inaktiv"
        }
    }
}
