package com.saferesale.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.saferesale.app.data.ApiClient
import com.saferesale.app.ui.screens.diagnose.DiagnosisPairScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApiClient.init(applicationContext)
        val token = intent?.getStringExtra("pairing_token").orEmpty()
        setContent {
            DiagnosisPairScreen(
                initialToken = token,
                onBack = { finish() },
                onComplete = { finish() }
            )
        }
    }
}
