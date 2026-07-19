package com.tabslify.tabs.exploretab

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import com.tabslify.R
import com.tabslify.core.objects.Config
import com.tabslify.core.ui.AlertDialogTabslify
import com.tabslify.core.ui.NeonBox
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import java.util.concurrent.TimeUnit
import kotlin.math.floor

@Composable
fun ExploreTabContent(setGesturesEnabled: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val vm: ExploreViewModel = viewModel()

    val tileCount by vm.tileCount.collectAsStateWithLifecycle()
    val exploredPercent by vm.exploredPercent.collectAsStateWithLifecycle()
    val tiles by vm.allTiles.collectAsStateWithLifecycle()
    val trackerStatus by ExploreLocationTracker.trackerStatus.collectAsStateWithLifecycle()
    val trackerInfo by ExploreLocationTracker.trackerInfo.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var tileToDelete by remember { mutableStateOf<ExploredTile?>(null) }
    var initialCenterDone by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            ExploreLocationTracker.start(ctx)
            if (!initialCenterDone) {
                LocationServices.getFusedLocationProviderClient(ctx).lastLocation
                    .addOnSuccessListener { loc ->
                        if (loc != null) {
                            mapView?.controller?.animateTo(GeoPoint(loc.latitude, loc.longitude))
                            initialCenterDone = true
                        }
                    }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                Lifecycle.Event.ON_RESUME -> {
                    mapView?.onResume()
                    if (ContextCompat.checkSelfPermission(
                            ctx,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        )
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        ExploreLocationTracker.start(ctx)
                    }
                }

                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onDetach()
        }
    }

    LaunchedEffect(Unit) {
        setGesturesEnabled(false)
        try {
            LocationServices.getFusedLocationProviderClient(ctx).lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) mapView?.controller?.animateTo(
                        GeoPoint(loc.latitude, loc.longitude)
                    )
                }
        } catch (_: Exception) {
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Stats Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    label = stringResource(R.string.tiles),
                    value = tileCount.toString(),
                    modifier = Modifier.weight(1f)
                )
                StatCard(stringResource(R.string.erkundet), "%.8f%%".format(exploredPercent), Modifier.weight(1f))
                StatCard(stringResource(R.string.heute_2), vm.todayCount.toString(), Modifier.weight(1f))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StatCard(
                    label = stringResource(R.string.tracker_status),
                    value = trackerStatus,
                    modifier = Modifier.weight(1f)
                )
            }

            TrackerDebugInfo(
                info = trackerInfo,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clipToBounds()
            ) {
                AndroidView(
                    factory = { context ->
                        val loc = if (ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            try {
                                Tasks.await(
                                    LocationServices.getFusedLocationProviderClient(context).lastLocation,
                                    1, TimeUnit.SECONDS
                                )
                            } catch (_: Exception) {
                                null
                            }
                        } else null
                        val center = if (loc != null) GeoPoint(loc.latitude, loc.longitude)
                        else GeoPoint(Config.LAT, Config.LON)

                        Configuration.getInstance().userAgentValue = context.packageName
                        MapView(context).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            controller.setZoom(15.0)
                            controller.setCenter(center)
                            isTilesScaledToDpi = false
                            setScrollableAreaLimitDouble(null)
                        }.also { mapView = it }
                    },
                    update = { mv ->
                        mv.overlays.removeAll(
                            mv.overlays.filterIsInstance<ExploreOverlay>().toSet()
                        )
                        if (tiles.isNotEmpty()) {
                            mv.overlays.add(
                                0,
                                ExploreOverlay(tiles) { tile -> tileToDelete = tile })
                            mv.invalidate()
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                )
            }
        }
        tileToDelete?.let { tile ->
            AlertDialogTabslify(
                onConfirm = {
                    scope.launch { vm.deleteTile(tile.tileX, tile.tileY) }
                    tileToDelete = null
                },
                onDismiss = { tileToDelete = null },
                icon = { Text("🗺️", fontSize = 24.sp) },
                title = stringResource(R.string.tile_loschen),
                text = stringResource(R.string.diesen_bereich_als_unbesucht_markieren)
            )
        }
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val cardModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    NeonBox(
        modifier = cardModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 2.dp),
        borderWidth = 3.dp,
        neonColors = listOf(Color(0xFF00FFAA), Color(0xFF00CCFF)),
        backgroundAlpha = 0.25f,        // etwas transparenter, damit es edel aussieht

    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 8.dp, horizontal = 10.dp),   // Innenabstand angepasst
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = Color(0xFF9090A0),
                fontSize = 11.sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TrackerDebugInfo(info: ExploreTrackerInfo, modifier: Modifier = Modifier) {
    NeonBox(
        modifier = modifier,
        borderWidth = 2.dp,
        neonColors = listOf(Color(0xFF00FFAA), Color(0xFF00CCFF)),
        backgroundAlpha = 0.15f,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp)
        ) {
            DebugLine(stringResource(R.string.zuhause), "%.6f, %.6f".format(info.homeLat, info.homeLng))
            DebugLine(
                stringResource(R.string.letzte_position),
                if (info.lastLat != null && info.lastLng != null)
                    "%.6f, %.6f".format(info.lastLat, info.lastLng)
                else "—"
            )
            DebugLine(
                stringResource(R.string.distanz_zuhause),
                info.distanceToHomeMeters?.let { "%.0f m".format(it) } ?: "—"
            )
            DebugLine(stringResource(R.string.letztes_update), info.lastUpdate ?: "—")
            DebugLine(
                stringResource(R.string.geofence),
                if (info.geofenceRegistered) stringResource(R.string.aktiv)
                else info.geofenceError?.let { stringResource(R.string.fehler_msg, it) }
                    ?: stringResource(R.string.nicht_registriert)
            )
            DebugLine(
                stringResource(R.string.heim_wlan),
                info.lastWifiHome?.let {
                    if (it) stringResource(R.string.verbunden) else stringResource(R.string.nicht_verbunden)
                } ?: "—"
            )
            DebugLine(stringResource(R.string.nachtmodus), if (info.isNight) stringResource(R.string.ja) else stringResource(R.string.nein))
            DebugLine(stringResource(R.string.enabled), if (info.isEnabled) stringResource(R.string.ja) else stringResource(R.string.nein))
        }
    }
}

@Composable
private fun DebugLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = Color(0xFF9090A0), fontSize = 11.sp)
        Text(text = value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

private class ExploreOverlay(
    private val tiles: List<ExploredTile>,
    private val onLongPress: (ExploredTile) -> Unit
) : Overlay() {
    private val fillPaint = Paint().apply {
        color = android.graphics.Color.argb(100, 100, 149, 237)  // Etwas sichtbarer
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint().apply {
        color = android.graphics.Color.argb(180, 70, 120, 220)  // Stärkere Umrandung
        style = Paint.Style.STROKE
        strokeWidth = 2f  // Dickere Linien für größere Tiles
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        val bbox = mapView.boundingBox

        val minX = floor(bbox.latSouth / TILE_SIZE).toLong() - 1
        val maxX = floor(bbox.latNorth / TILE_SIZE).toLong() + 1
        val minY = floor(bbox.lonWest / TILE_SIZE).toLong() - 1
        val maxY = floor(bbox.lonEast / TILE_SIZE).toLong() + 1

        tiles.asSequence()
            .filter { it.tileX in minX..maxX && it.tileY in minY..maxY }
            .take(5000)
            .forEach { tile ->
                val lat0 = tile.tileX * TILE_SIZE
                val lon0 = tile.tileY * TILE_SIZE
                val p0 = projection.toPixels(GeoPoint(lat0, lon0), null)
                val p1 = projection.toPixels(GeoPoint(lat0 + TILE_SIZE, lon0 + TILE_SIZE), null)

                val left = minOf(p0.x, p1.x).toFloat()
                val top = minOf(p0.y, p1.y).toFloat()
                val right = maxOf(p0.x, p1.x).toFloat()
                val bottom = maxOf(p0.y, p1.y).toFloat()

                canvas.drawRect(left, top, right, bottom, fillPaint)
                canvas.drawRect(left, top, right, bottom, strokePaint)
            }
    }

    override fun onLongPress(e: MotionEvent, mapView: MapView): Boolean {
        val projection = mapView.projection
        val geo = projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
        val tx = floor(geo.latitude / TILE_SIZE).toLong()
        val ty = floor(geo.longitude / TILE_SIZE).toLong()
        val hit = tiles.find { it.tileX == tx && it.tileY == ty }
        if (hit != null) {
            onLongPress(hit)
            return true
        }
        return false
    }
}