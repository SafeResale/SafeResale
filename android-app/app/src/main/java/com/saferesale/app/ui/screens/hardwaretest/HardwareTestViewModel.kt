package com.saferesale.app.ui.screens.hardwaretest

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.sin

enum class TestStatus { IDLE, RUNNING, PASS, FAIL }

data class HardwareTestItem(
    val id: String,
    val label: String,
    val status: TestStatus = TestStatus.IDLE,
)

@HiltViewModel
class HardwareTestViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _tests = MutableStateFlow(
        listOf(
            HardwareTestItem("vibration",    "Vibration"),
            HardwareTestItem("speaker",      "Speaker"),
            HardwareTestItem("microphone",   "Microphone"),
            HardwareTestItem("accelerometer","Accelerometer"),
            HardwareTestItem("gyroscope",    "Gyroscope"),
            HardwareTestItem("magnetometer", "Magnetometer"),
            HardwareTestItem("proximity",    "Proximity"),
            HardwareTestItem("light",        "Light Sensor"),
            HardwareTestItem("barometer",    "Barometer"),
            HardwareTestItem("step",         "Step Detector"),
        )
    )
    val tests: StateFlow<List<HardwareTestItem>> = _tests.asStateFlow()

    fun runTest(id: String) {
        viewModelScope.launch {
            updateStatus(id, TestStatus.RUNNING)
            delay(800)
            val passed = when (id) {
                "vibration"     -> testVibration()
                "speaker"       -> testSpeaker()
                "microphone"    -> hasMicrophone()
                "accelerometer" -> hasSensor(Sensor.TYPE_ACCELEROMETER)
                "gyroscope"     -> hasSensor(Sensor.TYPE_GYROSCOPE)
                "magnetometer"  -> hasSensor(Sensor.TYPE_MAGNETIC_FIELD)
                "proximity"     -> hasSensor(Sensor.TYPE_PROXIMITY)
                "light"         -> hasSensor(Sensor.TYPE_LIGHT)
                "barometer"     -> hasSensor(Sensor.TYPE_PRESSURE)
                "step"          -> hasSensor(Sensor.TYPE_STEP_DETECTOR)
                else -> false
            }
            updateStatus(id, if (passed) TestStatus.PASS else TestStatus.FAIL)
        }
    }

    fun runAll() {
        viewModelScope.launch {
            _tests.value.forEach { test ->
                delay(200)
                runTest(test.id)
            }
        }
    }

    private fun updateStatus(id: String, status: TestStatus) {
        _tests.value = _tests.value.map { if (it.id == id) it.copy(status = status) else it }
    }

    private fun testVibration(): Boolean = try {
        val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        val effect = VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
        vibrator.vibrate(effect)
        true
    } catch (e: Exception) { false }

    private fun testSpeaker(): Boolean = try {
        val sampleRate = 44100
        val numSamples = sampleRate / 4  // 250ms
        val samples = ShortArray(numSamples) { i ->
            (sin(2 * PI * 1000 * i / sampleRate) * Short.MAX_VALUE).toInt().toShort()
        }
        val track = AudioTrack.Builder()
            .setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA).build())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(numSamples * 2)
            .build()
        track.play()
        track.write(samples, 0, numSamples)
        track.stop()
        track.release()
        true
    } catch (e: Exception) { false }

    private fun hasMicrophone(): Boolean =
        context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_MICROPHONE)

    private fun hasSensor(type: Int): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return sm.getDefaultSensor(type) != null
    }
}
