package com.saferesale.app.data.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import com.saferesale.app.domain.model.AudioDeviceDetail
import com.saferesale.app.domain.model.AudioInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AudioInfoRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    fun getAudioInfo(): AudioInfo {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_ALL)
        val outputDevices = devices.filter { it.isSink }.map { it.toDetail() }
        val inputDevices  = devices.filter { it.isSource }.map { it.toDetail() }

        val hasSpeaker    = devices.any { it.isSink && it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
        val hasMic        = devices.any { it.isSource && it.type == AudioDeviceInfo.TYPE_BUILTIN_MIC }
        val hasBt         = devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                                          it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }
        val hasHeadphones = devices.any { it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                                          it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES }

        val nativeSampleRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
            ?.toIntOrNull() ?: 0
        val nativeFrames = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER)
            ?.toIntOrNull() ?: 0

        return AudioInfo(
            hasSpeaker             = hasSpeaker,
            hasMicrophone          = hasMic,
            hasBluetoothAudio      = hasBt,
            hasHeadphones          = hasHeadphones,
            nativeOutputSampleRate = nativeSampleRate,
            nativeOutputFramesPerBuffer = nativeFrames,
            maxVolume              = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            currentVolume          = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
            isMuted                = audioManager.isStreamMute(AudioManager.STREAM_MUSIC),
            audioOutputDevices     = outputDevices,
            audioInputDevices      = inputDevices,
            supportedEncodings     = getSupportedEncodings(),
        )
    }

    private fun AudioDeviceInfo.toDetail() = AudioDeviceDetail(
        id           = id,
        name         = productName.toString(),
        type         = audioDeviceTypeName(type),
        isSource     = isSource,
        isSink       = isSink,
        sampleRates  = sampleRates.toList(),
        channelCounts= channelCounts.toList(),
    )

    private fun audioDeviceTypeName(type: Int) = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER    -> "Built-in Speaker"
        AudioDeviceInfo.TYPE_BUILTIN_MIC        -> "Built-in Mic"
        AudioDeviceInfo.TYPE_WIRED_HEADSET      -> "Wired Headset"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES   -> "Wired Headphones"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP     -> "Bluetooth A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO      -> "Bluetooth SCO"
        AudioDeviceInfo.TYPE_USB_DEVICE         -> "USB Audio"
        AudioDeviceInfo.TYPE_USB_HEADSET        -> "USB Headset"
        AudioDeviceInfo.TYPE_HDMI               -> "HDMI"
        AudioDeviceInfo.TYPE_LINE_ANALOG        -> "Line Analog"
        else -> "Unknown ($type)"
    }

    private fun getSupportedEncodings(): List<String> {
        val encodings = listOf(
            AudioFormat.ENCODING_PCM_16BIT to "PCM 16-bit",
            AudioFormat.ENCODING_PCM_8BIT to "PCM 8-bit",
            AudioFormat.ENCODING_PCM_FLOAT to "PCM Float",
            AudioFormat.ENCODING_AC3 to "Dolby AC3",
            AudioFormat.ENCODING_E_AC3 to "Dolby E-AC3",
            AudioFormat.ENCODING_DTS to "DTS",
            AudioFormat.ENCODING_DTS_HD to "DTS HD",
            AudioFormat.ENCODING_DOLBY_TRUEHD to "Dolby TrueHD",
            AudioFormat.ENCODING_AAC_LC to "AAC-LC",
        )
        return encodings.filter { (enc, _) ->
            try { AudioFormat.Builder().setEncoding(enc).build(); true }
            catch (e: Exception) { false }
        }.map { it.second }
    }
}
