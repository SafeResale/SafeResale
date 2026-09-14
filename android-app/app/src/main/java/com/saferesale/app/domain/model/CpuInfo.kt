package com.saferesale.app.domain.model

data class CpuInfo(
    val model: String = "",
    val architecture: String = "",
    val totalCores: Int = 0,
    val availableCores: Int = 0,
    val currentFrequencyMHz: List<Long> = emptyList(),
    val maxFrequencyMHz: List<Long> = emptyList(),
    val minFrequencyMHz: List<Long> = emptyList(),
    val usagePercent: Float = 0f,
    val perCoreUsage: List<Float> = emptyList(),
    val coreOnline: List<Boolean> = emptyList(),
    val governor: String = "",
    val abi: String = "",
    val features: String = "",
    val hardware: String = "",
)
