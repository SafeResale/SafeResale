package com.saferesale.app.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.saferesale.app.R
import com.saferesale.app.data.ApiClient
import com.saferesale.app.data.FirebaseIdTokenReq
import com.saferesale.app.data.LoginReq
import com.saferesale.app.data.TokenStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * SafeResale login (eclassify-style).
 * Phone-first: Firebase phone OTP (no password needed), with Google as the
 * secondary option. In both cases the client performs the Firebase Auth and
 * then sends ONLY the Firebase ID token to our backend, which exchanges it
 * for SafeResale access/refresh tokens.
 */
@Composable
fun AuthScreen(onAuthed: (String) -> Unit) {
    val ctx = LocalContext.current
    val activity = ctx as? Activity
    val auth = remember { FirebaseAuth.getInstance() }
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf("phone") } // "phone" | "otp" | "email"
    var countryCode by remember { mutableStateOf("+91") }
    var phone by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var verificationId by remember { mutableStateOf<String?>(null) }
    var resendToken by remember { mutableStateOf<PhoneAuthProvider.ForceResendingToken?>(null) }
    var resendIn by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var devHint by remember { mutableStateOf("") }

    fun normalized() = "${countryCode.replace(" ", "")}${phone.filter { it.isDigit() }}"

    fun signInWithCredential(credential: PhoneAuthCredential) {
        if (activity == null) { error = "Invalid activity context"; return }
        loading = true
        error = ""
        scope.launch {
            try {
                val user = auth.signInWithCredential(credential).await().user
                    ?: throw IllegalStateException("Sign-in returned no user")
                val idToken = user.getIdToken(false).await().token ?: throw IllegalStateException("No Firebase ID token")
                val res = ApiClient.service.firebaseLogin(FirebaseIdTokenReq(idToken))
                TokenStore.save(ctx, res.access_token, res.refresh_token)
                onAuthed(res.access_token)
            } catch (e: FirebaseAuthInvalidCredentialsException) {
                error = "The code is incorrect or expired — try again."
                loading = false
            } catch (e: Exception) {
                error = e.message ?: "Sign-in failed"
                loading = false
            }
        }
    }

    val callbacks = remember {
        object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                signInWithCredential(credential)
            }

            override fun onVerificationFailed(e: FirebaseException) {
                loading = false
                error = e.message ?: "Unable to send the code"
            }

            override fun onCodeSent(
                vid: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                verificationId = vid
                resendToken = token
                loading = false
                error = ""
                step = "otp"
                resendIn = 30
            }
        }
    }

    fun sendOtp(forceResend: Boolean = false) {
        activity ?: run { error = "Invalid activity context"; return }
        val fullPhone = normalized()
        if (fullPhone.length < 10) { error = "Enter a valid phone number"; return }
        loading = true
        error = ""
        try {
            val builder = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(fullPhone)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
            if (forceResend) resendToken?.let { builder.setForceResendingToken(it) }
            PhoneAuthProvider.verifyPhoneNumber(builder.build())
            devHint = "OTP goes to $fullPhone (Firebase SMS or instant verification)."
        } catch (e: Exception) {
            loading = false
            error = "Phone sign-in unavailable: ${e.message}"
        }
    }

    // Email + password login (used by admin/operator accounts).
    fun signInWithEmail() {
        if (email.isBlank() || password.isBlank()) { error = "Enter your email and password"; return }
        loading = true
        error = ""
        scope.launch {
            try {
                val res = ApiClient.service.login(LoginReq(email.trim(), password))
                TokenStore.save(ctx, res.access_token, res.refresh_token)
                onAuthed(res.access_token)
            } catch (e: Exception) {
                error = e.message ?: "Invalid email or password"
                loading = false
            }
        }
    }

    // Resend countdown
    LaunchedEffect(step, resendIn) {
        if (step == "otp" && resendIn > 0) {
            delay(1000)
            resendIn -= 1
        }
    }

    // Google sign-in (play-services-auth -> Firebase credential -> backend)
    val googleClient = remember {
        val webClientId = try { ctx.getString(R.string.default_web_client_id) } catch (_: Exception) { "" }
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .apply { if (webClientId.isNotBlank()) requestIdToken(webClientId) }
            .requestEmail()
            .requestProfile()
            .build()
            .let { GoogleSignIn.getClient(ctx, it) }
    }
    val googleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken ?: throw IllegalStateException("No Google ID token")
            loading = true
            error = ""
            scope.launch {
                try {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    val user = auth.signInWithCredential(credential).await().user
                        ?: throw IllegalStateException("Sign-in returned no user")
                    val fbToken = user.getIdToken(false).await().token
                        ?: throw IllegalStateException("No Firebase ID token")
                    val res = ApiClient.service.firebaseLogin(FirebaseIdTokenReq(fbToken))
                    TokenStore.save(ctx, res.access_token, res.refresh_token)
                    onAuthed(res.access_token)
                } catch (e: Exception) {
                    error = e.message ?: "Google sign-in failed"
                    loading = false
                }
            }
        } catch (e: ApiException) {
            error = if (e.statusCode == 10) {
                "Google sign-in needs a web client ID (default_web_client_id)."
            } else {
                e.statusMessage ?: "Google sign-in cancelled"
            }
        } catch (e: Exception) {
            error = e.message ?: "Google sign-in failed"
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(64.dp))
            // Brand header (glass tile like the reference app)
            Image(
                painter = painterResource(R.mipmap.ic_launcher_round),
                contentDescription = "SafeResale",
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
            )
            Spacer(Modifier.height(20.dp))
            Text("SafeResale", style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold))
            Text(
                "Trusted pre-owned devices",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(40.dp))

            if (step == "phone") {
                Text(
                    "Insert your phone number to continue",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.Start)
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CountryCodeBox(countryCode, onSelect = { countryCode = it })
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it.filter { c -> c.isDigit() }.take(10) },
                        label = { Text("Phone number") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { sendOtp() },
                    enabled = !loading && phone.replace(" ", "").length >= 10,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Continue")
                }
            } else if (step == "email") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { step = "phone"; error = ""; password = "" }) { Text("← Phone login") }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "Admin & support accounts",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    )
                }
                Text(
                    "Sign in with email",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp)
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { signInWithEmail() },
                    enabled = !loading && email.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Login")
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { step = "phone"; error = ""; otp = "" }) { Text("← Edit number") }
                    Spacer(Modifier.weight(1f))
                    Text(normalized(), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                }
                Text(
                    "Verify your code",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 12.dp)
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = otp,
                    onValueChange = { otp = it.take(6).filter { c -> c.isDigit() } },
                    label = { Text("6-digit code") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { signInWithCredential(PhoneAuthProvider.getCredential(verificationId!!, otp)) },
                    enabled = !loading && otp.length == 6,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp) else Text("Verify & continue")
                }
                if (resendIn > 0) {
                    Text(
                        "Resend code in $resendIn s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                } else {
                    TextButton(onClick = { sendOtp(forceResend = true) }, modifier = Modifier.padding(top = 4.dp)) {
                        Text("Resend code")
                    }
                }
            }

            if (devHint.isNotEmpty()) {
                Text(devHint, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 12.dp))
            }
            if (error.isNotEmpty()) {
                Text(error, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 12.dp))
            }

            // Divider + Google
            Row(Modifier.fillMaxWidth().padding(vertical = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                Text("  or  ", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            }
            GoogleButton(onClick = { googleLauncher.launch(googleClient.signInIntent) })
            TextButton(
                onClick = { step = "email"; error = ""; otp = "" },
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text("Sign in with email & password", color = MaterialTheme.colorScheme.primary)
            }

            Spacer(Modifier.height(28.dp))
            Text(
                "By continuing you agree to the SafeResale terms.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Text(
                "Backend: ${ApiClient.baseUrl} — tokens via /auth/firebase",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
            )
        }
    }
}

@Composable
private fun CountryCodeBox(code: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 18.dp)
        ) {
            Text(code, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("+91", "+1", "+44", "+971", "+966", "+61", "+971").distinct().forEach { c ->
                DropdownMenuItem(text = { Text(c) }, onClick = { onSelect(c); expanded = false })
            }
        }
    }
}

@Composable
private fun GoogleButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text("G", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
        }
        Spacer(Modifier.width(10.dp))
        Text("Continue with Google", style = MaterialTheme.typography.bodyLarge)
    }
}