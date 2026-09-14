package com.saferesale.app.ui.screens.gps

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.saferesale.app.ui.components.*
import com.saferesale.app.ui.theme.TealAccent

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun GpsScreen(navController: NavController, vm: GpsViewModel = hiltViewModel()) {
    val locPerm = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val info    by vm.info.collectAsStateWithLifecycle()
    val isLive  by vm.isListening.collectAsStateWithLifecycle()

    if (!locPerm.status.isGranted) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.LocationOff, null, modifier = Modifier.size(48.dp), tint = TealAccent)
                Spacer(Modifier.height(16.dp))
                Text("Location permission required", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { locPerm.launchPermissionRequest() }) {
                    Text("Grant Permission")
                }
            }
        }
        return
    }

    LaunchedEffect(Unit) { vm.startListening() }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader("GPS Status")
                    StatusChip(if (info.isGpsAvailable) "Active" else "Unavailable", info.isGpsAvailable)
                }
                InfoRow("Provider", info.provider.ifEmpty { "Acquiring…" })
                InfoRow("Satellites", "${info.satelliteCount} (${info.satellites.count { it.usedInFix }} in fix)")
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Location")
                InfoRow("Latitude",  if (info.latitude != 0.0) "${"%.7f".format(info.latitude)}°" else "Acquiring…")
                InfoRow("Longitude", if (info.longitude != 0.0) "${"%.7f".format(info.longitude)}°" else "Acquiring…")
                InfoRow("Accuracy",  "${"%.1f".format(info.accuracyMeters)} m")
                InfoRow("Altitude",  "${"%.1f".format(info.altitudeMeters)} m")
                InfoRow("Speed",     "${"%.2f".format(info.speedMps)} m/s (${"%.1f".format(info.speedMps * 3.6)} km/h)")
                InfoRow("Bearing",   "${"%.1f".format(info.bearingDegrees)}°")
            }
        }
        if (info.satellites.isNotEmpty()) {
            item { SectionHeader("Satellite Details") }
            items(info.satellites.take(20), key = { it.svid }) { sat ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("SVN ${sat.svid}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                        Text(constellationName(sat.constellationType), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Text("CN0: ${"%.1f".format(sat.cn0Dbhz)} dB", style = MaterialTheme.typography.labelSmall)
                        StatusChip(if (sat.usedInFix) "Fix" else "—", sat.usedInFix)
                    }
                    Spacer(Modifier.height(4.dp))
                    ProgressBarRow("Signal", sat.cn0Dbhz.coerceIn(0f, 50f) / 50f * 100f, TealAccent,
                        suffix = "${"%.0f".format(sat.cn0Dbhz)} dB")
                }
            }
        }
    }
}

private fun constellationName(type: Int) = when (type) {
    1 -> "GPS"; 2 -> "SBAS"; 3 -> "GLONASS"; 4 -> "QZSS"
    5 -> "BeiDou"; 6 -> "Galileo"; 7 -> "IRNSS"; else -> "Unknown"
}
