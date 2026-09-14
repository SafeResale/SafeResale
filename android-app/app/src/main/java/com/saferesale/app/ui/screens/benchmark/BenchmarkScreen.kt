package com.saferesale.app.ui.screens.benchmark

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.saferesale.app.ui.components.*
import com.saferesale.app.ui.theme.*

@Composable
fun BenchmarkScreen(navController: NavController, vm: BenchmarkViewModel = hiltViewModel()) {
    val progress  by vm.progress.collectAsStateWithLifecycle()
    val isRunning by vm.isRunning.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Disclaimer
        GlassCard(Modifier.fillMaxWidth()) {
            Text(
                "⚡ CoreV Internal Benchmark",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = AmberWarm)
            )
            Spacer(Modifier.height(4.dp))
            Text("This is CoreV's proprietary benchmark. Results are not comparable to industry-standard suites.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }

        // Start button
        Button(
            onClick = vm::startBenchmark,
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AmberWarm),
        ) {
            if (isRunning) {
                CircularProgressIndicator(Modifier.size(20.dp), color = Color.Black, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text(progress.task, color = Color.Black)
            } else {
                Icon(Icons.Default.PlayArrow, null, tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("Start Benchmark", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }

        if (isRunning) {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Progress")
                LinearProgressIndicator(progress = { progress.progress }, modifier = Modifier.fillMaxWidth(),
                    color = AmberWarm, trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Spacer(Modifier.height(8.dp))
                Text(progress.task, style = MaterialTheme.typography.bodySmall, color = AmberWarm)
            }
        }

        if (progress.isDone) {
            // Overall score card
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Overall Score")
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Brush.linearGradient(listOf(ElectricViolet.copy(alpha = 0.3f), AmberWarm.copy(alpha = 0.3f))))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${progress.overallScore}", style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.Bold, color = AmberWarm))
                }
            }
            // Per-category scores
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ScoreCard("CPU", progress.cpuScore, CpuColor, Modifier.weight(1f))
                ScoreCard("Memory", progress.memoryScore, RamColor, Modifier.weight(1f))
                ScoreCard("Storage", progress.storageScore, StorageColor, Modifier.weight(1f))
            }
            // Detailed stats
            GlassCard(Modifier.fillMaxWidth()) {
                SectionHeader("Detailed Metrics")
                InfoRow("CPU Single-Core", "${progress.cpuSingleScore / 1_000_000} ms (prime sieve)")
                InfoRow("CPU Multi-Core",  "${progress.cpuMultiScore / 1_000_000} ms (parallel)")
                InfoRow("Memory Read",     "${"%.0f".format(progress.memReadMBps)} MB/s")
                InfoRow("Memory Write",    "${"%.0f".format(progress.memWriteMBps)} MB/s")
                InfoRow("Storage Read",    "${"%.0f".format(progress.storReadMBps)} MB/s")
                InfoRow("Storage Write",   "${"%.0f".format(progress.storWriteMBps)} MB/s")
            }
        }
    }
}

@Composable
fun ScoreCard(label: String, score: Int, color: Color, modifier: Modifier = Modifier) {
    GlassCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Spacer(Modifier.height(4.dp))
        Text(score.toString(), style = MaterialTheme.typography.headlineSmall.copy(
            fontWeight = FontWeight.Bold, color = color))
    }
}
