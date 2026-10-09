package com.saferesale.app.ui.market

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saferesale.app.data.TokenStore
import com.saferesale.app.data.marketplace.MarketplaceRepository
import com.saferesale.app.ui.theme.DeactivateRed
import com.saferesale.app.ui.theme.TerritoryAccent
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    token: String?,
    onOpenListing: (String) -> Unit,
    onNewCheck: () -> Unit,
    onMyAds: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var profileState by remember { mutableStateOf<ProfileDisplay?>(null) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var savedTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val p = runCatching { MarketplaceRepository.profile(token) }.getOrNull()
        if (p != null) {
            profileState = ProfileDisplay(p.id, p.email ?: p.phone, p.name, p.verified)
            if (name.isBlank()) name = p.name
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("My Profile") })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ProfileHeader(profileState, name) }

            if (editing) {
                item {
                    Column {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Display name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Phone (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    saving = true
                                    scope.launch {
                                        try {
                                            val u = MarketplaceRepository.updateProfile(token, name, phone)
                                            name = u.name
                                            profileState = ProfileDisplay(u.id, u.email ?: u.phone, u.name, u.verified)
                                            savedTick++
                                            editing = false
                                        } catch (_: Exception) {
                                        } finally {
                                            saving = false
                                        }
                                    }
                                },
                                enabled = !saving,
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                if (saving) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Saving…")
                                } else {
                                    Text("Save changes")
                                }
                            }
                            if (savedTick > 0) {
                                Text(
                                    "Saved ✓",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF02AD11),
                                    modifier = Modifier.align(Alignment.CenterVertically),
                                )
                            }
                        }
                    }
                }
            }

            item {
                MenuTile(
                    icon = Icons.Default.ShoppingBag,
                    title = "Sell a device",
                    onClick = onNewCheck,
                )
            }
            item {
                MenuTile(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = "My Ads",
                    onClick = onMyAds,
                )
            }
            item {
                MenuTile(
                    icon = Icons.Default.Edit,
                    title = "Edit profile",
                    onClick = { editing = !editing },
                )
            }
            item {
                MenuTile(
                    icon = Icons.Default.Mail,
                    title = "Contact us",
                    onClick = {
                        val i = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:support@saferesale.com")).apply {
                            putExtra(Intent.EXTRA_SUBJECT, "SafeResale app inquiry")
                        }
                        runCatching { ctx.startActivity(i) }
                    },
                )
            }
            item {
                MenuTile(
                    icon = Icons.Default.Info,
                    title = "Privacy Policy & Terms",
                    onClick = {
                        scope.launch {
                            snackbarHostState.showSnackbar("Privacy Policy & Terms are available on our website.")
                        }
                    },
                )
            }

            item { Spacer(Modifier.height(8.dp)) }

            item {
                MenuTile(
                    icon = Icons.Default.ShoppingBag,
                    title = "Log out",
                    tint = DeactivateRed,
                    chevron = false,
                    onClick = {
                        TokenStore.clear(ctx)
                        onLogout()
                    },
                )
            }
        }
    }
}

private data class ProfileDisplay(
    val id: String,
    val email: String?,
    val name: String?,
    val verified: Boolean,
)

@Composable
private fun ProfileHeader(profileState: ProfileDisplay?, name: String) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(50))
                .background(TerritoryAccent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = TerritoryAccent,
                modifier = Modifier.size(38.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            name.ifBlank { profileState?.name ?: "…" },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            profileState?.email ?: "…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (profileState?.verified == true) {
            Spacer(Modifier.height(10.dp))
            Surface(color = TerritoryAccent, shape = RoundedCornerShape(8.dp)) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "VERIFIED SELLER",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuTile(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    tint: Color = TerritoryAccent,
    chevron: Boolean = true,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        border = BorderStroke(1.dp, Color(0x21000000)),
    ) {
        Row(
            Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tint.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            if (chevron) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}