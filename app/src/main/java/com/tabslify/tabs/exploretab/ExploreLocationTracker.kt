package com.tabslify.tabs.exploretab

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.tabslify.core.objects.Config
import com.tabslify.core.objects.reportError
import com.tabslify.quiethoursnotificationhelper.getHomeWifiStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

class ExploreGeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: run {
            return
        }
        
        if (event.hasError()) {
            reportError("ExploreTracker", "GeofenceReceiver - GeofencingEvent hat Fehler: ${event.errorCode}", Instant.now().toString(), "ERROR")
            return
        }

        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                reportError("ExploreTracker", "GeofenceReceiver - ENTER: Benutzer ist zuhause → stop()", Instant.now().toString(), "LOG")
                ExploreLocationTracker.stop(context)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                reportError("ExploreTracker", "GeofenceReceiver - EXIT: Benutzer verlässt zuhause → start()", Instant.now().toString(), "LOG")
                ExploreLocationTracker.start(context)
            }
            else -> {
            }
        }
    }
}

object ExploreLocationTracker {

    val HOME_LAT = Config.LAT
    val HOME_LNG = Config.LON
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

    private fun getClient(context: Context) = LocationServices.getFusedLocationProviderClient(context.applicationContext)

    private fun geofencePendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, ExploreGeofenceReceiver::class.java)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
    }

    @SuppressLint("MissingPermission")
    private fun registerGeofence(context: Context) {
        val geofence = Geofence.Builder()
            .setRequestId(GEOFENCE_ID)
            .setCircularRegion(HOME_LAT, HOME_LNG, GEOFENCE_RADIUS)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .setTransitionTypes(
                Geofence.GEOFENCE_TRANSITION_ENTER or
                        Geofence.GEOFENCE_TRANSITION_EXIT
            )
            .build()

        val request = GeofencingRequest.Builder()
            .setInitialTrigger(0)
            .addGeofence(geofence)
            .build()

        LocationServices.getGeofencingClient(context)
            .addGeofences(request, geofencePendingIntent(context))
            .addOnSuccessListener {}
            .addOnFailureListener {}
    }

    fun start(context: Context) {
        val appCtx = context.applicationContext
        if (isNightTime()) {
            reportError("ExploreTracker", "start() - Night time (0-5 Uhr), wird nicht gestartet", Instant.now().toString(), "LOG")
            ExploreNightRestartWorker.schedule(appCtx)
            return
        }
        ExploreNightRestartWorker.cancel(appCtx)
        if (isEnabled) {
            return
        }
        isEnabled = true

        reportError("ExploreTracker", "start() - LocationTracker wird gestartet", Instant.now().toString(), "LOG")
        try {
            registerGeofence(appCtx)
        } catch (e: Exception) {
            reportError("ExploreTracker", "start() - Geofence Registrierung failed: ${e.message}", Instant.now().toString(), "ERROR")
        }

        getHomeWifiStatus(appCtx, HOME_WIFI_SSID) { isHomeWifi ->
            reportError("ExploreTracker", "start() - WiFi-Callback: isHome=$isHomeWifi", Instant.now().toString(), "LOG")
            if (isHomeWifi) {
                stop(appCtx)
                return@getHomeWifiStatus
            }
            if (isEnabled && !isNightTime()) {
                startLocationUpdates(appCtx)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(context: Context) {
        if (locationCallback != null) {
            reportError("ExploreTracker", "startLocationUpdates() - locationCallback existiert bereits, breche ab", Instant.now().toString(), "LOG")
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
                    stop(context)
                    return
                }
                val loc = result.lastLocation ?: run {
                    return
                }
                
                val distance = lastLocation?.distanceTo(loc) ?: Float.MAX_VALUE
                if (distance < 50f) {
                    return
                }
                
                lastLocation = loc
                scope.launch {
                    try {
                        repo.recordLocation(loc.latitude, loc.longitude)
                    } catch (e: Exception) {
                        reportError("ExploreTracker", "onLocationResult() - Fehler beim Speichern: ${e.message}", Instant.now().toString(), "ERROR")
                    }
                }
            }
        }

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnSuccessListener {}
                .addOnFailureListener {}
            locationCallback = callback
            ExploreWorker.schedule(context)
        } catch (e: Exception) {
            reportError("ExploreTracker", "startLocationUpdates() - Exception: ${e.message}", Instant.now().toString(), "ERROR")
        }
    }

    fun stop(context: Context) {
        val appCtx = context.applicationContext
        val wasEnabled = isEnabled
        isEnabled = false

        reportError("ExploreTracker", "stop() - wird aufgerufen (wasEnabled=$wasEnabled)", Instant.now().toString(), "LOG")

        if (locationCallback != null) {
            getClient(appCtx).removeLocationUpdates(locationCallback!!)
                .addOnSuccessListener {}
                .addOnFailureListener { }
            locationCallback = null
            lastLocation = null
            ExploreWorker.cancel(appCtx)
            reportError("ExploreTracker", "stop() - Worker abgebrochen", Instant.now().toString(), "LOG")
        } else if (wasEnabled) {
            reportError("ExploreTracker", "stop() - war im Startvorgang, aber wurde gestoppt", Instant.now().toString(), "LOG")
        } else {
            reportError("ExploreTracker", "stop() - war bereits inaktiv", Instant.now().toString(), "LOG")
        }
        
        if (isNightTime()) {
            ExploreNightRestartWorker.schedule(appCtx)
        }
    }
}