package com.saferesale.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.saferesale.app.ui.AuthScreen
import com.saferesale.app.ui.CaptureScreen
import com.saferesale.app.ui.DiagnosticsScreen
import com.saferesale.app.ui.HomeScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                val nav = rememberNavController()
                var token by remember { mutableStateOf<String?>(null) }
                NavHost(navController = nav, startDestination = "auth") {
                    composable("auth") {
                        AuthScreen(onAuthed = { t ->
                            token = t
                            nav.navigate("home") { popUpTo("auth") { inclusive = true } }
                        })
                    }
                    composable("home") {
                        HomeScreen(
                            onCapture = { nav.navigate("capture") },
                            token = token
                        )
                    }
                    composable("capture") {
                        CaptureScreen(token = token, onDone = { nav.navigate("diagnostics") })
                    }
                    composable("diagnostics") {
                        DiagnosticsScreen(token = token, listingId = null, onDone = { nav.navigate("home") { popUpTo("home") { inclusive = false } } })
                    }
                }
            }
        }
    }
}
