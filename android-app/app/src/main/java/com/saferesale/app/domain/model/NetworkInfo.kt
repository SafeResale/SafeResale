package com.saferesale.app.domain.model

data class NetworkInfo(
    // WiFi
    val isWifiConnected: Boolean = false,
    val ssid: String = "",
    val bssid: String = "",
    val signalStrengthDbm: Int = 0,
    val linkSpeedMbps: Int = 0,
    val frequencyMHz: Int = 0,
    val wifiIpAddress: String = "",
    val gateway: String = "",
    val dns1: String = "",
    val dns2: String = "",
    val wifiNetworkType: String = "",
    val wifiChannel: Int = 0,

    // Mobile
    val isMobileConnected: Boolean = false,
    val carrier: String = "",
    val simStatus: String = "",
    val mobileNetworkType: String = "",
    val signalStrengthAsu: Int = 0,
    val mcc: String = "",
    val mnc: String = "",
    val roaming: Boolean = false,

    // General
    val activeNetworkType: String = "",
    val ipv4Address: String = "",
    val ipv6Address: String = "",
    val publicIp: String = "",
    val dnsServer: String = "",
)