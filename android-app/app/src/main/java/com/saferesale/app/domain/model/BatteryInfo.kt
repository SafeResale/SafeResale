package com.saferesale.app.domain.model

data class BatteryInfo(
    val percentage: Int = 0,
    val isCharging: Boolean = false,
    val chargingMethod: String = "",   // USB / AC / Wireless / Unknown
    val temperatureCelsius: Float = 0f,
    val voltageMilliVolts: Int = 0,
    val currentMicroAmps: Int = 0,
    val capacityMah: Int = 0,
    val health: String = "",           // Good / Overheat / Dead / Over voltage / Unknown
    val technology: String = "",       // Li-ion etc.
    val status: String = "",
    val cycleCount: Int = 0,
    val plugged: Int = 0,
)