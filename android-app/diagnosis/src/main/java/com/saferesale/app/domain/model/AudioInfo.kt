package com.saferesale.app.domain.model

data class AudioInfo(
    val hasSpeaker: Boolean = false,
    val hasMicrophone: Boolean = false,
    val hasBluetoothAudio: Boolean = false,
    val hasHeadphones: Boolean = false,
    val nativeOutputSampleRate: Int = 0,
    val nativeOutputFramesPerBuffer: Int = 0,
    val maxVolume: Int = 0,
    val currentVolume: Int = 0,
    val isMuted: Boolean = false,
    val audioOutputDevices: List<AudioDeviceDetail> = emptyList(),
    val audioInputDevices: List<AudioDeviceDetail> = emptyList(),
    val supportedEncodings: List<String> = emptyList(),
)

data class AudioDeviceDetail(
    val id: Int,
    val name: String,
    val type: String,
    val isSource: Boolean,
    val isSink: Boolean,
    val sampleRates: List<Int>,
    val channelCounts: List<Int>,
)
