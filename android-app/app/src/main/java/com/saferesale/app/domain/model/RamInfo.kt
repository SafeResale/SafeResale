package com.saferesale.app.domain.model

data class RamInfo(
    val totalRamMB: Long = 0L,
    val usedRamMB: Long = 0L,
    val availableRamMB: Long = 0L,
    val freeRamMB: Long = 0L,
    val usagePercent: Float = 0f,
    val isLowMemory: Boolean = false,
    val threshold: Long = 0L,
    val swapTotal: Long = 0L,
    val swapFree: Long = 0L,
    val cached: Long = 0L,
    val buffers: Long = 0L,
)