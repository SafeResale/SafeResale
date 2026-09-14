package com.saferesale.app.ui.screens.audio

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
fun AudioScreen(navController: NavController, vm: AudioViewModel = hiltViewModel()) {
    val info by vm.info.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Capabilities")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("Speaker",    if (info.hasSpeaker) true else null)
                StatusChip("Microphone", if (info.hasMicrophone) true else null)
                StatusChip("Bluetooth",  if (info.hasBluetoothAudio) true else null)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("Headphones", if (info.hasHeadphones) true else null)
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Audio System")
            InfoRow("Native Sample Rate",   "${info.nativeOutputSampleRate} Hz")
            InfoRow("Native Buffer Size",    "${info.nativeOutputFramesPerBuffer} frames")
            InfoRow("Volume",               "${info.currentVolume} / ${info.maxVolume}")
            InfoRow("Muted",                if (info.isMuted) "Yes" else "No")
        }
        if (info.supportedEncodings.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Supported Encodings")
                info.supportedEncodings.forEach { enc ->
                    Text("• $enc", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
        if (info.audioOutputDevices.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Output Devices")
                info.audioOutputDevices.forEach { dev ->
                    InfoRow(dev.type, "${dev.name} · ${dev.sampleRates.firstOrNull() ?: 0} Hz")
                }
            }
        }
        if (info.audioInputDevices.isNotEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Input Devices")
                info.audioInputDevices.forEach { dev ->
                    InfoRow(dev.type, dev.name)
                }
            }
        }
    }
}
