package com.saferesale.app.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay

/**
 * OpenStreetMap location picker (osmdroid, no API key).
 * Tap the map to drop a pin; the position is reported via onLocationChange.
 * A "use my location" button snaps the pin to the device GPS location.
 * Auto-requests FINE_LOCATION permission on first composition if not granted.
 */
@Composable
fun OSMMapPicker(
    initialLat: Double,
    initialLng: Double,
    onLocationChange: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
) {
    val ctx = LocalContext.current
    val myLocation = remember { mutableStateOf<GeoPoint?>(null) }
    val markerRef = remember { arrayOfNulls<Marker>(1) }
    val hasPermission = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(ctx) }

    fun fetchMyLocation() {
        if (!hasPermission.value) return
        @SuppressLint("MissingPermission")
        val task = fusedClient.getCurrentLocation(
            Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            CancellationTokenSource().token,
        )
        task.addOnSuccessListener { loc ->
            if (loc != null) myLocation.value = GeoPoint(loc.latitude, loc.longitude)
        }
        // Fallback: try LocationManager if FusedLocation fails (no Google Play Services)
        task.addOnFailureListener {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@addOnFailureListener
            val loc = listOfNotNull(
                runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull(),
                runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull(),
            ).maxByOrNull { it.time } ?: return@addOnFailureListener
            myLocation.value = GeoPoint(loc.latitude, loc.longitude)
        }
    }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission.value = granted
        if (granted) fetchMyLocation()
    }

    // Auto-request permission when the map loads (non-read-only mode only)
    LaunchedEffect(Unit) {
        if (!readOnly && !hasPermission.value) {
            permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    fun placePin(map: MapView, geo: GeoPoint) {
        markerRef[0]?.let { map.overlays.remove(it) }
        val icon = try {
            BitmapDrawable(ctx.resources, BitmapFactory.decodeResource(ctx.resources, android.R.drawable.ic_menu_mylocation))
        } catch (_: Exception) {
            null
        }
        val m = Marker(map).apply {
            position = geo
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            icon?.let { this.icon = it }
            title = "Selected location"
        }
        map.overlays.add(m)
        markerRef[0] = m
        map.invalidate()
    }

    Box(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)),
            factory = { context ->
                val map = MapView(context).apply {
                    Configuration.getInstance().userAgentValue = context.packageName
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(13.0)
                }
                val start = GeoPoint(initialLat, initialLng)
                map.controller.setCenter(start)
                placePin(map, start)
                if (!readOnly) {
                    onLocationChange(initialLat, initialLng)
                    val tap = object : Overlay() {
                        override fun onSingleTapConfirmed(e: android.view.MotionEvent?, mapView: MapView?): Boolean {
                            if (e == null || mapView == null) return false
                            val g = try {
                                mapView.projection.fromPixels(e.x.toInt(), e.y.toInt()) as? GeoPoint
                            } catch (_: Exception) {
                                null
                            } ?: return false
                            placePin(mapView, g)
                            mapView.controller.setCenter(g)
                            onLocationChange(g.latitude, g.longitude)
                            return true
                        }
                    }
                    map.overlays.add(tap)
                }
                map
            },
            update = { map ->
                myLocation.value?.let { p ->
                    placePin(map, p)
                    map.controller.setCenter(p)
                    onLocationChange(p.latitude, p.longitude)
                }
            }
        )
        if (!readOnly) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clickable {
                        if (hasPermission.value) {
                            fetchMyLocation()
                        } else {
                            permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    },
                shape = RoundedCornerShape(10.dp),
                tonalElevation = 3.dp,
                shadowElevation = 2.dp,
                color = Color.White,
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("My location", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
