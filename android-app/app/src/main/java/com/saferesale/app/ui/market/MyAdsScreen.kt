package com.saferesale.app.ui.market

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saferesale.app.data.TokenStore
import com.saferesale.app.data.marketplace.MarketplaceRepository
import com.saferesale.app.data.marketplace.MyListingsStore
import com.saferesale.app.domain.model.MarketListing
import kotlinx.coroutines.coroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyAdsScreen(
    token: String?,
    onOpenListing: (String) -> Unit,
    onNewCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var myListings by remember { mutableStateOf(emptyList<MarketListing>()) }
    var busy by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(reloadKey) {
        busy = true
        val remote = runCatching { MarketplaceRepository.myListings(token) }.getOrNull()
        val loaded = if (remote.isNullOrEmpty()) {
            // fallback: stale drafts recorded on this device
            val ids = MyListingsStore.get(ctx)
            if (ids.isEmpty()) emptyList()
            else coroutineScope {
                ids.mapNotNull { id -> runCatching { MarketplaceRepository.listing(token, id) }.getOrNull() }
            }
        } else {
            remote
        }
        myListings = loaded.sortedByDescending { it.created_at ?: 0.0 }
        busy = false
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("My Ads") },
            actions = {
                IconButton(onClick = { reloadKey++ }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            },
        )
    }) { padding ->
        when {
            busy -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            myListings.isEmpty() -> Column(
                Modifier.fillMaxSize().padding(padding).padding(20.dp),
            ) {
                EmptyMarketBox(
                    title = "No ads yet",
                    subtitle = "Sell a device with a full inspection check and it will show up here with its status.",
                    actionLabel = "Sell a device",
                    onAction = onNewCheck,
                )
            }

            else -> LazyColumn(
                modifier = modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Button(
                        onClick = onNewCheck,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Sell a device")
                    }
                }
                items(myListings, key = { it._id }) { listing ->
                    ListingsSummaryRow(
                        listing = listing,
                        onClick = { onOpenListing(listing._id) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun ListingsSummaryRow(listing: MarketListing, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0x21000000)),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        listing.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${formatCategory(listing.category)} · ${timeAgo(listing.created_at)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    formatPrice(listing.price),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(10.dp))
                StatusChip(listing.status)
            }
            if (!listing.submission_url.isNullOrBlank()) {
                HorizontalDivider(color = Color(0x16000000))
                val clipboard = LocalClipboardManager.current
                TextButton(
                    onClick = { clipboard.setText(AnnotatedString(listing.submission_url)) },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Copy score submission link", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
internal fun StatusChip(status: String) {
    val (label, color) = listingStatusLabel(status)
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(20.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}