package com.cloud.exploretab

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object ExploreLocationTracker {

    private var fusedClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null
    private var lastLocation: Location? = null
    private var updatesStarted = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        if (fusedClient != null) return

        val repo = ExploreRepository(context.applicationContext)
        val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)

        // Viel häufigeres Tracking: alle 1 Minute statt 5 Minuten
        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            1 * 60 * 1000L  // 1 Minute (war: 5 Minuten)
        )
            .setMinUpdateDistanceMeters(50f)  // 50m Distanz (angepasst an größere Tiles)
            .setWaitForAccurateLocation(false)
            .setMaxUpdateDelayMillis(2 * 60 * 1000L)  // Max 2 Minuten Verzögerung
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val last = lastLocation
                // Aktualisiere wenn mindestens 50m entfernt (passt zu größeren Tiles)
                if (last != null && loc.distanceTo(last) < 50f) return
                lastLocation = loc
                scope.launch {
                    repo.recordLocation(loc.latitude, loc.longitude)
                }
            }
        }

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            fusedClient = client
            locationCallback = callback
            updatesStarted = true
        } catch (_: Exception) {
        }
    }

    fun stop() {
        if (updatesStarted) {
            locationCallback?.let { fusedClient?.removeLocationUpdates(it) }
            updatesStarted = false
        }
        locationCallback = null
        fusedClient = null
        lastLocation = null
    }
}