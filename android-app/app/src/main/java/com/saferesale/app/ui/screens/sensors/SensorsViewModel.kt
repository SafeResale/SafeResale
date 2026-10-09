package com.saferesale.app.ui.screens.sensors

import android.hardware.Sensor
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saferesale.app.data.sensor.SensorRepository
import com.saferesale.app.domain.model.SensorData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SensorsViewModel @Inject constructor(private val repo: SensorRepository) : ViewModel() {
    val availableSensors = repo.getAvailableSensors()

    private val _liveValues = MutableStateFlow<Map<Int, SensorData>>(emptyMap())
    val liveValues: StateFlow<Map<Int, SensorData>> = _liveValues.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private var listeningJob: Job? = null

    // Safe continuous or on-change sensors that can be streamed without security or trigger crashes
    private val safeContinuousSensors = setOf(
        Sensor.TYPE_ACCELEROMETER,
        Sensor.TYPE_GYROSCOPE,
        Sensor.TYPE_MAGNETIC_FIELD,
        Sensor.TYPE_LIGHT,
        Sensor.TYPE_PROXIMITY,
        Sensor.TYPE_PRESSURE,
        Sensor.TYPE_GRAVITY,
        Sensor.TYPE_LINEAR_ACCELERATION,
        Sensor.TYPE_ROTATION_VECTOR,
        Sensor.TYPE_AMBIENT_TEMPERATURE,
        Sensor.TYPE_RELATIVE_HUMIDITY,
    )

    fun startListening() {
        if (_isListening.value) return
        _isListening.value = true

        val sensorsToListen = availableSensors.filter { it.type in safeContinuousSensors }

        listeningJob = viewModelScope.launch {
            sensorsToListen.forEach { sensor ->
                launch {
                    try {
                        repo.listenToSensor(sensor.type)
                            .catch { /* ignore single sensor failure */ }
                            .collect { data ->
                                _liveValues.update { current ->
                                    current + (sensor.type to data)
                                }
                            }
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
        }
    }

    fun stopListening() {
        _isListening.value = false
        listeningJob?.cancel()
        listeningJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
