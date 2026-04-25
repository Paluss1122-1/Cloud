package com.cloud.tabs.exploretab

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
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

    private var locationCallback: LocationCallback? = null
    private var lastLocation: Location? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @SuppressLint("MissingPermission")
    fun start(context: Context) {
        if (locationCallback != null) return
        val appCtx = context.applicationContext
        val repo = ExploreRepository(appCtx)
        val client = LocationServices.getFusedLocationProviderClient(appCtx)

        val request = LocationRequest.Builder(Priority.PRIORITY_PASSIVE, 60_000L)
            .setMinUpdateDistanceMeters(50f)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                if (lastLocation?.distanceTo(loc)?.let { it < 50f } == true) return
                lastLocation = loc
                scope.launch { repo.recordLocation(loc.latitude, loc.longitude) }
            }
        }

        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
            locationCallback = callback
        } catch (_: Exception) {}

        ExploreWorker.schedule(appCtx)
    }

    fun stop(context: Context) {
        locationCallback?.let {
            LocationServices.getFusedLocationProviderClient(context).removeLocationUpdates(it)
        }
        locationCallback = null
        lastLocation = null
        ExploreWorker.cancel(context)
    }
}