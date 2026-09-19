import java.net.NetworkInterface

/** Best-effort IPv4 LAN address so a real device can reach the dev backend.
 * Prefers the Wi-Fi adapter and 192.168.x addresses; skips virtual NICs
 * (WSL/Hyper-V/Docker) and loopback. Override anytime with -PapiBaseUrl=. */
fun lanIpv4(): String? {
    val ordinary = mutableListOf<String>()
    val wifi = mutableListOf<String>()
    try {
        for (ni in NetworkInterface.getNetworkInterfaces()) {
            val name = (ni.displayName ?: ni.name).lowercase()
            if (ni.isLoopback || !ni.isUp) continue
            if ("vethernet" in name || "hyper-v" in name || "wsl" in name ||
                "virtual" in name || "vmware" in name || "docker" in name ||
                "default switch" in name
            ) continue
            for (addr in ni.inetAddresses) {
                val ip = addr.hostAddress ?: continue
                if (':' in ip) continue
                if (!ip.startsWith("192.168.") && !ip.startsWith("10.") && !ip.startsWith("172.")) continue
                val bucket = if ("wi-fi" in name || "wlan" in name || "wireless" in name) wifi else ordinary
                bucket.add(ip)
            }
        }
    } catch (e: Exception) {
    }
    val all = wifi + ordinary
    return all.firstOrNull { it.startsWith("192.168.") }
        ?: all.firstOrNull { it.startsWith("10.") }
        ?: all.firstOrNull { it.startsWith("172.") }
}

val apiBaseOverride = (findProperty("apiBaseUrl") as String?)
val defaultBaseUrl = apiBaseOverride ?: "http://${lanIpv4() ?: "10.0.2.2"}:8000"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.saferesale.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.saferesale.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        buildConfigField("String", "API_BASE_URL", "\"$defaultBaseUrl\"")
        buildConfigField("String", "EMULATOR_API_BASE_URL", "\"http://10.0.2.2:8000\"")
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isDebuggable = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
        )
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/native-image/**"
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.datastore.preferences)

    // Compose BOM
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // Permissions
    implementation(libs.accompanist.permissions)

    // Networking (SafeResale backend + CoreV tools)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // PDF export
    implementation(libs.itext7.core)

    // Charts
    implementation(libs.vico.compose)
    implementation(libs.vico.compose.m3)
    implementation(libs.vico.core)

    // Image loading
    implementation(libs.coil.compose)

    // SafeResale extras: encrypted token store + CameraX (seller capture flow)
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    // Firebase Auth (Google + phone OTP; client sends only the ID token to the backend)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.play.services.auth)
    implementation(libs.play.services.location)

    // OpenStreetMap (osmdroid, no API key needed)
    implementation(libs.osmdroid)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
