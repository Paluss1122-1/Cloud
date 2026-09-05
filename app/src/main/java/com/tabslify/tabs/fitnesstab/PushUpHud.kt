package com.tabslify.tabs.fitnesstab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabslify.R

@Composable
fun PushUpHud(vm: FitnessViewModel, modifier: Modifier = Modifier) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xCC060509), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RepHero(reps = vm.reps, phase = vm.phase)
            Spacer(Modifier.height(14.dp))
            StatsRow(vm = vm)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xE6060509))
                    )
                )
                .padding(horizontal = 20.dp)
                .padding(top = 60.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StatusLine(vm = vm)
            Spacer(Modifier.height(14.dp))
            PushUpControls(vm = vm)
        }

        AnimatedVisibility(
            visible = vm.showHint,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            PushUpHintOverlay(onDismiss = { vm.dismissHint() })
        }
    }
}

@Composable
private fun RepHero(reps: Int, phase: PushUpPhase) {
    val pulse by animateFloatAsState(
        targetValue = if (phase == PushUpPhase.DOWN) 1.08f else 1f,
        animationSpec = spring(),
        label = "repPulse"
    )
    val accent = when (phase) {
        PushUpPhase.UP -> PoseAccentUp
        PushUpPhase.DOWN -> PoseAccentDown
        PushUpPhase.UNKNOWN -> PoseAccentIdle
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = reps.toString(),
            fontSize = 92.sp,
            lineHeight = 96.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFF7F5FB),
            modifier = Modifier.scale(pulse)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.22f))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text(
                text = stringResource(
                    when (phase) {
                        PushUpPhase.UP -> R.string.fitness_phase_oben
                        PushUpPhase.DOWN -> R.string.fitness_phase_unten
                        PushUpPhase.UNKNOWN -> R.string.fitness_phase_bereit
                    }
                ),
                color = accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun StatsRow(vm: FitnessViewModel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF1E1A2D).copy(alpha = 0.82f))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatCell(
            label = stringResource(R.string.fitness_dauer),
            value = formatDuration(vm.elapsedMs)
        )
        StatCell(
            label = stringResource(R.string.fitness_tempo),
            value = stringResource(R.string.fitness_tempo_wert, vm.repsPerMinute)
        )
        StatCell(
            label = stringResource(R.string.fitness_bestleistung),
            value = vm.personalBest.toString()
        )
    }
}

@Composable
private fun StatCell(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = Color(0xFFF7F5FB),
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}
