package com.saferesale.app.ui.screens.camera

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.CameraRear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.saferesale.app.ui.components.*
import com.saferesale.app.ui.theme.SkyBlue

@Composable
fun CameraScreen(navController: NavController, vm: CameraViewModel = hiltViewModel()) {
    val info by vm.info.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (info.cameras.isEmpty()) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("No cameras detected", style = MaterialTheme.typography.bodyMedium)
            }
        }
        info.cameras.forEach { cam ->
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(if (cam.facing == "Front") Icons.Default.CameraFront else Icons.Default.CameraRear,
                        null, tint = SkyBlue, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("${cam.facing} Camera (ID: ${cam.id})",
                        style = MaterialTheme.typography.titleMedium, color = SkyBlue)
                }
                Spacer(Modifier.height(12.dp))
                InfoRow("Resolution",       "${cam.maxWidth} × ${cam.maxHeight} px")
                InfoRow("Megapixels",       "${cam.megaPixels} MP")
                InfoRow("Hardware Level",   cam.hardwareLevel)
                InfoRow("Flash",            if (cam.hasFlash) "Available" else "None")
                InfoRow("Autofocus",        if (cam.hasAutoFocus) "Yes" else "No")
                InfoRow("OIS",              if (cam.hasOIS) "Yes" else "No")
                InfoRow("Max Zoom",         "${cam.digitalZoomMax}x")
                if (cam.focalLengths.isNotEmpty())
                    InfoRow("Focal Lengths", cam.focalLengths.joinToString { "${"%.1f".format(it)} mm" })
                if (cam.apertureList.isNotEmpty())
                    InfoRow("Apertures",    cam.apertureList.joinToString { "f/${"%.1f".format(it)}" })
                if (cam.supportedFps.isNotEmpty())
                    InfoRow("Supported FPS",cam.supportedFps.joinToString())
                if (cam.supportedFormats.isNotEmpty())
                    InfoRow("Formats",      cam.supportedFormats.joinToString())
            }
        }
    }
}
