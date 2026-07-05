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
import com.tabslify.core.functions.errorInsert
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
        val event = GeofencingEvent.fromIntent(intent) ?: run {
            errorInsert(
                "ExploreTracker",
                "GeofenceReceiver - onReceive: GeofencingEvent.fromIntent() ist null, intent verworfen",
                Instant.now().toString(),
                "ERROR"
            )
            return
        }

        if (event.hasError()) {
            errorInsert(
                "ExploreTracker",
                "GeofenceReceiver - GeofencingEvent hat Fehler: ${event.errorCode}",
                Instant.now().toString(),
                "ERROR"
            )
            return
        }

        val triggeringLoc = event.triggeringLocation
        errorInsert(
            "ExploreTracker",
            "GeofenceReceiver - onReceive: transition=${event.geofenceTransition}, " +
                "triggeringLocation=${triggeringLoc?.latitude},${triggeringLoc?.longitude}, " +
                "geofences=${event.triggeringGeofences?.map { it.requestId }}",
            Instant.now().toString(),
            "LOG"
        )

        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                errorInsert(
                    "ExploreTracker",
                    "GeofenceReceiver - ENTER: Benutzer ist zuhause -> stop()",
                    Instant.now().toString(),
                    "LOG"
                )
                ExploreLocationTracker.stop(context)
            }

            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                errorInsert(
                    "ExploreTracker",
                    "GeofenceReceiver - EXIT: Benutzer verlässt zuhause -> start()",
                    Instant.now().toString(),
                    "LOG"
                )
                ExploreLocationTracker.start(context)
            }

            else -> {
                errorInsert(
                    "ExploreTracker",
                    "GeofenceReceiver - unbekannter transition-Typ: ${event.geofenceTransition}",
                    Instant.now().toString(),
                    "LOG"
                )
            }
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

        errorInsert(
            "ExploreTracker",
            "registerGeofence() - registriere Geofence bei HOME_LAT=$homeLat, HOME_LNG=$homeLng, radius=$GEOFENCE_RADIUS",
            Instant.now().toString(),
            "LOG"
        )

        if (homeLat == 0.0 && homeLng == 0.0) {
            errorInsert(
                "ExploreTracker",
                "registerGeofence() - WARNUNG: Config.LAT/LON sind (0.0, 0.0) -> Heimat-Koordinaten wahrscheinlich noch nicht gesetzt/geladen! Geofence wird trotzdem registriert, wird aber nie sinnvoll auslösen.",
                Instant.now().toString(),
                "ERROR"
            )
        }

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
                errorInsert(
                    "ExploreTracker",
                    "registerGeofence() - addGeofences() erfolgreich (lat=$homeLat, lng=$homeLng)",
                    Instant.now().toString(),
                    "LOG"
                )
            }
            .addOnFailureListener { e ->
                _trackerInfo.value = _trackerInfo.value.copy(
                    geofenceRegistered = false,
                    geofenceError = e.message,
                )
                errorInsert(
                    "ExploreTracker",
                    "registerGeofence() - addGeofences() fehlgeschlagen: ${e.message}",
                    Instant.now().toString(),
                    "ERROR"
                )
            }
    }

    fun start(context: Context) {
        val appCtx = context.applicationContext
        errorInsert(
            "ExploreTracker",
            "start() - aufgerufen (isEnabled=$isEnabled, isNightTime=${isNightTime()}, Config.LAT=${Config.LAT}, Config.LON=${Config.LON})",
            Instant.now().toString(),
            "LOG"
        )

        if (isNightTime()) {
            _trackerStatus.value = "Pausiert (Nacht)"
            _trackerInfo.value = _trackerInfo.value.copy(isNight = true, isEnabled = false)
            errorInsert(
                "ExploreTracker",
                "start() - Night time (0-5 Uhr), wird nicht gestartet",
                Instant.now().toString(),
                "LOG"
            )
            ExploreNightRestartWorker.schedule(appCtx)
            return
        }
        ExploreNightRestartWorker.cancel(appCtx)
        if (isEnabled) {
            errorInsert(
                "ExploreTracker",
                "start() - bereits aktiv, breche ab",
                Instant.now().toString(),
                "LOG"
            )
            return
        }
        isEnabled = true
        _trackerStatus.value = "Startet..."
        _trackerInfo.value = _trackerInfo.value.copy(isNight = false, isEnabled = true)

        errorInsert(
            "ExploreTracker",
            "start() - Geofence wird aktiv überwacht, WLAN-Check als Backup",
            Instant.now().toString(),
            "LOG"
        )
        try {
            registerGeofence(appCtx)
        } catch (e: Exception) {
            errorInsert(
                "ExploreTracker",
                "start() - Geofence Registrierung failed: ${e.message}",
                Instant.now().toString(),
                "ERROR"
            )
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
            errorInsert(
                "ExploreTracker",
                "start() - WiFi-Callback erhalten: isHome=$isHomeWifi",
                Instant.now().toString(),
                "LOG"
            )
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
            errorInsert(
                "ExploreTracker",
                "evaluateCurrentLocation() - Home-Koordinaten noch nicht verfügbar",
                Instant.now().toString(),
                "LOG"
            )
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
                    errorInsert(
                        "ExploreTracker",
                        "evaluateCurrentLocation() - kein aktueller Standort verfügbar",
                        Instant.now().toString(),
                        "LOG"
                    )
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
                errorInsert(
                    "ExploreTracker",
                    "evaluateCurrentLocation() - Status direkt gesetzt: ${_trackerStatus.value} (distanz=${distanceHome}m)",
                    Instant.now().toString(),
                    "LOG"
                )
            } catch (e: Exception) {
                _trackerStatus.value = "Warte auf Geofence"
                errorInsert(
                    "ExploreTracker",
                    "evaluateCurrentLocation() - Fehler: ${e.message}",
                    Instant.now().toString(),
                    "ERROR"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(context: Context) {
        if (locationCallback != null) {
            errorInsert(
                "ExploreTracker",
                "startLocationUpdates() - locationCallback existiert bereits, breche ab",
                Instant.now().toString(),
                "LOG"
            )
            return
        }

        val repo = ExploreRepository(context)
        val client = getClient(context)

        val request = LocationRequest.Builder(Priority.PRIORITY_PASSIVE, 60_000L)
            .setMinUpdateDistanceMeters(50f)
            .setWaitForAccurateLocation(true)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!isEnabled || isNightTime()) {
                    errorInsert(
                        "ExploreTracker",
                        "onLocationResult() - isEnabled=$isEnabled, isNightTime=${isNightTime()} -> stop()",
                        Instant.now().toString(),
                        "LOG"
                    )
                    stop(context)
                    return
                }
                val loc = result.lastLocation ?: run {
                    errorInsert(
                        "ExploreTracker",
                        "onLocationResult() - result.lastLocation ist null",
                        Instant.now().toString(),
                        "LOG"
                    )
                    return
                }

                val distanceHome = distanceToHome(loc.latitude, loc.longitude)
                _trackerInfo.value = _trackerInfo.value.copy(
                    lastLat = loc.latitude,
                    lastLng = loc.longitude,
                    lastUpdate = Instant.now().toString(),
                    distanceToHomeMeters = distanceHome,
                )

                val distance = lastLocation?.distanceTo(loc) ?: Float.MAX_VALUE
                if (distance < 50f) {
                    errorInsert(
                        "ExploreTracker",
                        "onLocationResult() - Update ignoriert (Bewegung nur ${distance}m, lat=${loc.latitude}, lng=${loc.longitude}, distanzZuhause=${distanceHome}m)",
                        Instant.now().toString(),
                        "LOG"
                    )
                    return
                }

                lastLocation = loc
                errorInsert(
                    "ExploreTracker",
                    "onLocationResult() - neue Position: lat=${loc.latitude}, lng=${loc.longitude}, bewegung=${distance}m, distanzZuhause=${distanceHome}m",
                    Instant.now().toString(),
                    "LOG"
                )
                scope.launch {
                    try {
                        repo.recordLocation(loc.latitude, loc.longitude)
                    } catch (e: Exception) {
                        errorInsert(
                            "ExploreTracker",
                            "onLocationResult() - Fehler beim Speichern: ${e.message}",
                            Instant.now().toString(),
                            "ERROR"
                        )
                    }
                }
            }
        }

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnSuccessListener {
                    errorInsert(
                        "ExploreTracker",
                        "startLocationUpdates() - requestLocationUpdates() erfolgreich",
                        Instant.now().toString(),
                        "LOG"
                    )
                }
                .addOnFailureListener { e ->
                    errorInsert(
                        "ExploreTracker",
                        "startLocationUpdates() - requestLocationUpdates() fehlgeschlagen: ${e.message}",
                        Instant.now().toString(),
                        "ERROR"
                    )
                }
            locationCallback = callback
            _trackerStatus.value = "Läuft aktiv"
            ExploreWorker.schedule(context)
            errorInsert(
                "ExploreTracker",
                "startLocationUpdates() - Active GPS-Updates gestartet (Geofence wird aktiv ueberwacht)",
                Instant.now().toString(),
                "LOG"
            )
        } catch (e: Exception) {
            _trackerStatus.value = "Fehler: ${e.message}"
            errorInsert(
                "ExploreTracker",
                "startLocationUpdates() - Exception: ${e.message}",
                Instant.now().toString(),
                "ERROR"
            )
        }
    }

    fun stop(context: Context) {
        val appCtx = context.applicationContext
        val wasEnabled = isEnabled
        isEnabled = false
        _trackerInfo.value = _trackerInfo.value.copy(isEnabled = false)

        errorInsert(
            "ExploreTracker",
            "stop() - wird aufgerufen (wasEnabled=$wasEnabled)",
            Instant.now().toString(),
            "LOG"
        )

        if (locationCallback != null) {
            getClient(appCtx).removeLocationUpdates(locationCallback!!)
                .addOnSuccessListener {
                    errorInsert(
                        "ExploreTracker",
                        "stop() - removeLocationUpdates() erfolgreich",
                        Instant.now().toString(),
                        "LOG"
                    )
                }
                .addOnFailureListener { e ->
                    errorInsert(
                        "ExploreTracker",
                        "stop() - removeLocationUpdates() fehlgeschlagen: ${e.message}",
                        Instant.now().toString(),
                        "ERROR"
                    )
                }
            locationCallback = null
            lastLocation = null
            ExploreWorker.cancel(appCtx)
            errorInsert(
                "ExploreTracker",
                "stop() - Worker abgebrochen (GPS deaktiviert)",
                Instant.now().toString(),
                "LOG"
            )
        } else if (wasEnabled) {
            errorInsert(
                "ExploreTracker",
                "stop() - war im Startvorgang, aber wurde gestoppt (Heim-WLAN aktiv)",
                Instant.now().toString(),
                "LOG"
            )
        } else {
            errorInsert(
                "ExploreTracker",
                "stop() - war bereits inaktiv",
                Instant.now().toString(),
                "LOG"
            )
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
