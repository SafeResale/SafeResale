package com.saferesale.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saferesale.app.data.TokenStore
import com.saferesale.app.data.marketplace.MarketplaceRepository
import com.saferesale.app.domain.model.MarketListing
import com.saferesale.app.ui.market.ApiState
import com.saferesale.app.ui.market.formatPrice
import com.saferesale.app.ui.market.rememberApi
import com.saferesale.app.ui.theme.TerritoryAccent
import kotlinx.coroutines.launch

private val dateOptions = listOf(
    "Today",
    "Tomorrow",
    "Within 2 days",
    "Within 5 days",
    "No preference",
)

@Composable
fun InspectionRequestScreen(
    token: String?,
    listingId: String,
    onBack: () -> Unit,
) {
    val listingState = rememberApi(listingId) { MarketplaceRepository.listing(token, listingId) }
    var date by remember { mutableStateOf("Within 2 days") }
    var note by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Book an inspection", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color.Black,
                    navigationIconContentColor = Color.Black,
                ),
            )
        },
        containerColor = Color(0xFFF6F5FA),
        bottomBar = {
            if (!done) {
                Surface(color = Color.White, shadowElevation = 8.dp) {
                    Button(
                        onClick = {
                            scope.launch {
                                submitting = true
                                error = null
                                try {
                                    MarketplaceRepository.createInspection(token, listingId, date, note.trim().takeIf { it.isNotBlank() })
                                    done = true
                                } catch (e: Exception) {
                                    error = e.message ?: "Could not book the inspection"
                                } finally {
                                    submitting = false
                                }
                            }
                        },
                        enabled = !submitting,
                        modifier = Modifier.fillMaxWidth().padding(12.dp).height(52.dp),
                        shape = RoundedCornerShape(26.dp),
                    ) {
                        if (submitting) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Confirm inspection request", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            when (val s = listingState) {
                is ApiState.Loading ->
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }

                is ApiState.Error ->
                    Text(s.message, color = MaterialTheme.colorScheme.error)

                is ApiState.Success -> {
                    val listing = s.data
                    header(listing)

                    Spacer(Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x21000000)),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Shield,
                                    contentDescription = null,
                                    tint = TerritoryAccent,
                                    modifier = Modifier.size(20.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "How it works",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "A verified inspector tests the device in person (battery, display, cameras, ports). You choose when — the seller and an inspector coordinate the visit. You only hand over money after the report comes back.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Spacer(Modifier.height(12.dp))

                    Text(
                        "Preferred date",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(dateOptions.size) { i ->
                            val option = dateOptions[i]
                            val isSelected = option == date
                            Surface(
                                onClick = { date = option },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) TerritoryAccent else Color.White,
                                border = BorderStroke(1.dp, if (isSelected) TerritoryAccent else Color(0x22000000)),
                            ) {
                                Text(
                                    option,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSelected) Color.White else Color.Black,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Note for the seller & inspector (optional)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(240) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. Can visit after 6 pm, would like the box checked too…") },
                        shape = RoundedCornerShape(14.dp),
                        minLines = 3,
                    )

                    error?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }

    if (done) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = Color.White,
            shape = RoundedCornerShape(20.dp),
            icon = {
                Surface(shape = CircleShape, color = TerritoryAccent.copy(alpha = 0.12f)) {
                    Icon(
                        Icons.Default.Event,
                        contentDescription = null,
                        tint = TerritoryAccent,
                        modifier = Modifier.padding(10.dp).size(28.dp),
                    )
                }
            },
            title = { Text("Request sent") },
            text = { Text("The seller and a SafeResale inspector have been notified. Track the status on the listing page.") },
            confirmButton = {
                Button(onClick = { onBack() }) {
                    Text("Done")
                }
            },
        )
    }
}

@Composable
private fun header(listing: MarketListing) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(listing.title ?: "Device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(
                formatPrice(listing.price),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TerritoryAccent,
            )
        }
    }
}