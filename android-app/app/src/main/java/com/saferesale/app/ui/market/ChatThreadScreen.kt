package com.saferesale.app.ui.market

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.saferesale.app.data.marketplace.MarketplaceRepository
import com.saferesale.app.domain.model.ChatMessage
import com.saferesale.app.ui.theme.TerritoryAccent
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadScreen(
    token: String?,
    listingId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var listingTitle by remember { mutableStateOf<String?>(null) }
    var otherName by remember { mutableStateOf<String?>(null) }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var offerText by remember { mutableStateOf("") }
    var showOffer by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var blocked by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    val listState = rememberLazyListState()

    suspend fun load() {
        if (token.isNullOrBlank()) return
        try {
            // Fetch messages (also marks read server-side)
            val (msgs, _) = MarketplaceRepository.getMessages(token, listingId, page = 1, pageSize = 100)
            messages = msgs
            // Derive other user from messages or threads list
            if (msgs.isNotEmpty() && currentUserId.isNullOrBlank()) {
                // need current user id via profile
                runCatching { MarketplaceRepository.profile(token) }.onSuccess { currentUserId = it.id }
            }
            // Try to get listing title for header
            if (listingTitle == null) {
                runCatching { MarketplaceRepository.listing(token, listingId) }.onSuccess { listingTitle = it.title }
                if (listingTitle == null) {
                    // fallback to threads enrich
                    runCatching { MarketplaceRepository.getThreads(token) }.onSuccess { threads ->
                        threads.firstOrNull { it.listing_id == listingId }?.let {
                            listingTitle = it.listing?.title
                            otherName = it.other_user?.name
                        }
                    }
                }
            }
            // current user id
            if (currentUserId == null) {
                runCatching { MarketplaceRepository.profile(token) }.onSuccess { currentUserId = it.id }
            }
            error = null
        } catch (e: Exception) {
            val msg = e.message ?: "Could not load messages"
            // Detect blocked
            if (msg.contains("blocked", ignoreCase = true) || msg.contains("403")) blocked = true
            error = msg
        } finally {
            loading = false
        }
    }

    LaunchedEffect(listingId, token) {
        // fetch current user id early
        runCatching { MarketplaceRepository.profile(token) }.onSuccess { currentUserId = it.id }
        load()
    }

    // Poll every 3s
    LaunchedEffect(listingId, token) {
        while (isActive) {
            delay(3000)
            if (!token.isNullOrBlank()) {
                runCatching { MarketplaceRepository.getMessages(token, listingId, page = 1, pageSize = 100) }
                    .onSuccess { (msgs, _) ->
                        if (msgs.size != messages.size || msgs.lastOrNull()?.id != messages.lastOrNull()?.id) {
                            messages = msgs
                        }
                    }
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(listingTitle ?: "Chat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                        if (otherName != null) Text(otherName!!, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = {
                        // block other user if known via messages
                        val otherId = messages.firstOrNull { it.sender_id != currentUserId }?.sender_id
                            ?: run {
                                scope.launch {
                                    val threads = runCatching { MarketplaceRepository.getThreads(token) }.getOrNull()
                                    threads?.firstOrNull { it.listing_id == listingId }?.other_user?.id?.let { oid ->
                                        runCatching { MarketplaceRepository.blockUser(token, oid) }
                                        blocked = true
                                    }
                                }
                                return@IconButton
                            }
                        scope.launch {
                            runCatching { MarketplaceRepository.blockUser(token, otherId) }
                                .onSuccess { blocked = true }
                                .onFailure { error = it.message }
                        }
                    }) {
                        Icon(Icons.Default.Block, contentDescription = "Block user")
                    }
                },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().padding(8.dp)) {
                    if (showOffer) {
                        OutlinedTextField(
                            value = offerText,
                            onValueChange = { offerText = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Offer price (₹)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions.Default.copy(capitalization = KeyboardCapitalization.None),
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text(if (blocked) "You blocked this user" else "Type a message…") },
                            enabled = !blocked && !sending,
                            modifier = Modifier.weight(1f),
                            minLines = 1,
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            if (!showOffer) {
                                TextButton(onClick = { showOffer = true }, enabled = !blocked) { Text("Offer") }
                            } else {
                                TextButton(onClick = { showOffer = false; offerText = "" }) { Text("Cancel") }
                            }
                            IconButton(
                                onClick = {
                                    val text = input.trim()
                                    if (text.isEmpty()) return@IconButton
                                    val offer = offerText.toDoubleOrNull()
                                    if (showOffer && offerText.isNotBlank() && offer == null) {
                                        error = "Enter a valid offer price"
                                        return@IconButton
                                    }
                                    sending = true
                                    error = null
                                    scope.launch {
                                        try {
                                            MarketplaceRepository.sendMessage(token, listingId, text, offer)
                                            input = ""
                                            offerText = ""
                                            showOffer = false
                                            load()
                                        } catch (e: Exception) {
                                            val m = e.message ?: "Send failed"
                                            if (m.contains("blocked", ignoreCase = true)) blocked = true
                                            error = m
                                        } finally { sending = false }
                                    }
                                },
                                enabled = !blocked && !sending && input.isNotBlank(),
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(50)).background(if (input.isNotBlank()) TerritoryAccent else MaterialTheme.colorScheme.surfaceVariant),
                            ) {
                                if (sending) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = if (input.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    error?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                    if (blocked) {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("You have blocked this user. ", style = MaterialTheme.typography.labelSmall)
                            TextButton(onClick = {
                                scope.launch {
                                    val otherId = messages.firstOrNull { it.sender_id != currentUserId }?.sender_id
                                        ?: MarketplaceRepository.getThreads(token).firstOrNull { it.listing_id == listingId }?.other_user?.id
                                    if (otherId != null) {
                                        runCatching { MarketplaceRepository.unblockUser(token, otherId) }
                                            .onSuccess { blocked = false; error = null }
                                    }
                                }
                            }, contentPadding = PaddingValues(0.dp)) { Text("Unblock") }
                        }
                    }
                }
            }
        },
        modifier = modifier,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                messages.isEmpty() -> Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(40.dp), tint = TerritoryAccent.copy(alpha = 0.6f))
                        Spacer(Modifier.height(8.dp))
                        Text("No messages yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("Say hello! Your message is tied to this listing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val mine = currentUserId != null && msg.sender_id == currentUserId
                        Bubble(msg = msg, mine = mine)
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(msg: ChatMessage, mine: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (mine) TerritoryAccent else MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 0.dp,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (msg.offer_price != null) {
                    Surface(color = if (mine) Color.White.copy(alpha = 0.18f) else TerritoryAccent.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
                        Text("Offer: ${formatPrice(msg.offer_price)}", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = if (mine) Color.White else TerritoryAccent)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Text(msg.message, style = MaterialTheme.typography.bodyMedium, color = if (mine) Color.White else MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                Text(timeAgo(msg.created_at), style = MaterialTheme.typography.labelSmall, color = if (mine) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
