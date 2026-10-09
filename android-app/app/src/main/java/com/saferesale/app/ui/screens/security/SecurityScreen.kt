package com.saferesale.app.ui.screens.security

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.saferesale.app.ui.components.*
import com.saferesale.app.ui.theme.LimeGreen

@Composable
fun SecurityScreen(navController: NavController, vm: SecurityViewModel = hiltViewModel()) {
    val info by vm.info.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Security Patch")
            Text(info.securityPatchLevel,
                style = MaterialTheme.typography.titleMedium, color = LimeGreen)
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Biometric Authentication")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip("Fingerprint", info.hasFingerprint)
                StatusChip("Face Unlock", info.hasFaceUnlock)
                StatusChip("Iris",        if (!info.hasIrisUnlock) null else true)
            }
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Device Security")
            InfoRow("Device Secure",         if (info.isDeviceSecure) "Yes ✓" else "No ✗")
            InfoRow("Encrypted",             if (info.isEncrypted) "Yes ✓" else "No ✗")
            InfoRow("Verified Boot",         info.verifiedBootState)
            InfoRow("Root Detected",         if (info.isRooted) "Yes ⚠" else "No")
            InfoRow("ADB Enabled",           if (info.isAdbEnabled) "Yes ⚠" else "No")
            InfoRow("Dev Settings",          if (info.developmentSettingsEnabled) "Enabled ⚠" else "Disabled")
        }
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Device ID")
            Text("Android ID", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Spacer(Modifier.height(4.dp))
            SelectionContainer {
                Text(info.androidId, style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
            }
        }
    }
}
