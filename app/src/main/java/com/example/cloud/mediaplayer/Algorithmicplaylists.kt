package com.example.cloud.mediaplayer

import androidx.compose.ui.graphics.Color
import com.example.cloud.service.MediaPlayerService

// ─────────────────────────────────────────────────────────────────────────────
// Types
// ─────────────────────────────────────────────────────────────────────────────

enum class PlaylistSourceType { ALGORITHMIC, USER_CREATED }

interface PlaylistSource {
    val id: String
    val name: String
    val description: String
    val type: PlaylistSourceType
    val icon: String
    val accentColor: Color
    fun getSongs(
        allSongs: List<MediaPlayerService.Song>,
        analytics: Map<String, SongAnalytics>
    ): List<MediaPlayerService.Song>
}

// ─────────────────────────────────────────────────────────────────────────────
// Built-in Algorithmic Playlists
// ─────────────────────────────────────────────────────────────────────────────

object RecentlyPlayedPlaylist : PlaylistSource {
    override val id = "recently_played"
    override val name = "Zuletzt gespielt"
    override val description = "Deine letzten 20 Songs"
    override val type = PlaylistSourceType.ALGORITHMIC
    override val icon = "🕐"
    override val accentColor = Color(0xFF7C4DFF)

    override fun getSongs(
        allSongs: List<MediaPlayerService.Song>,
        analytics: Map<String, SongAnalytics>
    ) = allSongs
        .filter { (analytics[it.path]?.lastPlayedAt ?: 0L) > 0L }
        .sortedByDescending { analytics[it.path]?.lastPlayedAt ?: 0L }
        .take(20)
}

object NeverPlayedPlaylist : PlaylistSource {
    override val id = "never_played"
    override val name = "Noch nie gehört"
    override val description = "Songs die auf ihre Chance warten"
    override val type = PlaylistSourceType.ALGORITHMIC
    override val icon = "🌱"
    override val accentColor = Color(0xFF00BFA5)

    override fun getSongs(
        allSongs: List<MediaPlayerService.Song>,
        analytics: Map<String, SongAnalytics>
    ) = allSongs
        .filter { (analytics[it.path]?.playCount ?: 0) == 0 }
        .shuffled()
        .take(30)
}

object HighCompletionPlaylist : PlaylistSource {
    override val id = "high_completion"
    override val name = "Bis zum Ende"
    override val description = "Songs die du meist vollständig hörst"
    override val type = PlaylistSourceType.ALGORITHMIC
    override val icon = "✅"
    override val accentColor = Color(0xFF43A047)

    override fun getSongs(
        allSongs: List<MediaPlayerService.Song>,
        analytics: Map<String, SongAnalytics>
    ) = allSongs
        .filter { (analytics[it.path]?.playCount ?: 0) >= 2 }
        .sortedByDescending { analytics[it.path]?.completionRate ?: 0f }
        .take(25)
}

object TopSkippedPlaylist : PlaylistSource {
    override val id = "top_skipped"
    override val name = "Oft geskippt"
    override val description = "Vielleicht ein zweiter Chance?"
    override val type = PlaylistSourceType.ALGORITHMIC
    override val icon = "⏭️"
    override val accentColor = Color(0xFFFF6F00)

    override fun getSongs(
        allSongs: List<MediaPlayerService.Song>,
        analytics: Map<String, SongAnalytics>
    ) = allSongs
        .filter { (analytics[it.path]?.skipCount ?: 0) >= 1 }
        .sortedByDescending { analytics[it.path]?.skipCount ?: 0 }
        .take(20)
}

object LateNightPlaylist : PlaylistSource {
    override val id = "late_night"
    override val name = "Nacht-Modus"
    override val description = "Deine Songs für die späten Stunden"
    override val type = PlaylistSourceType.ALGORITHMIC
    override val icon = "🌙"
    override val accentColor = Color(0xFF283593)

    override fun getSongs(
        allSongs: List<MediaPlayerService.Song>,
        analytics: Map<String, SongAnalytics>
    ): List<MediaPlayerService.Song> {
        val nightHours = setOf(22, 23, 0, 1, 2, 3, 4, 5)
        return allSongs
            .filter { song ->
                val a = analytics[song.path] ?: return@filter false
                val nightPlays = nightHours.sumOf { a.hourlyPlayCount[it] ?: 0 }
                nightPlays > 0
            }
            .sortedByDescending { song ->
                val a = analytics[song.path] ?: return@sortedByDescending 0
                nightHours.sumOf { a.hourlyPlayCount[it] ?: 0 }
            }
            .take(25)
    }
}

object MostPlayedPlaylist : PlaylistSource {
    override val id = "most_played"
    override val name = "Meistgespielt"
    override val description = "Deine absoluten Lieblinge"
    override val type = PlaylistSourceType.ALGORITHMIC
    override val icon = "🔥"
    override val accentColor = Color(0xFFE53935)

    override fun getSongs(
        allSongs: List<MediaPlayerService.Song>,
        analytics: Map<String, SongAnalytics>
    ) = allSongs
        .filter { (analytics[it.path]?.playCount ?: 0) > 0 }
        .sortedByDescending { analytics[it.path]?.playCount ?: 0 }
        .take(25)
}

object FavoritesPlaylist : PlaylistSource {
    override val id = "favorites"
    override val name = "Favoriten"
    override val description = "Deine markierten Lieblinge"
    override val type = PlaylistSourceType.ALGORITHMIC
    override val icon = "⭐"
    override val accentColor = Color(0xFFFDD835)

    override fun getSongs(
        allSongs: List<MediaPlayerService.Song>,
        analytics: Map<String, SongAnalytics>
    ) = allSongs
        .filter { analytics[it.path]?.isFavorite == true }
        .sortedByDescending { analytics[it.path]?.favoritedAt ?: 0L }
}

// ─────────────────────────────────────────────────────────────────────────────
// Registry
// ─────────────────────────────────────────────────────────────────────────────

object AlgorithmicPlaylistRegistry {
    val all: List<PlaylistSource> = listOf(
        RecentlyPlayedPlaylist,
        MostPlayedPlaylist,
        FavoritesPlaylist,
        HighCompletionPlaylist,
        NeverPlayedPlaylist,
        LateNightPlaylist,
        TopSkippedPlaylist
    )

    fun findById(id: String): PlaylistSource? = all.find { it.id == id }
}