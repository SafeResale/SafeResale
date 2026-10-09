package com.saferesale.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.saferesale.app.data.ApiClient
import com.saferesale.app.data.DiagReportReq
import com.saferesale.app.data.TokenStore
import com.saferesale.app.data.marketplace.MyListingsStore
import com.saferesale.app.diagnostics.CoreVScoreReport
import com.saferesale.app.ui.AuthScreen
import com.saferesale.app.ui.CaptureScreen
import com.saferesale.app.ui.InspectionRequestScreen
import com.saferesale.app.ui.NewCheckScreen
import com.saferesale.app.ui.ScoreScreen
import com.saferesale.app.ui.market.ChatThreadScreen
import com.saferesale.app.ui.market.ContactScreen
import com.saferesale.app.ui.market.ExploreScreen
import com.saferesale.app.ui.market.ListingDetailScreen
import com.saferesale.app.ui.market.MarketMainScreen
import com.saferesale.app.ui.market.ReportScreen
import com.saferesale.app.ui.navigation.CoreVNavGraph
import com.saferesale.app.ui.navigation.Screen
import com.saferesale.app.ui.theme.CoreVTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ApiClient.init(applicationContext)
        setContent {
            var darkTheme by remember { mutableStateOf(false) }
            CoreVTheme(darkTheme = darkTheme) {
                val nav = rememberNavController()
                val context = LocalContext.current
                var token by remember { mutableStateOf(TokenStore.access(context)) }

                // Observe forced logout (refresh token invalid)
                LaunchedEffect(Unit) {
                    ApiClient.onSessionCleared {
                        token = null
                    }
                }

                // Navigate to auth when token is cleared externally
                LaunchedEffect(token) {
                    if (token == null && nav.currentDestination?.route != "auth") {
                        nav.navigate("auth") { popUpTo(0) { inclusive = true } }
                    }
                }

                NavHost(
                    navController = nav,
                    startDestination = if (token != null) "main" else "auth",
                ) {
                    composable("auth") {
                        AuthScreen(onAuthed = { t ->
                            token = t
                            nav.navigate("main") { popUpTo("auth") { inclusive = true } }
                        })
                    }
                    composable("main") {
                        MarketMainScreen(
                            token = token,
                            onStartCheck = { nav.navigate("newcheck") },
                            onOpenListing = { id -> nav.navigate("listing/$id") },
                            onExplore = { cat ->
                                nav.navigate(if (cat == null) "explore" else "explore?cat=$cat") {
                                    launchSingleTop = true
                                }
                            },
                            onLogout = {
                                token = null
                                nav.navigate("auth") { popUpTo(0) { inclusive = true } }
                            },
                            initialCategory = null,
                            onOpenThread = { id -> nav.navigate("chat/$id") },
                        )
                    }
                    composable(
                        "explore?cat={cat}",
                        arguments = listOf(navArgument("cat") { type = NavType.StringType; defaultValue = "" }),
                    ) { back ->
                        val cat = back.arguments?.getString("cat")
                        ExploreScreen(
                            token = token,
                            initialCategory = cat?.ifBlank { null },
                            onBack = { nav.popBackStack() },
                            onOpenListing = { id -> nav.navigate("listing/$id") },
                        )
                    }
                    composable("listing/{lid}") { back ->
                        val lid = back.arguments?.getString("lid")
                        ListingDetailScreen(
                            token = token,
                            listingId = lid.orEmpty(),
                            onBack = { nav.popBackStack() },
                            onReport = { id -> nav.navigate("report/$id") },
                            onContact = { id -> nav.navigate("chat/$id") },
                            onBookInspection = { id -> nav.navigate("inspection/$id") },
                        )
                    }
                    composable(
                        "chat/{lid}",
                        arguments = listOf(navArgument("lid") { type = NavType.StringType }),
                    ) { back ->
                        val lid = back.arguments?.getString("lid").orEmpty()
                        ChatThreadScreen(token = token, listingId = lid, onBack = { nav.popBackStack() })
                    }
                    composable("inspection/{lid}") { back ->
                        val lid = back.arguments?.getString("lid")
                        InspectionRequestScreen(
                            token = token,
                            listingId = lid.orEmpty(),
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("report/{lid}") { back ->
                        val lid = back.arguments?.getString("lid")
                        ReportScreen(
                            token = token,
                            listingId = lid.orEmpty(),
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("contact/{lid}") { back ->
                        val lid = back.arguments?.getString("lid")
                        ContactScreen(
                            token = token,
                            listingId = lid.orEmpty(),
                            onBack = { nav.popBackStack() },
                        )
                    }
                    // Guided check flow: draft -> 8 photos -> diagnostics -> score.
                    // The listingId created in newcheck travels through every step,
                    // so uploads + diagnostics + scores all land on the same listing.
                    composable("newcheck") {
                        NewCheckScreen(token = token, onCreated = { lid ->
                            MyListingsStore.add(context, lid)
                            nav.navigate("capture/$lid")
                        })
                    }
                    composable(
                        "capture/{lid}",
                        arguments = listOf(navArgument("lid") { type = NavType.StringType })
                    ) { back ->
                        val lid = back.arguments?.getString("lid")
                        CaptureScreen(token = token, listingId = lid, onDone = { nav.navigate("inspect/$lid") })
                    }
                    // Device inspection = CoreV's real bench (all 18 modules), opened
                    // in check-flow mode with a sync bar. No custom diagnostics UI.
                    composable(
                        "inspect/{lid}",
                        arguments = listOf(navArgument("lid") { type = NavType.StringType })
                    ) { back ->
                        val lid = back.arguments?.getString("lid")
                        BenchScaffold(
                            darkTheme = darkTheme,
                            onToggleTheme = { darkTheme = !darkTheme },
                            onBack = { nav.popBackStack() },
                            flowListingId = lid,
                            flowToken = token,
                            onFlowScore = { id -> nav.navigate("score/$id") }
                        )
                    }
                    composable(
                        "score/{lid}",
                        arguments = listOf(navArgument("lid") { type = NavType.StringType })
                    ) { back ->
                        val lid = back.arguments?.getString("lid")
                        ScoreScreen(token = token, listingId = lid, onDone = {
                            nav.navigate("main") { popUpTo("main") { inclusive = false } }
                        })
                    }
                    composable("bench") {
                        BenchScaffold(
                            darkTheme = darkTheme,
                            onToggleTheme = { darkTheme = !darkTheme },
                            onBack = { nav.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BenchScaffold(
    darkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onBack: () -> Unit,
    // Check-flow mode: when set, the bench IS the device-inspection step and a
    // bottom bar collects the touch check + syncs CoreV results to the score.
    flowListingId: String? = null,
    flowToken: String? = null,
    onFlowScore: ((String) -> Unit)? = null
) {
    val benchNav = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    val inFlow = flowListingId != null && onFlowScore != null

    var touchCovered by remember { mutableStateOf(setOf<Int>()) }
    var syncing by remember { mutableStateOf(false) }
    var syncMsg by remember { mutableStateOf("") }

    // Runtime permissions up front so CoreV probes + contract gates see them.
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(inFlow) {
        if (inFlow) permLauncher.launch(arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_CONNECT,
        ))
    }

    fun syncCoreVResults() {
        val lid = flowListingId ?: return
        val token = flowToken ?: run { syncMsg = "Login expired — go back and log in again"; return }
        syncing = true
        syncMsg = "Reading CoreV results…"
        scope.launch {
            try {
                // Same CoreV repositories the bench screens display, mapped to
                // the scoring contract (CoreVScoreReport) — no custom probes.
                val report = CoreVScoreReport.build(ctx, "mobile", touchCovered.size)
                val passed = report.tests.count { it.status == "passed" }
                syncMsg = "Syncing ${report.tests.size} CoreV results ($passed passed)…"
                val body = DiagReportReq(
                    device = mapOf("model" to report.device.model,
                        "os_version" to report.device.os_version, "sdk" to report.device.sdk),
                    category = report.category,
                    skipped = report.skipped,
                    tests = report.tests.map {
                        mapOf("id" to it.id, "status" to it.status, "value" to it.value,
                            "unit" to it.unit, "simulated" to it.simulated,
                            "measured_at" to it.measured_at, "meta" to it.meta)
                    }
                )
                val res = ApiClient.service.runDiagnostics(lid, body, "Bearer $token")
                val srvScore = (res["diagnostic_score"] as? Number)?.toInt()
                syncMsg = "Server score $srvScore/100 — opening score…"
                onFlowScore?.invoke(lid)
            } catch (e: Exception) {
                syncMsg = "Sync failed: ${e.message}"
            } finally {
                syncing = false
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            BenchDrawer(onNavigate = { route ->
                scope.launch { drawerState.close() }
                benchNav.navigate(route) { launchSingleTop = true }
            })
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = {
                        val currentRoute by benchNav.currentBackStackEntryFlow
                            .collectAsState(initial = benchNav.currentBackStackEntry)
                        Text(
                            text = titleFor(currentRoute?.destination?.route),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        Row {
                            IconButton(onClick = onBack) {
                                Icon(Icons.Default.ArrowBack, "Back to SafeResale")
                            }
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, "Open bench menu")
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleTheme) {
                            Icon(
                                if (darkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                contentDescription = "Toggle theme",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                )
            },
            bottomBar = {
                if (inFlow) {
                    Surface(shadowElevation = 8.dp) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            // Contract touch check (CoreV has no tap-grid): tap all 9.
                            Text("Touch check — tap all 9 cells (${touchCovered.size}/9)",
                                style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.fillMaxWidth().padding(top = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (i in 0..8) {
                                    val done = i in touchCovered
                                    Card(
                                        onClick = { touchCovered = touchCovered + i },
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (done) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(if (done) "✓" else "${i + 1}",
                                                style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                            Button(onClick = { syncCoreVResults() }, enabled = !syncing,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                Text(if (syncing) "Syncing CoreV results…" else "Sync CoreV results → Score")
                            }
                            if (syncMsg.isNotEmpty()) Text(syncMsg,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        ) { padding ->
            CoreVNavGraph(navController = benchNav, modifier = Modifier.padding(padding))
        }
    }
}

fun titleFor(route: String?): String = when (route) {
    Screen.Dashboard.route -> "Hardware Bench"
    Screen.Device.route -> "Device Info"
    Screen.Cpu.route -> "CPU"
    Screen.Ram.route -> "RAM"
    Screen.Storage.route -> "Storage"
    Screen.Battery.route -> "Battery"
    Screen.Sensors.route -> "Sensors"
    Screen.Network.route -> "Network"
    Screen.NetworkTools.route -> "Network Tools"
    Screen.Camera.route -> "Camera"
    Screen.Audio.route -> "Audio"
    Screen.Display.route -> "Display"
    Screen.Security.route -> "Security"
    Screen.Apps.route -> "Installed Apps"
    Screen.Gps.route -> "GPS"
    Screen.Benchmark.route -> "Benchmark"
    Screen.HardwareTest.route -> "Hardware Tests"
    Screen.Export.route -> "Export Report"
    else -> "Hardware Bench"
}

data class BenchNavItem(val label: String, val icon: ImageVector, val route: String)

val benchNavItems = listOf(
    BenchNavItem("Dashboard", Icons.Default.Dashboard, Screen.Dashboard.route),
    BenchNavItem("Device Info", Icons.Default.PhoneAndroid, Screen.Device.route),
    BenchNavItem("CPU", Icons.Default.Memory, Screen.Cpu.route),
    BenchNavItem("RAM", Icons.Default.Storage, Screen.Ram.route),
    BenchNavItem("Storage", Icons.Default.SdStorage, Screen.Storage.route),
    BenchNavItem("Battery", Icons.Default.BatteryChargingFull, Screen.Battery.route),
    BenchNavItem("Sensors", Icons.Default.Sensors, Screen.Sensors.route),
    BenchNavItem("Network", Icons.Default.Wifi, Screen.Network.route),
    BenchNavItem("Network Tools", Icons.Default.NetworkCheck, Screen.NetworkTools.route),
    BenchNavItem("Camera", Icons.Default.CameraAlt, Screen.Camera.route),
    BenchNavItem("Audio", Icons.Default.VolumeUp, Screen.Audio.route),
    BenchNavItem("Display", Icons.Default.Monitor, Screen.Display.route),
    BenchNavItem("Security", Icons.Default.Security, Screen.Security.route),
    BenchNavItem("Installed Apps", Icons.Default.Apps, Screen.Apps.route),
    BenchNavItem("GPS", Icons.Default.LocationOn, Screen.Gps.route),
    BenchNavItem("Benchmark", Icons.Default.Speed, Screen.Benchmark.route),
    BenchNavItem("Hardware Tests", Icons.Default.BugReport, Screen.HardwareTest.route),
    BenchNavItem("Export Report", Icons.Default.FileDownload, Screen.Export.route),
)

@Composable
fun BenchDrawer(onNavigate: (String) -> Unit) {
    ModalDrawerSheet(
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        modifier = Modifier.width(280.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.mipmap.ic_launcher_round),
                    contentDescription = "SafeResale bench icon",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "SafeResale",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    )
                    Text(
                        "Hardware Bench · 18 modules",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            benchNavItems.forEach { item ->
                NavigationDrawerItem(
                    label = { Text(item.label, style = MaterialTheme.typography.bodyMedium) },
                    icon = { Icon(item.icon, item.label, modifier = Modifier.size(20.dp)) },
                    selected = false,
                    onClick = { onNavigate(item.route) },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(
                "SafeResale · CoreV-based diagnostics",
                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            )
        }
    }
}
