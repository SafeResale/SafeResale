package com.saferesale.app.data.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.saferesale.app.domain.model.AppInfo
import com.saferesale.app.domain.model.AppPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstalledAppsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val pm: PackageManager = context.packageManager

    suspend fun getInstalledApps(includeSystem: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            PackageManager.GET_META_DATA
        } else PackageManager.GET_META_DATA

        val packages = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(flags)
            }
        } catch (e: Exception) { emptyList() }

        packages.mapNotNull { pkg ->
            try {
                val appInfo = pkg.applicationInfo ?: return@mapNotNull null
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                if (!includeSystem && isSystem) return@mapNotNull null

                val label = pm.getApplicationLabel(appInfo).toString()
                val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                    pkg.longVersionCode else @Suppress("DEPRECATION") pkg.versionCode.toLong()

                AppInfo(
                    packageName    = pkg.packageName,
                    appName        = label,
                    versionName    = pkg.versionName ?: "",
                    versionCode    = versionCode,
                    installDate    = pkg.firstInstallTime,
                    updateDate     = pkg.lastUpdateTime,
                    appSizeBytes   = try {
                        val aif = pm.getApplicationInfo(pkg.packageName, 0)
                        aif.sourceDir?.let { java.io.File(it).length() } ?: 0L
                    } catch (e: Exception) { 0L },
                    isSystemApp    = isSystem,
                    isEnabled      = appInfo.enabled,
                    targetSdkVersion = appInfo.targetSdkVersion,
                    minSdkVersion    = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
                        appInfo.minSdkVersion else 0,
                    permissions    = getAppPermissions(pkg.packageName),
                )
            } catch (e: Exception) { null }
        }.sortedBy { it.appName.lowercase() }
    }

    private fun getAppPermissions(packageName: String): List<AppPermission> = try {
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        }
        val reqPerms   = pkgInfo.requestedPermissions ?: return emptyList()
        val permStates = pkgInfo.requestedPermissionsFlags ?: return emptyList()

        reqPerms.take(50).mapIndexed { i, perm ->
            val isGranted = permStates.getOrElse(i) { 0 } and
                    PackageManager.GET_PERMISSIONS != 0

            val isDangerous = try {
                val pi = pm.getPermissionInfo(perm, 0)
                (pi.protectionLevel and android.content.pm.PermissionInfo.PROTECTION_DANGEROUS) != 0
            } catch (e: Exception) { false }

            AppPermission(
                name        = perm.substringAfterLast("."),
                isGranted   = isGranted,
                isDangerous = isDangerous,
                groupName   = perm.substringBeforeLast(".", perm),
            )
        }
    } catch (e: Exception) { emptyList() }
}
