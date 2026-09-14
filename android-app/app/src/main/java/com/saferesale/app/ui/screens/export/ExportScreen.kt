package com.saferesale.app.ui.screens.export

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.saferesale.app.ui.components.GlassCard
import com.saferesale.app.ui.components.SectionHeader
import com.saferesale.app.ui.theme.*

@Composable
fun ExportScreen(navController: NavController, vm: ExportViewModel = hiltViewModel()) {
    val reportText   by vm.reportText.collectAsStateWithLifecycle()
    val isExporting  by vm.isExporting.collectAsStateWithLifecycle()
    val lastPath     by vm.lastExportPath.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Export Format")
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportButton("PDF", Icons.Default.PictureAsPdf, ElectricViolet, isExporting, Modifier.weight(1f))
                { vm.export(ExportFormat.PDF) }

                ExportButton("JSON", Icons.Default.Code, TealAccent, isExporting, Modifier.weight(1f))
                { vm.export(ExportFormat.JSON) }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExportButton("TXT", Icons.Default.TextSnippet, AmberWarm, isExporting, Modifier.weight(1f))
                { vm.export(ExportFormat.TXT) }

                ExportButton("CSV", Icons.Default.TableChart, LimeGreen, isExporting, Modifier.weight(1f))
                { vm.export(ExportFormat.CSV) }
            }
        }

        lastPath?.let { path ->
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = LimeGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("Exported Successfully!", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, color = LimeGreen))
                        Text(path, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                    }
                }
            }
        }

        GlassCard(Modifier.fillMaxWidth()) {
            SectionHeader("Report Preview")
            Spacer(Modifier.height(8.dp))
            Text(
                reportText.take(3000),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
fun ExportButton(
    label: String, icon: ImageVector, color: Color,
    isLoading: Boolean, modifier: Modifier, onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !isLoading,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.2f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
    ) {
        if (isLoading)
            CircularProgressIndicator(Modifier.size(16.dp), color = color, strokeWidth = 2.dp)
        else {
            Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = color, fontWeight = FontWeight.Bold)
        }
    }
}
