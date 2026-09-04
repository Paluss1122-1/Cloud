package com.tabslify.tabs.focusguard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabslify.R
import com.tabslify.core.ui.TextSecondary
import com.tabslify.tabs.focusguard.FocusGuardViewModel
import com.tabslify.tabs.focusguard.data.ymdDaysAgo

@Composable
fun FocusGuardStatsScreen(vm: FocusGuardViewModel, modifier: Modifier = Modifier) {
    val days = (6 downTo 0).map { ymdDaysAgo(it) }
    val labels = days.map { weekdayAbbrev(it) }

    val usageValues = days.map { date ->
        (vm.usageWeek[date]?.values?.sum() ?: 0L) / 60_000f
    }
    val sleepByDate = vm.sleepWeekData.associateBy { it.date }
    val sleepValues = days.map { date ->
        (sleepByDate[date]?.durationMs ?: 0L) / 3_600_000f
    }
    val goalsByDate = vm.goalsWeek.associateBy { it.date }
    val goalValues = days.map { date ->
        val goal = goalsByDate[date] ?: return@map 0f
        if (goal.targetCount <= 0) 0f else goal.completedCount.toFloat() / goal.targetCount
    }
    val goalDaysMet = days.count { date ->
        val goal = goalsByDate[date]
        goal != null && goal.targetCount > 0 && goal.completedCount >= goal.targetCount
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            FgSectionCard(stringResource(R.string.focusguard_stats_usage_week)) {
                if (usageValues.all { it <= 0f }) {
                    Text(
                        text = stringResource(R.string.focusguard_stats_no_data),
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                } else {
                    FocusGuardBarChart(
                        values = usageValues,
                        labels = labels,
                        maxValue = usageValues.maxOrNull() ?: 1f,
                        color = Color(0xFFFF8A4C)
                    )
                    Text(
                        text = stringResource(R.string.focusguard_stats_minutes),
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            FgSectionCard(stringResource(R.string.focusguard_stats_sleep_week)) {
                if (sleepValues.all { it <= 0f }) {
                    Text(
                        text = stringResource(R.string.focusguard_stats_no_data),
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                } else {
                    FocusGuardBarChart(
                        values = sleepValues,
                        labels = labels,
                        maxValue = 12f,
                        color = Color(0xFF4CFCC1)
                    )
                    Text(
                        text = "h",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        item {
            FgSectionCard(stringResource(R.string.focusguard_stats_goal_rate)) {
                FocusGuardBarChart(
                    values = goalValues,
                    labels = labels,
                    maxValue = 1f,
                    color = Color(0xFFB45CFC)
                )
                Text(
                    text = "$goalDaysMet / 7 ${stringResource(R.string.focusguard_stats_days)}",
                    color = TextSecondary,
                    fontSize = 13.sp
                )
            }
        }
    }
}
