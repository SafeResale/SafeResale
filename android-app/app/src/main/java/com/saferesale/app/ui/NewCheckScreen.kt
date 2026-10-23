package com.saferesale.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferesale.app.data.ApiClient
import com.saferesale.app.data.DraftReq
import com.saferesale.app.data.marketplace.MyListingsStore
import com.saferesale.app.ui.components.OSMMapPicker
import com.saferesale.app.ui.market.formatCategory
import com.saferesale.app.ui.theme.TerritoryAccent
import kotlinx.coroutines.launch

val CHECK_CATEGORIES = listOf(
    "mobile", "laptop", "electronics", "camera", "gaming", "appliance",
    "car", "bike", "furniture", "accessory", "bicycle", "watch",
    "books", "sports", "toys", "instruments"
)

private const val DEFAULT_LAT = 28.6139
private const val DEFAULT_LNG = 77.2090

private fun categoryIcon(category: String): ImageVector = when (category) {
    "mobile" -> Icons.Filled.PhoneAndroid
    "laptop" -> Icons.Filled.Laptop
    "electronics" -> Icons.Filled.Devices
    "camera" -> Icons.Filled.CameraAlt
    "gaming" -> Icons.Filled.SportsEsports
    "appliance" -> Icons.Filled.Kitchen
    "car" -> Icons.Filled.DirectionsCar
    "bike" -> Icons.Filled.TwoWheeler
    "furniture" -> Icons.Filled.Chair
    "accessory" -> Icons.Filled.Headphones
    "bicycle" -> Icons.AutoMirrored.Filled.DirectionsBike
    "watch" -> Icons.Filled.Watch
    "books" -> Icons.AutoMirrored.Filled.MenuBook
    "sports" -> Icons.Filled.FitnessCenter
    "toys" -> Icons.Filled.Toys
    "instruments" -> Icons.Filled.MusicNote
    else -> Icons.Filled.Sell
}

/**
 * Modern Post-an-Ad Form — Step 1 of the verified seller flow:
 * Listing Details -> Photo Capture -> Hardware Diagnostics -> Inspection Score
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewCheckScreen(
    token: String?,
    onBack: (() -> Unit)? = null,
    onCreated: (String, String) -> Unit
) {
    val context = LocalContext.current
    var category by remember { mutableStateOf("mobile") }
    var title by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("good") }
    var storage by remember { mutableStateOf("") }
    var registrationNo by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf(DEFAULT_LAT) }
    var lng by remember { mutableStateOf(DEFAULT_LNG) }
    var address by remember { mutableStateOf("") }
    var geocoding by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var brandSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var modelSuggestions by remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }
    var showBrandBottomSheet by remember { mutableStateOf(false) }
    var showModelBottomSheet by remember { mutableStateOf(false) }
    var selectedItemDetails by remember { mutableStateOf<Map<String, Any>?>(null) }

    val topBrandsForCategory = remember(category) {
        when (category) {
            "mobile" -> listOf("Apple", "Samsung", "Google", "OnePlus", "Xiaomi", "Realme", "Vivo", "Motorola")
            "laptop" -> listOf("Apple", "Dell", "HP", "Lenovo", "Asus", "Acer")
            "car" -> listOf("Maruti Suzuki", "Hyundai", "Tata", "Mahindra", "Toyota", "Honda", "Kia")
            "bike" -> listOf("Royal Enfield", "Honda", "Yamaha", "Bajaj", "TVS", "KTM", "Hero")
            "camera" -> listOf("Sony", "Canon", "Nikon", "Fujifilm")
            "gaming" -> listOf("Sony", "Microsoft", "Nintendo")
            else -> emptyList()
        }
    }

    LaunchedEffect(brand, category) {
        if (brand.length >= 2) {
            try {
                val res = ApiClient.service.searchCatalog(q = brand, category = category, limit = 10)
                val items = (res["items"] as? List<*>)?.filterIsInstance<Map<String, Any>>() ?: emptyList()
                val brands = items.mapNotNull { it["brand"]?.toString() }.distinct()
                brandSuggestions = if (brands.isNotEmpty()) brands else topBrandsForCategory.filter { it.contains(brand, ignoreCase = true) }
            } catch (_: Exception) {
                brandSuggestions = topBrandsForCategory.filter { it.contains(brand, ignoreCase = true) }
            }
        } else {
            brandSuggestions = topBrandsForCategory
        }
    }

    LaunchedEffect(model, brand, category) {
        if (model.length >= 2 || (brand.isNotEmpty() && model.isEmpty())) {
            try {
                val res = if (model.length >= 2) {
                    ApiClient.service.searchCatalog(q = "$brand $model".trim(), category = category, limit = 15)
                } else {
                    ApiClient.service.catalogModels(category = category, brand = brand)
                }
                val items = (res["items"] as? List<*>)?.filterIsInstance<Map<String, Any>>()
                    ?: (res["models"] as? List<*>)?.filterIsInstance<Map<String, Any>>()
                    ?: emptyList()
                modelSuggestions = items
            } catch (_: Exception) {
                modelSuggestions = emptyList()
            }
        }
    }

    fun onLocation(latitude: Double, longitude: Double) {
        lat = latitude
        lng = longitude
        if (!geocoding) {
            geocoding = true
            scope.launch {
                try {
                    ApiClient.reverseGeocode(latitude, longitude)?.let { address = it }
                } catch (_: Exception) {
                } finally {
                    geocoding = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Post an Ad",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Step 1 of 3: Listing details",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .padding(start = 16.dp, end = 8.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(TerritoryAccent.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.AddBusiness,
                                contentDescription = null,
                                tint = TerritoryAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 4.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    if (msg.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    msg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            val p = price.toDoubleOrNull()
                            if (token == null) {
                                msg = "Please log in to create a listing"
                                return@Button
                            }
                            if (title.isBlank()) {
                                msg = "Please enter an ad title"
                                return@Button
                            }
                            if (p == null || p <= 0) {
                                msg = "Please enter a valid price (> 0)"
                                return@Button
                            }

                            busy = true
                            msg = ""
                            scope.launch {
                                try {
                                    val res = ApiClient.service.createDraft(
                                        DraftReq(
                                            category = category,
                                            title = title.trim(),
                                            price = p,
                                            brand = brand.trim().ifBlank { null },
                                            model = model.trim().ifBlank { null },
                                            storage = storage.trim().ifBlank { null },
                                            battery_health = null,
                                            condition = condition,
                                            registration_no = registrationNo.trim().ifBlank { null },
                                            notes = notes.trim().ifBlank { null },
                                            latitude = lat,
                                            longitude = lng,
                                            address = address.trim().ifBlank { null }
                                        ),
                                        "Bearer $token"
                                    )
                                    val id = ((res["listing"] as? Map<*, *>)?.get("_id") as? String)
                                        ?: throw RuntimeException("No listing id returned from server")

                                    MyListingsStore.add(context, id)
                                    onCreated(id, category)
                                } catch (e: Exception) {
                                    msg = "Failed to create ad: ${e.message}"
                                    busy = false
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TerritoryAccent)
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Creating listing...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        } else {
                            Text(
                                "Continue to Photo Verification",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Step Progress Indicator ─────────────────────────────────────────────
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Listing Progress",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = TerritoryAccent
                        )
                        Text(
                            "1 of 3 Steps",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(TerritoryAccent)
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "1. Ad Details  ->  2. Multi-angle Photos  ->  3. Hardware Score",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Category Selection ──────────────────────────────────────────────────
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Category,
                            contentDescription = null,
                            tint = TerritoryAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Select Category",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Choose the type of item you want to sell",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(14.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(CHECK_CATEGORIES) { c ->
                            val isSelected = category == c
                            val bg = if (isSelected) TerritoryAccent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            val border = if (isSelected) TerritoryAccent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            val contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface

                            Column(
                                modifier = Modifier
                                    .width(84.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(bg)
                                    .border(BorderStroke(1.5.dp, border), RoundedCornerShape(14.dp))
                                    .clickable { category = c }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    categoryIcon(c),
                                    contentDescription = null,
                                    tint = contentColor,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = formatCategory(c),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = contentColor,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // ── Basic Details Card ──────────────────────────────────────────────────
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Description,
                            contentDescription = null,
                            tint = TerritoryAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Ad Information",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Title
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Ad Title *") },
                        placeholder = { Text("e.g. iPhone 14 Pro 128GB Deep Purple") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Filled.Sell, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Quick Brand Recommendation Chips for Selected Category
                    if (topBrandsForCategory.isNotEmpty()) {
                        Column {
                            Text(
                                "Popular Brands in ${formatCategory(category)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(topBrandsForCategory) { b ->
                                    val isSel = brand.equals(b, ignoreCase = true)
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = if (isSel) TerritoryAccent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        border = BorderStroke(1.dp, if (isSel) TerritoryAccent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                        modifier = Modifier.clickable {
                                            brand = b
                                            model = ""
                                            showModelBottomSheet = true
                                        }
                                    ) {
                                        Text(
                                            b,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Price & Brand with Searchable BottomSheet
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = price,
                            onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' } },
                            label = { Text("Price *") },
                            placeholder = { Text("0") },
                            singleLine = true,
                            prefix = {
                                Text(
                                    "₹ ",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = TerritoryAccent
                                )
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        )

                        OutlinedTextField(
                            value = brand,
                            onValueChange = { brand = it },
                            label = { Text("Brand") },
                            placeholder = { Text("Tap to select") },
                            trailingIcon = {
                                IconButton(onClick = { showBrandBottomSheet = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search Brand", tint = TerritoryAccent)
                                }
                            },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showBrandBottomSheet = true },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Model with Searchable Catalog BottomSheet
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model / Variant (Catalog)") },
                        placeholder = { Text(if (brand.isNotEmpty()) "Search $brand models..." else "Tap search for catalog...") },
                        trailingIcon = {
                            IconButton(onClick = { showModelBottomSheet = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search Catalog", tint = TerritoryAccent)
                            }
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showModelBottomSheet = true },
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Verified Catalog Badge Card
                    if (selectedItemDetails != null) {
                        val priceMap = selectedItemDetails!!["typical_resale_price"] as? Map<*, *>
                        val minP = (priceMap?.get("min") as? Number)?.toInt()
                        val maxP = (priceMap?.get("max") as? Number)?.toInt()
                        val specs = selectedItemDetails!!["specs"] as? Map<*, *>

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Verified SafeResale Catalog Model", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("${selectedItemDetails!!["brand"]} ${selectedItemDetails!!["model"]}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                if (minP != null && maxP != null) {
                                    Spacer(Modifier.height(2.dp))
                                    Text("Market benchmark: ₹${minP} - ₹${maxP}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (specs != null && specs.isNotEmpty()) {
                                    Spacer(Modifier.height(2.dp))
                                    val specSummary = specs.entries.joinToString(" • ") { "${it.key}: ${it.value}" }
                                    Text(specSummary, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // Condition Selector
                    Column {
                        Text(
                            "Physical Condition",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val conditionOptions = listOf(
                                Triple("good", "Like New", "Mint condition"),
                                Triple("moderate", "Good", "Normal usage"),
                                Triple("poor", "Fair", "Noticeable wear")
                            )

                            conditionOptions.forEach { (key, label, desc) ->
                                val selected = condition == key
                                Surface(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { condition = key },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) TerritoryAccent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    border = BorderStroke(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) TerritoryAccent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            label,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (selected) TerritoryAccent else MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            desc,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Category Specifics
                    if (category == "mobile" || category == "laptop") {
                        Column {
                            Text(
                                "Storage Capacity",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("64 GB", "128 GB", "256 GB", "512 GB", "1 TB").forEach { opt ->
                                    val isCur = storage == opt
                                    FilterChip(
                                        selected = isCur,
                                        onClick = { storage = if (isCur) "" else opt },
                                        label = { Text(opt) },
                                        leadingIcon = if (isCur) {
                                            { Icon(Icons.Filled.Check, contentDescription = null, Modifier.size(16.dp)) }
                                        } else null,
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = TerritoryAccent.copy(alpha = 0.15f),
                                            selectedLabelColor = TerritoryAccent
                                        )
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            OutlinedTextField(
                                value = storage,
                                onValueChange = { storage = it },
                                label = { Text("Storage (or enter custom)") },
                                placeholder = { Text("e.g. 256 GB") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }

                    if (category == "car" || category == "bike") {
                        OutlinedTextField(
                            value = registrationNo,
                            onValueChange = { registrationNo = it.uppercase() },
                            label = { Text("Vehicle Registration Plate *") },
                            placeholder = { Text(if (category == "bike") "e.g. KA03HY9449" else "e.g. KA03AB1234") },
                            singleLine = true,
                            leadingIcon = {
                                Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = TerritoryAccent)
                            },
                            supportingText = {
                                Text("Enables automated Parivahan RC verification badge")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // Description / Notes
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Description & Notes") },
                        placeholder = { Text("Mention accessories included, bill, box, battery health, any cosmetic wear...") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            // ── Pickup Location Card ────────────────────────────────────────────────
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = TerritoryAccent,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Pickup Location",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        "Tap on the map or move the pin to set the meetup/pickup location for buyers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    ) {
                        OSMMapPicker(
                            initialLat = lat,
                            initialLng = lng,
                            onLocationChange = ::onLocation,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text(if (geocoding) "Detecting address..." else "Pickup Address") },
                        placeholder = { Text("e.g. Indiranagar, Bengaluru") },
                        leadingIcon = {
                            if (geocoding) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = TerritoryAccent
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Place,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showBrandBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBrandBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            var brandQuery by remember { mutableStateOf(brand) }
            val filteredBrands = remember(brandQuery, topBrandsForCategory, brandSuggestions) {
                val pool = (topBrandsForCategory + brandSuggestions).distinct()
                if (brandQuery.isBlank()) pool
                else pool.filter { it.contains(brandQuery.trim(), ignoreCase = true) }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Select Brand",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Category: ${formatCategory(category)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showBrandBottomSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = brandQuery,
                    onValueChange = { brandQuery = it },
                    placeholder = { Text("Search brand (e.g. Apple, Samsung, Dell...)") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (brandQuery.isNotEmpty()) {
                            IconButton(onClick = { brandQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                if (topBrandsForCategory.isNotEmpty()) {
                    Text(
                        "POPULAR IN ${formatCategory(category).uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(topBrandsForCategory) { b ->
                            val isSel = brand.equals(b, ignoreCase = true)
                            FilterChip(
                                selected = isSel,
                                onClick = {
                                    brand = b
                                    model = ""
                                    showBrandBottomSheet = false
                                    showModelBottomSheet = true
                                },
                                label = { Text(b) },
                                leadingIcon = if (isSel) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                }

                Text(
                    "ALL BRANDS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 340.dp)
                ) {
                    if (brandQuery.isNotBlank() && !filteredBrands.any { it.equals(brandQuery.trim(), ignoreCase = true) }) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        brand = brandQuery.trim()
                                        showBrandBottomSheet = false
                                        showModelBottomSheet = true
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        "Use custom brand \"${brandQuery.trim()}\"",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                    }

                    items(filteredBrands) { b ->
                        val isSel = brand.equals(b, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    brand = b
                                    model = ""
                                    showBrandBottomSheet = false
                                    showModelBottomSheet = true
                                }
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    b,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium
                                )
                                if (isSel) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = TerritoryAccent)
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }

    if (showModelBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showModelBottomSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            var modelQuery by remember { mutableStateOf(model) }
            var isSearching by remember { mutableStateOf(false) }
            var searchResults by remember { mutableStateOf<List<Map<String, Any>>>(modelSuggestions) }

            LaunchedEffect(modelQuery, brand, category) {
                isSearching = true
                try {
                    val q = if (brand.isNotBlank()) "$brand $modelQuery".trim() else modelQuery.trim()
                    val res = if (q.isNotBlank()) {
                        ApiClient.service.searchCatalog(q = q, category = category, limit = 20)
                    } else if (brand.isNotBlank()) {
                        ApiClient.service.catalogModels(category = category, brand = brand)
                    } else {
                        ApiClient.service.searchCatalog(q = "", category = category, limit = 20)
                    }
                    val items = (res["items"] as? List<*>)?.filterIsInstance<Map<String, Any>>()
                        ?: (res["models"] as? List<*>)?.filterIsInstance<Map<String, Any>>()
                        ?: emptyList()
                    searchResults = items
                } catch (_: Exception) {
                    searchResults = modelSuggestions
                } finally {
                    isSearching = false
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            if (brand.isNotBlank()) "$brand Models" else "Search Model / Variant",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Verified SafeResale catalog benchmark & specs",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { showModelBottomSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = modelQuery,
                    onValueChange = { modelQuery = it },
                    placeholder = { Text(if (brand.isNotBlank()) "Search $brand models (e.g. S23, M2, Swift)..." else "Search model...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (modelQuery.isNotEmpty()) {
                            IconButton(onClick = { modelQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                if (isSearching) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = TerritoryAccent)
                    Spacer(Modifier.height(8.dp))
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 420.dp)
                ) {
                    if (modelQuery.isNotBlank()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        model = modelQuery.trim()
                                        showModelBottomSheet = false
                                        if (title.isBlank()) {
                                            title = if (brand.isNotBlank()) "$brand $model".trim() else model
                                        }
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Use custom: \"$modelQuery\"", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }

                    if (searchResults.isEmpty() && !isSearching) {
                        item {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No matching catalog models. Tap \"Use custom\" above to enter your exact model.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    items(searchResults) { item ->
                        val mName = item["model"]?.toString() ?: ""
                        val bName = item["brand"]?.toString() ?: brand
                        val priceMap = item["typical_resale_price"] as? Map<*, *>
                        val minP = (priceMap?.get("min") as? Number)?.toInt()
                        val maxP = (priceMap?.get("max") as? Number)?.toInt()
                        val specs = item["specs"] as? Map<*, *>
                        val isSel = model.equals(mName, ignoreCase = true)

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSel) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, if (isSel) TerritoryAccent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    brand = bName
                                    model = mName
                                    selectedItemDetails = item
                                    showModelBottomSheet = false
                                    if (title.isBlank()) {
                                        title = "$bName $mName".trim()
                                    }
                                }
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        mName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            bName,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                if (minP != null && maxP != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Typical resale: ₹$minP - ₹$maxP",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TerritoryAccent
                                    )
                                }

                                if (specs != null && specs.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    val specSummary = specs.entries.take(4).joinToString(" • ") { "${it.key}: ${it.value}" }
                                    Text(
                                        specSummary,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}