package com.saferesale.app.ui.market

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.saferesale.app.data.ApiClient
import com.saferesale.app.data.marketplace.MarketplaceRepository
import com.saferesale.app.domain.model.MarketListing

private val ItemBorder = Color(0x21000000) // black @ 13%, eclassify card border

// eclassify grid ItemCard: white, radius 18, bordered, image ≈ screenHeight/5.45
@Composable
fun ListingCard(
    listing: MarketListing,
    coverKey: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, ItemBorder),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(Modifier.padding(1.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(topStart = 17.dp, topEnd = 17.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (coverKey != null) {
                    AsyncImage(
                        model = imageUrl(coverKey),
                        contentDescription = listing.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Photo,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                if (verifiedListing(listing)) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(50),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp),
                            )
                            Spacer(Modifier.width(3.dp))
                            Text(
                                "Verified",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                TrustBadge(
                    listing,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                )
            }
            Column(Modifier.padding(10.dp)) {
                Text(
                    formatPrice(listing.price),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    listing.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.Black,
                )
                Spacer(Modifier.height(4.dp))
                val sub = listOfNotNull(
                    listing.brand,
                    listing.model,
                    listing.storage,
                ).joinToString(" · ").ifBlank { formatCategory(listing.category) }
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        timeAgo(listing.created_at),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

// eclassify horizontal list card (list-mode toggle): image left, content right
@Composable
fun ListingHorizontalCard(
    listing: MarketListing,
    coverKey: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, ItemBorder),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row {
            Box(
                Modifier
                    .width(104.dp)
                    .height(122.dp)
                    .clip(RoundedCornerShape(topStart = 17.dp, bottomStart = 17.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (coverKey != null) {
                    AsyncImage(
                        model = imageUrl(coverKey),
                        contentDescription = listing.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Photo,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
                TrustBadge(
                    listing,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                )
            }
            Column(
                Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                val sub = listOfNotNull(
                    listing.brand,
                    listing.model,
                ).joinToString(" · ").ifBlank { formatCategory(listing.category) }
                Text(
                    sub,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    formatPrice(listing.price),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
                Text(
                    listing.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                )
                Spacer(Modifier.weight(1f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        timeAgo(listing.created_at),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }
    }
}

fun imageUrl(storedKey: String): String = "${ApiClient.baseUrl}/uploads/$storedKey"

/** SafeResale trust badge: backend `trust` verdict rendered verbatim. */
@Composable
fun TrustBadge(listing: MarketListing, modifier: Modifier = Modifier) {
    val score = trustVerdict(listing).first ?: return
    TrustPill(score, trustColorOf(listing), modifier)
}

/** Generic trust pill (score + color provided by the caller). */
@Composable
fun TrustPill(score: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color.White.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(3.dp))
            Text(
                "Trust $score",
                style = MaterialTheme.typography.labelSmall,
                color = color,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

suspend fun listingCoverKey(token: String?, listingId: String): String? {
    return runCatching {
        MarketplaceRepository.images(token, listingId)
            .firstOrNull { !it.stored_key.isNullOrBlank() }
            ?.stored_key
    }.getOrNull()
}