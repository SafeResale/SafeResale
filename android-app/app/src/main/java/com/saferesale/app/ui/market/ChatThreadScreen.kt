package com.saferesale.app.ui.market

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferesale.app.data.marketplace.MarketplaceRepository
import com.saferesale.app.domain.model.ChatMessage
import com.saferesale.app.ui.theme.ActivateGreen
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
            val (msgs, _) = MarketplaceRepository.getMessages(token, listingId, page = 1, pageSize = 100)
            messages = msgs
            if (msgs.isNotEmpty() && currentUserId.isNullOrBlank()) {
                runCatching { MarketplaceRepository.profile(token) }.onSuccess { currentUserId = it.id }
            }
            if (listingTitle == null) {
                runCatching { MarketplaceRepository.listing(token, listingId) }.onSuccess {
                    listingTitle = it.title
                }
                if (listingTitle == null) {
                    runCatching { MarketplaceRepository.getThreads(token) }.onSuccess { threads ->
                        threads.firstOrNull { it.listing_id == listingId }?.let {
                            listingTitle = it.listing?.title
                            otherName = it.other_user?.name
                        }
                    }
                }
            }
            if (currentUserId == null) {
                runCatching { MarketplaceRepository.profile(token) }.onSuccess { currentUserId = it.id }
            }
            error = null
        } catch (e: Exception) {
            val msg = e.message ?: "Could not load messages"
            if (msg.contains("blocked", ignoreCase = true) || msg.contains("403")) blocked = true
            error = msg
        } finally {
            loading = false
        }
    }

    LaunchedEffect(listingId, token) {
        runCatching { MarketplaceRepository.profile(token) }.onSuccess { currentUserId = it.id }
        load()
    }

    // Auto-scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Poll every 3s
    LaunchedEffect(listingId, token) {
        while (isActive) {
            delay(3000)
            if (!token.isNullOrBlank() && !blocked) {
                runCatching { MarketplaceRepository.getMessages(token, listingId, page = 1, pageSize = 100) }
                    .onSuccess { (msgs, _) ->
                        if (msgs.size != messages.size) messages = msgs
                    }
            }
        }
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }

                    val displayName = otherName ?: messages.firstOrNull { it.sender_id != currentUserId }?.sender_id?.take(8) ?: "Seller / Buyer"
                    val initial = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "U"

                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(TerritoryAccent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = initial,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TerritoryAccent
                        )
                    }

                    Spacer(Modifier.width(10.dp))

                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = "Verified",
                                tint = ActivateGreen,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Text(
                            listingTitle ?: "Listing #${listingId.take(8)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = ActivateGreen.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, ActivateGreen.copy(alpha = 0.25f)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Shield, contentDescription = null, tint = ActivateGreen, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(3.dp))
                            Text("Safe Chat", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), fontWeight = FontWeight.Bold, color = ActivateGreen)
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    if (showOffer) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = TerritoryAccent.copy(alpha = 0.08f),
                            border = BorderStroke(1.dp, TerritoryAccent.copy(alpha = 0.3f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = offerText,
                                    onValueChange = { offerText = it.filter { c -> c.isDigit() } },
                                    label = { Text("Offer Price (₹)") },
                                    prefix = { Text("₹ ", fontWeight = FontWeight.Bold, color = TerritoryAccent) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                TextButton(onClick = { showOffer = false; offerText = "" }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showOffer = !showOffer },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                Icons.Default.LocalOffer,
                                contentDescription = "Make Offer",
                                tint = if (showOffer) TerritoryAccent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(Modifier.width(4.dp))

                        OutlinedTextField(
                            value = input,
                            onValueChange = { input = it },
                            placeholder = { Text(if (blocked) "User blocked" else "Type a message...") },
                            enabled = !blocked && !sending,
                            modifier = Modifier.weight(1f),
                            minLines = 1,
                            maxLines = 4,
                            shape = RoundedCornerShape(22.dp),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        )

                        Spacer(Modifier.width(8.dp))

                        Surface(
                            onClick = {
                                val text = input.trim()
                                if (text.isEmpty() && offerText.isEmpty()) return@Surface
                                val offer = offerText.toDoubleOrNull()
                                if (showOffer && offerText.isNotBlank() && offer == null) {
                                    error = "Enter a valid offer price"
                                    return@Surface
                                }
                                val msgToSend = if (offer != null) "Offer: ₹$offer. $text".trim() else text

                                sending = true
                                error = null
                                scope.launch {
                                    try {
                                        MarketplaceRepository.sendMessage(token, listingId, msgToSend, offer)
                                        input = ""
                                        offerText = ""
                                        showOffer = false
                                        load()
                                    } catch (e: Exception) {
                                        val m = e.message ?: "Send failed"
                                        if (m.contains("blocked", ignoreCase = true)) blocked = true
                                        error = m
                                    } finally {
                                        sending = false
                                    }
                                }
                            },
                            enabled = !blocked && !sending && (input.isNotBlank() || offerText.isNotBlank()),
                            shape = CircleShape,
                            color = if (input.isNotBlank() || offerText.isNotBlank()) TerritoryAccent else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (sending) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send",
                                        tint = if (input.isNotBlank() || offerText.isNotBlank()) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (error != null) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            error!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        },
        modifier = modifier,
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = TerritoryAccent)
                }

                messages.isEmpty() -> Box(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(TerritoryAccent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = TerritoryAccent
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "Start the Conversation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Ask questions about condition, inspection score, or submit a price offer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        val isMine = msg.sender_id == currentUserId
                        ModernMessageBubble(msg = msg, isMine = isMine)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModernMessageBubble(msg: ChatMessage, isMine: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        if (msg.offer_price != null) {
            // Highlighted Offer Card
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = if (isMine) TerritoryAccent.copy(alpha = 0.15f) else ActivateGreen.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, if (isMine) TerritoryAccent else ActivateGreen),
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .widthIn(max = 280.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.LocalOffer,
                        contentDescription = null,
                        tint = if (isMine) TerritoryAccent else ActivateGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            if (isMine) "Offer Sent" else "Offer Received",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isMine) TerritoryAccent else ActivateGreen
                        )
                        Text(
                            "₹${msg.offer_price.toInt()}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isMine) TerritoryAccent else ActivateGreen
                        )
                    }
                }
            }
        }

        // Regular Chat Bubble
        Surface(
            shape = if (isMine) {
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
            } else {
                RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp)
            },
            color = if (isMine) TerritoryAccent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Text(
                    text = msg.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = timeAgo(msg.created_at),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (isMine) Color.White.copy(alpha = 0.75f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
