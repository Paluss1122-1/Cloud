package com.cloud.tabs.school

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.filled.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloud.core.ui.AccentViolet
import com.cloud.core.ui.AccentVioletDim
import com.cloud.core.ui.BgCard
import com.cloud.core.ui.BgDeep
import com.cloud.core.ui.BgSurface
import com.cloud.core.ui.TextPrimary
import com.cloud.core.ui.TextSecondary
import com.cloud.core.ui.TextTertiary
import java.text.SimpleDateFormat
import java.util.Date

data class RecentMaterial(
    val subject: String,
    val fileName: String? = null,
    val previewUrl: String? = null,
    val lastUsed: Long = System.currentTimeMillis()
) {
    val path: String
        get() = if (fileName.isNullOrBlank()) subject else "$subject/$fileName"

    val title: String
        get() = fileName ?: subject

    val subtitle: String
        get() = if (fileName.isNullOrBlank()) "Fach" else "Material • $subject"
}

@Composable
fun SchoolHeader(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    savedSets: List<VokabelSet> = emptyList(),
    onOpenSet: (VokabelSet) -> Unit = {},
    recentMaterials: List<RecentMaterial> = emptyList(),
    onOpenMaterial: (RecentMaterial) -> Unit = {},
    showDashboard: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    paddingValues: PaddingValues,
    drawGradient: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (drawGradient) {
                    Modifier.background(
                        Brush.verticalGradient(
                            listOf(BgDeep, Color.Transparent)
                        )
                    )
                } else {
                    Modifier
                }
            )
            .padding(bottom = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(paddingValues)
                .padding(horizontal = 20.dp)
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(BgSurface.copy(alpha = 0.6f))
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
            }

            Column(modifier = modifier.weight(1f)) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = TextTertiary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            actions()
        }

        AnimatedVisibility(
            visible = showDashboard && (savedSets.isNotEmpty() || recentMaterials.isNotEmpty()),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 0.dp)
                        .padding(bottom = 9.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Zuletzt genutzt",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val combined = (
                            savedSets.map { it to true } +
                                    recentMaterials.map { it to false }
                            )
                        .sortedByDescending { (item, isSet) ->
                            if (isSet) (item as VokabelSet).lastUsed
                            else (item as RecentMaterial).lastUsed
                        }
                        .take(3)

                    items(combined) { (item, isSet) ->
                        if (isSet) QuickSetCard(item as VokabelSet, onClick = { onOpenSet(item) })
                        else QuickMaterialCard(item as RecentMaterial, onClick = { onOpenMaterial(item) })
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickMaterialCard(
    material: RecentMaterial,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(150.dp)
            .width(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.1f), Color.Transparent)
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(AccentViolet, AccentVioletDim)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (material.previewUrl != null) {
                    AsyncImage(
                        model = material.previewUrl,
                        contentDescription = material.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.AutoMirrored.Filled.StickyNote2, contentDescription = "", tint = Color(
                        0xFF1A32F8
                    ), modifier = Modifier.fillMaxSize(0.8f))
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = material.subject,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (material.lastUsed > 0L) {
                    Text(
                        text = SimpleDateFormat("dd.MM.yyyy", LocalLocale.current.platformLocale).format(Date(material.lastUsed)),
                        color = TextTertiary,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickSetCard(
    set: VokabelSet,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .height(150.dp)
            .width(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BgCard)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(Color.White.copy(alpha = 0.1f), Color.Transparent)
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(AccentViolet, AccentVioletDim)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Style, contentDescription = "", tint = Color(
                    0xFF1A32F8
                ), modifier = Modifier.fillMaxSize(0.8f))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = set.name,
                    color = TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
