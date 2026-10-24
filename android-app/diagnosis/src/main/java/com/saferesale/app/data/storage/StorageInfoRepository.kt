package com.saferesale.app.data.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import com.saferesale.app.domain.model.StorageInfo
import com.saferesale.app.domain.model.StoragePartition
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageInfoRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun getStorageInfo(): StorageInfo {
        val internalStat = StatFs(Environment.getDataDirectory().path)
        val internalTotal = internalStat.totalBytes
        val internalFree  = internalStat.availableBytes
        val internalUsed  = internalTotal - internalFree
        val internalPct   = if (internalTotal > 0) internalUsed.toFloat() / internalTotal.toFloat() * 100f else 0f

        val sdCard = getExternalStorageInfo()
        val partitions = readMounts()

        return StorageInfo(
            internalTotal         = internalTotal,
            internalUsed          = internalUsed,
            internalFree          = internalFree,
            internalUsagePercent  = internalPct,
            externalAvailable     = sdCard != null,
            externalTotal         = sdCard?.first ?: 0L,
            externalUsed          = (sdCard?.first ?: 0L) - (sdCard?.second ?: 0L),
            externalFree          = sdCard?.second ?: 0L,
            externalUsagePercent  = sdCard?.let {
                if (it.first > 0) (it.first - it.second).toFloat() / it.first.toFloat() * 100f
                else 0f
            } ?: 0f,
            partitions            = partitions,
        )
    }

    private fun getExternalStorageInfo(): Pair<Long, Long>? {
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
        return try {
            val dirs = context.getExternalFilesDirs(null)
            dirs.filterNotNull()
                .filter { Environment.isExternalStorageRemovable(it) }
                .firstOrNull()
                ?.let { dir ->
                    val stat = StatFs(dir.path)
                    stat.totalBytes to stat.availableBytes
                }
        } catch (e: Exception) { null }
    }

    private fun readMounts(): List<StoragePartition> {
        val result = mutableListOf<StoragePartition>()
        try {
            File("/proc/mounts").readLines()
                .filter { !it.startsWith("none") && !it.startsWith("proc") }
                .forEach { line ->
                    val parts = line.split(Regex("\\s+"))
                    val mountPoint = parts.getOrNull(1) ?: return@forEach
                    val fsType     = parts.getOrNull(2) ?: return@forEach
                    if (fsType in listOf("ext4", "f2fs", "vfat", "sdcardfs", "fuse")) {
                        try {
                            val stat = StatFs(mountPoint)
                            val total = stat.totalBytes
                            val free  = stat.availableBytes
                            val used  = total - free
                            if (total > 0) {
                                result.add(StoragePartition(
                                    mountPoint    = mountPoint,
                                    fileSystem    = fsType,
                                    total         = total,
                                    free          = free,
                                    used          = used,
                                    usagePercent  = used.toFloat() / total.toFloat() * 100f,
                                ))
                            }
                        } catch (_: Exception) {}
                    }
                }
        } catch (_: Exception) {}
        return result.distinctBy { it.mountPoint }
    }
}
