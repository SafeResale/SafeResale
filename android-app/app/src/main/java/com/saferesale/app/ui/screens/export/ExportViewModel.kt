package com.saferesale.app.ui.screens.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saferesale.app.data.battery.BatteryInfoRepository
import com.saferesale.app.data.cpu.CpuInfoRepository
import com.saferesale.app.data.device.DeviceInfoRepository
import com.saferesale.app.data.network.NetworkRepository
import com.saferesale.app.data.ram.RamInfoRepository
import com.saferesale.app.data.security.SecurityInfoRepository
import com.saferesale.app.data.storage.StorageInfoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

enum class ExportFormat { PDF, JSON, TXT, CSV }

@HiltViewModel
class ExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceRepo: DeviceInfoRepository,
    private val cpuRepo: CpuInfoRepository,
    private val ramRepo: RamInfoRepository,
    private val storageRepo: StorageInfoRepository,
    private val batteryRepo: BatteryInfoRepository,
    private val securityRepo: SecurityInfoRepository,
    private val networkRepo: NetworkRepository,
) : ViewModel() {

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting

    private val _lastExportPath = MutableStateFlow<String?>(null)
    val lastExportPath: StateFlow<String?> = _lastExportPath

    private val _reportText = MutableStateFlow("")
    val reportText: StateFlow<String> = _reportText

    init { buildReport() }

    private fun buildReport() {
        viewModelScope.launch {
            val device  = deviceRepo.getDeviceInfo()
            val cpu     = cpuRepo.getCpuInfoOnce()
            val ram     = ramRepo.getRamInfoOnce()
            val storage = storageRepo.getStorageInfo()
            val battery = batteryRepo.getBatteryInfoOnce()
            val security= securityRepo.getSecurityInfo()

            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

            _reportText.value = buildString {
                appendLine("═══════════════════════════════════")
                appendLine("   COREV DIAGNOSTIC REPORT")
                appendLine("   Generated: $ts")
                appendLine("═══════════════════════════════════")
                appendLine()
                appendLine("─── DEVICE ─────────────────────────")
                appendLine("Device:      ${device.manufacturer} ${device.model}")
                appendLine("Brand:       ${device.brand}")
                appendLine("Android:     ${device.androidVersion} (SDK ${device.sdkVersion})")
                appendLine("Build:       ${device.buildNumber}")
                appendLine("Security:    ${device.securityPatch}")
                appendLine("Kernel:      ${device.kernelVersion.take(60)}")
                appendLine("Arch:        ${device.architecture}")
                appendLine("Resolution:  ${device.screenResolution} @ ${device.refreshRate} Hz")
                appendLine()
                appendLine("─── CPU ─────────────────────────────")
                appendLine("Model:       ${cpu.model}")
                appendLine("Cores:       ${cpu.totalCores} (${cpu.availableCores} online)")
                appendLine("Max Freq:    ${cpu.maxFrequencyMHz.maxOrNull() ?: 0} MHz")
                appendLine("Governor:    ${cpu.governor}")
                appendLine()
                appendLine("─── MEMORY ──────────────────────────")
                appendLine("Total RAM:   ${ram.totalRamMB} MB")
                appendLine("Used:        ${ram.usedRamMB} MB (${ram.usagePercent.toInt()}%)")
                appendLine("Available:   ${ram.availableRamMB} MB")
                appendLine()
                appendLine("─── STORAGE ─────────────────────────")
                appendLine("Internal:    ${"%.2f".format(storage.internalTotal.toFloat() / (1024*1024*1024))} GB")
                appendLine("Used:        ${"%.2f".format(storage.internalUsed.toFloat() / (1024*1024*1024))} GB (${storage.internalUsagePercent.toInt()}%)")
                appendLine("Free:        ${"%.2f".format(storage.internalFree.toFloat() / (1024*1024*1024))} GB")
                appendLine()
                appendLine("─── BATTERY ─────────────────────────")
                appendLine("Level:       ${battery.percentage}%")
                appendLine("Status:      ${battery.status}")
                appendLine("Method:      ${battery.chargingMethod}")
                appendLine("Temp:        ${battery.temperatureCelsius}°C")
                appendLine("Voltage:     ${battery.voltageMilliVolts} mV")
                appendLine("Health:      ${battery.health}")
                appendLine("Technology:  ${battery.technology}")
                appendLine()
                appendLine("─── SECURITY ────────────────────────")
                appendLine("Patch:       ${security.securityPatchLevel}")
                appendLine("Encrypted:   ${if (security.isEncrypted) "Yes" else "No"}")
                appendLine("Biometric:   ${if (security.hasBiometric) "Available" else "None"}")
                appendLine("Rooted:      ${if (security.isRooted) "Yes ⚠" else "No"}")
                appendLine("ADB:         ${if (security.isAdbEnabled) "Enabled" else "Disabled"}")
                appendLine()
                appendLine("═══════════════════════════════════")
                appendLine("   End of CoreV Report")
                appendLine("═══════════════════════════════════")
            }
        }
    }

    fun export(format: ExportFormat) {
        viewModelScope.launch {
            _isExporting.value = true
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "CoreV_Report_$ts.${format.name.lowercase()}"
            val file = File(context.getExternalFilesDir(null), fileName)

            when (format) {
                ExportFormat.TXT -> file.writeText(_reportText.value)
                ExportFormat.JSON -> file.writeText(buildJsonReport())
                ExportFormat.CSV  -> file.writeText(buildCsvReport())
                ExportFormat.PDF  -> exportPdf(file)
            }

            _lastExportPath.value = file.absolutePath
            _isExporting.value = false

            // Share
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = when (format) {
                    ExportFormat.PDF -> "application/pdf"
                    ExportFormat.JSON -> "application/json"
                    else -> "text/plain"
                }
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Share CoreV Report").also {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    private fun buildJsonReport(): String {
        val device  = deviceRepo.getDeviceInfo()
        val cpu     = cpuRepo.getCpuInfoOnce()
        val ram     = ramRepo.getRamInfoOnce()
        val battery = batteryRepo.getBatteryInfoOnce()
        return """
{
  "generated": "${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(Date())}",
  "device": {
    "manufacturer": "${device.manufacturer}",
    "model": "${device.model}",
    "androidVersion": "${device.androidVersion}",
    "sdkVersion": ${device.sdkVersion},
    "buildNumber": "${device.buildNumber}",
    "securityPatch": "${device.securityPatch}",
    "architecture": "${device.architecture}"
  },
  "cpu": {
    "model": "${cpu.model}",
    "cores": ${cpu.totalCores},
    "maxFreqMHz": ${cpu.maxFrequencyMHz.maxOrNull() ?: 0},
    "governor": "${cpu.governor}"
  },
  "ram": {
    "totalMB": ${ram.totalRamMB},
    "usedMB": ${ram.usedRamMB},
    "availableMB": ${ram.availableRamMB}
  },
  "battery": {
    "percentage": ${battery.percentage},
    "status": "${battery.status}",
    "health": "${battery.health}",
    "temperatureC": ${battery.temperatureCelsius},
    "voltageMilliV": ${battery.voltageMilliVolts},
    "technology": "${battery.technology}"
  }
}""".trimIndent()
    }

    private fun buildCsvReport(): String {
        val sb = StringBuilder()
        sb.appendLine("Category,Field,Value")
        val device  = deviceRepo.getDeviceInfo()
        val ram     = ramRepo.getRamInfoOnce()
        val battery = batteryRepo.getBatteryInfoOnce()
        sb.appendLine("Device,Manufacturer,${device.manufacturer}")
        sb.appendLine("Device,Model,${device.model}")
        sb.appendLine("Device,Android Version,${device.androidVersion}")
        sb.appendLine("RAM,Total MB,${ram.totalRamMB}")
        sb.appendLine("RAM,Used MB,${ram.usedRamMB}")
        sb.appendLine("Battery,Percentage,${battery.percentage}")
        sb.appendLine("Battery,Health,${battery.health}")
        sb.appendLine("Battery,Temperature C,${battery.temperatureCelsius}")
        return sb.toString()
    }

    private fun exportPdf(file: File) {
        try {
            val doc = com.itextpdf.kernel.pdf.PdfDocument(com.itextpdf.kernel.pdf.PdfWriter(file))
            val layout = com.itextpdf.layout.Document(doc)
            layout.add(com.itextpdf.layout.element.Paragraph("CoreV Diagnostic Report")
                .setFontSize(20f).setBold())
            layout.add(com.itextpdf.layout.element.Paragraph(_reportText.value)
                .setFontSize(10f))
            layout.close()
            doc.close()
        } catch (e: Exception) {
            // Fallback to TXT if iText fails
            file.writeText(_reportText.value)
        }
    }
}
