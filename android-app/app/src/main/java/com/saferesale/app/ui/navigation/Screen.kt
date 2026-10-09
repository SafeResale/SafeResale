package com.saferesale.app.ui.navigation

sealed class Screen(val route: String) {
    object Dashboard     : Screen("dashboard")
    object Device        : Screen("device")
    object Cpu           : Screen("cpu")
    object Ram           : Screen("ram")
    object Storage       : Screen("storage")
    object Battery       : Screen("battery")
    object Sensors       : Screen("sensors")
    object Network       : Screen("network")
    object NetworkTools  : Screen("network_tools")
    object Camera        : Screen("camera")
    object Audio         : Screen("audio")
    object Display       : Screen("display")
    object Security      : Screen("security")
    object Apps          : Screen("apps")
    object AppPermissions: Screen("app_permissions/{packageName}") {
        fun withPackage(pkg: String) = "app_permissions/$pkg"
    }
    object Gps           : Screen("gps")
    object Benchmark     : Screen("benchmark")
    object HardwareTest  : Screen("hardware_test")
    object Export        : Screen("export")
}
