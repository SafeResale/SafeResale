package com.saferesale.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.saferesale.app.ui.screens.apps.AppsScreen
import com.saferesale.app.ui.screens.apps.AppPermissionsScreen
import com.saferesale.app.ui.screens.audio.AudioScreen
import com.saferesale.app.ui.screens.battery.BatteryScreen
import com.saferesale.app.ui.screens.benchmark.BenchmarkScreen
import com.saferesale.app.ui.screens.camera.CameraScreen
import com.saferesale.app.ui.screens.cpu.CpuScreen
import com.saferesale.app.ui.screens.dashboard.DashboardScreen
import com.saferesale.app.ui.screens.device.DeviceScreen
import com.saferesale.app.ui.screens.display.DisplayScreen
import com.saferesale.app.ui.screens.export.ExportScreen
import com.saferesale.app.ui.screens.gps.GpsScreen
import com.saferesale.app.ui.screens.hardwaretest.HardwareTestScreen
import com.saferesale.app.ui.screens.network.NetworkScreen
import com.saferesale.app.ui.screens.network.NetworkToolsScreen
import com.saferesale.app.ui.screens.ram.RamScreen
import com.saferesale.app.ui.screens.security.SecurityScreen
import com.saferesale.app.ui.screens.sensors.SensorsScreen
import com.saferesale.app.ui.screens.storage.StorageScreen

@Composable
fun CoreVNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController  = navController,
        startDestination = Screen.Dashboard.route,
        modifier       = modifier
    ) {
        composable(Screen.Dashboard.route)    { DashboardScreen(navController) }
        composable(Screen.Device.route)       { DeviceScreen(navController) }
        composable(Screen.Cpu.route)          { CpuScreen(navController) }
        composable(Screen.Ram.route)          { RamScreen(navController) }
        composable(Screen.Storage.route)      { StorageScreen(navController) }
        composable(Screen.Battery.route)      { BatteryScreen(navController) }
        composable(Screen.Sensors.route)      { SensorsScreen(navController) }
        composable(Screen.Network.route)      { NetworkScreen(navController) }
        composable(Screen.NetworkTools.route) { NetworkToolsScreen(navController) }
        composable(Screen.Camera.route)       { CameraScreen(navController) }
        composable(Screen.Audio.route)        { AudioScreen(navController) }
        composable(Screen.Display.route)      { DisplayScreen(navController) }
        composable(Screen.Security.route)     { SecurityScreen(navController) }
        composable(Screen.Apps.route)         { AppsScreen(navController) }
        composable(
            route = Screen.AppPermissions.route,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { back ->
            AppPermissionsScreen(
                navController = navController,
                packageName = back.arguments?.getString("packageName") ?: ""
            )
        }
        composable(Screen.Gps.route)          { GpsScreen(navController) }
        composable(Screen.Benchmark.route)    { BenchmarkScreen(navController) }
        composable(Screen.HardwareTest.route) { HardwareTestScreen(navController) }
        composable(Screen.Export.route)       { ExportScreen(navController) }
    }
}
