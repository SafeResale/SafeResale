package com.saferesale.app.ui.market

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saferesale.app.ui.theme.TerritoryAccent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    token: String?,
    onStartCheck: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(topBar = {
        TopAppBar(title = { Text("Chat") })
    }) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
        ) {
            item { Spacer(Modifier.height(24.dp)) }
            item {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(50))
                            .background(TerritoryAccent.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = TerritoryAccent,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No conversations yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Secure buyer-seller chat is coming. For now you can reach a seller directly from any listing, and replies land on your email.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onStartCheck,
                        shape = RoundedCornerShape(26.dp),
                    ) {
                        Text("Sell a device")
                    }
                    Spacer(Modifier.height(40.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Inspection status updates and moderation messages will appear here once chat is live.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}