package app.befare.android

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock

/**
 * GPS fixes from the platform [LocationManager]. No Google Play services and no network needed:
 * the GPS provider works offline (the network provider is used too when available, for a faster
 * first fix). Call [start] only after location permission is granted.
 */
class LocationTracker(context: Context) {
    private val manager = context.getSystemService(LocationManager::class.java)
    private var listener: LocationListener? = null

    @SuppressLint("MissingPermission")
    fun start(onFix: (Location) -> Unit) {
        stop()
        val l = object : LocationListener {
            override fun onLocationChanged(location: Location) = onFix(location)
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        listener = l
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            if (manager.allProviders.contains(provider)) {
                manager.requestLocationUpdates(provider, 2_000L, 5f, l, Looper.getMainLooper())
            }
        }
        // A cached fix is only useful if it's recent; an old one may be somewhere else entirely.
        manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?.takeIf { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos < 2 * 60 * 1_000_000_000L }
            ?.let(onFix)
    }

    fun stop() {
        listener?.let(manager::removeUpdates)
        listener = null
    }
}
