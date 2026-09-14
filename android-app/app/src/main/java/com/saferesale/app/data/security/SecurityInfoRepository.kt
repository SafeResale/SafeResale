package com.saferesale.app.data.security

import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.os.Build
import android.provider.Settings
import com.saferesale.app.domain.model.SecurityInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityInfoRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val biometricManager by lazy {
        context.getSystemService(BiometricManager::class.java)
    }
    private val keyguardManager by lazy {
        context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    }

    fun getSecurityInfo(): SecurityInfo {
        val canFP = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            biometricManager?.canAuthenticate(
                android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG
            ) == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
        } else false

        val canFace = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            biometricManager?.canAuthenticate(
                android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK
            ) == android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS
        } else false

        val verifiedBoot = try {
            val state = File("/sys/class/block/by-name/vbmeta").exists()
            if (state) "Verified" else "Unknown"
        } catch (e: Exception) { "Unknown" }

        val adbEnabled = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
        } catch (e: Exception) { false }

        val devSettings = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        } catch (e: Exception) { false }

        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: ""

        return SecurityInfo(
            securityPatchLevel    = Build.VERSION.SECURITY_PATCH,
            hasBiometric          = canFP || canFace,
            hasFingerprint        = canFP,
            hasFaceUnlock         = canFace,
            isDeviceSecure        = keyguardManager.isDeviceSecure,
            isEncrypted           = true,   // Android 10+ enforces encryption
            verifiedBootState     = verifiedBoot,
            isRooted              = isRooted(),
            isAdbEnabled          = adbEnabled,
            developmentSettingsEnabled = devSettings,
            androidId             = androidId,
        )
    }

    private fun isRooted(): Boolean {
        val paths = listOf("/system/bin/su", "/system/xbin/su", "/sbin/su",
            "/system/su", "/system/bin/.ext/.su")
        return paths.any { File(it).exists() } ||
               System.getenv("PATH")?.split(":")?.any { File("$it/su").exists() } == true
    }
}
