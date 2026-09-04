package com.tabslify.tabs.focusguard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabslify.core.ui.TextPrimary
import com.tabslify.core.ui.TextSecondary

@Composable
fun FocusGuardBarChart(
    values: List<Float>,
    labels: List<String>,
    maxValue: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    val effectiveMax = if (maxValue > 0f) maxValue else values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
    ) {
        values.forEachIndexed { index, value ->
            val fraction = if (effectiveMax > 0f) (value / effectiveMax).coerceIn(0f, 1f) else 0f
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom
            ) {
                Text(
                    text = formatBarValue(value),
                    color = TextSecondary,
                    fontSize = 9.sp,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                ) {
                    val barWidth = size.width * 0.55f
                    val barHeight = size.height * fraction
                    val left = (size.width - barWidth) / 2f
                    drawRoundRect(
                        color = color.copy(alpha = 0.35f),
                        topLeft = Offset(left, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(6f, 6f)
                    )
                    if (fraction > 0f) {
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(left, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = labels.getOrElse(index) { "" },
                    color = TextSecondary,
                    fontSize = 9.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun FocusGuardHorizontalBar(
    label: String,
    valueText: String,
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 13.sp,
            modifier = Modifier.width(104.dp)
        )
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(10.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(color)
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = valueText,
            color = TextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.width(58.dp),
            textAlign = TextAlign.End
        )
    }
}

private fun formatBarValue(value: Float): String =
    if (value >= 1000f) "${(value / 1000f).let { String.format("%.1f", it) }}k" else value.toInt().toString()
