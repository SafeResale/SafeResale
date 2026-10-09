package com.saferesale.app.ui.screens.network

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.saferesale.app.ui.components.*
import com.saferesale.app.ui.theme.*

@Composable
fun NetworkScreen(navController: NavController, vm: NetworkViewModel = hiltViewModel()) {
    val info by vm.info.collectAsStateWithLifecycle()
    val pubIp by vm.publicIp.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Status card
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Connection")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(info.activeNetworkType, info.isWifiConnected || info.isMobileConnected)
                if (info.roaming) StatusChip("Roaming", false)
            }
        }

        if (info.isWifiConnected) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Wi-Fi")
                InfoRow("SSID",         info.ssid.ifEmpty { "<hidden>" })
                InfoRow("BSSID",        info.bssid.ifEmpty { "Restricted" })
                InfoRow("Signal",       "${info.signalStrengthDbm} dBm")
                InfoRow("Link Speed",   "${info.linkSpeedMbps} Mbps")
                InfoRow("Frequency",    "${info.frequencyMHz} MHz (Ch ${info.wifiChannel})")
                InfoRow("Standard",     info.wifiNetworkType)
                InfoRow("IP",           info.wifiIpAddress)
                InfoRow("Gateway",      info.gateway)
                InfoRow("DNS 1",        info.dns1)
                InfoRow("DNS 2",        info.dns2)
            }
        }

        if (info.isMobileConnected) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Mobile Network")
                InfoRow("Carrier",       info.carrier)
                InfoRow("SIM Status",    info.simStatus)
                InfoRow("Network Type",  info.mobileNetworkType)
                InfoRow("MCC",           info.mcc)
                InfoRow("MNC",           info.mnc)
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("IP Addresses")
            InfoRow("IPv4 (Local)",  info.ipv4Address.ifEmpty { "Unknown" })
            InfoRow("IPv6 (Local)",  info.ipv6Address.ifEmpty { "Unknown" })
            InfoRow("Public IP",     pubIp.ifEmpty { "Fetching…" })
            InfoRow("DNS Server",    info.dns1.ifEmpty { "Unknown" })
        }
    }
}
