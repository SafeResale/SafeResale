package com.saferesale.app.data.cpu

import android.os.Build
import com.saferesale.app.domain.model.CpuInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

class CpuInfoRepository constructor() {

    private var previousCpuStats: Array<LongArray> = arrayOf()

    fun getCpuInfoFlow(intervalMs: Long = 1000L): Flow<CpuInfo> = flow {
        while (true) {
            emit(buildCpuInfo())
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    fun getCpuInfoOnce(): CpuInfo = buildCpuInfo()

    private fun buildCpuInfo(): CpuInfo {
        val cores = Runtime.getRuntime().availableProcessors()
        val freqs = (0 until cores).map { readCoreFreq(it, "scaling_cur_freq") }
        val maxFs = (0 until cores).map { readCoreFreq(it, "cpuinfo_max_freq") }
        val minFs = (0 until cores).map { readCoreFreq(it, "cpuinfo_min_freq") }
        val online = (0 until cores).map { isCoreOnline(it) }
        val (total, perCore) = readCpuUsage(cores)
        val governor = readGovernor(0)

        return CpuInfo(
            model            = parseCpuModel(),
            architecture     = Build.SUPPORTED_ABIS.firstOrNull() ?: "Unknown",
            totalCores       = cores,
            availableCores   = online.count { it },
            currentFrequencyMHz = freqs,
            maxFrequencyMHz  = maxFs,
            minFrequencyMHz  = minFs,
            usagePercent     = total,
            perCoreUsage     = perCore,
            coreOnline       = online,
            governor         = governor,
            abi              = Build.SUPPORTED_ABIS.joinToString(", "),
            hardware         = readProcCpuInfo("Hardware"),
            features         = readProcCpuInfo("Features").take(100),
        )
    }

    private fun readCoreFreq(core: Int, file: String): Long = try {
        val path = "/sys/devices/system/cpu/cpu$core/cpufreq/$file"
        File(path).readText().trim().toLongOrNull()?.div(1000L) ?: 0L
    } catch (e: Exception) { 0L }

    private fun isCoreOnline(core: Int): Boolean = try {
        if (core == 0) true
        else File("/sys/devices/system/cpu/cpu$core/online").readText().trim() == "1"
    } catch (e: Exception) { true }

    private fun readGovernor(core: Int): String = try {
        File("/sys/devices/system/cpu/cpu$core/cpufreq/scaling_governor")
            .readText().trim().replaceFirstChar { it.uppercase() }
    } catch (e: Exception) { "Unknown" }

    private fun parseCpuModel(): String = try {
        val hardware = readProcCpuInfo("Hardware")
        val model = readProcCpuInfo("model name")
        when {
            model.isNotEmpty() -> model
            hardware.isNotEmpty() -> hardware
            else -> "Unknown"
        }
    } catch (e: Exception) { "Unknown" }

    private fun readProcCpuInfo(field: String): String = try {
        File("/proc/cpuinfo").readLines()
            .firstOrNull { it.startsWith(field, ignoreCase = true) }
            ?.substringAfter(":")
            ?.trim() ?: ""
    } catch (e: Exception) { "" }

    /** Returns (totalUsage%, [perCore%]) */
    private fun readCpuUsage(cores: Int): Pair<Float, List<Float>> {
        return try {
            val lines = File("/proc/stat").readLines()
            val current = lines
                .filter { it.startsWith("cpu") }
                .map { parseCpuStatLine(it) }

            if (previousCpuStats.isEmpty() || previousCpuStats.size != current.size) {
                previousCpuStats = current.toTypedArray()
                return 0f to List(cores) { 0f }
            }

            val perCore = current.drop(1).take(cores).mapIndexed { i, cur ->
                val prev = previousCpuStats.getOrNull(i + 1) ?: LongArray(0)
                calcUsage(prev, cur)
            }

            val total = calcUsage(previousCpuStats[0], current[0])
            previousCpuStats = current.toTypedArray()
            total to perCore
        } catch (e: Exception) {
            0f to List(cores) { 0f }
        }
    }

    private fun parseCpuStatLine(line: String): LongArray {
        val parts = line.trim().split(Regex("\\s+")).drop(1)
        return LongArray(parts.size) { i -> parts[i].toLongOrNull() ?: 0L }
    }

    private fun calcUsage(prev: LongArray, cur: LongArray): Float {
        if (prev.isEmpty() || cur.isEmpty()) return 0f
        val prevIdle = prev.getOrElse(3) { 0L } + prev.getOrElse(4) { 0L }
        val curIdle  = cur.getOrElse(3)  { 0L } + cur.getOrElse(4)  { 0L }
        val prevTotal = prev.sum()
        val curTotal  = cur.sum()
        val diffTotal = curTotal - prevTotal
        val diffIdle  = curIdle - prevIdle
        if (diffTotal <= 0L) return 0f
        return ((diffTotal - diffIdle).toFloat() / diffTotal.toFloat() * 100f).coerceIn(0f, 100f)
    }
}