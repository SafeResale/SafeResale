package com.saferesale.app.ui.screens.hardwaretest

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.saferesale.app.ui.theme.*

@Composable
fun HardwareTestScreen(navController: NavController, vm: HardwareTestViewModel = hiltViewModel()) {
    val tests by vm.tests.collectAsStateWithLifecycle()
    val passCount = tests.count { it.status == TestStatus.PASS }
    val failCount = tests.count { it.status == TestStatus.FAIL }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // Summary row
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Hardware Tests", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text("$passCount Pass  ·  $failCount Fail  ·  ${tests.size - passCount - failCount} Pending",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            Button(onClick = vm::runAll, colors = ButtonDefaults.buttonColors(containerColor = CoralRed)) {
                Text("Run All", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement   = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(tests, key = { it.id }) { test ->
                TestTile(test) { vm.runTest(test.id) }
            }
        }
    }
}

@Composable
fun TestTile(test: HardwareTestItem, onRun: () -> Unit) {
    val bg = when (test.status) {
        TestStatus.IDLE    -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        TestStatus.RUNNING -> ElectricViolet.copy(alpha = 0.15f)
        TestStatus.PASS    -> LimeGreen.copy(alpha = 0.15f)
        TestStatus.FAIL    -> CoralRed.copy(alpha = 0.15f)
    }
    val iconColor = when (test.status) {
        TestStatus.PASS -> LimeGreen; TestStatus.FAIL -> CoralRed
        TestStatus.RUNNING -> ElectricViolet; else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    }
    val spin = rememberInfiniteTransition(label = "spin")
    val rotation by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1000)), label = "rot")

    Card(
        onClick = onRun,
        colors = CardDefaults.cardColors(containerColor = bg),
        shape  = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(100.dp)
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                when (test.status) {
                    TestStatus.PASS    -> Icons.Default.CheckCircle
                    TestStatus.FAIL    -> Icons.Default.Cancel
                    TestStatus.RUNNING -> Icons.Default.Refresh
                    else -> Icons.Default.RadioButtonUnchecked
                },
                null,
                tint = iconColor,
                modifier = Modifier.size(24.dp).let { if (test.status == TestStatus.RUNNING) it.rotate(rotation) else it }
            )
            Text(test.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            Text(
                when (test.status) {
                    TestStatus.IDLE    -> "Tap to test"
                    TestStatus.RUNNING -> "Testing…"
                    TestStatus.PASS    -> "✓ Pass"
                    TestStatus.FAIL    -> "✗ Not available"
                },
                style = MaterialTheme.typography.labelSmall,
                color = iconColor,
            )
        }
    }
}
