package com.saferesale.app.ui.screens.device

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.saferesale.app.ui.components.*

@Composable
fun DeviceScreen(navController: NavController, vm: DeviceViewModel = hiltViewModel()) {
    val info by vm.info.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Identity")
            InfoRow("Device Name",    info.deviceName)
            InfoRow("Manufacturer",   info.manufacturer)
            InfoRow("Model",          info.model)
            InfoRow("Brand",          info.brand)
            InfoRow("Product",        info.product)
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Software")
            InfoRow("Android Version",    info.androidVersion)
            InfoRow("SDK Version",        info.sdkVersion.toString())
            InfoRow("Build Number",       info.buildNumber)
            InfoRow("Security Patch",     info.securityPatch)
            InfoRow("Fingerprint",        info.fingerprint, valueColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Hardware")
            InfoRow("Architecture",   info.architecture)
            InfoRow("Hardware",       info.hardware)
            InfoRow("Bootloader",     info.bootloader)
            InfoRow("Kernel",         info.kernelVersion)
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Display")
            InfoRow("Resolution",     info.screenResolution)
            InfoRow("Density",        "${info.screenDensity} dpi")
            InfoRow("Refresh Rate",   "${info.refreshRate} Hz")
            InfoRow("HDR Support",    if (info.hdrSupport) "Yes" else "No")
        }
    }
}
