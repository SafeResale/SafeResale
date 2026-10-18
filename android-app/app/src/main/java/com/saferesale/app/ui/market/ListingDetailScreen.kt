package com.saferesale.app.ui.market

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.saferesale.app.domain.model.Defects
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

    val currentUserId = remember(token) { extractUserId(token) }
    val inspectionState = rememberApi("inspection-$listingId") { MarketplaceRepository.inspections(token) }
    val activeInspection = (inspectionState as? ApiState.Success)?.data?.firstOrNull { it["listing_id"] == listingId }
    val hasInspection = activeInspection != null
    val inspStatus = activeInspection?.get("status")?.toString() ?: ""

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
            val listing = (listingState as? ApiState.Success)?.data
            val isOwnListing = currentUserId != null && listing != null && currentUserId == listing.seller_id

            Surface(color = Color.White, shadowElevation = 8.dp) {
                if (isOwnListing) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = TerritoryAccent.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Text(
                                "This is your listing",
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TerritoryAccent,
                            )
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = { onBookInspection(listingId) },
                            modifier = Modifier.weight(1.1f).height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                            border = BorderStroke(
                                1.5.dp,
                                if (hasInspection) {
                                    if (inspStatus == "completed") ActivateGreen else TerritoryAccent
                                } else MaterialTheme.colorScheme.outline
                            ),
                        ) {
                            Icon(
                                if (inspStatus == "completed") Icons.Default.Check
                                else if (hasInspection) Icons.Default.Shield
                                else Icons.Default.Build,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (inspStatus == "completed") ActivateGreen else TerritoryAccent
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    inspStatus == "completed" -> "Report Ready ✅"
                                    hasInspection -> "Track Order 🛵"
                                    else -> "Inspection"
                                },
                                fontWeight = FontWeight.Bold,
                                color = if (inspStatus == "completed") ActivateGreen else TerritoryAccent
                            )
                        }
                        Button(
                            onClick = { onContact(listingId) },
                            modifier = Modifier.weight(1.3f).height(52.dp),
                            shape = RoundedCornerShape(26.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Contact seller", fontWeight = FontWeight.Bold)
                        }
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
                    InspectionStatusCard(token, listingId, onTrack = { onBookInspection(listingId) })

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
                    DefectsForCategoryCard(category = listing.category)

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

                    if (listing.category in listOf("car", "bike", "vehicle")) {
                        Spacer(Modifier.height(14.dp))
                        RtoDetailsCard(listing = listing)
                    }

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
    var fullscreenIndex by remember { mutableStateOf<Int?>(null) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0x21000000)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Color(0xFF0F172A)),
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { fullscreenIndex = page },
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = full[page],
                            contentDescription = "Listing photo ${page + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                // Tap to Zoom / View Full hint button on top right
                Surface(
                    onClick = { fullscreenIndex = pagerState.currentPage },
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.ZoomIn,
                            contentDescription = "Zoom photo",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Tap to Zoom",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                        )
                    }
                }
            }

            // Image counter & dots
            if (full.size > 1) {
                // Bottom center pagination dots
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
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

                // Image index badge (e.g. 1 / 4)
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 10.dp),
                ) {
                    Text(
                        "${pagerState.currentPage + 1} / ${full.size}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }

    // Fullscreen Pinch-to-Zoom & Pan Modal Dialog
    if (fullscreenIndex != null) {
        val initialPage = fullscreenIndex ?: 0
        FullscreenPhotoViewerDialog(
            urls = full,
            initialIndex = initialPage,
            onDismiss = { fullscreenIndex = null },
        )
    }
}

/**
 * Fullscreen Photo Viewer Dialog with:
 * - Full resolution rendering without cropping (ContentScale.Fit)
 * - Two-finger pinch-to-zoom (1.0x to 5.0x scale)
 * - Pan/drag when zoomed in
 * - Horizontal swipe across all posted photos
 * - Top close button and photo counter
 */
@Composable
private fun FullscreenPhotoViewerDialog(
    urls: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    val fullPagerState = rememberPagerState(initialPage = initialIndex, pageCount = { urls.size })

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            HorizontalPager(
                state = fullPagerState,
                key = { urls[it] },
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomablePhotoItem(url = urls[page])
            }

            // Top Bar with Close button and Page counter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onDismiss,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close full view",
                        tint = Color.White,
                    )
                }

                if (urls.size > 1) {
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            "${fullPagerState.currentPage + 1} of ${urls.size}",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            // Bottom hint
            Text(
                "Pinch with two fingers to zoom in / out",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            )
        }
    }
}

/**
 * Zoomable image element handling pinch-to-zoom scale and drag offset
 */
@Composable
private fun ZoomablePhotoItem(url: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                        val maxOffsetX = (size.width * (scale - 1)) / 2f
                        val maxOffsetY = (size.height * (scale - 1)) / 2f
                        offset = Offset(
                            x = (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                            y = (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY),
                        )
                    } else {
                        offset = Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = url,
            contentDescription = "Full photo",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DefectsForCategoryCard(category: String?) {
    val defects = Defects.forCategory(category)
    if (defects.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0x21000000)),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Build, contentDescription = null, tint = TerritoryAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Checked for this category", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(category ?: "—", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(3.dp))
            Text(
                when (category) {
                    "car", "vehicle" -> "Inspektlabs checklist: dents, paint, glass, lights, bumpers, tires, rust, water — per panel"
                    "bike" -> "Frame, wheels, tires, paint, rust, lights — transfers from car data until bike-specific set lands"
                    "furniture" -> "Surface, stain, water rings, wear, fabric tears, structural — honor QC standards"
                    "appliance" -> "Archimede faults: leaks, rust, noise, door seals — not just cosmetic"
                    else -> "Damage types tailored to this category — only relevant defects are weighted"
                },
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            // FlowRow of chips
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                defects.forEach { cls ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                        Text(Defects.label(cls), modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Listing damage in the score report is filtered to these types — other categories' defects show as “other category” in admin.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun maskRegNo(reg: String?, category: String?): String {
    val raw = reg?.ifBlank { null } ?: when (category) {
        "bike" -> "KA03HY9449"
        "car" -> "KA03AB1234"
        else -> "KA01AB1234"
    }
    if (raw.length < 6) return "****"
    return raw.take(4) + "****" + raw.takeLast(4)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RtoDetailsCard(listing: MarketListing) {
    var show by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val reg = listing.registration_no ?: when (listing.category) {
        "bike" -> "KA03HY9449"
        "car" -> "KA03AB1234"
        else -> null
    }
    val masked = maskRegNo(reg, listing.category)
    val comp = listing.compliance
    // demo fallback if compliance null and is vehicle
    val demoComp: Map<String, Any> = when {
        comp != null -> comp
        listing.category == "bike" -> mapOf(
            "registration_no" to (reg ?: "KA03HY9449"),
            "rc_status" to "Active",
            "insurance" to mapOf("provider" to "ICICI Lombard", "valid_till" to "2025-11-30", "type" to "Comprehensive"),
            "puc" to mapOf("valid_till" to "2025-12-15", "status" to "Valid"),
            "challan" to mapOf("pending_amount" to 6000, "count" to 2, "details" to "Signal jump + No helmet"),
            "demo" to true
        )
        listing.category == "car" -> mapOf(
            "registration_no" to (reg ?: "KA03AB1234"),
            "rc_status" to "Active",
            "insurance" to mapOf("provider" to "Bajaj Allianz", "valid_till" to "2025-10-20", "type" to "Comprehensive"),
            "puc" to mapOf("valid_till" to "2025-09-30", "status" to "Valid"),
            "challan" to mapOf("pending_amount" to 6000, "count" to 3, "details" to "Over-speed + Seatbelt + Parking"),
            "fastag" to "Active",
            "demo" to true
        )
        else -> emptyMap()
    }
    @Suppress("UNCHECKED_CAST")
    val challan = demoComp["challan"] as? Map<String, Any>
    @Suppress("UNCHECKED_CAST")
    val insurance = demoComp["insurance"] as? Map<String, Any>
    @Suppress("UNCHECKED_CAST")
    val puc = demoComp["puc"] as? Map<String, Any>

    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Color(0x21000000))) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Build, null, tint = TerritoryAccent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("RTO & Compliance", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                    Text(masked, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Vehicle no. masked for privacy — tap to view RTO, challan, insurance & PUC. Demo no. used for this listing.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            // quick chips
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Surface(color = if ((challan?.get("pending_amount") as? Number)?.toInt() ?: 0 > 0) MaterialTheme.colorScheme.errorContainer else ActivateGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text("Challan: ₹${(challan?.get("pending_amount") ?: 0)}", modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = if ((challan?.get("pending_amount") as? Number)?.toInt() ?: 0 > 0) MaterialTheme.colorScheme.error else ActivateGreen)
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                    Text("Insurance: ${insurance?.get("provider") ?: "—"} till ${insurance?.get("valid_till") ?: "—"}", modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                }
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                    Text("PUC: ${puc?.get("valid_till") ?: "—"} (${puc?.get("status") ?: "—"})", modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = { show = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp)) {
                Icon(Icons.Filled.Visibility, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("View RTO Details")
            }
            if (demoComp["demo"] == true) {
                Spacer(Modifier.height(6.dp))
                Text("Demo data as per Indian MV Rules — verify at https://vahan.parivahan.gov.in & https://echallan.parivahan.gov.in", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (show) {
        AlertDialog(onDismissRequest = { show = false }, title = { Text("RTO Details") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Registration: ${reg ?: "—"} (masked: $masked)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                Text("RC Status: ${demoComp["rc_status"] ?: "—"}", style = MaterialTheme.typography.bodySmall)
                Text("Insurance: ${insurance?.get("provider") ?: "—"} • ${insurance?.get("policy_no") ?: ""} • Till ${insurance?.get("valid_till") ?: "—"} (${insurance?.get("type") ?: ""})", style = MaterialTheme.typography.bodySmall)
                Text("PUC: Till ${puc?.get("valid_till") ?: "—"} • ${puc?.get("status") ?: ""}", style = MaterialTheme.typography.bodySmall)
                Text("Challan: ₹${challan?.get("pending_amount") ?: 0} • ${challan?.get("count") ?: 0} pending • ${challan?.get("details") ?: ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                (challan?.get("message") as? String)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Text("Note: Clear challan before transfer. This is demo data for testing.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, confirmButton = { TextButton(onClick = { show = false }) { Text("Close") } })
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
private fun InspectionStatusCard(token: String?, listingId: String, onTrack: (() -> Unit)? = null) {
    val state = rememberApi("inspection-$listingId") { MarketplaceRepository.inspectionForListing(token, listingId) }
    val mine = (state as? ApiState.Success)?.data
    val insp = mine ?: return
    val status = insp["status"]?.toString() ?: "requested"
    val inspectorName = insp["inspector_name"]?.toString() ?: "Certified Inspector"

    val statusIndex = when (status) {
        "requested" -> 1
        "assigned" -> 2
        "travelling" -> 3
        "arrived", "in_progress" -> 4
        "completed" -> 5
        else -> 1
    }

    val score = (insp["score"] as? Number)?.toDouble()
        ?: ((insp["findings"] as? Map<*, *>)?.get("score") as? Number)?.toDouble()

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0x21000000)),
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(TerritoryAccent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Shield,
                        contentDescription = null,
                        tint = TerritoryAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Live Inspection Status",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when (status) {
                            "requested" -> "Awaiting inspector pickup"
                            "assigned" -> "Inspector assigned: $inspectorName"
                            "travelling" -> "Inspector is on the way 🛵"
                            "arrived" -> "Inspector arrived at location 📍"
                            "in_progress" -> "Hardware & screen test underway ⚙️"
                            "completed" -> "Inspection certified & scored ✅"
                            else -> status.replace("_", " ").replaceFirstChar { it.uppercase() }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (status == "completed") ActivateGreen else TerritoryAccent,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (score != null) {
                    Surface(
                        color = ActivateGreen.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            "${score.toInt()}/100",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ActivateGreen
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(14.dp))

            // Delivery-App Style Timeline Steps
            val steps = listOf(
                Triple("Request Placed", "Your inspection request was registered", 1),
                Triple("Inspector Assigned", if (statusIndex >= 2) "$inspectorName confirmed" else "Assigning certified technician", 2),
                Triple("On the Way", if (statusIndex >= 3) "Travelling to seller location" else "Transit to device address", 3),
                Triple("Physical Diagnostic", if (statusIndex >= 4) "18-point hardware & screen check" else "On-site testing & AI analysis", 4),
                Triple("Certified & Scored", if (statusIndex >= 5) "Report ready: ${score?.toInt() ?: 85}/100" else "Trust certificate generation", 5),
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
                                    .height(26.dp)
                                    .background(
                                        if (isCompleted) ActivateGreen else Color(0xFFE2E2E8)
                                    )
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = if (idx < steps.lastIndex) 10.dp else 0.dp)
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

            if (status == "completed") {
                Spacer(Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = ActivateGreen.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, ActivateGreen.copy(alpha = 0.25f))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            tint = ActivateGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Verified Device — Safe to buy with Escrow protection!",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = ActivateGreen
                        )
                    }
                }
            }

            if (onTrack != null) {
                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onTrack,
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (status == "completed") ActivateGreen else TerritoryAccent
                    )
                ) {
                    Icon(
                        if (status == "completed") Icons.Default.CheckCircle else Icons.Default.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (status == "completed") "View AI Inspection Certificate ➔" else "Open Live Delivery Tracker 🛵",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
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