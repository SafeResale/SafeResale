package com.saferesale.app.ui.market

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferesale.app.data.marketplace.MarketplaceRepository
import com.saferesale.app.domain.model.ChatThread
import com.saferesale.app.ui.theme.TerritoryAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    token: String?,
    onStartCheck: () -> Unit,
    onOpenThread: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var threads by remember { mutableStateOf<List<ChatThread>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }
    var tick by remember { mutableIntStateOf(0) }
    var selectedFilter by remember { mutableStateOf<String?>("all") } // "all", "buying", "selling"

    suspend fun load() {
        try {
            if (token.isNullOrBlank()) {
                error = "Sign in to view your conversations"
                loading = false
                refreshing = false
                return
            }
            val data = MarketplaceRepository.getThreads(token)
            threads = data
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Could not load conversations"
        } finally {
            loading = false
            refreshing = false
        }
    }

    LaunchedEffect(token, tick) { load() }

    // Poll every 5s for realtime updates
    LaunchedEffect(token) {
        while (isActive) {
            delay(5000)
            if (!token.isNullOrBlank()) {
                runCatching { MarketplaceRepository.getThreads(token) }
                    .onSuccess { threads = it }
            }
        }
    }

    val filteredThreads = remember(threads, selectedFilter) {
        when (selectedFilter) {
            "buying" -> threads.filter { it.role != "selling" }
            "selling" -> threads.filter { it.role == "selling" }
            else -> threads
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                "Messages",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Verified buyer & seller chats",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        val unreadCount = threads.sumOf { it.unread }
                        if (unreadCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = TerritoryAccent,
                            ) {
                                Text(
                                    "$unreadCount new",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Role Filter Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val filters = listOf(
                            "all" to "All Chats",
                            "buying" to "Buying",
                            "selling" to "Selling"
                        )
                        filters.forEach { (key, label) ->
                            val isSelected = selectedFilter == key
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilter = key },
                                label = { Text(label, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TerritoryAccent,
                                    selectedLabelColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; tick++ },
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TerritoryAccent)
                }

                error != null && threads.isEmpty() -> Box(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Unable to load chats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text(error ?: "Error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { loading = true; tick++ },
                                colors = ButtonDefaults.buttonColors(containerColor = TerritoryAccent)
                            ) {
                                Text("Retry")
                            }
                        }
                    }
                }

                filteredThreads.isEmpty() -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Spacer(Modifier.height(32.dp))
                        Box(
                            Modifier
                                .size(88.dp)
                                .clip(CircleShape)
                                .background(TerritoryAccent.copy(alpha = 0.10f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                modifier = Modifier.size(42.dp),
                                tint = TerritoryAccent
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Text(
                            if (threads.isEmpty()) "No conversations yet" else "No $selectedFilter chats",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Chats are directly tied to verified listings. Open any ad and tap 'Contact Seller' to start a deal.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(0.9f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = onStartCheck,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TerritoryAccent)
                        ) {
                            Text("Browse Marketplace", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredThreads, key = { it.thread_id }) { thread ->
                        ModernThreadCard(thread = thread, onClick = { onOpenThread(thread.listing_id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernThreadCard(thread: ChatThread, onClick: () -> Unit) {
    val unread = thread.unread > 0
    val isSeller = thread.role == "selling"

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (unread) TerritoryAccent.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            1.dp,
            if (unread) TerritoryAccent.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // User Avatar with Initial
            val name = thread.other_user?.name ?: "User"
            val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "U"

            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (unread) TerritoryAccent else TerritoryAccent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (unread) Color.White else TerritoryAccent
                )
            }

            Spacer(Modifier.width(12.dp))

            // Conversation Details
            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Text(
                        timeAgo(thread.updated_at.takeIf { it != 0.0 } ?: thread.last_message_at),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (unread) TerritoryAccent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal
                    )
                }

                Spacer(Modifier.height(3.dp))

                // Listing Title pill
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = thread.listing?.title ?: "Listing #${thread.listing_id.take(6)}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isSeller) TerritoryAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            if (isSeller) "Selling" else "Buying",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            fontWeight = FontWeight.Bold,
                            color = if (isSeller) TerritoryAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        thread.last_message.ifBlank { "Tap to view conversation" },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (unread) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (thread.unread > 0) {
                        Badge(
                            containerColor = TerritoryAccent,
                            contentColor = Color.White
                        ) {
                            Text(
                                "${thread.unread}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
