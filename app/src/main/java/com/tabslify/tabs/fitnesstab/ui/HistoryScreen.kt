package com.tabslify.tabs.fitnesstab.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.tabslify.core.ui.AlertDialogTabslify
import com.tabslify.core.ui.BgCard
import com.tabslify.core.ui.NeonBox
import com.tabslify.core.ui.TextPrimary
import com.tabslify.core.ui.TextSecondary
import com.tabslify.core.ui.TextTertiary
import com.tabslify.tabs.fitnesstab.FitnessViewModel
import com.tabslify.tabs.fitnesstab.data.DayStats
import com.tabslify.tabs.fitnesstab.data.EXERCISE_ID_CYCLING
import com.tabslify.tabs.fitnesstab.data.ExerciseRepository
import com.tabslify.tabs.fitnesstab.data.ExploreActivityBridge
import com.tabslify.tabs.fitnesstab.data.MuscleGroup
import com.tabslify.tabs.fitnesstab.data.WorkoutSession
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val AccentOrange = Color(0xFFFF8A4C)
private val AccentGreen = Color(0xFF5BE388)
private val AccentViolet = Color(0xFF7C4DFF)
private val AccentBlue = Color(0xFF4CC9FF)
private val AccentPink = Color(0xFFE850A5)
private val AccentYellow = Color(0xFFFFD74C)
private val BgSoft = Color(0xFF242424)

private fun MuscleGroup.groupEmoji(): String = when (this) {
    MuscleGroup.CHEST -> "💪"; MuscleGroup.BACK -> "🔙"
    MuscleGroup.LEGS -> "🦵"; MuscleGroup.CORE -> "🎯"
    MuscleGroup.SHOULDERS -> "🏋️"; MuscleGroup.ARMS -> "💥"
    MuscleGroup.CARDIO -> "❤️"; MuscleGroup.FULLBODY -> "⚡"
}

private fun MuscleGroup.groupColors(): List<Color> = when (this) {
    MuscleGroup.CHEST -> listOf(AccentOrange, Color(0xFFFF4C4C))
    MuscleGroup.BACK -> listOf(Color(0xFF2ECC71), Color(0xFF1ABC9C))
    MuscleGroup.LEGS -> listOf(Color(0xFF8A4CFF), Color(0xFF4C4CFF))
    MuscleGroup.CORE -> listOf(Color(0xFFB45CFC), AccentPink)
    MuscleGroup.SHOULDERS -> listOf(AccentYellow, AccentOrange)
    MuscleGroup.ARMS -> listOf(AccentPink, Color(0xFFFF4C4C))
    MuscleGroup.CARDIO -> listOf(Color(0xFFE850A5), Color(0xFFFF4C7A))
    MuscleGroup.FULLBODY -> listOf(AccentBlue, Color(0xFF5BE388))
}

@Composable
fun HistoryScreen(vm: FitnessViewModel, modifier: Modifier = Modifier) {
    val stats by vm.stats.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val recentDays by vm.recentDays.collectAsState()
    var tab by remember { mutableStateOf(HistoryTab.SESSIONS) }
    var range by remember { mutableStateOf(HistoryRange.WEEK) }
    var sessionToDelete by remember { mutableStateOf<String?>(null) }

    if (sessionToDelete != null) {
        AlertDialogTabslify(
            onDismiss = { sessionToDelete = null },
            title = stringResource(R.string.fitness_history_session_delete_confirm),
            text = "",
            confirmText = stringResource(R.string.fitness_history_session_delete_yes),
            onConfirm = {
                val id = sessionToDelete
                sessionToDelete = null
                if (id != null) vm.deleteSession(id)
            }
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.fitness_history_title),
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BgCard)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HistoryTab.values().forEach { t ->
                    val selected = tab == t
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) Brush.horizontalGradient(
                                    listOf(Color(0xFF6B4CFC), Color(0xFFB45CFC))
                                ) else Brush.horizontalGradient(
                                    listOf(Color.Transparent, Color.Transparent)
                                )
                            )
                            .clickable { tab = t }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(
                                when (t) {
                                    HistoryTab.SESSIONS -> R.string.fitness_history_tab_sessions
                                    HistoryTab.STATS -> R.string.fitness_history_tab_stats
                                }
                            ),
                            color = if (selected) Color.White else TextSecondary,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        when (tab) {
            HistoryTab.SESSIONS -> SessionsList(
                sessions = sessions,
                onDeleteRequest = { sessionToDelete = it }
            )
            HistoryTab.STATS -> StatsOverview(
                vm = vm,
                stats = stats,
                recentDays = recentDays,
                range = range,
                onRangeChange = { range = it }
            )
        }
    }
}

private enum class HistoryTab { SESSIONS, STATS }
private enum class HistoryRange { WEEK, MONTH }

@Composable
private fun SessionsList(sessions: List<WorkoutSession>, onDeleteRequest: (String) -> Unit) {
    if (sessions.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(text = "📔", fontSize = 48.sp)
                Text(
                    text = stringResource(R.string.fitness_history_no_sessions_yet),
                    color = TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val dateFmt = remember { SimpleDateFormat("EEEE, dd. MMM", Locale.getDefault()) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dayKeyFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val grouped: Map<String, List<WorkoutSession>> = remember(sessions) {
        sessions.groupBy { dayKeyFmt.format(Date(it.dateStartMs)) }
            .mapValues { (_, v) -> v.sortedByDescending { s -> s.dateStartMs } }
            .toSortedMap(Comparator.reverseOrder())
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        grouped.forEach { (dayKey, daySessions) ->
            val minutes = daySessions.sumOf { ((it.dateEndMs - it.dateStartMs) / 60_000L).toInt() }
            val kcal = daySessions.sumOf { it.totalCalories.toDouble() }.toFloat()
            val displayDate = runCatching {
                dateFmt.format(dayKeyFmt.parse(dayKey) ?: Date())
            }.getOrDefault(dayKey)
            item {
                DayHeader(
                    displayDate = displayDate,
                    nWorkouts = daySessions.size,
                    minutes = minutes,
                    kcal = kcal
                )
            }
            items(daySessions, key = { it.sessionId }) { session ->
                SessionCard(
                    session = session,
                    timeFmt = timeFmt,
                    onDelete = { onDeleteRequest(session.sessionId) }
                )
            }
        }
    }
}

@Composable
private fun DayHeader(displayDate: String, nWorkouts: Int, minutes: Int, kcal: Float) {
    Column(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
        Text(
            displayDate,
            color = TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            stringResource(R.string.fitness_history_day_summary, nWorkouts, minutes, kcal),
            color = TextTertiary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun SessionCard(
    session: WorkoutSession,
    timeFmt: SimpleDateFormat,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val start = timeFmt.format(Date(session.dateStartMs))
    val end = timeFmt.format(Date(session.dateEndMs))
    val minutes = ((session.dateEndMs - session.dateStartMs) / 60_000L).coerceAtLeast(0L).toInt()
    val isPushup = session.sessionId.startsWith("pushup_")
    val isExplore = ExploreActivityBridge.isExploreSession(session.sessionId)
    val exploreExercise = if (isExplore) {
        session.entries.firstOrNull()?.let { ExerciseRepository.findById(it.exerciseId) }
    } else {
        null
    }
    val isCycling = session.entries.firstOrNull()?.exerciseId == EXERCISE_ID_CYCLING
    val distanceKm = session.entries.sumOf { e ->
        e.sets.sumOf { it.distanceKm.toDouble() }
    }.toFloat()
    val speedKmh = if (minutes > 0) distanceKm / (minutes / 60f) else 0f
    val nEx = session.entries.size
    val nSets = session.entries.sumOf { it.sets.size }
    val nReps = session.entries.sumOf { e -> e.sets.sumOf { s -> s.reps } }
    val volumeKg = session.entries.sumOf { e ->
        e.sets.sumOf { s -> (s.reps * s.weightKg).toDouble() }
    }

    NeonBox(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = RoundedCornerShape(16.dp),
        neonColors = when {
            isExplore -> listOf(AccentBlue, AccentGreen)
            isPushup -> listOf(AccentOrange, AccentViolet)
            else -> listOf(AccentBlue, AccentViolet)
        },
        backgroundAlpha = 0.12f,
        borderWidth = 2.dp
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when {
                            isExplore -> if (isCycling) "🚴" else "🚶"
                            isPushup -> "📷"
                            else -> "🏋️"
                        },
                        fontSize = 22.sp
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when {
                            exploreExercise != null -> stringResource(exploreExercise.nameRes)
                            isPushup -> stringResource(R.string.fitness_history_workout_pushup)
                            session.name.isNotBlank() -> session.name
                            else -> stringResource(R.string.fitness_history_workout_manual)
                        },
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        "$start – $end · ${stringResource(R.string.fitness_history_session_duration, minutes)}",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.width(6.dp))
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "%.0f".format(session.totalCalories),
                        color = AccentOrange,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        stringResource(R.string.fitness_history_chart_kcal),
                        color = TextTertiary,
                        fontSize = 10.sp
                    )
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = stringResource(R.string.fitness_history_session_expand),
                        tint = TextSecondary
                    )
                }
            }

            // Meta Chips (exercises, sets, reps, volume)
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isExplore) {
                    MetaChip(
                        stringResource(R.string.fitness_dashboard_distance_value, distanceKm),
                        AccentBlue
                    )
                    MetaChip(
                        stringResource(R.string.fitness_history_speed_value, speedKmh),
                        AccentGreen
                    )
                    MetaChip(
                        stringResource(R.string.fitness_history_explore_source),
                        AccentViolet
                    )
                } else {
                    MetaChip(
                        "${if (isPushup) 1 else nEx} " + stringResource(R.string.fitness_history_session_exercises).replace("%1\$d ",""),
                        AccentBlue
                    )
                    if (isPushup) {
                        MetaChip(
                            stringResource(R.string.fitness_history_entry_sets, 1, nReps),
                            AccentViolet
                        )
                    } else {
                        MetaChip(
                            stringResource(R.string.fitness_history_entry_sets, nSets, nReps),
                            AccentViolet
                        )
                    }
                    if (!isPushup && volumeKg > 0.0) {
                        MetaChip(
                            stringResource(R.string.fitness_history_entry_volume, volumeKg.toFloat()),
                            AccentGreen
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(Modifier.padding(top = 10.dp)) {
                    if (isExplore) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BgSoft)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(MuscleGroup.CARDIO.groupColors().map { it.copy(alpha = 0.3f) })
                                    ),
                                contentAlignment = Alignment.Center
                            ) { Text(if (isCycling) "🚴" else "🚶", fontSize = 18.sp) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = if (exploreExercise != null) stringResource(exploreExercise.nameRes) else "",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.fitness_history_explore_detail, distanceKm, minutes, speedKmh),
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else if (isPushup && session.entries.isEmpty()) {
                        val repsGuess = maxOf(nReps, (session.totalCalories / 0.35f).toInt())
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(BgSoft)
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.linearGradient(MuscleGroup.CHEST.groupColors().map { it.copy(alpha = 0.3f) })
                                    ),
                                contentAlignment = Alignment.Center
                            ) { Text(MuscleGroup.CHEST.groupEmoji(), fontSize = 18.sp) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.fitness_ex_chest_pushups),
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    stringResource(R.string.fitness_history_pushup_pose, repsGuess),
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            session.entries.forEach { entry ->
                                val ex = ExerciseRepository.findById(entry.exerciseId)
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(BgSoft)
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val mg = ex?.muscleGroup ?: MuscleGroup.FULLBODY
                                    Box(
                                        Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                Brush.linearGradient(mg.groupColors().map { it.copy(alpha = 0.32f) })
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) { Text(mg.groupEmoji(), fontSize = 18.sp) }
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            if (ex != null) stringResource(ex.nameRes) else entry.exerciseId,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                stringResource(
                                                    R.string.fitness_history_entry_sets,
                                                    entry.sets.size,
                                                    entry.sets.sumOf { it.reps }
                                                ),
                                                color = TextSecondary,
                                                fontSize = 11.sp
                                            )
                                            val vol = entry.sets.sumOf { s -> (s.reps * s.weightKg).toDouble() }
                                            if (vol > 0.0) {
                                                Text("·", color = TextTertiary, fontSize = 11.sp)
                                                Text(
                                                    stringResource(R.string.fitness_history_entry_volume, vol.toFloat()),
                                                    color = AccentGreen,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                    }
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        verticalArrangement = Arrangement.spacedBy(1.dp)
                                    ) {
                                        entry.sets.take(3).forEach { s ->
                                            val repsStr = if (s.durationSeconds > 0) "${s.durationSeconds}s" else "${s.reps}r"
                                            val wStr = if (s.weightKg > 0f) String.format("%.1fkg", s.weightKg) else ""
                                            Text(
                                                "$repsStr $wStr",
                                                color = if (s.completed) AccentGreen else TextTertiary,
                                                fontSize = 10.sp,
                                                fontWeight = if (s.completed) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                        if (entry.sets.size > 3) {
                                            Text(
                                                "+${entry.sets.size - 3}",
                                                color = TextTertiary,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    // Delete Button (nur sichtbar wenn expanded)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (isExplore) {
                            Text(
                                stringResource(R.string.fitness_history_explore_readonly),
                                color = TextTertiary,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(end = 8.dp, top = 6.dp)
                            )
                        } else {
                            IconButton(onClick = onDelete) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = stringResource(R.string.fitness_history_session_delete),
                                        tint = AccentPink
                                    )
                                    Text(
                                        stringResource(R.string.fitness_history_session_delete),
                                        color = AccentPink,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaChip(text: String, color: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatsOverview(
    vm: FitnessViewModel,
    stats: com.tabslify.tabs.fitnesstab.data.FitnessStats,
    recentDays: Map<String, DayStats>,
    range: HistoryRange,
    onRangeChange: (HistoryRange) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(BgCard)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                HistoryRange.values().forEach { r ->
                    val selected = range == r
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Color(0xFF2A2A35) else Color.Transparent)
                            .clickable { onRangeChange(r) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(
                                when (r) {
                                    HistoryRange.WEEK -> R.string.fitness_history_week
                                    HistoryRange.MONTH -> R.string.fitness_history_month
                                }
                            ),
                            color = if (selected) TextPrimary else TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }

        item { MiniBarChart(recentDays = recentDays, goalMin = vm.goalMinutes) }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                StatRow(
                    label = stringResource(R.string.fitness_history_total_workouts),
                    value = stats.totalWorkouts.toString(),
                    accent = Color(0xFF6B4CFC)
                )
                StatRow(
                    label = stringResource(R.string.fitness_history_total_minutes),
                    value = stats.totalMinutes.toString() + " " + stringResource(R.string.fitness_history_chart_min),
                    accent = Color(0xFFB45CFC)
                )
                StatRow(
                    label = stringResource(R.string.fitness_history_total_calories),
                    value = "%.0f".format(stats.totalCalories) + " " + stringResource(R.string.fitness_history_chart_kcal),
                    accent = Color(0xFFFF8A4C)
                )
                if (stats.totalDistanceKm > 0f) {
                    StatRow(
                        label = stringResource(R.string.fitness_history_total_distance),
                        value = stringResource(R.string.fitness_dashboard_distance_value, stats.totalDistanceKm),
                        accent = AccentBlue
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniBarChart(recentDays: Map<String, DayStats>, goalMin: Int) {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BgCard)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
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
                            .width(22.dp)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .fillMaxHeight(ratio.coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isToday) Brush.horizontalGradient(listOf(AccentOrange, Color(0xFFB45CFC)))
                                    else Brush.horizontalGradient(listOf(Color(0xFF6B4CFC), Color(0xFFB45CFC)))
                                )
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(7) { idx ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        labels[idx],
                        color = if (idx == 6) TextPrimary else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (idx == 6) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, accent: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextSecondary, fontSize = 13.sp)
        Text(text = value, color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
