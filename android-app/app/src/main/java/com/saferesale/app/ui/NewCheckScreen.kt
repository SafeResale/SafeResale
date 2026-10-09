package com.saferesale.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saferesale.app.data.ApiClient
import com.saferesale.app.data.DraftReq
import com.saferesale.app.ui.components.OSMMapPicker
import kotlinx.coroutines.launch

val CHECK_CATEGORIES = listOf("mobile", "laptop", "tablet")
private val CONDITIONS = listOf("good", "moderate", "poor")
private const val DEFAULT_LAT = 28.6139
private const val DEFAULT_LNG = 77.2090

/** Listing details form (source "Post an ad" flow): category, title, price,
 *  brand, model, condition, storage, notes + OSM location. Creates the draft
 *  that later carries photos, diagnostics and the score. */
@Composable
fun NewCheckScreen(token: String?, onCreated: (String) -> Unit) {
    var category by remember { mutableStateOf("mobile") }
    var title by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("good") }
    var storage by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf(DEFAULT_LAT) }
    var lng by remember { mutableStateOf(DEFAULT_LNG) }
    var address by remember { mutableStateOf("") }
    var geocoding by remember { mutableStateOf(false) }
    var conditionExpanded by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("Post an ad", style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold))
        Text("Tell buyers what you're selling; SafeResale verifies it with an inspection check.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(18.dp))

        Text("Category", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CHECK_CATEGORIES.forEach { c ->
                FilterChip(
                    selected = category == c,
                    onClick = { category = c },
                    label = { Text(c.replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = title, onValueChange = { title = it },
            label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = price, onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("Price") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(value = brand, onValueChange = { brand = it },
                label = { Text("Brand") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = model, onValueChange = { model = it },
                label = { Text("Model") }, singleLine = true, modifier = Modifier.weight(1f))
            ExposedDropdownMenuBox(expanded = conditionExpanded, onExpandedChange = { conditionExpanded = !conditionExpanded }) {
                OutlinedTextField(value = condition, onValueChange = {}, readOnly = true, label = { Text("Condition") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(conditionExpanded) },
                    modifier = Modifier.weight(1f).menuAnchor())
                ExposedDropdownMenu(expanded = conditionExpanded, onDismissRequest = { conditionExpanded = false }) {
                    CONDITIONS.forEach { c ->
                        DropdownMenuItem(text = { Text(c) }, onClick = { condition = c; conditionExpanded = false })
                    }
                }
            }
        }
        if (category == "mobile" || category == "laptop") {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = storage, onValueChange = { storage = it },
                label = { Text("Storage (e.g. 128 GB)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = notes, onValueChange = { notes = it },
            label = { Text("Notes (accessories, history…)") }, modifier = Modifier.fillMaxWidth().height(96.dp))

        Spacer(Modifier.height(18.dp))
        Text("Pickup location", style = MaterialTheme.typography.labelLarge)
        Text("Tap the map to set the spot, or use your device location.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        OSMMapPicker(
            initialLat = lat,
            initialLng = lng,
            onLocationChange = ::onLocation,
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = address, onValueChange = { address = it },
            label = { Text(if (geocoding) "Looking up address…" else "Address") },
            singleLine = true, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(22.dp))
        Button(
            onClick = {
                val p = price.toDoubleOrNull()
                if (token == null) { msg = "Log in first"; return@Button }
                if (title.isBlank() || p == null || p <= 0) { msg = "Enter a title and a price > 0"; return@Button }
                busy = true
                msg = ""
                scope.launch {
                    try {
                        val res = ApiClient.service.createDraft(
                            DraftReq(
                                category = category, title = title.trim(), price = p,
                                brand = brand.trim().ifBlank { null },
                                model = model.trim().ifBlank { null },
                                storage = storage.trim().ifBlank { null },
                                battery_health = null,
                                condition = condition,
                                notes = notes.trim().ifBlank { null },
                                latitude = lat, longitude = lng,
                                address = address.trim().ifBlank { null },
                            ),
                            "Bearer $token",
                        )
                        val id = ((res["listing"] as? Map<*, *>)?.get("_id") as? String)
                            ?: throw RuntimeException("No listing id returned")
                        onCreated(id)
                    } catch (e: Exception) {
                        msg = "Error: ${e.message}"
                        busy = false
                    }
                }
            },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.medium,
        ) {
            Text(if (busy) "Creating draft…" else "Create listing & run check →", fontWeight = FontWeight.SemiBold)
        }
        if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        Spacer(Modifier.height(24.dp))
    }
}