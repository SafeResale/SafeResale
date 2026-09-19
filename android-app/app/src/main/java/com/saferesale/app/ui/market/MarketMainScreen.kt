package com.saferesale.app.ui.market

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
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
                onFAB = onStartCheck,
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
                InboxScreen(token = token, onStartCheck = onStartCheck)
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
                )
            }
        }
    }
}

@Composable
private fun MarketBottomBar(
    selected: String,
    onSelect: (String) -> Unit,
    onFAB: () -> Unit,
) {
    Box(Modifier.fillMaxWidth().height(64.dp)) {
        Surface(
            shadowElevation = 8.dp,
            color = Color.White,
            modifier = Modifier.fillMaxWidth().height(64.dp).align(Alignment.BottomCenter),
        ) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MarketNavItem(tab = "home", icon = Icons.Default.Home, label = "Home", selected = selected == "home", onSelect = onSelect)
                MarketNavItem(tab = "inbox", icon = Icons.AutoMirrored.Filled.Chat, label = "Chat", selected = selected == "inbox", onSelect = onSelect)
                MarketNavItem(tab = "myads", icon = Icons.Default.ShoppingBag, label = "My Ads", selected = selected == "myads", onSelect = onSelect)
                MarketNavItem(tab = "profile", icon = Icons.Default.Person, label = "Profile", selected = selected == "profile", onSelect = onSelect)
            }
        }
        DiamondFab(
            modifier = Modifier.align(Alignment.TopCenter),
            onClick = onFAB,
        )
    }
}

@Composable
private fun RowScope.MarketNavItem(
    tab: String,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onSelect: (String) -> Unit,
) {
    val color = if (selected) TerritoryAccent else Color(0x80000000) // black 50%
    Column(
        Modifier.weight(1f).clickable(onClick = { onSelect(tab) }),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(3.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = color,
        )
    }
}

@Composable
private fun DiamondFab(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .rotate(45f)
                .clip(RoundedCornerShape(10.dp))
                .background(TerritoryAccent)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Sell",
                tint = Color.White,
                modifier = Modifier.rotate(-45f),
            )
        }
    }
}