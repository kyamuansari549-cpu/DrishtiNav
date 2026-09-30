package com.drishtinav.app.nav

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/**
 * High-accuracy GPS fixes for the navigation engine.
 * Started only while navigating, to save battery the rest of the time.
 */
class LocationTracker(context: Context) {

    interface Listener {
        fun onLocation(location: Location)
    }

    var listener: Listener? = null

    private val client = LocationServices.getFusedLocationProviderClient(context)

    @Volatile
    var lastLocation: Location? = null
        private set

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let {
                lastLocation = it
                listener?.onLocation(it)
            }
        }
    }

    @SuppressLint("MissingPermission") // Caller checks location permission first.
    fun start() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000L)
            .setMinUpdateDistanceMeters(2f)
            .setMaxUpdateDelayMillis(5_000L)
            .build()
        client.requestLocationUpdates(request, callback, Looper.getMainLooper())
    }

    fun stop() {
        client.removeLocationUpdates(callback)
    }
}
