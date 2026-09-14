package com.saferesale.app.ui.screens.battery

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.saferesale.app.ui.components.*
import com.saferesale.app.ui.theme.*

@Composable
fun BatteryScreen(navController: NavController, vm: BatteryViewModel = hiltViewModel()) {
    val info    by vm.info.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()

    val battColor = when {
        info.percentage > 60 -> BatteryColor
        info.percentage > 20 -> AmberWarm
        else -> CoralRed
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Battery Status")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center) {
                ArcGauge(info.percentage.toFloat(), battColor, if (info.isCharging) "Charging" else "Battery",
                    Modifier, 140.dp, 16.dp)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatusChip(info.status, info.status == "Full" || info.isCharging)
                StatusChip(info.health, info.health == "Good")
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("History")
            LiveLineGraph(history, battColor, modifier = Modifier.fillMaxWidth().height(80.dp),
                label = "Level %", maxValue = 100f)
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Details")
            InfoRow("Charge Level",     "${info.percentage}%")
            InfoRow("Charging Method",  info.chargingMethod)
            InfoRow("Temperature",      "${info.temperatureCelsius}°C",
                valueColor = if (info.temperatureCelsius > 45f) CoralRed else BatteryColor)
            InfoRow("Voltage",          "${info.voltageMilliVolts} mV")
            if (info.currentMicroAmps != 0)
                InfoRow("Current",      "${info.currentMicroAmps / 1000} mA")
            if (info.capacityMah > 0)
                InfoRow("Capacity",     "${info.capacityMah} mAh")
            InfoRow("Technology",       info.technology)
        }
    }
}
