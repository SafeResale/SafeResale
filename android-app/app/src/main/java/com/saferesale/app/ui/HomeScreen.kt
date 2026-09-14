package com.saferesale.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saferesale.app.data.ApiClient
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(onStartCheck: () -> Unit, onBench: () -> Unit = {}, token: String?) {
    var health by remember { mutableStateOf("loading...") }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try { health = ApiClient.service.health().toString() } catch (e: Exception) { health = "down: ${e.message}" }
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Home", style = MaterialTheme.typography.headlineMedium)
        Text("Health: $health", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("8-Angle Capture", style = MaterialTheme.typography.titleMedium)
                Text("Front, back, left, right, top, bottom, 45° front/back — CameraX + blur/luminance/hash on-device", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onStartCheck, enabled = token != null) { Text("Start device check →") }
                if (token == null) Text("Login first", color = MaterialTheme.colorScheme.error)
            }
        }
        Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Device Diagnostics (CoreV bench)", style = MaterialTheme.typography.titleMedium)
                Text("Walks the 18-module CoreV bench — battery mA/mV/°C, CPU/RAM/storage, 40+ sensors, network, camera, audio, GPS, benchmark — then syncs the results as the device score (R-DIAG-01..07)", style = MaterialTheme.typography.bodySmall)
                Text("Skipping adds risk penalty (R-DIAG-08); unsupported sensors are not penalized.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Button(onClick = onBench) { Text("Open Hardware Bench (18 modules)") }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            scope.launch {
                try {
                    val auth = "Bearer $token"
                    val draft = ApiClient.service.createDraft(com.saferesale.app.data.DraftReq("mobile", "Demo iPhone", 45000.0), auth)
                    val id = (draft["listing"] as Map<*, *>)["_id"] as String
                    ApiClient.service.runVision(id, auth)
                    val risk = ApiClient.service.computeRisk(id, auth)
                    health = "Risk: $risk"
                } catch (e: Exception) { health = "Error: ${e.message}" }
            }
        }) { Text("Test: create draft → run-vision → compute-risk") }
        Text(health, modifier = Modifier.padding(top = 8.dp))
    }
}
