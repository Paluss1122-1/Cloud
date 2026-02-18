package com.example.cloud.service

import android.Manifest
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
import android.util.Log
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
import com.example.cloud.MainActivity
import com.example.cloud.musicstatstab.MusicStatsManager
import com.example.cloud.showSimpleNotificationExtern
import java.io.File
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

private var statsManager: MusicStatsManager? = null

@OptIn(UnstableApi::class)
class MediaPlayerService : MediaSessionService() {

    // ─────────────────────────────────────────────────────────────────────────
    // Companion / Public API
    // ─────────────────────────────────────────────────────────────────────────
    companion object {
        const val MODE_MUSIC = "music"
        const val MODE_PODCAST = "podcast"

        // Backward-compat constants (formerly on PodcastPlayerService.Companion)
        const val KEY_ACTIVE_SERVICE = "active_media_service"
        const val SERVICE_MUSIC = "music"
        const val SERVICE_PODCAST = "podcast"
        const val EXTRA_FORWARD_MS = "extra_forward_ms"
        const val EXTRA_SONG_INDEX = "extra_song_index"

        private const val CHANNEL_ID = "media_player_channel"
        private const val NOTIFICATION_ID = 888888

        // ── Music actions ──────────────────────────────────────────────────
        private const val ACTION_MUSIC_PLAY = "com.example.cloud.ACTION_MUSIC_PLAY"
        private const val ACTION_MUSIC_PAUSE = "com.example.cloud.ACTION_MUSIC_PAUSE"
        private const val ACTION_MUSIC_NEXT = "com.example.cloud.ACTION_MUSIC_NEXT"
        private const val ACTION_MUSIC_PREVIOUS = "com.example.cloud.ACTION_MUSIC_PREVIOUS"
        private const val ACTION_TOGGLE_REPEAT = "com.example.cloud.ACTION_TOGGLE_REPEAT"
        private const val ACTION_TOGGLE_FAVORITE = "com.example.cloud.ACTION_TOGGLE_FAVORITE"
        const val ACTION_TOGGLE_FAVORITES_MODE = "TOGGLE_FAVORITES_MODE"
        const val ACTION_SHOW_FAVORITES = "SHOW_FAVORITES"

        // ── Podcast actions ────────────────────────────────────────────────
        private const val ACTION_PODCAST_PLAY = "com.example.cloud.ACTION_PODCAST_PLAY"
        private const val ACTION_PODCAST_PAUSE = "com.example.cloud.ACTION_PODCAST_PAUSE"
        private const val ACTION_PODCAST_REWIND = "com.example.cloud.ACTION_PODCAST_REWIND"
        private const val ACTION_PODCAST_FORWARD = "com.example.cloud.ACTION_PODCAST_FORWARD"
        private const val ACTION_SELECT_PODCAST = "com.example.cloud.ACTION_SELECT_PODCAST"
        private const val ACTION_DELETE_SINGLE = "com.example.cloud.ACTION_DELETE_SINGLE_"
        const val ACTION_SHOW_DELETE_COMPLETED = "ACTION_SHOW_DELETE_COMPLETED"
        const val ACTION_SET_SPEED = "ACTION_SET_SPEED"
        const val EXTRA_SPEED = "SPEED"

        // ── Mode switch (explicit, e.g. from UI) ──────────────────────────
        const val ACTION_SWITCH_TO_MUSIC = "com.example.cloud.ACTION_SWITCH_TO_MUSIC"
        const val ACTION_SWITCH_TO_PODCAST = "com.example.cloud.ACTION_SWITCH_TO_PODCAST"

        private const val ACTION_NOTIFICATION_DELETED = "ACTION_NOTIFICATION_DELETED"

        // ── Prefs ──────────────────────────────────────────────────────────
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

        private const val TAG = "MediaPlayerService"

        // ── Factory helpers (Music) ────────────────────────────────────────

        /** Start service in music mode (or switch to it if already running). */
        fun startMusicService(context: Context) = context.startForegroundService(
            Intent(context, MediaPlayerService::class.java).apply {
                action = ACTION_SWITCH_TO_MUSIC
            }
        )

        /** Start service, switch to music mode and immediately play.
         *  @param number 1-based song index to jump to, or null to resume. */
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

        // ── Factory helpers (Podcast) ──────────────────────────────────────

        /** Start service in podcast mode (or switch to it if already running). */
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

        fun managePodcast(context: Context) = context.startService(
            Intent(context, MediaPlayerService::class.java).apply {
                action = ACTION_SHOW_DELETE_COMPLETED
            }
        )

        fun setPlaybackSpeed(context: Context, speed: Float) = context.startService(
            Intent(context, MediaPlayerService::class.java).apply {
                action = ACTION_SET_SPEED
                putExtra(EXTRA_SPEED, speed)
            }
        )

        fun stopService(context: Context) = context.stopService(
            Intent(context, MediaPlayerService::class.java)
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Data classes
    // ─────────────────────────────────────────────────────────────────────────
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
        val items: MutableList<String> = mutableListOf() // Paths der Songs/Podcasts
    )

    enum class PlaylistType {
        MUSIC, PODCAST
    }

    // ─────────────────────────────────────────────────────────────────────────
    // State
    // ─────────────────────────────────────────────────────────────────────────
    private var currentMode = MODE_MUSIC

    // Music
    private var musicPlayer: MediaPlayer? = null
    private var playlist: List<Song> = emptyList()
    private var currentSongIndex = 0
    private var isPlayingMusic = false
    private var isRepeatEnabled = false
    private var favoritesMode = false
    private val favoriteSongs = mutableSetOf<String>()

    // Podcast
    private var podcastPlayer: MediaPlayer? = null
    private var podcasts: List<Podcast> = emptyList()
    private var podcastQueue: MutableList<String> = mutableListOf()
    private var currentPodcast: Podcast? = null
    private var isPlayingPodcast = false
    private var positionSaveRunnable: Runnable? = null

    // Common
    private var mediaSession: MediaSession? = null
    private lateinit var musicPrefs: SharedPreferences
    private lateinit var podcastPrefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var bluetoothReceiver: BroadcastReceiver? = null
    private var lastPlayPauseTime = 0L
    private var playPauseCount = 0

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
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
        statsManager = MusicStatsManager(this)

        // Restore last podcast
        podcastPrefs.getString(KEY_CURRENT_PODCAST, null)?.let { path ->
            podcasts.find { it.path == path }?.let { p ->
                currentPodcast = p.copy(savedPosition = getPodcastSavedPosition(p.path))
            }
        }

        createMediaSession()
        startForeground(NOTIFICATION_ID, buildNotification(), getServiceForegroundType())
        startPositionSaving()
        registerBluetoothReceiver()

        Log.d(
            TAG,
            "Service created. mode=$currentMode playlist=${playlist.size} podcasts=${podcasts.size}"
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // Optional song-index override for music
        intent?.getIntExtra(EXTRA_SONG_INDEX, -1)?.takeIf { it > 0 }?.let { idx ->
            currentSongIndex = idx - 1
            saveMusicState()
        }

        when (val action = intent?.action) {

            // ── Explicit mode switches ─────────────────────────────────────
            ACTION_SWITCH_TO_MUSIC -> switchToMusic()
            ACTION_SWITCH_TO_PODCAST -> switchToPodcast()

            // ── Music ──────────────────────────────────────────────────────
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

            // ── Podcast ────────────────────────────────────────────────────
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

            else -> when {
                action?.startsWith("SELECT_") == true -> {
                    action.removePrefix("SELECT_").toIntOrNull()?.let { hash ->
                        podcasts.find { it.path.hashCode() == hash }?.let { selected ->
                            currentPodcast =
                                selected.copy(savedPosition = getPodcastSavedPosition(selected.path))
                            loadPodcast(currentPodcast!!)
                        }
                    }
                }

                action?.startsWith(ACTION_DELETE_SINGLE) == true -> {
                    action.removePrefix(ACTION_DELETE_SINGLE).toIntOrNull()?.let { hash ->
                        podcasts.find { it.path.hashCode() == hash }?.let { deletePodcastFile(it) }
                    }
                }
            }
        }

        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        super.onDestroy()
        try {
            getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
        try {
            bluetoothReceiver?.let { unregisterReceiver(it); bluetoothReceiver = null }
        } catch (_: Exception) {
        }
        positionSaveRunnable?.let { handler.removeCallbacks(it) }
        savePodcastCurrentPosition()

        // Stats: record service interrupt for currently loaded music
        playlist.getOrNull(currentSongIndex)?.let { song ->
            musicPlayer?.let { mp ->
                statsManager?.recordServiceInterrupt(
                    song.path,
                    mp.currentPosition.toLong(),
                    mp.duration.toLong()
                )
            }
        }

        musicPlayer?.release(); musicPlayer = null
        podcastPlayer?.release(); podcastPlayer = null
        mediaSession?.release(); mediaSession = null

        Log.d(TAG, "Service destroyed")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mode switching
    // ─────────────────────────────────────────────────────────────────────────
    private fun ensureMusicMode() {
        if (currentMode != MODE_MUSIC) switchToMusic()
    }

    private fun ensurePodcastMode() {
        if (currentMode != MODE_PODCAST) switchToPodcast()
    }

    private fun switchToMusic() {
        Log.d(TAG, "🔄 Switching to MUSIC mode")
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
        Log.d(TAG, "🔄 Switching to PODCAST mode")
        if (isPlayingMusic) {
            playlist.getOrNull(currentSongIndex)?.let { song ->
                statsManager?.recordSongPause(
                    song.path, song.name,
                    musicPlayer?.duration?.toLong() ?: 0,
                    musicPlayer?.currentPosition?.toLong() ?: 0
                )
            }
            musicPlayer?.pause()
            isPlayingMusic = false
        }
        currentMode = MODE_PODCAST
        saveMusicState()
        updateNotification()
    }

    /** Called from the media-button 5× quick-tap handler. */
    private fun switchModeViaButton() {
        if (currentMode == MODE_MUSIC) switchToPodcast() else switchToMusic()
        Log.d(TAG, "🔄 Mode switched via button to $currentMode")
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MediaSession
    // ─────────────────────────────────────────────────────────────────────────
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
                    val keyEvent =
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
                                    Log.d(
                                        TAG,
                                        "playPauseCount=$playPauseCount, gap=${now - lastPlayPauseTime}"
                                    )
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
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling media button", e)
                    }
                    return super.onMediaButtonEvent(session, controllerInfo, intent)
                }
            })
            .build()
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Music playback
    // ─────────────────────────────────────────────────────────────────────────
    private fun playMusic() {
        val active = getActivePlaylist()
        if (active.isEmpty()) {
            showSimpleNotificationExtern(
                "❌ Keine Songs",
                if (favoritesMode) "Keine Favoriten verfügbar" else "Playlist ist leer",
                context = this
            )
            updateNotification()
            return
        }

        if (musicPlayer != null && !isPlayingMusic) {
            active.getOrNull(currentSongIndex)?.let { statsManager?.recordSongResume(it.path) }
            musicPlayer?.start()
            isPlayingMusic = true
            updateNotification()
            return
        }

        if (musicPlayer == null) loadSong(currentSongIndex)
    }

    private fun loadSong(index: Int) {
        val active = getActivePlaylist()
        if (active.isEmpty() || index !in active.indices) return

        // Record end of previous song
        active.getOrNull(currentSongIndex)?.let { prev ->
            musicPlayer?.let { mp ->
                statsManager?.recordSongEnd(
                    prev.path, prev.name,
                    mp.duration.toLong(), mp.currentPosition.toLong(), false
                )
            }
        }

        musicPlayer?.release()
        musicPlayer = null
        val song = active[index]

        try {
            musicPlayer = MediaPlayer().apply {
                try {
                    setDataSource(applicationContext, song.uri)
                    prepare()
                    Log.d(TAG, "✓ Music loaded via Content URI")
                } catch (e: Exception) {
                    Log.w(TAG, "Content URI failed, trying file path", e)
                    reset()
                    setDataSource(song.path)
                    prepare()
                    Log.d(TAG, "✓ Music loaded via file path")
                }
                setOnCompletionListener { onSongComplete() }
                start()
            }
            statsManager?.recordSongStart(
                song.path,
                song.name,
                musicPlayer?.duration?.toLong() ?: 0
            )
            isPlayingMusic = true
            currentSongIndex = index
            saveMusicState()
            updateNotification()
            Log.d(TAG, "▶ Music: ${song.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading song at $index", e)
            val next = (index + 1) % active.size
            if (next != index) handler.postDelayed({ loadSong(next) }, 500)
            else {
                isPlayingMusic = false; updateNotification()
            }
        }
    }

    private fun pauseMusic() {
        if (!isPlayingMusic || musicPlayer?.isPlaying != true) return
        getActivePlaylist().getOrNull(currentSongIndex)?.let { song ->
            statsManager?.recordSongPause(
                song.path, song.name,
                musicPlayer?.duration?.toLong() ?: 0,
                musicPlayer?.currentPosition?.toLong() ?: 0
            )
        }
        musicPlayer?.pause()
        isPlayingMusic = false
        updateNotification()
        Log.d(TAG, "⏸ Music paused")
    }

    private fun nextSong() {
        val active = getActivePlaylist()
        if (active.isEmpty()) return
        active.getOrNull(currentSongIndex)?.let { statsManager?.recordSongSkip(it.path) }
        currentSongIndex = (currentSongIndex + 1) % active.size
        musicPlayer?.release(); musicPlayer = null
        loadSong(currentSongIndex)
        Log.d(TAG, "⏭ Next song index=$currentSongIndex")
    }

    private fun previousSong() {
        val active = getActivePlaylist()
        if (active.isEmpty()) return
        currentSongIndex = if (currentSongIndex - 1 < 0) active.size - 1 else currentSongIndex - 1
        musicPlayer?.release(); musicPlayer = null
        loadSong(currentSongIndex)
        Log.d(TAG, "⏮ Previous song index=$currentSongIndex")
    }

    private fun toggleRepeat() {
        isRepeatEnabled = !isRepeatEnabled
        musicPrefs.edit { putBoolean(KEY_REPEAT_MODE, isRepeatEnabled) }
        updateNotification()
        Log.d(TAG, "🔁 Repeat: $isRepeatEnabled")
    }

    private fun onSongComplete() {
        val active = getActivePlaylist()
        active.getOrNull(currentSongIndex)?.let { song ->
            val dur = musicPlayer?.duration?.toLong() ?: 0
            statsManager?.recordSongEnd(song.path, song.name, dur, dur, true)
        }

        if (isRepeatEnabled) {
            try {
                musicPlayer?.seekTo(0); musicPlayer?.start()
            } catch (e: Exception) {
                Log.e(TAG, "Repeat failed", e); nextSong()
            }
        } else {
            currentSongIndex = if (currentSongIndex + 1 >= active.size) 0 else currentSongIndex + 1
            musicPlayer?.release(); musicPlayer = null
            loadSong(currentSongIndex)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Music – favorites & active playlist
    // ─────────────────────────────────────────────────────────────────────────
    private fun getActivePlaylist() =
        if (favoritesMode) playlist.filter { favoriteSongs.contains(it.path) } else playlist

    private fun loadFavorites() {
        favoriteSongs.clear()
        favoriteSongs.addAll(musicPrefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet())
        favoritesMode = musicPrefs.getBoolean(KEY_FAVORITES_MODE, false)
        Log.d(TAG, "📂 Loaded ${favoriteSongs.size} favorites, mode=$favoritesMode")
    }

    private fun saveFavorites() {
        musicPrefs.edit {
            putStringSet(KEY_FAVORITES, favoriteSongs)
            putBoolean(KEY_FAVORITES_MODE, favoritesMode)
        }
    }

    private fun toggleFavorite(songPath: String? = null) {
        val path = songPath ?: playlist.getOrNull(currentSongIndex)?.path ?: return
        val name = playlist.find { it.path == path }?.name ?: "Unbekannt"
        if (favoriteSongs.contains(path)) {
            favoriteSongs.remove(path)
            showSimpleNotificationExtern("💔 Favorit entfernt", name, context = this)
        } else {
            favoriteSongs.add(path)
            showSimpleNotificationExtern("⭐ Favorit hinzugefügt", name, context = this)
        }
        saveFavorites()
        updateNotification()
        Log.d(TAG, "⭐ Toggled favorite: $path (total: ${favoriteSongs.size})")
    }

    private fun toggleFavoritesMode() {
        favoritesMode = !favoritesMode
        val active = getActivePlaylist()
        if (favoritesMode && active.isEmpty()) {
            showSimpleNotificationExtern(
                "❌ Keine Favoriten",
                "Füge zuerst Songs zu deinen Favoriten hinzu!",
                context = this
            )
            favoritesMode = false; saveFavorites(); return
        }
        currentSongIndex = 0; saveMusicState()
        showSimpleNotificationExtern(
            if (favoritesMode) "⭐ Favoriten-Modus aktiviert" else "📁 Alle Songs",
            "${active.size} Songs verfügbar", context = this
        )
        if (isPlayingMusic) {
            musicPlayer?.release(); musicPlayer = null; loadSong(currentSongIndex)
        } else updateNotification()
        Log.d(TAG, "💫 Favorites mode: $favoritesMode (${active.size} songs)")
    }

    private fun showFavorites() {
        if (favoriteSongs.isEmpty()) {
            showSimpleNotificationExtern(
                "📂 Favoriten",
                "Keine Favoriten gespeichert",
                context = this
            )
            return
        }
        val list = playlist.filter { favoriteSongs.contains(it.path) }
            .mapIndexed { i, s -> "${i + 1}. ${s.name}" }.joinToString("\n")
        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.btn_star_big_on)
            .setContentTitle("⭐ Favoriten (${favoriteSongs.size})")
            .setContentText("${favoriteSongs.size} Songs")
            .setStyle(NotificationCompat.BigTextStyle().bigText(list))
            .setPriority(NotificationCompat.PRIORITY_HIGH).setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify(77777, n)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Podcast playback
    // ─────────────────────────────────────────────────────────────────────────
    private fun playPodcast() {
        if (currentPodcast == null) {
            podcastPrefs.getString(KEY_CURRENT_PODCAST, null)?.let { path ->
                podcasts.find { it.path == path }?.let { p ->
                    currentPodcast = p.copy(savedPosition = getPodcastSavedPosition(p.path))
                    updateNotification()
                    Log.d(TAG, "✓ Restored last podcast: ${p.name}")
                }
            }
            if (currentPodcast == null) {
                showPodcastSelection(); return
            }
        }

        if (podcastPlayer != null && !isPlayingPodcast) {
            val savedPos = getPodcastSavedPosition(currentPodcast!!.path)
            if (savedPos > 1000) podcastPlayer?.seekTo((savedPos - 1000).toInt())
            podcastPlayer?.start(); isPlayingPodcast = true; updateNotification(); return
        }

        if (podcastPlayer == null) loadPodcast(currentPodcast!!)
    }

    private fun loadPodcast(podcast: Podcast) {
        podcastPlayer?.release(); podcastPlayer = null

        if (currentPodcast?.path != podcast.path) {
            currentPodcast = podcast
            savePodcastCurrentPath(podcast.path)
        }
        if (podcast.isCompleted) setPodcastCompleted(podcast.path, false)

        Log.d(TAG, "Loading podcast: ${podcast.name} at ${formatTime(podcast.savedPosition)}")

        try {
            podcastPlayer = MediaPlayer().apply {
                try {
                    setDataSource(applicationContext, podcast.uri)
                    prepare()
                    Log.d(TAG, "✓ Podcast loaded via Content URI")
                } catch (e: Exception) {
                    Log.w(TAG, "Content URI failed, trying file path", e)
                    reset(); setDataSource(podcast.path); prepare()
                    Log.d(TAG, "✓ Podcast loaded via file path")
                }

                val speed = getSavedPlaybackSpeed()
                if (speed != 1.0f) {
                    playbackParams = playbackParams.setSpeed(speed)
                    Log.d(TAG, "Applied speed: ${speed}x")
                }

                if (podcast.savedPosition > 0) {
                    seekTo(podcast.savedPosition.toInt())
                    Log.d(TAG, "⏩ Jumped to ${formatTime(podcast.savedPosition)}")
                }

                setOnCompletionListener { onPodcastComplete() }

                isPlayingPodcast = true
                currentPodcast = podcast.copy(isCompleted = false)
                savePodcastCurrentPath(podcast.path)
                updateNotification()
                start()
            }
            Log.d(TAG, "▶ Podcast: ${podcast.name}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading podcast: ${podcast.name}", e)
            isPlayingPodcast = false; updateNotification()
        }
    }

    private fun pausePodcast() {
        if (!isPlayingPodcast || podcastPlayer?.isPlaying != true) return
        val pos = podcastPlayer?.currentPosition?.toLong() ?: 0
        podcastPlayer?.pause(); isPlayingPodcast = false
        currentPodcast?.let { savePodcastPosition(it.path, pos) }
        updateNotification()
        Log.d(TAG, "⏸ Podcast paused at ${formatTime(pos)}")
    }

    private fun rewind() {
        val player = podcastPlayer ?: return
        val newPos = maxOf(0, player.currentPosition - SKIP_TIME_MS)
        player.seekTo(newPos)
        currentPodcast?.let { savePodcastPosition(it.path, newPos.toLong()) }
        updateNotification()
        Log.d(TAG, "⏪ Rewound to ${formatTime(newPos.toLong())}")
    }

    private fun forward(skipMs: Int = SKIP_TIME_MS) {
        val player = podcastPlayer ?: return
        val newPos = minOf(player.duration, player.currentPosition + skipMs)
        player.seekTo(newPos)
        currentPodcast?.let { savePodcastPosition(it.path, newPos.toLong()) }
        updateNotification()
        Log.d(TAG, "⏩ Forwarded to ${formatTime(newPos.toLong())}")
    }

    private fun onPodcastComplete() {
        currentPodcast?.let { podcast ->
            savePodcastPosition(podcast.path, 0)
            setPodcastCompleted(podcast.path, true)
            podcasts = podcasts.map {
                if (it.path == podcast.path) it.copy(savedPosition = 0, isCompleted = true) else it
            }
        }

        try {
            podcastPlayer?.stop(); podcastPlayer?.release(); podcastPlayer = null
        } catch (_: Exception) {
        }
        isPlayingPodcast = false
        loadPodcastQueue()

        if (podcastQueue.isNotEmpty()) {
            val nextPath = podcastQueue.removeAt(0); savePodcastQueue()
            val next = podcasts.find { it.path == nextPath }
            if (next != null) {
                currentPodcast = next.copy(savedPosition = getPodcastSavedPosition(next.path))
                savePodcastCurrentPath(next.path)
                loadPodcast(currentPodcast!!)
                Log.d(TAG, "✓ Auto-playing next from queue: ${next.name}")
            } else {
                Log.w(TAG, "Next podcast not found: $nextPath")
                if (podcastQueue.isNotEmpty()) onPodcastComplete()
                else {
                    currentPodcast = null; updateNotification()
                }
            }
        } else {
            currentPodcast = null
            updateNotification()
            Log.d(TAG, "Queue empty – playback finished")
        }
    }

    private fun setPlaybackSpeed(speed: Float) {
        val s = speed.coerceIn(0.5f, 3.0f)
        podcastPlayer?.let { player ->
            player.playbackParams = player.playbackParams.setSpeed(s)
            isPlayingPodcast = player.isPlaying
            savePlaybackSpeed(s); updateNotification()
            Log.d(TAG, "Speed set to ${s}x")
        } ?: run {
            savePlaybackSpeed(s)
            showSimpleNotificationExtern(
                "✓ Gespeichert",
                "Geschwindigkeit ${s}x wird beim nächsten Start verwendet",
                5.seconds, this
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Podcast – selection & delete notifications
    // ─────────────────────────────────────────────────────────────────────────
    private fun showPodcastSelection() {
        if (podcasts.isEmpty()) return
        val nm = getSystemService(NotificationManager::class.java)
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
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                nm.notify(60000 + i, n)
        }
        val completedCount = updated.count { it.isCompleted }
        val summary = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Podcast auswählen")
            .setContentText("${podcasts.size} Podcasts${if (completedCount > 0) " ($completedCount fertig)" else ""}")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup("podcast_selection").setGroupSummary(true).setAutoCancel(true).build()
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            nm.notify(60999, summary)
    }

    private fun showDeleteCompletedNotifications() {
        val completed = podcasts.map { it.copy(isCompleted = getPodcastCompleted(it.path)) }
            .filter { it.isCompleted }
        if (completed.isEmpty()) {
            showNoCompletedPodcastsNotification(); return
        }
        val nm = getSystemService(NotificationManager::class.java)
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
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
                nm.notify(70000 + i, n)
        }
        val summary = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_delete)
            .setContentTitle("Fertige Podcasts löschen")
            .setContentText("${completed.size} Podcasts bereit zum Löschen")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup("podcast_delete").setGroupSummary(true).setAutoCancel(true).build()
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            nm.notify(70999, summary)
        Log.d(TAG, "Showing ${completed.size} completed podcasts for deletion")
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
            Log.d(TAG, "✓ Deleted: ${podcast.name}")
        } else {
            Log.e(TAG, "✗ Failed to delete: ${podcast.name}")
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
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val n = NotificationCompat.Builder(this, CHANNEL_ID).setSmallIcon(icon)
            .setContentTitle(title).setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true)
            .setTimeoutAfter(timeoutMs).build()
        getSystemService(NotificationManager::class.java).notify(id, n)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Notification
    // ─────────────────────────────────────────────────────────────────────────
    private fun updateNotification() {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification()
        )
    }

    private fun buildNotification(): Notification =
        if (currentMode == MODE_MUSIC) buildMusicNotification() else buildPodcastNotification()

    private fun buildMusicNotification(): Notification {
        val active = getActivePlaylist()
        val songName = active.getOrNull(currentSongIndex)?.name ?: "Keine Playlist"
        val isFav =
            active.getOrNull(currentSongIndex)?.let { favoriteSongs.contains(it.path) } ?: false

        fun pi(reqCode: Int, action: String) = PendingIntent.getService(
            this, reqCode,
            Intent(this, MediaPlayerService::class.java).apply { this.action = action },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("🎵 Musik Player")
            .setContentText(
                "$songName (${currentSongIndex + 1}/${active.size})" +
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
            .build()
    }

    private fun buildPodcastNotification(): Notification {
        val title = currentPodcast?.name ?: "Kein Podcast ausgewählt"
        val pos = podcastPlayer?.currentPosition?.toLong() ?: 0
        val dur = podcastPlayer?.duration?.toLong() ?: 0
        val progress = if (dur > 0) "${formatTime(pos)} / ${formatTime(dur)}" else "Bereit"
        val speedStr = podcastPlayer?.playbackParams?.speed?.toString() ?: ""

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

    // ─────────────────────────────────────────────────────────────────────────
    // MediaStore loaders
    // ─────────────────────────────────────────────────────────────────────────
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
                    val name = cursor.getString(nameCol) ?: continue
                    val data = cursor.getString(dataCol) ?: continue
                    val title = cursor.getString(titleCol)
                    val norm = try {
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
            Log.d(TAG, "Playlist loaded: ${playlist.size} songs")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading playlist", e)
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
                    val name = cursor.getString(nameCol) ?: continue
                    val data = cursor.getString(dataCol) ?: continue
                    val title = cursor.getString(titleCol)
                    val norm = try {
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
            Log.d(TAG, "Podcasts loaded: ${podcasts.size}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading podcasts", e)
            podcasts = emptyList()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Prefs helpers
    // ─────────────────────────────────────────────────────────────────────────
    private fun saveMusicState() {
        musicPrefs.edit {
            putInt(KEY_CURRENT_SONG_INDEX, currentSongIndex)
            putBoolean(KEY_REPEAT_MODE, isRepeatEnabled)
            putString(KEY_CURRENT_MODE, currentMode)
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
        val raw = podcastPrefs.getString(KEY_PODCAST_QUEUE, null)
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

    // ─────────────────────────────────────────────────────────────────────────
    // Position auto-save (podcast)
    // ─────────────────────────────────────────────────────────────────────────
    private fun startPositionSaving() {
        var lastSaved = 0L
        positionSaveRunnable = object : Runnable {
            override fun run() {
                if (isPlayingPodcast && podcastPlayer != null && currentPodcast != null) {
                    val pos = podcastPlayer?.currentPosition?.toLong() ?: 0
                    if (abs(pos - lastSaved) > 5000) {
                        savePodcastPosition(currentPodcast!!.path, pos)
                        lastSaved = pos
                        updateNotification()
                    }
                }
                handler.postDelayed(this, 60000)
            }
        }
        handler.post(positionSaveRunnable!!)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Bluetooth / noisy audio
    // ─────────────────────────────────────────────────────────────────────────
    private fun registerBluetoothReceiver() {
        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_ACL_DISCONNECTED,
                    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                        Log.d(TAG, "Audio output disconnected – pausing")
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

    // ─────────────────────────────────────────────────────────────────────────
    // Misc helpers
    // ─────────────────────────────────────────────────────────────────────────
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
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
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

    // ─────────────────────────────────────────────────────────────────────────
    // DummyPlayer – required stub for MediaSession.Builder
    // ─────────────────────────────────────────────────────────────────────────
    @UnstableApi
    private inner class DummyPlayer : Player {
        override fun getApplicationLooper() = Looper.getMainLooper()
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
        override fun getAvailableCommands() = Player.Commands.EMPTY
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
        override fun getPlaybackParameters() = PlaybackParameters.DEFAULT
        override fun stop() {}
        override fun release() {}
        override fun getCurrentTracks() = Tracks.EMPTY
        override fun getTrackSelectionParameters() =
            TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT

        override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {}
        override fun getMediaMetadata() = MediaMetadata.EMPTY
        override fun getPlaylistMetadata() = MediaMetadata.EMPTY
        override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {}
        override fun getCurrentManifest(): Any? = null
        override fun getCurrentTimeline() = Timeline.EMPTY
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
        override fun getAudioAttributes() = AudioAttributes.DEFAULT
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
        override fun getVideoSize() = VideoSize.UNKNOWN
        override fun getSurfaceSize() = Size.UNKNOWN
        override fun getCurrentCues() = CueGroup.EMPTY_TIME_ZERO
        override fun getDeviceInfo() = DeviceInfo.UNKNOWN
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
}

// ─────────────────────────────────────────────────────────────────────────────
// Backward-compat shims
// Existing call sites that import MusicPlayerService.* or PodcastPlayerService.*
// can be updated to use these objects without any other change.
//
// ⚠️  These are NOT registered in AndroidManifest.xml – they are just Kotlin
//     objects that delegate every call to MediaPlayerService.
// ─────────────────────────────────────────────────────────────────────────────

/** Drop-in for the former MusicPlayerService companion object. */
object MusicPlayerServiceCompat {
    fun startService(context: Context) = MediaPlayerService.startMusicService(context)
    fun stopService(context: Context) = MediaPlayerService.stopService(context)
    fun startAndPlay(context: Context, number: Int?) =
        MediaPlayerService.startAndPlayMusic(context, number)

    fun sendPlayAction(context: Context) = MediaPlayerService.sendMusicPlayAction(context)
    fun toggleFavorite(context: Context) = MediaPlayerService.toggleFavorite(context)
    fun toggleFavoritesMode(context: Context) = MediaPlayerService.toggleFavoritesMode(context)
    fun showFavorites(context: Context) = MediaPlayerService.showFavorites(context)
}

/** Drop-in for the former PodcastPlayerService companion object. */
object PodcastPlayerServiceCompat {
    const val KEY_ACTIVE_SERVICE = MediaPlayerService.KEY_ACTIVE_SERVICE
    const val SERVICE_MUSIC = MediaPlayerService.SERVICE_MUSIC
    const val SERVICE_PODCAST = MediaPlayerService.SERVICE_PODCAST
    const val EXTRA_FORWARD_MS = MediaPlayerService.EXTRA_FORWARD_MS

    fun startService(context: Context) = MediaPlayerService.startPodcastService(context)
    fun stopService(context: Context) = MediaPlayerService.stopService(context)
    fun sendPlayAction(context: Context) = MediaPlayerService.sendPodcastPlayAction(context)
    fun sendForwardAction(context: Context, ms: Int) =
        MediaPlayerService.sendPodcastForwardAction(context, ms)

    fun managePodcast(context: Context) = MediaPlayerService.managePodcast(context)
    fun setPlaybackSpeed(context: Context, speed: Float) =
        MediaPlayerService.setPlaybackSpeed(context, speed)
}

/** Top-level helper – keeps callers of restartMusicPlayer() working unchanged. */
fun restartMusicPlayer(number: Int? = null, context: Context) {
    try {
        MediaPlayerService.startAndPlayMusic(context, number)
    } catch (e: Exception) {
        Log.e("MediaPlayerService", "Error restarting music player", e)
        showSimpleNotificationExtern(
            "❌ Fehler",
            "Musik Player konnte nicht neu gestartet werden",
            context = context
        )
    }
}