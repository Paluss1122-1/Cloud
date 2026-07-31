package com.tabslify.tabs.fitnesstab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabslify.R
import com.tabslify.core.ui.BgCard
import com.tabslify.core.ui.TextPrimary
import com.tabslify.core.ui.TextSecondary
import com.tabslify.tabs.fitnesstab.FitnessViewModel
import com.tabslify.tabs.fitnesstab.data.Difficulty
import com.tabslify.tabs.fitnesstab.data.Equipment
import com.tabslify.tabs.fitnesstab.data.Exercise
import com.tabslify.tabs.fitnesstab.data.MuscleGroup

@Composable
fun ExerciseListScreen(vm: FitnessViewModel, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val search = vm.exercisesSearch
    val grp = vm.exercisesGroupFilter
    val diff = vm.exercisesDifficultyFilter
    val eq = vm.exercisesEquipmentFilter

    val allEx = vm.exercises
    val filtered = remember(allEx, search, grp, diff, eq) {
        allEx.filter { ex ->
            if (grp != null && ex.muscleGroup != grp) return@filter false
            if (diff != null && ex.difficulty != diff) return@filter false
            if (eq != null && ex.equipment != eq) return@filter false
            if (search.isNotBlank()) {
                val q = search.trim().lowercase()
                val name = ctx.getString(ex.nameRes).lowercase()
                if (q !in name) return@filter false
            }
            true
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.fitness_exercises_title),
                color = TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            SearchBar(
                value = search,
                onValueChange = { vm.exercisesSearch = it }
            )

            FilterChips(
                labelRes = null,
                options = listOf(
                    null to R.string.fitness_exercises_filters_all,
                    MuscleGroup.CHEST to R.string.fitness_exercises_group_chest,
                    MuscleGroup.BACK to R.string.fitness_exercises_group_back,
                    MuscleGroup.LEGS to R.string.fitness_exercises_group_legs,
                    MuscleGroup.CORE to R.string.fitness_exercises_group_core,
                    MuscleGroup.SHOULDERS to R.string.fitness_exercises_group_shoulders,
                    MuscleGroup.ARMS to R.string.fitness_exercises_group_arms,
                    MuscleGroup.CARDIO to R.string.fitness_exercises_group_cardio,
                    MuscleGroup.FULLBODY to R.string.fitness_exercises_group_fullbody
                ),
                selected = grp,
                onSelect = { vm.exercisesGroupFilter = it as MuscleGroup? },
                accentFor = { g -> groupAccent(g as MuscleGroup?) }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChips(
                    modifier = Modifier.weight(1f),
                    labelRes = R.string.fitness_ex_filter_difficulty,
                    options = listOf(
                        null to R.string.fitness_exercises_filters_all,
                        Difficulty.BEGINNER to R.string.fitness_ex_difficulty_beginner,
                        Difficulty.INTERMEDIATE to R.string.fitness_ex_difficulty_intermediate,
                        Difficulty.ADVANCED to R.string.fitness_ex_difficulty_advanced
                    ),
                    selected = diff,
                    onSelect = { vm.exercisesDifficultyFilter = it as Difficulty? },
                    accentFor = { d -> difficultyAccent(d as Difficulty?) }
                )
            }

            FilterChips(
                labelRes = R.string.fitness_ex_filter_gear,
                options = listOf(
                    null to R.string.fitness_exercises_filters_all,
                    Equipment.NONE to R.string.fitness_ex_gear_none,
                    Equipment.RESISTANCE_BAND to R.string.fitness_ex_gear_band,
                    Equipment.DUMBBELL to R.string.fitness_ex_gear_dumbbell,
                    Equipment.BARBELL to R.string.fitness_ex_gear_barbell,
                    Equipment.KETTLEBELL to R.string.fitness_ex_gear_kettlebell,
                    Equipment.PULLUP_BAR to R.string.fitness_ex_gear_pullupbar,
                    Equipment.BENCH to R.string.fitness_ex_gear_bench
                ),
                selected = eq,
                onSelect = { vm.exercisesEquipmentFilter = it as Equipment? },
                accentFor = { e -> equipmentAccent(e as Equipment?) }
            )
        }

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 40.dp, vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(text = "🔍", fontSize = 38.sp)
                    Text(
                        text = stringResource(R.string.fitness_ex_none_found),
                        color = TextSecondary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        } else {
            val grouped = remember(filtered) { filtered.groupBy { it.muscleGroup } }
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MuscleGroup.values().forEach { group ->
                    val list = grouped[group].orEmpty()
                    if (list.isEmpty()) return@forEach
                    item {
                        Spacer(Modifier.height(6.dp))
                        GroupHeader(group, list.size)
                    }
                    items(list, key = { it.id }) { exercise ->
                        ExerciseRow(exercise = exercise, onClick = { vm.openExerciseDetail(exercise.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        placeholder = {
            Text(
                text = stringResource(R.string.fitness_ex_search),
                color = TextSecondary.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            focusedContainerColor = BgCard,
            unfocusedContainerColor = BgCard,
            focusedBorderColor = Color(0xFFB45CFC),
            unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
            cursorColor = Color(0xFFB45CFC)
        ),
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun <T> FilterChips(
    options: List<Pair<T?, Int>>,
    selected: T?,
    onSelect: (T?) -> Unit,
    accentFor: (T?) -> List<Color>,
    modifier: Modifier = Modifier,
    labelRes: Int? = null
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (labelRes != null) {
            Text(
                text = stringResource(labelRes),
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            options.forEach { (value, nameRes) ->
                val isSelected = value == selected
                val colors = accentFor(value)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isSelected) Brush.horizontalGradient(colors)
                            else Brush.horizontalGradient(listOf(BgCard, BgCard))
                        )
                        .border(
                            width = if (isSelected) 0.dp else 1.dp,
                            color = Color.White.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(50)
                        )
                        .clickable { onSelect(value) }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = stringResource(nameRes),
                        color = if (isSelected) Color.White else TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(group: MuscleGroup, count: Int) {
    val accent = groupAccent(group).first()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = groupEmoji(group), fontSize = 18.sp)
        Text(
            text = stringResource(groupNameRes(group)),
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.18f))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = count.toString(),
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ExerciseRow(exercise: Exercise, onClick: () -> Unit) {
    val accent = groupAccent(exercise.muscleGroup).first()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = groupEmoji(exercise.muscleGroup), fontSize = 18.sp)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = stringResource(exercise.nameRes),
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DiffBadge(exercise.difficulty)
                EqBadge(exercise.equipment)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFFF8A4C).copy(alpha = 0.14f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = Color(0xFFFF8A4C),
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = "%.0f".format(exercise.caloriesPerMinute),
                            color = Color(0xFFFF8A4C),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextSecondary.copy(alpha = 0.5f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun DiffBadge(difficulty: Difficulty) {
    val (textRes, color) = when (difficulty) {
        Difficulty.BEGINNER -> R.string.fitness_ex_difficulty_beginner to Color(0xFF4CAF50)
        Difficulty.INTERMEDIATE -> R.string.fitness_ex_difficulty_intermediate to Color(0xFFFFC107)
        Difficulty.ADVANCED -> R.string.fitness_ex_difficulty_advanced to Color(0xFFF44336)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = stringResource(textRes),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EqBadge(equipment: Equipment) {
    val (textRes, color) = when (equipment) {
        Equipment.NONE -> R.string.fitness_ex_gear_none to Color(0xFF4CFCC1)
        Equipment.RESISTANCE_BAND -> R.string.fitness_ex_gear_band to Color(0xFFB45CFC)
        Equipment.DUMBBELL -> R.string.fitness_ex_gear_dumbbell to Color(0xFFFF8A4C)
        Equipment.BARBELL -> R.string.fitness_ex_gear_barbell to Color(0xFFF44336)
        Equipment.KETTLEBELL -> R.string.fitness_ex_gear_kettlebell to Color(0xFFFFC107)
        Equipment.PULLUP_BAR -> R.string.fitness_ex_gear_pullupbar to Color(0xFF2196F3)
        Equipment.BENCH -> R.string.fitness_ex_gear_bench to Color(0xFF00BCD4)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = stringResource(textRes),
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun groupEmoji(g: MuscleGroup): String = when (g) {
    MuscleGroup.CHEST -> "💪"
    MuscleGroup.BACK -> "🔙"
    MuscleGroup.LEGS -> "🦵"
    MuscleGroup.CORE -> "🎯"
    MuscleGroup.SHOULDERS -> "🏋️"
    MuscleGroup.ARMS -> "💥"
    MuscleGroup.CARDIO -> "❤️"
    MuscleGroup.FULLBODY -> "⚡"
}

private fun groupNameRes(g: MuscleGroup): Int = when (g) {
    MuscleGroup.CHEST -> R.string.fitness_exercises_group_chest
    MuscleGroup.BACK -> R.string.fitness_exercises_group_back
    MuscleGroup.LEGS -> R.string.fitness_exercises_group_legs
    MuscleGroup.CORE -> R.string.fitness_exercises_group_core
    MuscleGroup.SHOULDERS -> R.string.fitness_exercises_group_shoulders
    MuscleGroup.ARMS -> R.string.fitness_exercises_group_arms
    MuscleGroup.CARDIO -> R.string.fitness_exercises_group_cardio
    MuscleGroup.FULLBODY -> R.string.fitness_exercises_group_fullbody
}

private fun groupAccent(g: MuscleGroup?): List<Color> = when (g) {
    MuscleGroup.CHEST -> listOf(Color(0xFFFF8A4C), Color(0xFFFF5C9A))
    MuscleGroup.BACK -> listOf(Color(0xFF4CAF50), Color(0xFF4CFCC1))
    MuscleGroup.LEGS -> listOf(Color(0xFF6B4CFC), Color(0xFF2196F3))
    MuscleGroup.CORE -> listOf(Color(0xFFB45CFC), Color(0xFFE040FB))
    MuscleGroup.SHOULDERS -> listOf(Color(0xFFFFC107), Color(0xFFFF8A4C))
    MuscleGroup.ARMS -> listOf(Color(0xFFFF5C9A), Color(0xFFF44336))
    MuscleGroup.CARDIO -> listOf(Color(0xFFE040FB), Color(0xFFFF5C9A))
    MuscleGroup.FULLBODY -> listOf(Color(0xFF00BCD4), Color(0xFF4CAF50))
    null -> listOf(Color(0xFF8E8AA8), Color(0xFFB0AECB))
}

private fun difficultyAccent(d: Difficulty?): List<Color> = when (d) {
    Difficulty.BEGINNER -> listOf(Color(0xFF4CAF50), Color(0xFF4CFCC1))
    Difficulty.INTERMEDIATE -> listOf(Color(0xFFFFC107), Color(0xFFFF8A4C))
    Difficulty.ADVANCED -> listOf(Color(0xFFF44336), Color(0xFFFF5C9A))
    null -> listOf(Color(0xFF8E8AA8), Color(0xFFB0AECB))
}

private fun equipmentAccent(e: Equipment?): List<Color> = when (e) {
    Equipment.NONE -> listOf(Color(0xFF4CFCC1), Color(0xFF4CAF50))
    Equipment.RESISTANCE_BAND -> listOf(Color(0xFFB45CFC), Color(0xFFE040FB))
    Equipment.DUMBBELL -> listOf(Color(0xFFFF8A4C), Color(0xFFFF5C9A))
    Equipment.BARBELL -> listOf(Color(0xFFF44336), Color(0xFFFF8A4C))
    Equipment.KETTLEBELL -> listOf(Color(0xFFFFC107), Color(0xFFFF8A4C))
    Equipment.PULLUP_BAR -> listOf(Color(0xFF2196F3), Color(0xFF00BCD4))
    Equipment.BENCH -> listOf(Color(0xFF00BCD4), Color(0xFF4CFCC1))
    null -> listOf(Color(0xFF8E8AA8), Color(0xFFB0AECB))
}
