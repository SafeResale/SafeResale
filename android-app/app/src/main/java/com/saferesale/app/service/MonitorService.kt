package com.saferesale.app.service

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.saferesale.app.MainActivity
import com.saferesale.app.R
import com.saferesale.app.data.battery.BatteryInfoRepository
import com.saferesale.app.data.cpu.CpuInfoRepository
import com.saferesale.app.data.ram.RamInfoRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import javax.inject.Inject

@AndroidEntryPoint
class MonitorService : Service() {

    @Inject lateinit var cpuRepo: CpuInfoRepository
    @Inject lateinit var ramRepo: RamInfoRepository
    @Inject lateinit var batteryRepo: BatteryInfoRepository

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        const val CHANNEL_ID = "corev_monitor_channel"
        const val NOTIF_ID   = 1001
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, buildNotification("Initializing…"))
        startMonitoring()
    }

    private fun startMonitoring() {
        scope.launch {
            cpuRepo.getCpuInfoFlow(2000L).collect { cpu ->
                val ram     = ramRepo.getRamInfoOnce()
                val battery = batteryRepo.getBatteryInfoOnce()
                val text    = "CPU: ${cpu.usagePercent.toInt()}%  RAM: ${ram.usagePercent.toInt()}%  Batt: ${battery.percentage}%"
                val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                nm.notify(NOTIF_ID, buildNotification(text))
            }
        }
    }

    private fun buildNotification(content: String): Notification {
        val intent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CoreV Monitor")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(intent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "System Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "CoreV live system monitoring" }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
