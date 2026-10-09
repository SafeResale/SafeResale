package com.saferesale.app.ui.screens.apps

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.saferesale.app.domain.model.AppInfo
import com.saferesale.app.ui.components.GlassCard
import com.saferesale.app.ui.components.StatusChip
import com.saferesale.app.ui.navigation.Screen
import com.saferesale.app.ui.theme.ElectricViolet
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AppsScreen(navController: NavController, vm: AppsViewModel = hiltViewModel()) {
    val apps      by vm.apps.collectAsStateWithLifecycle()
    val query     by vm.query.collectAsStateWithLifecycle()
    val sort      by vm.sort.collectAsStateWithLifecycle()
    val filter    by vm.filter.collectAsStateWithLifecycle()
    val isLoading by vm.isLoading.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        // Search + controls
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                label = { Text("Search apps…") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) {
                        Icon(Icons.Default.Clear, null)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ElectricViolet),
            )
            Spacer(Modifier.height(8.dp))
            // Filter tabs
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppFilter.entries.forEach { f ->
                    FilterChip(selected = filter == f, onClick = { vm.setFilter(f) },
                        label = { Text(f.name) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sort:", style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.align(Alignment.CenterVertically),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                AppSort.entries.forEach { s ->
                    FilterChip(selected = sort == s, onClick = { vm.setSort(s) },
                        label = { Text(s.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) })
                }
            }
            Text("${apps.size} apps", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ElectricViolet)
            }
            return
        }

        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(apps, key = { it.packageName }) { app ->
                AppListItem(app) {
                    navController.navigate(Screen.AppPermissions.withPackage(app.packageName))
                }
            }
        }
    }
}

@Composable
fun AppListItem(app: AppInfo, onClick: () -> Unit) {
    val context = LocalContext.current
    val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    GlassCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // App icon
            val icon: Drawable? = remember(app.packageName) {
                try { context.packageManager.getApplicationIcon(app.packageName) } catch (e: Exception) { null }
            }
            if (icon != null) {
                AsyncImage(
                    model = icon, contentDescription = app.appName,
                    modifier = Modifier.size(44.dp)
                )
            } else {
                Icon(Icons.Default.Android, null, modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(app.appName, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), maxLines = 1)
                Text("v${app.versionName} · ${app.appSizeBytes / 1024L / 1024L} MB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
            }
            Column(horizontalAlignment = Alignment.End) {
                if (app.isSystemApp) StatusChip("System", null)
                Text("Installed ${fmt.format(Date(app.installDate))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            }
        }
    }
}
