package com.saferesale.app.diagnostics

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Graded bench score: runs over the full CoreV inspection and scores each
 * hardware module 0–100 with industry-style weights (battery first, then
 * functional modules — cf. NSYS/reboxed 60–70pt pro grading and A+..D bands).
 *
 * This is a CAPABILITY score (what the hardware can do), distinct from the
 * backend contract score (fraction of applicable checks passed, which drives
 * the risk engine). Rules:
 *  - passed/failed/unsupported hardware counts (absent hardware lowers the
 *    capability score — that is the point of grading);
 *  - permission_required / skipped / unavailable modules are EXCLUDED and the
 *    remaining weights renormalized (never punished for refusing a permission).
 */
data class ModuleScore(
    val id: String,
    val label: String,
    val score: Int,          // 0..100
    val detail: String,
    val weight: Double,
    val counted: Boolean,    // false -> excluded, weight renormalized
)

data class BenchResult(
    val modules: List<ModuleScore>,
    val total: Int,          // 0..100 weighted over counted modules
    val band: String,        // Pristine / Excellent / Great / Fair / Poor
    val countedWeight: Double,
)

object BenchScore {

    fun bandFor(total: Int): String = when {
        total >= 90 -> "Pristine (A+)"
        total >= 80 -> "Excellent (A)"
        total >= 70 -> "Great (B)"
        total >= 50 -> "Fair (C)"
        else -> "Poor (D)"
    }

    @OptIn(ExperimentalContracts::class)
    private fun isExcluded(t: DiagTest?): Boolean {
        contract { returns(false) implies (t != null) }
        return t == null || t.status in setOf("permission_required", "skipped", "unavailable")
    }

    @OptIn(ExperimentalContracts::class)
    fun grade(report: DiagnosticsReport): BenchResult {
        val byId = report.tests.associateBy { it.id }
        fun meta(id: String): Map<String, Any?> {
            val raw = byId[id]?.meta as? Map<*, *> ?: return emptyMap()
            val out = mutableMapOf<String, Any?>()
            for ((k, v) in raw.entries) out[k as? String ?: "?"] = v
            return out
        }
        fun num(id: String, key: String): Double =
            (meta(id)[key] as? Number)?.toDouble() ?: Double.NaN
        fun str(id: String, key: String): String = meta(id)[key]?.toString() ?: ""

        val mods = mutableListOf<ModuleScore>()
        fun add(id: String, label: String, weight: Double, score: Int, detail: String, counted: Boolean = true) {
            mods.add(ModuleScore(id, label, score.coerceIn(0, 100), detail, weight, counted))
        }

        // Battery — 15 (industry #1 factor: health + thermals)
        run {
            val t = byId["battery"]
            if (isExcluded(t)) { add("battery", "Battery", 15.0, 0, "not measured", false); return@run }
            val health = str("battery", "health")
            val temp = num("battery", "temp_C")
            val s = when {
                t!!.status == "failed" -> 20
                health == "Overheat" || health == "Dead" -> 10
                health != "Good" && health.isNotEmpty() && health != "Unknown" -> 40
                !temp.isNaN() && temp > 50 -> 20
                !temp.isNaN() && temp > 45 -> 70
                else -> 100
            }
            add("battery", "Battery", 15.0, s, "${t.value}${t.unit ?: ""} · ${health.ifEmpty { "?" }} · ${if (temp.isNaN()) "?" else "${temp.toInt()}°C"}")
        }

        // Display — 10 (resolution + density + refresh + HDR)
        run {
            val t = byId["sys_display"]
            if (t == null || t.status != "passed") { add("display", "Display", 10.0, 0, "not measured", t?.status !in setOf("permission_required", "skipped", "unavailable") && t != null); return@run }
            var s = 0
            val w = num("sys_display", "width_px")
            val dpi = num("sys_display", "density_dpi")
            val hz = num("sys_display", "refresh_hz")
            val hdr = meta("sys_display")["hdr_or_wide"] == true
            if (!w.isNaN() && w >= 1080) s += 40
            if (!dpi.isNaN() && dpi >= 320) s += 20
            s += when { hz.isNaN() -> 0; hz >= 90 -> 25; hz >= 60 -> 15; else -> 5 }
            if (hdr) s += 15
            add("display", "Display", 10.0, s, "${meta("sys_display")["width_px"]}×${meta("sys_display")["height_px"]} · ${if (hz.isNaN()) "?" else "${hz.toInt()}Hz"}")
        }

        // Touch — 5 (tapped cells fraction)
        run {
            val t = byId["touch"]
            if (isExcluded(t)) { add("touch", "Touch", 5.0, 0, "skipped", false); return@run }
            val covered = (meta("touch")["covered_cells"] as? Number)?.toInt() ?: 0
            val s = (covered * 100 / 9).coerceIn(0, 100)
            add("touch", "Touch", 5.0, s, "$covered/9 cells")
        }

        // Sensors — 10 (core trio, live sample = full marks)
        run {
            val ids = listOf("sensor_accelerometer" to "Accel", "sensor_gyroscope" to "Gyro", "sensor_proximity" to "Prox")
            var sum = 0
            val bits = ids.map { (id, _) ->
                val t = byId[id]
                val v = when {
                    t == null || t.status == "unsupported" -> 0
                    isExcluded(t) -> -1
                    t.status == "passed" && t.meta?.toString()?.contains("live") == true -> 100
                    t.status == "passed" -> 60
                    else -> 0
                }
                sum += maxOf(v, 0)
                v
            }
            val countedAny = bits.any { it >= 0 }
            val s = if (bits.all { it < 0 }) 0 else sum / ids.size
            val live = ids.count { (id, _) -> byId[id]?.status == "passed" && byId[id]?.meta?.toString()?.contains("live") == true }
            add("sensors", "Sensors", 10.0, s, "$live/3 live", countedAny)
        }

        // Camera — 10 (max-MP tiers)
        run {
            val t = byId["camera"]
            if (isExcluded(t)) { add("camera", "Camera", 10.0, 0, "no permission", false); return@run }
            val mp = (t!!.value as? Number)?.toDouble() ?: 0.0
            val s = when {
                t.status == "unsupported" -> 0
                t.status != "passed" -> 10
                mp >= 12 -> 100; mp >= 8 -> 85; mp >= 5 -> 70; mp > 0 -> 50; else -> 30
            }
            add("camera", "Camera", 10.0, s, if (t.status == "passed") "${mp}MP · ${meta("camera")["count"]} lens" else t.status)
        }

        // Microphone / Speaker — 5 each
        for ((id, label) in listOf("microphone" to "Microphone", "speaker" to "Speaker")) {
            val t = byId[id]
            if (isExcluded(t)) { add(id, label, 5.0, 0, "no permission", false); continue }
            val s = when (t!!.status) { "passed" -> 100; "unsupported" -> 0; else -> 10 }
            add(id, label, 5.0, s, t.status)
        }

        // Wi-Fi — 10 (RSSI tiers)
        run {
            val t = byId["wifi"]
            if (isExcluded(t)) { add("wifi", "Wi-Fi", 10.0, 0, "no permission", false); return@run }
            val rssi = num("wifi", "rssi_dBm")
            val s = when {
                t!!.status != "passed" -> 10
                rssi.isNaN() || rssi == 0.0 -> 70
                rssi >= -50 -> 100; rssi >= -60 -> 85; rssi >= -70 -> 70; else -> 55
            }
            add("wifi", "Wi-Fi", 10.0, s, str("wifi", "method").let { "${t.value} · ${if (rssi.isNaN()) "?" else "${rssi.toInt()}dBm"}" })
        }

        // Bluetooth — 5
        run {
            val t = byId["bluetooth"]
            if (isExcluded(t)) { add("bluetooth", "Bluetooth", 5.0, 0, "no permission", false); return@run }
            val s = when (t!!.status) { "passed" -> 100; "failed" -> 30; "unsupported" -> 0; else -> 0 }
            add("bluetooth", "Bluetooth", 5.0, s, t.status)
        }

        // GPS — 5 (provider on; live fix not awaited)
        run {
            val t = byId["gps"]
            if (isExcluded(t)) { add("gps", "GPS", 5.0, 0, "no permission", false); return@run }
            val s = when (t!!.status) { "passed" -> 85; "failed" -> 10; "unsupported" -> 0; else -> 0 }
            add("gps", "GPS", 5.0, s, if (t.status == "passed") "provider on" else t.status)
        }

        // CPU — 5
        run {
            val raw = meta("sys_snapshot")["cpu"] as? Map<*, *>
            if (raw == null) { add("cpu", "CPU", 5.0, 0, "not measured", false); return@run }
            fun g(k: String): Any? = raw[k]
            val cores = (g("cores") as? Number)?.toInt() ?: 0
            val online = (g("online") as? Number)?.toInt() ?: 0
            @Suppress("UNCHECKED_CAST")
            val freqs = (g("freq_MHz") as? List<*>)?.mapNotNull { (it as? Number)?.toLong() } ?: emptyList()
            var s = 0
            if (cores > 0 && online == cores) s += 50 else if (online > 0) s += 25
            if (freqs.any { it > 0 }) s += 25
            val usage = (g("usage_pct") as? Number)?.toFloat() ?: 0f
            if (usage < 90) s += 25
            add("cpu", "CPU", 5.0, s, "$online/$cores cores · ${g("governor")}")
        }

        // RAM — 5
        run {
            val raw = meta("sys_snapshot")["ram"] as? Map<*, *>
            if (raw == null) { add("ram", "RAM", 5.0, 0, "not measured", false); return@run }
            val usage = (raw["usage_pct"] as? Number)?.toFloat() ?: 0f
            val s = when { usage < 85 -> 100; usage < 90 -> 80; usage < 95 -> 50; else -> 20 }
            add("ram", "RAM", 5.0, s, "${raw["used_MB"]}/${raw["total_MB"]}MB")
        }

        // Storage — 5 (free-space tiers)
        run {
            val raw = meta("sys_snapshot")["storage"] as? Map<*, *>
            if (raw == null) { add("storage", "Storage", 5.0, 0, "not measured", false); return@run }
            fun longOf(k: String): Long = when (val v = raw[k]) {
                is Number -> v.toLong()
                else -> 0L
            }
            val total = longOf("internal_total").coerceAtLeast(1)
            val free = longOf("internal_free")
            val frac = free.toDouble() / total.toDouble()
            val s = when { frac > 0.2 -> 100; frac > 0.1 -> 70; frac > 0.05 -> 40; else -> 15 }
            add("storage", "Storage", 5.0, s, "${(frac * 100).toInt()}% free")
        }

        // Security — 5 (root = fail)
        run {
            val t = byId["sys_security"]
            if (t == null || isExcluded(t)) { add("security", "Security", 5.0, 0, "not measured", false); return@run }
            val rooted = meta("sys_security")["rooted"] == true
            add("security", "Security", 5.0, if (rooted) 0 else 100, if (rooted) "ROOTED" else "clean · ${meta("sys_security")["patch"]}")
        }

        // Functional (vibration + speaker tone + mic) — 5
        run {
            val t = byId["sys_functional"]
            if (t == null || isExcluded(t)) { add("functional", "Functional", 5.0, 0, "not measured", false); return@run }
            val m = meta("sys_functional")
            val ok = listOf("vibration", "speaker_tone", "mic_feature").count { m[it] == true }
            add("functional", "Functional", 5.0, ok * 100 / 3, "$ok/3 functional")
        }

        val countedMods = mods.filter { it.counted }
        val wSum = countedMods.sumOf { it.weight }.coerceAtLeast(0.001)
        val total = (countedMods.sumOf { it.score * it.weight } / wSum).toInt().coerceIn(0, 100)
        return BenchResult(mods, total, bandFor(total), wSum)
    }
}
