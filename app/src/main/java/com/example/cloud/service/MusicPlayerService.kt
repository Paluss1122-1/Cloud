package com.example.cloud.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
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
import com.example.cloud.MainActivity
import java.io.File
import java.net.URLDecoder

class MusicPlayerService : MediaSessionService() {

    companion object {
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

        fun startService(context: Context) {
            val intent = Intent(context, MusicPlayerService::class.java)
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MusicPlayerService::class.java)
            context.stopService(intent)
        }

        fun sendPlayAction(context: Context) {
            val intent = Intent(context, MusicPlayerService::class.java).apply {
                action = ACTION_PLAY
            }
            context.startService(intent)
        }

        fun startAndPlay(context: Context) {
            val intent = Intent(context, MusicPlayerService::class.java).apply {
                action = ACTION_PLAY
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
    private val handler = Handler(Looper.getMainLooper())

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        currentSongIndex = sharedPreferences.getInt(KEY_CURRENT_SONG_INDEX, 0)
        isRepeatEnabled = sharedPreferences.getBoolean(KEY_REPEAT_MODE, false)

        createNotificationChannel()
        loadPlaylist()

        // MediaSession ohne Player erstellen (nur für Notification/Lock Screen Control)
        mediaSession = MediaSession.Builder(this, DummyPlayer())
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        startForeground(NOTIFICATION_ID, createNotification(), getServiceForegroundType())

        Log.d("MusicPlayerService", "Service created. Playlist size: ${playlist.size}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_PLAY -> playMusic()
            ACTION_PAUSE -> pauseMusic()
            ACTION_NEXT -> nextSong()
            ACTION_PREVIOUS -> previousSong()
            ACTION_TOGGLE_REPEAT -> toggleRepeat()
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
            playlist = songs.sortedBy { it.name }

            Log.d("MusicPlayerService", "Loaded ${playlist.size} songs")
            playlist.forEachIndexed { index, song ->
                Log.d("MusicPlayerService", "[$index] ${song.name}")
            }

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

                    Log.d("MusicPlayerService", "Checking: '$name' -> '$normalizedPath'")

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
                            continue // FIXED: continue statt return@use
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
            if (playlist.isEmpty()) {
                Log.w("MusicPlayerService", "Playlist is empty, cannot play")
                updateNotification()
                return
            }

            if (mediaPlayer != null && !isPlaying) {
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
            if (playlist.isEmpty() || index < 0 || index >= playlist.size) {
                Log.w("MusicPlayerService", "Invalid song index: $index")
                return
            }

            mediaPlayer?.release()
            mediaPlayer = null

            val song = playlist[index]
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

            isPlaying = true
            currentSongIndex = index
            saveCurrentIndex()
            updateNotification()

            Log.d("MusicPlayerService", "▶ Now playing: ${song.name}")

        } catch (e: Exception) {
            Log.e("MusicPlayerService", "Error loading song at index $index", e)

            if (index < playlist.size) {
                Log.e("MusicPlayerService", "Skipping broken file: ${playlist[index].path}")
            }

            val nextIndex = (index + 1) % playlist.size
            if (nextIndex != index && nextIndex < playlist.size) {
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
            if (isPlaying && mediaPlayer?.isPlaying == true) {
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
            if (playlist.isEmpty()) return

            currentSongIndex = (currentSongIndex + 1) % playlist.size
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
            if (playlist.isEmpty()) return

            currentSongIndex = if (currentSongIndex - 1 < 0) {
                playlist.size - 1
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

        if (isRepeatEnabled) {
            try {
                mediaPlayer?.seekTo(0)
                mediaPlayer?.start()
                Log.d("MusicPlayerService", "🔁 Repeating song")
            } catch (e: Exception) {
                Log.e("MusicPlayerService", "Error repeating song", e)
                nextSong()
            }
        } else {
            if (currentSongIndex + 1 >= playlist.size) {
                currentSongIndex = 0
                Log.d("MusicPlayerService", "End of playlist, restarting from beginning")
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
        val notification = buildNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): Notification {
        val currentSong = if (playlist.isNotEmpty() && currentSongIndex < playlist.size) {
            playlist[currentSongIndex].name
        } else {
            "Keine Playlist"
        }

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

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("🎵 Musik Player")
            .setContentText("$currentSong (${currentSongIndex + 1}/${playlist.size}) ${if (isRepeatEnabled) "🔁" else ""}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
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

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession?.release()
        mediaSession = null
        Log.d("MusicPlayerService", "Service destroyed")
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

        @OptIn(UnstableApi::class)
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