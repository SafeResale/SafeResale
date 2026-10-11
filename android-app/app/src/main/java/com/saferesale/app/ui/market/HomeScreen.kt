package com.saferesale.app.ui.market

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Kitchen
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferesale.app.R
import com.saferesale.app.data.marketplace.MarketplaceRepository
import com.saferesale.app.domain.model.MarketCategory
import com.saferesale.app.ui.theme.ActivateGreen
import com.saferesale.app.ui.theme.TerritoryAccent
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

data class HomeActions(
    val onOpenListing: (String) -> Unit = {},
    val onExplore: (String?) -> Unit = {},
    val onSell: () -> Unit = {},
)

private fun categoryIcon(category: String): ImageVector = when (category) {
    "mobile" -> Icons.Default.PhoneAndroid
    "laptop" -> Icons.Default.Laptop
    "electronics" -> Icons.Default.Kitchen
    "camera" -> Icons.Default.CameraAlt
    "gaming" -> Icons.Default.SportsEsports
    "car" -> Icons.Default.DirectionsCar
    "bike" -> Icons.Default.TwoWheeler
    "vehicle" -> Icons.Default.DirectionsCar
    "accessory" -> Icons.Default.Headphones
    "bicycle" -> Icons.AutoMirrored.Filled.DirectionsBike
    "watch" -> Icons.Default.Watch
    "books" -> Icons.AutoMirrored.Filled.MenuBook
    "sports" -> Icons.Default.FitnessCenter
    "toys" -> Icons.Default.SportsEsports
    "instruments" -> Icons.Default.MusicNote
    else -> Icons.Default.GridView
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    token: String?,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    var reloadKey by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var selectedCategoryFilter by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(reloadKey) {
        refreshing = true
        delay(600)
        refreshing = false
    }

    val categoriesState = rememberApi(reloadKey) { MarketplaceRepository.categories() }
    val listingsState = rememberApi(listOf("listings", reloadKey)) {
        coroutineScope {
            val data = MarketplaceRepository.listings(token, null, 1, 30)
            val covers = data.items.associate { it._id to listingCoverKey(token, it._id) }
            data.items to covers
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ── FIXED TOP BAR: Brand Header + Sticky Search + Sticky Categories ──────────
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            shadowElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 10.dp)
            ) {
                // Brand Header
                HomeHeader(onSell = actions.onSell)

                Spacer(Modifier.height(8.dp))

                // Sticky Search Bar
                HomeSearchBar(onClick = { actions.onExplore(null) })

                Spacer(Modifier.height(10.dp))

                // Sticky Horizontal Categories Row
                when (val c = categoriesState) {
                    is ApiState.Success -> {
                        val cats = c.data.ifEmpty { defaultCategories() }
                        HomeCategoryRow(
                            cats = cats,
                            selected = selectedCategoryFilter,
                            onSelect = { selectedCategoryFilter = it }
                        )
                    }
                    else -> {
                        HomeCategoryRow(
                            cats = defaultCategories(),
                            selected = selectedCategoryFilter,
                            onSelect = { selectedCategoryFilter = it }
                        )
                    }
                }
            }
        }

        // ── SCROLLABLE LISTINGS FEED ──────────────────────────────────────────────────
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { reloadKey++ },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // AI Verification Hero Banner
                item(span = { GridItemSpan(2) }) {
                    HomeHeroBanner(onSell = actions.onSell, onExplore = { actions.onExplore(null) })
                }

                // Trust & Safety Highlights Strip
                item(span = { GridItemSpan(2) }) {
                    HomeTrustHighlights()
                }

                // Section Title
                item(span = { GridItemSpan(2) }) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(ActivateGreen.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Bolt,
                                    contentDescription = null,
                                    tint = ActivateGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (selectedCategoryFilter == null) "Freshly Verified" else "${selectedCategoryFilter?.replaceFirstChar { it.uppercase() }} Listings",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        TextButton(
                            onClick = { actions.onExplore(selectedCategoryFilter) },
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Text("See all", color = TerritoryAccent, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Listings Grid
                when (val s = listingsState) {
                    is ApiState.Loading -> items(6) { ShimmerCard() }
                    is ApiState.Error -> item(span = { GridItemSpan(2) }) {
                        ErrorBox(s.message) { reloadKey++ }
                    }
                    is ApiState.Success -> {
                        val (allItems, covers) = s.data
                        val items = if (selectedCategoryFilter == null) {
                            allItems
                        } else {
                            allItems.filter { it.category.equals(selectedCategoryFilter, ignoreCase = true) }
                        }

                        if (items.isEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                EmptyMarketBox(
                                    title = "No listings found",
                                    subtitle = if (selectedCategoryFilter != null) {
                                        "No items found for this category yet. Be the first to post!"
                                    } else {
                                        "Be the first to list a device with AI verified inspection."
                                    },
                                    actionLabel = "Sell an item",
                                    onAction = actions.onSell,
                                )
                            }
                        } else {
                            gridItemsIndexed(items) { _, listing ->
                                ListingCard(
                                    listing = listing,
                                    coverKey = covers[listing._id],
                                    onClick = { actions.onOpenListing(listing._id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Modern Brand Top Header ─────────────────────────────────────────────────────
@Composable
private fun HomeHeader(onSell: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(TerritoryAccent.copy(alpha = 0.10f))
                    .border(1.dp, TerritoryAccent.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_round),
                    contentDescription = "SafeResale",
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "SafeResale",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = TerritoryAccent.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "AI VERIFIED",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            fontWeight = FontWeight.Bold,
                            color = TerritoryAccent
                        )
                    }
                }
                Text(
                    "Certified pre-owned marketplace",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Quick Sell Pill in top bar
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = TerritoryAccent,
            modifier = Modifier.clickable(onClick = onSell)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Sell",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

// ── Floating Rounded Search Bar ─────────────────────────────────────────────────
@Composable
private fun HomeSearchBar(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = TerritoryAccent,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Search verified phones, laptops, bikes...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(TerritoryAccent.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Tune,
                    contentDescription = "Filter",
                    tint = TerritoryAccent,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}

// ── Hero Banner: AI Trust & Instant Inspection ──────────────────────────────────
@Composable
private fun HomeHeroBanner(onSell: () -> Unit, onExplore: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF0F172A),
                            Color(0xFF1E293B),
                            TerritoryAccent.copy(alpha = 0.85f)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Shield,
                                contentDescription = null,
                                tint = ActivateGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "100% Verified Quality",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    Icon(
                        Icons.Default.Verified,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    "Shop Pre-Owned with Zero Fear",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    "Every listing verified with CoreV 18-point hardware test, battery health, and fraud prevention.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(16.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onSell,
                        colors = ButtonDefaults.buttonColors(containerColor = TerritoryAccent),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Sell an Item", fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.White
                        )
                    }

                    OutlinedButton(
                        onClick = onExplore,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text("Browse Verified", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ── Trust Highlights Strip ──────────────────────────────────────────────────────
@Composable
private fun HomeTrustHighlights() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val pillars = listOf(
            Triple(Icons.Default.Security, "18-Point Test", "Hardware checked"),
            Triple(Icons.Default.CheckCircle, "AI Trust Score", "Diagnostic badge"),
            Triple(Icons.Default.Verified, "Verified Sellers", "DigiLocker KYC")
        )

        pillars.forEach { (icon, title, desc) ->
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = TerritoryAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                    Text(
                        desc,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ── Horizontal Categories Sticky Bar ────────────────────────────────────────────
@Composable
private fun HomeCategoryRow(
    cats: List<MarketCategory>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item {
            CategoryChipItem(
                label = "All",
                icon = Icons.Default.GridView,
                isSelected = selected == null,
                onClick = { onSelect(null) }
            )
        }
        itemsIndexed(cats) { _, cat ->
            CategoryChipItem(
                label = formatCategory(cat.category),
                icon = categoryIcon(cat.category),
                isSelected = selected == cat.category,
                onClick = { onSelect(if (selected == cat.category) null else cat.category) }
            )
        }
    }
}

@Composable
private fun CategoryChipItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) TerritoryAccent else TerritoryAccent.copy(alpha = 0.08f)
    val contentColor = if (isSelected) Color.White else TerritoryAccent
    val border = if (isSelected) TerritoryAccent else TerritoryAccent.copy(alpha = 0.16f)

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

private fun defaultCategories(): List<MarketCategory> = listOf(
    MarketCategory(category = "mobile"),
    MarketCategory(category = "laptop"),
    MarketCategory(category = "electronics"),
    MarketCategory(category = "camera"),
    MarketCategory(category = "gaming"),
    MarketCategory(category = "appliance"),
    MarketCategory(category = "car"),
    MarketCategory(category = "bike"),
    MarketCategory(category = "furniture"),
    MarketCategory(category = "accessory"),
    MarketCategory(category = "bicycle"),
    MarketCategory(category = "watch"),
    MarketCategory(category = "books"),
    MarketCategory(category = "sports"),
    MarketCategory(category = "toys"),
    MarketCategory(category = "instruments"),
)

@Composable
fun ShimmerCard() {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            )
            Column(Modifier.padding(12.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.13f)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Could not load listings",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = TerritoryAccent)
            ) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun EmptyMarketBox(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.13f)),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(TerritoryAccent.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ShoppingBag,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = TerritoryAccent,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null) {
                Spacer(Modifier.height(18.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(containerColor = TerritoryAccent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(actionLabel, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}