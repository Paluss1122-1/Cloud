package com.cloud.core.ui

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch


@Composable
fun PloppingButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onFinishedClick: () -> Unit = {},
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    shape: Shape = ButtonDefaults.shape,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable () -> Unit,
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val containerColor = if (enabled) colors.containerColor else colors.disabledContainerColor

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                alpha = if (enabled) 1f else 0.38f
            }
            .clip(shape)
            .background(containerColor)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                enabled = enabled
            ) {
                scope.launch {
                    scale.animateTo(
                        0.82f,
                        spring(
                            stiffness = Spring.StiffnessHigh,
                            dampingRatio = Spring.DampingRatioMediumBouncy
                        )
                    )
                    onClick()
                    scale.animateTo(
                        1f,
                        spring(
                            stiffness = Spring.StiffnessMedium,
                            dampingRatio = Spring.DampingRatioLowBouncy
                        )
                    )
                    onFinishedClick()
                }
            }
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun NeonBox(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    backgroundAlpha: Float = 0.22f,
    borderWidth: Dp = 3.dp,
    glowBlur1: Float = 42f,
    glowBlur2: Float = 114f,
    neonColors: List<Color>,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            neonColors[0].copy(alpha = backgroundAlpha),
            neonColors[1].copy(alpha = backgroundAlpha)
        )
    )

    Box(
        modifier = modifier
            .drawBehind {
                val w = size.width
                val h = size.height
                val r = cornerRadius.toPx()

                drawIntoCanvas {
                    it.nativeCanvas.apply {
                        drawRoundRect(
                            0f, 0f, w, h, r, r,
                            Paint().apply {
                                color = neonColors.last().copy(alpha = 0.55f).toArgb()
                                isAntiAlias = true
                                maskFilter = android.graphics.BlurMaskFilter(glowBlur1, android.graphics.BlurMaskFilter.Blur.OUTER)
                            }
                        )
                    }
                }

                drawIntoCanvas {
                    it.nativeCanvas.apply {
                        drawRoundRect(
                            0f, 0f, w, h, r, r,
                            Paint().apply {
                                color = neonColors.first().copy(alpha = 0.65f).toArgb()
                                isAntiAlias = true
                                maskFilter = android.graphics.BlurMaskFilter(glowBlur2, android.graphics.BlurMaskFilter.Blur.OUTER)
                            }
                        )
                    }
                }
            }
            .clip(RoundedCornerShape(cornerRadius))
            .background(gradientBrush)
            .border(
                width = borderWidth,
                brush = Brush.linearGradient(neonColors),
                shape = RoundedCornerShape(cornerRadius)
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),


        contentAlignment = Alignment.Center
    ) {
        content()
    }
}