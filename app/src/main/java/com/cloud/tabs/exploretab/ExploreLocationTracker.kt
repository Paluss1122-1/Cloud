package com.cloud.tabs.exploretab

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Looper
import android.util.Log
import com.cloud.core.objects.Config
import com.cloud.core.objects.reportError
import com.cloud.quiethoursnotificationhelper.getHomeWifiStatus
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Instant

class ExploreGeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        reportError("ExploreTracker", "GeofenceReceiver.onReceive() - aufgerufen", Instant.now().toString(), "LOG")
        
        val event = GeofencingEvent.fromIntent(intent) ?: run {
            reportError("ExploreTracker", "GeofenceReceiver - GeofencingEvent ist null", Instant.now().toString(), "ERROR")
            return
        }
        
        if (event.hasError()) {
            reportError("ExploreTracker", "GeofenceReceiver - GeofencingEvent hat Fehler: ${event.errorCode}", Instant.now().toString(), "ERROR")
            return
        }

        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                reportError("ExploreTracker", "GeofenceReceiver - ENTER: Benutzer ist zuhause → stop()", Instant.now().toString(), "LOG")
                Log.d("CLOUDSA", "Geofence: zuhause → Tracking stoppen")
                ExploreLocationTracker.stop(context)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                reportError("ExploreTracker", "GeofenceReceiver - EXIT: Benutzer verlässt zuhause → start()", Instant.now().toString(), "LOG")
                Log.d("CLOUDSA", "Geofence: unterwegs → Tracking starten")
                ExploreLocationTracker.start(context)
            }
            else -> {
                reportError("ExploreTracker", "GeofenceReceiver - Unbekannter Übergang: ${event.geofenceTransition}", Instant.now().toString(), "WARN")
            }
        }
    }
}

object ExploreLocationTracker {

    const val HOME_LAT = Config.LAT
    const val HOME_LNG = Config.LON
    private const val GEOFENCE_ID = "HOME"
    private const val GEOFENCE_RADIUS = 100f
    const val HOME_WIFI_SSID = "FRITZ!Box 5590 XO"

    private var locationCallback: LocationCallback? = null
    private var lastLocation: Location? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Volatile
    private var isEnabled = false

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
        reportError("ExploreTracker", "registerGeofence() - wird registriert für HOME_LAT=$HOME_LAT, HOME_LNG=$HOME_LNG", Instant.now().toString(), "LOG")
        
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
            .addOnSuccessListener { 
                reportError("ExploreTracker", "registerGeofence() - erfolgreich registriert", Instant.now().toString(), "LOG")
                Log.d("CLOUDSA", "Geofence registriert") 
            }
            .addOnFailureListener { 
                reportError("ExploreTracker", "registerGeofence() - Fehler: ${it.message}", Instant.now().toString(), "ERROR")
                Log.e("CLOUDSA", "Geofence Fehler: ${it.message}")
            }
    }

    fun start(context: Context) {
        val appCtx = context.applicationContext
        if (isEnabled) {
            reportError("ExploreTracker", "start() - bereits aktiviert, breche ab", Instant.now().toString(), "LOG")
            return
        }
        isEnabled = true

        reportError("ExploreTracker", "start() - LocationTracker wird gestartet", Instant.now().toString(), "LOG")
        Log.d("CLOUDSA", "ExploreLocationTracker: start() aufgerufen")
        
        try {
            registerGeofence(appCtx)
            reportError("ExploreTracker", "start() - Geofence registriert erfolgreich", Instant.now().toString(), "LOG")
        } catch (e: Exception) {
            reportError("ExploreTracker", "start() - Geofence Registrierung failed: ${e.message}", Instant.now().toString(), "ERROR")
            Log.e("CLOUDSA", "Geofence Registrierung fehlgeschlagen: ${e.message}")
        }

        reportError("ExploreTracker", "start() - WiFi-Status wird überprüft für SSID: $HOME_WIFI_SSID", Instant.now().toString(), "LOG")
        getHomeWifiStatus(appCtx, HOME_WIFI_SSID) { isHomeWifi ->
            reportError("ExploreTracker", "start() - WiFi-Callback: isHome=$isHomeWifi", Instant.now().toString(), "LOG")
            if (isHomeWifi) {
                reportError("ExploreTracker", "start() - WiFi verbunden → Tracking wird NICHT gestartet", Instant.now().toString(), "LOG")
                Log.d("CLOUDSA", "WiFi-Check: zuhause → Tracking stoppen")
                stop(appCtx)
                return@getHomeWifiStatus
            }
            if (isEnabled) {
                reportError("ExploreTracker", "start() - Nicht zuhause → startLocationUpdates() wird aufgerufen", Instant.now().toString(), "LOG")
                startLocationUpdates(appCtx)
            } else {
                reportError("ExploreTracker", "start() - isEnabled wurde false während WiFi-Check, breche ab", Instant.now().toString(), "LOG")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(context: Context) {
        reportError("ExploreTracker", "startLocationUpdates() - startet", Instant.now().toString(), "LOG")
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
                if (!isEnabled) {
                    reportError("ExploreTracker", "onLocationResult() - isEnabled=false, stoppe Updates", Instant.now().toString(), "LOG")
                    stop(context)
                    return
                }
                val loc = result.lastLocation ?: run {
                    reportError("ExploreTracker", "onLocationResult() - lastLocation ist null", Instant.now().toString(), "WARN")
                    return
                }
                
                val distance = lastLocation?.distanceTo(loc) ?: Float.MAX_VALUE
                if (distance < 50f) {
                    reportError("ExploreTracker", "onLocationResult() - Entfernung zu klein: ${distance}m, ignoriere", Instant.now().toString(), "LOG")
                    return
                }
                
                lastLocation = loc
                reportError("ExploreTracker", "onLocationResult() - Neue Location: lat=${loc.latitude}, lng=${loc.longitude}, distanz=${distance}m", Instant.now().toString(), "LOG")
                scope.launch { 
                    try {
                        repo.recordLocation(loc.latitude, loc.longitude)
                        reportError("ExploreTracker", "onLocationResult() - Location gespeichert", Instant.now().toString(), "LOG")
                    } catch (e: Exception) {
                        reportError("ExploreTracker", "onLocationResult() - Fehler beim Speichern: ${e.message}", Instant.now().toString(), "ERROR")
                    }
                }
            }
        }

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnSuccessListener { 
                    reportError("ExploreTracker", "startLocationUpdates() - Location Updates erfolgreich gestartet", Instant.now().toString(), "LOG")
                    Log.d("CLOUDSA", "Location Updates gestartet") 
                }
                .addOnFailureListener { 
                    reportError("ExploreTracker", "startLocationUpdates() - requestLocationUpdates failed: ${it.message}", Instant.now().toString(), "ERROR")
                    Log.e("CLOUDSA", "requestLocationUpdates failed: ${it.message}") 
                }
            locationCallback = callback
            ExploreWorker.schedule(context)
            reportError("ExploreTracker", "startLocationUpdates() - Worker geplant", Instant.now().toString(), "LOG")
        } catch (e: Exception) {
            reportError("ExploreTracker", "startLocationUpdates() - Exception: ${e.message}", Instant.now().toString(), "ERROR")
            Log.e("CLOUDSA", "requestLocationUpdates Fehler: ${e.message}")
        }
    }

    // ========== DEBUG TEST FUNCTIONS ==========
    fun debugTriggerExitGeofence(context: Context) {
        reportError("ExploreTracker", "DEBUG: Sende EXIT Intent zum GeofenceReceiver", Instant.now().toString(), "LOG")
        val intent = Intent(context, ExploreGeofenceReceiver::class.java)
        intent.action = "com.google.android.gms.location.GEOFENCE_TRANSITION"
        
        // Simuliere ein EXIT Geofencing Event
        val geofenceData = android.content.Intent()
        geofenceData.putExtra("geofence_transition", Geofence.GEOFENCE_TRANSITION_EXIT)
        geofenceData.putExtra("geofence_request_id", "HOME")
        
        val receiver = ExploreGeofenceReceiver()
        receiver.onReceive(context, geofenceData)
    }

    fun debugTriggerEnterGeofence(context: Context) {
        reportError("ExploreTracker", "DEBUG: Sende ENTER Intent zum GeofenceReceiver", Instant.now().toString(), "LOG")
        val intent = Intent(context, ExploreGeofenceReceiver::class.java)
        intent.action = "com.google.android.gms.location.GEOFENCE_TRANSITION"
        
        val geofenceData = android.content.Intent()
        geofenceData.putExtra("geofence_transition", Geofence.GEOFENCE_TRANSITION_ENTER)
        geofenceData.putExtra("geofence_request_id", "HOME")
        
        val receiver = ExploreGeofenceReceiver()
        receiver.onReceive(context, geofenceData)
    }

    fun debugGetCurrentStatus(): String {
        return "isEnabled=$isEnabled, locationCallback=${locationCallback != null}, lastLocation=$lastLocation"
    }

    fun stop(context: Context) {
        val appCtx = context.applicationContext
        val wasEnabled = isEnabled
        isEnabled = false

        reportError("ExploreTracker", "stop() - wird aufgerufen (wasEnabled=$wasEnabled)", Instant.now().toString(), "LOG")

        if (locationCallback != null) {
            getClient(appCtx).removeLocationUpdates(locationCallback!!)
                .addOnSuccessListener { 
                    reportError("ExploreTracker", "stop() - Location Updates erfolgreich entfernt", Instant.now().toString(), "LOG")
                    Log.d("CLOUDSA", "Location Updates erfolgreich entfernt") 
                }
                .addOnFailureListener { 
                    reportError("ExploreTracker", "stop() - Fehler beim Entfernen: ${it.message}", Instant.now().toString(), "ERROR")
                    Log.e("CLOUDSA", "Fehler beim Entfernen der Updates: ${it.message}") 
                }
            locationCallback = null
            lastLocation = null
            ExploreWorker.cancel(appCtx)
            reportError("ExploreTracker", "stop() - Worker abgebrochen", Instant.now().toString(), "LOG")
            Log.d("CLOUDSA", "Location Tracking gestoppt")
        } else if (wasEnabled) {
            reportError("ExploreTracker", "stop() - war im Startvorgang, aber wurde gestoppt", Instant.now().toString(), "LOG")
            Log.d("CLOUDSA", "Location Tracking war im Startvorgang, aber wurde gestoppt")
        } else {
            reportError("ExploreTracker", "stop() - war bereits inaktiv", Instant.now().toString(), "LOG")
        }
    }
}