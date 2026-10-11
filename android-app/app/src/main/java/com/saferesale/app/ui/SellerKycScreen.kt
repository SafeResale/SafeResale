package com.saferesale.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saferesale.app.data.ApiClient
import com.saferesale.app.data.KycReq
import com.saferesale.app.data.TokenStore
import com.saferesale.app.ui.theme.TerritoryAccent
import java.security.MessageDigest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** SHA-256 hex of [input]. ID numbers are hashed on-device — raw number never leaves the phone. */
private fun sha256Hex(input: String): String {
    val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { "%02x".format(it) }
}

enum class KycIdType(val label: String, val hint: String) {
    AADHAAR("Aadhaar Card", "12-digit Aadhaar number"),
    PAN("PAN Card", "10-character PAN (e.g. ABCDE1234F)"),
    PASSPORT("Passport", "Passport number (e.g. A1234567)"),
    VOTER_ID("Voter ID", "Voter ID / EPIC number"),
    DRIVING_LICENCE("Driving Licence", "DL number (e.g. MH0120200012345)"),
}

/**
 * Seller KYC screen — DigiLocker-style instant identity verification.
 * Gated when the user chooses to sell items on SafeResale.
 *
 * OTP Simulation rule:
 * - 123456 -> Authentication successful with verified tick & details fetched
 * - 654321 -> Couldn't verify: details mismatch error
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellerKycScreen(
    token: String?,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }

    // Form inputs
    var fullName by remember { mutableStateOf("") }
    var idType by remember { mutableStateOf(KycIdType.AADHAAR) }
    var idNumber by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }

    // UI & Validation states
    var idTypeMenuOpen by remember { mutableStateOf(false) }
    var step by remember { mutableIntStateOf(1) } // 1: Intro, 2: Form + OTP, 3: Success Fetched Details
    var verifyingOtp by remember { mutableStateOf(false) }
    var otpError by remember { mutableStateOf<String?>(null) }
    var otpSuccess by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Seller Identity Check",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            if (step == 1) "DigiLocker Verification" else if (step == 2) "Step 1 of 2: Details & OTP" else "Step 2 of 2: Verified",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Back", modifier = Modifier.size(24.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
        ) {
            when (step) {
                1 -> KycIntroStep(
                    onContinue = { step = 2 },
                    onSkip = {
                        TokenStore.setKycDone(ctx)
                        onDone()
                    },
                )
                2 -> KycFormAndOtpStep(
                    fullName = fullName,
                    onNameChange = { fullName = it },
                    idType = idType,
                    onIdTypeChange = { idType = it },
                    idTypeMenuOpen = idTypeMenuOpen,
                    onIdTypeMenuToggle = { idTypeMenuOpen = it },
                    idNumber = idNumber,
                    onIdNumberChange = { idNumber = it.filter { c -> c.isLetterOrDigit() }.uppercase() },
                    otp = otp,
                    onOtpChange = {
                        otp = it.filter { c -> c.isDigit() }.take(6)
                        otpError = null
                    },
                    verifyingOtp = verifyingOtp,
                    otpError = otpError,
                    otpSuccess = otpSuccess,
                    onVerifyOtp = {
                        verifyingOtp = true
                        otpError = null
                        scope.launch {
                            delay(1200) // Realistic verification latency
                            if (otp == "123456") {
                                otpSuccess = true
                                delay(600)
                                // Submit to backend
                                try {
                                    val idHash = sha256Hex(idNumber)
                                    val verifiedName = fullName.trim()
                                    if (token != null) {
                                        ApiClient.service.submitKyc(
                                            KycReq(
                                                full_name = verifiedName,
                                                id_type = idType.name.lowercase(),
                                                id_number_hash = idHash,
                                                address = null,
                                            ),
                                            "Bearer $token",
                                        )
                                        // Also update profile name explicitly so /auth/me reflects it immediately
                                        runCatching {
                                            ApiClient.service.updateMe(mapOf("name" to verifiedName), "Bearer $token")
                                        }
                                    }
                                    TokenStore.setKycDone(ctx, verifiedName)
                                    step = 3
                                } catch (e: Exception) {
                                    snack.showSnackbar("Verified locally, backend sync: ${e.message ?: "saved"}")
                                    TokenStore.setKycDone(ctx, fullName.trim())
                                    step = 3
                                }
                            } else if (otp == "654321") {
                                otpError = "Couldn't verify: details mismatch"
                            } else {
                                otpError = "Invalid OTP. Use test OTP 123456 or 654321."
                            }
                            verifyingOtp = false
                        }
                    },
                )
                3 -> KycSuccessProfileStep(
                    fullName = fullName,
                    idType = idType,
                    idNumber = idNumber,
                    onContinue = onDone,
                )
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Step 1: Intro / Framing
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun KycIntroStep(onContinue: () -> Unit, onSkip: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))

        Box(
            Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(TerritoryAccent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TerritoryAccent, modifier = Modifier.size(52.dp))
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "One quick step to protect\nour buyers",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "To protect our buyers from getting scammed, we verify everyone who sells on SafeResale. " +
                "Don't worry — we never store your raw ID data. Only a secure hash is kept internally for fraud detection.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))

        WhyCard(
            icon = Icons.Default.Shield,
            title = "Buyer protection",
            body = "Verifying sellers stops scammers from deceiving buyers on our platform.",
        )
        Spacer(Modifier.height(10.dp))
        WhyCard(
            icon = Icons.Default.Badge,
            title = "Verified Seller badge",
            body = "Complete verification to display the 'SafeResale Verified' badge on your listings and profile.",
        )
        Spacer(Modifier.height(10.dp))
        WhyCard(
            icon = Icons.Default.Lock,
            title = "Your data is safe",
            body = "We never store your raw ID number — only a hashed reference is used for fraud detection. It is never shared.",
        )

        Spacer(Modifier.height(32.dp))

        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Demo mode — DigiLocker simulation. Test OTP: Enter 123456 to succeed or 654321 to test details mismatch.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Verify my identity")
        }
        TextButton(onClick = onSkip, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)) {
            Text("Skip for now", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f))
        }
    }
}

@Composable
private fun WhyCard(icon: ImageVector, title: String, body: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x1A000000), RoundedCornerShape(12.dp)),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(TerritoryAccent.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = TerritoryAccent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                Spacer(Modifier.height(2.dp))
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Step 2: Form + DigiLocker OTP Verification
// ────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KycFormAndOtpStep(
    fullName: String,
    onNameChange: (String) -> Unit,
    idType: KycIdType,
    onIdTypeChange: (KycIdType) -> Unit,
    idTypeMenuOpen: Boolean,
    onIdTypeMenuToggle: (Boolean) -> Unit,
    idNumber: String,
    onIdNumberChange: (String) -> Unit,
    otp: String,
    onOtpChange: (String) -> Unit,
    verifyingOtp: Boolean,
    otpError: String?,
    otpSuccess: Boolean,
    onVerifyOtp: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(20.dp))

        SectionLabel("1. Full Legal Name")
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = fullName,
            onValueChange = onNameChange,
            label = { Text("Full Name") },
            placeholder = { Text("e.g. Rahul Sharma") },
            leadingIcon = { Icon(Icons.Default.Person, null, tint = TerritoryAccent) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(20.dp))

        SectionLabel("2. Government Document")
        Spacer(Modifier.height(6.dp))
        ExposedDropdownMenuBox(
            expanded = idTypeMenuOpen,
            onExpandedChange = { onIdTypeMenuToggle(!idTypeMenuOpen) },
        ) {
            OutlinedTextField(
                value = idType.label,
                onValueChange = {},
                readOnly = true,
                label = { Text("Document Type") },
                leadingIcon = { Icon(Icons.Default.Badge, null, tint = TerritoryAccent) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = idTypeMenuOpen) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
            ExposedDropdownMenu(
                expanded = idTypeMenuOpen,
                onDismissRequest = { onIdTypeMenuToggle(false) },
            ) {
                KycIdType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type.label) },
                        onClick = { onIdTypeChange(type); onIdTypeMenuToggle(false) },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = idNumber,
            onValueChange = onIdNumberChange,
            label = { Text("${idType.label} Number") },
            placeholder = { Text(idType.hint) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )

        Spacer(Modifier.height(20.dp))

        // OTP section
        SectionLabel("3. DigiLocker Authentication (OTP)")
        Spacer(Modifier.height(6.dp))
        Text(
            "Enter OTP sent to mobile linked with your government ID.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "Demo hint: Use 123456 to succeed, or 654321 for details mismatch.",
            style = MaterialTheme.typography.labelSmall,
            color = TerritoryAccent,
            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
        )

        OutlinedTextField(
            value = otp,
            onValueChange = onOtpChange,
            label = { Text("6-Digit OTP") },
            placeholder = { Text("123456") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                if (otpSuccess) {
                    Icon(Icons.Default.CheckCircle, contentDescription = "Verified", tint = Color(0xFF00C853))
                }
            },
        )

        // Error message if details mismatch or invalid
        if (otpError != null) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        otpError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Success badge with tick
        if (otpSuccess) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = Color(0xFF00C853).copy(alpha = 0.12f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00C853), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Authentication successful ✓",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF00A844),
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        val isFormFilled = fullName.trim().length >= 2 && idNumber.length >= 6 && otp.length == 6
        Button(
            onClick = onVerifyOtp,
            enabled = isFormFilled && !verifyingOtp,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (verifyingOtp) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Authenticating via DigiLocker…")
            } else {
                Text("Verify & Authenticate")
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Step 3: Success with Demo Profile Pic & Fetched Details
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun KycSuccessProfileStep(
    fullName: String,
    idType: KycIdType,
    idNumber: String,
    onContinue: () -> Unit,
) {
    val maskedId = if (idNumber.length > 4) {
        "X".repeat(idNumber.length - 4) + idNumber.takeLast(4)
    } else {
        idNumber
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Spacer(Modifier.height(20.dp))

        // Large Tick + Avatar container
        Box(contentAlignment = Alignment.BottomEnd) {
            // Profile Pic container (Demo avatar generated from initials)
            Box(
                Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(TerritoryAccent.copy(alpha = 0.15f))
                    .border(2.dp, TerritoryAccent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Profile Avatar",
                    tint = TerritoryAccent,
                    modifier = Modifier.size(60.dp),
                )
            }
            // Verified Tick Chip
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00C853)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = "Verified", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            "Authentication Successful!",
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(4.dp))

        // The Full Name as the verified profile name with badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                fullName.ifBlank { "Verified Seller" },
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.width(6.dp))
            Surface(
                color = TerritoryAccent,
                shape = RoundedCornerShape(6.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Verified, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "VERIFIED",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = Color.White,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "DigiLocker has successfully authenticated your credentials. Your verified name is now set across your profile and listings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(20.dp))

        // Details summary card
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0x1F000000), RoundedCornerShape(12.dp)),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailRow(label = "Verified Name", value = fullName)
                DetailRow(label = "Document", value = idType.label)
                DetailRow(label = "Doc Number", value = maskedId)
                DetailRow(label = "Authentication Provider", value = "DigiLocker Verified ✓")
                DetailRow(label = "Seller Status", value = "Verified Seller (Eligible to List)")
            }
        }

        Spacer(Modifier.height(28.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Proceed to Create Listing →")
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        ),
        color = TerritoryAccent,
    )
}
