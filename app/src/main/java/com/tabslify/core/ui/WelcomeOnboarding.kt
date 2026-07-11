package com.tabslify.core.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.tabslify.R
import kotlinx.coroutines.launch
import java.util.Calendar

/* ------------------------------------------------------------------ */
/*  Palette (shared with the welcome flow)                            */
/* ------------------------------------------------------------------ */

private val BgTop = Color(0xFF1A1330)
private val BgBottom = Color(0xFF060509)
private val Surface = Color(0xFF0F0B14)
private val TextPrimaryOnboarding = Color(0xFFF7F5FB)
private val TextSecondaryOnboarding = Color(0xB3FFFFFF) // white @ 70%
private val ChipBg = Color(0x0DFFFFFF)        // white @ 5%
private val ChipBgSelected = Color(0x3DB45CFC) // accent violet @ 24%

private val AccentColors = listOf(Color(0xFFFF8A4C), Color(0xFFB45CFC), Color(0xFF6B4CFC))
private val AccentBrush = Brush.linearGradient(AccentColors)
private val AccentBorderBrush = Brush.linearGradient(listOf(Color(0xFFFF8A4C), Color(0xFF7C4DFF)))

/* ------------------------------------------------------------------ */
/*  Page model                                                        */
/* ------------------------------------------------------------------ */

private enum class PageKind { LOGO, GRID, BULLETS, BULLETS_ALT, PERMISSIONS }

private data class OnboardPage(
    val kind: PageKind,
    val title: String,
    val subtitle: String = "",
    val icon: ImageVector? = null,
    val iconBrush: Brush = AccentBrush,
    val bullets: List<String> = emptyList()
)

private val pages = listOf(
    OnboardPage(
        kind = PageKind.LOGO,
        title = "Tabslify",
        subtitle = "Eine App, die 20+ Tools ersetzt.\nWillkommen an Bord."
    ),
    OnboardPage(
        kind = PageKind.GRID,
        title = "Alles an einem Ort",
        subtitle = "KI-Chat, Passwörter, Browser, Notizen, Vokabel Traning, Musik / Podcasts & mehr — kein App-Wechsel mehr nötig."
    ),
    OnboardPage(
        kind = PageKind.BULLETS,
        title = "KI, die wirklich hilft",
        icon = Icons.Filled.AutoAwesome,
        iconBrush = Brush.linearGradient(listOf(Color(0xFFB45CFC), Color(0xFF6B4CFC))),
        bullets = listOf(
            "Chat mit Text & Bildern",
            "Vokabeln automatisch aus Fotos",
            "Optionale Musik-Analyse"
        )
    ),
    OnboardPage(
        kind = PageKind.BULLETS_ALT,
        title = "Privatsphäre zuerst",
        icon = Icons.Filled.Shield,
        iconBrush = Brush.linearGradient(listOf(Color(0xFF5C8CFF), Color(0xFF5C6BFC))),
        bullets = listOf(
            "Kein Tracking, keine Werbung",
            "Tracking-Funktionen nur optional"
        )
    ),
    // Last page — NOT skippable. "Überspringen" on earlier pages jumps straight here.
    OnboardPage(
        kind = PageKind.PERMISSIONS,
        title = "Tabslify Berechtigungen"
    )
)

/* Icon tiles for the grid page */
private data class GridTile(val icon: ImageVector, val brush: Brush)

private val gridTiles = listOf(
    GridTile(Icons.AutoMirrored.Filled.Chat, Brush.linearGradient(listOf(Color(0xFFFF8A4C), Color(0xFFFF5C7A)))),
    GridTile(Icons.Filled.Lock, Brush.linearGradient(listOf(Color(0xFFFFB05C), Color(0xFFFF8A4C)))),
    GridTile(Icons.Filled.Public, Brush.linearGradient(listOf(Color(0xFFD95CFF), Color(0xFFB45CFC)))),
    GridTile(Icons.Filled.School, Brush.linearGradient(listOf(Color(0xFF7A5CFF), Color(0xFF6B4CFC)))),
    GridTile(Icons.Filled.CalendarMonth, Brush.linearGradient(listOf(Color(0xFF5C8CFF), Color(0xFF5C6BFC)))),
    GridTile(Icons.Filled.AutoAwesome, Brush.linearGradient(listOf(Color(0xFFFF5CAF), Color(0xFFD95CFF))))
)

@Preview
@Composable
fun Prev() {
    WelcomeOnboardingScreen(currentPage = 3)
}

@Composable
fun WelcomeOnboardingScreen(
    onFinished: () -> Unit = {},
    initialPage: Int = 0,
    currentPage: Int? = null,
    onPageChanged: ((Int) -> Unit)? = null
) {
    val safeInitialPage = remember(initialPage) { initialPage.coerceIn(0, pages.lastIndex) }
    val pagerState = rememberPagerState(initialPage = safeInitialPage, pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.lastIndex

    BackHandler {
        if (pagerState.currentPage > 0) {
            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
        }
    }

    LaunchedEffect(currentPage) {
        if (currentPage != null && currentPage in pages.indices && pagerState.currentPage != currentPage) {
            pagerState.scrollToPage(currentPage)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged?.invoke(pagerState.currentPage)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemGestureExclusion()
    ) {
        val currentHour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
        val bgpicture = remember {
            when (currentHour) {
                in 11..16 -> R.drawable.day
                else -> R.drawable.night
            }
        }
        Image(
            painter = painterResource(id = bgpicture),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().zIndex(0f)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BgTop.copy(alpha = 0.9f), BgBottom.copy(alpha = 0.9f))))
                .statusBarsPadding().zIndex(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                if (!isLast) {
                    Text(
                        text = "Überspringen",
                        color = Color(0x8CFFFFFF),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                scope.launch { pagerState.animateScrollToPage(pages.lastIndex) }
                            }
                            .padding(6.dp)
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                PageContent(
                    page = pages[page],
                    isActive = pagerState.currentPage == page
                )
            }

            // Dots
            DotsIndicator(
                count = pages.size,
                current = pagerState.currentPage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            )

            // Primary action
            GradientButton(
                label = if (isLast) "Los geht's" else "Weiter",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 28.dp)
            ) {
                if (isLast) onFinished()
                else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/*  Page content + entrance animation                                 */
/* ------------------------------------------------------------------ */

@Composable
private fun PageContent(page: OnboardPage, isActive: Boolean) {
    if (page.kind == PageKind.PERMISSIONS) {
        PermissionInfoScreen(onboarding = true)
        return
    }

    val context = LocalContext.current

    // Bounce-in when the page becomes the current one.
    val scale = remember(page) { Animatable(0.35f) }
    val alpha = remember(page) { Animatable(0f) }

    var mediaAnalyticsEnabled by remember { mutableStateOf(true) }
    LaunchedEffect(context) {
        com.tabslify.tabs.mediaplayer.MediaAnalyticsManager.init(context)
        mediaAnalyticsEnabled = com.tabslify.tabs.mediaplayer.MediaAnalyticsManager.isAnalyticsEnabled()
    }

    LaunchedEffect(isActive) {
        if (isActive) {
            alpha.animateTo(1f, tween(300))
            scale.animateTo(
                1f,
                spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)
            )
        } else {
            scale.snapTo(0.35f)
            alpha.snapTo(0f)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        when (page.kind) {
            PageKind.LOGO -> {
                Box(
                    modifier = Modifier
                        .size(132.dp)
                        .scale(scale.value)
                        .clip(RoundedCornerShape(32.dp))
                        .background(AccentBrush),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.mipmap.app_icon_foreground_rounded_corners),
                        contentDescription = "Tabslify",
                        modifier = Modifier.size(96.dp)
                    )
                }
            }

            PageKind.GRID -> {
                IconGrid(scale = scale.value)
            }

            else -> {
                val icon = page.icon
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(scale.value)
                            .clip(CircleShape)
                            .background(page.iconBrush),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
                    }
                }
            }
        }

        Spacer(Modifier.height(34.dp))

        Text(
            text = page.title,
            color = TextPrimaryOnboarding,
            fontSize = if (page.kind == PageKind.LOGO) 34.sp else 26.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        if (page.subtitle.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                text = page.subtitle,
                color = TextSecondaryOnboarding,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                textAlign = TextAlign.Center
            )
        }

        if (page.bullets.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            page.bullets.forEach { line ->
                BulletRow(text = line)
                Spacer(Modifier.height(14.dp))
            }
        }

        if (page.kind == PageKind.BULLETS_ALT) {
            Spacer(Modifier.height(22.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(ChipBg)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Media Analytics",
                        color = TextPrimaryOnboarding,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Tracke Hörgewohnheiten für Statistiken",
                        color = TextSecondaryOnboarding,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = mediaAnalyticsEnabled,
                    onCheckedChange = { newValue ->
                        mediaAnalyticsEnabled = newValue
                        com.tabslify.tabs.mediaplayer.MediaAnalyticsManager.setAnalyticsEnabled(newValue)
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentColors[1], // Using the accent violet color from the palette
                        uncheckedThumbColor = Color.Gray,
                        uncheckedTrackColor = Color.DarkGray,
                        disabledUncheckedTrackColor = Color.DarkGray.copy(alpha = 0.4f)
                    )
                )
            }
        }
    }
}

@Composable
private fun IconGrid(scale: Float) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        gridTiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { tile ->
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .scale(scale)
                            .clip(RoundedCornerShape(22.dp))
                            .background(tile.brush),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(tile.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun BulletRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(ChipBg)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(AccentBrush)
        )
        Spacer(Modifier.width(12.dp))
        Text(text = text, color = Color(0xD1FFFFFF), fontSize = 15.sp)
    }
}

/* ------------------------------------------------------------------ */
/*  Dots + button                                                     */
/* ------------------------------------------------------------------ */

@Composable
private fun DotsIndicator(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { i ->
            val active = i == current
            val width by animateDpAsState(if (active) 26.dp else 8.dp, tween(250), label = "dotW")
            Box(
                modifier = Modifier
                    .width(width)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        if (active) AccentBrush
                        else Brush.linearGradient(listOf(Color(0x38FFFFFF), Color(0x38FFFFFF)))
                    )
            )
        }
    }
}

@Composable
private fun GradientButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(AccentBrush)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}
