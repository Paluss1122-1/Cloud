package com.cloud.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.KeyEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.android.identity.util.UUID
import com.cloud.MainActivity
import com.cloud.mediaplayer.AlgorithmicPlaylistRegistry
import com.cloud.mediaplayer.FavoritesPlaylist
import com.cloud.mediaplayer.ListenSession
import com.cloud.mediaplayer.MediaAnalyticsManager
import com.cloud.mediaplayer.PodcastShowManager
import com.cloud.quiethoursnotificationhelper.pushMediaStateToLaptop
import com.cloud.showSimpleNotificationExtern
import java.io.File
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

@OptIn(UnstableApi::class)
class MediaPlayerService : MediaSessionService() {
    companion object {
        const val MODE_MUSIC = "music"
        const val MODE_PODCAST = "podcast"

        const val EXTRA_FORWARD_MS = "extra_forward_ms"
        const val EXTRA_SONG_INDEX = "extra_song_index"

        private const val CHANNEL_ID = "media_player_channel"
        private const val NOTIFICATION_ID = 888888

        private const val ACTION_MUSIC_PLAY = "com.cloud.ACTION_MUSIC_PLAY"
        private const val ACTION_MUSIC_PAUSE = "com.cloud.ACTION_MUSIC_PAUSE"
        private const val ACTION_MUSIC_NEXT = "com.cloud.ACTION_MUSIC_NEXT"
        private const val ACTION_MUSIC_PREVIOUS = "com.cloud.ACTION_MUSIC_PREVIOUS"
        private const val ACTION_TOGGLE_REPEAT = "com.cloud.ACTION_TOGGLE_REPEAT"
        private const val ACTION_TOGGLE_FAVORITE = "com.cloud.ACTION_TOGGLE_FAVORITE"
        const val ACTION_TOGGLE_FAVORITES_MODE = "TOGGLE_FAVORITES_MODE"
        const val ACTION_SHOW_FAVORITES = "SHOW_FAVORITES"

        private const val ACTION_PODCAST_PLAY = "com.cloud.ACTION_PODCAST_PLAY"
        private const val ACTION_PODCAST_PAUSE = "com.cloud.ACTION_PODCAST_PAUSE"
        private const val ACTION_PODCAST_REWIND = "com.cloud.ACTION_PODCAST_REWIND"
        private const val ACTION_PODCAST_FORWARD = "com.cloud.ACTION_PODCAST_FORWARD"
        private const val ACTION_SELECT_PODCAST = "com.cloud.ACTION_SELECT_PODCAST"
        private const val ACTION_DELETE_SINGLE = "com.cloud.ACTION_DELETE_SINGLE_"
        const val ACTION_SHOW_DELETE_COMPLETED = "ACTION_SHOW_DELETE_COMPLETED"
        const val ACTION_SET_SPEED = "ACTION_SET_SPEED"
        const val EXTRA_SPEED = "SPEED"

        const val ACTION_SWITCH_TO_MUSIC = "com.cloud.ACTION_SWITCH_TO_MUSIC"
        const val ACTION_SWITCH_TO_PODCAST = "com.cloud.ACTION_SWITCH_TO_PODCAST"

        private const val ACTION_NOTIFICATION_DELETED = "ACTION_NOTIFICATION_DELETED"

        private const val MUSIC_PREFS = "music_player_prefs"
        private const val PODCAST_PREFS = "podcast_player_prefs"
        private const val KEY_CURRENT_MODE = "current_mode"
        private const val KEY_CURRENT_SONG_INDEX = "current_song_index"
        private const val KEY_REPEAT_MODE = "repeat_mode"
        private const val KEY_FAVORITES = "favorite_songs"
        private const val KEY_FAVORITES_MODE = "favorites_only_mode"
        private const val KEY_PREFIX_POSITION = "podcast_position_"
        private const val KEY_CURRENT_PODCAST = "current_podcast_path"
        private const val KEY_PREFIX_COMPLETED = "podcast_completed_"
        private const val KEY_PODCAST_QUEUE = "podcast_queue"
        private const val KEY_PLAYBACK_SPEED = "podcast_playback_speed"

        private const val SKIP_TIME_MS = 15000
        private const val SERVICE_SWITCH_TIMEOUT = 20000L

        private const val KEY_PLAYLISTS = "playlists_json"
        private const val KEY_ACTIVE_PLAYLIST = "active_playlist_id"

        private const val KEY_ACTIVE_ALGORITHMIC_PLAYLIST = "active_algorithmic_playlist_id"
        const val ACTION_ACTIVATE_ALGORITHMIC_PLAYLIST = "com.cloud.ACTION_ACTIVATE_ALG_PLAYLIST"
        const val EXTRA_PLAYLIST_SOURCE_ID = "PLAYLIST_SOURCE_ID"

        const val ACTION_PLAY_ALL_SONGS_AT_INDEX = "com.cloud.ACTION_PLAY_ALL_SONGS_AT_INDEX"

        fun playFromAllSongs(context: Context, index: Int) = context.startForegroundService(
            Intent(context, MediaPlayerService::class.java).apply {
                action = ACTION_PLAY_ALL_SONGS_AT_INDEX
                putExtra(EXTRA_SONG_INDEX, index)
            }
        )

        fun activateAlgorithmicPlaylist(context: Context, sourceId: String, songIndex: Int = 0) =
            context.startService(
                Intent(context, MediaPlayerService::class.java).apply {
                    action = ACTION_ACTIVATE_ALGORITHMIC_PLAYLIST
                    putExtra(EXTRA_PLAYLIST_SOURCE_ID, sourceId)
                    putExtra(EXTRA_SONG_INDEX, songIndex)
                }
            )

        fun startMusicService(context: Context) = context.startForegroundService(
            Intent(context, MediaPlayerService::class.java).apply {
                action = ACTION_SWITCH_TO_MUSIC
            }
        )

        fun startAndPlayMusic(context: Context, number: Int? = null) =
            context.startForegroundService(
                Intent(context, MediaPlayerService::class.java).apply {
                    action = ACTION_MUSIC_PLAY
                    if (number != null && number > 0) putExtra(EXTRA_SONG_INDEX, number)
                }
            )

        fun sendMusicPlayAction(context: Context) = context.startService(
            Intent(context, MediaPlayerService::class.java).apply { action = ACTION_MUSIC_PLAY }
        )

        fun toggleFavorite(context: Context) = context.startService(
            Intent(context, MediaPlayerService::class.java).apply {
                action = ACTION_TOGGLE_FAVORITE
            }
        )

        fun toggleFavoritesMode(context: Context) = context.startService(
            Intent(context, MediaPlayerService::class.java).apply {
                action = ACTION_TOGGLE_FAVORITES_MODE
            }
        )

        fun showFavorites(context: Context) = context.startService(
            Intent(context, MediaPlayerService::class.java).apply { action = ACTION_SHOW_FAVORITES }
        )

        fun startPodcastService(context: Context) = context.startForegroundService(
            Intent(context, MediaPlayerService::class.java).apply {
                action = ACTION_SWITCH_TO_PODCAST
            }
        )

        fun sendPodcastPlayAction(context: Context) = context.startService(
            Intent(context, MediaPlayerService::class.java).apply { action = ACTION_PODCAST_PLAY }
        )

        fun sendPodcastForwardAction(context: Context, ms: Int) = context.startService(
            Intent(context, MediaPlayerService::class.java).apply {
                action = ACTION_PODCAST_FORWARD
                putExtra(EXTRA_FORWARD_MS, ms)
            }
        )

        fun managePodcast(context: Context) {
            if (isServiceActive()) {
                context.startService(
                    Intent(context, MediaPlayerService::class.java).apply {
                        action = ACTION_SHOW_DELETE_COMPLETED
                    }
                )
            } else {
                showCompletedPodcastsWithoutService(context)
            }
        }

        @Volatile
        private var isRunning = false

        fun isServiceActive() = isRunning

        @SuppressLint("LaunchActivityFromNotification")
        private fun showCompletedPodcastsWithoutService(context: Context) {
            val podcastPrefs: SharedPreferences = context.getSharedPreferences(
                "podcast_player_prefs",
                MODE_PRIVATE
            )
            val nm: NotificationManager? = context.getSystemService(NotificationManager::class.java)

            val proj = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.TITLE
            )
            val completedPodcasts = mutableListOf<Pair<String, String>>()

            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                proj, null, null,
                "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val nameCol =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val dataCol =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val titleCol =
                    cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                while (cursor.moveToNext()) {
                    val name: String = cursor.getString(nameCol) ?: continue
                    val data: String = cursor.getString(dataCol) ?: continue
                    val title: String? = cursor.getString(titleCol)
                    val norm: String = try {
                        URLDecoder.decode(data, "UTF-8").replace("\\", "/").lowercase()
                    } catch (_: Exception) {
                        data.replace("\\", "/").lowercase()
                    }
                    val inPodcasts = norm.contains("/download/cloud/podcasts/") ||
                            norm.contains("/downloads/cloud/podcasts/") ||
                            data.contains("/Cloud/Podcasts/", ignoreCase = true)
                    if (inPodcasts && (name.endsWith(".mp3") || name.endsWith(".m4a"))) {
                        val isCompleted =
                            podcastPrefs.getBoolean("podcast_completed_${data.hashCode()}", false)
                        if (isCompleted) {
                            val displayName =
                                if (!title.isNullOrBlank() && title != "<unknown>") title
                                else name.substringBeforeLast('.')
                            completedPodcasts.add(Pair(data, displayName))
                        }
                    }
                }
            }

            if (completedPodcasts.isEmpty()) {
                showSimpleNotificationExtern(
                    "Keine fertigen Podcasts",
                    "Es gibt aktuell keine fertigen Podcasts zum Löschen",
                    10.seconds,
                    context = context
                )
                return
            }

            completedPodcasts.forEachIndexed { i, (path, name) ->
                val deleteIntent = PendingIntent.getService(
                    context, 70000 + i,
                    Intent(context, MediaPlayerService::class.java).apply {
                        action = ACTION_DELETE_SINGLE + path.hashCode()
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val n =
                    NotificationCompat.Builder(context, "media_player_channel")
                        .setSmallIcon(android.R.drawable.ic_menu_delete)
                        .setContentTitle("🗑️ $name")
                        .setContentText("Antippen zum Löschen • Fertig angehört")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setContentIntent(deleteIntent)
                        .setGroup("podcast_delete")
                        .build()
                if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                    nm?.notify(70000 + i, n)
            }

            val summary =
                NotificationCompat.Builder(context, "media_player_channel")
                    .setSmallIcon(android.R.drawable.ic_menu_delete)
                    .setContentTitle("Fertige Podcasts löschen")
                    .setContentText("${completedPodcasts.size} Podcasts bereit zum Löschen")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setGroup("podcast_delete")
                    .setGroupSummary(true)
                    .setAutoCancel(true)
                    .build()
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                nm?.notify(70999, summary)
        }

        fun setPlaybackSpeed(context: Context, speed: Float) = context.startService(
            Intent(context, MediaPlayerService::class.java).apply {
                action = ACTION_SET_SPEED
                putExtra(EXTRA_SPEED, speed)
            }
        )

        fun stopService(context: Context) = context.stopService(
            Intent(context, MediaPlayerService::class.java)
        )

        fun createPlaylist(context: Context, name: String, type: PlaylistType): String {
            val intent = Intent(context, MediaPlayerService::class.java).apply {
                action = "CREATE_PLAYLIST"
                putExtra("PLAYLIST_NAME", name)
                putExtra("PLAYLIST_TYPE", type.name)
            }
            context.startService(intent)
            return ""
        }

        fun showPlaylists(context: Context, type: PlaylistType? = null) {
            val intent = Intent(context, MediaPlayerService::class.java).apply {
                action = "SHOW_PLAYLISTS"
                type?.let { putExtra("PLAYLIST_TYPE", it.name) }
            }
            context.startService(intent)
        }

        fun activatePlaylist(context: Context, playlistId: String) {
            val intent = Intent(context, MediaPlayerService::class.java).apply {
                action = "ACTIVATE_PLAYLIST"
                putExtra("PLAYLIST_ID", playlistId)
            }
            context.startService(intent)
        }

        fun deactivatePlaylist(context: Context) {
            val intent = Intent(context, MediaPlayerService::class.java).apply {
                action = "DEACTIVATE_PLAYLIST"
            }
            context.startService(intent)
        }

        fun addCurrentToPlaylist(context: Context, playlistName: String) {
            val intent = Intent(context, MediaPlayerService::class.java).apply {
                action = "ADD_CURRENT_TO_PLAYLIST"
                putExtra("PLAYLIST_NAME", playlistName)
            }
            context.startService(intent)
        }

        fun deletePlaylist(context: Context, playlistId: String) {
            val intent = Intent(context, MediaPlayerService::class.java).apply {
                action = "DELETE_PLAYLIST"
                putExtra("PLAYLIST_ID", playlistId)
            }
            context.startService(intent)
        }

        fun createPlaylistWithSongs(context: Context, name: String, songPaths: List<String>) {
            val intent = Intent(context, MediaPlayerService::class.java).apply {
                action = "CREATE_PLAYLIST_WITH_SONGS"
                putExtra("PLAYLIST_NAME", name)
                putStringArrayListExtra("SONG_PATHS", ArrayList(songPaths))
            }
            context.startService(intent)
        }
    }

    data class Song(val uri: Uri, val name: String, val path: String)

    data class Podcast(
        val uri: Uri,
        val name: String,
        val path: String,
        val savedPosition: Long = 0,
        val isCompleted: Boolean = false
    )

    data class Playlist(
        val id: String = UUID.randomUUID().toString(),
        val name: String,
        val type: PlaylistType,
        val items: MutableList<String> = mutableListOf()
    )

    enum class PlaylistType {
        MUSIC
    }

    @Volatile private var currentMode = MODE_MUSIC

    private var musicPlayer: MediaPlayer? = null
    private var playlist: List<Song> = emptyList()
    @Volatile private var currentSongIndex = 0
    @Volatile private var isPlayingMusic = false
    private var isRepeatEnabled = false
    private var favoritesMode = false
    private val favoriteSongs = mutableMapOf<String, Long>()
    @Volatile private var isServiceDestroyed = false

    private var podcastPlayer: MediaPlayer? = null
    private var podcasts: List<Podcast> = emptyList()
    private var podcastQueue: MutableList<String> = mutableListOf()
    private var currentPodcast: Podcast? = null
    @Volatile private var isPlayingPodcast = false
    private var positionSaveRunnable: Runnable? = null
    private var canPostNotifications = true

    private var mediaSession: MediaSession? = null
    private lateinit var musicPrefs: SharedPreferences
    private lateinit var podcastPrefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var bluetoothReceiver: BroadcastReceiver? = null
    private var lastPlayPauseTime = 0L
    private var playPauseCount = 0

    private val playlists = mutableListOf<Playlist>()
    private var activePlaylistId: String? = null
    private var activeAlgorithmicPlaylistId: String? = null
    private var algorithmicPlaylistSongs: List<Song> = emptyList()

    private var songStartedAt: Long = 0L
    private var currentSongName: String = ""
    private var consecutiveRepeatCount: Int = 0

    private var podcastSessionStartedAt: Long = 0L
    private var podcastSessionStartPos: Long = 0L

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        musicPrefs = getSharedPreferences(MUSIC_PREFS, MODE_PRIVATE)
        podcastPrefs = getSharedPreferences(PODCAST_PREFS, MODE_PRIVATE)

        currentMode = musicPrefs.getString(KEY_CURRENT_MODE, MODE_MUSIC) ?: MODE_MUSIC
        currentSongIndex = musicPrefs.getInt(KEY_CURRENT_SONG_INDEX, 0)
        isRepeatEnabled = musicPrefs.getBoolean(KEY_REPEAT_MODE, false)

        createNotificationChannel()
        loadPlaylist()
        loadFavorites()
        loadPodcasts()
        loadPodcastQueue()
        loadPlaylists()

        activeAlgorithmicPlaylistId = musicPrefs.getString(KEY_ACTIVE_ALGORITHMIC_PLAYLIST, null)
        if (activeAlgorithmicPlaylistId != null) {
            FavoritesPlaylist.setContext(this)
            MediaAnalyticsManager.init(this)
            val src = AlgorithmicPlaylistRegistry.findById(activeAlgorithmicPlaylistId!!)
            if (src != null) {
                algorithmicPlaylistSongs = src.getSongs(playlist, emptyMap())
                if (algorithmicPlaylistSongs.isNotEmpty() && currentSongIndex >= algorithmicPlaylistSongs.size) {
                    currentSongIndex = 0
                    saveMusicState()
                }
            } else {
                activeAlgorithmicPlaylistId = null
            }
        }

        podcastPrefs.getString(KEY_CURRENT_PODCAST, null)?.let { path ->
            podcasts.find { it.path == path }?.let { p ->
                currentPodcast = p.copy(savedPosition = getPodcastSavedPosition(p.path))
            }
        }

        createMediaSession()
        startForeground(NOTIFICATION_ID, buildNotification(), getServiceForegroundType())
        startPositionSaving()
        registerBluetoothReceiver()

        // Cache permission check result to avoid repeated checks
        canPostNotifications = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        intent?.getIntExtra(EXTRA_SONG_INDEX, -1)?.takeIf { it > 0 }?.let { idx ->
            currentSongIndex = idx - 1
            saveMusicState()
        }

        when (val action = intent?.action) {

            ACTION_SWITCH_TO_MUSIC -> switchToMusic()
            ACTION_SWITCH_TO_PODCAST -> switchToPodcast()

            ACTION_MUSIC_PLAY -> {
                ensureMusicMode(); playMusic()
            }

            ACTION_MUSIC_PAUSE -> {
                if (currentMode == MODE_MUSIC) pauseMusic()
            }

            ACTION_MUSIC_NEXT -> {
                if (currentMode == MODE_MUSIC) nextSong()
            }

            ACTION_MUSIC_PREVIOUS -> {
                if (currentMode == MODE_MUSIC) previousSong()
            }

            ACTION_TOGGLE_REPEAT -> toggleRepeat()
            ACTION_TOGGLE_FAVORITE -> toggleFavorite()
            ACTION_SHOW_FAVORITES -> showFavorites()
            ACTION_TOGGLE_FAVORITES_MODE -> toggleFavoritesMode()

            ACTION_PODCAST_PLAY -> {
                ensurePodcastMode(); playPodcast()
            }

            ACTION_PODCAST_PAUSE -> {
                if (currentMode == MODE_PODCAST) pausePodcast()
            }

            ACTION_PODCAST_REWIND -> {
                if (currentMode == MODE_PODCAST) {
                    rewind()
                    podcastCurrentPosition()?.let {
                        currentPodcast?.let { p ->
                            savePodcastPosition(
                                p.path,
                                it
                            )
                        }
                    }
                }
            }

            ACTION_PODCAST_FORWARD -> {
                if (currentMode == MODE_PODCAST) {
                    val ms = intent.getIntExtra(EXTRA_FORWARD_MS, SKIP_TIME_MS)
                    forward(ms)
                    podcastCurrentPosition()?.let {
                        currentPodcast?.let { p ->
                            savePodcastPosition(
                                p.path,
                                it
                            )
                        }
                    }
                }
            }

            ACTION_SELECT_PODCAST -> showPodcastSelection()
            ACTION_SHOW_DELETE_COMPLETED -> showDeleteCompletedNotifications()
            ACTION_SET_SPEED -> {
                val speed = intent.getFloatExtra(EXTRA_SPEED, 1.0f)
                setPlaybackSpeed(speed)
            }

            ACTION_NOTIFICATION_DELETED -> {
                savePodcastCurrentPosition()
                stopSelf()
                return START_NOT_STICKY
            }

            "CREATE_PLAYLIST" -> {
                val name: String = intent.getStringExtra("PLAYLIST_NAME") ?: "Neue Playlist"
                val typeName: String =
                    intent.getStringExtra("PLAYLIST_TYPE") ?: PlaylistType.MUSIC.name
                val type = PlaylistType.valueOf(typeName)
                createPlaylist(name, type)
                showSimpleNotificationExtern(
                    "✓ Playlist erstellt",
                    "\"$name\" wurde erstellt",
                    10.seconds,
                    context = this
                )
            }

            "SHOW_PLAYLISTS" -> {
                val typeName: String? = intent.getStringExtra("PLAYLIST_TYPE")
                val type = typeName?.let { PlaylistType.valueOf(it) }
                showPlaylistsNotification(type)
            }

            "ACTIVATE_PLAYLIST" -> {
                val id: String? = intent.getStringExtra("PLAYLIST_ID")
                if (id != null && activatePlaylist(id)) {
                    val pl = playlists.find { it.id == id }
                    showSimpleNotificationExtern(
                        "▶ Playlist aktiviert",
                        "\"${pl?.name}\" wird abgespielt",
                        10.seconds,
                        context = this
                    )
                } else {
                    showSimpleNotificationExtern(
                        "❌ Fehler",
                        "Playlist konnte nicht aktiviert werden",
                        10.seconds,
                        context = this
                    )
                }
            }

            "DEACTIVATE_PLAYLIST" -> {
                val pl = playlists.find { it.id == activePlaylistId }
                deactivatePlaylist()
                showSimpleNotificationExtern(
                    "⏸ Playlist deaktiviert",
                    if (pl != null) "\"${pl.name}\" beendet" else "Zurück zur normalen Wiedergabe",
                    10.seconds,
                    context = this
                )
            }

            "ADD_CURRENT_TO_PLAYLIST" -> {
                val name: String? = intent.getStringExtra("PLAYLIST_NAME")
                if (name != null) {
                    val path = when (currentMode) {
                        MODE_MUSIC -> playlist.getOrNull(currentSongIndex)?.path
                        else -> null
                    }

                    if (path != null && addToPlaylist(name, path)) {
                        val pl = playlists.find { it.name == name }
                        val itemName = when (currentMode) {
                            MODE_MUSIC -> playlist.find { it.path == path }?.name
                            else -> null
                        }
                        showSimpleNotificationExtern(
                            "✓ Hinzugefügt",
                            "\"$itemName\" → \"${pl?.name}\"",
                            10.seconds,
                            context = this
                        )
                    } else {
                        showSimpleNotificationExtern(
                            "❌ Fehler",
                            "Item konnte nicht hinzugefügt werden",
                            10.seconds,
                            context = this
                        )
                    }
                }
            }

            "DELETE_PLAYLIST" -> {
                val id: String? = intent.getStringExtra("PLAYLIST_ID")
                if (id != null) {
                    val pl = playlists.find { it.id == id }
                    if (deletePlaylist(id)) {
                        showSimpleNotificationExtern(
                            "🗑 Playlist gelöscht",
                            "\"${pl?.name}\" wurde gelöscht",
                            10.seconds,
                            context = this
                        )
                    }
                }
            }

            "CREATE_PLAYLIST_WITH_SONGS" -> {
                val name = intent.getStringExtra("PLAYLIST_NAME") ?: "Neue Playlist"
                val paths = intent.getStringArrayListExtra("SONG_PATHS") ?: arrayListOf()
                val pl = Playlist(name = name, type = PlaylistType.MUSIC,
                    items = paths.filter { p -> playlist.any { it.path == p } }.toMutableList())
                playlists.add(pl)
                savePlaylists()
                showSimpleNotificationExtern(
                    "✓ Playlist erstellt",
                    "\"$name\" · ${pl.items.size} Songs",
                    10.seconds, context = this
                )
            }

            ACTION_ACTIVATE_ALGORITHMIC_PLAYLIST -> {
                val sourceId = intent.getStringExtra(EXTRA_PLAYLIST_SOURCE_ID)
                val idx = intent.getIntExtra(EXTRA_SONG_INDEX, 0)
                if (sourceId != null) activateAlgorithmicPlaylistInternal(sourceId, idx)
            }

            "DEACTIVATE_ALGORITHMIC_PLAYLIST" -> {
                val name = AlgorithmicPlaylistRegistry.findById(activeAlgorithmicPlaylistId ?: "")?.name
                activeAlgorithmicPlaylistId = null
                algorithmicPlaylistSongs = emptyList()
                saveMusicState()
                showSimpleNotificationExtern(
                    "⏸ Playlist deaktiviert",
                    if (name != null) "\"$name\" beendet" else "Zurück zur normalen Wiedergabe",
                    10.seconds, context = this
                )
                updateNotification()
            }

            ACTION_PLAY_ALL_SONGS_AT_INDEX -> {
                activeAlgorithmicPlaylistId = null
                algorithmicPlaylistSongs = emptyList()
                activePlaylistId = null
                favoritesMode = false
                saveFavorites()
                savePlaylists()
                loadPlaylist()
                val idx = intent.getIntExtra(EXTRA_SONG_INDEX, 1)
                currentSongIndex = (idx - 1).coerceAtLeast(0)
                saveMusicState()
                ensureMusicMode()
                musicPlayer?.release(); musicPlayer = null
                loadSong(currentSongIndex)
            }

            else -> when {
                action?.startsWith("SELECT_") == true -> {
                    action.removePrefix("SELECT_").toIntOrNull()?.let { hash ->
                        podcasts.find { it.path.hashCode() == hash }?.let { selected ->
                            switchToPodcast()
                            currentPodcast =
                                selected.copy(savedPosition = getPodcastSavedPosition(selected.path))
                            loadPodcast(currentPodcast!!)
                        }
                    }
                }

                action?.startsWith(ACTION_DELETE_SINGLE) == true -> {
                    val wasAlreadyRunning = musicPlayer != null || podcastPlayer != null ||
                            isPlayingMusic || isPlayingPodcast
                    action.removePrefix(ACTION_DELETE_SINGLE).toIntOrNull()?.let { hash ->
                        podcasts.find { it.path.hashCode() == hash }?.let { deletePodcastFile(it) }
                    }
                    if (!wasAlreadyRunning) {
                        stopSelf()
                        return START_NOT_STICKY
                    }
                }

                action?.startsWith("ACTIVATE_PL_") == true -> {
                    val id = action.removePrefix("ACTIVATE_PL_")
                    if (activatePlaylist(id)) {
                        val pl = playlists.find { it.id == id }
                        showSimpleNotificationExtern(
                            "▶ Playlist aktiviert",
                            "\"${pl?.name}\" (${pl?.items?.size ?: 0} Items)",
                            10.seconds,
                            context = this
                        )
                    }
                }

                action?.startsWith("DELETE_PL_") == true -> {
                    val id = action.removePrefix("DELETE_PL_")
                    val pl = playlists.find { it.id == id }
                    if (deletePlaylist(id)) {
                        showSimpleNotificationExtern(
                            "🗑 Gelöscht",
                            "\"${pl?.name}\"",
                            10.seconds,
                            context = this
                        )
                        handler.postDelayed({ showPlaylistsNotification(pl?.type) }, 300)
                    }
                }
            }
        }

        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        isServiceDestroyed = true // Mark as destroyed to prevent handler callbacks from rescheduling
        try {
            val nm: NotificationManager? = getSystemService(NotificationManager::class.java)
            nm?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
        try {
            bluetoothReceiver?.let { unregisterReceiver(it); bluetoothReceiver = null }
        } catch (_: Exception) {
        }
        positionSaveRunnable?.let { handler.removeCallbacks(it) }
        savePodcastCurrentPosition()

        if (isPlayingMusic && songStartedAt > 0L && currentSongName.isNotEmpty()) {
            val listenedMs = System.currentTimeMillis() - songStartedAt
            if (listenedMs > 3000) {
                MediaAnalyticsManager.addSession(
                    ListenSession(
                        label = currentSongName,
                        type = "music",
                        listenedMs = listenedMs,
                        startedAt = songStartedAt,
                        repeatCount = consecutiveRepeatCount.coerceAtLeast(1)
                    )
                )
            }
        }
        if (isPlayingPodcast) savePodcastSession()

        musicPlayer?.release(); musicPlayer = null
        podcastPlayer?.release(); podcastPlayer = null
        mediaSession?.release(); mediaSession = null
    }

    private fun ensureMusicMode() {
        if (currentMode != MODE_MUSIC) switchToMusic()
    }

    private fun ensurePodcastMode() {
        if (currentMode != MODE_PODCAST) switchToPodcast()
    }

    private fun switchToMusic() {
        if (isPlayingPodcast) {
            savePodcastCurrentPosition()
            podcastPlayer?.pause()
            isPlayingPodcast = false
        }
        currentMode = MODE_MUSIC
        saveMusicState()
        updateNotification()
    }

    private fun switchToPodcast() {
        if (isPlayingMusic) {
            musicPlayer?.pause()
            isPlayingMusic = false
        }
        currentMode = MODE_PODCAST
        saveMusicState()
        updateNotification()
    }

    private fun switchModeViaButton() {
        if (currentMode == MODE_MUSIC) {
            if (isPlayingMusic) {
                if (songStartedAt > 0L && currentSongName.isNotEmpty()) {
                    val listenedMs = System.currentTimeMillis() - songStartedAt
                    if (listenedMs > 3000) {
                        MediaAnalyticsManager.addSession(
                            ListenSession(
                                label = currentSongName,
                                type = "music",
                                listenedMs = listenedMs,
                                startedAt = songStartedAt,
                                repeatCount = consecutiveRepeatCount.coerceAtLeast(1)
                            )
                        )
                    }
                    songStartedAt = 0L
                    consecutiveRepeatCount = 0
                }
                musicPlayer?.pause()
                isPlayingMusic = false
            }
            switchToPodcast()
        } else {
            // Speichere Podcast-Session BEVOR wir wechseln
            if (isPlayingPodcast) {
                savePodcastSession()
                podcastPlayer?.pause()
                isPlayingPodcast = false
            }
            switchToMusic()
        }
    }

    private fun createMediaSession() {
        mediaSession = MediaSession.Builder(this, DummyPlayer())
            .setId("CombinedMediaSession")
            .setSessionActivity(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setCallback(object : MediaSession.Callback {
                override fun onMediaButtonEvent(
                    session: MediaSession,
                    controllerInfo: MediaSession.ControllerInfo,
                    intent: Intent
                ): Boolean {
                    val keyEvent: KeyEvent =
                        intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                            ?: return super.onMediaButtonEvent(session, controllerInfo, intent)

                    if (keyEvent.action != KeyEvent.ACTION_DOWN) return false

                    try {
                        when (keyEvent.keyCode) {
                            KeyEvent.KEYCODE_MEDIA_PLAY,
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                val now = System.currentTimeMillis()
                                if (now - lastPlayPauseTime < SERVICE_SWITCH_TIMEOUT) {
                                    playPauseCount++
                                    if (playPauseCount >= 5) {
                                        playPauseCount = 0
                                        handler.post { switchModeViaButton() }
                                        return true
                                    }
                                } else {
                                    playPauseCount = 1
                                }
                                lastPlayPauseTime = now

                                if (currentMode == MODE_MUSIC) {
                                    if (isPlayingMusic) pauseMusic() else playMusic()
                                } else {
                                    if (currentPodcast != null) {
                                        if (isPlayingPodcast) pausePodcast() else playPodcast()
                                    } else {
                                        showPodcastSelection()
                                    }
                                }
                                return true
                            }

                            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                if (currentMode == MODE_MUSIC) nextSong() else forward()
                                return true
                            }

                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                if (currentMode == MODE_MUSIC) previousSong() else rewind()
                                return true
                            }
                        }
                    } catch (_: Exception) {}
                    return super.onMediaButtonEvent(session, controllerInfo, intent)
                }
            })
            .build()
    }

    private fun playMusic() {
        val active = getActivePlaylist()
        if (active.isEmpty()) {
            showSimpleNotificationExtern(
                "❌ Keine Songs",
                if (favoritesMode) "Keine Favoriten verfügbar" else "Playlist ist leer",
                10.seconds,
                context = this
            )
            updateNotification()
            return
        }

        if (musicPlayer != null && !isPlayingMusic) {
            musicPlayer?.start()
            musicPrefs.editAsync { putBoolean("is_playing", true) }
            isPlayingMusic = true
            updateNotification()
            return
        }

        if (musicPlayer == null) loadSong(currentSongIndex)
    }

    private fun loadSong(index: Int) {
        val active = getActivePlaylist()
        if (active.isEmpty() || index !in active.indices) return

        val prevSong = active.getOrNull(currentSongIndex)
        if (prevSong != null && songStartedAt > 0L && currentSongName.isNotEmpty()) {
            val listenedMs = System.currentTimeMillis() - songStartedAt
            if (listenedMs > 3000) {
                MediaAnalyticsManager.addSession(
                    ListenSession(
                        label = currentSongName,
                        type = "music",
                        listenedMs = listenedMs,
                        startedAt = songStartedAt,
                        repeatCount = consecutiveRepeatCount.coerceAtLeast(1)
                    )
                )
            }
            consecutiveRepeatCount = 0
        }

        musicPlayer?.release()
        musicPlayer = null

        // Try to load current song, with fallback to next songs if needed
        var nextIndex = index
        var attempts = 0
        val maxAttempts = active.size

        while (attempts < maxAttempts) {
            val song = active[nextIndex]
            try {
                musicPlayer = MediaPlayer().apply {
                    try {
                        setDataSource(applicationContext, song.uri)
                        prepare()
                    } catch (_: Exception) {
                        reset(); setDataSource(song.path); prepare()
                    }
                    setOnCompletionListener { onSongComplete() }
                    start()
                }

                songStartedAt = System.currentTimeMillis()
                currentSongName = song.name
                consecutiveRepeatCount = 0

                isPlayingMusic = true
                currentSongIndex = nextIndex
                saveMusicState()
                updateNotification()
                musicPrefs.editAsync { putBoolean("is_playing", true) }
                return // Successfully loaded
            } catch (_: Exception) {
                musicPlayer?.release()
                musicPlayer = null
                attempts++
                nextIndex = (nextIndex + 1) % active.size
            }
        }

        // All songs failed to load
        isPlayingMusic = false
        updateNotification()
    }

    private fun pauseMusic() {
        if (!isPlayingMusic || musicPlayer?.isPlaying != true) return

        if (songStartedAt > 0L && currentSongName.isNotEmpty()) {
            val listenedMs = System.currentTimeMillis() - songStartedAt
            if (listenedMs > 3000) {
                MediaAnalyticsManager.addSession(
                    ListenSession(
                        label = currentSongName,
                        type = "music",
                        listenedMs = listenedMs,
                        startedAt = songStartedAt,
                        repeatCount = consecutiveRepeatCount.coerceAtLeast(1)
                    )
                )
            }
            songStartedAt = 0L
            consecutiveRepeatCount = 0
        }

        musicPlayer?.pause()
        musicPrefs.editAsync { putBoolean("is_playing", false) }
        isPlayingMusic = false
        updateNotification()
    }

    private fun nextSong() {
        val active = getActivePlaylist()
        if (active.isEmpty()) return
        currentSongIndex = (currentSongIndex + 1) % active.size
        musicPlayer?.release(); musicPlayer = null
        loadSong(currentSongIndex)
    }

    private fun previousSong() {
        val active = getActivePlaylist()
        if (active.isEmpty()) return
        currentSongIndex = if (currentSongIndex - 1 < 0) active.size - 1 else currentSongIndex - 1
        musicPlayer?.release(); musicPlayer = null
        loadSong(currentSongIndex)
    }

    private fun toggleRepeat() {
        isRepeatEnabled = !isRepeatEnabled
        musicPrefs.edit { putBoolean(KEY_REPEAT_MODE, isRepeatEnabled) }
        updateNotification()
    }

    private fun onSongComplete() {
        if (isRepeatEnabled) {
            consecutiveRepeatCount++
            try {
                musicPlayer?.seekTo(0); musicPlayer?.start()
            } catch (_: Exception) {
            }
        } else {
            if (songStartedAt > 0L && currentSongName.isNotEmpty()) {
                val listenedMs = System.currentTimeMillis() - songStartedAt
                if (listenedMs > 3000) {
                    MediaAnalyticsManager.addSession(
                        ListenSession(
                            label = currentSongName,
                            type = "music",
                            listenedMs = listenedMs,
                            startedAt = songStartedAt,
                            repeatCount = consecutiveRepeatCount.coerceAtLeast(1)
                        )
                    )
                }
                songStartedAt = 0L
                consecutiveRepeatCount = 0
            }
            val active = getActivePlaylist()
            currentSongIndex = if (currentSongIndex + 1 >= active.size) 0 else currentSongIndex + 1
            musicPlayer?.release(); musicPlayer = null
            loadSong(currentSongIndex)
        }
    }

    private fun getActivePlaylist(): List<Song> {
        if (activeAlgorithmicPlaylistId != null && algorithmicPlaylistSongs.isNotEmpty())
            return algorithmicPlaylistSongs
        return if (favoritesMode)
            playlist
                .filter { favoriteSongs.containsKey(it.path) }
                .sortedByDescending { favoriteSongs[it.path] ?: 0L }
        else playlist
    }

    private fun loadFavorites() {
        favoriteSongs.clear()

        // Versuche zuerst, als String zu laden (neues Format)
        val raw = try {
            musicPrefs.getString(KEY_FAVORITES, null)
        } catch (_: ClassCastException) {
            // Falls ein HashSet gespeichert ist (altes Format), migriere es
            try {
                val oldSet = musicPrefs.getStringSet(KEY_FAVORITES, null)
                if (oldSet != null) {
                    // Konvertiere altes Format zu neuem Format
                    oldSet.forEach { entry ->
                        val parts = entry.split(":::")
                        if (parts.size == 2) {
                            favoriteSongs[parts[0]] = parts[1].toLongOrNull() ?: System.currentTimeMillis()
                        } else if (parts.size == 1 && parts[0].isNotEmpty()) {
                            favoriteSongs[parts[0]] = System.currentTimeMillis()
                        }
                    }
                    // Speichere im neuen Format und lösche altes Format
                    saveFavorites()
                    return
                }
            } catch (_: Exception) {
                // Konnte nicht migriert werden, lösche den fehlerhaften Eintrag
                musicPrefs.edit { remove(KEY_FAVORITES) }
            }
            null
        }

        var needsMigration = false
        if (!raw.isNullOrEmpty()) {
            raw.split("|||").forEach { entry ->
                val parts = entry.split(":::")
                if (parts.size == 2) {
                    favoriteSongs[parts[0]] = parts[1].toLongOrNull() ?: 0L
                } else if (parts.size == 1 && parts[0].isNotEmpty()) {
                    favoriteSongs[parts[0]] = System.currentTimeMillis()
                    needsMigration = true
                }
            }
        }
        favoritesMode = musicPrefs.getBoolean(KEY_FAVORITES_MODE, false)
        if (needsMigration) saveFavorites()
    }

    private fun saveFavorites() {
        val raw = favoriteSongs.entries.joinToString("|||") { "${it.key}:::${it.value}" }
        musicPrefs.edit {
            putString(KEY_FAVORITES, raw)
            putBoolean(KEY_FAVORITES_MODE, favoritesMode)
        }
    }

    private fun toggleFavorite(songPath: String? = null) {
        val path = songPath ?: playlist.getOrNull(currentSongIndex)?.path ?: return
        val name = playlist.find { it.path == path }?.name ?: "Unbekannt"
        if (favoriteSongs.containsKey(path)) {
            favoriteSongs.remove(path)
            showSimpleNotificationExtern("💔 Favorit entfernt", name, 10.seconds, context = this)
        } else {
            favoriteSongs[path] = System.currentTimeMillis()
            showSimpleNotificationExtern("⭐ Favorit hinzugefügt", name, 10.seconds, context = this)
        }
        saveFavorites()
        updateNotification()
    }

    private fun toggleFavoritesMode() {
        if (activePlaylistId != null || activeAlgorithmicPlaylistId != null) {
            activePlaylistId = null
            activeAlgorithmicPlaylistId = null
            algorithmicPlaylistSongs = emptyList()
            loadPlaylist()
            savePlaylists()
            saveMusicState()
            favoritesMode = false
        }
        favoritesMode = !favoritesMode
        val active = getActivePlaylist()
        if (favoritesMode && active.isEmpty()) {
            showSimpleNotificationExtern(
                "❌ Keine Favoriten",
                "Füge zuerst Songs zu deinen Favoriten hinzu!",
                10.seconds,
                context = this
            )
            favoritesMode = false; saveFavorites(); return
        }

        currentSongIndex = if (favoritesMode) {
            0
        } else {
            musicPrefs.getInt(KEY_CURRENT_SONG_INDEX, 0).coerceIn(0, active.size - 1)
        }
        saveMusicState()
        saveFavorites()
        showSimpleNotificationExtern(
            if (favoritesMode) "⭐ Favoriten-Modus aktiviert" else "📁 Alle Songs",
            "${active.size} Songs verfügbar",
            10.seconds,
            context = this
        )
        if (isPlayingMusic) {
            musicPlayer?.release(); musicPlayer = null; loadSong(currentSongIndex)
        } else updateNotification()
    }

    private fun showFavorites() {
        if (favoriteSongs.isEmpty()) {
            showSimpleNotificationExtern(
                "📂 Favoriten",
                "Keine Favoriten gespeichert",
                10.seconds,
                context = this
            )
            return
        }
        val list = playlist.filter { favoriteSongs.containsKey(it.path) }
            .mapIndexed { i, s -> "${i + 1}. ${s.name}" }.joinToString("\n")
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.btn_star_big_on)
            .setContentTitle("⭐ Favoriten (${favoriteSongs.size})")
            .setContentText("${favoriteSongs.size} Songs")
            .setStyle(NotificationCompat.BigTextStyle().bigText(list))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
        val nm: NotificationManager? = getSystemService(NotificationManager::class.java)
        nm?.notify(77777, n)
    }

    private fun playPodcast() {
        if (currentPodcast == null) {
            podcastPrefs.getString(KEY_CURRENT_PODCAST, null)?.let { path ->
                podcasts.find { it.path == path }?.let { p ->
                    currentPodcast = p.copy(savedPosition = getPodcastSavedPosition(p.path))
                    updateNotification()
                }
            }
            if (currentPodcast == null) {
                showPodcastSelection()
                return
            }
        }

        val podcast = currentPodcast ?: return

        if (podcastPlayer != null && !isPlayingPodcast) {
            val savedPos = getPodcastSavedPosition(podcast.path)
            if (savedPos > 1000) podcastPlayer?.seekTo((savedPos - 1000).toInt())
            musicPrefs.editAsync { putBoolean("is_playing", true) }
            podcastPlayer?.start()
            isPlayingPodcast = true
            updateNotification()
            return
        }

        if (podcastPlayer == null) loadPodcast(podcast)
    }

    private fun loadPodcast(podcast: Podcast) {
        podcastPlayer?.release(); podcastPlayer = null

        if (currentPodcast?.path != podcast.path) {
            currentPodcast = podcast
            savePodcastCurrentPath(podcast.path)
        }
        if (podcast.isCompleted) setPodcastCompleted(podcast.path, false)

        currentMode = MODE_PODCAST
        saveMusicState()

        if (isPlayingMusic) {
            musicPlayer?.release(); musicPlayer = null
            isPlayingMusic = false
            musicPrefs.editAsync { putBoolean("is_playing", false) }
        }

        try {
            podcastPlayer = MediaPlayer().apply {
                try {
                    setDataSource(applicationContext, podcast.uri)
                    prepare()
                } catch (_: Exception) {
                    reset(); setDataSource(podcast.path); prepare()
                }

                val speed = getSavedPlaybackSpeed()
                if (speed != 1.0f) playbackParams = playbackParams.setSpeed(speed)

                if (podcast.savedPosition > 0) seekTo(podcast.savedPosition.toInt())

                setOnCompletionListener { onPodcastComplete() }

                isPlayingPodcast = true
                currentPodcast = podcast.copy(isCompleted = false)
                savePodcastCurrentPath(podcast.path)
                updateNotification()
                start()
                musicPrefs.editAsync { putBoolean("is_playing", true) }
            }
            podcastSessionStartedAt = System.currentTimeMillis()
            podcastSessionStartPos = podcast.savedPosition
        } catch (_: Exception) {
            isPlayingPodcast = false; updateNotification()
        }
    }

    private fun pausePodcast() {
        if (!isPlayingPodcast || podcastPlayer?.isPlaying != true) return
        val pos = podcastPlayer?.currentPosition?.toLong() ?: 0
        podcastPlayer?.pause()
        isPlayingPodcast = false
        musicPrefs.editAsync { putBoolean("is_playing", false) }
        currentPodcast?.let { savePodcastPosition(it.path, pos) }

        savePodcastSession()

        updateNotification()
    }

    private fun rewind() {
        if (currentPodcast == null) return
        val player = podcastPlayer ?: return
        val newPos = maxOf(0, player.currentPosition - SKIP_TIME_MS)
        player.seekTo(newPos)
        currentPodcast?.let { savePodcastPosition(it.path, newPos.toLong()) }
        updateNotification()
    }

    private fun forward(skipMs: Int = SKIP_TIME_MS) {
        if (currentPodcast == null) return
        val player = podcastPlayer ?: return
        val newPos = minOf(player.duration, player.currentPosition + skipMs)
        player.seekTo(newPos)
        currentPodcast?.let { savePodcastPosition(it.path, newPos.toLong()) }
        updateNotification()
    }

    private fun onPodcastComplete() {
        currentPodcast?.let { podcast ->
            savePodcastPosition(podcast.path, 0)
            setPodcastCompleted(podcast.path, true)
            podcasts = podcasts.map {
                if (it.path == podcast.path) it.copy(savedPosition = 0, isCompleted = true) else it
            }

            savePodcastSession()
        }

        try { podcastPlayer?.stop(); podcastPlayer?.release(); podcastPlayer = null } catch (_: Exception) {}
        isPlayingPodcast = false
        loadPodcastQueue()

        if (podcastQueue.isNotEmpty()) {
            val nextPath = podcastQueue.removeAt(0); savePodcastQueue()
            val next = podcasts.find { it.path == nextPath }
            if (next != null) {
                currentPodcast = next.copy(savedPosition = getPodcastSavedPosition(next.path))
                savePodcastCurrentPath(next.path)
                loadPodcast(currentPodcast!!)
            } else {
                if (podcastQueue.isNotEmpty()) onPodcastComplete()
                else { currentPodcast = null; updateNotification() }
            }
        } else {
            currentPodcast = null; updateNotification()
        }
    }

    private fun setPlaybackSpeed(speed: Float) {
        val s = speed.coerceIn(0.5f, 3.0f)
        podcastPlayer?.let { player ->
            player.playbackParams = player.playbackParams.setSpeed(s)
            isPlayingPodcast = player.isPlaying
            savePlaybackSpeed(s); updateNotification()
        } ?: run {
            savePlaybackSpeed(s)
            showSimpleNotificationExtern(
                "✓ Gespeichert",
                "Geschwindigkeit ${s}x wird beim nächsten Start verwendet",
                5.seconds, this
            )
        }
    }

    @SuppressLint("LaunchActivityFromNotification")
    private fun showPodcastSelection() {
        if (podcasts.isEmpty()) return
        val nm: NotificationManager? = getSystemService(NotificationManager::class.java)
        val updated = podcasts.map {
            it.copy(
                savedPosition = getPodcastSavedPosition(it.path),
                isCompleted = getPodcastCompleted(it.path)
            )
        }
        updated.forEachIndexed { i, p ->
            val pi = PendingIntent.getService(
                this, 50000 + i,
                Intent(this, MediaPlayerService::class.java).apply {
                    action = "SELECT_${p.path.hashCode()}"
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val status = when {
                p.isCompleted -> "✓"; p.savedPosition > 0 -> "▶"; else -> "⏸"
            }
            val progress = when {
                p.isCompleted -> " • Fertig"; p.savedPosition > 0 -> " • ${formatTime(p.savedPosition)}"; else -> ""
            }
            val n = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("${i + 1} $status ${p.name}")
                .setContentText("Antippen zum Abspielen$progress")
                .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true)
                .setContentIntent(pi).setGroup("podcast_selection").build()
            if (hasNotificationPermission())
                nm?.notify(60000 + i, n)
        }
        val completedCount = updated.count { it.isCompleted }
        val summary = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Podcast auswählen")
            .setContentText("${podcasts.size} Podcasts${if (completedCount > 0) " ($completedCount fertig)" else ""}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup("podcast_selection").setGroupSummary(true).setAutoCancel(true).build()
        if (hasNotificationPermission())
            nm?.notify(60999, summary)
    }

    @SuppressLint("LaunchActivityFromNotification")
    private fun showDeleteCompletedNotifications() {
        val completed = podcasts.map { it.copy(isCompleted = getPodcastCompleted(it.path)) }
            .filter { it.isCompleted }
        if (completed.isEmpty()) {
            showNoCompletedPodcastsNotification(); return
        }
        val nm: NotificationManager? = getSystemService(NotificationManager::class.java)
        completed.forEachIndexed { i, p ->
            val pi = PendingIntent.getService(
                this, 70000 + i,
                Intent(this, MediaPlayerService::class.java).apply {
                    action = ACTION_DELETE_SINGLE + p.path.hashCode()
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_delete)
                .setContentTitle("🗑️ ${p.name}")
                .setContentText("Antippen zum Löschen • Fertig angehört")
                .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true)
                .setContentIntent(pi).setGroup("podcast_delete").build()
            if (hasNotificationPermission())
                nm?.notify(70000 + i, n)
        }
        val summary = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_delete)
            .setContentTitle("Fertige Podcasts löschen")
            .setContentText("${completed.size} Podcasts bereit zum Löschen")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup("podcast_delete").setGroupSummary(true).setAutoCancel(true).build()
        if (hasNotificationPermission())
            nm?.notify(70999, summary)
    }

    private fun showNoCompletedPodcastsNotification() {
        postSimpleNotification(
            71000, android.R.drawable.ic_menu_info_details,
            "Keine fertigen Podcasts", "Es gibt aktuell keine fertigen Podcasts zum Löschen", 10000
        )
    }

    private fun deletePodcastFile(podcast: Podcast) {
        if (currentPodcast?.path == podcast.path) {
            pausePodcast()
            podcastPlayer?.release(); podcastPlayer = null
            currentPodcast = null; isPlayingPodcast = false
        }
        val file = File(podcast.path)
        if (file.exists() && file.delete()) {
            podcastPrefs.edit(commit = true) {
                remove(KEY_PREFIX_POSITION + podcast.path.hashCode())
                remove(KEY_PREFIX_COMPLETED + podcast.path.hashCode())
            }
            podcasts = podcasts.filter { it.path != podcast.path }
            showDeleteSuccessNotification(podcast.name)
            if (currentPodcast == null) updateNotification()
        } else {
            showDeleteErrorNotification(podcast.name)
        }
    }

    private fun showDeleteSuccessNotification(name: String) =
        postSimpleNotification(71001, android.R.drawable.ic_menu_delete, "✓ Gelöscht", name, 2000)

    private fun showDeleteErrorNotification(name: String) =
        postSimpleNotification(
            71002,
            android.R.drawable.ic_dialog_alert,
            "✗ Fehler beim Löschen",
            name,
            4000
        )

    private fun postSimpleNotification(
        id: Int,
        icon: Int,
        title: String,
        text: String,
        timeoutMs: Long
    ) {
        if (!hasNotificationPermission()) return
        val n = NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(icon)
            .setContentTitle(title).setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true)
            .setTimeoutAfter(timeoutMs).build()
        val nm: NotificationManager? = getSystemService(NotificationManager::class.java)
        nm?.notify(id, n)
    }

    private fun updateNotification() {
        val nm: NotificationManager? = getSystemService(NotificationManager::class.java)
        nm?.notify(
            NOTIFICATION_ID,
            buildNotification()
        )
        pushMediaStateToLaptop(this)
    }

    private fun buildNotification(): Notification =
        if (currentMode == MODE_MUSIC) buildMusicNotification() else buildPodcastNotification()

    private fun buildMusicNotification(): Notification {
        val active = getActivePlaylist()
        val songName = active.getOrNull(currentSongIndex)?.name ?: "Keine Playlist"
        val isFav = active.getOrNull(currentSongIndex)?.let { favoriteSongs.containsKey(it.path) } ?: false

        fun pi(reqCode: Int, action: String) = PendingIntent.getService(
            this, reqCode,
            Intent(this, MediaPlayerService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playlistLabel = when {
            activeAlgorithmicPlaylistId != null ->
                AlgorithmicPlaylistRegistry.findById(activeAlgorithmicPlaylistId!!)
                    ?.let { " [${it.icon}${it.name}]" } ?: ""
            activePlaylistId != null ->
                playlists.find { it.id == activePlaylistId }?.let { " [${it.name}]" } ?: ""
            else -> ""
        }

        val currentPositionMs = musicPlayer?.currentPosition?.toLong() ?: 0L

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("🎵 Musik Player")
            .setContentText(
                "$songName (${currentSongIndex + 1}/${active.size})$playlistLabel" +
                        "${if (isFav) " ⭐" else ""}${if (favoritesMode) " 💫" else ""}${if (isRepeatEnabled) " 🔁" else ""}"
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setDeleteIntent(pi(4, ACTION_NOTIFICATION_DELETED))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pi(3, ACTION_TOGGLE_REPEAT))
            .addAction(android.R.drawable.ic_media_previous, "Zurück", pi(0, ACTION_MUSIC_PREVIOUS))
            .addAction(
                if (isPlayingMusic) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlayingMusic) "Pause" else "Spielen",
                pi(1, if (isPlayingMusic) ACTION_MUSIC_PAUSE else ACTION_MUSIC_PLAY)
            )
            .addAction(android.R.drawable.ic_media_next, "Weiter", pi(2, ACTION_MUSIC_NEXT))

        if (isPlayingMusic) {
            builder
                .setWhen(System.currentTimeMillis() - currentPositionMs)
                .setUsesChronometer(true)
                .setShowWhen(true)
        } else {
            builder
                .setUsesChronometer(false)
                .setShowWhen(true)
                .setSubText(formatTime(currentPositionMs))
        }

        return builder.build()
    }

    private fun buildPodcastNotification(): Notification {
        val title = currentPodcast?.name ?: "Kein Podcast ausgewählt"
        val pos = podcastPlayer?.currentPosition?.toLong() ?: 0
        val dur = podcastPlayer?.duration?.toLong() ?: 0
        val progress = if (dur > 0) "${formatTime(pos)} / ${formatTime(dur)}" else "Bereit"
        val speedStr: String = podcastPlayer?.playbackParams?.speed?.toString() ?: ""

        fun pi(reqCode: Int, action: String, extraMs: Int? = null) = PendingIntent.getService(
            this, reqCode,
            Intent(this, MediaPlayerService::class.java).apply {
                this.action = action
                extraMs?.let { putExtra(EXTRA_FORWARD_MS, it) }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("🎙️ $title $speedStr")
            .setContentText(progress)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(pi(3, ACTION_SELECT_PODCAST))
            .setDeleteIntent(pi(4, ACTION_NOTIFICATION_DELETED))
            .setRequestPromotedOngoing(true)
            .addAction(android.R.drawable.ic_media_rew, "-15s", pi(0, ACTION_PODCAST_REWIND))
            .addAction(
                if (isPlayingPodcast) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlayingPodcast) "Pause" else "Play",
                pi(1, if (isPlayingPodcast) ACTION_PODCAST_PAUSE else ACTION_PODCAST_PLAY)
            )
            .addAction(android.R.drawable.ic_media_ff, "+15s", pi(2, ACTION_PODCAST_FORWARD))
            .build()
    }

    private fun loadPlaylist() {
        try {
            val songs = mutableListOf<Song>()
            val proj = arrayOf(
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TITLE
            )
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, null, null,
                "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name: String = cursor.getString(nameCol) ?: continue
                    val data: String = cursor.getString(dataCol) ?: continue
                    val title: String? = cursor.getString(titleCol)
                    val norm: String = try {
                        URLDecoder.decode(data, "UTF-8").replace("\\", "/").lowercase()
                    } catch (_: Exception) {
                        data.replace("\\", "/").lowercase()
                    }
                    val inCloud = norm.contains("/download/cloud/") ||
                            norm.contains("/downloads/cloud/") ||
                            data.contains("/Cloud/", ignoreCase = true)
                    if (inCloud && !norm.contains("/download/cloud/podcast")) {
                        val contentUri = Uri.withAppendedPath(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )
                        val accessible = try {
                            contentResolver.openFileDescriptor(contentUri, "r")?.use { true }
                                ?: false
                        } catch (_: Exception) {
                            false
                        }
                        val displayName =
                            if (!title.isNullOrBlank() && title != "<unknown>") title else name.substringBeforeLast(
                                '.'
                            )
                        val uri = if (accessible) contentUri else Uri.fromFile(File(data))
                        songs.add(Song(uri, displayName, data))
                    }
                }
            }
            playlist = songs.sortedBy { it.name.lowercase() }
            if (currentSongIndex >= playlist.size) {
                currentSongIndex = 0; saveMusicState()
            }
        } catch (_: Exception) {
            playlist = emptyList()
        }
    }

    private fun loadPodcasts() {
        try {
            val list = mutableListOf<Podcast>()
            val proj = arrayOf(
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA, MediaStore.Audio.Media.TITLE
            )
            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, proj, null, null,
                "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name: String = cursor.getString(nameCol) ?: continue
                    val data: String = cursor.getString(dataCol) ?: continue
                    val title: String? = cursor.getString(titleCol)
                    val norm: String = try {
                        URLDecoder.decode(data, "UTF-8").replace("\\", "/").lowercase()
                    } catch (_: Exception) {
                        data.replace("\\", "/").lowercase()
                    }
                    val inPodcasts = norm.contains("/download/cloud/podcasts/") ||
                            norm.contains("/downloads/cloud/podcasts/") ||
                            data.contains("/Cloud/Podcasts/", ignoreCase = true)
                    if (inPodcasts && (name.endsWith(".mp3") || name.endsWith(".m4a"))) {
                        val contentUri = Uri.withAppendedPath(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )
                        val displayName =
                            if (!title.isNullOrBlank() && title != "<unknown>") title else name.substringBeforeLast(
                                '.'
                            )
                        list.add(Podcast(contentUri, displayName, data))
                    }
                }
            }
            podcasts = list.map { p ->
                p.copy(
                    savedPosition = getPodcastSavedPosition(p.path),
                    isCompleted = getPodcastCompleted(p.path)
                )
            }.sortedBy { it.name }
        } catch (_: Exception) {
            podcasts = emptyList()
        }
    }

    private fun saveMusicState() {
        musicPrefs.edit {
            putInt(KEY_CURRENT_SONG_INDEX, currentSongIndex)
            putBoolean(KEY_REPEAT_MODE, isRepeatEnabled)
            putString(KEY_CURRENT_MODE, currentMode)
            putString("current_song_name", currentSongName)
            putString(KEY_ACTIVE_ALGORITHMIC_PLAYLIST, activeAlgorithmicPlaylistId)
        }
    }

    private fun getPodcastSavedPosition(path: String) =
        podcastPrefs.getLong(KEY_PREFIX_POSITION + path.hashCode(), 0L)

    private fun savePodcastPosition(path: String, pos: Long) =
        podcastPrefs.edit(commit = true) { putLong(KEY_PREFIX_POSITION + path.hashCode(), pos) }

    private fun getPodcastCompleted(path: String) =
        podcastPrefs.getBoolean(KEY_PREFIX_COMPLETED + path.hashCode(), false)

    private fun setPodcastCompleted(path: String, done: Boolean) =
        podcastPrefs.edit(commit = true) {
            putBoolean(
                KEY_PREFIX_COMPLETED + path.hashCode(),
                done
            )
        }

    private fun savePodcastCurrentPath(path: String) =
        podcastPrefs.edit(commit = true) { putString(KEY_CURRENT_PODCAST, path) }

    private fun podcastCurrentPosition() = podcastPlayer?.currentPosition?.toLong()

    private fun savePodcastCurrentPosition() {
        currentPodcast?.let { p ->
            podcastCurrentPosition()?.let {
                savePodcastPosition(
                    p.path,
                    it
                )
            }
        }
    }

    private fun getSavedPlaybackSpeed() = podcastPrefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f)

    private fun savePlaybackSpeed(speed: Float) =
        podcastPrefs.edit(commit = true) { putFloat(KEY_PLAYBACK_SPEED, speed) }

    private fun loadPodcastQueue() {
        val raw: String? = podcastPrefs.getString(KEY_PODCAST_QUEUE, null)
        podcastQueue =
            if (!raw.isNullOrEmpty()) raw.split("|||").toMutableList() else mutableListOf()
    }

    private fun savePodcastQueue() {
        podcastPrefs.edit(commit = true) {
            putString(
                KEY_PODCAST_QUEUE,
                podcastQueue.joinToString("|||")
            )
        }
    }

    private fun startPositionSaving() {
        var lastSaved = 0L
        positionSaveRunnable = object : Runnable {
            override fun run() {
                if (isServiceDestroyed) return

                if (isPlayingPodcast && podcastPlayer != null && currentPodcast != null) {
                    val pos = podcastPlayer?.currentPosition?.toLong() ?: 0
                    if (abs(pos - lastSaved) > 5000) {
                        savePodcastPosition(currentPodcast!!.path, pos)
                        updateNotification()
                        lastSaved = pos
                    }
                }
                handler.postDelayed(this, 60000)
            }
        }
        handler.post(positionSaveRunnable!!)
    }

    private fun registerBluetoothReceiver() {
        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_ACL_DISCONNECTED,
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                        if (currentMode == MODE_MUSIC) pauseMusic() else pausePodcast()
                    }
                }
            }
        }
        registerReceiver(bluetoothReceiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        })
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Media Player",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Musik & Podcast Wiedergabe"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
        }
        val nm: NotificationManager? = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(channel)
    }

    private fun getServiceForegroundType() = try {
        if (checkSelfPermission(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK) == PackageManager.PERMISSION_GRANTED)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        else ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    } catch (_: Exception) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
    }

    private fun formatTime(ms: Long): String {
        val h = TimeUnit.MILLISECONDS.toHours(ms)
        val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
        val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    private fun hasNotificationPermission(): Boolean = canPostNotifications

    private fun savePlaylists() {
        val json = playlists.joinToString("\n---\n") { pl ->
            "${pl.id}:::${pl.name}:::${pl.type}:::${pl.items.joinToString("|~~|")}"
        }
        musicPrefs.edit {
            putString(KEY_PLAYLISTS, json)
            putString(KEY_ACTIVE_PLAYLIST, activePlaylistId)
        }
    }

    private fun loadPlaylists() {
        val json: String? = musicPrefs.getString(KEY_PLAYLISTS, null)
        if (!json.isNullOrEmpty()) {
            try {
                playlists.clear()
                val lines = json.split("\n---\n")
                lines.forEach { line ->
                    val parts = line.split(":::", limit = 4)
                    if (parts.size >= 3) {
                        val id = parts[0]
                        val name = parts[1]
                        val type = PlaylistType.valueOf(parts[2])
                        val items = if (parts.size > 3 && parts[3].isNotEmpty())
                            parts[3].split("|~~|").toMutableList()
                        else mutableListOf()
                        playlists.add(Playlist(id, name, type, items))
                    }
                }
            } catch (_: Exception) {
            }
        }
        activePlaylistId = musicPrefs.getString(KEY_ACTIVE_PLAYLIST, null)
    }

    fun createPlaylist(name: String, type: PlaylistType): String {
        if (type != PlaylistType.MUSIC) {
            showSimpleNotificationExtern(
                "❌ Nicht unterstützt",
                "Nur Musik-Playlisten werden unterstützt",
                10.seconds,
                context = this
            )
            return ""
        }
        val playlist = Playlist(name = name, type = type)
        playlists.add(playlist)
        savePlaylists()
        return playlist.id
    }

    fun addToPlaylist(name: String, itemPath: String): Boolean {
        val pl = playlists.find { it.id == name } ?: return false

        if (pl.type != PlaylistType.MUSIC) {
            showSimpleNotificationExtern(
                "❌ Fehler",
                "Nur Songs können zu Playlisten hinzugefügt werden",
                10.seconds,
                context = this
            )
            return false
        }

        if (!pl.items.contains(itemPath)) {
            pl.items.add(itemPath)
            savePlaylists()
            return true
        }
        return false
    }

    fun deletePlaylist(playlistId: String): Boolean {
        val removed = playlists.removeIf { it.id == playlistId }
        if (removed) {
            if (activePlaylistId == playlistId) activePlaylistId = null
            savePlaylists()
        }
        return removed
    }

    fun activatePlaylist(playlistId: String): Boolean {
        activeAlgorithmicPlaylistId = null
        algorithmicPlaylistSongs = emptyList()
        val playlist = playlists.find { it.id == playlistId } ?: return false

        if (playlist.type != PlaylistType.MUSIC) {
            showSimpleNotificationExtern(
                "❌ Nicht unterstützt",
                "Nur Musik-Playlisten können abgespielt werden",
                10.seconds, context = this
            )
            return false
        }

        val playlistSongs = playlist.items.mapNotNull { path ->
            this.playlist.find { it.path == path }
        }

        if (playlistSongs.isEmpty()) return false

        this.playlist = playlistSongs
        currentSongIndex = 0
        activePlaylistId = playlistId
        savePlaylists()

        if (isPlayingPodcast) {
            savePodcastCurrentPosition()
            podcastPlayer?.pause()
            isPlayingPodcast = false
            musicPrefs.editAsync { putBoolean("is_playing", false) }
        }
        currentMode = MODE_MUSIC

        musicPlayer?.release()
        musicPlayer = null
        loadSong(0)

        saveMusicState()
        updateNotification()
        return true
    }

    fun deactivatePlaylist() {
        activePlaylistId = null
        activeAlgorithmicPlaylistId = null
        algorithmicPlaylistSongs = emptyList()
        savePlaylists()

        if (currentMode == MODE_MUSIC) {
            loadPlaylist()
            currentSongIndex = 0
            if (isPlayingMusic) {
                musicPlayer?.release()
                musicPlayer = null
                loadSong(0)
            }
            updateNotification()
        } else {
            podcastQueue.clear()
            savePodcastQueue()
        }
    }

    @SuppressLint("LaunchActivityFromNotification")
    private fun showPlaylistsNotification(type: PlaylistType? = null) {
        val filtered = if (type != null) playlists.filter { it.type == type } else playlists

        if (filtered.isEmpty()) {
            showSimpleNotificationExtern(
                "📂 Keine Playlisten",
                if (type != null) "Keine ${type.name.lowercase()}-Playlisten vorhanden" else "Keine Playlisten vorhanden",
                10.seconds,
                context = this
            )
            return
        }

        val nm: NotificationManager? = getSystemService(NotificationManager::class.java)

        filtered.forEachIndexed { i, pl ->
            val activateIntent = Intent(this, MediaPlayerService::class.java).apply {
                action = "ACTIVATE_PL_${pl.id}"
            }
            val activatePi = PendingIntent.getService(
                this, 80000 + i,
                activateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val deleteIntent = Intent(this, MediaPlayerService::class.java).apply {
                action = "DELETE_PL_${pl.id}"
            }
            val deletePi = PendingIntent.getService(
                this, 81000 + i,
                deleteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val typeIcon = if (pl.type == PlaylistType.MUSIC) "🎵" else "🎙️"
            val activeMarker = if (pl.id == activePlaylistId) "▶ " else ""

            val n = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("$activeMarker$typeIcon ${pl.name} ${pl.id}")
                .setContentText("${pl.items.size} Items • Tippen zum Abspielen")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(activatePi)
                .addAction(android.R.drawable.ic_menu_delete, "Löschen", deletePi)
                .setGroup("playlists")
                .build()

            if (hasNotificationPermission())
                nm?.notify(80000 + i, n)
        }

        val summary = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("📂 Playlisten")
            .setContentText("${filtered.size} ${type?.name?.lowercase() ?: ""} Playlisten")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup("playlists")
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        if (hasNotificationPermission())
            nm?.notify(80999, summary)
    }

    @UnstableApi
    private class DummyPlayer : Player {
        override fun getApplicationLooper(): Looper = Looper.getMainLooper()
        override fun addListener(listener: Player.Listener) {}
        override fun removeListener(listener: Player.Listener) {}
        override fun setMediaItems(mediaItems: MutableList<MediaItem>) {}
        override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {}
        override fun setMediaItems(
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ) {
        }

        override fun setMediaItem(mediaItem: MediaItem) {}
        override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {}
        override fun setMediaItem(mediaItem: MediaItem, resetPosition: Boolean) {}
        override fun addMediaItem(mediaItem: MediaItem) {}
        override fun addMediaItem(index: Int, mediaItem: MediaItem) {}
        override fun addMediaItems(mediaItems: MutableList<MediaItem>) {}
        override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {}
        override fun moveMediaItem(currentIndex: Int, newIndex: Int) {}
        override fun moveMediaItems(fromIndex: Int, toIndex: Int, newIndex: Int) {}
        override fun replaceMediaItem(index: Int, mediaItem: MediaItem) {}
        override fun replaceMediaItems(
            fromIndex: Int,
            toIndex: Int,
            mediaItems: MutableList<MediaItem>
        ) {
        }

        override fun removeMediaItem(index: Int) {}
        override fun removeMediaItems(fromIndex: Int, toIndex: Int) {}
        override fun clearMediaItems() {}
        override fun isCommandAvailable(command: Int) = false
        override fun canAdvertiseSession() = true
        override fun getAvailableCommands(): Player.Commands = Player.Commands.EMPTY
        override fun prepare() {}
        override fun getPlaybackState() = Player.STATE_IDLE
        override fun getPlaybackSuppressionReason() = Player.PLAYBACK_SUPPRESSION_REASON_NONE
        override fun isPlaying() = false
        override fun getPlayerError(): PlaybackException? = null
        override fun play() {}
        override fun pause() {}
        override fun setPlayWhenReady(playWhenReady: Boolean) {}
        override fun getPlayWhenReady() = false
        override fun setRepeatMode(repeatMode: Int) {}
        override fun getRepeatMode() = Player.REPEAT_MODE_OFF
        override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {}
        override fun getShuffleModeEnabled() = false
        override fun isLoading() = false
        override fun seekToDefaultPosition() {}
        override fun seekToDefaultPosition(mediaItemIndex: Int) {}
        override fun seekTo(positionMs: Long) {}
        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {}
        override fun getSeekBackIncrement() = 0L
        override fun seekBack() {}
        override fun getSeekForwardIncrement() = 0L
        override fun seekForward() {}
        override fun hasPreviousMediaItem() = false
        override fun seekToPreviousMediaItem() {}
        override fun getMaxSeekToPreviousPosition() = 0L
        override fun seekToPrevious() {}
        override fun hasNextMediaItem() = false
        override fun seekToNextMediaItem() {}
        override fun seekToNext() {}
        override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {}
        override fun setPlaybackSpeed(speed: Float) {}
        override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT
        override fun stop() {}
        override fun release() {}
        override fun getCurrentTracks(): Tracks = Tracks.EMPTY
        override fun getTrackSelectionParameters(): TrackSelectionParameters =
            TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT

        override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {}
        override fun getMediaMetadata(): MediaMetadata = MediaMetadata.EMPTY
        override fun getPlaylistMetadata(): MediaMetadata = MediaMetadata.EMPTY
        override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {}
        override fun getCurrentManifest(): Any? = null
        override fun getCurrentTimeline(): Timeline = Timeline.EMPTY
        override fun getCurrentPeriodIndex() = 0

        @Deprecated("Deprecated in Java")
        override fun getCurrentWindowIndex() = 0
        override fun getCurrentMediaItemIndex() = 0

        @Deprecated("Deprecated in Java")
        override fun getNextWindowIndex() = 0
        override fun getNextMediaItemIndex() = 0

        @Deprecated("Deprecated in Java")
        override fun getPreviousWindowIndex() = 0
        override fun getPreviousMediaItemIndex() = 0
        override fun getCurrentMediaItem(): MediaItem? = null
        override fun getMediaItemCount() = 0
        override fun getMediaItemAt(index: Int): MediaItem = throw IndexOutOfBoundsException()
        override fun getDuration() = 0L
        override fun getCurrentPosition() = 0L
        override fun getBufferedPosition() = 0L
        override fun getBufferedPercentage() = 0
        override fun getTotalBufferedDuration() = 0L

        @Deprecated("Deprecated in Java")
        override fun isCurrentWindowDynamic() = false
        override fun isCurrentMediaItemDynamic() = false

        @Deprecated("Deprecated in Java")
        override fun isCurrentWindowLive() = false
        override fun isCurrentMediaItemLive() = false
        override fun getCurrentLiveOffset() = 0L

        @Deprecated("Deprecated in Java")
        override fun isCurrentWindowSeekable() = false
        override fun isCurrentMediaItemSeekable() = false
        override fun isPlayingAd() = false
        override fun getCurrentAdGroupIndex() = 0
        override fun getCurrentAdIndexInAdGroup() = 0
        override fun getContentDuration() = 0L
        override fun getContentPosition() = 0L
        override fun getContentBufferedPosition() = 0L
        override fun getAudioAttributes(): AudioAttributes = AudioAttributes.DEFAULT
        override fun setVolume(volume: Float) {}
        override fun getVolume() = 1f
        override fun mute() {}
        override fun unmute() {}
        override fun clearVideoSurface() {}
        override fun clearVideoSurface(surface: Surface?) {}
        override fun setVideoSurface(surface: Surface?) {}
        override fun setVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {}
        override fun clearVideoSurfaceHolder(surfaceHolder: SurfaceHolder?) {}
        override fun setVideoSurfaceView(surfaceView: SurfaceView?) {}
        override fun clearVideoSurfaceView(surfaceView: SurfaceView?) {}
        override fun setVideoTextureView(textureView: TextureView?) {}
        override fun clearVideoTextureView(textureView: TextureView?) {}
        override fun getVideoSize(): VideoSize = VideoSize.UNKNOWN
        override fun getSurfaceSize(): Size = Size.UNKNOWN
        override fun getCurrentCues(): CueGroup = CueGroup.EMPTY_TIME_ZERO
        override fun getDeviceInfo(): DeviceInfo = DeviceInfo.UNKNOWN
        override fun getDeviceVolume() = 0
        override fun isDeviceMuted() = false

        @Deprecated("Deprecated in Java")
        override fun setDeviceVolume(volume: Int) {
        }

        override fun setDeviceVolume(volume: Int, flags: Int) {}

        @Deprecated("Deprecated in Java")
        override fun increaseDeviceVolume() {
        }

        override fun increaseDeviceVolume(flags: Int) {}

        @Deprecated("Deprecated in Java")
        override fun decreaseDeviceVolume() {
        }

        override fun decreaseDeviceVolume(flags: Int) {}

        @Deprecated("Deprecated in Java")
        override fun setDeviceMuted(muted: Boolean) {
        }

        override fun setDeviceMuted(muted: Boolean, flags: Int) {}
        override fun setAudioAttributes(
            audioAttributes: AudioAttributes,
            handleAudioFocus: Boolean
        ) {
        }
    }
    private fun savePodcastSession() {
        val podcast = currentPodcast ?: return
        if (podcastSessionStartedAt == 0L) return
        val listenedMs = System.currentTimeMillis() - podcastSessionStartedAt
        if (listenedMs < 3000) { podcastSessionStartedAt = 0L; return }

        val showId = podcast.path.let { path ->
            PodcastShowManager.resolveShowForEpisode(path, podcast.name)
        }
        val showName = PodcastShowManager.getShow(showId)?.name ?: podcast.name

        MediaAnalyticsManager.addSession(
            ListenSession(
                label = showName,
                type = "podcast",
                listenedMs = listenedMs,
                startedAt = podcastSessionStartedAt
            )
        )
        podcastSessionStartedAt = 0L
    }

    private fun activateAlgorithmicPlaylistInternal(sourceId: String, startIndex: Int = 0) {
        FavoritesPlaylist.setContext(this)
        MediaAnalyticsManager.init(this)
        val source = AlgorithmicPlaylistRegistry.findById(sourceId) ?: run {
            showSimpleNotificationExtern("❌ Fehler", "Playlist nicht gefunden", 10.seconds, context = this)
            return
        }
        val songs = source.getSongs(playlist, emptyMap())
        if (songs.isEmpty()) {
            showSimpleNotificationExtern("❌ Leer", "Keine Songs in \"${source.name}\"", 10.seconds, context = this)
            return
        }
        activeAlgorithmicPlaylistId = sourceId
        algorithmicPlaylistSongs = songs
        activePlaylistId = null
        currentSongIndex = startIndex.coerceIn(0, songs.lastIndex)
        ensureMusicMode()
        musicPlayer?.release(); musicPlayer = null
        saveMusicState()
        loadSong(currentSongIndex)
        showSimpleNotificationExtern(
            "▶ ${source.icon} ${source.name}",
            "${songs.size} Songs · ab #${currentSongIndex + 1}",
            10.seconds, context = this
        )
    }
}

object MusicPlayerServiceCompat {
    fun startService(context: Context) = MediaPlayerService.startMusicService(context)
    fun stopService(context: Context) = MediaPlayerService.stopService(context)
    fun startAndPlay(context: Context, number: Int? = null) =
        MediaPlayerService.startAndPlayMusic(context, number)

    fun toggleFavorite(context: Context) = MediaPlayerService.toggleFavorite(context)
    fun toggleFavoritesMode(context: Context) = MediaPlayerService.toggleFavoritesMode(context)
    fun showFavorites(context: Context) = MediaPlayerService.showFavorites(context)
}

object PodcastPlayerServiceCompat {
    fun startService(context: Context) = MediaPlayerService.startPodcastService(context)
    fun stopService(context: Context) = MediaPlayerService.stopService(context)
    fun sendPlayAction(context: Context) = MediaPlayerService.sendPodcastPlayAction(context)
    fun sendForwardAction(context: Context, ms: Int) =
        MediaPlayerService.sendPodcastForwardAction(context, ms)

    fun managePodcast(context: Context) = MediaPlayerService.managePodcast(context)
    fun setPlaybackSpeed(context: Context, speed: Float) =
        MediaPlayerService.setPlaybackSpeed(context, speed)
}

fun restartMusicPlayer(number: Int? = null, context: Context) {
    try {
        MediaPlayerService.startAndPlayMusic(context, number)
    } catch (_: Exception) {
        showSimpleNotificationExtern(
            "❌ Fehler",
            "Musik Player konnte nicht neu gestartet werden",
            10.seconds,
            context = context
        )
    }
}

private fun SharedPreferences.editAsync(block: SharedPreferences.Editor.() -> Unit) {
    edit {apply(block).apply()}
}