package com.saferesale.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.saferesale.app.data.ApiClient
import com.saferesale.app.diagnostics.DiagnosticsRunner
import com.saferesale.app.diagnostics.DiagTest
import kotlinx.coroutines.launch

@Composable
fun DiagnosticsScreen(token: String?, listingId: String?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var tests by remember { mutableStateOf<List<DiagTest>>(emptyList()) }
    var running by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf<Int?>(null) }
    var msg by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    fun run() {
        // request runtime perms first (R-DIAG-07)
        permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.BLUETOOTH_CONNECT))
        running = true
        scope.launch {
            try {
                val report = DiagnosticsRunner.runAll(ctx, "mobile")
                tests = report.tests
                val passed = tests.count { it.status == "passed" }
                val failed = tests.count { it.status == "failed" }
                val perm = tests.count { it.status == "permission_required" }
                score = (passed * 100 / tests.size.coerceAtLeast(1))
                // sync to backend if listingId + token, else local only (R-DIAG-08 skip adds penalty)
                if (listingId != null && token != null) {
                    try {
                        val body: Map<String, Any> = mapOf(
                            "device" to mapOf("model" to report.device.model, "os_version" to report.device.os_version, "sdk" to report.device.sdk),
                            "category" to report.category,
                            "skipped" to report.skipped,
                            "tests" to report.tests.map { mapOf("id" to it.id, "status" to it.status, "value" to it.value, "unit" to it.unit, "simulated" to it.simulated, "meta" to it.meta) }
                        )
                        val res = ApiClient.service.runDiagnostics(listingId, body, "Bearer $token")
                        val srvScore = (res["diagnostic_score"] as? Number)?.toInt() ?: score
                        msg = "Synced ${tests.size} tests — passed $passed, failed $failed, perm $perm — server score $srvScore/100"
                        score = srvScore
                    } catch (e: Exception) { msg = "Local ${tests.size} tests — passed $passed (sync failed: ${e.message})" }
                } else {
                    msg = "Local ${tests.size} tests — passed $passed, failed $failed, perm $perm (not synced — no listing yet, will penalize risk if skipped)"
                }
            } catch (e: Exception) { msg = "Error: ${e.message}" }
            running = false
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Device Diagnostics", style = MaterialTheme.typography.headlineSmall)
        Text("Real measurements — never simulated (R-DIAG-01). Permissions handled gracefully (R-DIAG-07).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { run() }, enabled = !running) { Text(if (running) "Running..." else "Run Tests") }
            OutlinedButton(onClick = {
                // R-DIAG-08: skipping adds missing_penalty — inform backend
                if (listingId != null && token != null) {
                    scope.launch {
                        try {
                            ApiClient.service.runDiagnostics(listingId, mapOf("skipped" to true, "tests" to emptyList<Any>(), "category" to "mobile", "device" to mapOf("model" to "unknown")), "Bearer $token")
                        } catch (_: Exception) {}
                        onDone()
                    }
                } else onDone()
            }) { Text("Skip (adds risk penalty)") }
        }
        if (score != null) {
            Card(Modifier.fillMaxWidth().padding(top = 12.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Text("Score: $score/100  ${if ((score?:0) >= 85) "Good" else if ((score?:0) >= 50) "Moderate" else "Defective"}", modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.titleMedium)
            }
        }
        if (msg.isNotEmpty()) Text(msg, modifier = Modifier.padding(top = 8.dp))
        // Touch grid (9 cells)
        Text("Touch Grid — tap all 9 cells", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
        TouchGrid()
        // Results
        LazyColumn(Modifier.weight(1f).padding(top = 12.dp)) {
            items(tests) { t ->
                val color = when (t.status) {
                    "passed" -> MaterialTheme.colorScheme.primary
                    "failed" -> MaterialTheme.colorScheme.error
                    "permission_required" -> MaterialTheme.colorScheme.tertiary
                    "unsupported","unavailable" -> MaterialTheme.colorScheme.outline
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                ListItem(
                    headlineContent = { Text(t.id) },
                    supportingContent = { Text("${t.status} ${t.value ?: ""} ${t.unit ?: ""} ${t.meta?.get("method") ?: ""}") },
                    trailingContent = { Badge(containerColor = color) { Text(t.status) } }
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun TouchGrid() {
    var covered by remember { mutableStateOf(setOf<Int>()) }
    Column {
        for (r in 0..2) Row {
            for (c in 0..2) {
                val idx = r*3+c
                val done = idx in covered
                Card(
                    onClick = { covered = covered + idx },
                    modifier = Modifier.weight(1f).padding(4.dp).height(60.dp),
                    colors = CardDefaults.cardColors(containerColor = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                ) { Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { Text(if (done) "✓" else "${r},${c}") } }
            }
        }
        Text("${covered.size}/9 cells covered", style = MaterialTheme.typography.bodySmall)
    }
}
