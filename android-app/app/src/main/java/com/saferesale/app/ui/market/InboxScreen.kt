package com.saferesale.app.ui.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Person
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

    suspend fun load() {
        try {
            if (token.isNullOrBlank()) {
                error = "Sign in to view your messages"
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

    // Poll every 5s
    LaunchedEffect(token) {
        while (isActive) {
            delay(5000)
            if (!token.isNullOrBlank()) {
                runCatching { MarketplaceRepository.getThreads(token) }
                    .onSuccess { threads = it }
            }
        }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Chat") })
    }) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { refreshing = true; tick++ },
            modifier = modifier.fillMaxSize().padding(padding),
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null && threads.isEmpty() -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(error ?: "Error", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { loading = true; tick++ }) { Text("Retry") }
                    }
                }
                threads.isEmpty() -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                ) {
                    item { Spacer(Modifier.height(24.dp)) }
                    item {
                        Column(
                            Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier.size(96.dp).clip(RoundedCornerShape(50)).background(TerritoryAccent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(44.dp), tint = TerritoryAccent)
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("No conversations yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Start a conversation from any listing — tap Contact seller and your chats will appear here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(20.dp))
                            Button(onClick = onStartCheck, shape = RoundedCornerShape(26.dp)) { Text("Browse listings") }
                            Spacer(Modifier.height(40.dp))
                            HorizontalDivider()
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Chats are listing-tied. Each conversation stays linked to the device you asked about.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(threads, key = { it.thread_id }) { thread ->
                        ThreadRow(thread = thread, onClick = { onOpenThread(thread.listing_id) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ThreadRow(thread: ChatThread, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(TerritoryAccent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Person, contentDescription = null, tint = TerritoryAccent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    thread.other_user?.name ?: "User",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (thread.unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    Badge(containerColor = TerritoryAccent) { Text("${thread.unread}", color = Color.White, style = MaterialTheme.typography.labelSmall) }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                thread.listing?.title ?: "Listing #${thread.listing_id.take(6)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                thread.last_message.ifBlank { "Tap to start chatting" },
                style = MaterialTheme.typography.bodySmall,
                color = if (thread.unread > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (thread.unread > 0) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(timeAgo(thread.updated_at.takeIf { it != 0.0 } ?: thread.last_message_at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            Spacer(Modifier.height(4.dp))
            Surface(
                color = if (thread.role == "selling") TerritoryAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(
                    if (thread.role == "selling") "Selling" else "Buying",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (thread.role == "selling") TerritoryAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
