package com.example.cloud.mediaplayer

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cloud.service.MediaPlayerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

data class MediaUiState(
    val songs: List<MediaPlayerService.Song> = emptyList(),
    val episodes: List<PodcastEpisode> = emptyList(),
    val shows: List<PodcastShow> = emptyList(),
    val songAnalytics: Map<String, SongAnalytics> = emptyMap(),
    val podcastAnalytics: Map<String, PodcastAnalytics> = emptyMap(),
    val userPlaylists: List<MediaPlayerService.Playlist> = emptyList(),
    val algorithmicPlaylists: List<Pair<PlaylistSource, List<MediaPlayerService.Song>>> = emptyList(),
    val globalStats: MediaAnalyticsManager.GlobalStats = MediaAnalyticsManager.GlobalStats(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val currentTab: MediaTab = MediaTab.HOME
)

enum class MediaTab { HOME, SEARCH, LIBRARY }

data class SearchResults(
    val songs: List<MediaPlayerService.Song> = emptyList(),
    val episodes: List<PodcastEpisode> = emptyList(),
    val shows: List<PodcastShow> = emptyList()
)

// ─────────────────────────────────────────────────────────────────────────────
// ViewModel
// ─────────────────────────────────────────────────────────────────────────────

class MediaViewModel(app: Application) : AndroidViewModel(app) {

    private val _uiState = MutableStateFlow(MediaUiState(isLoading = true))
    val uiState: StateFlow<MediaUiState> = _uiState.asStateFlow()

    private val _nowPlaying = MutableStateFlow(NowPlayingState())
    val nowPlaying: StateFlow<NowPlayingState> = _nowPlaying.asStateFlow()

    val searchResults: StateFlow<SearchResults> = _uiState.map { state ->
        val q = state.searchQuery.lowercase().trim()
        if (q.length < 2) return@map SearchResults()
        SearchResults(
            songs = state.songs.filter { it.name.lowercase().contains(q) },
            episodes = state.episodes.filter {
                it.title.lowercase().contains(q) || it.path.lowercase().contains(q)
            },
            shows = state.shows.filter { it.name.lowercase().contains(q) }
        )
    }.stateIn(viewModelScope, SharingStarted.Lazily, SearchResults())

    init {
        MediaAnalyticsManager.init(app)
        PodcastShowManager.init(app)
        refresh()
        startNowPlayingPoller()
    }

    fun startNowPlayingPoller() {
        viewModelScope.launch {
            while (isActive) {
                _nowPlaying.value = readNowPlayingFromPrefs()
                delay(1000)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val songs = withContext(Dispatchers.IO) { loadSongsFromMediaStore() }
            val rawEpisodes = withContext(Dispatchers.IO) { loadEpisodesFromMediaStore() }
            val songAnalytics =
                withContext(Dispatchers.IO) { MediaAnalyticsManager.getAllSongAnalytics() }
            val podcastAnalytics =
                withContext(Dispatchers.IO) { MediaAnalyticsManager.getAllPodcastAnalytics() }
            val shows = PodcastShowManager.getShows()
            val globalStats = withContext(Dispatchers.IO) { MediaAnalyticsManager.getGlobalStats() }

            // Resolve show for each episode
            val episodes = rawEpisodes.map { ep ->
                val showId = PodcastShowManager.resolveShowForEpisode(ep.path, ep.title)
                ep.copy(showId = showId)
            }

            // Build algorithmic playlists (only non-empty)
            val algPlaylists = AlgorithmicPlaylistRegistry.all.map { source ->
                source to source.getSongs(songs, songAnalytics)
            }.filter { (_, songs) -> songs.isNotEmpty() }

            _uiState.value = MediaUiState(
                songs = songs,
                episodes = episodes,
                shows = shows,
                songAnalytics = songAnalytics,
                podcastAnalytics = podcastAnalytics,
                algorithmicPlaylists = algPlaylists,
                globalStats = globalStats,
                isLoading = false
            )
        }
    }

    fun setSearchQuery(q: String) {
        _uiState.value = _uiState.value.copy(searchQuery = q)
    }

    fun setTab(tab: MediaTab) {
        _uiState.value = _uiState.value.copy(currentTab = tab)
    }

    // ── MediaStore Loaders ────────────────────────────────────────────────────

    private fun loadSongsFromMediaStore(): List<MediaPlayerService.Song> {
        val songs = mutableListOf<MediaPlayerService.Song>()
        val cr: ContentResolver = getApplication<Application>().contentResolver
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE
        )
        cr.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, null, null,
            "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: continue
                val data = cursor.getString(dataCol) ?: continue
                val title = cursor.getString(titleCol)
                val norm = try {
                    URLDecoder.decode(data, "UTF-8").replace("\\", "/").lowercase()
                } catch (_: Exception) {
                    data.lowercase()
                }
                val inCloud = norm.contains("/download/cloud/") ||
                        norm.contains("/downloads/cloud/") ||
                        data.contains("/Cloud/", ignoreCase = true)
                if (inCloud && !norm.contains("/podcast")) {
                    val contentUri = Uri.withAppendedPath(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    val displayName =
                        if (!title.isNullOrBlank() && title != "<unknown>") title else name.substringBeforeLast(
                            '.'
                        )
                    songs.add(MediaPlayerService.Song(contentUri, displayName, data))
                }
            }
        }
        return songs.sortedBy { it.name.lowercase() }
    }

    private fun loadEpisodesFromMediaStore(): List<PodcastEpisode> {
        val episodes = mutableListOf<PodcastEpisode>()
        val cr: ContentResolver = getApplication<Application>().contentResolver
        val podcastPrefs = getApplication<Application>().getSharedPreferences(
            "podcast_player_prefs",
            Context.MODE_PRIVATE
        )
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DURATION
        )
        cr.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, null, null,
            "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
        )?.use { cursor ->
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameCol) ?: continue
                val data = cursor.getString(dataCol) ?: continue
                val title = cursor.getString(titleCol)
                val duration = if (durCol >= 0) cursor.getLong(durCol) else 0L
                val norm = try {
                    URLDecoder.decode(data, "UTF-8").replace("\\", "/").lowercase()
                } catch (_: Exception) {
                    data.lowercase()
                }
                val inPodcasts = norm.contains("/download/cloud/podcasts/") ||
                        norm.contains("/downloads/cloud/podcasts/") ||
                        data.contains("/Cloud/Podcasts/", ignoreCase = true)
                if (inPodcasts && (name.endsWith(".mp3") || name.endsWith(".m4a"))) {
                    val displayName =
                        if (!title.isNullOrBlank() && title != "<unknown>") title else name.substringBeforeLast(
                            '.'
                        )
                    val savedPos = podcastPrefs.getLong("podcast_position_${data.hashCode()}", 0L)
                    val isCompleted =
                        podcastPrefs.getBoolean("podcast_completed_${data.hashCode()}", false)
                    episodes.add(
                        PodcastEpisode(
                            data,
                            displayName,
                            "unassigned",
                            null,
                            duration,
                            savedPos,
                            isCompleted
                        )
                    )
                }
            }
        }
        return episodes.sortedBy { it.title.lowercase() }
    }

    private fun readNowPlayingFromPrefs(): NowPlayingState {
        val app = getApplication<Application>()
        if (!MediaPlayerService.isServiceActive()) return NowPlayingState()

        val musicPrefs = app.getSharedPreferences("music_player_prefs", Context.MODE_PRIVATE)
        val podcastPrefs = app.getSharedPreferences("podcast_player_prefs", Context.MODE_PRIVATE)
        val mode = musicPrefs.getString("current_mode", "music") ?: "music"

        return if (mode == "music") {
            val idx = musicPrefs.getInt("current_song_index", 0)
            val song = _uiState.value.songs.getOrNull(idx)
            NowPlayingState(
                isActive = song != null,
                mode = "music",
                title = song?.name ?: "",
                subtitle = "Musik",
                isPlaying = musicPrefs.getBoolean("is_playing", false),
                positionMs = 0L,
                durationMs = 0L,
                progress = 0f
            )
        } else {
            val path = podcastPrefs.getString("current_podcast_path", null)
            val pos = if (path != null) podcastPrefs.getLong(
                "podcast_position_${path.hashCode()}",
                0L
            ) else 0L
            val ep = _uiState.value.episodes.find { it.path == path }
            val show = _uiState.value.shows.find { it.id == ep?.showId }
            val progress =
                if (ep != null && ep.durationMs > 0) (pos.toFloat() / ep.durationMs).coerceIn(
                    0f,
                    1f
                ) else 0f
            NowPlayingState(
                isActive = ep != null,
                mode = "podcast",
                title = ep?.title ?: "",
                subtitle = show?.name ?: "Podcast",
                isPlaying = musicPrefs.getBoolean("is_playing", false),
                positionMs = pos,
                durationMs = ep?.durationMs ?: 0L,
                progress = progress
            )
        }
    }

    fun playerAction(context: Context, action: PlayerAction) {
        when (action) {
            PlayerAction.PLAY_PAUSE -> {
                val np = _nowPlaying.value
                if (np.mode == "music") {
                    if (np.isPlaying) context.startService(
                        Intent(context, MediaPlayerService::class.java).apply {
                            this.action = "com.example.cloud.ACTION_MUSIC_PAUSE"
                        }
                    ) else MediaPlayerService.sendMusicPlayAction(context)
                } else {
                    if (np.isPlaying) context.startService(
                        Intent(context, MediaPlayerService::class.java).apply {
                            this.action = "com.example.cloud.ACTION_PODCAST_PAUSE"
                        }
                    ) else MediaPlayerService.sendPodcastPlayAction(context)
                }
            }

            PlayerAction.NEXT -> context.startService(
                Intent(context, MediaPlayerService::class.java).apply {
                    this.action = "com.example.cloud.ACTION_MUSIC_NEXT"
                }
            )

            PlayerAction.PREVIOUS -> context.startService(
                Intent(context, MediaPlayerService::class.java).apply {
                    this.action = "com.example.cloud.ACTION_MUSIC_PREVIOUS"
                }
            )

            PlayerAction.REWIND_15 -> MediaPlayerService.sendPodcastForwardAction(context, -15_000)
            PlayerAction.FORWARD_15 -> MediaPlayerService.sendPodcastForwardAction(context, 15_000)
        }
    }

    enum class PlayerAction { PLAY_PAUSE, NEXT, PREVIOUS, REWIND_15, FORWARD_15 }
}