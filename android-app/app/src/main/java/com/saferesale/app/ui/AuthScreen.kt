package com.saferesale.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.saferesale.app.data.ApiClient
import com.saferesale.app.data.LoginReq
import com.saferesale.app.data.TokenStore
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(onAuthed: (String) -> Unit) {
    var email by remember { mutableStateOf("admin@example.com") }
    var pass by remember { mutableStateOf("Admin123!") }
    var msg by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("SafeResale — Login", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            scope.launch {
                try {
                    val res = ApiClient.service.login(LoginReq(email, pass))
                    TokenStore.save(ctx, res.access_token, res.refresh_token)
                    msg = "Logged in as ${res.user["email"]}"
                    onAuthed(res.access_token)
                } catch (e: Exception) { msg = "Error: ${e.message}" }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("Login") }
        if (msg.isNotEmpty()) Text(msg, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        Text("Backend: via 10.0.2.2:8000 (emulator) — see 10-setup-guide.md", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 24.dp))
    }
}
