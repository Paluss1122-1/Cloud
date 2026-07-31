package com.tabslify.tabs.fitnesstab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabslify.R
import androidx.compose.ui.window.Dialog
import com.tabslify.core.ui.BgCard
import com.tabslify.core.ui.BgSurface
import com.tabslify.core.ui.NeonBox
import com.tabslify.core.ui.PloppingButton
import com.tabslify.core.ui.TextPrimary
import com.tabslify.core.ui.TextSecondary
import com.tabslify.tabs.fitnesstab.FitnessScreen
import com.tabslify.tabs.fitnesstab.FitnessViewModel
import com.tabslify.tabs.fitnesstab.data.DayStats
import com.tabslify.tabs.fitnesstab.formatDuration
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(vm: FitnessViewModel, modifier: Modifier = Modifier) {
    val stats by vm.stats.collectAsState()
    val recentDays by vm.recentDays.collectAsState()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item { GreetingHeader(vm) }
            item { StreakCard(stats.streakDays) }
            item { TodayStatsGrid(vm) }
            item { GoalProgress(vm) }
            item { QuickStart(vm) }
            item { WeekBars(recentDays, vm.goalMinutes) }
        }

        if (vm.profileDialogVisible) {
            ProfileDialog(vm)
        }
    }
}

@Composable
private fun GreetingHeader(vm: FitnessViewModel) {
    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greeting = when (hour) {
        in 4..10 -> R.string.fitness_dashboard_greeting_morning
        in 11..17 -> R.string.fitness_dashboard_greeting_day
        else -> R.string.fitness_dashboard_greeting_evening
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(greeting),
                color = TextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.fitness_dashboard_today),
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(BgCard)
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                .clickable { vm.openProfileDialog() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = stringResource(R.string.fitness_profile_title),
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ProfileDialog(vm: FitnessViewModel) {
    var age by remember { mutableIntStateOf(vm.userAgeYears) }
    var weight by remember { mutableFloatStateOf(vm.userWeightKg) }

    Dialog(onDismissRequest = { vm.closeProfileDialog() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(BgSurface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(R.string.fitness_profile_title),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.fitness_profile_hint),
                color = TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = stringResource(R.string.fitness_profile_age), color = TextPrimary, fontSize = 13.sp)
                Text(
                    text = stringResource(R.string.fitness_profile_age_value, age),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Slider(
                value = age.toFloat(),
                onValueChange = { age = it.roundToInt() },
                valueRange = 10f..80f,
                steps = 69,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFF6B4CFC),
                    activeTrackColor = Color(0xFF6B4CFC)
                )
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = stringResource(R.string.fitness_profile_weight), color = TextPrimary, fontSize = 13.sp)
                Text(
                    text = stringResource(R.string.fitness_profile_weight_value, weight.roundToInt()),
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Slider(
                value = weight,
                onValueChange = { weight = it },
                valueRange = 30f..150f,
                steps = 119,
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFFF6B4C),
                    activeTrackColor = Color(0xFFFF6B4C)
                )
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgCard)
                        .clickable { vm.closeProfileDialog() }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(stringResource(R.string.abbrechen), color = TextSecondary)
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFF8A4C), Color(0xFFB45CFC), Color(0xFF6B4CFC))
                            )
                        )
                        .clickable { vm.updateProfile(age, weight) }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(stringResource(R.string.speichern), color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun StreakCard(streakDays: Int) {
    NeonBox(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = RoundedCornerShape(22.dp),
        neonColors = listOf(Color(0xFFFF8A4C), Color(0xFFFF5C9A)),
        backgroundAlpha = 0.18f,
        borderWidth = 2.5.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFF8A4C).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Whatshot,
                    contentDescription = null,
                    tint = Color(0xFFFF8A4C),
                    modifier = Modifier.size(30.dp)
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.fitness_dashboard_streak),
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Text(
                    text = stringResource(R.string.fitness_dashboard_streak_days, streakDays),
                    color = TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                val flame = buildString {
                    val n = max(0, streakDays).coerceAtMost(7)
                    repeat(n) { append("🔥") }
                    if (streakDays > 7) append("+$streakDays")
                }
                Text(
                    text = flame.ifEmpty { "🚀" },
                    fontSize = 18.sp
                )
            }
        }
    }
}

@Composable
private fun TodayStatsGrid(vm: FitnessViewModel) {
    val stats by vm.stats.collectAsState()
    val today = stats.today

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Schedule,
                iconColor = Color(0xFF6B4CFC),
                label = stringResource(R.string.fitness_dashboard_minutes),
                value = stringResource(R.string.fitness_dashboard_minutes_value, today.totalMinutes),
                bg = Color(0xFF6B4CFC).copy(alpha = 0.15f)
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.LocalFireDepartment,
                iconColor = Color(0xFFFF6B4C),
                label = stringResource(R.string.fitness_dashboard_calories),
                value = stringResource(R.string.fitness_dashboard_calories_value, today.totalCalories),
                bg = Color(0xFFFF6B4C).copy(alpha = 0.15f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.FitnessCenter,
                iconColor = Color(0xFFB45CFC),
                label = stringResource(R.string.fitness_dashboard_workouts),
                value = stringResource(R.string.fitness_dashboard_workouts_value, today.workoutsCount),
                bg = Color(0xFFB45CFC).copy(alpha = 0.15f)
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Repeat,
                iconColor = Color(0xFF4CFCC1),
                label = stringResource(R.string.fitness_dashboard_reps),
                value = stringResource(R.string.fitness_dashboard_reps_value, today.totalReps),
                bg = Color(0xFF4CFCC1).copy(alpha = 0.15f)
            )
        }
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    iconColor: Color,
    label: String,
    value: String,
    bg: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(BgCard)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(bg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            Text(
                text = value,
                color = TextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun GoalProgress(vm: FitnessViewModel) {
    val stats by vm.stats.collectAsState()
    val today = stats.today
    val goalMin = vm.goalMinutes
    val goalKcal = vm.goalCalories
    val minPct = (today.totalMinutes.toFloat() / goalMin).coerceIn(0f, 1f)
    val kcalPct = (today.totalCalories / goalKcal).coerceIn(0f, 1f)
    val bothReached = minPct >= 1f && kcalPct >= 1f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BgCard)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = if (bothReached) stringResource(R.string.fitness_dashboard_goal_reached) else "🎯",
                color = if (bothReached) Color(0xFF4CFCC1) else TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            if (bothReached) {
                Text(text = "🎉", fontSize = 18.sp)
            }
        }

        GoalBar(
            label = stringResource(R.string.fitness_dashboard_minutes),
            valueText = stringResource(R.string.fitness_dashboard_minutes_value, today.totalMinutes),
            goalText = stringResource(R.string.fitness_dashboard_goal_minutes, goalMin),
            progress = minPct,
            colors = listOf(Color(0xFF6B4CFC), Color(0xFFB45CFC))
        )
        GoalBar(
            label = stringResource(R.string.fitness_dashboard_calories),
            valueText = stringResource(R.string.fitness_dashboard_calories_value, today.totalCalories),
            goalText = stringResource(R.string.fitness_dashboard_goal_calories, goalKcal),
            progress = kcalPct,
            colors = listOf(Color(0xFFFF6B4C), Color(0xFFFF8A4C))
        )
    }
}

@Composable
private fun GoalBar(
    label: String,
    valueText: String,
    goalText: String,
    progress: Float,
    colors: List<Color>
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = TextSecondary, fontSize = 12.sp)
            Text(text = valueText, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(colors))
            )
        }
        Text(
            text = goalText,
            color = TextSecondary.copy(alpha = 0.8f),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun QuickStart(vm: FitnessViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.fitness_dashboard_quick_start),
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PloppingButton(
                onClick = { vm.openCameraWorkout() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFFF8A4C), Color(0xFFB45CFC), Color(0xFF6B4CFC))
                            )
                        )
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "📷", fontSize = 22.sp)
                        Text(
                            text = stringResource(R.string.fitness_dashboard_quick_pushups),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }

            PloppingButton(
                onClick = { vm.openManualWorkout() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(BgCard)
                        .border(1.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(text = "🏋️", fontSize = 22.sp)
                        Text(
                            text = stringResource(R.string.fitness_dashboard_quick_workout),
                            color = TextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekBars(recentDays: Map<String, DayStats>, goalMin: Int) {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val dayFmt = SimpleDateFormat("EEEEE", Locale.getDefault())
    val days = (0 until 7).map {
        val c = cal.clone() as Calendar
        c.add(Calendar.DAY_OF_YEAR, -it)
        fmt.format(Date(c.timeInMillis)) to dayFmt.format(Date(c.timeInMillis))
    }.reversed()
    val dayKeys = days.map { it.first }
    val labels = days.map { it.second }

    val maxMin = dayKeys
        .mapNotNull { recentDays[it]?.totalMinutes }
        .maxOrNull()?.coerceAtLeast(goalMin)?.coerceAtLeast(1) ?: goalMin.coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.fitness_dashboard_week_overview),
            color = TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(BgCard)
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
                .padding(16.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    dayKeys.forEachIndexed { idx, key ->
                        val minutes = recentDays[key]?.totalMinutes ?: 0
                        val ratio = minutes.toFloat() / maxMin
                        val isToday = idx == 6
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(18.dp)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color.White.copy(alpha = 0.06f)),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(18.dp)
                                        .fillMaxHeight(ratio.coerceIn(0f, 1f))
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isToday) Brush.horizontalGradient(
                                                listOf(Color(0xFFFF8A4C), Color(0xFFB45CFC))
                                            ) else Brush.horizontalGradient(
                                                listOf(Color(0xFF6B4CFC), Color(0xFFB45CFC))
                                            )
                                        )
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    repeat(7) { idx ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = labels[idx],
                                color = if (idx == 6) TextPrimary else TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (idx == 6) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                val weekMinutes = dayKeys.sumOf { recentDays[it]?.totalMinutes ?: 0 }
                val weekKcal = dayKeys.sumOf { (recentDays[it]?.totalCalories?.toDouble() ?: 0.0) }.toFloat()
                if (weekMinutes == 0 && weekKcal < 0.5f) {
                    Text(
                        text = stringResource(R.string.fitness_dashboard_week_empty),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "${weekMinutes} ${stringResource(R.string.fitness_history_chart_min)}",
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "%.0f ${stringResource(R.string.fitness_history_chart_kcal)}".format(weekKcal),
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}
