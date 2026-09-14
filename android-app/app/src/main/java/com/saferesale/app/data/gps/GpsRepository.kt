package com.saferesale.app.data.gps

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssStatus
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import com.saferesale.app.domain.model.GpsInfo
import com.saferesale.app.domain.model.SatelliteInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class GpsRepository constructor(
    private val context: Context
) {
    private val locationManager by lazy {
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }

    fun isGpsAvailable(): Boolean =
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    @SuppressLint("MissingPermission")
    fun getGpsInfoFlow(): Flow<GpsInfo> = callbackFlow {
        val satellites = mutableListOf<SatelliteInfo>()

        val gnssCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    satellites.clear()
                    for (i in 0 until status.satelliteCount) {
                        satellites.add(SatelliteInfo(
                            svid = status.getSvid(i),
                            constellationType = status.getConstellationType(i),
                            elevation = status.getElevationDegrees(i),
                            azimuth   = status.getAzimuthDegrees(i),
                            cn0Dbhz   = status.getCn0DbHz(i),
                            hasAlmanac   = status.hasAlmanacData(i),
                            hasEphemeris = status.hasEphemerisData(i),
                            usedInFix    = status.usedInFix(i),
                        ))
                    }
                }
            }
        } else null

        val locationListener = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(GpsInfo(
                    isGpsAvailable  = true,
                    latitude        = location.latitude,
                    longitude       = location.longitude,
                    accuracyMeters  = location.accuracy,
                    altitudeMeters  = location.altitude,
                    speedMps        = location.speed,
                    bearingDegrees  = location.bearing,
                    provider        = location.provider ?: "",
                    satelliteCount  = satellites.size,
                    satellites      = satellites.toList(),
                    lastFixTime     = location.time,
                ))
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
        }

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener, Looper.getMainLooper()
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                gnssCallback?.let {
                    locationManager.registerGnssStatusCallback(it, null)
                }
            }
        } catch (e: SecurityException) {
            trySend(GpsInfo(isGpsAvailable = false))
        }

        awaitClose {
            locationManager.removeUpdates(locationListener)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                gnssCallback?.let { locationManager.unregisterGnssStatusCallback(it) }
            }
        }
    }
}