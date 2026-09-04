package com.tabslify.tabs.focusguard.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabslify.R
import com.tabslify.core.ui.BgCard
import com.tabslify.core.ui.TextPrimary
import com.tabslify.core.ui.TextSecondary
import com.tabslify.tabs.focusguard.FocusGuardViewModel
import com.tabslify.tabs.focusguard.formatDurationMs
import com.tabslify.tabs.focusguard.data.FOCUSGUARD_RESTRICTED_CATEGORIES
import com.tabslify.tabs.focusguard.data.SleepRecord

@Composable
fun FocusGuardDashboardScreen(vm: FocusGuardViewModel, modifier: Modifier = Modifier) {
    val todayUsage by vm.todayUsage.collectAsState()
    val sleepWeek by vm.sleepWeek.collectAsState()
    val goal by vm.todayGoal.collectAsState()
    val achievements by vm.achievements.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item { StatusBanner(vm) }
        item {
            FgSectionCard(stringResource(R.string.focusguard_dash_usage_today)) {
                val categories = FOCUSGUARD_RESTRICTED_CATEGORIES.filter { (todayUsage[it] ?: 0L) > 0L }
                if (categories.isEmpty()) {
                    Text(
                        text = stringResource(R.string.focusguard_dash_no_usage),
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                } else {
                    val max = categories.maxOf { todayUsage[it] ?: 0L }.coerceAtLeast(1L)
                    categories.forEach { category ->
                        val ms = todayUsage[category] ?: 0L
                        FocusGuardHorizontalBar(
                            label = categoryLabel(category),
                            valueText = formatDurationMs(ms),
                            fraction = ms.toFloat() / max.toFloat(),
                            color = categoryColor(category)
                        )
                    }
                }
            }
        }
        item { StudyGoalCard(vm, goal) }
        item { SleepCard(sleepWeek) }
        item { PointsStreakRow(vm) }
        if (achievements.isNotEmpty()) {
            item { AchievementsCard(achievements.map { it.type }) }
        }
    }
}

@Composable
private fun StatusBanner(vm: FocusGuardViewModel) {
    val text = when {
        vm.schoolDay -> stringResource(R.string.focusguard_status_school_day)
        vm.overrideActive -> stringResource(R.string.focusguard_status_override)
        vm.restrictionsActive -> stringResource(R.string.focusguard_status_active)
        else -> stringResource(R.string.focusguard_status_inactive)
    }
    val colors = if (vm.restrictionsActive && !vm.schoolDay) {
        listOf(Color(0xFFFF8A4C), Color(0xFFB45CFC))
    } else {
        listOf(Color(0xFF4CFCC1), Color(0xFF6B4CFC))
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(colors))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "🛡️", fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StudyGoalCard(
    vm: FocusGuardViewModel,
    goal: com.tabslify.tabs.focusguard.data.StudyGoal?
) {
    FgSectionCard(stringResource(R.string.focusguard_dash_goal)) {
        val completed = goal?.completedCount ?: 0
        val target = goal?.targetCount ?: vm.studyTarget
        val fraction = if (target > 0) completed.toFloat() / target.toFloat() else 0f
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(6.dp)),
            color = Color(0xFFB45CFC),
            trackColor = Color.White.copy(alpha = 0.08f)
        )
        Text(
            text = "$completed / $target ${stringResource(R.string.focusguard_config_goal_target)}",
            color = TextSecondary,
            fontSize = 14.sp
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { vm.addStudyProgress(1) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B4CFC))
            ) {
                Text(stringResource(R.string.focusguard_config_goal_add), color = Color.White)
            }
            TextButton(onClick = { vm.resetTodayGoal() }) {
                Text(
                    text = stringResource(R.string.focusguard_goals_reset),
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun SleepCard(sleepWeek: List<SleepRecord>) {
    FgSectionCard(stringResource(R.string.focusguard_dash_sleep)) {
        val lastNight = sleepWeek.firstOrNull()
        if (lastNight != null) {
            Text(
                text = stringResource(R.string.focusguard_summary_sleep) +
                    " ${formatDurationMs(lastNight.durationMs)}",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (sleepWeek.isNotEmpty()) {
            val chronological = sleepWeek.reversed()
            FocusGuardBarChart(
                values = chronological.map { it.durationMs / 3_600_000f },
                labels = chronological.map { weekdayAbbrev(it.date) },
                maxValue = 12f,
                color = Color(0xFF4CFCC1)
            )
        }
    }
}

@Composable
private fun PointsStreakRow(vm: FocusGuardViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        StatBox(
            label = stringResource(R.string.focusguard_dash_points),
            value = vm.points.toString(),
            emoji = "⭐",
            modifier = Modifier.weight(1f)
        )
        StatBox(
            label = stringResource(R.string.focusguard_dash_streak),
            value = "${vm.currentStreak} ${stringResource(R.string.focusguard_stats_days)}".let { it },
            emoji = "🔥",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatBox(label: String, value: String, emoji: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(BgCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(text = emoji, fontSize = 22.sp)
        Text(text = value, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(text = label, color = TextSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun AchievementsCard(types: List<String>) {
    FgSectionCard(stringResource(R.string.focusguard_dash_achievements)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            types.take(8).forEach { type ->
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = achievementEmoji(type), fontSize = 18.sp)
                }
            }
        }
    }
}

fun achievementEmoji(type: String): String = when {
    type.startsWith("streak_") -> "🔥"
    type.startsWith("points_") -> "⭐"
    type == "first_day" -> "🌱"
    else -> "🏆"
}
