package com.saferesale.app.ui.screens.ram

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
fun RamScreen(navController: NavController, vm: RamViewModel = hiltViewModel()) {
    val info    by vm.info.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Memory Usage")
            ArcGauge(info.usagePercent, RamColor, "RAM", Modifier.align(androidx.compose.ui.Alignment.CenterHorizontally), 120.dp, 14.dp)
            Spacer(Modifier.height(12.dp))
            LiveLineGraph(history, RamColor, modifier = Modifier.fillMaxWidth().height(100.dp), label = "Usage %")
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Memory Details")
            InfoRow("Total RAM",     "${info.totalRamMB} MB")
            InfoRow("Used",          "${info.usedRamMB} MB")
            InfoRow("Available",     "${info.availableRamMB} MB")
            InfoRow("Free",          "${info.freeRamMB} MB")
            InfoRow("Cached",        "${info.cached} MB")
            InfoRow("Buffers",       "${info.buffers} MB")
            InfoRow("Low Memory",    if (info.isLowMemory) "Yes ⚠" else "No")
            InfoRow("LM Threshold",  "${info.threshold} MB")
        }
        if (info.swapTotal > 0) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Swap")
                ProgressBarRow("Swap Used", (info.swapTotal - info.swapFree).toFloat() / info.swapTotal.toFloat() * 100f, RamColor,
                    suffix = "${info.swapTotal - info.swapFree} / ${info.swapTotal} MB")
            }
        }
    }
}
