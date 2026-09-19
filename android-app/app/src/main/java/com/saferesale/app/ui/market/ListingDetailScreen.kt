package com.saferesale.app.ui.market

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.saferesale.app.data.ApiClient
import com.saferesale.app.data.marketplace.MarketplaceRepository
import com.saferesale.app.domain.model.MarketListing
import com.saferesale.app.domain.model.MlModelsReport
import com.saferesale.app.domain.model.MlModelVerdict
import com.saferesale.app.ui.components.OSMMapPicker
import com.saferesale.app.ui.theme.ActivateGreen
import com.saferesale.app.ui.theme.CoreVTheme
import com.saferesale.app.ui.theme.SoldOutAmber
import com.saferesale.app.ui.theme.TerritoryAccent
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListingDetailScreen(
    token: String?,
    listingId: String,
    onBack: () -> Unit,
    onReport: (String) -> Unit,
    onContact: (String) -> Unit,
    onBookInspection: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val listingState = rememberApi(listingId) { MarketplaceRepository.listing(token, listingId) }
    val scoresState = rememberApi(listingId) {
        MarketplaceRepository.latestScores(token, listingId)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Ad details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onReport(listingId) }) {
                        Icon(Icons.Default.Flag, contentDescription = "Report")
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = Color.White, shadowElevation = 8.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = { onBookInspection(listingId) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Inspection")
                    }
                    Button(
                        onClick = { onContact(listingId) },
                        modifier = Modifier.weight(1.4f).height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Contact seller", fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
    ) { padding ->
        when (val s = listingState) {
            is ApiState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is ApiState.Error -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                ErrorBox(s.message) { }
            }

            is ApiState.Success -> {
                val listing = s.data
                val scoresData = (scoresState as? ApiState.Success)?.data
                val riskScore = scoresData?.risk?.adjusted_score
                val (trustScoreNr, trustLabel) = trustVerdict(listing)
                            val band = scoreToBand(riskScore) ?: riskBandOf(listing)
                val diagnosticScore = scoresData?.diagnostic_score
                val decisionStatus = scoresData?.decision?.get("status")?.toString()
                val conditionLabel = scoresData?.condition?.get("label")?.toString()
                    ?: scoresData?.condition?.get("result")?.toString()
                    ?: scoresData?.condition?.get("predicted")?.toString()
                val imageKeysState = rememberApi(listingId) {
                    MarketplaceRepository.images(token, listingId)
                }

                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    PhotoPagerCard(
                        keys = (imageKeysState as? ApiState.Success)?.data.orEmpty().map { it.stored_key }.filterNotNull(),
                    )

                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            formatPrice(listing.price),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = TerritoryAccent,
                            modifier = Modifier.weight(1f),
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            if (verifiedListing(listing)) {
                                VerifiedPill()
                            }
                            if (trustScoreNr != null) {
                                Spacer(Modifier.height(6.dp))
                                TrustPill(trustScoreNr, trustTint(listing))
                            }
                            if (band != null) {
                                Spacer(Modifier.height(6.dp))
                                RiskBadge(band = band)
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        listing.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${formatCategory(listing.category)} · ${formatCondition(listing.seller_condition) ?: "Inspected"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            formatDate(listing.created_at),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    ScoresCard(
                        trustScore = trustScoreNr,
                        trustLabel = trustLabel,
                        riskScore = riskScore,
                        riskBand = band,
                        diagnosticScore = diagnosticScore,
                        conditionLabel = conditionLabel,
                        decisionStatus = decisionStatus,
                    )

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Spacer(Modifier.height(14.dp))
                    Text(
                        "Details",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    ExpandableDescription(
                        text = listing.notes
                            ?: "This device passed a full hardware inspection. Photo evidence and the risk review are recorded on the SafeResale check before this listing went live.",
                    )

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Spacer(Modifier.height(14.dp))
                    AiAnalysisCard(listing.ml_models)

                    Spacer(Modifier.height(18.dp))
                    Text(
                        "Specifications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    SpecRows(
                        rows = listOfNotNull(
                            "Brand" to listing.brand,
                            "Model" to listing.model,
                            "Storage" to listing.storage,
                            "Battery health" to listing.battery_health,
                            "Year" to listing.year?.toString(),
                            "Odometer" to listing.odometer?.let { "$it km" },
                            "Condition" to formatCondition(listing.seller_condition),
                        )
                    )

                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    if (listing.latitude != null && listing.longitude != null) {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Location",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        OSMMapPicker(
                            initialLat = listing.latitude!!,
                            initialLng = listing.longitude!!,
                            onLocationChange = { _, _ -> },
                            modifier = Modifier.fillMaxWidth().height(140.dp),
                            readOnly = true,
                        )
                        if (!listing.address.isNullOrBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    listing.address,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    InspectionStatusCard(token, listingId)

                    Spacer(Modifier.height(14.dp))
                    SubmissionStatusBlock(listing)

                    Spacer(Modifier.height(14.dp))
                    VerificationBlock()

                    Spacer(Modifier.height(14.dp))
                    ReportAdBlock(onReport = { onReport(listingId) })

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PhotoPagerCard(keys: List<String>) {
    val full = keys.map { "${ApiClient.baseUrl}/uploads/$it" }
    val pagerState = rememberPagerState(pageCount = { if (full.isEmpty()) 1 else full.size })
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0x21000000)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (full.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Photo,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(56.dp),
                    )
                }
            } else {
                HorizontalPager(
                    state = pagerState,
                    key = { full[it] },
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    AsyncImage(
                        model = full[page],
                        contentDescription = "Listing photo ${page + 1}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (full.size > 1) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    repeat(full.size) { i ->
                        Box(
                            Modifier
                                .size(if (pagerState.currentPage == i) 8.dp else 6.dp)
                                .background(
                                    if (pagerState.currentPage == i) TerritoryAccent else Color.White.copy(alpha = 0.6f),
                                    CircleShape,
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VerifiedPill() {
    Surface(color = TerritoryAccent.copy(alpha = 0.12f), shape = CircleShape) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Verified,
                contentDescription = null,
                tint = TerritoryAccent,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "Verified",
                style = MaterialTheme.typography.labelMedium,
                color = TerritoryAccent,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun RiskBadge(band: String, modifier: Modifier = Modifier) {
    val color = riskColor(band)
    Surface(color = color.copy(alpha = 0.12f), shape = CircleShape, modifier = modifier) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(
                riskLabel(band),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun ScorePill(label: String, tint: Color) {
    Surface(color = tint.copy(alpha = 0.12f), shape = CircleShape) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Full score report: trust, risk, diagnostics, condition and decision badges. */
@Composable
private fun ScoresCard(
    trustScore: Int?,
    trustLabel: String?,
    riskScore: Double?,
    riskBand: String?,
    diagnosticScore: Int?,
    conditionLabel: String?,
    decisionStatus: String?,
) {
    val pills = mutableListOf<Pair<String, Color>>()
    if (trustScore != null) {
        val tLabel = trustLabel?.let { " · $it" } ?: ""
        pills.add("Trust $trustScore/100$tLabel" to trustTint(trustScore))
    }
    if (riskScore != null && riskBand != null) {
        pills.add("Risk ${riskScore.roundToInt()}/100 · ${riskLabel(riskBand)}" to riskColor(riskBand))
    }
    if (diagnosticScore != null) {
        pills.add("Diagnostics $diagnosticScore/100" to ActivateGreen)
    }
    val cond = conditionLabel?.takeIf { it.isNotBlank() }
    if (cond != null) {
        pills.add("Condition $cond" to conditionTint(cond))
    }
    decisionInfo(decisionStatus)?.let { (label, color) ->
        pills.add(label to color)
    }
    if (pills.isEmpty()) return

    val allGood = (trustScore ?: 0) >= 70 &&
        (riskScore ?: Double.MAX_VALUE) < 30 &&
        decisionStatus?.lowercase() != "blocked"

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
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Score report",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                if (allGood) {
                    Surface(color = TerritoryAccent.copy(alpha = 0.12f), shape = RoundedCornerShape(5.dp)) {
                        Text(
                            "ALL CHECKS PASSED",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = TerritoryAccent,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            pills.chunked(2).forEach { rowPills ->
                Row(Modifier.padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowPills.forEach { pill ->
                        ScorePill(pill.first, pill.second)
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "Generated by SafeResale's verification pipeline — trust, risk and diagnostics are model-backed.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun conditionTint(label: String): Color {
    val l = label.lowercase()
    return when {
        l.contains("good") || l.contains("excellent") || l.contains("perfect") ||
            l.contains("pass") || l.contains("new") -> ActivateGreen
        l.contains("poor") || l.contains("fail") || l.contains("damage") ||
            l.contains("broken") || l.contains("bad") -> MaterialTheme.colorScheme.error
        else -> SoldOutAmber
    }
}

@Composable
private fun decisionInfo(status: String?): Pair<String, Color>? = when (status?.lowercase()) {
    "approved" -> "Approved" to ActivateGreen
    "blocked", "rejected" -> "Blocked" to MaterialTheme.colorScheme.error
    "review", "verifying", "submitted" -> "In review" to SoldOutAmber
    else -> null
}

@Composable
private fun ExpandableDescription(text: String) {
    var expanded by remember { mutableStateOf(false) }
    val collapsed = text.length > 140
    Column {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else if (collapsed) 3 else Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
        )
        if (collapsed) {
            Spacer(Modifier.height(2.dp))
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.height(32.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = TerritoryAccent),
            ) {
                Text(if (expanded) "Read less" else "Read more", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun AiAnalysisCard(models: MlModelsReport?) {
    val items = models?.list.orEmpty()
    if (items.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0x21000000)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Science, contentDescription = null, tint = TerritoryAccent, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("AI Analysis", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text("What SafeResale's 4 ML models found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            items.forEach { model ->
                AiModelRow(model)
                if (model != items.last()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

@Composable
private fun AiModelRow(model: MlModelVerdict) {
    val tintColor = if (model.ok) ActivateGreen else MaterialTheme.colorScheme.error
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = tintColor.copy(alpha = 0.12f), shape = RoundedCornerShape(4.dp)) {
                Text(
                    model.id.uppercase(),
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = tintColor,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(model.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text(
                    model.model + if (model.simulated) " (simulated)" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(model.verdict, style = MaterialTheme.typography.bodySmall)
        if (model.items.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                model.items.forEach { item ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            "${item.label}: ${item.value}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SpecRows(rows: List<Pair<String, String?>>) {
    val present = rows.filter { it.second != null }
    if (present.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0x21000000)),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
            present.forEachIndexed { index, (label, value) ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        value.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (index != present.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun VerificationBlock() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0x21000000)),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(TerritoryAccent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Shield,
                    contentDescription = null,
                    tint = TerritoryAccent,
                    modifier = Modifier.size(30.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "SafeResale verified",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(color = TerritoryAccent, shape = RoundedCornerShape(5.dp)) {
                        Text(
                            "VERIFIED",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "This device passed a full hardware inspection and risk review before the listing went live.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ReportAdBlock(onReport: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0x21000000)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Did you find any problem with this ad?",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Let us know — our moderation team will review this listing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = onReport,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape = RoundedCornerShape(22.dp),
            ) {
                Text("Report this ad", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun InspectionStatusCard(token: String?, listingId: String) {
    val state = rememberApi("inspection-$listingId") { MarketplaceRepository.inspections(token) }
    val mine = (state as? ApiState.Success)?.data?.firstOrNull { it["listing_id"] == listingId }
    val insp = mine ?: return
    val status = insp["status"]?.toString() ?: "requested"
    val color = when (status) {
        "completed" -> ActivateGreen
        "cancelled", "rejected" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0x21000000)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Event,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Inspection",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                ScorePill(statusLabel(status), color)
            }
            insp["preferred_date"]?.toString()?.let { date ->
                Spacer(Modifier.height(6.dp))
                Text(
                    "Preferred date: $date",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            insp["note"]?.toString()?.takeIf { it.isNotBlank() }?.let { note ->
                Spacer(Modifier.height(2.dp))
                Text(
                    note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SubmissionStatusBlock(listing: MarketListing) {
    val fit = listing.is_fit_to_show ?: return
    val (title, body, color) = if (fit) {
        Triple(
            "Trust badge live",
            "This device's technician score passed moderation and is shown to buyers as its Trust badge.",
            ActivateGreen,
        )
    } else {
        Triple(
            "Trust badge withheld",
            "A technician score was submitted but is still under moderation. The badge stays hidden until the report is approved.",
            SoldOutAmber,
        )
    }
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
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun statusLabel(status: String): String = when (status) {
    "requested" -> "Requested"
    "assigned" -> "Inspector assigned"
    "in_progress" -> "In progress"
    "completed" -> "Completed"
    "cancelled" -> "Cancelled"
    "rejected" -> "Rejected"
    else -> status
}

@Preview(showBackground = true, widthDp = 400)
@Composable
private fun ScoresCardPreview() {
    CoreVTheme(darkTheme = false) {
        Column(Modifier.padding(16.dp)) {
            ScoresCard(
                trustScore = 85,
                trustLabel = "Good",
                riskScore = 14.8,
                riskBand = "low",
                diagnosticScore = 92,
                conditionLabel = "Good",
                decisionStatus = "approved",
            )
            Spacer(Modifier.height(12.dp))
            ScoresCard(
                trustScore = 38,
                trustLabel = "Poor",
                riskScore = 61.0,
                riskBand = "moderate",
                diagnosticScore = 44,
                conditionLabel = "Fair",
                decisionStatus = "review",
            )
        }
    }
}