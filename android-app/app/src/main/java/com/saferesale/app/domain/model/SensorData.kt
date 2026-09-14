package com.saferesale.app.domain.model

import android.hardware.Sensor

data class SensorData(
    val sensorId: Int,
    val name: String,
    val type: Int,
    val vendor: String,
    val version: Int,
    val maxRange: Float,
    val resolution: Float,
    val power: Float,
    val minDelay: Int,
    val values: List<Float> = emptyList(),
    val timestamp: Long = 0L,
    val isAvailable: Boolean = true,
    val uniqueId: String = "",
) {
    val typeName: String get() = when (type) {
        Sensor.TYPE_ACCELEROMETER       -> "Accelerometer"
        Sensor.TYPE_GYROSCOPE           -> "Gyroscope"
        Sensor.TYPE_MAGNETIC_FIELD      -> "Magnetometer"
        Sensor.TYPE_PROXIMITY           -> "Proximity"
        Sensor.TYPE_LIGHT               -> "Light"
        Sensor.TYPE_PRESSURE            -> "Barometer"
        Sensor.TYPE_STEP_DETECTOR       -> "Step Detector"
        Sensor.TYPE_STEP_COUNTER        -> "Step Counter"
        Sensor.TYPE_ROTATION_VECTOR     -> "Rotation Vector"
        Sensor.TYPE_GRAVITY             -> "Gravity"
        Sensor.TYPE_LINEAR_ACCELERATION -> "Linear Acceleration"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "Temperature"
        Sensor.TYPE_RELATIVE_HUMIDITY   -> "Humidity"
        Sensor.TYPE_HEART_RATE          -> "Heart Rate"
        else -> "Sensor #$type"
    }
    val unit: String get() = when (type) {
        Sensor.TYPE_ACCELEROMETER, Sensor.TYPE_LINEAR_ACCELERATION,
        Sensor.TYPE_GRAVITY        -> "m/s²"
        Sensor.TYPE_GYROSCOPE      -> "rad/s"
        Sensor.TYPE_MAGNETIC_FIELD -> "μT"
        Sensor.TYPE_LIGHT          -> "lx"
        Sensor.TYPE_PRESSURE       -> "hPa"
        Sensor.TYPE_PROXIMITY      -> "cm"
        Sensor.TYPE_AMBIENT_TEMPERATURE -> "°C"
        Sensor.TYPE_RELATIVE_HUMIDITY   -> "%"
        else -> ""
    }
}
