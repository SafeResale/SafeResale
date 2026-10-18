package com.saferesale.app.ui.market

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.VerifiedUser
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
import androidx.compose.ui.unit.sp
import com.saferesale.app.data.TokenStore
import com.saferesale.app.data.marketplace.MarketplaceRepository
import com.saferesale.app.ui.theme.ActivateGreen
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
    var showLogoutDialog by remember { mutableStateOf(false) }

    val cachedKycName = remember { TokenStore.getKycName(ctx) }
    val isKycDone = remember { TokenStore.kycDone(ctx) }

    LaunchedEffect(token) {
        val p = runCatching { MarketplaceRepository.profile(token) }.getOrNull()
        if (p != null) {
            val resolvedName = p.name.ifBlank { cachedKycName.orEmpty() }
            profileState = ProfileDisplay(p.id, p.email ?: p.phone, resolvedName, p.verified || isKycDone)
            name = resolvedName
        } else if (!cachedKycName.isNullOrBlank()) {
            name = cachedKycName
            profileState = ProfileDisplay("local", null, cachedKycName, isKycDone)
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Account Profile",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    IconButton(onClick = { editing = true }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit Profile",
                            tint = TerritoryAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Modern Profile Hero Card ────────────────────────────────────────────
            item {
                ModernProfileHeroCard(
                    profileState = profileState,
                    name = name,
                    isKycDone = isKycDone,
                    onEdit = { editing = true }
                )
            }

            // ── Quick Trust & Seller Overview ───────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickStatCard(
                        title = "Seller KYC",
                        value = if (isKycDone) "Approved" else "Pending",
                        icon = Icons.Default.VerifiedUser,
                        color = if (isKycDone) ActivateGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    QuickStatCard(
                        title = "Inspection",
                        value = "18-Point CoreV",
                        icon = Icons.Default.Shield,
                        color = TerritoryAccent,
                        modifier = Modifier.weight(1f)
                    )
                    QuickStatCard(
                        title = "Trust Status",
                        value = "Certified",
                        icon = Icons.Default.Security,
                        color = ActivateGreen,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ── Group 1: Marketplace Actions ────────────────────────────────────────
            item {
                SettingsSection(title = "Marketplace") {
                    ModernMenuTile(
                        icon = Icons.Default.PostAdd,
                        title = "Post a New Ad",
                        subtitle = "List a device with 18-point verification",
                        tint = TerritoryAccent,
                        onClick = onNewCheck
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ModernMenuTile(
                        icon = Icons.AutoMirrored.Filled.List,
                        title = "My Ads & Listings",
                        subtitle = "Manage active listings and drafts",
                        tint = TerritoryAccent,
                        onClick = onMyAds
                    )
                }
            }

            // ── Group 2: Account & Verification ────────────────────────────────────
            item {
                SettingsSection(title = "Account & Verification") {
                    ModernMenuTile(
                        icon = Icons.Default.Verified,
                        title = "DigiLocker KYC Status",
                        subtitle = if (isKycDone) "Legal ID verified with Government Database" else "Verify legal identity to unlock seller badge",
                        tint = if (isKycDone) ActivateGreen else TerritoryAccent,
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar(
                                    if (isKycDone) "KYC verified: $name" else "Start KYC from sell ad flow."
                                )
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ModernMenuTile(
                        icon = Icons.Default.Edit,
                        title = "Edit Display Details",
                        subtitle = "Update display name & phone number",
                        tint = TerritoryAccent,
                        onClick = { editing = true }
                    )
                }
            }

            // ── Group 3: Support & Information ─────────────────────────────────────
            item {
                SettingsSection(title = "Support & Legal") {
                    ModernMenuTile(
                        icon = Icons.Default.Mail,
                        title = "Contact Support",
                        subtitle = "Questions or feedback? Email our team",
                        tint = TerritoryAccent,
                        onClick = {
                            val i = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:support@saferesale.com")).apply {
                                putExtra(Intent.EXTRA_SUBJECT, "SafeResale Support Request")
                            }
                            runCatching { ctx.startActivity(i) }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    ModernMenuTile(
                        icon = Icons.Default.Info,
                        title = "Terms & Privacy Policy",
                        subtitle = "SafeResale terms of service",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Privacy Policy and Terms of Service available at saferesale.com")
                            }
                        }
                    )
                }
            }

            // ── Log Out Button ──────────────────────────────────────────────────────
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, DeactivateRed.copy(alpha = 0.25f)),
                    onClick = { showLogoutDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(DeactivateRed.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Logout, contentDescription = null, tint = DeactivateRed, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Log Out", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = DeactivateRed)
                            Text("Sign out of this device", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // ── Edit Profile Modal Sheet ────────────────────────────────────────────────
    if (editing) {
        ModalBottomSheet(
            onDismissRequest = { editing = false },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    "Edit Profile",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = {
                        saving = true
                        scope.launch {
                            try {
                                val u = MarketplaceRepository.updateProfile(token, name, phone)
                                name = u.name
                                profileState = ProfileDisplay(u.id, u.email ?: u.phone, u.name, u.verified || isKycDone)
                                editing = false
                                snackbarHostState.showSnackbar("Profile updated successfully")
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("Failed to update profile: ${e.message}")
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = !saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TerritoryAccent)
                ) {
                    if (saving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Saving changes...")
                    } else {
                        Text("Save Changes", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    // ── Logout Confirmation Dialog ──────────────────────────────────────────────
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out") },
            text = { Text("Are you sure you want to sign out of SafeResale?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        TokenStore.clear(ctx)
                        onLogout()
                    }
                ) {
                    Text("Log Out", color = DeactivateRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

private data class ProfileDisplay(
    val id: String,
    val email: String?,
    val name: String?,
    val verified: Boolean,
)

@Composable
private fun ModernProfileHeroCard(
    profileState: ProfileDisplay?,
    name: String,
    isKycDone: Boolean,
    onEdit: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(TerritoryAccent.copy(alpha = 0.12f))
                    .border(2.dp, TerritoryAccent.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                val initial = name.firstOrNull()?.uppercaseChar()?.toString()
                    ?: profileState?.name?.firstOrNull()?.uppercaseChar()?.toString()
                    ?: "U"
                Text(
                    text = initial,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = TerritoryAccent
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = name.ifBlank { profileState?.name ?: "SafeResale Member" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = profileState?.email ?: "Verified Account",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (isKycDone || profileState?.verified == true) {
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = ActivateGreen.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, ActivateGreen.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = null,
                            tint = ActivateGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "VERIFIED SELLER",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = ActivateGreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(title, style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = TerritoryAccent,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun ModernMenuTile(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(1.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}