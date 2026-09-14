package com.saferesale.app.data.battery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.saferesale.app.domain.model.BatteryInfo
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOf

class BatteryInfoRepository constructor(
    private val context: Context
) {
    private val batteryManager by lazy {
        context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
    }

    fun getBatteryInfoFlow(): Flow<BatteryInfo> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                trySend(buildBatteryInfo(intent))
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        context.registerReceiver(receiver, filter)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    fun getBatteryInfoOnce(): BatteryInfo {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return buildBatteryInfo(intent)
    }

    private fun buildBatteryInfo(intent: Intent?): BatteryInfo {
        val level    = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale    = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val pct      = if (level >= 0 && scale > 0) level * 100 / scale else 0

        val status   = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharg  = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                       status == BatteryManager.BATTERY_STATUS_FULL

        val plugged  = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val chargeMethod = when (plugged) {
            BatteryManager.BATTERY_PLUGGED_AC      -> "AC Charger"
            BatteryManager.BATTERY_PLUGGED_USB     -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS-> "Wireless"
            else -> if (isCharg) "Unknown" else "Not charging"
        }

        val tempRaw  = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val voltage  = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0

        val health = when (intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)) {
            BatteryManager.BATTERY_HEALTH_GOOD          -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT      -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD          -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE  -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_COLD          -> "Cold"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            else -> "Unknown"
        }

        val statusStr = when (status) {
            BatteryManager.BATTERY_STATUS_CHARGING    -> "Charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
            BatteryManager.BATTERY_STATUS_FULL        -> "Full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING-> "Not Charging"
            else -> "Unknown"
        }

        val currentNow = try {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        } catch (e: Exception) { 0 }

        val capacity = try {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        } catch (e: Exception) { 0 }

        return BatteryInfo(
            percentage      = pct,
            isCharging      = isCharg,
            chargingMethod  = chargeMethod,
            temperatureCelsius = tempRaw / 10f,
            voltageMilliVolts  = voltage,
            currentMicroAmps   = currentNow,
            capacityMah        = capacity / 1000,
            health          = health,
            technology      = intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "",
            status          = statusStr,
            plugged         = plugged,
        )
    }
}