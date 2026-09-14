package com.saferesale.app.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import com.saferesale.app.domain.model.NetworkInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

class NetworkRepository constructor(
    private val context: Context
) {
    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }
    private val wifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    }
    private val telephonyManager by lazy {
        context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun getNetworkInfoFlow(intervalMs: Long = 3000L): Flow<NetworkInfo> = flow {
        while (true) {
            emit(buildNetworkInfo())
            delay(intervalMs)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getPublicIp(): String = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("https://api.ipify.org").build()
            httpClient.newCall(req).execute().use { it.body?.string() ?: "" }
        } catch (e: Exception) { "" }
    }

    private fun buildNetworkInfo(): NetworkInfo {
        val activeNet = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNet)

        val isWifi   = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val isMobile = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        val wifiInfo   = if (isWifi) getWifiInfo() else null
        val mobileInfo = if (isMobile) getMobileInfo() else null

        val (ipv4, ipv6) = getLocalAddresses()

        val activeType = when {
            isWifi   -> "Wi-Fi"
            isMobile -> "Mobile"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "No connection"
        }

        return NetworkInfo(
            isWifiConnected    = isWifi,
            ssid               = wifiInfo?.get("ssid") ?: "",
            bssid              = wifiInfo?.get("bssid") ?: "",
            signalStrengthDbm  = wifiInfo?.get("rssi")?.toIntOrNull() ?: 0,
            linkSpeedMbps      = wifiInfo?.get("linkSpeed")?.toIntOrNull() ?: 0,
            frequencyMHz       = wifiInfo?.get("frequency")?.toIntOrNull() ?: 0,
            wifiIpAddress      = wifiInfo?.get("ip") ?: "",
            gateway            = wifiInfo?.get("gateway") ?: "",
            dns1               = wifiInfo?.get("dns1") ?: "",
            dns2               = wifiInfo?.get("dns2") ?: "",
            wifiNetworkType    = wifiInfo?.get("standard") ?: "",
            wifiChannel        = frequencyToChannel(wifiInfo?.get("frequency")?.toIntOrNull() ?: 0),
            isMobileConnected  = isMobile,
            carrier            = mobileInfo?.get("carrier") ?: "",
            simStatus          = mobileInfo?.get("simStatus") ?: "",
            mobileNetworkType  = mobileInfo?.get("networkType") ?: "",
            signalStrengthAsu  = 0,
            mcc                = mobileInfo?.get("mcc") ?: "",
            mnc                = mobileInfo?.get("mnc") ?: "",
            roaming            = telephonyManager.isNetworkRoaming,
            activeNetworkType  = activeType,
            ipv4Address        = ipv4,
            ipv6Address        = ipv6,
        )
    }

    @Suppress("DEPRECATION")
    private fun getWifiInfo(): Map<String, String>? = try {
        val info: WifiInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val net = connectivityManager.activeNetwork ?: return null
            val tp  = connectivityManager.getNetworkCapabilities(net) ?: return null
            if (Build.VERSION.SDK_INT >= 31) {
                tp.transportInfo as? WifiInfo ?: return null
            } else {
                wifiManager.connectionInfo ?: return null
            }
        } else {
            wifiManager.connectionInfo ?: return null
        }

        val dhcp = wifiManager.dhcpInfo
        val ipInt = dhcp.ipAddress
        val ip = (ipInt and 0xFF).toString() + "." + (ipInt shr 8 and 0xFF) + "." +
                 (ipInt shr 16 and 0xFF) + "." + (ipInt shr 24 and 0xFF)
        val gwInt = dhcp.gateway
        val gw = (gwInt and 0xFF).toString() + "." + (gwInt shr 8 and 0xFF) + "." +
                 (gwInt shr 16 and 0xFF) + "." + (gwInt shr 24 and 0xFF)
        val d1Int = dhcp.dns1
        val d1 = (d1Int and 0xFF).toString() + "." + (d1Int shr 8 and 0xFF) + "." +
                 (d1Int shr 16 and 0xFF) + "." + (d1Int shr 24 and 0xFF)
        val d2Int = dhcp.dns2
        val d2 = (d2Int and 0xFF).toString() + "." + (d2Int shr 8 and 0xFF) + "." +
                 (d2Int shr 16 and 0xFF) + "." + (d2Int shr 24 and 0xFF)

        val ssid = info.ssid?.removePrefix("\"")?.removeSuffix("\"") ?: ""

        val standard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            when (info.wifiStandard) {
                ScanResult.WIFI_STANDARD_LEGACY -> "802.11b/g/n"
                ScanResult.WIFI_STANDARD_11N    -> "Wi-Fi 4 (802.11n)"
                ScanResult.WIFI_STANDARD_11AC   -> "Wi-Fi 5 (802.11ac)"
                ScanResult.WIFI_STANDARD_11AX   -> "Wi-Fi 6 (802.11ax)"
                ScanResult.WIFI_STANDARD_11AD   -> "802.11ad"
                else -> "Unknown"
            }
        } else "Unknown"

        mapOf(
            "ssid"      to ssid,
            "bssid"     to (info.bssid ?: ""),
            "rssi"      to info.rssi.toString(),
            "linkSpeed" to info.linkSpeed.toString(),
            "frequency" to info.frequency.toString(),
            "ip"        to ip,
            "gateway"   to gw,
            "dns1"      to d1,
            "dns2"      to d2,
            "standard"  to standard,
        )
    } catch (e: Exception) { null }

    private fun getMobileInfo(): Map<String, String> = try {
        val networkType = when (telephonyManager.networkType) {
            TelephonyManager.NETWORK_TYPE_NR         -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE        -> "4G LTE"
            TelephonyManager.NETWORK_TYPE_HSPAP,
            TelephonyManager.NETWORK_TYPE_HSPA       -> "3G HSPA"
            TelephonyManager.NETWORK_TYPE_UMTS       -> "3G UMTS"
            TelephonyManager.NETWORK_TYPE_EDGE       -> "2G EDGE"
            TelephonyManager.NETWORK_TYPE_GPRS       -> "2G GPRS"
            else -> "Unknown"
        }
        val simState = when (telephonyManager.simState) {
            TelephonyManager.SIM_STATE_READY          -> "Ready"
            TelephonyManager.SIM_STATE_ABSENT         -> "Absent"
            TelephonyManager.SIM_STATE_PIN_REQUIRED,
            TelephonyManager.SIM_STATE_PUK_REQUIRED,
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Locked"
            else -> "Unknown"
        }
        val plmn = telephonyManager.networkOperator ?: ""
        mapOf(
            "carrier"     to (telephonyManager.networkOperatorName ?: ""),
            "simStatus"   to simState,
            "networkType" to networkType,
            "mcc"         to plmn.take(3),
            "mnc"         to plmn.drop(3),
        )
    } catch (e: SecurityException) { emptyMap() }

    private fun getLocalAddresses(): Pair<String, String> = try {
        var ipv4 = ""; var ipv6 = ""
        NetworkInterface.getNetworkInterfaces()?.toList()?.forEach { ni ->
            if (!ni.isLoopback && ni.isUp) {
                ni.inetAddresses.toList().forEach { addr ->
                    when (addr) {
                        is Inet4Address -> if (ipv4.isEmpty()) ipv4 = addr.hostAddress ?: ""
                        is Inet6Address -> if (ipv6.isEmpty() && !addr.isLinkLocalAddress)
                            ipv6 = addr.hostAddress ?: ""
                    }
                }
            }
        }
        ipv4 to ipv6
    } catch (e: Exception) { "" to "" }

    private fun frequencyToChannel(freq: Int): Int = when {
        freq in 2412..2484 -> (freq - 2412) / 5 + 1
        freq in 5180..5825 -> (freq - 5180) / 5 + 36
        else -> 0
    }
}