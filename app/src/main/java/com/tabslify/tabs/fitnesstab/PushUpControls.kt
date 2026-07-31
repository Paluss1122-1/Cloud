package com.tabslify.tabs.fitnesstab

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabslify.R
import com.tabslify.core.ui.PloppingButton

@Composable
fun StatusLine(vm: FitnessViewModel) {
    val message = when {
        vm.errorMessage != null -> vm.errorMessage
        vm.trackingIssue == TrackingIssue.NO_BODY -> stringResource(R.string.fitness_kein_korper)
        vm.trackingIssue == TrackingIssue.ARMS_HIDDEN -> stringResource(R.string.fitness_arme_verdeckt)
        vm.isRunning && vm.trackingIssue == TrackingIssue.CALIBRATING ->
            stringResource(R.string.fitness_kalibriert)

        vm.isRunning && vm.formHintRes != null -> stringResource(vm.formHintRes!!)
        vm.isNewRecord -> stringResource(R.string.fitness_neuer_rekord)
        else -> stringResource(R.string.fitness_ellbogen, vm.elbowAngle)
    }

    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Color(0xFF1E1A2D).copy(alpha = 0.9f))
                .padding(horizontal = 18.dp, vertical = 9.dp)
        ) {
            Text(
                text = message.orEmpty(),
                color = Color(0xFFF7F5FB),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PushUpControls(vm: FitnessViewModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircleControl(
            icon = Icons.Filled.Cameraswitch,
            description = stringResource(R.string.fitness_kamera_wechseln),
            active = false,
            onClick = { vm.switchCamera() }
        )

        PloppingButton(
            modifier = Modifier.weight(1f),
            onClick = { vm.toggleSession() },
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.horizontalGradient(
                            if (vm.isRunning) {
                                listOf(Color(0xFF3A3550), Color(0xFF2A2A35))
                            } else {
                                listOf(Color(0xFFFF8A4C), Color(0xFFB45CFC), Color(0xFF6B4CFC))
                            }
                        )
                    )
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(
                        if (vm.isRunning) R.string.fitness_stop else R.string.fitness_start
                    ),
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        CircleControl(
            icon = Icons.Filled.Accessibility,
            description = stringResource(R.string.fitness_skelett),
            active = vm.showSkeleton,
            onClick = { vm.showSkeleton = !vm.showSkeleton }
        )

        CircleControl(
            icon = Icons.Filled.Refresh,
            description = stringResource(R.string.fitness_reset),
            active = false,
            onClick = { vm.resetSession() }
        )
    }
}

@Composable
private fun CircleControl(
    icon: ImageVector,
    description: String,
    active: Boolean,
    onClick: () -> Unit
) {
    PloppingButton(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (active) Color(0xFFB45CFC).copy(alpha = 0.3f) else Color(0xFF1E1A2D)
        ),
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(13.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (active) Color(0xFFB45CFC) else Color(0xFFF7F5FB),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
fun PushUpHintOverlay(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6060509))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color(0xFFFF8A4C), Color(0xFFB45CFC))
                        )
                    )
                    .padding(horizontal = 18.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(R.string.fitness_tipp_titel),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.fitness_tipp_text),
                color = Color(0xFFF7F5FB),
                fontSize = 16.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(26.dp))

            PloppingButton(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFFB45CFC), Color(0xFF6B4CFC))
                            )
                        )
                        .padding(horizontal = 34.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = stringResource(R.string.fitness_verstanden),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
