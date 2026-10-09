package com.saferesale.app.ui.market

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.saferesale.app.data.marketplace.MarketplaceRepository
import com.saferesale.app.ui.theme.TealAccent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactScreen(
    token: String?,
    listingId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var prefillDone by remember { mutableStateOf(false) }

    val listingState = rememberApi(listingId) { MarketplaceRepository.listing(token, listingId) }
    val listingTitle = (listingState as? ApiState.Success)?.data?.title.orEmpty()

    LaunchedEffect(Unit) {
        if (!prefillDone) {
            prefillDone = true
            runCatching { MarketplaceRepository.profile(token) }
                .onSuccess {
                    if (name.isBlank()) name = it.name
                    if (email.isBlank()) email = it.email
                }
        }
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Contact seller") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
        )
    }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
        ) {
            if (sent) {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(Modifier.height(32.dp))
                    Text("Message sent", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "The seller will be able to reply to your email address.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(onClick = onBack) { Text("Back to listing") }
                }
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = TealAccent.copy(alpha = 0.12f))) {
                    Text(
                        "About: $listingTitle",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(14.dp),
                    )
                }
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Your email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text("Message") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(20.dp))
                Button(
                    onClick = {
                        when {
                            name.isBlank() -> error = "Please enter your name"
                            email.isBlank() || !email.contains("@") -> error = "Please enter a valid email"
                            message.isBlank() -> error = "Please write a message"
                            else -> {
                                busy = true
                                error = null
                                scope.launch {
                                    try {
                                        MarketplaceRepository.contact(
                                            name = name,
                                            email = email,
                                            subject = "Interested in \"$listingTitle\" (#$listingId)",
                                            message = message,
                                        )
                                        sent = true
                                    } catch (e: Exception) {
                                        error = e.message ?: "Could not send message"
                                    } finally {
                                        busy = false
                                    }
                                }
                            }
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (busy) "Sending…" else "Send message")
                }
            }
        }
    }
}