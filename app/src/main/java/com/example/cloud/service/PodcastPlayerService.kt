package com.example.cloud.service
/*
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
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
import com.example.cloud.showSimpleNotificationExtern
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.time.Duration.Companion.seconds

class PodcastPlayerService : Service() {

    companion object {
        private const val CHANNEL_ID = "podcast_player_channel"
        private const val NOTIFICATION_ID = 777777

        private const val ACTION_PLAY = "com.example.cloud.ACTION_PODCAST_PLAY"
        private const val ACTION_PAUSE = "com.example.cloud.ACTION_PODCAST_PAUSE"
        private const val ACTION_REWIND = "com.example.cloud.ACTION_PODCAST_REWIND"
        private const val ACTION_FORWARD = "com.example.cloud.ACTION_PODCAST_FORWARD"
        private const val ACTION_SELECT_PODCAST = "com.example.cloud.ACTION_SELECT_PODCAST"

        private const val PREFS_NAME = "podcast_player_prefs"
        private const val KEY_PREFIX_POSITION = "podcast_position_"
        private const val KEY_CURRENT_PODCAST = "current_podcast_path"

        private const val SKIP_TIME_MS = 15000

        private const val MEDIA_STATE_PREFS = "media_state_prefs"
        const val KEY_ACTIVE_SERVICE = "active_media_service"
        const val SERVICE_MUSIC = "music"
        const val SERVICE_PODCAST = "podcast"

        private const val KEY_PREFIX_COMPLETED = "podcast_completed_"

        private const val ACTION_DELETE_SINGLE = "com.example.cloud.ACTION_DELETE_SINGLE_"
        private const val KEY_PODCAST_QUEUE = "podcast_queue"

        const val EXTRA_FORWARD_MS = "extra_forward_ms"

        private const val SERVICE_SWITCH_TIMEOUT = 20000L

        private const val KEY_PLAYBACK_SPEED = "podcast_playback_speed"

        fun startService(context: Context) {
            val intent = Intent(context, PodcastPlayerService::class.java)
            context.startForegroundService(intent)
        }

        fun sendPlayAction(context: Context) {
            val intent = Intent(context, PodcastPlayerService::class.java).apply {
                action = ACTION_PLAY
            }
            context.startService(intent)
        }

        fun sendForwardAction(context: Context, ms: Int) {
            val intent = Intent(context, PodcastPlayerService::class.java).apply {
                action = ACTION_FORWARD
                putExtra(EXTRA_FORWARD_MS, ms)
            }
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, PodcastPlayerService::class.java)
            context.stopService(intent)
        }

        fun managePodcast(context: Context) {
            val intent = Intent(context, PodcastPlayerService::class.java).apply {
                action = "ACTION_SHOW_DELETE_COMPLETED"
            }
            context.startService(intent)
        }

        fun setPlaybackSpeed(context: Context, speed: Float) {
            val intent = Intent(context, PodcastPlayerService::class.java).apply {
                action = "ACTION_SET_SPEED"
                putExtra("SPEED", speed)
            }
            context.startService(intent)
        }
    }

    data class Podcast(
        val uri: Uri,
        val name: String,
        val path: String,
        val savedPosition: Long = 0,
        val isCompleted: Boolean = false
    )

    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    private var isPlayingB = false
    private var podcasts: List<Podcast> = emptyList()
    private var podcastQueue: MutableList<String> = mutableListOf()
    private var currentPodcast: Podcast? = null
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var mediaStatePrefs: SharedPreferences
    private var positionSaveRunnable: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var bluetoothReceiver: BroadcastReceiver? = null
    private var lastPlayPauseTime = 0L
    private var playPauseCount = 0
    private var isSwitching = false

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        mediaStatePrefs = getSharedPreferences(MEDIA_STATE_PREFS, MODE_PRIVATE)

        createNotificationChannel()
        loadPodcasts()
        loadPodcastQueue()

        val lastPodcastPath = sharedPreferences.getString(KEY_CURRENT_PODCAST, null)
        if (lastPodcastPath != null) {
            val podcast = podcasts.find { it.path == lastPodcastPath }
            if (podcast != null) {
                val savedPos = getSavedPosition(podcast.path)
                currentPodcast = podcast.copy(savedPosition = savedPos)
                Log.d("PodcastPlayerService", "✓ Loaded last podcast in onCreate: ${podcast.name}")
            }
        }

        // WICHTIG: ERST JETZT foreground starten, NACHDEM currentPodcast gesetzt ist
        startForeground(NOTIFICATION_ID, createNotification(), getServiceForegroundType())
        Log.d(
            "PodcastPlayerService",
            "✓ Started foreground with notification (currentPodcast=${currentPodcast?.name})"
        )

        try {
            mediaSession = MediaSession.Builder(this, object : Player {
                override fun getApplicationLooper() = android.os.Looper.getMainLooper()
                override fun addListener(listener: Player.Listener) {}
                override fun removeListener(listener: Player.Listener) {}
                override fun setMediaItems(mediaItems: MutableList<MediaItem>) {}
                override fun setMediaItems(
                    mediaItems: MutableList<MediaItem>,
                    resetPosition: Boolean
                ) {
                }

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
                override fun getPlaybackSuppressionReason() =
                    Player.PLAYBACK_SUPPRESSION_REASON_NONE

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

                @OptIn(UnstableApi::class)
                override fun getCurrentManifest(): Any? = null
                override fun getCurrentTimeline() = Timeline.EMPTY
                override fun getCurrentPeriodIndex() = 0

                @OptIn(UnstableApi::class)
                override fun getCurrentWindowIndex() = 0
                override fun getCurrentMediaItemIndex() = 0

                @OptIn(UnstableApi::class)
                override fun getNextWindowIndex() = 0
                override fun getNextMediaItemIndex() = 0

                @OptIn(UnstableApi::class)
                override fun getPreviousWindowIndex() = 0
                override fun getPreviousMediaItemIndex() = 0
                override fun getCurrentMediaItem(): MediaItem? = null
                override fun getMediaItemCount() = 0
                override fun getMediaItemAt(index: Int): MediaItem =
                    throw IndexOutOfBoundsException()

                override fun getDuration() = 0L
                override fun getCurrentPosition() = 0L
                override fun getBufferedPosition() = 0L
                override fun getBufferedPercentage() = 0
                override fun getTotalBufferedDuration() = 0L

                @OptIn(UnstableApi::class)
                override fun isCurrentWindowDynamic() = false
                override fun isCurrentMediaItemDynamic() = false

                @OptIn(UnstableApi::class)
                override fun isCurrentWindowLive() = false
                override fun isCurrentMediaItemLive() = false
                override fun getCurrentLiveOffset() = 0L

                @OptIn(UnstableApi::class)
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
                override fun clearVideoSurface(surface: android.view.Surface?) {}
                override fun setVideoSurface(surface: android.view.Surface?) {}
                override fun setVideoSurfaceHolder(surfaceHolder: android.view.SurfaceHolder?) {}
                override fun clearVideoSurfaceHolder(surfaceHolder: android.view.SurfaceHolder?) {}
                override fun setVideoSurfaceView(surfaceView: android.view.SurfaceView?) {}
                override fun clearVideoSurfaceView(surfaceView: android.view.SurfaceView?) {}
                override fun setVideoTextureView(textureView: android.view.TextureView?) {}
                override fun clearVideoTextureView(textureView: android.view.TextureView?) {}
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
            })
                .setId("PodcastPlayerSession")
                .setCallback(object : MediaSession.Callback {
                    private var clickCount = 0

                    override fun onMediaButtonEvent(
                        session: MediaSession,
                        controllerInfo: MediaSession.ControllerInfo,
                        intent: Intent
                    ): Boolean {
                        if (isSwitching) {
                            Log.d(
                                "PodcastPlayerService",
                                "⊘ Ignoring media button - service switching in progress"
                            )
                            return true
                        }

                        if (!isThisServiceActive()) {
                            Log.d(
                                "PodcastPlayerService",
                                "⊘ Not active service - passing media button to other session"
                            )
                            return false
                        }

                        Log.d("PodcastPlayerService", "ℹ️ MediaButton event received")

                        try {
                            val keyEvent =
                                intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                                when (keyEvent.keyCode) {
                                    KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                        val currentTime = System.currentTimeMillis()

                                        if (currentTime - lastPlayPauseTime < SERVICE_SWITCH_TIMEOUT) {
                                            playPauseCount++
                                            if (playPauseCount >= 5) {
                                                Log.d(
                                                    "PodcastPlayerService",
                                                    "🔄 Switching to Music Player (deferred)"
                                                )
                                                playPauseCount = 0
                                                // NICHT synchron aus dem Callback: MediaSession release erst nach Rückkehr aus onMediaButtonEvent
                                                handler.post { switchToMusicPlayer() }
                                                return true
                                            }
                                        } else {
                                            playPauseCount = 1
                                        }
                                        lastPlayPauseTime = currentTime

                                        // Normale Multi-Click-Logik
                                        clickCount++

                                        handler.removeCallbacksAndMessages(null)
                                        Log.d("PodcastPlayerService", "🎙️ 1x Klick: Play/Pause")

                                        if (isPlayingB) {
                                            pausePodcast()
                                        } else {
                                            // NEU: Stelle sicher dass Podcast geladen ist
                                            if (currentPodcast != null) {
                                                playPodcast()
                                            } else {
                                                Log.w(
                                                    "PodcastPlayerService",
                                                    "Cannot play - no podcast selected"
                                                )
                                                showPodcastSelection()
                                            }
                                        }
                                        return true
                                    }

                                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                        Log.d(
                                            "PodcastPlayerService",
                                            "⏩ MediaButton: PodcastService active, handling +15s"
                                        )
                                        forward()
                                        return true
                                    }

                                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                        Log.d(
                                            "PodcastPlayerService",
                                            "⏪ MediaButton: PodcastService active, handling -15s"
                                        )
                                        rewind()
                                        return true
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("PodcastPlayerService", "Error handling media button", e)
                        }
                        return super.onMediaButtonEvent(session, controllerInfo, intent)
                    }
                })
                .build()

        } catch (e: Exception) {
            Log.w("PodcastPlayerService", "Could not create MediaSession", e)
        }

        Toast.makeText(this, "READY", Toast.LENGTH_SHORT).show()

        startPositionSaving()
        registerBluetoothReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        Log.d(
            "PodcastPlayerService",
            "📨 onStartCommand - action=$action, currentPodcast=${currentPodcast?.name}, isSwitching=$isSwitching"
        )

        if (action == ACTION_PLAY) {
            setActiveService()
            Log.d("PodcastPlayerService", "✓ Set as active service immediately on ACTION_PLAY")
        }

        when {
            action == ACTION_PLAY -> {
                Log.d("PodcastPlayerService", "▶ ACTION_PLAY received")
                playPodcast()
            }

            action == ACTION_PAUSE -> {
                pausePodcast()
                val position = mediaPlayer?.currentPosition?.toLong() ?: 0
                currentPodcast?.let { savePosition(it.path, position) }
            }

            action == "ACTION_NOTIFICATION_DELETED" -> {
                Log.d("PodcastPlayerService", "📱 Notification deleted by user")

                if (currentPodcast != null && mediaPlayer != null) {
                    try {
                        val position = mediaPlayer?.currentPosition?.toLong() ?: 0
                        savePosition(currentPodcast!!.path, position)
                        Log.d(
                            "PodcastPlayerService",
                            "Position saved on delete: ${formatTime(position)}"
                        )
                    } catch (e: Exception) {
                        Log.e("PodcastPlayerService", "Error saving position on delete", e)
                    }
                }

                stopSelf()
                return START_NOT_STICKY
            }

            action == ACTION_REWIND -> {
                rewind()
                val position = mediaPlayer?.currentPosition?.toLong() ?: 0
                currentPodcast?.let { savePosition(it.path, position) }
            }

            action == ACTION_FORWARD -> {
                val ms = intent.getIntExtra(EXTRA_FORWARD_MS, SKIP_TIME_MS)
                forward(ms)
                val position = mediaPlayer?.currentPosition?.toLong() ?: 0
                currentPodcast?.let { savePosition(it.path, position) }
            }

            action == ACTION_SELECT_PODCAST -> showPodcastSelection()

            action?.startsWith("SELECT_") == true -> {
                val hashPart = action.removePrefix("SELECT_")
                val hash = hashPart.toIntOrNull()

                if (hash != null) {
                    val selected = podcasts.find { it.path.hashCode() == hash }
                    if (selected != null) {
                        currentPodcast = selected
                        val savedPos = getSavedPosition(selected.path)
                        currentPodcast = selected.copy(savedPosition = savedPos)
                        loadPodcast(currentPodcast!!)
                    } else {
                        Log.w("PodcastPlayerService", "Kein Podcast zu Hash $hash gefunden")
                    }
                } else {
                    Log.w("PodcastPlayerService", "Ungültige SELECT_-Action: $action")
                }
            }

            action == "ACTION_SHOW_DELETE_COMPLETED" -> {
                showDeleteCompletedNotifications()
            }

            action?.startsWith(ACTION_DELETE_SINGLE) == true -> {
                val hashPart = action.removePrefix(ACTION_DELETE_SINGLE)
                val hash = hashPart.toIntOrNull()

                if (hash != null) {
                    val podcast = podcasts.find { it.path.hashCode() == hash }
                    if (podcast != null) {
                        deletePodcastFile(podcast)
                    } else {
                        Log.w("PodcastPlayerService", "Kein Podcast zu Hash $hash gefunden")
                    }
                } else {
                    Log.w("PodcastPlayerService", "Ungültiger Hash in DELETE_SINGLE: $action")
                }
            }

            action == "ACTION_SET_SPEED" -> {
                val speed = intent.getFloatExtra("SPEED", 1.0f)
                setPlaybackSpeed(speed)
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Podcast Player",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Steuerung für Podcast-Wiedergabe"
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setSound(null, null)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun getServiceForegroundType(): Int {
        return try {
            if (checkSelfPermission(android.Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
        } catch (_: Exception) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        }
    }

    private fun getCompletedStatus(podcastPath: String): Boolean {
        val key = KEY_PREFIX_COMPLETED + podcastPath.hashCode()
        return sharedPreferences.getBoolean(key, false)
    }

    private fun setCompletedStatus(podcastPath: String, completed: Boolean) {
        val key = KEY_PREFIX_COMPLETED + podcastPath.hashCode()
        sharedPreferences.edit(commit = true) {
            putBoolean(key, completed)
        }
        Log.d("PodcastPlayerService", "Completed status set to $completed for $podcastPath")
    }

    private fun loadPodcasts() {
        try {
            val podcasts = mutableListOf<Podcast>()
            loadFromMediaStore(podcasts)

            this.podcasts = podcasts.map { podcast ->
                val savedPosition = getSavedPosition(podcast.path)
                val isCompleted = getCompletedStatus(podcast.path)
                podcast.copy(
                    savedPosition = savedPosition,
                    isCompleted = isCompleted
                )
            }.sortedBy { it.name }

            Log.d("PodcastPlayerService", "Loaded ${this.podcasts.size} podcasts")
            this.podcasts.forEach { podcast ->
                val status = if (podcast.isCompleted) "✓ Fertig" else "⏸ Offen"
                Log.d(
                    "PodcastPlayerService",
                    "$status - ${podcast.name} - Position: ${formatTime(podcast.savedPosition)}"
                )
            }

        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error loading podcasts", e)
            podcasts = emptyList()
        }
    }

    private fun loadFromMediaStore(podcasts: MutableList<Podcast>) {
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

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn) ?: continue
                    val data = cursor.getString(dataColumn) ?: continue
                    val title = cursor.getString(titleColumn)

                    val normalizedPath = try {
                        java.net.URLDecoder.decode(data, "UTF-8")
                            .replace("\\", "/")
                            .lowercase()
                    } catch (_: Exception) {
                        data.replace("\\", "/").lowercase()
                    }

                    val isInPodcasts = normalizedPath.contains("/download/cloud/podcasts/") ||
                            normalizedPath.contains("/downloads/cloud/podcasts/") ||
                            data.contains("/Cloud/Podcasts/", ignoreCase = true)


                    if (isInPodcasts && (name.endsWith(".mp3") || name.endsWith(".m4a"))) {
                        val contentUri = Uri.withAppendedPath(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )

                        val displayName = if (!title.isNullOrBlank() && title != "<unknown>") {
                            title
                        } else {
                            name.substringBeforeLast('.')
                        }

                        podcasts.add(
                            Podcast(
                                uri = contentUri,
                                name = displayName,
                                path = data
                            )
                        )
                        Log.d("PodcastPlayerService", "✓ Added: $displayName")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error loading from MediaStore", e)
        }
    }

    private fun getSavedPosition(podcastPath: String): Long {
        val key = KEY_PREFIX_POSITION + podcastPath.hashCode()
        return sharedPreferences.getLong(key, 0L)
    }

    private fun savePosition(podcastPath: String, position: Long) {
        val key = KEY_PREFIX_POSITION + podcastPath.hashCode()
        sharedPreferences.edit(commit = true) {
            putLong(key, position)
        }
        Log.d("PodcastPlayerService", "Position saved: ${formatTime(position)} for $podcastPath")
    }

    private fun saveCurrentPodcast(podcastPath: String) {
        sharedPreferences.edit(commit = true) {
            putString(KEY_CURRENT_PODCAST, podcastPath)
        }
    }

    private fun startPositionSaving() {
        var lastSavedPosition: Long = 0
        positionSaveRunnable = object : Runnable {
            override fun run() {
                if (isPlayingB && mediaPlayer != null && currentPodcast != null) {
                    try {
                        val position = mediaPlayer?.currentPosition?.toLong() ?: 0
                        if (abs(position - lastSavedPosition) > 5000) {
                            savePosition(currentPodcast!!.path, position)
                            lastSavedPosition = position
                            updateNotification()
                        }
                    } catch (e: Exception) {
                        Log.e("PodcastPlayerService", "Error saving position", e)
                    }
                }
                handler.postDelayed(this, 60000)
            }
        }
        handler.post(positionSaveRunnable!!)
    }

    private fun playPodcast() {
        try {
            Log.d(
                "PodcastPlayerService",
                "🎵 playPodcast() called - currentPodcast=${currentPodcast?.name}"
            )

            if (currentPodcast == null) {
                Log.d("PodcastPlayerService", "⚠ currentPodcast is null, trying to load from prefs")
                val lastPodcastPath = sharedPreferences.getString(KEY_CURRENT_PODCAST, null)

                if (lastPodcastPath != null) {
                    val podcast = podcasts.find { it.path == lastPodcastPath }
                    if (podcast != null) {
                        val savedPos = getSavedPosition(podcast.path)
                        currentPodcast = podcast.copy(savedPosition = savedPos)
                        Log.d(
                            "PodcastPlayerService",
                            "✓ Restored last podcast: ${podcast.name} at ${formatTime(savedPos)}"
                        )

                        // WICHTIG: Notification SOFORT aktualisieren nach Wiederherstellung
                        updateNotification()
                    } else {
                        Log.w(
                            "PodcastPlayerService",
                            "Last podcast not found in list: $lastPodcastPath"
                        )
                    }
                }

                if (currentPodcast == null) {
                    Log.d("PodcastPlayerService", "No saved podcast found, showing selection")
                    showPodcastSelection()
                    return
                }
            }

            setActiveService()

            if (mediaPlayer != null && !isPlayingB) {
                val currentPos = getSavedPosition(currentPodcast!!.path).toInt()
                if (currentPos > 1000) {
                    val newPos = currentPos - 1000
                    mediaPlayer?.seekTo(newPos)
                }
                mediaPlayer?.start()
                isPlayingB = true
                Log.d("PodcastPlayerService", "▶ Resumed: ${currentPodcast!!.name}")
                updateNotification() // Sofortiges Update
                return
            }

            if (mediaPlayer == null) {
                loadPodcast(currentPodcast!!)
            }

        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error playing podcast", e)
        }
    }

    // FIX 3: In loadPodcast() - Notification VOR dem Start aktualisieren
    private fun loadPodcast(podcast: Podcast) {
        try {
            mediaPlayer?.release()
            mediaPlayer = null

            Log.d("PodcastPlayerService", "Loading: ${podcast.name}")
            Log.d("PodcastPlayerService", "Saved position: ${formatTime(podcast.savedPosition)}")

            if (currentPodcast?.path != podcast.path) {
                currentPodcast = podcast
                saveCurrentPodcast(podcast.path)
                Log.d("PodcastPlayerService", "Set currentPodcast to: ${podcast.name}")
            }

            if (podcast.isCompleted) {
                setCompletedStatus(podcast.path, false)
                Log.d("PodcastPlayerService", "Reset completed status (podcast restarted)")
            }

            mediaPlayer = MediaPlayer().apply {
                try {
                    setDataSource(applicationContext, podcast.uri)
                    prepare()

                    // Gespeicherte Geschwindigkeit anwenden
                    val savedSpeed = getPlaybackSpeed()
                    if (savedSpeed != 1.0f) {
                        playbackParams = playbackParams.setSpeed(savedSpeed)
                        Log.d("PodcastPlayerService", "Applied playback speed: ${savedSpeed}x")
                    }

                    Log.d("PodcastPlayerService", "✓ Loaded via Content URI")
                } catch (e: Exception) {
                    Log.w("PodcastPlayerService", "Failed with Content URI, trying file path", e)
                    reset()
                    setDataSource(podcast.path)
                    prepare()
                    Log.d("PodcastPlayerService", "✓ Loaded via file path")
                }

                if (podcast.savedPosition > 0) {
                    seekTo(podcast.savedPosition.toInt())
                    Log.d(
                        "PodcastPlayerService",
                        "⏩ Jumped to ${formatTime(podcast.savedPosition)}"
                    )
                }

                setOnCompletionListener {
                    onPodcastComplete()
                }

                // WICHTIG: isPlayingB und currentPodcast VORHER setzen
                isPlayingB = true
                currentPodcast = podcast.copy(isCompleted = false)
                saveCurrentPodcast(podcast.path)

                // Notification SYNCHRON aktualisieren BEVOR start()
                updateNotification()
                Log.d(
                    "PodcastPlayerService",
                    "✓ Notification updated BEFORE start: ${podcast.name}"
                )

                start()
            }

            Log.d("PodcastPlayerService", "▶ Now playing: ${podcast.name}")

        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error loading podcast: ${podcast.name}", e)
            isPlayingB = false
            updateNotification()
        }
    }

    private fun pausePodcast() {
        try {
            Log.d("PodcastPlayerService", "⏸ pausePodcast() called")

            if (isPlayingB && mediaPlayer?.isPlaying == true) {
                val position = mediaPlayer?.currentPosition?.toLong() ?: 0
                mediaPlayer?.pause()
                isPlayingB = false

                currentPodcast?.let { podcast ->
                    savePosition(podcast.path, position)
                }
                updateNotification()
                Log.d("PodcastPlayerService", "⏸ Paused at ${formatTime(position)}")
            }
        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error pausing podcast", e)
        }
    }

    private fun rewind() {
        if (currentPodcast == null) return
        try {
            mediaPlayer?.let { player ->
                val currentPos = player.currentPosition
                val newPos = maxOf(0, currentPos - SKIP_TIME_MS)
                player.seekTo(newPos)

                currentPodcast?.let { podcast ->
                    savePosition(podcast.path, newPos.toLong())
                }

                updateNotification()
                Log.d("PodcastPlayerService", "⏪ Rewinded to ${formatTime(newPos.toLong())}")
            }
        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error rewinding", e)
        }
    }

    private fun forward(skipMs: Int = SKIP_TIME_MS) {
        if (currentPodcast == null) return
        try {
            mediaPlayer?.let { player ->
                val currentPos = player.currentPosition
                val duration = player.duration
                val newPos = minOf(duration, currentPos + skipMs)
                player.seekTo(newPos)

                currentPodcast?.let { podcast ->
                    savePosition(podcast.path, newPos.toLong())
                }

                updateNotification()
                Log.d("PodcastPlayerService", "⏩ Forwarded to ${formatTime(newPos.toLong())}")
            }
        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error forwarding", e)
        }
    }

    private fun showPodcastSelection() {
        try {
            if (podcasts.isEmpty()) {
                return
            }

            val updatedPodcasts = podcasts.map { podcast ->
                val currentSavedPos = getSavedPosition(podcast.path)
                val isCompleted = getCompletedStatus(podcast.path)
                podcast.copy(
                    savedPosition = currentSavedPos,
                    isCompleted = isCompleted
                )
            }

            val notificationManager = getSystemService(NotificationManager::class.java)

            updatedPodcasts.forEachIndexed { index, podcast ->
                val selectIntent = Intent(this, PodcastPlayerService::class.java).apply {
                    action = "SELECT_${podcast.path.hashCode()}"
                }

                val selectPendingIntent = PendingIntent.getService(
                    this,
                    50000 + index,
                    selectIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val statusIcon = when {
                    podcast.isCompleted -> "✓"
                    podcast.savedPosition > 0 -> "▶"
                    else -> "⏸"
                }

                val progressText = when {
                    podcast.isCompleted -> " • Fertig"
                    podcast.savedPosition > 0 -> " • ${formatTime(podcast.savedPosition)}"
                    else -> ""
                }

                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .setContentTitle("${index + 1} $statusIcon ${podcast.name}")
                    .setContentText("Antippen zum Abspielen$progressText")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(selectPendingIntent)
                    .setGroup("podcast_selection")
                    .build()

                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notificationManager.notify(60000 + index, notification)
                }
            }

            val completedCount = updatedPodcasts.count { it.isCompleted }
            val summaryText = if (completedCount > 0) {
                "${podcasts.size} Podcasts ($completedCount fertig)"
            } else {
                "${podcasts.size} Podcasts verfügbar"
            }

            val summaryNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("Podcast auswählen")
                .setContentText(summaryText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup("podcast_selection")
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()

            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(60999, summaryNotification)
            }

            Log.d(
                "PodcastPlayerService",
                "Showing ${podcasts.size} podcasts ($completedCount completed)"
            )

        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error showing podcast selection", e)
        }
    }

    private fun onPodcastComplete() {
        currentPodcast?.let { podcast ->
            savePosition(podcast.path, 0)
            setCompletedStatus(podcast.path, true)

            val updatedPodcast = podcast.copy(
                savedPosition = 0,
                isCompleted = true
            )
            podcasts = podcasts.map {
                if (it.path == podcast.path) updatedPodcast else it
            }
        }

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error releasing MediaPlayer", e)
        }

        isPlayingB = false

        loadPodcastQueue()

        if (podcastQueue.isNotEmpty()) {
            val nextPath = podcastQueue.removeAt(0)
            savePodcastQueue()

            val nextPodcast = podcasts.find { it.path == nextPath }
            if (nextPodcast != null) {
                val savedPos = getSavedPosition(nextPodcast.path)
                currentPodcast = nextPodcast.copy(savedPosition = savedPos)
                saveCurrentPodcast(nextPodcast.path)
                loadPodcast(currentPodcast!!)
                Log.d("PodcastPlayerService", "✓ Auto-playing next from queue: ${nextPodcast.name}")
            } else {
                Log.w("PodcastPlayerService", "Next podcast not found: $nextPath")
                if (podcastQueue.isNotEmpty()) {
                    onPodcastComplete()
                } else {
                    currentPodcast = null
                    updateNotification()
                }
            }
        } else {
            currentPodcast = null
            updateNotification()
            Log.d("PodcastPlayerService", "Queue empty - playback finished")
        }
    }

    private fun formatTime(milliseconds: Long): String {
        val hours = TimeUnit.MILLISECONDS.toHours(milliseconds)
        val minutes = TimeUnit.MILLISECONDS.toMinutes(milliseconds) % 60
        val seconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds) % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    private fun createNotification(): android.app.Notification {
        return buildNotification()
    }

    private fun updateNotification() {
        val activeService = mediaStatePrefs.getString(
            KEY_ACTIVE_SERVICE,
            SERVICE_PODCAST
        )

        Log.d("PodcastPlayerService", "updateNotification() called - activeService=$activeService")
        // Wenn wir NICHT der aktive Service sind, keine Notification anzeigen
        if (activeService != SERVICE_PODCAST) { // oder SERVICE_MUSIC
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.cancel(NOTIFICATION_ID)
            Log.d("PodcastPlayerService", "⊘ Not active service - notification canceled")
            return
        }

        val notification = buildNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d("PodcastPlayerService", "🔔 Notification updated")
    }

    private fun buildNotification(): android.app.Notification {
        val title = currentPodcast?.name ?: "Kein Podcast ausgewählt"
        val currentPos = mediaPlayer?.currentPosition?.toLong() ?: 0
        val duration = mediaPlayer?.duration?.toLong() ?: 0
        val progressText = if (duration > 0) {
            "${formatTime(currentPos)} / ${formatTime(duration)}"
        } else {
            "Bereit"
        }

        val rewindIntent = Intent(this, PodcastPlayerService::class.java).apply {
            action = ACTION_REWIND
        }
        val rewindPendingIntent = PendingIntent.getService(
            this, 0, rewindIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = Intent(this, PodcastPlayerService::class.java).apply {
            action = if (isPlayingB) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 1, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val forwardIntent = Intent(this, PodcastPlayerService::class.java).apply {
            action = ACTION_FORWARD
        }
        val forwardPendingIntent = PendingIntent.getService(
            this, 2, forwardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val selectIntent = Intent(this, PodcastPlayerService::class.java).apply {
            action = ACTION_SELECT_PODCAST
        }
        val selectPendingIntent = PendingIntent.getService(
            this, 3, selectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deleteIntent = Intent(this, PodcastPlayerService::class.java).apply {
            action = "ACTION_NOTIFICATION_DELETED"
        }
        val deletePendingIntent = PendingIntent.getService(
            this,
            4,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("🎙️ $title ${mediaPlayer?.playbackParams?.speed}")
            .setContentText(progressText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(selectPendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .setRequestPromotedOngoing(true)
            .setGroup("group_services")
            .setGroupSummary(false)
            .addAction(
                android.R.drawable.ic_media_rew,
                "-15s",
                rewindPendingIntent
            )
            .addAction(
                if (isPlayingB) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlayingB) "Pause" else "Play",
                playPausePendingIntent
            )
            .addAction(
                android.R.drawable.ic_media_ff,
                "+15s",
                forwardPendingIntent
            )

        return builder.build()
    }

    private fun savePodcastQueue() {
        val queueJson = podcastQueue.joinToString("|||")
        sharedPreferences.edit(commit = true) {
            putString(KEY_PODCAST_QUEUE, queueJson)
        }
        Log.d("PodcastPlayerService", "Queue saved: ${podcastQueue.size} items")
    }

    private fun loadPodcastQueue() {
        val queueJson = sharedPreferences.getString(KEY_PODCAST_QUEUE, null)
        if (queueJson != null && queueJson.isNotEmpty()) {
            podcastQueue = queueJson.split("|||").toMutableList()
            Log.d("PodcastPlayerService", "Queue loaded: ${podcastQueue.size} items")
        } else {
            podcastQueue = mutableListOf()
        }
    }

    private fun playNextInQueue() {
        if (podcastQueue.isEmpty()) {
            Log.d("PodcastPlayerService", "Queue is empty, nothing to play")
            return
        }

        val nextPath = podcastQueue.removeAt(0)
        savePodcastQueue()

        val nextPodcast = podcasts.find { it.path == nextPath }
        if (nextPodcast != null) {
            currentPodcast = nextPodcast
            val savedPos = getSavedPosition(nextPodcast.path)
            currentPodcast = nextPodcast.copy(savedPosition = savedPos)
            loadPodcast(currentPodcast!!)
            Log.d("PodcastPlayerService", "Playing next from queue: ${nextPodcast.name}")
        } else {
            Log.w("PodcastPlayerService", "Next podcast not found: $nextPath")
            playNextInQueue()
        }
    }

    private fun switchToMusicPlayer() {
        try {
            isSwitching = true

            // Timeout-Schutz
            handler.postDelayed({
                if (isSwitching) {
                    Log.w("PodcastPlayerService", "⚠ Switching timeout - resetting flag")
                    isSwitching = false
                }
            }, 5000)

            // 1. Playback pausieren und Position speichern
            if (isPlayingB && mediaPlayer != null && currentPodcast != null) {
                val position = mediaPlayer?.currentPosition?.toLong() ?: 0
                savePosition(currentPodcast!!.path, position)
                mediaPlayer?.pause()
                isPlayingB = false
                Log.d("PodcastPlayerService", "✓ Paused and saved position")
            }

            // 2. MediaSession komplett freigeben (release), damit nur noch Music Key-Events erhält
            try {
                mediaSession?.release()
                mediaSession = null
                Log.d("PodcastPlayerService", "✓ MediaSession released")
            } catch (e: Exception) {
                Log.e("PodcastPlayerService", "Error releasing MediaSession", e)
            }

            // 3. Service-Status wechseln
            mediaStatePrefs.edit(commit = true) {
                putString(KEY_ACTIVE_SERVICE, SERVICE_MUSIC)
            }
            Log.d("PodcastPlayerService", "✓ Service status switched to MUSIC")

            // 4. Notification SOFORT entfernen (wie in MusicPlayerService.switchToPodcastPlayer)
            try {
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
            } catch (e: Exception) {
                Log.e("PodcastPlayerService", "Error stopForeground", e)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.cancel(NOTIFICATION_ID)
            Log.d("PodcastPlayerService", "✓ Notification removed")

            // 5. Music Player starten, dann diesen Service stoppen (Spiegel zu switchToPodcastPlayer)
            handler.postDelayed({
                try {
                    MusicPlayerService.startAndPlay(this, null)
                    Log.d("PodcastPlayerService", "✓ Music Player started")
                    handler.postDelayed({
                        stopSelf()
                        Log.d("PodcastPlayerService", "✓ Podcast service stopped")
                    }, 150)
                } catch (e: Exception) {
                    Log.e("PodcastPlayerService", "Error starting Music Player", e)
                    isSwitching = false
                }
            }, 150)

            handler.postDelayed({
                isSwitching = false
                Log.d("PodcastPlayerService", "✓ Switch completed")
            }, 500)

        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error switching to Music Player", e)
            isSwitching = false
        }
    }

    override fun onDestroy() {
        try {
            bluetoothReceiver?.let {
                unregisterReceiver(it)
                bluetoothReceiver = null
            }
        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error unregistering receiver", e)
        }

        super.onDestroy()

        // Notification entfernen beim Destroy
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.cancel(NOTIFICATION_ID)
            Log.d("PodcastPlayerService", "✓ Notification canceled in onDestroy")
        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error canceling notification", e)
        }

        if (!isSwitching) {
            clearActiveService()
        }

        try {
            if (currentPodcast != null && mediaPlayer != null) {
                val position = mediaPlayer?.currentPosition?.toLong() ?: 0
                savePosition(currentPodcast!!.path, position)
                Log.d("PodcastPlayerService", "Final position saved: ${formatTime(position)}")
            }
        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error saving final position", e)
        }

        try {
            positionSaveRunnable?.let {
                handler.removeCallbacks(it)
                Log.d("PodcastPlayerService", "Position save runnable removed")
            }
        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error removing callbacks", e)
        }

        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            Log.d("PodcastPlayerService", "MediaPlayer released")
        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error releasing MediaPlayer", e)
        }

        try {
            mediaSession?.release()
            mediaSession = null
            Log.d("PodcastPlayerService", "MediaSession released")
        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error releasing MediaSession", e)
        }

        isPlayingB = false
        isSwitching = false
        Log.d("PodcastPlayerService", "PodcastPlayerService destroyed")
    }

    private fun setActiveService() {
        mediaStatePrefs.edit(commit = true) {
            putString(KEY_ACTIVE_SERVICE, SERVICE_PODCAST)
        }
        Log.d("PodcastPlayerService", "✓ Set as active media service")
    }

    private fun clearActiveService() {
        val currentActive = mediaStatePrefs.getString(KEY_ACTIVE_SERVICE, null)
        if (currentActive == SERVICE_PODCAST) {
            mediaStatePrefs.edit(commit = true) {
                remove(KEY_ACTIVE_SERVICE)
            }
            Log.d("PodcastPlayerService", "✓ Cleared active service state")
        }
    }

    private fun isThisServiceActive(): Boolean {
        val activeService = mediaStatePrefs.getString(KEY_ACTIVE_SERVICE, null)
        return activeService == SERVICE_PODCAST
    }

    fun showDeleteCompletedNotifications() {
        try {
            val updatedPodcasts = podcasts.map { podcast ->
                val isCompleted = getCompletedStatus(podcast.path)
                podcast.copy(isCompleted = isCompleted)
            }

            val completedPodcasts = updatedPodcasts.filter { it.isCompleted }

            if (completedPodcasts.isEmpty()) {
                showNoCompletedPodcastsNotification()
                return
            }

            val notificationManager = getSystemService(NotificationManager::class.java)

            completedPodcasts.forEachIndexed { index, podcast ->
                val deleteIntent = Intent(this, PodcastPlayerService::class.java).apply {
                    action = ACTION_DELETE_SINGLE + podcast.path.hashCode()
                }

                val deletePendingIntent = PendingIntent.getService(
                    this,
                    70000 + index,
                    deleteIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_delete)
                    .setContentTitle("🗑️ ${podcast.name}")
                    .setContentText("Antippen zum Löschen • Fertig angehört")
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(true)
                    .setContentIntent(deletePendingIntent)
                    .setGroup("podcast_delete")
                    .build()

                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    notificationManager.notify(70000 + index, notification)
                }
            }

            val summaryNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_delete)
                .setContentTitle("Fertige Podcasts löschen")
                .setContentText("${completedPodcasts.size} Podcasts bereit zum Löschen")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup("podcast_delete")
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()

            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(70999, summaryNotification)
            }

            Log.d(
                "PodcastPlayerService",
                "Showing ${completedPodcasts.size} completed podcasts for deletion"
            )

        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error showing delete notifications", e)
        }
    }

    private fun showNoCompletedPodcastsNotification() {
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("Keine fertigen Podcasts")
                .setContentText("Es gibt aktuell keine fertigen Podcasts zum Löschen")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setTimeoutAfter(10000)
                .build()

            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(71000, notification)
            }
        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error showing info notification", e)
        }
    }

    private fun deletePodcastFile(podcast: Podcast) {
        try {
            Log.d("PodcastPlayerService", "Attempting to delete: ${podcast.name}")

            if (currentPodcast?.path == podcast.path) {
                pausePodcast()
                mediaPlayer?.release()
                mediaPlayer = null
                currentPodcast = null
                isPlayingB = false
                Log.d("PodcastPlayerService", "Stopped currently playing podcast")
            }

            val file = java.io.File(podcast.path)
            val deleted = if (file.exists()) {
                file.delete()
            } else {
                Log.w("PodcastPlayerService", "File does not exist: ${podcast.path}")
                false
            }

            if (deleted) {
                Log.d("PodcastPlayerService", "✓ File deleted: ${podcast.name}")

                val posKey = KEY_PREFIX_POSITION + podcast.path.hashCode()
                val completedKey = KEY_PREFIX_COMPLETED + podcast.path.hashCode()
                sharedPreferences.edit(commit = true) {
                    remove(posKey)
                    remove(completedKey)
                }

                podcasts = podcasts.filter { it.path != podcast.path }

                showDeleteSuccessNotification(podcast.name)

                val notificationManager = getSystemService(NotificationManager::class.java)
                val notifId = 70000 + podcasts.indexOf(podcast)
                notificationManager.cancel(notifId)

                val remainingCompleted = podcasts.count { it.isCompleted }
                if (remainingCompleted == 0) {
                    notificationManager.cancel(70999)
                }

                if (currentPodcast == null) {
                    updateNotification()
                }

            } else {
                Log.e("PodcastPlayerService", "✗ Failed to delete file: ${podcast.name}")
                showDeleteErrorNotification(podcast.name)
            }

        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error deleting podcast", e)
            showDeleteErrorNotification(podcast.name)
        }
    }

    private fun showDeleteSuccessNotification(podcastName: String) {
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_delete)
                .setContentTitle("✓ Gelöscht")
                .setContentText(podcastName)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setTimeoutAfter(2000)
                .build()

            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(71001, notification)
            }
        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error showing success notification", e)
        }
    }

    private fun showDeleteErrorNotification(podcastName: String) {
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("✗ Fehler beim Löschen")
                .setContentText(podcastName)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setTimeoutAfter(4000)
                .build()

            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(71002, notification)
            }
        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error showing error notification", e)
        }
    }

    private fun registerBluetoothReceiver() {
        bluetoothReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        Log.d(
                            "PodcastPlayerService",
                            "🔌 Bluetooth disconnected - stopping playback"
                        )
                        pausePodcast()
                    }

                    AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                        Log.d("PodcastPlayerService", "🎧 Audio output disconnected - pausing")
                        pausePodcast()
                    }
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }

        registerReceiver(bluetoothReceiver, filter)
        Log.d("PodcastPlayerService", "✓ Bluetooth receiver registered")
    }

    private fun getPlaybackSpeed(): Float {
        return sharedPreferences.getFloat(KEY_PLAYBACK_SPEED, 1.0f)
    }

    private fun savePlaybackSpeed(speed: Float) {
        sharedPreferences.edit(commit = true) {
            putFloat(KEY_PLAYBACK_SPEED, speed)
        }
    }

    private fun setPlaybackSpeed(speed: Float) {
        try {
            val clampedSpeed = speed.coerceIn(0.5f, 3.0f)
            mediaPlayer?.let { player ->
                player.playbackParams = player.playbackParams.setSpeed(clampedSpeed)
                isPlayingB = player.isPlaying
                savePlaybackSpeed(clampedSpeed)
                updateNotification()
                Log.d("PodcastPlayerService", "Playback speed set to ${clampedSpeed}x (isPlaying=${isPlayingB})")
            } ?: run {
                savePlaybackSpeed(clampedSpeed)
                showSimpleNotificationExtern(
                    "✓ Gespeichert",
                    "Geschwindigkeit ${clampedSpeed}x wird beim nächsten Start verwendet",
                    5.seconds,
                    this
                )
            }
        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error setting playback speed", e)
        }
    }
}*/