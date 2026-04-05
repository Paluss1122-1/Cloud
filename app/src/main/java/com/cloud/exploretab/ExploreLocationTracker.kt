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

        val request = LocationRequest.Builder(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            5 * 60 * 1000L
        )
            .setMinUpdateDistanceMeters(30f)
            .setWaitForAccurateLocation(false)
            .setMaxUpdateDelayMillis(10 * 60 * 1000L)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                val last = lastLocation
                if (last != null && loc.distanceTo(last) < 30f) return
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