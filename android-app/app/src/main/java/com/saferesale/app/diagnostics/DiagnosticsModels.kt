package com.saferesale.app.diagnostics

import android.os.Build

data class DeviceInfo(
    val model: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val os_version: String = "Android ${Build.VERSION.RELEASE}",
    val sdk: Int = Build.VERSION.SDK_INT
)

data class DiagTest(
    val id: String,
    val status: String, // passed|failed|permission_required|unsupported|unavailable|skipped
    val value: Any? = null,
    val unit: String? = null,
    val simulated: Boolean = false,
    val measured_at: String? = java.time.Instant.now().toString(),
    val meta: Map<String, Any?>? = null
)

data class DiagnosticsReport(
    val device: DeviceInfo = DeviceInfo(),
    val category: String = "mobile",
    val skipped: Boolean = false,
    val tests: List<DiagTest> = emptyList()
)
