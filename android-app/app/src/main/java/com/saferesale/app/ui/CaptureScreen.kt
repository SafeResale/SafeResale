package com.saferesale.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.saferesale.app.data.ApiClient
import com.saferesale.app.data.ConfirmReq
import com.saferesale.app.data.UploadTokenReq
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val ANGLES = listOf("front", "back", "left", "right", "top", "bottom", "front_45", "back_45")

/** On-device blur gate: variance of Laplacian on a downscaled grayscale
 *  thumbnail. Higher = sharper. Threshold ~100 rejects motion-blur/dark frames
 *  before any bytes leave the device (R-CAPTURE-03). */
fun laplacianVariance(jpeg: ByteArray): Double {
    val opts = BitmapFactory.Options().apply { inSampleSize = 8 }
    val bmp = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, opts) ?: return 0.0
    val w = bmp.width
    val h = bmp.height
    if (w < 3 || h < 3) return 0.0
    val px = IntArray(w * h)
    bmp.getPixels(px, 0, w, 0, 0, w, h)
    val gray = DoubleArray(w * h) { i ->
        val c = px[i]
        (0.299 * ((c shr 16) and 0xFF) + 0.587 * ((c shr 8) and 0xFF) + 0.114 * (c and 0xFF))
    }
    var sum = 0.0
    var sumSq = 0.0
    var n = 0
    for (y in 1 until h - 1) for (x in 1 until w - 1) {
        val lap = -4 * gray[y * w + x] + gray[y * w + x - 1] + gray[y * w + x + 1] +
            gray[(y - 1) * w + x] + gray[(y + 1) * w + x]
        sum += lap
        sumSq += lap * lap
        n++
    }
    if (n == 0) return 0.0
    val mean = sum / n
    return sumSq / n - mean * mean
}

const val BLUR_THRESHOLD = 100.0

data class AngleState(val angle: String, val status: String, val detail: String = "")
// status: pending | captured | uploaded | failed

@Composable
fun CaptureScreen(token: String?, listingId: String?, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var idx by remember { mutableStateOf(0) }
    var states by remember { mutableStateOf(ANGLES.map { AngleState(it, "pending") }) }
    var busy by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var hasCamPerm by remember {
        mutableStateOf(ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCamPerm = ctx.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    LaunchedEffect(Unit) { if (!hasCamPerm) permLauncher.launch(Manifest.permission.CAMERA) }

    fun setState(angle: String, status: String, detail: String = "") {
        states = states.map { if (it.angle == angle) it.copy(status = status, detail = detail) else it }
    }

    suspend fun uploadCurrent(bytes: ByteArray, angle: String, mime: String, filename: String) {
        val auth = "Bearer $token"
        val lid = listingId ?: throw RuntimeException("No listing — restart the check from Home")
        // 1) token
        val tok = ApiClient.service.uploadToken(
            lid, UploadTokenReq(angle, filename, mime, bytes.size), auth)
        val uploadUrl = tok["upload_url"] as? String ?: throw RuntimeException("No upload_url")
        val key = tok["key"] as? String ?: throw RuntimeException("No storage key")
        val upToken = tok["upload_token"] as? String ?: throw RuntimeException("No upload token")
        // 2) bytes (authorized: key is bound to our upload token)
        ApiClient.putBytes(uploadUrl, bytes, mime, auth)
        // 3) confirm (server re-hashes + runs OpenCV quality)
        val blur = withContext(Dispatchers.Default) { laplacianVariance(bytes) }
        ApiClient.service.confirmUpload(lid, ConfirmReq(
            upload_token = upToken,
            stored_key = key,
            angle = angle,
            quality = mapOf("blur" to blur, "passed" to (blur >= BLUR_THRESHOLD), "method" to "laplacian-variance")
        ), auth)
        setState(angle, "uploaded", "blur ${blur.toInt()}")
    }

    /** Shared gate: blur-check then upload. Used by camera capture and gallery picks. */
    suspend fun processBytes(angle: String, bytes: ByteArray, mime: String, filename: String) {
        val blur = withContext(Dispatchers.Default) { laplacianVariance(bytes) }
        if (blur < BLUR_THRESHOLD) {
            setState(angle, "captured", "too blurry (${blur.toInt()} < ${BLUR_THRESHOLD.toInt()}) — retake or pick another")
            msg = "$angle too blurry (score ${blur.toInt()}). Hold steady and retake, or pick another photo."
        } else {
            msg = "Uploading $angle…"
            uploadCurrent(bytes, angle, mime, filename)
            msg = "$angle uploaded."
            if (idx < 7) idx++ else msg = "All 8 angles uploaded — press Done."
        }
    }

    // Manual upload: pick a photo from the gallery for the current angle.
    // Same on-device blur gate + token → PUT → confirm pipeline as the camera.
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val angle = ANGLES[idx]
        busy = true
        scope.launch {
            try {
                val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
                if (mime !in setOf("image/jpeg", "image/png", "image/webp")) {
                    msg = "Only jpeg/png/webp supported (got $mime)"
                    return@launch
                }
                val bytes = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw RuntimeException("Could not read picked image")
                }
                if (bytes.size > 10 * 1024 * 1024) {
                    msg = "Image too large (${bytes.size / 1024} KB, max 10 MB)"
                    return@launch
                }
                val name = (uri.lastPathSegment ?: "gallery.jpg").substringAfterLast('/').takeLast(60)
                processBytes(angle, bytes, mime, name)
            } catch (e: Exception) {
                setState(angle, "failed", e.message ?: "error")
                msg = "$angle failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun captureAndUpload() {
        val cap = imageCapture ?: run { msg = "Camera not ready yet"; return }
        if (token == null || listingId == null) { msg = "Login + listing required"; return }
        val angle = ANGLES[idx]
        busy = true
        msg = "Capturing $angle…"
        val file = File(ctx.cacheDir, "cap_${angle}_${System.currentTimeMillis()}.jpg")
        cap.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(out: ImageCapture.OutputFileResults) {
                    scope.launch {
                        try {
                            val bytes = withContext(Dispatchers.IO) { file.readBytes() }
                            processBytes(angle, bytes, "image/jpeg", "$angle.jpg")
                        } catch (e: Exception) {
                            setState(angle, "failed", e.message ?: "error")
                            msg = "$angle failed: ${e.message}"
                        } finally {
                            busy = false
                        }
                    }
                }
                override fun onError(e: ImageCaptureException) {
                    busy = false
                    msg = "Capture failed: ${e.message}"
                }
            }
        )
    }

    val uploaded = states.count { it.status == "uploaded" }
    Column(Modifier.fillMaxSize()) {
        Text("Capture ${idx + 1}/8 — ${ANGLES[idx]}", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.titleLarge)
        Text("Uploaded $uploaded/8", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodyMedium)
        if (!hasCamPerm) {
            Text("Camera permission is required for capture.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Grant camera") }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(factory = { c ->
                PreviewView(c).also { pv ->
                    val fut = ProcessCameraProvider.getInstance(c)
                    fut.addListener({
                        val provider = fut.get()
                        val preview = Preview.Builder().build().also { it.setSurfaceProvider(pv.surfaceProvider) }
                        val cap = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
                        imageCapture = cap
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, cap)
                    }, ContextCompat.getMainExecutor(c))
                }
            }, modifier = Modifier.fillMaxSize())
            Card(Modifier.padding(16.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Text("Guide: ${ANGLES[idx]} — fill the frame, avoid glare")
                    Text("Blur gate ≥ ${BLUR_THRESHOLD.toInt()} runs on-device before upload (R-CAPTURE-03).", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (msg.isNotEmpty()) Text(msg, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.bodyMedium)
        LazyColumn(Modifier.height(120.dp).padding(horizontal = 16.dp)) {
            items(states) { s ->
                val color = when (s.status) {
                    "uploaded" -> MaterialTheme.colorScheme.primary
                    "failed" -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                ListItem(
                    headlineContent = { Text(s.angle) },
                    supportingContent = { s.detail.takeIf { it.isNotEmpty() }?.let { Text(it) } },
                    trailingContent = { Badge(containerColor = color) { Text(s.status) } }
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = { if (idx > 0) idx-- }) { Text("Back") }
            Button(onClick = { captureAndUpload() }, enabled = !busy && hasCamPerm) {
                Text(if (busy) "Working…" else "Capture")
            }
            Button(onClick = { galleryLauncher.launch("image/*") }, enabled = !busy) { Text("Gallery") }
            Button(onClick = onDone, enabled = uploaded >= 8) { Text("Done") }
        }
    }
}
