package com.saferesale.app.ui.screens.cpu

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.saferesale.app.ui.components.*
import com.saferesale.app.ui.theme.*

@Composable
fun CpuScreen(navController: NavController, vm: CpuViewModel = hiltViewModel()) {
    val info    by vm.info.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("CPU Usage")
            ArcGauge(info.usagePercent, CpuColor, "CPU", Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally), 120.dp, 14.dp)
            Spacer(Modifier.height(12.dp))
            LiveLineGraph(history, CpuColor, modifier = Modifier.fillMaxWidth().height(100.dp), label = "Usage %")
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Processor Info")
            InfoRow("Model",        info.model)
            InfoRow("Architecture", info.architecture)
            InfoRow("Hardware",     info.hardware)
            InfoRow("ABI Support",  info.abi)
            InfoRow("Governor",     info.governor)
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Cores (${info.totalCores} total)")
            info.currentFrequencyMHz.take(info.totalCores).forEachIndexed { i, freq ->
                val maxF = info.maxFrequencyMHz.getOrElse(i) { 1 }.let { if (it == 0L) 1L else it }
                val pct = freq.toFloat() / maxF.toFloat() * 100f
                Spacer(Modifier.height(4.dp))
                ProgressBarRow(
                    label  = "Core $i ${if (info.coreOnline.getOrElse(i) { true }) "" else "⏸ Offline"}",
                    percent = pct,
                    color  = CpuColor,
                    suffix  = if (freq > 0) "${freq} MHz" else "Idle"
                )
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Frequency Range")
            InfoRow("Max Frequency", "${info.maxFrequencyMHz.maxOrNull() ?: 0} MHz")
            InfoRow("Min Frequency", "${info.minFrequencyMHz.minOrNull() ?: 0} MHz")
            InfoRow("Available Cores", info.availableCores.toString())
        }
    }
}
