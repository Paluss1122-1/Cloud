package com.tabslify.tabs.fitnesstab

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import kotlin.math.max

val PoseAccentUp = Color(0xFFB45CFC)
val PoseAccentDown = Color(0xFFFF8A4C)
val PoseAccentIdle = Color(0xFF8E8AA8)

@Composable
fun PoseOverlay(
    frame: PoseFrame?,
    phase: PushUpPhase,
    modifier: Modifier = Modifier
) {
    val target = when (phase) {
        PushUpPhase.UP -> PoseAccentUp
        PushUpPhase.DOWN -> PoseAccentDown
        PushUpPhase.UNKNOWN -> PoseAccentIdle
    }
    val accent by animateColorAsState(
        targetValue = target,
        animationSpec = tween(220),
        label = "poseAccent"
    )

    if (frame == null || frame.landmarks.size < 33) return
    if (frame.imageWidth <= 0 || frame.imageHeight <= 0) return

    Canvas(modifier) {
        val scale = max(
            size.width / frame.imageWidth.toFloat(),
            size.height / frame.imageHeight.toFloat()
        )
        val offsetX = (size.width - frame.imageWidth * scale) / 2f
        val offsetY = (size.height - frame.imageHeight * scale) / 2f

        fun pointOf(index: Int): Offset {
            val landmark = frame.landmarks[index]
            return Offset(
                landmark.x() * frame.imageWidth * scale + offsetX,
                landmark.y() * frame.imageHeight * scale + offsetY
            )
        }

        val boneWidth = 5.dp.toPx()
        val glowWidth = 14.dp.toPx()

        PoseLandmarks.CONNECTIONS.forEach { (startIndex, endIndex) ->
            val start = pointOf(startIndex)
            val end = pointOf(endIndex)

            drawLine(
                color = accent.copy(alpha = 0.22f),
                start = start,
                end = end,
                strokeWidth = glowWidth,
                cap = StrokeCap.Round
            )
            drawLine(
                color = accent,
                start = start,
                end = end,
                strokeWidth = boneWidth,
                cap = StrokeCap.Round
            )
        }

        val jointRadius = 7.dp.toPx()
        PoseLandmarks.DRAWN_POINTS.forEach { index ->
            val center = pointOf(index)
            drawCircle(color = accent.copy(alpha = 0.28f), radius = jointRadius * 2.1f, center = center)
            drawCircle(color = Color.White, radius = jointRadius, center = center)
            drawCircle(color = accent, radius = jointRadius * 0.5f, center = center)
        }
    }
}
