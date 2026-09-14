package com.saferesale.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saferesale.app.data.ApiClient
import com.saferesale.app.data.DraftReq
import kotlinx.coroutines.launch

val CHECK_CATEGORIES = listOf("mobile", "laptop", "tablet")

@Composable
fun NewCheckScreen(token: String?, onCreated: (String) -> Unit) {
    var title by remember { mutableStateOf("Demo phone") }
    var price by remember { mutableStateOf("45000") }
    var category by remember { mutableStateOf("mobile") }
    var expanded by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("New device check", style = MaterialTheme.typography.headlineMedium)
        Text("Creates a draft listing that will carry your photos, diagnostics and score.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = price, onValueChange = { price = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Price") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(value = category, onValueChange = {}, readOnly = true, label = { Text("Category") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor())
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                CHECK_CATEGORIES.forEach {
                    DropdownMenuItem(text = { Text(it) }, onClick = { category = it; expanded = false })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            val p = price.toDoubleOrNull()
            if (token == null) { msg = "Login first"; return@Button }
            if (title.isBlank() || p == null || p <= 0) { msg = "Enter a title and a price > 0"; return@Button }
            busy = true
            scope.launch {
                try {
                    val res = ApiClient.service.createDraft(DraftReq(category, title.trim(), p), "Bearer $token")
                    val id = ((res["listing"] as? Map<*, *>)?.get("_id") as? String)
                        ?: throw RuntimeException("No listing id returned")
                    onCreated(id)
                } catch (e: Exception) { msg = "Error: ${e.message}"; busy = false }
            }
        }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Creating…" else "Create draft & continue →")
        }
        if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
    }
}
