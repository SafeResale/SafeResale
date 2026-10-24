package com.saferesale.app.data.network

import com.saferesale.app.domain.model.NetworkToolResult
import com.saferesale.app.domain.model.NetworkToolType
import com.saferesale.app.domain.model.TracerouteHop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

@Singleton
class NetworkToolsRepository @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun ping(host: String, count: Int = 4): NetworkToolResult = withContext(Dispatchers.IO) {
        try {
            val latencies = mutableListOf<Long>()
            var success = false
            val sb = StringBuilder()
            repeat(count) { i ->
                val start = System.currentTimeMillis()
                val reachable = InetAddress.getByName(host).isReachable(2000)
                val elapsed = System.currentTimeMillis() - start
                if (reachable) { latencies.add(elapsed); success = true }
                sb.appendLine("${i + 1}: ${if (reachable) "${elapsed}ms" else "Timeout"}")
            }
            if (latencies.isNotEmpty()) {
                sb.appendLine("\n--- $host ping statistics ---")
                sb.appendLine("${latencies.size}/$count packets received")
                sb.appendLine("Min: ${latencies.min()}ms  Max: ${latencies.max()}ms  Avg: ${latencies.average().roundToLong()}ms")
            }
            NetworkToolResult(
                type = NetworkToolType.PING,
                success = success,
                resultText = sb.toString(),
                latencyMs = latencies.average().takeIf { !it.isNaN() }?.roundToLong() ?: 0L,
            )
        } catch (e: Exception) {
            NetworkToolResult(type = NetworkToolType.PING, success = false, resultText = "Error: ${e.message}")
        }
    }

    suspend fun dnsLookup(host: String): NetworkToolResult = withContext(Dispatchers.IO) {
        try {
            val start = System.currentTimeMillis()
            val addresses = InetAddress.getAllByName(host)
            val elapsed = System.currentTimeMillis() - start
            val sb = StringBuilder("DNS lookup for: $host\n\n")
            addresses.forEachIndexed { i, addr ->
                sb.appendLine("${i + 1}: ${addr.hostAddress}")
            }
            sb.appendLine("\nLookup time: ${elapsed}ms")
            NetworkToolResult(
                type = NetworkToolType.DNS_LOOKUP,
                success = true,
                resultText = sb.toString(),
                latencyMs = elapsed,
                dnsAddresses = addresses.map { it.hostAddress ?: "" },
            )
        } catch (e: Exception) {
            NetworkToolResult(type = NetworkToolType.DNS_LOOKUP, success = false, resultText = "Error: ${e.message}")
        }
    }

    suspend fun httpTest(url: String): NetworkToolResult = withContext(Dispatchers.IO) {
        try {
            val targetUrl = if (!url.startsWith("http")) "https://$url" else url
            val start = System.currentTimeMillis()
            val req = Request.Builder().url(targetUrl).head().build()
            val resp = client.newCall(req).execute()
            val elapsed = System.currentTimeMillis() - start
            val sb = StringBuilder()
            sb.appendLine("URL: $targetUrl")
            sb.appendLine("Status: ${resp.code} ${resp.message}")
            sb.appendLine("Response Time: ${elapsed}ms")
            sb.appendLine("Content-Type: ${resp.header("Content-Type") ?: "N/A"}")
            sb.appendLine("Server: ${resp.header("Server") ?: "N/A"}")
            sb.appendLine("Protocol: ${resp.protocol}")
            NetworkToolResult(
                type = NetworkToolType.HTTP_TEST,
                success = resp.isSuccessful,
                resultText = sb.toString(),
                latencyMs = elapsed,
                httpStatusCode = resp.code,
            )
        } catch (e: Exception) {
            NetworkToolResult(type = NetworkToolType.HTTP_TEST, success = false, resultText = "Error: ${e.message}")
        }
    }

    suspend fun checkConnectivity(): NetworkToolResult = withContext(Dispatchers.IO) {
        try {
            val start = System.currentTimeMillis()
            val addr = InetAddress.getByName("8.8.8.8")
            val elapsed = System.currentTimeMillis() - start
            NetworkToolResult(
                type = NetworkToolType.CONNECTIVITY,
                success = true,
                resultText = "Internet reachable via ${addr.hostAddress}\nLatency: ${elapsed}ms",
                latencyMs = elapsed,
            )
        } catch (e: Exception) {
            NetworkToolResult(type = NetworkToolType.CONNECTIVITY, success = false, resultText = "No internet: ${e.message}")
        }
    }

    /** Simple multi-TTL traceroute using shell ping commands */
    suspend fun traceroute(host: String, maxHops: Int = 30): NetworkToolResult = withContext(Dispatchers.IO) {
        val hops = mutableListOf<TracerouteHop>()
        try {
            for (ttl in 1..maxHops) {
                val start = System.currentTimeMillis()
                val proc = Runtime.getRuntime().exec(arrayOf("ping", "-c", "1", "-t", ttl.toString(), "-W", "2", host))
                val output = proc.inputStream.bufferedReader().readText()
                val elapsed = System.currentTimeMillis() - start
                proc.waitFor()

                val ipRegex = Regex("""(\d{1,3}(?:\.\d{1,3}){3})""")
                val ip = ipRegex.find(output)?.value ?: "*"
                val timedOut = output.contains("100% packet loss") || ip == "*"

                hops.add(TracerouteHop(
                    hop = ttl,
                    ip = ip,
                    hostname = try { InetAddress.getByName(ip).canonicalHostName } catch (e: Exception) { ip },
                    latencyMs = elapsed,
                    timedOut = timedOut,
                ))

                if (!timedOut && ip == InetAddress.getByName(host).hostAddress) break
            }
        } catch (e: Exception) {
            return@withContext NetworkToolResult(
                type = NetworkToolType.TRACEROUTE,
                success = false,
                resultText = "Error: ${e.message}"
            )
        }

        val sb = StringBuilder("Traceroute to $host\n\n")
        hops.forEach { hop ->
            sb.appendLine("${hop.hop.toString().padStart(2)}  ${hop.ip.padEnd(20)} ${if (hop.timedOut) "* * *" else "${hop.latencyMs}ms"}")
        }

        NetworkToolResult(
            type = NetworkToolType.TRACEROUTE,
            success = true,
            resultText = sb.toString(),
            hops = hops,
        )
    }
}
