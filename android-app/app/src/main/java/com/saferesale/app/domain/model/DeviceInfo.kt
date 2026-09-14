package com.saferesale.app.domain.model

data class DeviceInfo(
    val deviceName: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val brand: String = "",
    val androidVersion: String = "",
    val sdkVersion: Int = 0,
    val buildNumber: String = "",
    val securityPatch: String = "",
    val kernelVersion: String = "",
    val bootloader: String = "",
    val architecture: String = "",
    val screenResolution: String = "",
    val screenDensity: Int = 0,
    val refreshRate: Float = 0f,
    val hdrSupport: Boolean = false,
    val product: String = "",
    val hardware: String = "",
    val fingerprint: String = "",
)
