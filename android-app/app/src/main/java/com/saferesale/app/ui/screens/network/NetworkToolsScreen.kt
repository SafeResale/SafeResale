package com.saferesale.app.ui.screens.network

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.saferesale.app.ui.components.GlassCard
import com.saferesale.app.ui.components.SectionHeader
import com.saferesale.app.ui.components.StatusChip
import com.saferesale.app.ui.theme.TealAccent

@Composable
fun NetworkToolsScreen(navController: NavController, vm: NetworkToolsViewModel = hiltViewModel()) {
    val result     by vm.result.collectAsStateWithLifecycle()
    val isRunning  by vm.isRunning.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Ping", "DNS", "HTTP", "Trace", "Connectivity")

    var inputText by remember { mutableStateOf("8.8.8.8") }
    val focusManager = LocalFocusManager.current

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // Tab row
        ScrollableTabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
            tabs.forEachIndexed { i, title ->
                Tab(selected = selectedTab == i, onClick = {
                    selectedTab = i
                    inputText = when (i) {
                        0 -> "8.8.8.8"; 1 -> "google.com"; 2 -> "https://google.com"
                        3 -> "8.8.8.8"; else -> ""
                    }
                }) {
                    Text(title, modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (selectedTab != 4) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                label = { Text(when (selectedTab) { 0, 3 -> "Host/IP"; 1 -> "Domain"; 2 -> "URL"; else -> "Input" }) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { focusManager.clearFocus(); runTool(selectedTab, inputText, vm) }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = TealAccent),
            )
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = {
                focusManager.clearFocus()
                if (selectedTab == 4) vm.checkNet() else runTool(selectedTab, inputText, vm)
            },
            enabled = !isRunning,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
        ) {
            if (isRunning)
                CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.background, strokeWidth = 2.dp)
            else
                Text(if (selectedTab == 4) "Check Connectivity" else "Run Test")
        }

        Spacer(Modifier.height(16.dp))

        result?.let { r ->
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()) {
                    SectionHeader("Result")
                    StatusChip(if (r.success) "Success" else "Failed", r.success)
                }
                Spacer(Modifier.height(8.dp))
                Text(r.resultText,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                )
            }
        }
    }
}

private fun runTool(tab: Int, input: String, vm: NetworkToolsViewModel) {
    when (tab) {
        0 -> vm.ping(input)
        1 -> vm.dns(input)
        2 -> vm.http(input)
        3 -> vm.traceroute(input)
    }
}
