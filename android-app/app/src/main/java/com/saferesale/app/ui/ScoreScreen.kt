package com.saferesale.app.ui

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saferesale.app.DIAGNOSTIC_CATEGORIES
import com.saferesale.app.data.ApiClient
import com.saferesale.app.data.marketplace.MarketplaceRepository
import com.saferesale.app.diagnostics.BenchScore
import com.saferesale.app.diagnostics.CoreVScoreReport
import com.saferesale.app.diagnostics.DeviceInfo
import com.saferesale.app.diagnostics.DiagTest
import com.saferesale.app.diagnostics.DiagnosticsReport
import com.saferesale.app.diagnostics.ModuleScore
import com.saferesale.app.ui.market.ApiState
import com.saferesale.app.ui.market.rememberApi
import kotlinx.coroutines.launch

/**
 * Score screen: runs vision + risk on the server, grades the full CoreV
 * bench (weighted module scores, A+..D band) and shows everything as a
 * testing-app style module grid.
 * Flow: photos (capture) + CoreV inspection already synced →
 * run-vision (defects + condition) → compute-risk (0.4 physical + 0.4
 * diagnostic + 0.2 behavioral → decision + badge) → bench grading →
 * latest-scores display.
 */
@Composable
fun ScoreScreen(token: String?, listingId: String?, category: String = "mobile", onDone: () -> Unit) {
    val ctx = LocalContext.current
    var phase by remember { mutableStateOf("Starting…") }
    var vision by remember { mutableStateOf<Map<String, Any>?>(null) }
    var risk by remember { mutableStateOf<Map<String, Any>?>(null) }
    var scores by remember { mutableStateOf<Map<String, Any>?>(null) }
    var bench by remember { mutableStateOf<com.saferesale.app.diagnostics.BenchResult?>(null) }
    var error by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun run() {
        if (token == null || listingId == null) { error = "Login + listing required"; return }
        running = true
        error = ""
        scope.launch {
            try {
                val auth = "Bearer $token"
                phase = "1/4 Running vision (defects + condition)…"
                vision = ApiClient.service.runVision(listingId, auth)
                phase = "2/4 Computing risk…"
                risk = ApiClient.service.computeRisk(listingId, auth)
                phase = "3/4 Fetching synced results…"
                scores = ApiClient.service.latestScores(listingId, auth)
                // The hardware bench only applies to device categories; other
                // categories (books, furniture, sports…) are scored on photos,
                // vision and risk alone.
                if (category in DIAGNOSTIC_CATEGORIES) {
                    phase = "4/4 Grading verified hardware diagnostics…"
                    val diagMap = scores?.get("diagnostics") as? Map<*, *>
                    val diagScore = (scores?.get("diagnostic_score") as? Number)?.toInt()
                    if (diagMap != null && diagScore != null) {
                        val tests = diagMap["tests"] as? List<*>
                        val diagTests = (tests ?: emptyList<Any>()).mapNotNull { t ->
                            val tm = t as? Map<*, *> ?: return@mapNotNull null
                            @Suppress("UNCHECKED_CAST")
                            DiagTest(
                                id = tm["id"]?.toString() ?: "",
                                status = tm["status"]?.toString() ?: "passed",
                                value = tm["value"],
                                unit = tm["unit"]?.toString(),
                                simulated = tm["simulated"] as? Boolean ?: false,
                                meta = tm["meta"] as? Map<String, Any?>
                            )
                        }
                        val devMap = diagMap["device"] as? Map<*, *>
                        val report = DiagnosticsReport(
                            device = DeviceInfo(model = devMap?.get("model")?.toString() ?: "Verified Device"),
                            category = category,
                            skipped = diagMap["skipped"] as? Boolean ?: false,
                            tests = diagTests
                        )
                        bench = BenchScore.grade(report)
                    } else {
                        bench = null
                    }
                } else {
                    phase = "4/4 Finalizing…"
                    bench = null
                }
                phase = "Done"
            } catch (e: Exception) {
                error = "Failed at ${phase}: ${e.message}"
            } finally {
                running = false
            }
        }
    }

    LaunchedEffect(listingId) { run() }

    fun strMap(m: Any?): Map<String, Any> = @Suppress("UNCHECKED_CAST") ((m as? Map<*, *>)?.entries
        ?.associate { (it.key as? String ?: "?") to (it.value as Any) } ?: emptyMap())

    val listingState = rememberApi(listingId) { MarketplaceRepository.listing(token, listingId.orEmpty()) }
    val submissionUrl = (listingState as? ApiState.Success)?.data?.submission_url
    val clipboard = LocalClipboardManager.current

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Device score", style = MaterialTheme.typography.headlineMedium)
        Text("Listing ${listingId?.take(8)}…", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        Text(phase, style = MaterialTheme.typography.bodyMedium)
        if (error.isNotEmpty()) {
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
            Button(onClick = { run() }, enabled = !running, modifier = Modifier.padding(top = 8.dp)) { Text("Retry") }
        }

        // Aggregate bench score
        bench?.let { b ->
            Card(Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${b.total}/100", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold))
                    Text(b.band, style = MaterialTheme.typography.titleMedium)
                    val diagScore = (scores?.get("diagnostic_score") as? Number)?.toInt()
                    Text("Backend contract score: ${diagScore ?: "…"}/100 · ${b.modules.count { it.counted }}/${b.modules.size} modules counted",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Hardware modules", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            b.modules.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { m -> ModuleTile(m, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        if (bench == null && category in DIAGNOSTIC_CATEGORIES) {
            Card(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Device Health", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Device has not been tested. Hardware and internal components have not been independently verified.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        val images = strMap(scores?.get("images"))
        val condition = strMap(scores?.get("condition") ?: vision?.get("condition"))
        val decision = strMap(scores?.get("decision") ?: risk?.get("decision"))
        val badge = (scores?.get("risk") as? Map<*, *>)?.get("badge") ?: risk?.get("badge")
        val hardStops = ((decision["hard_stops"] as? List<*>) ?: emptyList<Any>())

        Card(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("Photos & vision", style = MaterialTheme.typography.titleMedium)
                Text("Photos: ${images["count"] ?: 0}/8 angles (${((images["angles"] as? List<*>) ?: emptyList<Any>()).size} covered — 6 required, 45° optional)")
                Text("Condition: ${condition["class"] ?: condition["label"] ?: "…"}")
            }
        }

        val assessment = scores?.get("assessment") as? Map<*, *>
        if (assessment != null) {
            val identity = assessment["product_identity"] as? Map<*, *>
            val overall = assessment["overall_assessment"]?.toString()
            val unverified = assessment["unverified_claims"] as? List<*>
            val discrepancies = assessment["discrepancies"] as? List<*>

            Card(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("AI Verification & Reconciliation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    if (identity != null) {
                        Text(
                            "Identity: ${identity["brand"] ?: "Unknown"} ${identity["model"] ?: "Unknown"} (${identity["verification_status"]})",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (!overall.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(overall, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!discrepancies.isNullOrEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        discrepancies.forEach { disc ->
                            Text("⚠️ $disc", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (!unverified.isNullOrEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text("Unverified: ${unverified.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (risk != null) {
            Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Risk", style = MaterialTheme.typography.titleMedium)
                    Text("Adjusted: ${risk!!["adjusted_score"]}  (raw ${risk!!["raw_score"]})")
                    Text("Physical: ${(strMap(risk!!["physical_risk"]))["score"]}  •  " +
                        "Diagnostic: ${(strMap(risk!!["diagnostic_risk"]))["score"]}  •  " +
                        "Behavioral: ${(strMap(risk!!["behavioral_risk"]))["score"]}")
                }
            }
        }

        if (decision.isNotEmpty()) {
            val status = decision["status"] as? String ?: "review"
            val color = when (status) {
                "approved" -> MaterialTheme.colorScheme.primary
                "blocked" -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.tertiary
            }
            val reviewReasons = (decision["review_reasons"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            val actionableTips = (decision["actionable_tips"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()
            val hasMismatch = reviewReasons.any { it.contains("Device mismatch", ignoreCase = true) } || decision["reason_code"] == "REVIEW_DEVICE_MISMATCH"

            Card(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
                border = BorderStroke(1.5.dp, color.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = when (status) {
                                "approved" -> Icons.Default.CheckCircle
                                "blocked" -> Icons.Default.Cancel
                                else -> Icons.Default.Warning
                            },
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = when (status) {
                                "approved" -> "Verification Decision: Approved"
                                "blocked" -> "Verification Decision: In Review"
                                else -> "Verification Decision: In Review"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = color
                        )
                    }

                    if (badge != null) {
                        Spacer(Modifier.height(4.dp))
                        Text("Trust Badge: $badge", style = MaterialTheme.typography.labelMedium)
                    }

                    if (hardStops.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        hardStops.forEach { Text("⛔ $it", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }

                    // Prominent Device Hardware Mismatch Warning Banner
                    if (hasMismatch) {
                        Spacer(Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Device Hardware Mismatch", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                }
                                Spacer(Modifier.height(6.dp))
                                val mismatchMsg = reviewReasons.firstOrNull { it.contains("Device mismatch", ignoreCase = true) }
                                    ?: "The physical hardware tested does not match the device specified in the listing."
                                Text(mismatchMsg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Medium)
                            }
                        }
                    }

                    // Reasons why the listing is under review
                    if (reviewReasons.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("Why is this listing in review?", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        reviewReasons.forEach { reason ->
                            Row(Modifier.padding(vertical = 3.dp)) {
                                Text("• ", fontWeight = FontWeight.Bold, color = color)
                                Text(reason, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    } else if (decision["reason_code"] != null) {
                        Spacer(Modifier.height(6.dp))
                        Text("Reason: ${decision["reason_code"]}", style = MaterialTheme.typography.bodySmall)
                    }

                    // Actionable steps for seller to get listing approved
                    if (actionableTips.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        Text("What you can do to resolve this:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        actionableTips.forEachIndexed { idx, tip ->
                            Row(Modifier.padding(vertical = 2.dp)) {
                                Text("${idx + 1}. ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text(tip, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (submissionUrl != null) {
            Card(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Share score link", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Send this link to the technician running the visual report. The moderated score becomes this listing's Trust badge.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(submissionUrl)) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy link")
                        }
                        Button(
                            onClick = {
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, submissionUrl)
                                }
                                ctx.startActivity(Intent.createChooser(send, "Share score link"))
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Back to Home") }
    }
}

@Composable
fun ModuleTile(m: ModuleScore, modifier: Modifier = Modifier) {
    val color = when {
        !m.counted -> MaterialTheme.colorScheme.outline
        m.score >= 80 -> MaterialTheme.colorScheme.primary
        m.score >= 50 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Card(modifier, colors = CardDefaults.cardColors(
        containerColor = if (m.counted) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Badge(containerColor = color) { Text(if (!m.counted) "N/A" else "${m.score}") }
                Spacer(Modifier.width(8.dp))
                Text(m.label, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
            }
            Spacer(Modifier.height(6.dp))
            if (m.counted) {
                LinearProgressIndicator(progress = { m.score / 100f }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(4.dp))
            }
            Text(m.detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
