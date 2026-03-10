package com.example.cloud.mediaplayer

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.cloud.quiethoursnotificationhelper.formatMs
import com.example.cloud.service.MediaPlayerService
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

// ─────────────────────────────────────────────────────────────────────────────
// Theme Colors
// ─────────────────────────────────────────────────────────────────────────────

private val BgDeep = Color(0xFF121212)
private val BgSurface = Color(0xFF1E1E1E)
private val BgCard = Color(0xFF2A2A2A)
private val AccentViolet = Color(0xFF7C4DFF)
private val AccentVioletDim = Color(0xFF4A148C)
private val TextPrimary = Color(0xFFFFFFFF)
private val TextSecondary = Color(0xFFB0B0B0)
private val TextTertiary = Color(0xFF757575)

// ─────────────────────────────────────────────────────────────────────────────
// Entry Point
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaTab(viewModel: MediaViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()
    val context = LocalContext.current

    var analyticsTarget by remember { mutableStateOf<Any?>(null) }

    var showFullscreenPlayer by remember { mutableStateOf(false) }
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    Log.d("MEDIAPLAYER", "MediaTab: $nowPlaying")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgDeep)
    ) {
        Scaffold(
            containerColor = BgDeep,
            bottomBar = {
                Column {
                    // Mini player sits above the nav bar
                    NowPlayingBar(
                        nowPlaying = nowPlaying,
                        onOpenFullscreen = { showFullscreenPlayer = true },
                        onPlayPause = {
                            viewModel.playerAction(
                                context,
                                MediaViewModel.PlayerAction.PLAY_PAUSE
                            )
                        }
                    )
                    // Original nav bar
                    MediaBottomBar(
                        currentTab = state.currentTab,
                        onTabSelected = { viewModel.setTab(it) }
                    )
                }
            }
        ) { padding ->
            Crossfade(
                targetState = state.currentTab,
                modifier = Modifier.padding(padding),
                label = "tab_transition"
            ) { tab ->
                when (tab) {
                    MediaTab.HOME -> HomeTab(
                        state = state,
                        onSongClick = { song -> playSong(context, song, state.songs) },
                        onSongLongClick = { analyticsTarget = it },
                        onEpisodeClick = { ep -> playEpisode(context, ep) },
                        onRefresh = { viewModel.refresh() }
                    )

                    MediaTab.SEARCH -> SearchTab(
                        query = state.searchQuery,
                        results = searchResults,
                        onQueryChange = { viewModel.setSearchQuery(it) },
                        onSongClick = { song -> playSong(context, song, state.songs) },
                        onSongLongClick = { analyticsTarget = it },
                        onEpisodeClick = { ep -> playEpisode(context, ep) },
                        onShowClick = { viewModel.setTab(MediaTab.LIBRARY) }
                    )

                    MediaTab.LIBRARY -> LibraryTab(
                        state = state,
                        onSongClick = { song -> playSong(context, song, state.songs) },
                        onSongLongClick = { analyticsTarget = it },
                        onEpisodeClick = { ep -> playEpisode(context, ep) },
                        onEpisodeLongClick = { analyticsTarget = it }
                    )
                }
            }
        }

        // Analytics Bottom Sheet
        analyticsTarget?.let { target ->
            AnalyticsBottomSheet(
                target = target,
                songAnalytics = state.songAnalytics,
                podcastAnalytics = state.podcastAnalytics,
                onDismiss = { analyticsTarget = null }
            )
        }

        if (showFullscreenPlayer) {
            var offsetY by remember { mutableStateOf(0f) }
            val dismissThreshold = 200f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f)
                    .offset { IntOffset(0, offsetY.toInt().coerceAtLeast(0)) }
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            if (offsetY + delta >= 0f) offsetY += delta
                        },
                        onDragStopped = {
                            if (offsetY > dismissThreshold) {
                                showFullscreenPlayer = false
                            }
                            offsetY = 0f
                        }
                    )
            ) {
                FullscreenPlayerContent(
                    nowPlaying = nowPlaying,
                    onDismiss = { showFullscreenPlayer = false },
                    onAction = { action -> viewModel.playerAction(context, action) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom Navigation Bar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MediaBottomBar(currentTab: MediaTab, onTabSelected: (MediaTab) -> Unit) {
    BottomAppBar(
        containerColor = BgSurface,
        contentColor = TextSecondary,
        tonalElevation = 0.dp
    ) {
        val tabs = listOf(
            Triple(MediaTab.HOME, "Home", "🏠"),
            Triple(MediaTab.SEARCH, "Suche", "🔍"),
            Triple(MediaTab.LIBRARY, "Bibliothek", "📚")
        )
        tabs.forEach { (tab, label, icon) ->
            val selected = currentTab == tab
            NavigationBarItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = {
                    Text(icon, fontSize = 20.sp)
                },
                label = {
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = if (selected) AccentViolet else TextTertiary
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AccentViolet,
                    indicatorColor = AccentVioletDim.copy(alpha = 0.3f)
                )
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HOME TAB
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTab(
    state: MediaUiState,
    onSongClick: (MediaPlayerService.Song) -> Unit,
    onSongLongClick: (MediaPlayerService.Song) -> Unit,
    onEpisodeClick: (PodcastEpisode) -> Unit,
    onRefresh: () -> Unit
) {
    // Active Podcasts
    val activePodcasts = remember(state.episodes) {
        state.episodes.filter { !it.isCompleted && it.savedPositionMs > 0 }
    }

    // Recent Songs
    val recentSongs = remember(state.songs, state.songAnalytics) {
        state.songs
            .filter { (state.songAnalytics[it.path]?.lastPlayedAt ?: 0L) > 0L }
            .sortedByDescending { state.songAnalytics[it.path]?.lastPlayedAt ?: 0L }
            .take(10)
    }

    PullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Global Stats Strip
            item {
                GlobalStatsStrip(stats = state.globalStats)
            }

            // Algorithmic Playlists Header
            if (state.algorithmicPlaylists.isNotEmpty()) {
                item {
                    SectionHeader("Für dich")
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.algorithmicPlaylists) { (source, songs) ->
                            AlgorithmicPlaylistCard(
                                source = source,
                                songCount = songs.size,
                                onClick = {
                                    songs.firstOrNull()?.let { onSongClick(it) }
                                }
                            )
                        }
                    }
                }
            }
            if (activePodcasts.isNotEmpty()) {
                item { SectionHeader("Weiter hören") }
                items(activePodcasts.take(5)) { ep ->
                    PodcastEpisodeRow(
                        episode = ep,
                        analytics = state.podcastAnalytics[ep.path],
                        showName = state.shows.find { it.id == ep.showId }?.name ?: "Sonstige",
                        onClick = { onEpisodeClick(ep) },
                        onLongClick = {}
                    )
                }
            }
            if (recentSongs.isNotEmpty()) {
                item { SectionHeader("Zuletzt gehört") }
                items(recentSongs) { song ->
                    SongRow(
                        song = song,
                        analytics = state.songAnalytics[song.path],
                        onClick = { onSongClick(song) },
                        onLongClick = { onSongLongClick(song) }
                    )
                }
            }

            // Empty state
            if (state.songs.isEmpty() && state.episodes.isEmpty() && !state.isLoading) {
                item {
                    EmptyState(
                        icon = "🎵",
                        title = "Keine Medien gefunden",
                        subtitle = "Lege Musik in /Cloud/ ab und ziehe zum Aktualisieren"
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SEARCH TAB
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchTab(
    query: String,
    results: SearchResults,
    onQueryChange: (String) -> Unit,
    onSongClick: (MediaPlayerService.Song) -> Unit,
    onSongLongClick: (MediaPlayerService.Song) -> Unit,
    onEpisodeClick: (PodcastEpisode) -> Unit,
    onShowClick: (PodcastShow) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SearchBar(query = query, onQueryChange = onQueryChange)

        val hasResults =
            results.songs.isNotEmpty() || results.episodes.isNotEmpty() || results.shows.isNotEmpty()

        if (query.length < 2) {
            EmptyState(
                icon = "🔍",
                title = "Suche starten",
                subtitle = "Gib mindestens 2 Zeichen ein"
            )
        } else if (!hasResults) {
            EmptyState(
                icon = "😕",
                title = "Keine Ergebnisse",
                subtitle = "Nichts für \"$query\" gefunden"
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (results.songs.isNotEmpty()) {
                    item { SectionHeader("Songs (${results.songs.size})") }
                    items(results.songs) { song ->
                        SongRow(
                            song = song, analytics = null,
                            onClick = { onSongClick(song) },
                            onLongClick = { onSongLongClick(song) })
                    }
                }
                if (results.episodes.isNotEmpty()) {
                    item { SectionHeader("Podcast-Folgen (${results.episodes.size})") }
                    items(results.episodes) { ep ->
                        PodcastEpisodeRow(
                            ep,
                            null,
                            "",
                            onClick = { onEpisodeClick(ep) },
                            onLongClick = {})
                    }
                }
                if (results.shows.isNotEmpty()) {
                    item { SectionHeader("Shows (${results.shows.size})") }
                    items(results.shows) { show ->
                        ShowCard(
                            show = show,
                            episodeCount = 0,
                            unheard = 0,
                            completionFraction = 0f,
                            expanded = false,
                            episodes = emptyList(),
                            podcastAnalytics = emptyMap(),
                            onToggle = { onShowClick(show) },
                            onEpisodeClick = {})
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LIBRARY TAB
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LibraryTab(
    state: MediaUiState,
    onSongClick: (MediaPlayerService.Song) -> Unit,
    onSongLongClick: (MediaPlayerService.Song) -> Unit,
    onEpisodeClick: (PodcastEpisode) -> Unit,
    onEpisodeLongClick: (PodcastEpisode) -> Unit
) {
    var expandedShowId by remember { mutableStateOf<String?>(null) }
    val grouped = remember(state.episodes, state.shows) {
        PodcastShowManager.groupEpisodesIntoShows(state.episodes)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Podcast Shows
        item { SectionHeader("Podcast-Shows") }
        items(state.shows) { show ->
            val eps = grouped[show] ?: emptyList()
            val stats = PodcastShowManager.getShowStats(show.id, eps, state.podcastAnalytics)
            ShowCard(
                show = show,
                episodeCount = stats.totalEpisodes,
                unheard = stats.unheardEpisodes,
                completionFraction = stats.completionFraction,
                expanded = expandedShowId == show.id,
                episodes = eps,
                podcastAnalytics = state.podcastAnalytics,
                onToggle = {
                    expandedShowId = if (expandedShowId == show.id) null else show.id
                },
                onEpisodeClick = { ep ->
                    onEpisodeClick(ep)
                }
            )
        }

        // All Songs
        if (state.songs.isNotEmpty()) {
            item { SectionHeader("Alle Songs (${state.songs.size})") }
            items(state.songs) { song ->
                SongRow(
                    song = song,
                    analytics = state.songAnalytics[song.path],
                    onClick = { onSongClick(song) },
                    onLongClick = { onSongLongClick(song) }
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Reusable Components
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GlobalStatsStrip(stats: MediaAnalyticsManager.GlobalStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BgSurface)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem("⏱", formatDuration(stats.totalListenedMs), "Gesamt")
        StatDivider()
        StatItem("🔥", "${stats.listeningStreakDays}d", "Streak")
        StatDivider()
        StatItem("🎵", "${stats.totalSongsPlayed}", "Songs gespielt")
    }
}

@Composable
private fun StatItem(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 20.sp)
        Text(value, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = TextTertiary, fontSize = 11.sp)
    }
}

@Composable
private fun StatDivider() {
    Box(
        modifier = Modifier
            .height(40.dp)
            .width(1.dp)
            .background(BgCard)
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = TextPrimary,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun AlgorithmicPlaylistCard(
    source: PlaylistSource,
    songCount: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(160.dp, 200.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(source.accentColor.copy(alpha = 0.8f), BgCard)
                )
            )
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(source.icon, fontSize = 36.sp)
            Column {
                Text(
                    source.name,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text("$songCount Songs", color = TextSecondary, fontSize = 12.sp)
                Text(
                    source.description, color = TextTertiary, fontSize = 11.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    song: MediaPlayerService.Song,
    analytics: SongAnalytics?,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Color circle avatar
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(generateAccentColor(song.name)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                song.name.take(1).uppercase(),
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.name, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (analytics != null && analytics.playCount > 0) {
                Text(
                    "${analytics.playCount}× · ${(analytics.completionRate * 100).toInt()}% Completion",
                    color = TextTertiary, fontSize = 11.sp
                )
            }
        }
        if (analytics?.isFavorite == true) {
            Text("⭐", fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PodcastEpisodeRow(
    episode: PodcastEpisode,
    analytics: PodcastAnalytics?,
    showName: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(generateAccentColor(showName)),
                contentAlignment = Alignment.Center
            ) {
                Text("🎙️", fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    episode.title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row {
                    if (showName.isNotEmpty()) {
                        Text("$showName · ", color = TextTertiary, fontSize = 11.sp)
                    }
                    if (episode.durationMs > 0) {
                        Text(
                            formatDuration(episode.durationMs),
                            color = TextTertiary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            if (episode.isCompleted) Text("✓", color = Color(0xFF43A047), fontSize = 14.sp)
        }
        // Progress bar
        if (episode.savedPositionMs > 0 && episode.durationMs > 0 && !episode.isCompleted) {
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 56.dp)
            ) {
                LinearProgressIndicator(
                    progress = {
                        (episode.savedPositionMs.toFloat() / episode.durationMs).coerceIn(
                            0f,
                            1f
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(CircleShape),
                    color = AccentViolet,
                    trackColor = BgCard
                )
            }
        }
    }
}

@Composable
private fun ShowCard(
    show: PodcastShow,
    episodeCount: Int,
    unheard: Int,
    completionFraction: Float,
    expanded: Boolean,
    episodes: List<PodcastEpisode>,
    podcastAnalytics: Map<String, PodcastAnalytics>,
    onToggle: () -> Unit,
    onEpisodeClick: (PodcastEpisode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BgSurface)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggle() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Gradient cover
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(generateShowGradient(show.accentColorSeed)),
                contentAlignment = Alignment.Center
            ) {
                Text("🎙️", fontSize = 24.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(show.name, color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "$episodeCount Folgen${if (unheard > 0) " · $unheard neu" else ""}",
                    color = TextSecondary, fontSize = 12.sp
                )
            }
            // Completion ring
            CompletionRing(fraction = completionFraction, size = 36.dp, color = AccentViolet)
            Spacer(Modifier.width(8.dp))
            Text(if (expanded) "▲" else "▼", color = TextTertiary, fontSize = 12.sp)
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column {
                episodes.forEach { ep ->
                    PodcastEpisodeRow(
                        episode = ep,
                        analytics = podcastAnalytics[ep.path],
                        showName = "",
                        onClick = { onEpisodeClick(ep) },
                        onLongClick = {}
                    )
                }
                if (episodes.isEmpty()) {
                    Text(
                        "Keine Folgen",
                        color = TextTertiary, fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompletionRing(fraction: Float, size: androidx.compose.ui.unit.Dp, color: Color) {
    Canvas(modifier = Modifier.size(size)) {
        val strokeWidth = 4.dp.toPx()
        val radius = (this.size.minDimension - strokeWidth) / 2f
        val center = Offset(this.size.width / 2, this.size.height / 2)
        // Track
        drawCircle(
            color = Color.White.copy(alpha = 0.1f), radius = radius, center = center,
            style = Stroke(strokeWidth)
        )
        // Arc
        if (fraction > 0f) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(strokeWidth, cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2)
            )
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(BgSurface)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        if (query.isEmpty()) {
            Text("🔍  Songs, Podcasts, Shows suchen...", color = TextTertiary, fontSize = 15.sp)
        }
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
            singleLine = true
        )
    }
}

@Composable
private fun EmptyState(icon: String, title: String, subtitle: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(icon, fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(title, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = TextTertiary, fontSize = 14.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Analytics Bottom Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnalyticsBottomSheet(
    target: Any,
    songAnalytics: Map<String, SongAnalytics>,
    podcastAnalytics: Map<String, PodcastAnalytics>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgSurface
    ) {
        when (target) {
            is MediaPlayerService.Song -> {
                val a = songAnalytics[target.path]
                SongAnalyticsContent(song = target, analytics = a)
            }

            is PodcastEpisode -> {
                val a = podcastAnalytics[target.path]
                PodcastAnalyticsContent(episode = target, analytics = a)
            }
        }
    }
}

@Composable
private fun SongAnalyticsContent(song: MediaPlayerService.Song, analytics: SongAnalytics?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(song.name, color = TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))

        if (analytics == null || analytics.playCount == 0) {
            Text("Noch nicht gespielt", color = TextTertiary, fontSize = 14.sp)
            Spacer(Modifier.height(32.dp))
            return@Column
        }

        Spacer(Modifier.height(16.dp))
        // Stats row
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem("▶️", "${analytics.playCount}", "Plays")
            StatItem("✅", "${(analytics.completionRate * 100).toInt()}%", "Completion")
            StatItem("⏭️", "${analytics.skipCount}", "Skips")
        }

        Spacer(Modifier.height(24.dp))
        // Plays by day of week bar chart
        Text(
            "Plays nach Wochentag",
            color = TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(12.dp))
        WeekdayBarChart(sessions = analytics.playHistory)

        Spacer(Modifier.height(24.dp))
        // Completion ring
        Text(
            "Ø Completion Rate",
            color = TextSecondary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompletionRing(fraction = analytics.completionRate, size = 72.dp, color = AccentViolet)
            Spacer(Modifier.width(16.dp))
            Text(
                "${(analytics.completionRate * 100).toInt()}%",
                color = TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(24.dp))
        // Last 10 sessions timeline
        if (analytics.playHistory.isNotEmpty()) {
            Text(
                "Letzte Sessions",
                color = TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            SessionsTimeline(sessions = analytics.playHistory.takeLast(10))
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PodcastAnalyticsContent(episode: PodcastEpisode, analytics: PodcastAnalytics?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        Text(
            episode.title,
            color = TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2
        )
        Spacer(Modifier.height(16.dp))

        if (analytics == null || analytics.playCount == 0) {
            Text("Noch nicht angehört", color = TextTertiary, fontSize = 14.sp)
            Spacer(Modifier.height(32.dp))
            return@Column
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            StatItem("▶️", "${analytics.playCount}", "Plays")
            StatItem("⏪", "${analytics.rewindCount}", "Zurückspulen")
            StatItem("⏩", "${analytics.forwardSkipCount}", "Vorspulen")
        }
        Spacer(Modifier.height(16.dp))
        Row {
            StatItem("⏱", formatDuration(analytics.totalListenedMs), "Gesamt gehört")
            Spacer(Modifier.width(24.dp))
            StatItem("🚀", "${String.format("%.1f", analytics.averageSpeed)}x", "Ø Geschwindigkeit")
        }

        if (episode.isCompleted) {
            Spacer(Modifier.height(12.dp))
            Text(
                "✓ Vollständig angehört",
                color = Color(0xFF43A047),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(Modifier.height(32.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Canvas Charts
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun WeekdayBarChart(sessions: List<PlaySession>) {
    val days = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    val counts = IntArray(7)
    sessions.forEach { s ->
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = s.startedAt
        val dow = (cal.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7 // 0=Mon
        counts[dow]++
    }
    val maxCount = counts.max().coerceAtLeast(1)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
    ) {
        val barWidth = size.width / 7f * 0.6f
        val gap = size.width / 7f
        counts.forEachIndexed { i, count ->
            val x = gap * i + gap * 0.2f
            val barHeight = (count.toFloat() / maxCount) * (size.height - 24.dp.toPx())
            val top = size.height - barHeight - 20.dp.toPx()
            drawRoundRect(
                color = if (count > 0) AccentViolet else BgCard,
                topLeft = Offset(x, top),
                size = Size(barWidth, barHeight.coerceAtLeast(4.dp.toPx())),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
            )
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        days.forEach { d ->
            Text(d, color = TextTertiary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SessionsTimeline(sessions: List<PlaySession>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        sessions.reversed().forEach { s ->
            val date = java.text.SimpleDateFormat("dd.MM · HH:mm", java.util.Locale.GERMANY)
                .format(java.util.Date(s.startedAt))
            val dur = formatDuration(s.durationMs)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BgCard)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(date, color = TextSecondary, fontSize = 12.sp)
                Text(dur, color = TextTertiary, fontSize = 12.sp)
                if (s.wasCompleted) Text("✓", color = Color(0xFF43A047), fontSize = 12.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Skeleton Loading
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SkeletonItem() {
    val infiniteTransition = rememberInfiniteTransition(label = "skeleton")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 0.7f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(BgCard.copy(alpha = alpha))
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(BgCard.copy(alpha = alpha))
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(BgCard.copy(alpha = alpha * 0.6f))
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun playSong(
    context: Context,
    song: MediaPlayerService.Song,
    allSongs: List<MediaPlayerService.Song>
) {
    val index = allSongs.indexOf(song) + 1
    MediaPlayerService.startAndPlayMusic(context, index.coerceAtLeast(1))

}

private fun playEpisode(context: Context, ep: PodcastEpisode) {
    // Switch to podcast mode and trigger selection via the service
    val intent = Intent(context, MediaPlayerService::class.java).apply {
        action = "SELECT_${ep.path.hashCode()}"
    }
    context.startService(intent)
}

private fun generateAccentColor(seed: String): Brush {
    val hash = seed.hashCode()
    val hue = ((hash and 0xFFFFFF) % 360).toFloat()
    val c1 = androidx.compose.ui.graphics.lerp(Color.hsl(hue, 0.6f, 0.25f), Color.Black, 0.2f)
    val c2 = Color.hsl((hue + 30) % 360, 0.5f, 0.35f)
    return Brush.linearGradient(listOf(c1, c2))
}

private fun generateShowGradient(seed: Int): Brush {
    val hue = ((seed and 0xFFFFFF) % 360).toFloat()
    return Brush.linearGradient(
        listOf(
            Color.hsl(hue, 0.7f, 0.3f),
            Color.hsl((hue + 60) % 360, 0.6f, 0.2f)
        )
    )
}

private fun formatDuration(ms: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (h > 0) "${h}h ${m}m" else if (m > 0) "${m}m ${s}s" else "${s}s"
}

// ─────────────────────────────────────────────────────────────────────────────
// Mini Player Bar (sits above BottomNavigation)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NowPlayingBar(
    nowPlaying: NowPlayingState,
    onOpenFullscreen: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!nowPlaying.isActive) return

    val accent = generateBarAccentColor(nowPlaying.title)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .clickable { onOpenFullscreen() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(listOf(accent.copy(alpha = 0.25f), BgSurface))
                )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth(nowPlaying.progress)
                .height(2.dp)
                .background(accent)
                .align(Alignment.TopStart)
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cover / avatar
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.4f)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (nowPlaying.mode == "podcast") "🎙️" else nowPlaying.title.take(1)
                        .uppercase(),
                    fontSize = if (nowPlaying.mode == "podcast") 22.sp else 18.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                MarqueeText(
                    text = nowPlaying.title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    nowPlaying.subtitle,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Play/Pause button (tap here does NOT open fullscreen)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.15f))
                    .clickable(onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (nowPlaying.isPlaying) "⏸" else "▶",
                    fontSize = 18.sp
                )
            }

            Spacer(Modifier.width(8.dp))

            // Skip/Next button (music only)
            if (nowPlaying.mode == "music") {
                Text(
                    "⏭",
                    fontSize = 22.sp,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { /* next */ }
                        .padding(4.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Fullscreen Player Sheet
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenPlayerSheet(
    nowPlaying: NowPlayingState,
    onDismiss: () -> Unit,
    onAction: (MediaViewModel.PlayerAction) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = BgDeep,
        dragHandle = null,
    ) {
        FullscreenPlayerContent(
            nowPlaying = nowPlaying,
            onDismiss = {
                scope.launch {
                    sheetState.hide()
                    onDismiss()
                }
            },
            onAction = onAction,
            modifier = Modifier.fillMaxHeight(0.95f)
        )
    }
}

@Composable
private fun FullscreenPlayerContent(
    nowPlaying: NowPlayingState,
    onDismiss: () -> Unit,
    onAction: (MediaViewModel.PlayerAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = generateBarAccentColor(nowPlaying.title)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(
                        accent.copy(alpha = 0.3f),
                        BgDeep,
                        BgDeep
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.height(16.dp))

            // Drag handle + dismiss
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(TextTertiary)
                    .clickable { onDismiss() }
            )

            Spacer(Modifier.height(24.dp))

            // Mode badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(accent.copy(alpha = 0.2f))
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    if (nowPlaying.mode == "podcast") "🎙️  PODCAST" else "🎵  MUSIK",
                    color = accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                )
            }

            Spacer(Modifier.height(40.dp))

            // Large cover art / avatar
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                accent,
                                accent.copy(alpha = 0.5f),
                                BgCard
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (nowPlaying.mode == "podcast") {
                    Text("🎙️", fontSize = 80.sp)
                } else {
                    Text(
                        nowPlaying.title.take(1).uppercase(),
                        fontSize = 96.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            // Title + subtitle
            MarqueeText(
                text = nowPlaying.title,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Text(
                nowPlaying.subtitle,
                color = TextSecondary,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(32.dp))

            // Progress / Seekbar
            PlayerProgressRow(nowPlaying = nowPlaying)

            Spacer(Modifier.height(32.dp))

            // Controls
            if (nowPlaying.mode == "podcast") {
                PodcastControls(
                    isPlaying = nowPlaying.isPlaying,
                    accentColor = accent,
                    onAction = onAction
                )
            } else {
                MusicControls(
                    isPlaying = nowPlaying.isPlaying,
                    accentColor = accent,
                    onAction = onAction
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PlayerProgressRow(nowPlaying: NowPlayingState) {
    val posText = formatMs(nowPlaying.positionMs)
    val durText = if (nowPlaying.durationMs > 0) formatMs(nowPlaying.durationMs) else "--:--"

    Column {
        // Track bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(BgCard)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(nowPlaying.progress)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(AccentViolet, AccentViolet.copy(alpha = 0.7f))
                        )
                    )
                    .clip(CircleShape)
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(posText, color = TextTertiary, fontSize = 12.sp)
            Text(durText, color = TextTertiary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PodcastControls(
    isPlaying: Boolean,
    accentColor: Color,
    onAction: (MediaViewModel.PlayerAction) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // -15s
        ControlButton(
            label = "-15",
            sublabel = "sek",
            size = 56.dp,
            bgColor = BgCard,
            onClick = { onAction(MediaViewModel.PlayerAction.REWIND_15) }
        )

        // Play/Pause (large)
        ControlButton(
            label = if (isPlaying) "⏸" else "▶",
            size = 80.dp,
            fontSize = 30.sp,
            bgColor = accentColor,
            onClick = { onAction(MediaViewModel.PlayerAction.PLAY_PAUSE) }
        )

        // +15s
        ControlButton(
            label = "+15",
            sublabel = "sek",
            size = 56.dp,
            bgColor = BgCard,
            onClick = { onAction(MediaViewModel.PlayerAction.FORWARD_15) }
        )
    }
}

@Composable
private fun MusicControls(
    isPlaying: Boolean,
    accentColor: Color,
    onAction: (MediaViewModel.PlayerAction) -> Unit
) {
    var isPlaying = isPlaying
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControlButton(
            "⏮",
            size = 56.dp,
            fontSize = 22.sp,
            bgColor = BgCard
        ) { onAction(MediaViewModel.PlayerAction.PREVIOUS) }
        ControlButton(
            if (isPlaying) "⏸" else "▶",
            size = 80.dp, fontSize = 30.sp, bgColor = accentColor
        ) {
            onAction(MediaViewModel.PlayerAction.PLAY_PAUSE)
        }
        ControlButton(
            "⏭",
            size = 56.dp,
            fontSize = 22.sp,
            bgColor = BgCard
        ) { onAction(MediaViewModel.PlayerAction.NEXT) }
    }
}

@Composable
private fun ControlButton(
    label: String,
    sublabel: String? = null,
    size: androidx.compose.ui.unit.Dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 16.sp,
    bgColor: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(bgColor)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(label, fontSize = fontSize, color = TextPrimary, fontWeight = FontWeight.Bold)
        }
        if (sublabel != null) {
            Spacer(Modifier.height(4.dp))
            Text(sublabel, color = TextTertiary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun MarqueeText(
    text: String,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontWeight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier
) {
    val scrollX = remember { Animatable(0f) }
    LaunchedEffect(text) {
        while (true) {
            scrollX.animateTo(
                -400f, animationSpec = tween(6000, easing = LinearEasing, delayMillis = 1500)
            )
            scrollX.snapTo(0f)
            kotlinx.coroutines.delay(500)
        }
    }
    Box(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun generateBarAccentColor(seed: String): Color {
    val hash = seed.hashCode()
    val hue = ((hash and 0xFFFFFF) % 360).toFloat()
    return Color.hsl(hue, 0.7f, 0.55f)
}