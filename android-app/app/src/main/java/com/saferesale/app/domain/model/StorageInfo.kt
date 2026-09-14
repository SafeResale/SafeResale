package com.saferesale.app.domain.model

data class StorageInfo(
    val internalTotal: Long = 0L,
    val internalUsed: Long = 0L,
    val internalFree: Long = 0L,
    val internalUsagePercent: Float = 0f,
    val externalAvailable: Boolean = false,
    val externalTotal: Long = 0L,
    val externalUsed: Long = 0L,
    val externalFree: Long = 0L,
    val externalUsagePercent: Float = 0f,
    val partitions: List<StoragePartition> = emptyList(),
)

data class StoragePartition(
    val mountPoint: String,
    val fileSystem: String,
    val total: Long,
    val free: Long,
    val used: Long,
    val usagePercent: Float,
)