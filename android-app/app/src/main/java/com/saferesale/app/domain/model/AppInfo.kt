package com.saferesale.app.domain.model

data class AppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val installDate: Long,
    val updateDate: Long,
    val appSizeBytes: Long,
    val isSystemApp: Boolean,
    val isEnabled: Boolean,
    val targetSdkVersion: Int,
    val minSdkVersion: Int,
    val permissions: List<AppPermission> = emptyList(),
)

data class AppPermission(
    val name: String,
    val isGranted: Boolean,
    val isDangerous: Boolean,
    val groupName: String,
)