package com.saferesale.app.domain.model

data class SecurityInfo(
    val securityPatchLevel: String = "",
    val hasBiometric: Boolean = false,
    val hasFingerprint: Boolean = false,
    val hasFaceUnlock: Boolean = false,
    val hasIrisUnlock: Boolean = false,
    val isDeviceSecure: Boolean = false,
    val isEncrypted: Boolean = false,
    val verifiedBootState: String = "",  // Verified / Self-signed / Unverified / Failed
    val isRooted: Boolean = false,
    val isAdbEnabled: Boolean = false,
    val developmentSettingsEnabled: Boolean = false,
    val androidId: String = "",
    val playIntegrityStatus: String = "Unknown",
)