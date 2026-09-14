package com.saferesale.app.data.device

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import com.saferesale.app.domain.model.DeviceInfo
import java.io.File

class DeviceInfoRepository constructor(
    private val context: Context
) {
    fun getDeviceInfo(): DeviceInfo {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)

        val refreshRate = try {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.refreshRate
        } catch (e: Exception) { 0f }

        return DeviceInfo(
            deviceName     = Build.DEVICE,
            manufacturer   = Build.MANUFACTURER,
            model          = Build.MODEL,
            brand          = Build.BRAND,
            androidVersion = Build.VERSION.RELEASE,
            sdkVersion     = Build.VERSION.SDK_INT,
            buildNumber    = Build.DISPLAY,
            securityPatch  = Build.VERSION.SECURITY_PATCH,
            kernelVersion  = readKernelVersion(),
            bootloader     = Build.BOOTLOADER,
            architecture   = getArchitecture(),
            screenResolution = "${metrics.widthPixels} x ${metrics.heightPixels}",
            screenDensity  = metrics.densityDpi,
            refreshRate    = refreshRate,
            hdrSupport     = false,
            product        = Build.PRODUCT,
            hardware       = Build.HARDWARE,
            fingerprint    = Build.FINGERPRINT,
        )
    }

    private fun readKernelVersion(): String = try {
        File("/proc/version").readText().trim().take(80)
    } catch (e: Exception) { "Unavailable" }

    private fun getArchitecture(): String {
        val abis = Build.SUPPORTED_ABIS
        return when {
            abis.any { it.startsWith("arm64") } -> "ARM64 (64-bit)"
            abis.any { it.startsWith("armeabi") } -> "ARM (32-bit)"
            abis.any { it.startsWith("x86_64") } -> "x86_64"
            abis.any { it.startsWith("x86") } -> "x86"
            else -> abis.firstOrNull() ?: "Unknown"
        }
    }
}