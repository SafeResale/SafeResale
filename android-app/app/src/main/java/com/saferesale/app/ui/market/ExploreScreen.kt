package com.saferesale.app.ui.market

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferesale.app.data.marketplace.MarketplaceRepository
import com.saferesale.app.domain.model.MarketListing
import com.saferesale.app.ui.theme.TerritoryAccent

enum class MarketSort(val label: String) {
    NEWEST("Newest First"),
    PRICE_LOW("Price: Low to High"),
    PRICE_HIGH("Price: High to Low"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    token: String?,
    initialCategory: String? = null,
    onBack: () -> Unit,
    onOpenListing: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyGridState()

    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(MarketSort.NEWEST) }
    var isGrid by remember { mutableStateOf(true) }
    var showFilter by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }

    var items by remember { mutableStateOf(emptyList<MarketListing>()) }
    var covers by remember { mutableStateOf(emptyMap<String, String?>()) }
    var page by remember { mutableIntStateOf(1) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var loadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableIntStateOf(0) }

    val categoriesState = rememberApi(reloadKey) { MarketplaceRepository.categories() }

    suspend fun loadPage(p: Int, append: Boolean) {
        try {
            val resp = MarketplaceRepository.listings(token, selectedCategory, p, 12)
            val newCovers = resp.items.associate { it._id to listingCoverKey(token, it._id) }
            items = if (append) items + resp.items else resp.items
            covers = covers + newCovers
            total = resp.total
            page = resp.page
        } catch (e: Exception) {
            if (!append && items.isEmpty()) error = e.message ?: "Network error"
        }
    }

    LaunchedEffect(selectedCategory, reloadKey) {
        loading = true
        error = null
        items = emptyList()
        covers = emptyMap()
        listState.scrollToItem(0)
        loadPage(1, append = false)
        loading = false
    }

    val filtered = remember(items, query, sort) {
        var list = items
        val q = query.trim()
        if (q.isNotEmpty()) {
            list = list.filter {
                it.title.contains(q, ignoreCase = true) ||
                    (it.brand?.contains(q, ignoreCase = true) == true) ||
                    (it.model?.contains(q, ignoreCase = true) == true)
            }
        }
        when (sort) {
            MarketSort.NEWEST -> list.sortedByDescending { it.created_at ?: 0.0 }
            MarketSort.PRICE_LOW -> list.sortedBy { it.price }
            MarketSort.PRICE_HIGH -> list.sortedByDescending { it.price }
        }
    }

    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 3
        }
    }

    LaunchedEffect(nearEnd, loadingMore, page, items.size, total) {
        if (nearEnd && !loadingMore && items.isNotEmpty() && items.size < total) {
            loadingMore = true
            loadPage(page + 1, append = true)
            loadingMore = false
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    // Search bar + Back + View Mode Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }

                        // Modern Embedded Search Input
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = TerritoryAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                BasicTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    cursorBrush = SolidColor(TerritoryAccent),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { inner ->
                                        if (query.isEmpty()) {
                                            Text(
                                                "Search title, brand, model...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                            )
                                        }
                                        inner()
                                    }
                                )
                                if (query.isNotEmpty()) {
                                    IconButton(
                                        onClick = { query = "" },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Clear",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.width(4.dp))

                        IconButton(onClick = { isGrid = !isGrid }) {
                            Icon(
                                if (isGrid) Icons.Default.ViewList else Icons.Default.GridView,
                                contentDescription = if (isGrid) "Switch to list" else "Switch to grid",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Sticky Category Pills
                    when (val c = categoriesState) {
                        is ApiState.Success -> {
                            val cats = c.data
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                item {
                                    val isAllSelected = selectedCategory == null
                                    FilterChip(
                                        selected = isAllSelected,
                                        onClick = { selectedCategory = null },
                                        label = { Text("All") },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = TerritoryAccent,
                                            selectedLabelColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                                itemsIndexed(cats) { _, cat ->
                                    val isSelected = selectedCategory == cat.category
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            selectedCategory = if (isSelected) null else cat.category
                                        },
                                        label = { Text(formatCategory(cat.category)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = TerritoryAccent,
                                            selectedLabelColor = Color.White
                                        ),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                        else -> Unit
                    }

                    // Sort & Result Count Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (loading && items.isEmpty()) "Searching verified listings..." else "${filtered.size} items found",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Sort Pill
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable { showSort = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(14.dp), tint = TerritoryAccent)
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        sort.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            // Filter Pill
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (selectedCategory != null) TerritoryAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                border = BorderStroke(1.dp, if (selectedCategory != null) TerritoryAccent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                modifier = Modifier.clickable { showFilter = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Tune,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = if (selectedCategory != null) TerritoryAccent else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (selectedCategory != null) formatCategory(selectedCategory!!) else "Filter",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selectedCategory != null) TerritoryAccent else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = if (isGrid) GridCells.Fixed(2) else GridCells.Fixed(1),
            state = listState,
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                loading && items.isEmpty() -> items(6) { ShimmerCard() }

                error != null && items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    ErrorBox(error ?: "Network error") { reloadKey++ }
                }

                filtered.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyMarketBox(
                        title = "No matching items",
                        subtitle = "Try searching for a different keyword or clear category filters.",
                        actionLabel = if (query.isNotEmpty() || selectedCategory != null) "Clear all filters" else null,
                        onAction = {
                            query = ""
                            selectedCategory = null
                        }
                    )
                }

                else -> {
                    gridItemsIndexed(filtered) { _, listing ->
                        if (isGrid) {
                            ListingCard(
                                listing = listing,
                                coverKey = covers[listing._id],
                                onClick = { onOpenListing(listing._id) },
                            )
                        } else {
                            ListingHorizontalCard(
                                listing = listing,
                                coverKey = covers[listing._id],
                                onClick = { onOpenListing(listing._id) },
                            )
                        }
                    }
                }
            }

            if (loadingMore) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = TerritoryAccent)
                    }
                }
            }
        }
    }

    // Modern Filter Bottom Sheet
    if (showFilter) {
        ModalBottomSheet(
            onDismissRequest = { showFilter = false },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp, start = 20.dp, end = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Filter by Category",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (selectedCategory != null) {
                        TextButton(onClick = { selectedCategory = null; showFilter = false }) {
                            Text("Reset", color = TerritoryAccent)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(8.dp))

                when (val c = categoriesState) {
                    is ApiState.Success -> {
                        val options = listOf<Pair<String?, String>>(
                            null to "All Categories",
                        ) + c.data.map { it.category to formatCategory(it.category) }

                        options.forEach { (value, label) ->
                            val isSelected = selectedCategory == value
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) TerritoryAccent.copy(alpha = 0.08f) else Color.Transparent,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedCategory = value
                                        showFilter = false
                                    }
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) TerritoryAccent else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = TerritoryAccent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    // Modern Sort Bottom Sheet
    if (showSort) {
        ModalBottomSheet(
            onDismissRequest = { showSort = false },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp, start = 20.dp, end = 20.dp)
            ) {
                Text(
                    "Sort Listings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(Modifier.height(8.dp))

                MarketSort.values().forEach { s ->
                    val isSelected = sort == s
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) TerritoryAccent.copy(alpha = 0.08f) else Color.Transparent,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sort = s
                                showSort = false
                            }
                    ) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                s.label,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) TerritoryAccent else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            if (isSelected) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = TerritoryAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}