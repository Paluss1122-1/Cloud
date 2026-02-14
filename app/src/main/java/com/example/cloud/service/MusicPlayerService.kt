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
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.KeyEvent
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.ui.text.toLowerCase
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
import com.example.cloud.MainActivity
import com.example.cloud.musicstatstab.MusicStatsManager
import com.example.cloud.service.PodcastPlayerService.Companion.KEY_ACTIVE_SERVICE
import com.example.cloud.service.PodcastPlayerService.Companion.SERVICE_MUSIC
import com.example.cloud.service.PodcastPlayerService.Companion.SERVICE_PODCAST
import com.example.cloud.showSimpleNotificationExtern
import java.io.File
import java.net.URLDecoder

private var statsManager: MusicStatsManager? = null

class MusicPlayerService : MediaSessionService() {

    companion object {
        private const val EXTRA_SONG_INDEX = "extra_song_index"

        private const val CHANNEL_ID = "music_player_channel"
        private const val NOTIFICATION_ID = 888888

        private const val ACTION_PLAY = "com.example.cloud.ACTION_MUSIC_PLAY"
        private const val ACTION_PAUSE = "com.example.cloud.ACTION_MUSIC_PAUSE"
        private const val ACTION_NEXT = "com.example.cloud.ACTION_MUSIC_NEXT"
        private const val ACTION_PREVIOUS = "com.example.cloud.ACTION_MUSIC_PREVIOUS"
        private const val ACTION_TOGGLE_REPEAT = "com.example.cloud.ACTION_TOGGLE_REPEAT"

        private const val PREFS_NAME = "music_player_prefs"
        private const val KEY_CURRENT_SONG_INDEX = "current_song_index"
        private const val KEY_REPEAT_MODE = "repeat_mode"
        private const val MEDIA_STATE_PREFS = "media_state_prefs"
        private const val ACTION_NOTIFICATION_DELETED = "ACTION_NOTIFICATION_DELETED"
        private const val SERVICE_SWITCH_TIMEOUT = 20000L

        private const val KEY_FAVORITES = "favorite_songs"
        private const val KEY_FAVORITES_MODE = "favorites_only_mode"
        private const val ACTION_TOGGLE_FAVORITE = "com.example.cloud.ACTION_TOGGLE_FAVORITE"

        fun startService(context: Context) {
            val intent = Intent(context, MusicPlayerService::class.java)
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MusicPlayerService::class.java)
            context.stopService(intent)
        }

        fun toggleFavorite(context: Context) {
            val intent = Intent(context, MusicPlayerService::class.java).apply {
                action = ACTION_TOGGLE_FAVORITE
            }
            context.startService(intent)
        }

        fun toggleFavoritesMode(context: Context) {
            val intent = Intent(context, MusicPlayerService::class.java).apply {
                action = "TOGGLE_FAVORITES_MODE"
            }
            context.startService(intent)
        }

        fun showFavorites(context: Context) {
            val intent = Intent(context, MusicPlayerService::class.java).apply {
                action = "SHOW_FAVORITES"
            }
            context.startService(intent)
        }

        fun sendPlayAction(context: Context) {
            val intent = Intent(context, MusicPlayerService::class.java).apply {
                action = ACTION_PLAY
            }
            context.startService(intent)
        }

        fun startAndPlay(context: Context, number: Int?) {
            val intent = Intent(context, MusicPlayerService::class.java).apply {
                action = ACTION_PLAY
                if (number != null && number > 0) {
                    putExtra(EXTRA_SONG_INDEX, number)
                }
            }
            context.startForegroundService(intent)
        }
    }

    data class Song(
        val uri: Uri,
        val name: String,
        val path: String
    )

    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    private var isPlaying = false
    private var playlist: List<Song> = emptyList()
    private var currentSongIndex = 0
    private var isRepeatEnabled = false
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var mediaStatePrefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())
    private var bluetoothReceiver: BroadcastReceiver? = null
    private var lastPlayPauseTime = 0L
    private var playPauseCount = 0
    private var isSwitching = false

    private var favoritesMode = false
    private val favoriteSongs = mutableSetOf<String>()

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        // Nach Switch Podcast→Music: Hoerbuch wurde ggf. durch MediaButtonReceiver gestartet – stoppen, damit unsere Session Key-Events bekommt
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        mediaStatePrefs = getSharedPreferences(MEDIA_STATE_PREFS, MODE_PRIVATE)

        currentSongIndex = sharedPreferences.getInt(KEY_CURRENT_SONG_INDEX, 0)
        isRepeatEnabled = sharedPreferences.getBoolean(KEY_REPEAT_MODE, false)

        createNotificationChannel()
        loadPlaylist()
        loadFavorites()
        statsManager = MusicStatsManager(this)
        registerBluetoothReceiver()

        // FIX 1: Setze Service als aktiv beim Start
        setActiveService()

        mediaSession = MediaSession.Builder(this, DummyPlayer())
            .setId("MusicPlayerSession")
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
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
                    if (isSwitching) {
                        Log.d(
                            "MusicPlayerService",
                            "⊘ Ignoring media button - service switching in progress"
                        )
                        return true
                    }

                    if (!isThisServiceActive()) {
                        Log.d(
                            "MusicPlayerService",
                            "⊘ Not active service - passing media button to other session"
                        )
                        return false
                    }

                    try {
                        val keyEvent =
                            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
                        if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                            when (keyEvent.keyCode) {
                                KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                    val currentTime = System.currentTimeMillis()

                                    Log.d(
                                        "MusicPlayerService",
                                        "letzter klick ${currentTime - lastPlayPauseTime}"
                                    )

                                    if (currentTime - lastPlayPauseTime < SERVICE_SWITCH_TIMEOUT) {
                                        playPauseCount++
                                        Log.d("MusicPlayerService", "playPauseCount added")
                                        if (playPauseCount >= 5) {
                                            Log.d(
                                                "MusicPlayerService",
                                                "🔄 Switching to Podcast Player (deferred)"
                                            )
                                            playPauseCount = 0
                                            handler.post { switchToPodcastPlayer() }
                                            return true
                                        }
                                    } else {
                                        playPauseCount = 1
                                        Log.d("MusicPlayerService", "playPauseCount 1")
                                    }
                                    lastPlayPauseTime = currentTime

                                    if (isPlaying) pauseMusic() else playMusic()
                                    return true
                                }

                                KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                    nextSong()
                                    return true
                                }

                                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                    previousSong()
                                    return true
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MusicPlayerService", "Error handling media button event", e)
                    }
                    return super.onMediaButtonEvent(session, controllerInfo, intent)
                }
            })
            .build()

        startForeground(NOTIFICATION_ID, createNotification(), getServiceForegroundType())

        Log.d("MusicPlayerService", "Service created. Playlist size: ${playlist.size}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        intent?.getIntExtra(EXTRA_SONG_INDEX, -1)?.let { receivedIndex ->
            if (receivedIndex > 0) {
                currentSongIndex = receivedIndex - 1
                saveCurrentIndex()
            }
        }

        when (intent?.action) {
            ACTION_PLAY -> {
                Log.d("MusicPlayerService", "▶ ACTION_PLAY received")
                playMusic()
            }
            ACTION_PAUSE -> {
                Log.d("MusicPlayerService", "⏸ ACTION_PAUSE received")
                pauseMusic()
            }
            ACTION_NEXT -> nextSong()
            ACTION_PREVIOUS -> previousSong()
            ACTION_TOGGLE_REPEAT -> toggleRepeat()
            ACTION_NOTIFICATION_DELETED -> {
                Log.d("MusicPlayerService", "Notification deleted - stopping service")
                val intent = Intent(this, MusicPlayerService::class.java)
                stopService(intent)
            }
            ACTION_TOGGLE_FAVORITE -> {
                Log.d("MusicPlayerService", "⭐ Toggle favorite action")
                toggleFavorite()
            }
            "SHOW_FAVORITES" -> {
                Log.d("MusicPlayerService", "📂 Show favorites action")
                showFavorites()
            }
            "TOGGLE_FAVORITES_MODE" -> {
                Log.d("MusicPlayerService", "💫 Toggle favorites mode action")
                toggleFavoritesMode()
            }
        }
        return START_STICKY
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Musik Player",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Steuerung für Musik-Wiedergabe"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun getServiceForegroundType(): Int {
        return try {
            if (checkSelfPermission(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK)
                == PackageManager.PERMISSION_GRANTED
            ) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
        } catch (_: Exception) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }
    }

    private fun loadPlaylist() {
        try {
            val songs = mutableListOf<Song>()
            loadFromMediaStore(songs)
            playlist = songs.sortedBy { it.name.lowercase() }

            if (currentSongIndex >= playlist.size) {
                currentSongIndex = 0
                saveCurrentIndex()
            }

        } catch (e: Exception) {
            Log.e("MusicPlayerService", "Error loading playlist", e)
            playlist = emptyList()
        }
    }

    private fun loadFromMediaStore(songs: MutableList<Song>) {
        try {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.TITLE
            )

            val sortOrder = "${MediaStore.Audio.Media.DISPLAY_NAME} ASC"

            contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val titleColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)

                Log.d("MusicPlayerService", "MediaStore total audio files: ${cursor.count}")

                var cloudCount = 0

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: continue
                    val data = cursor.getString(dataColumn) ?: continue
                    val title = cursor.getString(titleColumn)

                    val normalizedPath = try {
                        URLDecoder.decode(data, "UTF-8")
                            .replace("\\", "/")
                            .lowercase()
                    } catch (_: Exception) {
                        data.replace("\\", "/").lowercase()
                    }

                    val isInCloud = normalizedPath.contains("/download/cloud/") ||
                            normalizedPath.contains("/downloads/cloud/") ||
                            data.contains("/Cloud/", ignoreCase = true)

                    if (isInCloud && !normalizedPath.contains("/download/cloud/podcast")) {
                        val contentUri = Uri.withAppendedPath(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )

                        val isAccessible = try {
                            contentResolver.openFileDescriptor(contentUri, "r")?.use { true }
                                ?: false
                        } catch (e: Exception) {
                            Log.w("MusicPlayerService", "Cannot access URI for $name: ${e.message}")
                            false
                        }

                        val displayName = if (!title.isNullOrBlank() && title != "<unknown>") {
                            title
                        } else {
                            name.substringBeforeLast('.')
                        }

                        if (!isAccessible) {
                            Log.w("MusicPlayerService", "⚠ Using file URI for inaccessible: $name")
                            val fileUri = Uri.fromFile(File(data))
                            songs.add(
                                Song(
                                    uri = fileUri,
                                    name = displayName,
                                    path = data
                                )
                            )
                            Log.d("MusicPlayerService", "✓ Added via file URI: '$displayName'")
                            continue
                        }

                        songs.add(
                            Song(
                                uri = contentUri,
                                name = displayName,
                                path = data
                            )
                        )
                        cloudCount++
                        Log.d(
                            "MusicPlayerService",
                            "✓ Added from Cloud: '$displayName' (file: '$name')"
                        )
                    }
                }

                Log.d("MusicPlayerService", "Summary: $cloudCount songs found in Cloud folder")
            }

        } catch (e: Exception) {
            Log.e("MusicPlayerService", "Error loading from MediaStore", e)
        }
    }

    private fun playMusic() {
        try {
            Log.d("MusicPlayerService", "🎵 playMusic() called - isPlaying=$isPlaying, mediaPlayer=$mediaPlayer")

            val activePlaylist = getActivePlaylist()
            if (activePlaylist.isEmpty()) {
                Log.w("MusicPlayerService", "Active playlist is empty, cannot play")
                showSimpleNotificationExtern(
                    "❌ Keine Songs",
                    if (favoritesMode) "Keine Favoriten verfügbar" else "Playlist ist leer",
                    context = this
                )
                updateNotification()
                return
            }

            // FIX 4: Stelle sicher, dass Service als aktiv markiert ist
            setActiveService()

            if (mediaPlayer != null && !isPlaying) {
                val song = activePlaylist.getOrNull(currentSongIndex)
                if (song != null) {
                    statsManager?.recordSongResume(song.path)
                }

                mediaPlayer?.start()
                isPlaying = true
                Log.d("MusicPlayerService", "▶ Resumed playback")
                updateNotification()
                return
            }

            if (mediaPlayer == null) {
                loadSong(currentSongIndex)
            }

        } catch (e: Exception) {
            Log.e("MusicPlayerService", "Error playing music", e)
        }
    }

    private fun loadSong(index: Int) {
        try {
            val activePlaylist = getActivePlaylist()
            if (activePlaylist.isEmpty() || index < 0 || index >= activePlaylist.size) {
                Log.w("MusicPlayerService", "Invalid song index: $index")
                return
            }

            // WICHTIG: Hole Song aus activePlaylist, nicht aus playlist!
            val previousSong = activePlaylist.getOrNull(currentSongIndex)
            if (previousSong != null && mediaPlayer != null) {
                val currentPosition = (mediaPlayer?.currentPosition ?: 0L).toLong()
                val duration = mediaPlayer?.duration ?: 0L

                statsManager?.recordSongEnd(
                    previousSong.path,
                    previousSong.name,
                    duration.toLong(),
                    currentPosition,
                    isCompleted = false
                )
            }

            mediaPlayer?.release()
            mediaPlayer = null

            // WICHTIG: Verwende activePlaylist statt playlist!
            val song = activePlaylist[index]
            Log.d("MusicPlayerService", "Loading song [$index]: ${song.name}")

            mediaPlayer = MediaPlayer().apply {
                try {
                    setDataSource(applicationContext, song.uri)
                    prepare()
                    Log.d("MusicPlayerService", "✓ Loaded via Content URI")
                } catch (e: Exception) {
                    Log.w("MusicPlayerService", "Failed with Content URI, trying file path", e)

                    try {
                        reset()
                        setDataSource(song.path)
                        prepare()
                        Log.d("MusicPlayerService", "✓ Loaded via file path")
                    } catch (e2: Exception) {
                        Log.e("MusicPlayerService", "Failed with both methods", e2)
                        throw e2
                    }
                }

                setOnCompletionListener {
                    onSongComplete()
                }
                start()
            }

            val duration = mediaPlayer?.duration ?: 0L
            statsManager?.recordSongStart(song.path, song.name, duration.toLong())

            isPlaying = true
            currentSongIndex = index
            saveCurrentIndex()
            updateNotification()

            Log.d("MusicPlayerService", "▶ Now playing: ${song.name}")

        } catch (e: Exception) {
            Log.e("MusicPlayerService", "Error loading song at index $index", e)

            val activePlaylist = getActivePlaylist()
            if (index < activePlaylist.size) {
                Log.e("MusicPlayerService", "Skipping broken file: ${activePlaylist[index].path}")
            }

            val nextIndex = (index + 1) % activePlaylist.size
            if (nextIndex != index && nextIndex < activePlaylist.size) {
                handler.postDelayed({
                    loadSong(nextIndex)
                }, 500)
            } else {
                isPlaying = false
                updateNotification()
            }
        }
    }

    private fun pauseMusic() {
        try {
            Log.d("MusicPlayerService", "⏸ pauseMusic() called - isPlaying=$isPlaying")

            if (isPlaying && mediaPlayer?.isPlaying == true) {
                val activePlaylist = getActivePlaylist()
                val song = activePlaylist.getOrNull(currentSongIndex)
                if (song != null) {
                    val currentPosition = (mediaPlayer?.currentPosition ?: 0L).toLong()
                    val duration = mediaPlayer?.duration ?: 0L

                    statsManager?.recordSongPause(
                        song.path,
                        song.name,
                        duration.toLong(),
                        currentPosition
                    )
                }

                mediaPlayer?.pause()
                isPlaying = false
                updateNotification()
                Log.d("MusicPlayerService", "⏸ Music paused at index $currentSongIndex")
            }
        } catch (e: Exception) {
            Log.e("MusicPlayerService", "Error pausing music", e)
        }
    }

    private fun nextSong() {
        try {
            val activePlaylist = getActivePlaylist()
            if (activePlaylist.isEmpty()) return

            val currentSong = activePlaylist.getOrNull(currentSongIndex)
            if (currentSong != null) {
                statsManager?.recordSongSkip(currentSong.path)
            }

            currentSongIndex = (currentSongIndex + 1) % activePlaylist.size
            mediaPlayer?.release()
            mediaPlayer = null
            loadSong(currentSongIndex)

            Log.d("MusicPlayerService", "⏭ Next song: index $currentSongIndex")

        } catch (e: Exception) {
            Log.e("MusicPlayerService", "Error skipping to next song", e)
        }
    }

    private fun previousSong() {
        try {
            val activePlaylist = getActivePlaylist()
            if (activePlaylist.isEmpty()) return
            currentSongIndex = if (currentSongIndex - 1 < 0) {
                activePlaylist.size - 1
            } else {
                currentSongIndex - 1
            }

            mediaPlayer?.release()
            mediaPlayer = null
            loadSong(currentSongIndex)

            Log.d("MusicPlayerService", "⏮ Previous song: index $currentSongIndex")

        } catch (e: Exception) {
            Log.e("MusicPlayerService", "Error skipping to previous song", e)
        }
    }

    private fun toggleRepeat() {
        isRepeatEnabled = !isRepeatEnabled
        sharedPreferences.edit {
            putBoolean(KEY_REPEAT_MODE, isRepeatEnabled)
        }
        updateNotification()
        Log.d("MusicPlayerService", "🔁 Repeat mode: $isRepeatEnabled")
    }

    private fun onSongComplete() {
        Log.d("MusicPlayerService", "Song completed at index $currentSongIndex")

        val activePlaylist = getActivePlaylist()
        if (currentSongIndex < activePlaylist.size) {
            val song = activePlaylist[currentSongIndex]
            val duration = (mediaPlayer?.duration ?: 0L).toLong()

            statsManager?.recordSongEnd(
                song.path,
                song.name,
                duration,
                duration,
                isCompleted = true
            )
        }

        if (isRepeatEnabled) {
            try {
                mediaPlayer?.seekTo(0)
                mediaPlayer?.start()
            } catch (e: Exception) {
                Log.e("MusicPlayerService", "Error repeating song", e)
                nextSong()
            }
        } else {
            if (currentSongIndex + 1 >= activePlaylist.size) {
                currentSongIndex = 0
            } else {
                currentSongIndex++
            }

            mediaPlayer?.release()
            mediaPlayer = null
            loadSong(currentSongIndex)
        }
    }

    private fun saveCurrentIndex() {
        sharedPreferences.edit {
            putInt(KEY_CURRENT_SONG_INDEX, currentSongIndex)
        }
    }

    private fun createNotification(): Notification {
        return buildNotification()
    }

    private fun updateNotification() {
        val activeService = mediaStatePrefs.getString(
            KEY_ACTIVE_SERVICE,
            SERVICE_MUSIC
        ) // oder SERVICE_MUSIC je nach Service

        Log.d("MusicPlayerService", "updateNotification() called - activeService=$activeService")
        // Wenn wir NICHT der aktive Service sind, keine Notification anzeigen
        if (activeService != SERVICE_MUSIC) { // oder SERVICE_MUSIC
            val notificationManager = getSystemService(NotificationManager::class.java)
            stopForeground(STOP_FOREGROUND_REMOVE)
            notificationManager.cancel(NOTIFICATION_ID)
            Log.d("MusicPlayerService", "⊘ Not active service - notification canceled")
            return
        }

        val notification = buildNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d("PodcastPlayerService", "🔔 Notification updated")
    }

    private fun buildNotification(): Notification {
        val activePlaylist = getActivePlaylist()
        val currentSong =
            if (activePlaylist.isNotEmpty() && currentSongIndex < activePlaylist.size) {
                activePlaylist[currentSongIndex].name
            } else {
                "Keine Playlist"
            }

        val isFavorite = activePlaylist.getOrNull(currentSongIndex)?.let {
            favoriteSongs.contains(it.path)
        } ?: false

        val playPauseIntent = Intent(this, MusicPlayerService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 0, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previousIntent = Intent(this, MusicPlayerService::class.java).apply {
            action = ACTION_PREVIOUS
        }
        val previousPendingIntent = PendingIntent.getService(
            this, 1, previousIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, MusicPlayerService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this, 2, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleRepeatIntent = Intent(this, MusicPlayerService::class.java).apply {
            action = ACTION_TOGGLE_REPEAT
        }
        val toggleRepeatPendingIntent = PendingIntent.getService(
            this, 3, toggleRepeatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = Intent(this, MusicPlayerService::class.java).apply {
            action = ACTION_NOTIFICATION_DELETED
        }
        val deletePendingIntent = PendingIntent.getService(
            this,
            4,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("🎵 Musik Player")
            .setContentText("$currentSong (${currentSongIndex + 1}/${activePlaylist.size}) ${if (isFavorite) "⭐" else ""} ${if (favoritesMode) "💫" else ""} ${if (isRepeatEnabled) "🔁" else ""}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setDeleteIntent(deletePendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(toggleRepeatPendingIntent)

        if (playlist.isNotEmpty()) {
            builder
                .addAction(
                    android.R.drawable.ic_media_previous,
                    "Zurück",
                    previousPendingIntent
                )
                .addAction(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Pause" else "Spielen",
                    playPausePendingIntent
                )
                .addAction(
                    android.R.drawable.ic_media_next,
                    "Weiter",
                    nextPendingIntent
                )
        }

        return builder.build()
    }

    private fun switchToPodcastPlayer() {
        try {
            isSwitching = true

            // Timeout-Schutz
            handler.postDelayed({
                if (isSwitching) {
                    Log.w("MusicPlayerService", "⚠ Switching timeout - resetting flag")
                    isSwitching = false
                }
            }, 5000)

            // 1. Playback pausieren
            if (isPlaying && mediaPlayer?.isPlaying == true) {
                mediaPlayer?.pause()
                isPlaying = false

                val song = playlist.getOrNull(currentSongIndex)
                if (song != null) {
                    val currentPosition = (mediaPlayer?.currentPosition ?: 0L).toLong()
                    val duration = mediaPlayer?.duration?.toLong() ?: 0L
                    statsManager?.recordSongPause(song.path, song.name, duration, currentPosition)
                }
                Log.d("MusicPlayerService", "✓ Paused playback")
            }

            // 2. WICHTIG: MediaSession SOFORT deaktivieren
            try {
                mediaSession?.player?.stop()
                Log.d("MusicPlayerService", "✓ MediaSession deactivated")
            } catch (e: Exception) {
                Log.e("MusicPlayerService", "Error deactivating MediaSession", e)
            }

            // 4. Notification entfernen
            updateNotification()
            // 3. Service-Status wechseln
            mediaStatePrefs.edit(commit = true) {
                putString(KEY_ACTIVE_SERVICE, SERVICE_PODCAST)
            }
            Log.d("MusicPlayerService", "✓ Service status switched to PODCAST")

            Log.d("MusicPlayerService", "✓ Notification removed")

            // 5. Podcast Service starten (kurze Verzögerung für Session-Release)
            handler.postDelayed({
                try {
                    PodcastPlayerService.startService(this)
                    PodcastPlayerService.sendPlayAction(this)
                    Log.d("MusicPlayerService", "✓ Podcast Player started")
                    handler.postDelayed({
                        val intent = Intent(this, MusicPlayerService::class.java)
                        stopService(intent)
                    }, 500)
                } catch (e: Exception) {
                    Log.e("MusicPlayerService", "Error starting Podcast Player", e)
                    isSwitching = false
                }
            }, 300)

            handler.postDelayed({
                isSwitching = false
            }, 1000)

        } catch (e: Exception) {
            Log.e("MusicPlayerService", "Error switching to Podcast Player", e)
            isSwitching = false
        }
    }

    private fun loadFavorites() {
        val savedFavorites = sharedPreferences.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
        favoriteSongs.clear()
        favoriteSongs.addAll(savedFavorites)
        favoritesMode = sharedPreferences.getBoolean(KEY_FAVORITES_MODE, false)

        Log.d("MusicPlayerService", "📂 Loaded ${favoriteSongs.size} favorites, mode=$favoritesMode")
    }

    private fun saveFavorites() {
        sharedPreferences.edit {
            putStringSet(KEY_FAVORITES, favoriteSongs)
            putBoolean(KEY_FAVORITES_MODE, favoritesMode)
        }
        Log.d("MusicPlayerService", "💾 Saved ${favoriteSongs.size} favorites")
    }

    fun toggleFavorite(songPath: String? = null) {
        val path = songPath ?: playlist.getOrNull(currentSongIndex)?.path
        if (path == null) {
            Log.w("MusicPlayerService", "No song to favorite")
            return
        }

        if (favoriteSongs.contains(path)) {
            favoriteSongs.remove(path)
            showSimpleNotificationExtern(
                "💔 Favorit entfernt",
                playlist.find { it.path == path }?.name ?: "Unbekannt",
                context = this
            )
        } else {
            favoriteSongs.add(path)
            showSimpleNotificationExtern(
                "⭐ Favorit hinzugefügt",
                playlist.find { it.path == path }?.name ?: "Unbekannt",
                context = this
            )
        }

        saveFavorites()
        updateNotification()
        Log.d("MusicPlayerService", "⭐ Toggled favorite: $path (total: ${favoriteSongs.size})")
    }

    fun toggleFavoritesMode() {
        favoritesMode = !favoritesMode
        saveFavorites()

        val activePlaylist = getActivePlaylist()

        if (favoritesMode && activePlaylist.isEmpty()) {
            showSimpleNotificationExtern(
                "❌ Keine Favoriten",
                "Füge zuerst Songs zu deinen Favoriten hinzu!",
                context = this
            )
            favoritesMode = false
            saveFavorites()
            return
        }

        // Reset zur ersten Song in der neuen Playlist
        currentSongIndex = 0
        saveCurrentIndex()

        showSimpleNotificationExtern(
            if (favoritesMode) "⭐ Favoriten-Modus aktiviert" else "📁 Alle Songs",
            "${activePlaylist.size} Songs verfügbar",
            context = this
        )

        // Wenn etwas spielt, zum ersten Song der neuen Playlist wechseln
        if (isPlaying) {
            mediaPlayer?.release()
            mediaPlayer = null
            loadSong(currentSongIndex)
        } else {
            // Auch wenn nicht spielt, Notification aktualisieren
            updateNotification()
        }

        Log.d("MusicPlayerService", "🎵 Favorites mode: $favoritesMode (${activePlaylist.size} songs)")
    }

    private fun getActivePlaylist(): List<Song> {
        return if (favoritesMode) {
            playlist.filter { favoriteSongs.contains(it.path) }
        } else {
            playlist
        }
    }

    fun showFavorites() {
        if (favoriteSongs.isEmpty()) {
            showSimpleNotificationExtern(
                "📂 Favoriten",
                "Keine Favoriten gespeichert",
                context = this
            )
            return
        }

        val favoriteList = playlist
            .filter { favoriteSongs.contains(it.path) }
            .mapIndexed { index, song -> "${index + 1}. ${song.name}" }
            .joinToString("\n")

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.btn_star_big_on)
            .setContentTitle("⭐ Favoriten (${favoriteSongs.size})")
            .setContentText("${favoriteSongs.size} Songs")
            .setStyle(NotificationCompat.BigTextStyle().bigText(favoriteList))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(77777, notification)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Notification entfernen beim Destroy
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.cancel(NOTIFICATION_ID)
            Log.d("MusicPlayerService", "✓ Notification canceled in onDestroy")
        } catch (e: Exception) {
            Log.e("MusicPlayerService", "Error canceling notification", e)
        }

        try {
            bluetoothReceiver?.let {
                unregisterReceiver(it)
                bluetoothReceiver = null
            }
        } catch (e: Exception) {
            Log.e("MusicPlayerService", "Error unregistering receiver", e)
        }

        if (!isSwitching) {
            clearActiveService()
        }

        isSwitching = false

        val currentSong = playlist.getOrNull(currentSongIndex)
        if (currentSong != null && mediaPlayer != null) {
            val currentPosition = (mediaPlayer?.currentPosition ?: 0L).toLong()
            val duration = mediaPlayer?.duration ?: 0L

            statsManager?.recordServiceInterrupt(
                currentSong.path,
                currentPosition,
                duration.toLong()
            )
        }

        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession?.release()
        mediaSession = null
    }

    private fun setActiveService() {
        mediaStatePrefs.edit(commit = true) {
            putString(
                PodcastPlayerService.KEY_ACTIVE_SERVICE,
                PodcastPlayerService.SERVICE_MUSIC
            )
        }
    }

    private fun clearActiveService() {
        val currentActive = mediaStatePrefs.getString(PodcastPlayerService.KEY_ACTIVE_SERVICE, null)
        if (currentActive == PodcastPlayerService.SERVICE_MUSIC) {
            mediaStatePrefs.edit(commit = true) {
                remove(PodcastPlayerService.KEY_ACTIVE_SERVICE)
            }
        }
    }

    private fun isThisServiceActive(): Boolean {
        val activeService = mediaStatePrefs.getString(PodcastPlayerService.KEY_ACTIVE_SERVICE, null)
        val isActive = activeService == PodcastPlayerService.SERVICE_MUSIC
        return isActive
    }

    @UnstableApi
    private inner class DummyPlayer : Player {
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

        @OptIn(UnstableApi::class)
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

    private fun registerBluetoothReceiver() {
        // Sicherstellen, dass nicht doppelt registriert wird
        try {
            bluetoothReceiver?.let {
                unregisterReceiver(it)
            }
        } catch (e: Exception) {
            // Ignorieren wenn nicht registriert
        }

        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        pauseMusic()
                    }

                    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                        pauseMusic()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }

        registerReceiver(bluetoothReceiver, filter)
    }
}

fun restartMusicPlayer(number: Int? = null, context: Context) {
    try {
        MusicPlayerService.startAndPlay(context, number)
    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error restarting music player", e)
        showSimpleNotificationExtern(
            "❌ Fehler",
            "Musik Player konnte nicht neu gestartet werden",
            context = context
        )
    }
}