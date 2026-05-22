package com.cloud.core.ui

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.cloud.tabs.Episode
import com.cloud.tabs.PodcastFeed
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
    cornerRadius: RoundedCornerShape = RoundedCornerShape(20.dp),
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
                val tl = cornerRadius.topStart.toPx(size, this)
                val tr = cornerRadius.topEnd.toPx(size, this)
                val br = cornerRadius.bottomEnd.toPx(size, this)
                val bl = cornerRadius.bottomStart.toPx(size, this)

                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            rect = Rect(0f, 0f, w, h),
                            topLeft = CornerRadius(tl),
                            topRight = CornerRadius(tr),
                            bottomRight = CornerRadius(br),
                            bottomLeft = CornerRadius(bl)
                        )
                    )
                }

                drawIntoCanvas {
                    it.nativeCanvas.apply {
                        drawPath(
                            path.asAndroidPath(),
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
                        drawPath(
                            path.asAndroidPath(),
                            Paint().apply {
                                color = neonColors.first().copy(alpha = 0.65f).toArgb()
                                isAntiAlias = true
                                maskFilter = android.graphics.BlurMaskFilter(glowBlur2, android.graphics.BlurMaskFilter.Blur.OUTER)
                            }
                        )
                    }
                }
            }
            .clip(cornerRadius)
            .background(gradientBrush)
            .border(
                width = borderWidth,
                brush = Brush.linearGradient(neonColors),
                shape = cornerRadius
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),


        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
fun FeedCard(
    feed: PodcastFeed,
    isExpanded: Boolean,
    feedEpisodes: List<Episode>?,
    loadingEpisodes: String?,
    isFavorite: Boolean,
    onToggleExpand: () -> Unit,
    onToggleFav: () -> Unit,
    onDownload: (String, String) -> Unit,
    onStream: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E24)),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImage(
                    model = feed.image.ifEmpty { null },
                    contentDescription = null,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF2A2A32)),
                    contentScale = ContentScale.Crop,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        feed.title,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (feed.author.isNotEmpty()) {
                        Text(
                            feed.author,
                            fontSize = 12.sp,
                            color = Color(0xFF7A7880),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = onToggleFav) {
                    Icon(
                        if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = null,
                        tint = if (isFavorite) Color(0xFFE8622A) else Color(0xFF4A4850)
                    )
                }
                IconButton(onClick = onToggleExpand) {
                    if (loadingEpisodes == feed.feedUrl) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFFE8622A)
                        )
                    } else {
                        Icon(
                            if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null, tint = Color(0xFFE8622A)
                        )
                    }
                }
            }

            if (isExpanded) {
                HorizontalDivider(color = Color.White.copy(0.07f))
                when {
                    feedEpisodes == null -> Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color(0xFFE8622A)
                        )
                    }

                    feedEpisodes.isEmpty() -> Text(
                        "Keine Episoden gefunden",
                        modifier = Modifier.padding(16.dp),
                        color = Color(0xFF7A7880),
                        fontSize = 13.sp
                    )

                    else -> feedEpisodes.forEachIndexed { idx, ep ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "${idx + 1}",
                                fontSize = 11.sp,
                                color = Color(0xFF4A4850),
                                modifier = Modifier.width(24.dp)
                            )
                            Text(
                                ep.title,
                                modifier = Modifier.weight(1f),
                                fontSize = 13.sp,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            OutlinedIconButton(
                                onClick = { onStream(ep.audioUrl) },
                                modifier = Modifier.size(36.dp),
                                border = BorderStroke(1.dp, Color.White.copy(0.15f))
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color(0xFFE8622A),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            OutlinedIconButton(
                                onClick = { onDownload(ep.audioUrl, ep.title) },
                                modifier = Modifier.size(36.dp),
                                border = BorderStroke(1.dp, Color.White.copy(0.15f))
                            ) {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color(0xFFE8622A),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        if (idx < feedEpisodes.lastIndex) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = Color.White.copy(0.04f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlertDialogCloud(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (() -> Unit)? = null,
    title: String = "",
    text: String = "",
    confirmText: String = "Löschen",
    oneButton: Boolean = false,
    shape: Shape = AlertDialogDefaults.shape,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        confirmButton = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (!oneButton) Color(0xFFB71C1C) else MaterialTheme.colorScheme.primary)
                    .clickable { onConfirm() }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) { Text(confirmText, color = TextPrimary, fontWeight = FontWeight.SemiBold) }
        },
        modifier = modifier,
        dismissButton = {
            if(!oneButton) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(BgCard)
                        .clickable { onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) { Text("Abbrechen", color = TextSecondary) }
            }
        },
        icon = icon,
        title = { Text(title, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = { Text(text, color = TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        shape = shape,
        containerColor = BgSurface,
        iconContentColor = iconContentColor,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
        tonalElevation = tonalElevation,
        properties = properties
    )
}