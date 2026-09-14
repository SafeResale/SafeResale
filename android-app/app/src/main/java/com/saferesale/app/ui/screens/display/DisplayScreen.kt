package com.saferesale.app.ui.screens.display

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

@Composable
fun DisplayScreen(navController: NavController, vm: DisplayViewModel = hiltViewModel()) {
    val info by vm.info.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Resolution & Size")
            InfoRow("Resolution",       "${info.widthPx} × ${info.heightPx} px")
            InfoRow("Density",          "${info.densityDpi} dpi")
            InfoRow("xDPI / yDPI",      "${"%.1f".format(info.xdpi)} / ${"%.1f".format(info.ydpi)}")
            InfoRow("Physical Size",    "${"%.1f".format(info.diagonalInch)}\" diagonal")
            InfoRow("Physical Width",   "${"%.2f".format(info.physicalWidthInch)}\"")
            InfoRow("Physical Height",  "${"%.2f".format(info.physicalHeightInch)}\"")
            InfoRow("Orientation",      info.orientation)
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Performance")
            InfoRow("Refresh Rate",         "${"%.0f".format(info.refreshRateHz)} Hz")
            InfoRow("Supported Rates",      info.supportedRefreshRates.joinToString { "${"%.0f".format(it)} Hz" })
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Color & HDR")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("HDR", if (info.hasHdr) true else null)
                StatusChip("Wide Color", if (info.hasWideColorGamut) true else null)
            }
            if (info.hdrCapabilities.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                info.hdrCapabilities.forEach { h ->
                    Text("• $h", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (info.brightnessPercent > 0) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Brightness")
                ProgressBarRow("Screen Brightness", info.brightnessPercent,
                    MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
