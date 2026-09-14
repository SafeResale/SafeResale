package com.saferesale.app.domain.model

data class BenchmarkResult(
    val cpuScore: Int = 0,
    val memoryScore: Int = 0,
    val storageScore: Int = 0,
    val overallScore: Int = 0,
    val cpuSingleCoreMips: Double = 0.0,
    val cpuMultiCoreMips: Double = 0.0,
    val memReadMBps: Double = 0.0,
    val memWriteMBps: Double = 0.0,
    val storageReadMBps: Double = 0.0,
    val storageWriteMBps: Double = 0.0,
    val isRunning: Boolean = false,
    val progress: Float = 0f,
    val currentTask: String = "",
    val timestamp: Long = System.currentTimeMillis(),
)

data class NetworkToolResult(
    val type: NetworkToolType,
    val isRunning: Boolean = false,
    val success: Boolean = false,
    val resultText: String = "",
    val latencyMs: Long = 0L,
    val downloadMbps: Double = 0.0,
    val uploadMbps: Double = 0.0,
    val hops: List<TracerouteHop> = emptyList(),
    val dnsAddresses: List<String> = emptyList(),
    val httpStatusCode: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
)

data class TracerouteHop(
    val hop: Int,
    val ip: String,
    val hostname: String,
    val latencyMs: Long,
    val timedOut: Boolean,
)

enum class NetworkToolType {
    PING, DNS_LOOKUP, HTTP_TEST, SPEED_TEST, TRACEROUTE, CONNECTIVITY
}