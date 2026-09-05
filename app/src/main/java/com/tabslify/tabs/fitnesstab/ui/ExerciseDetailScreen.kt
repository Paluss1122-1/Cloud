package com.tabslify.tabs.fitnesstab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabslify.R
import com.tabslify.core.ui.BgCard
import com.tabslify.core.ui.NeonBox
import com.tabslify.core.ui.PloppingButton
import com.tabslify.core.ui.TextPrimary
import com.tabslify.core.ui.TextSecondary
import com.tabslify.tabs.fitnesstab.FitnessViewModel
import com.tabslify.tabs.fitnesstab.data.Difficulty
import com.tabslify.tabs.fitnesstab.data.Equipment
import com.tabslify.tabs.fitnesstab.data.Exercise
import com.tabslify.tabs.fitnesstab.data.MuscleGroup
import com.tabslify.tabs.fitnesstab.data.TrackingType

@Composable
fun ExerciseDetailScreen(
    exercise: Exercise,
    vm: FitnessViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopBar(
            title = stringResource(exercise.nameRes),
            onBack = { vm.navigateBackFromDetail() }
        )

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { HeroHeader(exercise) }
            item { MetaChipRow(exercise) }
            item { DescriptionBox(exercise) }
            item { Section(R.string.fitness_ex_detail_steps, Icons.Default.CheckCircle, accentFor(exercise)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    exercise.stepsRes.forEachIndexed { idx, stepRes ->
                        StepRow(number = idx + 1, text = stringResource(stepRes), accent = accentFor(exercise))
                    }
                }
            }}
            if (exercise.tipsRes.isNotEmpty()) {
                item { Section(R.string.fitness_ex_detail_tips, Icons.Default.Lightbulb, Color(0xFFFFC107)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        exercise.tipsRes.forEach { tipRes ->
                            BulletRow(text = stringResource(tipRes), dotColor = Color(0xFFFFC107))
                        }
                    }
                }}
            }
            if (exercise.mistakesRes.isNotEmpty()) {
                item { Section(R.string.fitness_ex_detail_mistakes, Icons.Default.ErrorOutline, Color(0xFFFF5C9A)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        exercise.mistakesRes.forEach { mRes ->
                            BulletRow(text = stringResource(mRes), dotColor = Color(0xFFFF5C9A))
                        }
                    }
                }}
            }
            item { StartCTA(exercise = exercise, vm = vm) }
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.fitness_ex_detail_back),
                tint = TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = title,
            color = TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HeroHeader(exercise: Exercise) {
    val accent = accentFor(exercise)
    NeonBox(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = RoundedCornerShape(22.dp),
        neonColors = listOf(accent, accent.copy(red = (accent.red * 0.5f).coerceAtLeast(0.2f),
            green = (accent.green * 0.5f).coerceAtLeast(0.2f),
            blue = (accent.blue * 0.5f).coerceAtLeast(0.6f))),
        backgroundAlpha = 0.18f,
        borderWidth = 2.5.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(accent.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = groupEmoji(exercise.muscleGroup), fontSize = 30.sp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(exercise.nameRes),
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = groupName(exercise.muscleGroup),
                    color = TextSecondary,
                    fontSize = 13.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    MetaBadge(
                        icon = Icons.Default.LocalFireDepartment,
                        iconColor = Color(0xFFFF8A4C),
                        text = stringResource(R.string.fitness_ex_detail_calmin, exercise.caloriesPerMinute)
                    )
                    MetaBadge(
                        icon = Icons.Default.Schedule,
                        iconColor = Color(0xFF6B4CFC),
                        text = trackingLabel(exercise.trackingType)
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaChipRow(exercise: Exercise) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        MetaChip(
            label = stringResource(R.string.fitness_ex_detail_level),
            value = difficultyLabel(exercise.difficulty),
            accent = difficultyAccent(exercise.difficulty),
            modifier = Modifier.weight(1f)
        )
        MetaChip(
            label = stringResource(R.string.fitness_ex_detail_gear),
            value = equipmentLabel(exercise.equipment),
            accent = equipmentAccent(exercise.equipment),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetaChip(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BgCard)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(accent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Box(modifier = Modifier
                .size(12.dp)
                .clip(RoundedCornerShape(50))
                .background(accent))
        }
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(text = label, color = TextSecondary, fontSize = 10.sp)
            Text(text = value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DescriptionBox(exercise: Exercise) {
    if (exercise.descriptionRes == 0) return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(exercise.descriptionRes),
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 20.sp
        )
    }
}

@Composable
private fun Section(
    titleRes: Int,
    icon: ImageVector,
    accent: Color,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            }
            Text(
                text = stringResource(titleRes),
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(BgCard)
                .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) { content() }
    }
}

@Composable
private fun StepRow(number: Int, text: String, accent: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(50))
                .background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.5f)))),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Text(
            text = text,
            color = TextPrimary,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f).padding(top = 4.dp)
        )
    }
}

@Composable
private fun BulletRow(text: String, dotColor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(8.dp)
                .clip(RoundedCornerShape(50))
                .background(dotColor)
        )
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StartCTA(exercise: Exercise, vm: FitnessViewModel) {
    val cameraAvailable = exercise.trackingType == TrackingType.CAMERA_POSE && exercise.id == "chest_pushups"
    val accent = accentFor(exercise)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (cameraAvailable) {
            PloppingButton(
                onClick = { vm.openCameraWorkout() },
                modifier = Modifier.fillMaxWidth(),
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
                    Text(
                        text = "📷  " + stringResource(R.string.fitness_workout_open_camera),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            PloppingButton(
                onClick = { vm.openManualWorkout(exercise.id) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xFF2A2A2A))
                        .border(1.5.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                        .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🏋️  " + stringResource(R.string.fitness_ex_detail_log_set),
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            PloppingButton(
                onClick = { vm.openManualWorkout(exercise.id) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(accent, accent.copy(alpha = 0.7f))
                            )
                        )
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🏋️  " + stringResource(R.string.fitness_ex_detail_log_set),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun MetaBadge(icon: ImageVector, iconColor: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(14.dp))
        Text(text = text, color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
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

@Composable
private fun groupName(g: MuscleGroup): String = stringResource(
    when (g) {
        MuscleGroup.CHEST -> R.string.fitness_exercises_group_chest
        MuscleGroup.BACK -> R.string.fitness_exercises_group_back
        MuscleGroup.LEGS -> R.string.fitness_exercises_group_legs
        MuscleGroup.CORE -> R.string.fitness_exercises_group_core
        MuscleGroup.SHOULDERS -> R.string.fitness_exercises_group_shoulders
        MuscleGroup.ARMS -> R.string.fitness_exercises_group_arms
        MuscleGroup.CARDIO -> R.string.fitness_exercises_group_cardio
        MuscleGroup.FULLBODY -> R.string.fitness_exercises_group_fullbody
    }
)

@Composable
private fun trackingLabel(t: TrackingType): String = stringResource(
    when (t) {
        TrackingType.REPS -> R.string.fitness_ex_detail_tracking_reps
        TrackingType.TIME -> R.string.fitness_ex_detail_tracking_time
        TrackingType.WEIGHT_REPS -> R.string.fitness_ex_detail_tracking_weight
        TrackingType.CAMERA_POSE -> R.string.fitness_ex_detail_tracking_camera
        TrackingType.DISTANCE -> R.string.fitness_ex_detail_tracking_distance
    }
)

@Composable
private fun difficultyLabel(d: Difficulty): String = stringResource(
    when (d) {
        Difficulty.BEGINNER -> R.string.fitness_ex_difficulty_beginner
        Difficulty.INTERMEDIATE -> R.string.fitness_ex_difficulty_intermediate
        Difficulty.ADVANCED -> R.string.fitness_ex_difficulty_advanced
    }
)

private fun difficultyAccent(d: Difficulty): Color = when (d) {
    Difficulty.BEGINNER -> Color(0xFF4CAF50)
    Difficulty.INTERMEDIATE -> Color(0xFFFFC107)
    Difficulty.ADVANCED -> Color(0xFFF44336)
}

@Composable
private fun equipmentLabel(e: Equipment): String = stringResource(
    when (e) {
        Equipment.NONE -> R.string.fitness_ex_gear_none
        Equipment.RESISTANCE_BAND -> R.string.fitness_ex_gear_band
        Equipment.DUMBBELL -> R.string.fitness_ex_gear_dumbbell
        Equipment.BARBELL -> R.string.fitness_ex_gear_barbell
        Equipment.KETTLEBELL -> R.string.fitness_ex_gear_kettlebell
        Equipment.PULLUP_BAR -> R.string.fitness_ex_gear_pullupbar
        Equipment.BENCH -> R.string.fitness_ex_gear_bench
    }
)

private fun equipmentAccent(e: Equipment): Color = when (e) {
    Equipment.NONE -> Color(0xFF4CFCC1)
    Equipment.RESISTANCE_BAND -> Color(0xFFB45CFC)
    Equipment.DUMBBELL -> Color(0xFFFF8A4C)
    Equipment.BARBELL -> Color(0xFFF44336)
    Equipment.KETTLEBELL -> Color(0xFFFFC107)
    Equipment.PULLUP_BAR -> Color(0xFF2196F3)
    Equipment.BENCH -> Color(0xFF00BCD4)
}

private fun accentFor(e: Exercise): Color = when (e.muscleGroup) {
    MuscleGroup.CHEST -> Color(0xFFFF8A4C)
    MuscleGroup.BACK -> Color(0xFF4CAF50)
    MuscleGroup.LEGS -> Color(0xFF6B4CFC)
    MuscleGroup.CORE -> Color(0xFFB45CFC)
    MuscleGroup.SHOULDERS -> Color(0xFFFFC107)
    MuscleGroup.ARMS -> Color(0xFFFF5C9A)
    MuscleGroup.CARDIO -> Color(0xFFE040FB)
    MuscleGroup.FULLBODY -> Color(0xFF00BCD4)
}
