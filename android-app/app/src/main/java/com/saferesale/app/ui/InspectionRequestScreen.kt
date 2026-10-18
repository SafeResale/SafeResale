package com.saferesale.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saferesale.app.data.marketplace.MarketplaceRepository
import com.saferesale.app.domain.model.MarketListing
import com.saferesale.app.ui.market.ApiState
import com.saferesale.app.ui.market.formatPrice
import com.saferesale.app.ui.market.rememberApi
import com.saferesale.app.ui.theme.ActivateGreen
import com.saferesale.app.ui.theme.TerritoryAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val dateOptions = listOf(
    "Today",
    "Tomorrow",
    "Within 2 days",
    "Within 5 days",
    "No preference",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InspectionRequestScreen(
    token: String?,
    listingId: String,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val listingState = rememberApi(listingId) { MarketplaceRepository.listing(token, listingId) }

    // Live inspections polling state
    var inspectionsList by remember { mutableStateOf<List<Map<String, Any>>?>(null) }
    var loadingInspections by remember { mutableStateOf(true) }

    suspend fun refreshInspections() {
        try {
            inspectionsList = MarketplaceRepository.inspections(token)
        } catch (_: Exception) {
        } finally {
            loadingInspections = false
        }
    }

    // Auto-polling effect to update delivery status live (like Swiggy / Uber)
    LaunchedEffect(listingId, token) {
        while (true) {
            refreshInspections()
            delay(4000) // Poll every 4 seconds
        }
    }

    val activeInspection = remember(inspectionsList) {
        inspectionsList?.firstOrNull { it["listing_id"] == listingId }
    }

    var date by remember { mutableStateOf("Within 2 days") }
    var note by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val currentUserId = remember(token) { extractUserId(token) }
    val listingObj = (listingState as? ApiState.Success)?.data
    val isOwnListing = currentUserId != null && listingObj != null && currentUserId == listingObj.seller_id

    val isTrackingMode = activeInspection != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isTrackingMode) "Live Inspection Tracker" else "Book an Inspection",
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isTrackingMode) {
                        IconButton(onClick = { scope.launch { refreshInspections() } }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = TerritoryAccent)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black,
                ),
            )
        },
        containerColor = Color(0xFFF6F5FA),
        bottomBar = {
            if (!isTrackingMode) {
                Surface(color = Color.White, shadowElevation = 8.dp) {
                    if (isOwnListing) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "You cannot request an inspection for your own listing",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        Button(
                            onClick = {
                                scope.launch {
                                    submitting = true
                                    error = null
                                    try {
                                        MarketplaceRepository.createInspection(
                                            token,
                                            listingId,
                                            date,
                                            note.trim().takeIf { it.isNotBlank() }
                                        )
                                        refreshInspections()
                                    } catch (e: Exception) {
                                        error = e.message ?: "Could not book the inspection"
                                    } finally {
                                        submitting = false
                                    }
                                }
                            },
                            enabled = !submitting,
                            modifier = Modifier.fillMaxWidth().padding(12.dp).height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                        ) {
                            if (submitting) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Confirm inspection request", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            when (val s = listingState) {
                is ApiState.Loading ->
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                is ApiState.Error ->
                    Text(s.message, color = MaterialTheme.colorScheme.error)

                is ApiState.Success -> {
                    val listing = s.data
                    header(listing)

                    Spacer(Modifier.height(16.dp))

                    if (isTrackingMode && activeInspection != null) {
                        // ── Delivery-Style Live Tracker View ──
                        LiveInspectionTrackerView(
                            inspection = activeInspection,
                            listing = listing,
                        )
                    } else {
                        // ── Booking Request Form View ──
                        BookingFormView(
                            date = date,
                            onDateChange = { date = it },
                            note = note,
                            onNoteChange = { note = it },
                            error = error,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dedicated Delivery-App Style Live Tracking UI
 */
@Composable
private fun LiveInspectionTrackerView(
    inspection: Map<String, Any>,
    listing: MarketListing,
) {
    val status = inspection["status"]?.toString() ?: "requested"
    val inspectorName = inspection["inspector_name"]?.toString() ?: "Certified Field Inspector"

    val statusIndex = when (status) {
        "requested" -> 1
        "assigned" -> 2
        "travelling" -> 3
        "arrived", "in_progress" -> 4
        "completed" -> 5
        else -> 1
    }

    val score = (inspection["score"] as? Number)?.toDouble()
        ?: ((inspection["findings"] as? Map<*, *>)?.get("score") as? Number)?.toDouble()

    val findings = inspection["findings"] as? Map<*, *>

    // Live Status Banner
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0x21000000)),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (status == "completed") ActivateGreen.copy(alpha = 0.12f)
                            else TerritoryAccent.copy(alpha = 0.12f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (status == "completed") Icons.Default.CheckCircle else Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (status == "completed") ActivateGreen else TerritoryAccent,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (status) {
                            "requested" -> "Awaiting Inspector Acceptance ⏱️"
                            "assigned" -> "Inspector Assigned! 🔍"
                            "travelling" -> "Inspector On the Way 🛵"
                            "arrived" -> "Inspector Arrived at Location 📍"
                            "in_progress" -> "Hardware Diagnostic Underway ⚙️"
                            "completed" -> "Inspection Certified & Scored ✅"
                            else -> status.replaceFirstChar { it.uppercase() }
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (status == "completed") ActivateGreen else Color.Black,
                    )
                    Text(
                        when (status) {
                            "requested" -> "Dispatched to nearby certified inspectors."
                            "assigned" -> "$inspectorName is reviewing location details."
                            "travelling" -> "Travelling to inspect the physical device."
                            "arrived" -> "Meeting seller to inspect hardware."
                            "in_progress" -> "Testing screen, battery, cameras, and biometrics."
                            "completed" -> "AI Quality rating computed and verified."
                            else -> "Status: $status"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (score != null) {
                    Surface(
                        color = ActivateGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "${score.toInt()}/100",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = ActivateGreen
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(16.dp))

            // Step Progress Stepper
            val steps = listOf(
                Triple("Request Placed", "Buyer requested on-site inspection", 1),
                Triple("Inspector Assigned", if (statusIndex >= 2) "$inspectorName confirmed" else "Waiting for inspector pickup", 2),
                Triple("On the Way 🛵", if (statusIndex >= 3) "En route to seller's location" else "Transit to device address", 3),
                Triple("Physical Diagnostic ⚙️", if (statusIndex >= 4) "18-point hardware check" else "On-site hardware testing", 4),
                Triple("Certified & Scored ✅", if (statusIndex >= 5) "AI Quality Score: ${score?.toInt() ?: 85}/100" else "Trust certificate generation", 5),
            )

            steps.forEachIndexed { idx, (stepTitle, stepDesc, stepNum) ->
                val isCompleted = statusIndex > stepNum || (statusIndex == 5 && stepNum == 5)
                val isCurrent = statusIndex == stepNum && statusIndex < 5

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(28.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isCompleted -> ActivateGreen
                                        isCurrent -> TerritoryAccent
                                        else -> Color(0xFFE2E2E8)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isCompleted) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else {
                                Text(
                                    "$stepNum",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCurrent) Color.White else Color.Gray
                                )
                            }
                        }

                        if (idx < steps.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(30.dp)
                                    .background(
                                        if (isCompleted) ActivateGreen else Color(0xFFE2E2E8)
                                    )
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = if (idx < steps.lastIndex) 14.dp else 0.dp)
                    ) {
                        Text(
                            stepTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isCurrent || isCompleted) FontWeight.Bold else FontWeight.Normal,
                            color = if (isCurrent) TerritoryAccent else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            stepDesc,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Inspector Card (if assigned)
    if (statusIndex >= 2) {
        Spacer(Modifier.height(14.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, Color(0x21000000)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8EEF5)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = TerritoryAccent,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            inspectorName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = ActivateGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        "Certified Hardware Technician",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFB800), modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(3.dp))
                        Text("4.9 Rating • 120+ Audits", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                }
            }
        }
    }

    // Inspection Findings & AI Report (when completed)
    if (status == "completed" && findings != null) {
        Spacer(Modifier.height(14.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            border = BorderStroke(1.dp, ActivateGreen.copy(alpha = 0.3f)),
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = ActivateGreen, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "AI Inspection Certificate",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ActivateGreen
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "Overall Verdict: ${findings["verdict"] ?: "Passed"}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )

                findings["notes"]?.toString()?.takeIf { it.isNotBlank() }?.let { n ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Inspector Feedback: \"$n\"",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                val defects = (findings["defects_found"] as? List<*>)?.filterIsInstance<String>()
                if (!defects.isNullOrEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Identified Defects: ${defects.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/**
 * Standard Booking Form View
 */
@Composable
private fun BookingFormView(
    date: String,
    onDateChange: (String) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    error: String?,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0x21000000)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = TerritoryAccent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "How it works",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "A certified inspector visits the device in person (screen, battery, cameras, chassis). Track their arrival live like a delivery app. You only pay after reviewing the official AI report.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    HorizontalDivider(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
    Spacer(Modifier.height(12.dp))

    Text(
        "Preferred date",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(dateOptions.size) { i ->
            val option = dateOptions[i]
            val isSelected = option == date
            Surface(
                onClick = { onDateChange(option) },
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) TerritoryAccent else Color.White,
                border = BorderStroke(1.dp, if (isSelected) TerritoryAccent else Color(0x22000000)),
            ) {
                Text(
                    option,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) Color.White else Color.Black,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Text(
        "Note for the seller & inspector (optional)",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = note,
        onValueChange = { onNoteChange(it.take(240)) },
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("e.g. Can visit after 5 pm, check charging port carefully…") },
        shape = RoundedCornerShape(14.dp),
        minLines = 3,
    )

    error?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun header(listing: MarketListing) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(listing.title.ifBlank { "Device" }, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(
                formatPrice(listing.price),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TerritoryAccent,
            )
        }
    }
}

private fun extractUserId(token: String?): String? {
    if (token.isNullOrBlank()) return null
    return try {
        val parts = token.split(".")
        if (parts.size >= 2) {
            val decoded = android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
            val json = org.json.JSONObject(String(decoded, Charsets.UTF_8))
            json.optString("sub", null).takeIf { !it.isNullOrBlank() }
        } else null
    } catch (_: Exception) {
        null
    }
}