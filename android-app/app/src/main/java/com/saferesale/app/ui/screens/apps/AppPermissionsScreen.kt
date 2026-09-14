package com.saferesale.app.ui.screens.apps

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.saferesale.app.domain.model.AppPermission
import com.saferesale.app.ui.components.GlassCard
import com.saferesale.app.ui.components.SectionHeader
import com.saferesale.app.ui.theme.CoralRed
import com.saferesale.app.ui.theme.LimeGreen

@Composable
fun AppPermissionsScreen(
    navController: NavController,
    packageName: String
) {
    val context = LocalContext.current
    val permissions = remember(packageName) {
        try {
            val pm = context.packageManager
            val pkgInfo = pm.getPackageInfo(packageName, android.content.pm.PackageManager.GET_PERMISSIONS)
            val reqPerms   = pkgInfo.requestedPermissions ?: emptyArray()
            val permStates = pkgInfo.requestedPermissionsFlags ?: IntArray(0)
            reqPerms.mapIndexed { i, perm ->
                val granted = permStates.getOrElse(i) { 0 } and
                    android.content.pm.PackageManager.GET_PERMISSIONS != 0
                val isDangerous = try {
                    val pi = pm.getPermissionInfo(perm, 0)
                    (pi.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_DANGEROUS) != 0
                } catch (e: Exception) { false }
                AppPermission(
                    name = perm.substringAfterLast("."),
                    isGranted = granted,
                    isDangerous = isDangerous,
                    groupName = perm.substringBeforeLast(".", perm),
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    val appName = remember(packageName) {
        try { context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) { packageName }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(16.dp)) {
            Text(appName, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
            Text(packageName, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            Text("${permissions.count { it.isGranted }} / ${permissions.size} permissions granted",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
        }

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val dangerous = permissions.filter { it.isDangerous }
            val normal    = permissions.filter { !it.isDangerous }

            if (dangerous.isNotEmpty()) {
                item { SectionHeader("Dangerous Permissions") }
                items(dangerous) { PermissionRow(it) }
            }
            if (normal.isNotEmpty()) {
                item { SectionHeader("Normal Permissions") }
                items(normal) { PermissionRow(it) }
            }
        }
    }
}

@Composable
private fun PermissionRow(perm: AppPermission) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(perm.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            color = if (perm.isDangerous) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        Icon(
            if (perm.isGranted) Icons.Default.Check else Icons.Default.Close,
            null,
            tint = if (perm.isGranted) LimeGreen else CoralRed,
            modifier = Modifier.size(20.dp)
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
}
