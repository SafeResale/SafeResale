package com.saferesale.app.domain.model

data class GpsInfo(
    val isGpsAvailable: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracyMeters: Float = 0f,
    val altitudeMeters: Double = 0.0,
    val speedMps: Float = 0f,
    val bearingDegrees: Float = 0f,
    val provider: String = "",
    val satelliteCount: Int = 0,
    val satellites: List<SatelliteInfo> = emptyList(),
    val lastFixTime: Long = 0L,
)

data class SatelliteInfo(
    val svid: Int,
    val constellationType: Int,
    val elevation: Float,
    val azimuth: Float,
    val cn0Dbhz: Float,
    val hasAlmanac: Boolean,
    val hasEphemeris: Boolean,
    val usedInFix: Boolean,
)