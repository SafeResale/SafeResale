package com.saferesale.app.ui.market

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.saferesale.app.data.marketplace.MarketplaceRepository
import com.saferesale.app.data.marketplace.MyListingsStore
import com.saferesale.app.domain.model.MarketListing
import com.saferesale.app.ui.theme.TerritoryAccent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyAdsScreen(
    token: String?,
    onOpenListing: (String) -> Unit,
    onNewCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var myListings by remember { mutableStateOf(emptyList<MarketListing>()) }
    var covers by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var busy by remember { mutableStateOf(true) }
    var reloadKey by remember { mutableIntStateOf(0) }
    var actionListingId by remember { mutableStateOf<String?>(null) }
    var listingToDelete by remember { mutableStateOf<MarketListing?>(null) }

    LaunchedEffect(reloadKey) {
        busy = true
        val remote = runCatching { MarketplaceRepository.myListings(token) }.getOrNull()
        val loaded = if (remote.isNullOrEmpty()) {
            val ids = MyListingsStore.get(ctx)
            if (ids.isEmpty()) emptyList()
            else coroutineScope {
                ids.mapNotNull { id -> runCatching { MarketplaceRepository.listing(token, id) }.getOrNull() }
            }
        } else {
            remote
        }
        val sortedListings = loaded.sortedByDescending { it.created_at ?: 0.0 }
        myListings = sortedListings

        // Asynchronously load image covers for all listings
        coroutineScope {
            val coversMap = sortedListings.associate { it._id to listingCoverKey(token, it._id) }
            covers = coversMap
        }
        busy = false
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("My Ads", fontWeight = FontWeight.Bold)
                        if (!busy) {
                            Text(
                                "${myListings.size} listings posted",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { reloadKey++ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
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
                    subtitle = "List a device with an automated check and it will appear here with live updates.",
                    actionLabel = "Sell a device",
                    onAction = onNewCheck,
                )
            }

            else -> LazyColumn(
                modifier = modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Button(
                        onClick = onNewCheck,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Post another ad", fontWeight = FontWeight.Bold)
                    }
                }

                items(myListings, key = { it._id }) { listing ->
                    MyListingCard(
                        listing = listing,
                        coverKey = covers[listing._id],
                        isProcessing = actionListingId == listing._id,
                        onClick = { onOpenListing(listing._id) },
                        onMarkSold = {
                            actionListingId = listing._id
                            scope.launch {
                                try {
                                    MarketplaceRepository.updateListing(token, listing._id, mapOf("status" to "sold"))
                                    myListings = myListings.map {
                                        if (it._id == listing._id) it.copy(status = "sold") else it
                                    }
                                    snackbarHostState.showSnackbar("Listing marked as Sold! Hidden from buyers.")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Failed to update status: ${e.message ?: "retry"}")
                                } finally {
                                    actionListingId = null
                                }
                            }
                        },
                        onRemove = {
                            listingToDelete = listing
                        },
                    )
                }
            }
        }
    }

    // Confirmation Dialog for deleting / removing draft or ad
    if (listingToDelete != null) {
        val target = listingToDelete!!
        val isDraft = target.status.lowercase() == "draft"

        AlertDialog(
            onDismissRequest = { listingToDelete = null },
            title = {
                Text(if (isDraft) "Delete draft listing?" else "Remove listing?")
            },
            text = {
                Text(
                    if (isDraft) "Are you sure you want to delete this draft? All entered details and photos will be permanently removed."
                    else "Are you sure you want to remove \"${target.title.ifBlank { "this listing" }}\"? It will be removed from your ads and marketplace."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val idToRemove = target._id
                        listingToDelete = null
                        actionListingId = idToRemove
                        scope.launch {
                            try {
                                val deleteSucceeded = runCatching {
                                    MarketplaceRepository.deleteListing(token, idToRemove)
                                }.isSuccess

                                if (!deleteSucceeded) {
                                    // Fallback to PATCH status='deleted' for compatibility with running servers
                                    runCatching {
                                        MarketplaceRepository.updateListing(token, idToRemove, mapOf("status" to "deleted"))
                                    }
                                }

                                MyListingsStore.remove(ctx, idToRemove)
                                myListings = myListings.filterNot { it._id == idToRemove }
                                snackbarHostState.showSnackbar(if (isDraft) "Draft removed" else "Listing removed successfully")
                            } catch (e: Exception) {
                                MyListingsStore.remove(ctx, idToRemove)
                                myListings = myListings.filterNot { it._id == idToRemove }
                                snackbarHostState.showSnackbar("Listing removed")
                            } finally {
                                actionListingId = null
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text("Remove", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { listingToDelete = null }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun MyListingCard(
    listing: MarketListing,
    coverKey: String?,
    isProcessing: Boolean,
    onClick: () -> Unit,
    onMarkSold: () -> Unit,
    onRemove: () -> Unit,
) {
    val isDraft = listing.status.lowercase() == "draft"
    val isSold = listing.status.lowercase() in listOf("sold", "sold_out", "soldout")
    val isActive = listing.status.lowercase() in listOf("published", "approved")

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0x21000000)),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Product Thumbnail Image
                Box(
                    Modifier
                        .size(90.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (!coverKey.isNullOrBlank()) {
                        AsyncImage(
                            model = imageUrl(coverKey),
                            contentDescription = listing.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Icon(
                            Icons.Default.Photo,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(36.dp),
                        )
                    }

                    if (isSold) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = Color.Black.copy(alpha = 0.55f),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "SOLD",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.width(14.dp))

                // Title, Category, Price & Status
                Column(Modifier.weight(1f)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            listing.title.ifBlank { "Untitled Ad" },
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(6.dp))
                        StatusChip(listing.status)
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        "${formatCategory(listing.category)} · ${timeAgo(listing.created_at)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(6.dp))

                    Text(
                        formatPrice(listing.price),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TerritoryAccent,
                    )
                }
            }

            HorizontalDivider(color = Color(0x14000000))

            // Action Row: Contextual actions depending on status (Draft vs Active vs Sold)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    when {
                        isDraft -> "Draft · Tap to resume listing"
                        isSold -> "Item marked as Sold (hidden from buyers)"
                        isActive -> "Visible to buyers in marketplace"
                        else -> "Submitted for review"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        isDraft -> MaterialTheme.colorScheme.onSurfaceVariant
                        isSold -> Color(0xFFC2410C)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // For drafts: show "Remove" option
                    if (isDraft) {
                        OutlinedButton(
                            onClick = onRemove,
                            enabled = !isProcessing,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f)),
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(4.dp))
                            } else {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                            }
                            Text("Remove draft", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                        }
                    }
                    // For active items: show "Mark as Sold"
                    else if (isActive) {
                        OutlinedButton(
                            onClick = onMarkSold,
                            enabled = !isProcessing,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFC2410C)),
                            border = BorderStroke(1.dp, Color(0xFFC2410C)),
                        ) {
                            if (isProcessing) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFFC2410C))
                                Spacer(Modifier.width(6.dp))
                                Text("Updating…", style = MaterialTheme.typography.labelSmall)
                            } else {
                                Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Mark as Sold", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    }
                    // For already sold items: display badge and allow removing
                    else if (isSold) {
                        Surface(
                            color = Color(0xFFFFF7ED),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Check, null, tint = Color(0xFFC2410C), modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Sold",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFFC2410C),
                                )
                            }
                        }

                        IconButton(
                            onClick = onRemove,
                            enabled = !isProcessing,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete sold listing",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
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
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
        )
    }
}