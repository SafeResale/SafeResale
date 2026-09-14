package com.saferesale.app.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.saferesale.app.domain.model.SensorData
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SensorRepository constructor(
    private val context: Context
) {
    private val sensorManager by lazy {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }

    fun getAvailableSensors(): List<SensorData> {
        return try {
            sensorManager.getSensorList(Sensor.TYPE_ALL).mapIndexed { index, sensor ->
                SensorData(
                    sensorId    = sensor.id,
                    uniqueId    = "${sensor.type}_${sensor.name}_$index",
                    name        = sensor.name ?: "Unknown Sensor",
                    type        = sensor.type,
                    vendor      = sensor.vendor ?: "Unknown",
                    version     = sensor.version,
                    maxRange    = sensor.maximumRange,
                    resolution  = sensor.resolution,
                    power       = sensor.power,
                    minDelay    = sensor.minDelay,
                    isAvailable = true,
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun listenToSensor(sensorType: Int): Flow<SensorData> = callbackFlow {
        val sensor = try {
            sensorManager.getDefaultSensor(sensorType)
        } catch (e: Exception) {
            null
        }

        if (sensor == null) {
            close()
            return@callbackFlow
        }

        // Avoid trigger / one-shot sensors which throw IllegalArgumentException on registerListener
        if (sensor.reportingMode == Sensor.REPORTING_MODE_ONE_SHOT) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(SensorData(
                    sensorId    = sensor.id,
                    uniqueId    = "${sensor.type}_${sensor.name}",
                    name        = sensor.name,
                    type        = event.sensor.type,
                    vendor      = sensor.vendor,
                    version     = sensor.version,
                    maxRange    = sensor.maximumRange,
                    resolution  = sensor.resolution,
                    power       = sensor.power,
                    minDelay    = sensor.minDelay,
                    values      = event.values.toList(),
                    timestamp   = event.timestamp,
                ))
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        val registered = try {
            sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        } catch (e: Exception) {
            false
        }

        if (!registered) {
            close()
            return@callbackFlow
        }

        awaitClose {
            try {
                sensorManager.unregisterListener(listener)
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }
}