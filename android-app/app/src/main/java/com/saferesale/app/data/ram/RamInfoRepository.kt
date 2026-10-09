package com.saferesale.app.data.ram

import android.app.ActivityManager
import android.content.Context
import com.saferesale.app.domain.model.RamInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RamInfoRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val activityManager by lazy {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    fun getRamInfoFlow(intervalMs: Long = 1000L): Flow<RamInfo> = flow {
        while (true) {
            emit(buildRamInfo())
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    fun getRamInfoOnce(): RamInfo = buildRamInfo()

    private fun buildRamInfo(): RamInfo {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)

        val totalMB     = memInfo.totalMem / (1024 * 1024)
        val availableMB = memInfo.availMem / (1024 * 1024)
        val usedMB      = totalMB - availableMB
        val usagePct    = if (totalMB > 0) usedMB.toFloat() / totalMB.toFloat() * 100f else 0f

        val procMem = readProcMemInfo()

        return RamInfo(
            totalRamMB     = totalMB,
            usedRamMB      = usedMB,
            availableRamMB = availableMB,
            freeRamMB      = procMem.getOrDefault("MemFree", 0L),
            usagePercent   = usagePct,
            isLowMemory    = memInfo.lowMemory,
            threshold      = memInfo.threshold / (1024 * 1024),
            swapTotal      = procMem.getOrDefault("SwapTotal", 0L),
            swapFree       = procMem.getOrDefault("SwapFree", 0L),
            cached         = procMem.getOrDefault("Cached", 0L),
            buffers        = procMem.getOrDefault("Buffers", 0L),
        )
    }

    private fun readProcMemInfo(): Map<String, Long> = try {
        File("/proc/meminfo").readLines()
            .associate { line ->
                val parts = line.split(Regex("\\s+"))
                val key = parts.getOrElse(0) { "" }.removeSuffix(":")
                val value = parts.getOrElse(1) { "0" }.toLongOrNull()?.div(1024L) ?: 0L
                key to value
            }
    } catch (e: Exception) { emptyMap() }
}
