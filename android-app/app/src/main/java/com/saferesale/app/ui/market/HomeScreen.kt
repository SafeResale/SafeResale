package com.saferesale.app.ui.market

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.saferesale.app.R
import com.saferesale.app.data.marketplace.MarketplaceRepository
import com.saferesale.app.domain.model.MarketCategory
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
    "vehicle" -> Icons.Default.DirectionsCar
    "accessory" -> Icons.Default.Headphones
    else -> Icons.Default.GridView
}

@Composable
fun HomeScreen(
    token: String?,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    var reloadKey by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }

    LaunchedEffect(reloadKey) {
        refreshing = true
        delay(600)
        refreshing = false
    }

    val categoriesState = rememberApi(reloadKey) { MarketplaceRepository.categories() }
    val listingsState = rememberApi(listOf("listings", reloadKey)) {
        coroutineScope {
            val data = MarketplaceRepository.listings(token, null, 1, 24)
            val covers = data.items.associate { it._id to listingCoverKey(token, it._id) }
            data.items to covers
        }
    }

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { reloadKey++ },
        modifier = modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(2) }) { HomeHeader() }
            item(span = { GridItemSpan(2) }) { HomeSearchBar(onClick = { actions.onExplore(null) }) }
            item(span = { GridItemSpan(2) }) {
                when (val c = categoriesState) {
                    is ApiState.Success -> {
                        val cats = c.data.ifEmpty { defaultCategories() }
                        HomeCategoryRow(cats = cats, onExplore = actions.onExplore)
                    }
                    else -> Unit
                }
            }
            item(span = { GridItemSpan(2) }) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Freshly verified",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { actions.onExplore(null) }) { Text("See all") }
                }
            }

            when (val s = listingsState) {
                is ApiState.Loading -> items(6) { ShimmerCard() }
                is ApiState.Error -> item(span = { GridItemSpan(2) }) {
                    ErrorBox(s.message) { reloadKey++ }
                }
                is ApiState.Success -> {
                    val (items, covers) = s.data
                    if (items.isEmpty()) {
                        item(span = { GridItemSpan(2) }) {
                            EmptyMarketBox(
                                title = "Nothing verified yet",
                                subtitle = "Be the first — sell a device with a full inspection check.",
                                actionLabel = "Sell now",
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

// eclassify header: 40x40 white rounded box (territory icon) + brand title
@Composable
private fun HomeHeader() {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.mipmap.ic_launcher_round),
                contentDescription = "SafeResale",
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                "SafeResale",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Verified resale marketplace",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// eclassify HomeSearchField: 56dp white pill, radius 10, territory search icon
@Composable
private fun HomeSearchBar(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f)
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = TerritoryAccent,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "Search verified devices…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

// eclassify CategoryWidgetHome: horizontal row of 70-wide white rounded squares
@Composable
private fun HomeCategoryRow(cats: List<MarketCategory>, onExplore: (String?) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    ) {
        item { CategoryHomeCard("All", Icons.Default.GridView, onClick = { onExplore(null) }) }
        itemsIndexed(cats) { _, cat ->
            CategoryHomeCard(
                formatCategory(cat.category),
                categoryIcon(cat.category),
                onClick = { onExplore(cat.category) },
            )
        }
    }
}

@Composable
private fun CategoryHomeCard(label: String, icon: ImageVector, onClick: () -> Unit) {
    Column(
        Modifier.width(78.dp).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White)
                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.13f)))
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = TerritoryAccent,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
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
)

@Composable
fun ShimmerCard() {
    Card(shape = RoundedCornerShape(18.dp)) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Column(Modifier.padding(10.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth(0.6f)
                        .height(20.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(14.dp)
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
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Could not load",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRetry) { Text("Retry") }
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
            Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.ShoppingBag,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = TerritoryAccent,
            )
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null) {
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}