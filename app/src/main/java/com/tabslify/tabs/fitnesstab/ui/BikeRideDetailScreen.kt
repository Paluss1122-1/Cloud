package com.tabslify.tabs.fitnesstab.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.tabslify.R
import com.tabslify.core.ui.BgCard
import com.tabslify.core.ui.TextPrimary
import com.tabslify.core.ui.TextSecondary
import com.tabslify.core.ui.TextTertiary
import com.tabslify.tabs.exploretab.ExploreBikeClassifier
import com.tabslify.tabs.fitnesstab.FitnessViewModel
import com.tabslify.tabs.fitnesstab.data.BIKE_MODE_AUTO
import com.tabslify.tabs.fitnesstab.data.BikeRide
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun BikeRideDetailScreen(ride: BikeRide, vm: FitnessViewModel, modifier: Modifier = Modifier) {
    val dateFmt = remember { SimpleDateFormat("EEEE, dd. MMMM", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { vm.closeRide() }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.fitness_bike_back),
                    tint = TextPrimary
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = dateFmt.format(Date(ride.startTime)),
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = "${timeFmt.format(Date(ride.startTime))} – ${timeFmt.format(Date(ride.endTime))}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            Text(text = bikeModeIcon(ride.mode), fontSize = 26.sp)
        }

        RouteMap(ride = ride)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ModeSwitchCard(ride = ride, vm = vm)
            StatsGrid(ride = ride)
            SpeedProfile(ride = ride)
            ElevationProfile(ride = ride)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun RouteMap(ride: BikeRide) {
    val points = ride.route
    if (points.size < 2) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(BgCard),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.fitness_bike_detail_no_route),
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(20.dp)
            )
        }
        return
    }

    var mapView by remember { mutableStateOf<MapView?>(null) }
    val lineColor = bikeModeColors(ride.mode).first().toArgb()

    DisposableEffect(Unit) {
        onDispose {
            val view = mapView
            view?.tileProvider?.clearTileCache()
            view?.onDetach()
            mapView = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .padding(horizontal = 20.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(18.dp))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                Configuration.getInstance().userAgentValue = context.packageName
                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = false
                    setScrollableAreaLimitDouble(null)
                    controller.setZoom(14.0)
                    controller.setCenter(GeoPoint(points.first().lat, points.first().lon))
                }.also { mapView = it }
            },
            update = { view ->
                view.overlays.removeAll(view.overlays.filterIsInstance<Polyline>().toSet())
                val geoPoints = points.map { GeoPoint(it.lat, it.lon) }
                view.overlays.add(
                    Polyline().apply {
                        setPoints(geoPoints)
                        outlinePaint.color = lineColor
                        outlinePaint.strokeWidth = 9f
                        outlinePaint.isAntiAlias = true
                    }
                )
                val box = BoundingBox.fromGeoPoints(geoPoints)
                view.post { view.zoomToBoundingBox(box, false, 60) }
                view.invalidate()
            }
        )
    }
}

@Composable
private fun ModeSwitchCard(ride: BikeRide, vm: FitnessViewModel) {
    val autoLabel = bikeModeLabel(ride.autoMode)
    val currentLabel = bikeModeLabel(ride.mode)
    val confidence = if (ride.autoMode == ExploreBikeClassifier.MODE_EBIKE) {
        ride.classifierScore
    } else {
        1.0 - ride.classifierScore
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BgCard)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.fitness_bike_type_title),
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            BikeTag(text = currentLabel, color = bikeModeColors(ride.mode).first())
        }

        BikeChipRow(
            options = listOf(
                BikeChip(
                    key = BIKE_MODE_AUTO,
                    label = stringResource(R.string.fitness_bike_mode_auto),
                    selected = !ride.overridden,
                    colors = listOf(BikeOrange, BikeYellow)
                ),
                BikeChip(
                    key = ExploreBikeClassifier.MODE_BICYCLE,
                    label = "🚲 " + stringResource(R.string.modus_fahrrad),
                    selected = ride.overridden && !ride.isEbike,
                    colors = listOf(BikeAccent, BikeAccentSecondary)
                ),
                BikeChip(
                    key = ExploreBikeClassifier.MODE_EBIKE,
                    label = "🚴⚡ " + stringResource(R.string.modus_ebike),
                    selected = ride.overridden && ride.isEbike,
                    colors = listOf(EbikeAccent, EbikeAccentSecondary)
                )
            ),
            onSelect = { key ->
                vm.setRideMode(ride.rideId, if (key == BIKE_MODE_AUTO) null else key)
            }
        )

        Text(
            text = when {
                ride.overridden -> stringResource(R.string.fitness_bike_type_hint_manual, currentLabel)
                !ride.classifierDecided -> stringResource(R.string.fitness_bike_type_hint_undecided)
                else -> stringResource(
                    R.string.fitness_bike_type_hint_auto,
                    autoLabel,
                    (confidence * 100).roundToInt()
                )
            },
            color = TextTertiary,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )

        if (ride.isEbike) {
            Text(
                text = stringResource(R.string.fitness_bike_ebike_note),
                color = EbikeAccent,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun StatsGrid(ride: BikeRide) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BikeMiniStat(
                label = stringResource(R.string.fitness_bike_stat_distance),
                value = stringResource(R.string.fitness_dashboard_distance_value, ride.distanceKm),
                accent = BikeAccent,
                modifier = Modifier.weight(1f)
            )
            BikeMiniStat(
                label = stringResource(R.string.fitness_bike_stat_duration),
                value = bikeDurationLabel(ride.durationMs),
                accent = BikeAccentSecondary,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BikeMiniStat(
                label = stringResource(R.string.fitness_bike_stat_avg_speed),
                value = stringResource(R.string.fitness_history_speed_value, ride.avgSpeedKmh),
                accent = EbikeAccent,
                modifier = Modifier.weight(1f)
            )
            BikeMiniStat(
                label = stringResource(R.string.fitness_bike_stat_max_speed),
                value = stringResource(R.string.fitness_history_speed_value, ride.maxSpeedKmh),
                accent = EbikeAccentSecondary,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BikeMiniStat(
                label = stringResource(R.string.fitness_bike_stat_moving),
                value = bikeDurationLabel(ride.movingSeconds * 1000L),
                accent = BikeAccent,
                modifier = Modifier.weight(1f)
            )
            BikeMiniStat(
                label = stringResource(R.string.fitness_bike_stat_calories),
                value = stringResource(R.string.fitness_bike_value_kcal, ride.calories),
                accent = BikeOrange,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BikeMiniStat(
                label = stringResource(R.string.fitness_bike_stat_ascent),
                value = stringResource(R.string.fitness_bike_value_meters, ride.elevationGainM),
                accent = BikeYellow,
                modifier = Modifier.weight(1f)
            )
            BikeMiniStat(
                label = stringResource(R.string.fitness_bike_stat_descent),
                value = stringResource(R.string.fitness_bike_value_meters, ride.elevationLossM),
                accent = BikeAccentSecondary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SpeedProfile(ride: BikeRide) {
    val values = remember(ride.rideId) { smooth(ride.route.map { it.speedKmh }, 5) }
    if (values.size < 3) return
    ProfileSection(
        title = stringResource(R.string.fitness_bike_detail_speed),
        topLabel = stringResource(R.string.fitness_history_speed_value, values.max()),
        bottomLabel = stringResource(R.string.fitness_history_speed_value, values.min()),
        values = values,
        colors = bikeModeColors(ride.mode)
    )
}

@Composable
private fun ElevationProfile(ride: BikeRide) {
    val values = remember(ride.rideId) {
        smooth(ride.route.mapNotNull { it.altitude?.toFloat() }, 5)
    }
    if (values.size < 3) {
        BikeSectionTitle(stringResource(R.string.fitness_bike_detail_elevation))
        Text(
            text = stringResource(R.string.fitness_bike_detail_no_elevation),
            color = TextTertiary,
            fontSize = 12.sp
        )
        return
    }
    ProfileSection(
        title = stringResource(R.string.fitness_bike_detail_elevation),
        topLabel = stringResource(R.string.fitness_bike_value_meters, values.max()),
        bottomLabel = stringResource(R.string.fitness_bike_value_meters, values.min()),
        values = values,
        colors = listOf(BikeYellow, BikeOrange)
    )
}

@Composable
private fun ProfileSection(
    title: String,
    topLabel: String,
    bottomLabel: String,
    values: List<Float>,
    colors: List<Color>
) {
    BikeSectionTitle(title)
    BikeChartFrame(height = 160) {
        Row(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.width(56.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = topLabel, color = TextSecondary, fontSize = 10.sp)
                Text(text = bottomLabel, color = TextTertiary, fontSize = 10.sp)
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                val minValue = values.min()
                val maxValue = values.max()
                val span = (maxValue - minValue).takeIf { it > 0.001f } ?: 1f
                val stepX = size.width / (values.size - 1)
                val line = Path()
                val area = Path()
                values.forEachIndexed { index, value ->
                    val x = index * stepX
                    val y = size.height - ((value - minValue) / span) * size.height
                    if (index == 0) {
                        line.moveTo(x, y)
                        area.moveTo(x, size.height)
                        area.lineTo(x, y)
                    } else {
                        line.lineTo(x, y)
                        area.lineTo(x, y)
                    }
                }
                area.lineTo(size.width, size.height)
                area.close()
                drawPath(
                    path = area,
                    brush = Brush.verticalGradient(
                        listOf(colors.first().copy(alpha = 0.35f), Color.Transparent)
                    )
                )
                drawPath(
                    path = line,
                    brush = Brush.horizontalGradient(colors),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
    }
}

private fun smooth(values: List<Float>, window: Int): List<Float> {
    if (values.size < window || window < 2) return values
    val half = window / 2
    return values.indices.map { index ->
        val from = (index - half).coerceAtLeast(0)
        val to = (index + half).coerceAtMost(values.size - 1)
        var sum = 0f
        for (i in from..to) sum += values[i]
        sum / (to - from + 1)
    }
}
