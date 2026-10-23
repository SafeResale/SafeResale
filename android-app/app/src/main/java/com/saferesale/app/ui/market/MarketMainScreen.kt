package com.saferesale.app.ui.market

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.saferesale.app.ui.theme.TerritoryAccent

@Composable
fun MarketMainScreen(
    token: String?,
    onStartCheck: () -> Unit,
    onOpenListing: (String) -> Unit,
    onExplore: (String?) -> Unit,
    onLogout: () -> Unit,
    initialCategory: String?,
    onOpenThread: (String) -> Unit = onOpenListing,
    onDiagnoseDevice: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val marketNav = rememberNavController()
    val backStackEntry by marketNav.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    fun go(route: String) {
        marketNav.navigate(route) {
            popUpTo(marketNav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            MarketBottomBar(
                selected = currentRoute ?: "home",
                onSelect = { go(it) },
                onSell = onStartCheck,
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = marketNav,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding),
        ) {
            composable("home") {
                HomeScreen(
                    token = token,
                    actions = HomeActions(
                        onOpenListing = onOpenListing,
                        onExplore = onExplore,
                        onSell = onStartCheck,
                    ),
                )
            }
            composable("inbox") {
                InboxScreen(token = token, onStartCheck = onStartCheck, onOpenThread = onOpenThread)
            }
            composable("myads") {
                MyAdsScreen(
                    token = token,
                    onOpenListing = onOpenListing,
                    onNewCheck = onStartCheck,
                )
            }
            composable("profile") {
                ProfileScreen(
                    token = token,
                    onOpenListing = onOpenListing,
                    onNewCheck = onStartCheck,
                    onMyAds = { go("myads") },
                    onLogout = onLogout,
                    onDiagnoseDevice = onDiagnoseDevice,
                )
            }
        }
    }
}

/**
 * Modern Curved Bottom Navigation Bar with centered prominent Sell action
 */
@Composable
private fun MarketBottomBar(
    selected: String,
    onSelect: (String) -> Unit,
    onSell: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 10.dp,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Home
            ModernNavItem(
                tab = "home",
                icon = Icons.Default.Home,
                label = "Home",
                selected = selected == "home",
                onSelect = onSelect,
                modifier = Modifier.weight(1f)
            )

            // Chat
            ModernNavItem(
                tab = "inbox",
                icon = Icons.AutoMirrored.Filled.Chat,
                label = "Chat",
                selected = selected == "inbox",
                onSelect = onSelect,
                modifier = Modifier.weight(1f)
            )

            // Prominent Center "+" Circle Action
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    onClick = onSell,
                    shape = CircleShape,
                    color = TerritoryAccent,
                    shadowElevation = 6.dp,
                    tonalElevation = 4.dp,
                    modifier = Modifier.size(50.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Post an ad",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // My Ads
            ModernNavItem(
                tab = "myads",
                icon = Icons.Default.Sell,
                label = "My Ads",
                selected = selected == "myads",
                onSelect = onSelect,
                modifier = Modifier.weight(1f)
            )

            // Profile
            ModernNavItem(
                tab = "profile",
                icon = Icons.Default.Person,
                label = "Profile",
                selected = selected == "profile",
                onSelect = onSelect,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ModernNavItem(
    tab: String,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val activeColor = TerritoryAccent
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)

    Column(
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onSelect(tab) }
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) activeColor.copy(alpha = 0.12f) else Color.Transparent)
                .padding(horizontal = 12.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (selected) activeColor else inactiveColor,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) activeColor else inactiveColor,
            maxLines = 1,
        )
    }
}