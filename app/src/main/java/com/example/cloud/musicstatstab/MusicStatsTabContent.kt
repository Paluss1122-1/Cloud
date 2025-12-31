package com.example.cloud.musicstatstab

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicStatsTabContent(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val statsManager = remember { MusicStatsManager(context) }

    var searchQuery by remember { mutableStateOf("") }
    var songStats by remember { mutableStateOf<List<SongStats>>(emptyList()) }
    var sortMode by remember { mutableStateOf(0) } // 0=PlayCount, 1=PlayTime, 2=Recent, 3=Skipped, 4=Paused
    var isRefreshing by remember { mutableStateOf(false) }

    val loadStats = {
        songStats = when (sortMode) {
            0 -> statsManager.getSortedByPlayCount()
            1 -> statsManager.getSortedByTotalPlayTime()
            2 -> statsManager.getSortedByMostRecent()
            3 -> statsManager.getMostSkipped()
            4 -> statsManager.getMostPausedSongs()
            else -> statsManager.getSortedByPlayCount()
        }
    }

    LaunchedEffect(Unit) {
        loadStats()
    }

    LaunchedEffect(sortMode) {
        loadStats()
    }

    val filteredStats = if (searchQuery.isEmpty()) {
        songStats
    } else {
        songStats.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    val pullRefreshState = rememberPullToRefreshState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .pullToRefresh(
                isRefreshing = isRefreshing,
                onRefresh = {
                    isRefreshing = true
                    Thread {
                        Thread.sleep(100)
                        loadStats()
                        isRefreshing = false
                    }.start()
                },
                state = pullRefreshState
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1a1a1a))
                .padding(16.dp)
        ) {
            SearchField(
                query = searchQuery,
                onQueryChange = { searchQuery = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Stats Übersicht
            StatsOverview(statsManager)

            Spacer(modifier = Modifier.height(20.dp))

            // Sort Buttons
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(5) { index ->
                    val labels = listOf("Plays", "Zeit", "Recent", "Skips", "Pauses")
                    FilterChip(
                        selected = sortMode == index,
                        onClick = { sortMode = index },
                        label = { Text(labels[index], fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF4CAF50),
                            selectedLabelColor = Color.White,
                            containerColor = Color(0xFF2a2a2a),
                            labelColor = Color.Gray
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Song Liste
            if (filteredStats.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentSize(Alignment.Center)
                ) {
                    Text(
                        "Keine Songs gefunden",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(filteredStats) { stat ->
                        SongStatCard(stat)
                    }
                }
            }
        }

        // Refresh Indicator oben anzeigen
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp),
                color = Color(0xFF4CAF50)
            )
        }
    }
}

@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        placeholder = { Text("Song suchen...", color = Color.Gray) },
        leadingIcon = {
            Icon(
                Icons.Default.Search,
                contentDescription = "Suchen",
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        },
        singleLine = true,
        colors = TextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Color(0xFF4CAF50),
            focusedContainerColor = Color(0xFF2a2a2a),
            unfocusedContainerColor = Color(0xFF2a2a2a),
            focusedIndicatorColor = Color(0xFF4CAF50),
            unfocusedIndicatorColor = Color(0xFF333333)
        ),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
fun StatsOverview(statsManager: MusicStatsManager) {
    val totalStats = statsManager.getTotalStats()

    val totalSongs = when (val value = totalStats["totalSongs"]) {
        is Long -> value.toInt()
        is Int -> value
        else -> 0
    }
    val totalPlays = when (val value = totalStats["totalPlays"]) {
        is Long -> value.toInt()
        is Int -> value
        else -> 0
    }
    val totalPlayTimeMs = when (val value = totalStats["totalPlayTimeMs"]) {
        is Long -> value
        is Int -> value.toLong()
        else -> 0L
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color(0xFF2a2a2a),
                RoundedCornerShape(16.dp)
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem("Songs", totalSongs.toString(), "🎼")
        StatItem("Plays", totalPlays.toString(), "▶️")
        StatItem("Zeit", formatPlayTime(totalPlayTimeMs), "⏱️")
    }
}

@Composable
fun StatItem(
    label: String,
    value: String,
    icon: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            icon,
            fontSize = 24.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50)
        )
        Text(
            label,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun SongStatCard(stat: SongStats) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF252525)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Songtitel mit Rang
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        stat.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        stat.path.substringBeforeLast('/'),
                        fontSize = 12.sp,
                        color = Color.Gray,
                        maxLines = 1
                    )
                }

                // Play Count Badge
                Surface(
                    color = Color(0xFF4CAF50),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        "${stat.playCount}x",
                        modifier = Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 4.dp
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            Divider(
                color = Color(0xFF3a3a3a),
                thickness = 0.5.dp,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            // Completion Rate Progress Bar
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Completion",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Text(
                        "%.0f%%".format(stat.completionRate),
                        fontSize = 11.sp,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.Bold
                    )
                }
                LinearProgressIndicator(
                    progress = { stat.completionRate / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Color(0xFF4CAF50),
                    trackColor = Color(0xFF333333)
                )
            }

            // Stats Grid 3x2
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Row 1
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatDetail(
                        icon = "▶️",
                        label = "Gespielt",
                        value = formatPlayTime(stat.totalTimePlayedMs),
                        modifier = Modifier.weight(1f)
                    )
                    StatDetail(
                        icon = "⏱️",
                        label = "Länge",
                        value = formatPlayTime(stat.duration),
                        modifier = Modifier.weight(1f)
                    )
                    StatDetail(
                        icon = "🔁",
                        label = "Längste",
                        value = formatPlayTime(stat.longestSessionMs),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 2
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatDetail(
                        icon = "⏸️",
                        label = "Pausiert",
                        value = stat.pauseCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    StatDetail(
                        icon = "⏭️",
                        label = "Übersprungen",
                        value = stat.skipCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    StatDetail(
                        icon = "❌",
                        label = "Unterbrochen",
                        value = stat.interruptCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }

                // Row 3
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatDetail(
                        icon = "🕐",
                        label = "Zuletzt",
                        value = formatLastPlayed(stat.lastPlayedTime),
                        modifier = Modifier.weight(1f)
                    )
                    StatDetail(
                        icon = "📍",
                        label = "Ø Position",
                        value = formatPlayTime(stat.averagePlaybackPosition),
                        modifier = Modifier.weight(1f)
                    )
                    StatDetail(
                        icon = "🛑",
                        label = "Abrupte Stopps",
                        value = stat.abruptStopsCount.toString(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun StatDetail(
    icon: String,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(Color(0xFF1f1f1f), RoundedCornerShape(8.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            icon,
            fontSize = 16.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            label,
            fontSize = 10.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Text(
            value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4CAF50),
            modifier = Modifier.padding(top = 4.dp),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

fun formatPlayTime(ms: Long): String {
    return when {
        ms < 1000 -> "0s"
        ms < 60000 -> "${ms / 1000}s"
        ms < 3600000 -> "${ms / 60000}m ${(ms % 60000) / 1000}s"
        else -> "${ms / 3600000}h ${(ms % 3600000) / 60000}m"
    }
}

fun formatLastPlayed(timestamp: Long): String {
    return if (timestamp == 0L) {
        "—"
    } else {
        val diff = System.currentTimeMillis() - timestamp
        when {
            diff < 60000 -> "gerade eben"
            diff < 3600000 -> "${diff / 60000}m"
            diff < 86400000 -> "${diff / 3600000}h"
            diff < 604800000 -> "${diff / 86400000}d"
            else -> SimpleDateFormat("dd.MM.", Locale.getDefault()).format(Date(timestamp))
        }
    }
}