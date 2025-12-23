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
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import java.io.File

class MusicPlayerService : Service() {

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
    }

    data class Song(
        val uri: Uri,
        val name: String,
        val path: String
    )

    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var playlist: List<Song> = emptyList()
    private var currentSongIndex = 0
    private var isRepeatEnabled = false
    private lateinit var sharedPreferences: SharedPreferences

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Gespeicherte Werte laden
        currentSongIndex = sharedPreferences.getInt(KEY_CURRENT_SONG_INDEX, 0)
        isRepeatEnabled = sharedPreferences.getBoolean(KEY_REPEAT_MODE, false)

        createNotificationChannel()
        loadPlaylist()

        startForeground(NOTIFICATION_ID, createNotification(), getServiceForegroundType())

        Log.d("MusicPlayerService", "Service created. Playlist size: ${playlist.size}")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> playMusic()
            ACTION_PAUSE -> pauseMusic()
            ACTION_NEXT -> nextSong()
            ACTION_PREVIOUS -> previousSong()
            ACTION_TOGGLE_REPEAT -> toggleRepeat()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Musik Player",
            NotificationManager.IMPORTANCE_DEFAULT // Erhöht von LOW auf DEFAULT
        ).apply {
            description = "Steuerung für Musik-Wiedergabe"
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setSound(null, null) // Kein Sound für Notification-Updates
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun getServiceForegroundType(): Int {
        return try {
            if (checkSelfPermission(android.Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
        } catch (e: Exception) {
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
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

            // Index validieren
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

                    // Normalisiere den Pfad und dekodiere URL-Encoding
                    val normalizedPath = try {
                        java.net.URLDecoder.decode(data, "UTF-8")
                            .replace("\\", "/")
                            .lowercase()
                    } catch (e: Exception) {
                        data.replace("\\", "/").lowercase()
                    }

                    Log.d("MusicPlayerService", "Checking: '$name' -> '$normalizedPath'")

                    // Prüfe verschiedene mögliche Pfad-Varianten
                    val isInCloud = normalizedPath.contains("/download/cloud/") ||
                            normalizedPath.contains("/downloads/cloud/") ||
                            data.contains("/Cloud/", ignoreCase = true)

                    if (isInCloud && !normalizedPath.contains("/download/cloud/podcast")) {
                        val contentUri = Uri.withAppendedPath(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            id.toString()
                        )

                        val isAccessible = try {
                            contentResolver.openFileDescriptor(contentUri, "r")?.use { true } ?: false
                        } catch (e: Exception) {
                            Log.w("MusicPlayerService", "Cannot access URI for $name: ${e.message}")
                            false
                        }

                        if (!isAccessible) {
                            Log.w("MusicPlayerService", "⚠ Skipping inaccessible file: $name")
                            // Versuche direkte File-URI als Fallback
                            val fileUri = Uri.fromFile(File(data))
                            val displayName = if (!title.isNullOrBlank() && title != "<unknown>") {
                                title
                            } else {
                                name.substringBeforeLast('.')
                            }

                            songs.add(
                                Song(
                                    uri = fileUri, // File-URI statt Content-URI
                                    name = displayName,
                                    path = data
                                )
                            )
                            cloudCount++
                            Log.d("MusicPlayerService", "✓ Added via file URI: '$displayName'")
                            return@use // Zum nächsten File
                        }

                        val displayName = if (!title.isNullOrBlank() && title != "<unknown>") {
                            title
                        } else {
                            name.substringBeforeLast('.')
                        }

                        songs.add(
                            Song(
                                uri = contentUri,
                                name = displayName,
                                path = data
                            )
                        )
                        cloudCount++
                        Log.d("MusicPlayerService", "✓ Added from Cloud: '$displayName' (file: '$name')")
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

            // Wenn bereits ein Song läuft, fortsetzen
            if (mediaPlayer != null && !isPlaying) {
                mediaPlayer?.start()
                isPlaying = true
                Log.d("MusicPlayerService", "▶ Resumed playback")
                updateNotification()
                return
            }

            // Neuen Song laden
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

            // Alten MediaPlayer aufräumen
            mediaPlayer?.release()
            mediaPlayer = null

            val song = playlist[index]
            Log.d("MusicPlayerService", "Loading song [$index]: ${song.name}")
            Log.d("MusicPlayerService", "URI: ${song.uri}")
            Log.d("MusicPlayerService", "Path: ${song.path}")

            mediaPlayer = MediaPlayer().apply {
                try {
                    // Versuch 1: Content URI verwenden
                    setDataSource(applicationContext, song.uri)
                    prepare()
                    Log.d("MusicPlayerService", "✓ Loaded via Content URI")
                } catch (e: Exception) {
                    Log.w("MusicPlayerService", "Failed with Content URI, trying file path", e)

                    // Versuch 2: Direkter Dateipfad
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
            Log.e("MusicPlayerService", "Error loading song at index $index: ${playlist[index].name}", e)

            // Markiere fehlerhafte Songs
            if (index < playlist.size) {
                Log.e("MusicPlayerService", "Skipping broken file: ${playlist[index].path}")
            }

            // Versuche nächsten Song (aber nicht endlos)
            val nextIndex = (index + 1) % playlist.size
            if (nextIndex != index && nextIndex < playlist.size) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    loadSong(nextIndex)
                }, 500) // Kurze Verzögerung vor nächstem Versuch
            } else {
                // Keine weiteren Songs verfügbar
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
            // Wiederhole aktuellen Song
            try {
                mediaPlayer?.seekTo(0)
                mediaPlayer?.start()
                Log.d("MusicPlayerService", "🔁 Repeating song")
            } catch (e: Exception) {
                Log.e("MusicPlayerService", "Error repeating song", e)
                nextSong()
            }
        } else {
            // Nächster Song oder zurück zu Song 1
            if (currentSongIndex + 1 >= playlist.size) {
                // Am Ende der Playlist, zurück zu Song 1
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

    private fun createNotification(): android.app.Notification {
        return buildNotification()
    }

    private fun updateNotification() {
        val notification = buildNotification()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(): android.app.Notification {
        val currentSong = if (playlist.isNotEmpty() && currentSongIndex < playlist.size) {
            playlist[currentSongIndex].name
        } else {
            "Keine Playlist"
        }

        val repeatStatus = if (isRepeatEnabled) "🔁" else ""

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

        val repeatIntent = Intent(this, MusicPlayerService::class.java).apply {
            action = ACTION_TOGGLE_REPEAT
        }
        val repeatPendingIntent = PendingIntent.getService(
            this, 3, repeatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("🎵 Musik Player")
            .setContentText("$currentSong (${currentSongIndex + 1}/${playlist.size}) ${if (isRepeatEnabled) "🔁" else ""}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT) // Erhöht von LOW auf DEFAULT
            .setOngoing(true) // Macht die Notification nicht wegwischbar
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(repeatPendingIntent)

        // Nur 3 Hauptbuttons (Android Limit für kompakte Ansicht)
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
        Log.d("MusicPlayerService", "Service destroyed")
    }
}