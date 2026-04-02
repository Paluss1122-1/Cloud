package com.cloud.exploretab

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.floor

@SuppressLint("MissingPermission")
@Composable
fun ExploreTabContent() {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val vm: ExploreViewModel = viewModel()

    val tileCount by vm.tileCount.collectAsState()
    val exploredPercent by vm.exploredPercent.collectAsState()
    val tiles by vm.allTiles.collectAsState()
    var mapView by remember { mutableStateOf<MapView?>(null) }

    LaunchedEffect(Unit) {
        try {
            LocationServices.getFusedLocationProviderClient(ctx).lastLocation
                .addOnSuccessListener { loc ->
                    if (loc != null) mapView?.controller?.animateTo(
                        GeoPoint(
                            loc.latitude,
                            loc.longitude
                        )
                    )
                }
        } catch (_: Exception) {
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onDetach()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard("🗺️ Tiles", tileCount.toString(), Modifier.weight(1f))
            StatCard("🌍 Erkundet", "%.8f%%".format(exploredPercent), Modifier.weight(1f))
            StatCard("📅 Heute", vm.todayCount.toString(), Modifier.weight(1f))
        }

        AndroidView(
            factory = { context ->
                Configuration.getInstance().userAgentValue = context.packageName
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(15.0)
                    controller.setCenter(GeoPoint(48.137, 11.576))
                }.also { mapView = it }
            },
            update = { mv ->
                mv.overlays.removeAll(mv.overlays.filterIsInstance<ExploreOverlay>().toSet())
                if (tiles.isNotEmpty()) {
                    mv.overlays.add(0, ExploreOverlay(tiles))
                    mv.invalidate()
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1E1E))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = Color(0xFF9090A0), fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

private class ExploreOverlay(private val tiles: List<ExploredTile>) : Overlay() {
    private val fillPaint = Paint().apply {
        color = android.graphics.Color.argb(80, 100, 149, 237)
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint().apply {
        color = android.graphics.Color.argb(150, 70, 120, 220)
        style = Paint.Style.STROKE
        strokeWidth = 1f
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
}
