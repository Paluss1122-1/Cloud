package com.example.cloud.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.net.Uri
import android.os.IBinder
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
import java.util.concurrent.TimeUnit
import kotlin.math.abs

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

        private const val SKIP_TIME_MS = 15000 // 15 Sekunden

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
    }

    data class Podcast(
        val uri: Uri,
        val name: String,
        val path: String,
        val savedPosition: Long = 0
    )

    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    private var isPlaying = false
    private var podcasts: List<Podcast> = emptyList()
    private var currentPodcast: Podcast? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var positionSaveRunnable: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        createNotificationChannel()
        loadPodcasts()

        val lastPodcastPath = sharedPreferences.getString(KEY_CURRENT_PODCAST, null)
        if (lastPodcastPath != null) {
            currentPodcast = podcasts.find { it.path == lastPodcastPath }
        }

        startForeground(NOTIFICATION_ID, createNotification(), getServiceForegroundType())

        // MediaSession hinzufügen, damit Kopfhörer-Tasten funktionieren
        try {
            mediaSession = MediaSession.Builder(this, object : Player {
                override fun getApplicationLooper() = android.os.Looper.getMainLooper()
                override fun addListener(listener: Player.Listener) {}
                override fun removeListener(listener: Player.Listener) {}
                override fun setMediaItems(mediaItems: MutableList<MediaItem>) {}
                override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {}
                override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {}
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
                override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: MutableList<MediaItem>) {}
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

                @androidx.annotation.OptIn(UnstableApi::class)
                override fun getTrackSelectionParameters() = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
                override fun setTrackSelectionParameters(parameters: TrackSelectionParameters) {}
                override fun getMediaMetadata() = MediaMetadata.EMPTY
                override fun getPlaylistMetadata() = MediaMetadata.EMPTY
                override fun setPlaylistMetadata(mediaMetadata: MediaMetadata) {}
                @androidx.annotation.OptIn(UnstableApi::class)
                override fun getCurrentManifest(): Any? = null
                override fun getCurrentTimeline() = Timeline.EMPTY
                override fun getCurrentPeriodIndex() = 0
                @androidx.annotation.OptIn(UnstableApi::class)
                override fun getCurrentWindowIndex() = 0
                override fun getCurrentMediaItemIndex() = 0
                @androidx.annotation.OptIn(UnstableApi::class)
                override fun getNextWindowIndex() = 0
                override fun getNextMediaItemIndex() = 0
                @androidx.annotation.OptIn(UnstableApi::class)
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
                @androidx.annotation.OptIn(UnstableApi::class)
                override fun isCurrentWindowDynamic() = false
                override fun isCurrentMediaItemDynamic() = false
                @androidx.annotation.OptIn(UnstableApi::class)
                override fun isCurrentWindowLive() = false
                override fun isCurrentMediaItemLive() = false
                override fun getCurrentLiveOffset() = 0L
                @androidx.annotation.OptIn(UnstableApi::class)
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
                override fun setDeviceVolume(volume: Int) {}
                override fun setDeviceVolume(volume: Int, flags: Int) {}
                @Deprecated("Deprecated in Java")
                override fun increaseDeviceVolume() {}
                override fun increaseDeviceVolume(flags: Int) {}
                @Deprecated("Deprecated in Java")
                override fun decreaseDeviceVolume() {}
                override fun decreaseDeviceVolume(flags: Int) {}
                @Deprecated("Deprecated in Java")
                override fun setDeviceMuted(muted: Boolean) {}
                override fun setDeviceMuted(muted: Boolean, flags: Int) {}
                override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {}
            })
                .setCallback(object : MediaSession.Callback {
                    override fun onMediaButtonEvent(
                        session: MediaSession,
                        controllerInfo: MediaSession.ControllerInfo,
                        intent: Intent
                    ): Boolean {
                        try {
                            val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                            if (keyEvent != null && keyEvent.action == KeyEvent.ACTION_DOWN) {
                                when (keyEvent.keyCode) {
                                    KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                        Log.d("PodcastPlayerService", "Media button: play/pause -> toggle")
                                        if (isPlaying) pausePodcast() else playPodcast()
                                        return true
                                    }
                                    KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                        Log.d("PodcastPlayerService", "Media button: pause")
                                        pausePodcast()
                                        return true
                                    }
                                    KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                        Log.d("PodcastPlayerService", "Media button: next -> +15s")
                                        forward()
                                        return true
                                    }
                                    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                        Log.d("PodcastPlayerService", "Media button: previous -> -15s")
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

        // Starte automatisches Speichern der Position
        startPositionSaving()

        Log.d("PodcastPlayerService", "Service created. Podcasts found: ${podcasts.size}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        when {
            action == ACTION_PLAY -> playPodcast()
            action == ACTION_PAUSE -> {
                pausePodcast()
                val position = mediaPlayer?.currentPosition?.toLong() ?: 0
                savePosition(currentPodcast!!.path, position)
            }
            action == "ACTION_NOTIFICATION_DELETED" -> {
                if (currentPodcast != null && mediaPlayer != null) {
                    try {
                        val position = mediaPlayer?.currentPosition?.toLong() ?: 0
                        savePosition(currentPodcast!!.path, position)
                        Log.d("PodcastPlayerService", "Position saved on delete: ${formatTime(position)}")
                    } catch (e: Exception) {
                        Log.e("PodcastPlayerService", "Error saving position on delete", e)
                    }
                }
                stopSelf() // Service beenden
            }
            action == ACTION_REWIND -> {
                rewind()
                val position = mediaPlayer?.currentPosition?.toLong() ?: 0
                savePosition(currentPodcast!!.path, position)
            }
            action == ACTION_FORWARD -> {
                forward()
                val position = mediaPlayer?.currentPosition?.toLong() ?: 0
                savePosition(currentPodcast!!.path, position)
            }
            action == ACTION_SELECT_PODCAST -> showPodcastSelection()

            // NEU: Auswahl aus Notification
            action?.startsWith("SELECT_") == true -> {
                val hashPart = action.removePrefix("SELECT_")
                val hash = hashPart.toIntOrNull()

                if (hash != null) {
                    val selected = podcasts.find { it.path.hashCode() == hash }
                    if (selected != null) {
                        currentPodcast = selected
                        // Wichtig: neue Position aus SharedPreferences holen
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

    private fun loadPodcasts() {
        try {
            val podcasts = mutableListOf<Podcast>()

            loadFromMediaStore(podcasts)

            // Lade gespeicherte Positionen für jeden Podcast
            this.podcasts = podcasts.map { podcast ->
                val savedPosition = getSavedPosition(podcast.path)
                podcast.copy(savedPosition = savedPosition)
            }.sortedBy { it.name }

            Log.d("PodcastPlayerService", "Loaded ${this.podcasts.size} podcasts")
            this.podcasts.forEach { podcast ->
                Log.d(
                    "PodcastPlayerService",
                    "${podcast.name} - Position: ${formatTime(podcast.savedPosition)}"
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
                if (isPlaying && mediaPlayer != null && currentPodcast != null) {
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
                handler.postDelayed(this, 60000) // Alle 5 Sekunden speichern
            }
        }
        handler.post(positionSaveRunnable!!)
    }

    private fun playPodcast() {
        try {
            if (currentPodcast == null) {
                // Kein Podcast ausgewählt, zeige Auswahl
                showPodcastSelection()
                return
            }

            // Wenn bereits pausiert, fortsetzen
            if (mediaPlayer != null && !isPlaying) {
                val currentPos = mediaPlayer?.currentPosition ?: 0
                if (currentPos > 1000) {
                    val newPos = currentPos - 1000
                    mediaPlayer?.seekTo(newPos)
                    Log.d("PodcastPlayerService", "⏪ Rewinded 2s before resume: ${formatTime(newPos.toLong())}")
                }
                mediaPlayer?.start()
                isPlaying = true
                Log.d("PodcastPlayerService", "▶ Resumed playback")
                updateNotification()
                return
            }

            // Neuen Podcast laden
            if (mediaPlayer == null) {
                loadPodcast(currentPodcast!!)
            }

        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error playing podcast", e)
        }
    }

    private fun loadPodcast(podcast: Podcast) {
        try {
            mediaPlayer?.release()
            mediaPlayer = null

            Log.d("PodcastPlayerService", "Loading: ${podcast.name}")
            Log.d("PodcastPlayerService", "Saved position: ${formatTime(podcast.savedPosition)}")

            mediaPlayer = MediaPlayer().apply {
                try {
                    setDataSource(applicationContext, podcast.uri)
                    prepare()
                    Log.d("PodcastPlayerService", "✓ Loaded via Content URI")
                } catch (e: Exception) {
                    Log.w("PodcastPlayerService", "Failed with Content URI, trying file path", e)
                    reset()
                    setDataSource(podcast.path)
                    prepare()
                    Log.d("PodcastPlayerService", "✓ Loaded via file path")
                }

                // Springe zur gespeicherten Position
                if (podcast.savedPosition > 0) {
                    seekTo(podcast.savedPosition.toInt())
                    Log.d("PodcastPlayerService", "⏩ Jumped to ${formatTime(podcast.savedPosition)}")
                }

                setOnCompletionListener {
                    onPodcastComplete()
                }
                start()
            }

            isPlaying = true
            currentPodcast = podcast
            saveCurrentPodcast(podcast.path)
            updateNotification()

            Log.d("PodcastPlayerService", "▶ Now playing: ${podcast.name}")

        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error loading podcast: ${podcast.name}", e)
            isPlaying = false
            updateNotification()
        }
    }

    private fun pausePodcast() {
        try {
            if (isPlaying && mediaPlayer?.isPlaying == true) {
                val position = mediaPlayer?.currentPosition?.toLong() ?: 0
                mediaPlayer?.pause()
                isPlaying = false

                // Speichere Position sofort beim Pausieren
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

    private fun forward() {
        try {
            mediaPlayer?.let { player ->
                val currentPos = player.currentPosition
                val duration = player.duration
                val newPos = minOf(duration, currentPos + SKIP_TIME_MS)
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

            // ✅ WICHTIG: Aktualisiere alle savedPositions VOR der Anzeige
            val updatedPodcasts = podcasts.map { podcast ->
                val currentSavedPos = getSavedPosition(podcast.path)
                podcast.copy(savedPosition = currentSavedPos)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)

            // Erstelle für jeden Podcast eine Notification
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

                val progressText = if (podcast.savedPosition > 0) {
                    " • ${formatTime(podcast.savedPosition)}"
                } else {
                    ""
                }

                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_info_details)
                    .setContentTitle("🎙️ ${podcast.name}")
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

            val summaryNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("Podcast auswählen")
                .setContentText("${podcasts.size} Podcasts verfügbar")
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

            Log.d("PodcastPlayerService", "Showing ${podcasts.size} podcasts for selection")

        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error showing podcast selection", e)
        }
    }

    private fun onPodcastComplete() {
        Log.d("PodcastPlayerService", "Podcast completed")

        // Lösche gespeicherte Position (Podcast fertig angehört)
        currentPodcast?.let { podcast ->
            savePosition(podcast.path, 0)
        }

        isPlaying = false
        mediaPlayer?.release()
        mediaPlayer = null
        updateNotification()
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
        val notification = buildNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
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
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
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
            .setContentTitle("🎙️ $title")
            .setContentText(progressText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(selectPendingIntent)
            .setDeleteIntent(deletePendingIntent)
            .addAction(
                android.R.drawable.ic_media_rew,
                "-15s",
                rewindPendingIntent
            )
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                playPausePendingIntent
            )
            .addAction(
                android.R.drawable.ic_media_ff,
                "+15s",
                forwardPendingIntent
            )

        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()

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

        // Release MediaPlayer SICHER
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
            mediaPlayer = null
            Log.d("PodcastPlayerService", "MediaPlayer released")
        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error releasing MediaPlayer", e)
        }

        // Release MediaSession
        try {
            mediaSession?.release()
            mediaSession = null
            Log.d("PodcastPlayerService", "MediaSession released")
        } catch (e: Exception) {
            Log.e("PodcastPlayerService", "Error releasing MediaSession", e)
        }

        isPlaying = false
        Log.d("PodcastPlayerService", "PodcastPlayerService destroyed")
    }

    @UnstableApi
    private inner class DummyPlayer : Player {
        override fun getApplicationLooper(): Looper = Looper.getMainLooper()
        override fun addListener(listener: Player.Listener) {}
        override fun removeListener(listener: Player.Listener) {}
        override fun setMediaItems(mediaItems: MutableList<MediaItem>) {}
        override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {}
        override fun setMediaItems(mediaItems: MutableList<MediaItem>, startIndex: Int, startPositionMs: Long) {}
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
        override fun replaceMediaItems(fromIndex: Int, toIndex: Int, mediaItems: MutableList<MediaItem>) {}
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

        @androidx.annotation.OptIn(UnstableApi::class)
        override fun getTrackSelectionParameters() = TrackSelectionParameters.DEFAULT_WITHOUT_CONTEXT
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
        override fun setDeviceVolume(volume: Int) {}
        override fun setDeviceVolume(volume: Int, flags: Int) {}
        @Deprecated("Deprecated in Java")
        override fun increaseDeviceVolume() {}
        override fun increaseDeviceVolume(flags: Int) {}
        @Deprecated("Deprecated in Java")
        override fun decreaseDeviceVolume() {}
        override fun decreaseDeviceVolume(flags: Int) {}
        @Deprecated("Deprecated in Java")
        override fun setDeviceMuted(muted: Boolean) {}
        override fun setDeviceMuted(muted: Boolean, flags: Int) {}
        override fun setAudioAttributes(audioAttributes: AudioAttributes, handleAudioFocus: Boolean) {}
    }
}