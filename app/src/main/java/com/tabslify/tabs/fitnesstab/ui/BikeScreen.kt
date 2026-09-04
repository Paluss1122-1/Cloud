package com.tabslify.tabs.fitnesstab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabslify.R
import com.tabslify.core.ui.BgCard
import com.tabslify.core.ui.NeonBox
import com.tabslify.core.ui.TextPrimary
import com.tabslify.core.ui.TextSecondary
import com.tabslify.core.ui.TextTertiary
import com.tabslify.tabs.exploretab.ExploreBikeClassifier
import com.tabslify.tabs.fitnesstab.BikeFilter
import com.tabslify.tabs.fitnesstab.FitnessViewModel
import com.tabslify.tabs.fitnesstab.data.BIKE_MODE_AUTO
import com.tabslify.tabs.fitnesstab.data.BikeRide
import com.tabslify.tabs.fitnesstab.data.BikeStats
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val BikeAccent = Color(0xFF00FFAA)
internal val BikeAccentSecondary = Color(0xFF00CCFF)
internal val EbikeAccent = Color(0xFFB388FF)
internal val EbikeAccentSecondary = Color(0xFF6B4CFC)
internal val BikeOrange = Color(0xFFFF8A4C)
internal val BikeYellow = Color(0xFFFFD74C)

internal fun bikeModeColors(mode: String): List<Color> =
    if (mode == ExploreBikeClassifier.MODE_EBIKE) listOf(EbikeAccent, EbikeAccentSecondary)
    else listOf(BikeAccent, BikeAccentSecondary)

internal fun bikeModeIcon(mode: String): String =
    if (mode == ExploreBikeClassifier.MODE_EBIKE) "🚴⚡" else "🚲"

@Composable
internal fun bikeModeLabel(mode: String): String = stringResource(
    if (mode == ExploreBikeClassifier.MODE_EBIKE) R.string.modus_ebike else R.string.modus_fahrrad
)

@Composable
internal fun bikeDurationLabel(millis: Long): String {
    val totalMinutes = (millis / 60_000L).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(R.string.fitness_bike_value_hours, hours, minutes)
    } else {
        stringResource(R.string.fitness_bike_value_minutes, minutes)
    }
}

@Composable
fun BikeScreen(vm: FitnessViewModel, modifier: Modifier = Modifier) {
    val rides by vm.bikeRides.collectAsState()
    val stats by vm.bikeStats.collectAsState()

    LaunchedEffect(Unit) { vm.loadBikeRides() }

    val selected = vm.selectedRideId?.let { id -> rides.firstOrNull { it.rideId == id } }
    if (selected != null) {
        BikeRideDetailScreen(ride = selected, vm = vm, modifier = modifier)
        return
    }

    val filter = vm.bikeFilter
    val filtered = remember(rides, filter) {
        when (filter) {
            BikeFilter.ALL -> rides
            BikeFilter.BIKE -> rides.filter { !it.isEbike }
            BikeFilter.EBIKE -> rides.filter { it.isEbike }
        }
    }
    val dateFmt = remember { SimpleDateFormat("EEE, dd. MMM", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.fitness_bike_title),
                        color = TextPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(
                            R.string.fitness_bike_summary_split,
                            stats.bikeRides,
                            stats.ebikeRides
                        ),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                if (vm.bikeLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = BikeAccent,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = { vm.loadBikeRides(force = true) }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.fitness_bike_reload),
                            tint = BikeAccentSecondary
                        )
                    }
                }
            }
        }

        item { BikeSummaryCard(stats = stats) }

        item {
            BikeChipRow(
                options = BIKE_RANGES.map { days ->
                    BikeChip(
                        key = days.toString(),
                        label = stringResource(R.string.fitness_bike_range_days, days),
                        selected = vm.bikeRangeDays == days,
                        colors = listOf(BikeAccentSecondary, EbikeAccentSecondary)
                    )
                },
                onSelect = { key -> vm.changeBikeRange(key.toInt()) }
            )
        }

        item {
            BikeChipRow(
                options = listOf(
                    BikeChip(
                        key = BikeFilter.ALL.name,
                        label = stringResource(R.string.fitness_bike_filter_all),
                        selected = filter == BikeFilter.ALL,
                        colors = listOf(BikeOrange, EbikeAccent)
                    ),
                    BikeChip(
                        key = BikeFilter.BIKE.name,
                        label = "🚲 " + stringResource(R.string.fitness_bike_filter_bike),
                        selected = filter == BikeFilter.BIKE,
                        colors = listOf(BikeAccent, BikeAccentSecondary)
                    ),
                    BikeChip(
                        key = BikeFilter.EBIKE.name,
                        label = "🚴⚡ " + stringResource(R.string.fitness_bike_filter_ebike),
                        selected = filter == BikeFilter.EBIKE,
                        colors = listOf(EbikeAccent, EbikeAccentSecondary)
                    )
                ),
                onSelect = { key -> vm.changeBikeFilter(BikeFilter.valueOf(key)) }
            )
        }

        item { BikeDefaultModeCard(vm = vm) }

        if (filtered.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "🚲", fontSize = 48.sp)
                        Text(
                            text = when {
                                vm.bikeLoading -> stringResource(R.string.fitness_bike_loading)
                                rides.isEmpty() -> stringResource(R.string.fitness_bike_empty)
                                else -> stringResource(R.string.fitness_bike_empty_filtered)
                            },
                            color = TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(filtered, key = { it.rideId }) { ride ->
                BikeRideCard(
                    ride = ride,
                    dateLabel = dateFmt.format(Date(ride.startTime)),
                    timeLabel = "${timeFmt.format(Date(ride.startTime))} – ${timeFmt.format(Date(ride.endTime))}",
                    onClick = { vm.openRide(ride.rideId) }
                )
            }
        }
    }
}

private val BIKE_RANGES = listOf(7, 30, 90)

internal data class BikeChip(
    val key: String,
    val label: String,
    val selected: Boolean,
    val colors: List<Color>
)

@Composable
internal fun BikeChipRow(options: List<BikeChip>, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (option.selected) Brush.horizontalGradient(option.colors)
                        else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                    )
                    .clickable { onSelect(option.key) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option.label,
                    color = if (option.selected) Color.Black else TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (option.selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun BikeSummaryCard(stats: BikeStats) {
    NeonBox(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = RoundedCornerShape(20.dp),
        neonColors = listOf(BikeAccent, EbikeAccent),
        backgroundAlpha = 0.14f,
        borderWidth = 2.dp
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "%.1f".format(stats.totalKm),
                    color = TextPrimary,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "km",
                    color = BikeAccent,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 7.dp)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.fitness_bike_summary_total_distance),
                    color = TextTertiary,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BikeMiniStat(
                    label = stringResource(R.string.fitness_bike_stat_rides),
                    value = stats.rides.toString(),
                    accent = BikeAccentSecondary,
                    modifier = Modifier.weight(1f)
                )
                BikeMiniStat(
                    label = stringResource(R.string.fitness_bike_stat_duration),
                    value = bikeDurationLabel(stats.totalMinutes * 60_000L),
                    accent = EbikeAccent,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BikeMiniStat(
                    label = stringResource(R.string.fitness_bike_stat_calories),
                    value = stringResource(R.string.fitness_bike_value_kcal, stats.totalCalories),
                    accent = BikeOrange,
                    modifier = Modifier.weight(1f)
                )
                BikeMiniStat(
                    label = stringResource(R.string.fitness_bike_stat_elevation),
                    value = stringResource(R.string.fitness_bike_value_meters, stats.totalElevationM),
                    accent = BikeYellow,
                    modifier = Modifier.weight(1f)
                )
            }
            if (stats.rides > 0) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BikeMiniStat(
                        label = stringResource(R.string.fitness_bike_stat_longest),
                        value = stringResource(R.string.fitness_dashboard_distance_value, stats.longestKm),
                        accent = BikeAccent,
                        modifier = Modifier.weight(1f)
                    )
                    BikeMiniStat(
                        label = stringResource(R.string.fitness_bike_stat_fastest),
                        value = stringResource(R.string.fitness_history_speed_value, stats.fastestKmh),
                        accent = EbikeAccentSecondary,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
internal fun BikeMiniStat(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.28f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(text = label, color = TextTertiary, fontSize = 10.sp)
        Text(text = value, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun BikeDefaultModeCard(vm: FitnessViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.fitness_bike_default_mode_title),
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = stringResource(R.string.fitness_bike_default_mode_hint),
            color = TextTertiary,
            fontSize = 11.sp
        )
        BikeChipRow(
            options = listOf(
                BikeChip(
                    key = BIKE_MODE_AUTO,
                    label = stringResource(R.string.fitness_bike_mode_auto),
                    selected = vm.bikeDefaultMode == BIKE_MODE_AUTO,
                    colors = listOf(BikeOrange, BikeYellow)
                ),
                BikeChip(
                    key = ExploreBikeClassifier.MODE_BICYCLE,
                    label = "🚲 " + stringResource(R.string.modus_fahrrad),
                    selected = vm.bikeDefaultMode == ExploreBikeClassifier.MODE_BICYCLE,
                    colors = listOf(BikeAccent, BikeAccentSecondary)
                ),
                BikeChip(
                    key = ExploreBikeClassifier.MODE_EBIKE,
                    label = "🚴⚡ " + stringResource(R.string.modus_ebike),
                    selected = vm.bikeDefaultMode == ExploreBikeClassifier.MODE_EBIKE,
                    colors = listOf(EbikeAccent, EbikeAccentSecondary)
                )
            ),
            onSelect = { key -> vm.changeBikeDefaultMode(key) }
        )
    }
}

@Composable
private fun BikeRideCard(
    ride: BikeRide,
    dateLabel: String,
    timeLabel: String,
    onClick: () -> Unit
) {
    NeonBox(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = RoundedCornerShape(18.dp),
        neonColors = bikeModeColors(ride.mode),
        backgroundAlpha = 0.12f,
        borderWidth = 2.dp,
        onClick = onClick
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) { Text(text = bikeModeIcon(ride.mode), fontSize = 20.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = dateLabel,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(text = timeLabel, color = TextSecondary, fontSize = 11.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = stringResource(R.string.fitness_dashboard_distance_value, ride.distanceKm),
                        color = TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = bikeDurationLabel(ride.durationMs),
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BikeTag(
                    text = bikeModeLabel(ride.mode),
                    color = bikeModeColors(ride.mode).first()
                )
                BikeTag(
                    text = stringResource(R.string.fitness_history_speed_value, ride.avgSpeedKmh),
                    color = BikeAccentSecondary
                )
                BikeTag(
                    text = stringResource(R.string.fitness_bike_value_kcal, ride.calories),
                    color = BikeOrange
                )
                if (ride.elevationGainM >= 1f) {
                    BikeTag(
                        text = "↑ " + stringResource(R.string.fitness_bike_value_meters, ride.elevationGainM),
                        color = BikeYellow
                    )
                }
            }
            if (ride.overridden) {
                Text(
                    text = stringResource(R.string.fitness_bike_type_hint_manual, bikeModeLabel(ride.mode)),
                    color = TextTertiary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
internal fun BikeTag(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(text = text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
internal fun BikeSectionTitle(text: String) {
    Text(
        text = text,
        color = TextPrimary,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
internal fun BikeChartFrame(height: Int, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) { content() }
}
