package com.saferesale.app.ui.screens.storage

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

private fun Long.toGB() = "%.2f GB".format(this.toFloat() / (1024 * 1024 * 1024))

@Composable
fun StorageScreen(navController: NavController, vm: StorageViewModel = hiltViewModel()) {
    val info by vm.info.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Internal Storage")
            Spacer(Modifier.height(4.dp))
            ProgressBarRow("Used", info.internalUsagePercent, StorageColor,
                suffix = "${info.internalUsed.toGB()} / ${info.internalTotal.toGB()}")
            Spacer(Modifier.height(8.dp))
            InfoRow("Total",    info.internalTotal.toGB())
            InfoRow("Used",     info.internalUsed.toGB())
            InfoRow("Free",     info.internalFree.toGB())
        }
        if (info.externalAvailable) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("External / SD Card")
                Spacer(Modifier.height(4.dp))
                ProgressBarRow("Used", info.externalUsagePercent, AmberWarm,
                    suffix = "${info.externalUsed.toGB()} / ${info.externalTotal.toGB()}")
                Spacer(Modifier.height(8.dp))
                InfoRow("Total",    info.externalTotal.toGB())
                InfoRow("Free",     info.externalFree.toGB())
            }
        }
        if (info.partitions.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Partitions")
                info.partitions.forEach { part ->
                    Spacer(Modifier.height(8.dp))
                    Text(part.mountPoint, style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text("${part.fileSystem} · ${part.total.toGB()}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    Spacer(Modifier.height(4.dp))
                    ProgressBarRow("", part.usagePercent, StorageColor,
                        suffix = "${part.used.toGB()} used")
                }
            }
        }
    }
}
