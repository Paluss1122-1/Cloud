package com.cloud.tabs.exploretab

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.cloud.core.objects.Config.client
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
import okhttp3.internal.platform.PlatformRegistry.applicationContext
import androidx.core.content.edit
import com.cloud.core.objects.Config

class ExploreGeofenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) return

        when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> {
                Log.d("CLOUDSA", "Geofence: zuhause → Tracking stoppen")
                ExploreLocationTracker.stop(context)
            }
            Geofence.GEOFENCE_TRANSITION_EXIT -> {
                Log.d("CLOUDSA", "Geofence: unterwegs → Tracking starten")
                ExploreLocationTracker.start(context)
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
    val isTracking: Boolean get() = locationCallback != null
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
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofence(geofence)
            .build()

        LocationServices.getGeofencingClient(context)
            .addGeofences(request, geofencePendingIntent(context))
            .addOnSuccessListener { Log.d("CLOUDSA", "Geofence registriert") }
            .addOnFailureListener { Log.e("CLOUDSA", "Geofence Fehler: ${it.message}") }
    }

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        val appCtx = context.applicationContext
        
        // Wenn bereits aktiviert, nicht alles neu triggern
        if (isEnabled) return
        isEnabled = true

        Log.d("CLOUDSA", "ExploreLocationTracker: start() aufgerufen")
        registerGeofence(appCtx)

        getHomeWifiStatus(appCtx, HOME_WIFI_SSID) { isHome ->
            if (isHome) {
                Log.d("CLOUDSA", "WiFi-Check: zuhause → Tracking stoppen")
                stop(appCtx)
                return@getHomeWifiStatus
            }
            if (isEnabled) {
                startLocationUpdates(appCtx)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(context: Context) {
        if (locationCallback != null) return
        val repo = ExploreRepository(context)
        val client = getClient(context)

        val request = LocationRequest.Builder(Priority.PRIORITY_PASSIVE, 60_000L)
            .setMinUpdateDistanceMeters(50f)
            .setWaitForAccurateLocation(true)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (!isEnabled) {
                    stop(context)
                    return
                }
                val loc = result.lastLocation ?: return
                if (lastLocation?.distanceTo(loc)?.let { it < 50f } == true) return
                lastLocation = loc
                scope.launch { repo.recordLocation(loc.latitude, loc.longitude) }
            }
        }

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnSuccessListener { Log.d("CLOUDSA", "Location Updates gestartet") }
                .addOnFailureListener { Log.e("CLOUDSA", "requestLocationUpdates failed: ${it.message}") }
            
            locationCallback = callback
            ExploreWorker.schedule(context)
        } catch (e: Exception) {
            Log.e("CLOUDSA", "requestLocationUpdates Fehler: ${e.message}")
        }
    }

    // ─── Stop ─────────────────────────────────────────────────────────────────

    fun stop(context: Context) {
        val appCtx = context.applicationContext
        val wasEnabled = isEnabled
        isEnabled = false
        
        if (locationCallback != null) {
            getClient(appCtx).removeLocationUpdates(locationCallback!!)
                .addOnSuccessListener { Log.d("CLOUDSA", "Location Updates erfolgreich entfernt") }
                .addOnFailureListener { Log.e("CLOUDSA", "Fehler beim Entfernen der Updates: ${it.message}") }
            locationCallback = null
            lastLocation = null
            ExploreWorker.cancel(appCtx)
            Log.d("CLOUDSA", "Location Tracking gestoppt")
        } else if (wasEnabled) {
            Log.d("CLOUDSA", "Location Tracking war im Startvorgang, aber wurde gestoppt")
        }
    }
}