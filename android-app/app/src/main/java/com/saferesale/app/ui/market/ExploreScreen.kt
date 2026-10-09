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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saferesale.app.data.marketplace.MarketplaceRepository
import com.saferesale.app.domain.model.MarketListing
import com.saferesale.app.ui.theme.TerritoryAccent

enum class MarketSort(val label: String) {
    NEWEST("Newest"),
    PRICE_LOW("Price: low to high"),
    PRICE_HIGH("Price: high to low"),
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

    val nearEnd by remember { derivedStateOf {
        val info = listState.layoutInfo
        val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
        last >= info.totalItemsCount - 3
    } }
    LaunchedEffect(nearEnd, loadingMore, page, items.size, total) {
        if (nearEnd && !loadingMore && items.isNotEmpty() && items.size < total) {
            loadingMore = true
            loadPage(page + 1, append = true)
            loadingMore = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Items") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { isGrid = !isGrid }) {
                        Icon(
                            if (isGrid) Icons.Default.ViewList else Icons.Default.GridView,
                            contentDescription = if (isGrid) "Switch to list" else "Switch to grid",
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = Color.White, shadowElevation = 8.dp) {
                Row(
                    Modifier.fillMaxWidth().height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        Modifier.weight(1f).height(52.dp).clickable { showFilter = true },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Filter", fontWeight = FontWeight.Bold)
                    }
                    VerticalDivider()
                    Row(
                        Modifier.weight(1f).height(52.dp).clickable { showSort = true },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Sort, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(sort.label, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
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
            item(span = { GridItemSpan(maxLineSpan) }) {
                SearchField(value = query, onChange = { query = it })
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                when (val c = categoriesState) {
                    is ApiState.Success -> {
                        val cats = c.data
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(
                                    selected = selectedCategory == null,
                                    onClick = { selectedCategory = null },
                                    label = { Text("All") },
                                )
                            }
                            itemsIndexed(cats) { _, cat ->
                                FilterChip(
                                    selected = selectedCategory == cat.category,
                                    onClick = { selectedCategory = cat.category },
                                    label = { Text(formatCategory(cat.category)) },
                                )
                            }
                        }
                    }
                    else -> Unit
                }
            }

            when {
                loading && items.isEmpty() -> items(6) { ShimmerCard() }

                error != null && items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    ErrorBox(error ?: "Network error") { reloadKey++ }
                }

                filtered.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyMarketBox(
                        title = "No matches",
                        subtitle = "Try a different search or category.",
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
                    if (loadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilter) {
        ModalBottomSheet(onDismissRequest = { showFilter = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    "Category",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                when (val c = categoriesState) {
                    is ApiState.Success -> {
                        val options = listOf<Pair<String?, String>>(
                            null to "All",
                        ) + c.data.map { it.category to formatCategory(it.category) }
                        options.forEach { (value, label) ->
                            Row(
                                Modifier.fillMaxWidth().clickable { selectedCategory = value; showFilter = false }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                if (selectedCategory == value) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = TerritoryAccent,
                                    )
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }
    }

    if (showSort) {
        ModalBottomSheet(onDismissRequest = { showSort = false }) {
            Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Text(
                    "Sort by",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                MarketSort.values().forEach { s ->
                    Row(
                        Modifier.fillMaxWidth().clickable { sort = s; showSort = false }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(s.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (sort == s) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = TerritoryAccent,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(
                BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.20f)
                )
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = TerritoryAccent,
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        "Search title, brand, model…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                inner()
            },
        )
        if (value.isNotEmpty()) {
            IconButton(onClick = { onChange("") }) {
                Icon(Icons.Default.Close, contentDescription = "Clear")
            }
        }
    }
}