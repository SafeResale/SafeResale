package com.saferesale.app.ui.screens.sensors

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.saferesale.app.ui.components.*
import com.saferesale.app.ui.theme.ElectricViolet

@Composable
fun SensorsScreen(navController: NavController, vm: SensorsViewModel = hiltViewModel()) {
    val sensors    = vm.availableSensors
    val liveValues by vm.liveValues.collectAsStateWithLifecycle()
    val isListening by vm.isListening.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.startListening() }
    DisposableEffect(Unit) { onDispose { vm.stopListening() } }

    Column(Modifier.fillMaxSize()) {
        // Header row
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("${sensors.size} Sensors Detected", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            StatusChip(if (isListening) "LIVE" else "PAUSED", isListening)
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(sensors, key = { index, sensor -> sensor.uniqueId.ifEmpty { "${sensor.type}_$index" } }) { _, sensor ->
                val live = liveValues[sensor.type]
                var expanded by remember { mutableStateOf(false) }

                GlassCard(
                    Modifier.fillMaxWidth().clickable { expanded = !expanded }
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(sensor.typeName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                            Text(sensor.name, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                        if (live != null && live.values.isNotEmpty()) {
                            Text(
                                "${"%+.3f".format(live.values.first())} ${sensor.unit}",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = ElectricViolet, fontWeight = FontWeight.Bold)
                            )
                        }
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            null, modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }

                    AnimatedVisibility(expanded) {
                        Column(Modifier.padding(top = 12.dp)) {
                            live?.values?.forEachIndexed { i, v ->
                                val axis = listOf("X", "Y", "Z", "W").getOrElse(i) { "$i" }
                                InfoRow("$axis", "%+.5f ${sensor.unit}".format(v))
                            }
                            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            InfoRow("Vendor",      sensor.vendor)
                            InfoRow("Max Range",   "${sensor.maxRange} ${sensor.unit}")
                            InfoRow("Resolution",  "${sensor.resolution} ${sensor.unit}")
                            InfoRow("Power",       "${sensor.power} mA")
                            InfoRow("Min Delay",   "${sensor.minDelay} μs")
                        }
                    }
                }
            }
        }
    }
}
