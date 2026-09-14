package com.saferesale.app.data.benchmark

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class BenchmarkRepository @Inject constructor() {

    fun runBenchmark(cacheDir: File): Flow<BenchmarkProgress> = flow {
        emit(BenchmarkProgress(task = "CPU Single-Core", progress = 0f))
        val singleScore = benchmarkCpuSingleCore()
        emit(BenchmarkProgress(task = "CPU Multi-Core", progress = 0.2f, cpuSingleScore = singleScore))

        val multiScore = benchmarkCpuMultiCore()
        emit(BenchmarkProgress(task = "Memory Read/Write", progress = 0.4f,
            cpuSingleScore = singleScore, cpuMultiScore = multiScore))

        val (memRead, memWrite) = benchmarkMemory()
        emit(BenchmarkProgress(task = "Storage Read/Write", progress = 0.6f,
            cpuSingleScore = singleScore, cpuMultiScore = multiScore,
            memReadMBps = memRead, memWriteMBps = memWrite))

        val (storRead, storWrite) = benchmarkStorage(cacheDir)
        emit(BenchmarkProgress(task = "Computing Score", progress = 0.9f,
            cpuSingleScore = singleScore, cpuMultiScore = multiScore,
            memReadMBps = memRead, memWriteMBps = memWrite,
            storReadMBps = storRead, storWriteMBps = storWrite))

        val cpu    = ((singleScore * 0.4 + multiScore * 0.6) / 1_000_000.0 * 10_000).toInt().coerceIn(0, 15000)
        val memory = ((memRead + memWrite) / 2.0 / 10_000.0 * 10_000).toInt().coerceIn(0, 15000)
        val storage= ((storRead + storWrite) / 2.0 / 5_000.0 * 10_000).toInt().coerceIn(0, 15000)
        val overall= ((cpu * 0.4 + memory * 0.35 + storage * 0.25)).toInt()

        emit(BenchmarkProgress(
            task = "Done", progress = 1f,
            cpuSingleScore = singleScore, cpuMultiScore = multiScore,
            memReadMBps = memRead, memWriteMBps = memWrite,
            storReadMBps = storRead, storWriteMBps = storWrite,
            cpuScore = cpu, memoryScore = memory, storageScore = storage, overallScore = overall,
            isDone = true,
        ))
    }.flowOn(Dispatchers.Default)

    private fun benchmarkCpuSingleCore(): Long {
        val start = System.nanoTime()
        var count = 0L
        val limit = 50_000L
        for (n in 2..limit) {
            var isPrime = true
            for (i in 2..sqrt(n.toDouble()).toLong()) {
                if (n % i == 0L) { isPrime = false; break }
            }
            if (isPrime) count++
        }
        return System.nanoTime() - start
    }

    private fun benchmarkCpuMultiCore(): Long {
        val cores = Runtime.getRuntime().availableProcessors()
        val results = LongArray(cores)
        val threads = (0 until cores).map { idx ->
            Thread {
                var x = 0.0
                for (i in 0..500_000) x += sqrt(i.toDouble())
                results[idx] = x.toLong()
            }.also { t -> t.start() }
        }
        val start = System.nanoTime()
        threads.forEach { it.join() }
        return System.nanoTime() - start
    }

    private fun benchmarkMemory(): Pair<Double, Double> {
        val size = 64 * 1024 * 1024 // 64 MB
        val arr = ByteArray(size)

        var start = System.nanoTime()
        for (i in arr.indices) arr[i] = (i and 0xFF).toByte()
        val writeNs = System.nanoTime() - start

        start = System.nanoTime()
        var checksum = 0L
        for (b in arr) checksum += b.toLong()
        val readNs = System.nanoTime() - start

        val readMBps  = size.toDouble() / (readNs  / 1_000_000_000.0) / (1024 * 1024)
        val writeMBps = size.toDouble() / (writeNs / 1_000_000_000.0) / (1024 * 1024)
        return readMBps to writeMBps
    }

    private fun benchmarkStorage(cacheDir: File): Pair<Double, Double> = try {
        val testFile = File(cacheDir, "corev_bench_${System.currentTimeMillis()}.tmp")
        val data = ByteArray(16 * 1024 * 1024) // 16 MB

        var start = System.nanoTime()
        testFile.outputStream().buffered().use { it.write(data) }
        val writeNs = System.nanoTime() - start

        start = System.nanoTime()
        testFile.readBytes()
        val readNs = System.nanoTime() - start

        testFile.delete()
        val mb = data.size.toDouble() / (1024 * 1024)
        val readMBps  = mb / (readNs  / 1_000_000_000.0)
        val writeMBps = mb / (writeNs / 1_000_000_000.0)
        readMBps to writeMBps
    } catch (e: Exception) { 0.0 to 0.0 }
}

data class BenchmarkProgress(
    val task: String = "",
    val progress: Float = 0f,
    val cpuSingleScore: Long = 0L,
    val cpuMultiScore: Long = 0L,
    val memReadMBps: Double = 0.0,
    val memWriteMBps: Double = 0.0,
    val storReadMBps: Double = 0.0,
    val storWriteMBps: Double = 0.0,
    val cpuScore: Int = 0,
    val memoryScore: Int = 0,
    val storageScore: Int = 0,
    val overallScore: Int = 0,
    val isDone: Boolean = false,
)

