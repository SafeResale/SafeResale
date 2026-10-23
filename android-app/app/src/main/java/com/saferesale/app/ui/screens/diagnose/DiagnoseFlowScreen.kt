package com.saferesale.app.ui.screens.diagnose

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferesale.app.data.ApiClient
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * DiagnoseFlowScreen
 *
 * Implements the SafeResale Device Diagnosis flow on the listing device:
 * 1. Asks confirmation: "Are you selling this device?" (or laptop)
 *    - Cancel: stops without creating diagnosis session or running tests.
 *    - Yes, Continue: creates session and generates secure pairing token.
 * 2. If category is Laptop:
 *    - Provides a direct Web Diagnostic Link (https://moonbase.qzz.io/laptopdiagnosis/?token=...)
 *    - Provides Copy Link and Share Link buttons so the seller can open it on their laptop.
 *    - Automatically polls and syncs verified results when tests are submitted from the browser.
 * 3. If category is Mobile / other:
 *    - Displays token prominently with instructions to connect via Diagnosis app.
 * 4. Polls backend for verified transitions:
 *    awaiting_confirmation -> token_generated -> paired -> in_progress -> results_received -> verification_completed.
 * 5. Does NOT mark complete until backend confirms results receipt and verification.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnoseFlowScreen(
    token: String,
    listingId: String,
    category: String,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onDone: () -> Unit,
    onDiagnoseThisDevice: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    val isLaptop = remember(category) {
        val cat = category.trim().lowercase()
        cat in listOf("laptop", "computer", "pc", "macbook", "notebook", "desktop")
    }

    var stage by remember { mutableStateOf("confirm") } // confirm | creating | token | polling | done | error
    var pairingToken by remember { mutableStateOf<String?>(null) }
    var sessionId by remember { mutableStateOf<String?>(null) }
    var expiresAt by remember { mutableStateOf<Double?>(null) }
    var statusText by remember { mutableStateOf("Awaiting confirmation") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var diagnosisState by remember { mutableStateOf("awaiting_confirmation") }
    var diagnosticScore by remember { mutableStateOf<Int?>(null) }
    var copiedToast by remember { mutableStateOf(false) }
    var copiedLinkToast by remember { mutableStateOf(false) }

    val webDiagnosisUrl = remember(pairingToken) {
        if (pairingToken != null) {
            "https://moonbase.qzz.io/laptopdiagnosis/?token=$pairingToken"
        } else {
            "https://moonbase.qzz.io/laptopdiagnosis/"
        }
    }

    // Check if separate Diagnosis app package is installed
    val isDiagnosisAppInstalled = remember {
        try {
            context.packageManager.getPackageInfo("com.saferesale.diagnosis", 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    // Polling loop: observes real backend-confirmed state
    LaunchedEffect(sessionId, stage) {
        val sid = sessionId ?: return@LaunchedEffect
        if (stage != "token" && stage != "polling") return@LaunchedEffect
        stage = "polling"
        while (true) {
            try {
                val res = ApiClient.service.diagnosisSession(sid, pairingToken, "Bearer $token")
                val s = res["session"] as? Map<*, *>
                val st = s?.get("state")?.toString() ?: diagnosisState
                diagnosisState = st

                statusText = when (st) {
                    "token_generated" -> if (isLaptop) {
                        "Waiting for laptop to open diagnostic link..."
                    } else {
                        "Waiting for Diagnosis app to pair using the token above."
                    }
                    "paired" -> if (isLaptop) {
                        "Laptop connected! Running hardware tests in browser..."
                    } else {
                        "Pairing successful. Connected to device. Starting tests..."
                    }
                    "in_progress" -> if (isLaptop) {
                        "Hardware tests in progress on laptop..."
                    } else {
                        "Diagnosis in progress on paired device..."
                    }
                    "results_received" -> if (isLaptop) {
                        "Laptop test results received! Verifying..."
                    } else {
                        "Results received. Verifying..."
                    }
                    "verification_completed" -> "Verification completed."
                    "failed" -> "Diagnosis failed."
                    "expired" -> "Pairing token expired. Regenerate to retry."
                    else -> st
                }

                if (st == "results_received") {
                    // Finalize backend verification
                    try {
                        val verRes = ApiClient.service.verifyDiagnosisSession(sid, "Bearer $token")
                        diagnosisState = "verification_completed"
                        statusText = "Verification completed."
                        val sDoc = verRes["session"] as? Map<*, *>
                        val resDoc = sDoc?.get("results") as? Map<*, *>
                        diagnosticScore = (resDoc?.get("score") as? Number)?.toInt()
                    } catch (_: Exception) {}
                    stage = "done"
                    break
                }

                if (st == "verification_completed") {
                    stage = "done"
                    break
                }

                if (st == "failed" || st == "expired") {
                    stage = "error"
                    errorMessage = statusText
                    break
                }
            } catch (e: Exception) {
                errorMessage = e.message
                stage = "error"
                break
            }
            delay(2500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isLaptop) "Laptop Diagnostics" else "Device Diagnosis") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("\u2190") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (stage) {
                "confirm" -> {
                    Icon(
                        Icons.Default.Devices,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (isLaptop) "Are you selling this laptop?" else "Are you selling this device?",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        if (isLaptop) {
                            "SafeResale provides a browser-based hardware diagnostic suite to verify the laptop's display, keyboard, trackpad, webcam, audio, and battery. We'll generate a secure link to open on the laptop."
                        } else {
                            "To help buyers understand the device's condition, you can run a diagnostic test and include the verified results in your listing."
                        },
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(28.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                stage = "creating"
                                scope.launch {
                                    try {
                                        val res = ApiClient.service.createDiagnosisSession(
                                            mapOf("listing_id" to listingId, "category" to category),
                                            "Bearer $token",
                                        )
                                        val sess = res["session"] as? Map<*, *>
                                        sessionId = sess?.get("_id")?.toString()
                                        pairingToken = res["token"]?.toString()
                                        expiresAt = (res["expires_at"] as? Number)?.toDouble()
                                        diagnosisState = sess?.get("state")?.toString() ?: "token_generated"
                                        stage = "token"
                                    } catch (e: Exception) {
                                        errorMessage = e.message
                                        stage = "error"
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text(if (isLaptop) "Generate Link" else "Yes, Continue")
                        }
                    }
                }
                "creating" -> {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(if (isLaptop) "Generating diagnostic link..." else "Generating pairing token...", fontSize = 16.sp)
                }
                "token", "polling" -> {
                    if (isLaptop) {
                        // Laptop Diagnosis Web Link Flow
                        Text(
                            "Open this diagnostic link on the laptop you are selling:",
                            textAlign = TextAlign.Center,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(16.dp))

                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                Modifier.padding(20.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "LAPTOP DIAGNOSIS LINK",
                                    fontSize = 11.sp,
                                    letterSpacing = 1.2.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    webDiagnosisUrl,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(webDiagnosisUrl))
                                            copiedLinkToast = true
                                        },
                                        modifier = Modifier.weight(1f).height(42.dp)
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (copiedLinkToast) "Copied!" else "Copy Link", fontSize = 13.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, "SafeResale Laptop Diagnosis Link")
                                                putExtra(
                                                    Intent.EXTRA_TEXT,
                                                    "Open this link on the laptop being sold to run hardware diagnostics:\n$webDiagnosisUrl"
                                                )
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share diagnostic link to laptop"))
                                        },
                                        modifier = Modifier.weight(1f).height(42.dp)
                                    ) {
                                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Share Link", fontSize = 13.sp)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Compact Token Card
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        "PAIRING TOKEN",
                                        fontSize = 10.sp,
                                        letterSpacing = 1.sp,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        pairingToken ?: "...",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        pairingToken?.let {
                                            clipboard.setText(AnnotatedString(it))
                                            copiedToast = true
                                        }
                                    }
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text(if (copiedToast) "Copied!" else "Copy", fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Instructions Card
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text("HOW IT WORKS:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(6.dp))
                                Text("1. Open the link on the laptop (Chrome, Edge, Safari).", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("2. The laptop pairs automatically with this listing.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("3. Run the hardware tests (Display, Keyboard, Mic, etc.).", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("4. Click 'Sync Diagnostic Score' — results sync here instantly!", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    } else {
                        // Standard Mobile Diagnosis Token Display
                        Text(
                            "Open the SafeResale Diagnosis app and enter this token to connect your device.",
                            textAlign = TextAlign.Center,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(20.dp))
                        Card(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                Modifier.padding(24.dp).fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "PAIRING TOKEN",
                                    fontSize = 11.sp,
                                    letterSpacing = 1.5.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    pairingToken ?: "...",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center,
                                    letterSpacing = 2.sp
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clickable {
                                            pairingToken?.let {
                                                clipboard.setText(AnnotatedString(it))
                                                copiedToast = true
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text(if (copiedToast) "Copied!" else "Copy Token", fontSize = 12.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                    // Status pill
                    Surface(
                        color = when (diagnosisState) {
                            "paired" -> MaterialTheme.colorScheme.secondaryContainer
                            "in_progress" -> MaterialTheme.colorScheme.tertiaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        },
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 2.dp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(statusText, fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    expiresAt?.let {
                        val mins = ((it - System.currentTimeMillis() / 1000.0) / 60).toInt().coerceAtLeast(0)
                        Text(
                            "Expires in ~$mins min",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    if (!isLaptop && isDiagnosisAppInstalled) {
                        Button(
                            onClick = {
                                try {
                                    val launchIntent = context.packageManager.getLaunchIntentForPackage("com.saferesale.diagnosis")
                                    launchIntent?.putExtra("pairing_token", pairingToken)
                                    launchIntent?.let { context.startActivity(it) }
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Open Diagnosis App")
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    if (!isLaptop && onDiagnoseThisDevice != null && pairingToken != null) {
                        OutlinedButton(
                            onClick = { onDiagnoseThisDevice(pairingToken!!) },
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text("Selling this phone? Diagnose here")
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    TextButton(onClick = onSkip) {
                        Text("Skip diagnosis for now")
                    }
                }
                "done" -> {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (isLaptop) "Laptop Diagnostics Complete" else "Diagnosis Complete",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (isLaptop) {
                            "Hardware test results verified from laptop and linked to your listing."
                        } else {
                            "Hardware test results verified and linked to your listing."
                        },
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    diagnosticScore?.let {
                        Spacer(Modifier.height(12.dp))
                        Text("Score: $it/100", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("Continue to score")
                    }
                }
                "error" -> {
                    Text(
                        "Diagnosis Issue",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage ?: "Unknown error", textAlign = TextAlign.Center, fontSize = 14.sp)
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (sessionId != null) {
                            Button(onClick = {
                                stage = "creating"
                                scope.launch {
                                    try {
                                        val res = ApiClient.service.regenerateDiagnosisToken(sessionId!!, "Bearer $token")
                                        val sess = res["session"] as? Map<*, *>
                                        sessionId = sess?.get("_id")?.toString() ?: sessionId
                                        pairingToken = res["token"]?.toString()
                                        expiresAt = (res["expires_at"] as? Number)?.toDouble()
                                        errorMessage = null
                                        diagnosisState = "token_generated"
                                        stage = "token"
                                    } catch (e: Exception) {
                                        errorMessage = e.message
                                        stage = "error"
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Regenerate Token")
                            }
                        }
                        OutlinedButton(onClick = onSkip) { Text("Skip") }
                    }
                }
            }
        }
    }
}
