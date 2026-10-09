package com.saferesale.app.ui.screens.dashboard

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.saferesale.app.ui.components.*
import com.saferesale.app.ui.navigation.Screen
import com.saferesale.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    navController: NavController,
    vm: DashboardViewModel = hiltViewModel()
) {
    val cpu     by vm.cpu.collectAsStateWithLifecycle()
    val ram     by vm.ram.collectAsStateWithLifecycle()
    val battery by vm.battery.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()
    val network by vm.network.collectAsStateWithLifecycle()
    val cpuHist by vm.cpuHistory.collectAsStateWithLifecycle()
    val ramHist by vm.ramHistory.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // ── Live Time ──────────────────────────────────────────────────────────
        var time by remember { mutableStateOf("") }
        LaunchedEffect(Unit) {
            while (true) {
                time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                kotlinx.coroutines.delay(1000)
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("System Monitor", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            // Live indicator
            val pulseAnim = rememberInfiniteTransition(label = "pulse")
            val scale by pulseAnim.animateFloat(1f, 1.3f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "scale")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).scale(scale).clip(CircleShape).background(Color(0xFF4CAF50)))
                Spacer(Modifier.width(6.dp))
                Text("LIVE", style = MaterialTheme.typography.labelSmall.copy(
                    color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold))
            }
        }

        // ── Gauge Row ──────────────────────────────────────────────────────────
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Performance Overview")
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ArcGauge(cpu.usagePercent, CpuColor, "CPU", Modifier.weight(1f), 68.dp, 8.dp)
                ArcGauge(ram.usagePercent, RamColor, "RAM", Modifier.weight(1f), 68.dp, 8.dp)
                ArcGauge(storage.internalUsagePercent, StorageColor, "Disk", Modifier.weight(1f), 68.dp, 8.dp)
                ArcGauge(battery.percentage.toFloat(), BatteryColor, "Batt", Modifier.weight(1f), 68.dp, 8.dp)
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Live CPU Graph ─────────────────────────────────────────────────────
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("CPU Usage")
            Spacer(Modifier.height(4.dp))
            LiveLineGraph(
                values = cpuHist,
                color  = CpuColor,
                label  = "CPU",
                modifier = Modifier.fillMaxWidth().height(80.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${cpu.totalCores} Cores · ${cpu.governor}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Text("${cpu.currentFrequencyMHz.maxOrNull() ?: 0} MHz",
                    style = MaterialTheme.typography.bodySmall.copy(color = CpuColor, fontWeight = FontWeight.Bold))
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── RAM Graph ──────────────────────────────────────────────────────────
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Memory Usage")
            Spacer(Modifier.height(4.dp))
            LiveLineGraph(
                values = ramHist,
                color  = RamColor,
                label  = "RAM",
                modifier = Modifier.fillMaxWidth().height(80.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${ram.usedRamMB} MB / ${ram.totalRamMB} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                if (ram.isLowMemory)
                    StatusChip("LOW MEMORY", false)
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Status Cards ───────────────────────────────────────────────────────
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Battery card
            GlassCard(Modifier.weight(1f)) {
                Icon(
                    if (battery.isCharging) Icons.Default.BatteryChargingFull else Icons.Default.BatteryFull,
                    null, tint = BatteryColor, modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text("${battery.percentage}%", style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold, color = BatteryColor))
                Text(battery.chargingMethod, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Text("${battery.temperatureCelsius}°C",
                    style = MaterialTheme.typography.bodySmall.copy(color = TempColor))
            }
            // Network card
            GlassCard(Modifier.weight(1f)) {
                Icon(
                    if (network.isWifiConnected) Icons.Default.Wifi else Icons.Default.SignalCellularAlt,
                    null, tint = NetworkColor, modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(network.activeNetworkType,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = NetworkColor))
                Text(network.ssid.ifEmpty { network.carrier },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), maxLines = 1)
                Text(network.ipv4Address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Storage Bar ────────────────────────────────────────────────────────
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Storage")
            Spacer(Modifier.height(8.dp))
            ProgressBarRow(
                label = "Internal",
                percent = storage.internalUsagePercent,
                color = StorageColor,
                suffix = "${storage.internalUsed / (1024 * 1024 * 1024L)} GB / ${storage.internalTotal / (1024 * 1024 * 1024L)} GB"
            )
            if (storage.externalAvailable) {
                Spacer(Modifier.height(8.dp))
                ProgressBarRow(
                    label = "SD Card",
                    percent = storage.externalUsagePercent,
                    color = AmberWarm,
                    suffix = "${storage.externalUsed / (1024 * 1024 * 1024L)} GB / ${storage.externalTotal / (1024 * 1024 * 1024L)} GB"
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Quick Access Grid ──────────────────────────────────────────────────
        SectionHeader("Quick Access")
        Spacer(Modifier.height(8.dp))

        data class QuickItem(val label: String, val icon: ImageVector, val color: Color, val route: String)
        val quickItems = listOf(
            QuickItem("Sensors",    Icons.Default.Sensors,      ElectricViolet, Screen.Sensors.route),
            QuickItem("Net Tools",  Icons.Default.NetworkCheck, TealAccent,     Screen.NetworkTools.route),
            QuickItem("Camera",     Icons.Default.CameraAlt,    SkyBlue,        Screen.Camera.route),
            QuickItem("Security",   Icons.Default.Security,     LimeGreen,      Screen.Security.route),
            QuickItem("Benchmark",  Icons.Default.Speed,        AmberWarm,      Screen.Benchmark.route),
            QuickItem("HW Tests",   Icons.Default.BugReport,    CoralRed,       Screen.HardwareTest.route),
            QuickItem("GPS",        Icons.Default.LocationOn,   TealAccent,     Screen.Gps.route),
            QuickItem("Export",     Icons.Default.FileDownload, ElectricViolet, Screen.Export.route),
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
            userScrollEnabled = false,
        ) {
            items(quickItems) { item ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(item.color.copy(alpha = 0.1f))
                        .clickable { navController.navigate(item.route) }
                        .padding(8.dp)
                ) {
                    Icon(item.icon, null, tint = item.color, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(item.label, style = MaterialTheme.typography.labelSmall.copy(color = item.color),
                        maxLines = 1, fontSize = 9.sp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ── Creator Credit ─────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            ElectricViolet.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            ElectricViolet.copy(alpha = 0.4f),
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(vertical = 12.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Code,
                    contentDescription = null,
                    tint = ElectricViolet,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Created by ",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    text = "Fahath0x",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = ElectricViolet
                    )
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
