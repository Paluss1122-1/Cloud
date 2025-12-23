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
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
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

        fun stopService(context: Context) {
            val intent = Intent(context, PodcastPlayerService::class.java)
            context.stopService(intent)
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
    private var isPlaying = false
    private var podcasts: List<Podcast> = emptyList()
    private var currentPodcast: Podcast? = null
    private lateinit var sharedPreferences: SharedPreferences
    private var positionSaveRunnable: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        createNotificationChannel()
        loadPodcasts()

        // Letzten Podcast laden
        val lastPodcastPath = sharedPreferences.getString(KEY_CURRENT_PODCAST, null)
        if (lastPodcastPath != null) {
            currentPodcast = podcasts.find { it.path == lastPodcastPath }
            Toast.makeText(this, "lastPodcastPath != null", Toast.LENGTH_SHORT).show()
        }

        startForeground(NOTIFICATION_ID, createNotification(), getServiceForegroundType())

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
                    } catch (e: Exception) {
                        data.replace("\\", "/").lowercase()
                    }

                    val isInPodcasts = normalizedPath.contains("/download/cloud/podcasts/") ||
                            normalizedPath.contains("/downloads/cloud/podcasts/") ||
                            data.contains("/Cloud/Podcasts/", ignoreCase = true)

                    if (isInPodcasts && name.endsWith(".mp3", ignoreCase = true)) {
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
        sharedPreferences.edit {
            putLong(key, position)
        }
        Log.d("PodcastPlayerService", "Position saved: ${formatTime(position)} for $podcastPath")
    }

    private fun saveCurrentPodcast(podcastPath: String) {
        sharedPreferences.edit {
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
                showSimpleNotification(
                    "Keine Podcasts",
                    "Keine MP3-Dateien in Downloads/Cloud/Podcasts gefunden"
                )
                return
            }

            val notificationManager = getSystemService(NotificationManager::class.java)

            // Erstelle für jeden Podcast eine Notification
            podcasts.forEachIndexed { index, podcast ->
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

            // Summary Notification
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

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("🎙️ $title")
            .setContentText(progressText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(selectPendingIntent)
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

    private fun showSimpleNotification(title: String, text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
            == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(70000, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // Speichere finale Position vor dem Beenden
        if (currentPodcast != null && mediaPlayer != null) {
            try {
                val position = mediaPlayer?.currentPosition?.toLong() ?: 0
                savePosition(currentPodcast!!.path, position)
            } catch (e: Exception) {
                Log.e("PodcastPlayerService", "Error saving final position", e)
            }
        }

        handler.removeCallbacks(positionSaveRunnable!!)
        mediaPlayer?.release()
        mediaPlayer = null
        Log.d("PodcastPlayerService", "Service destroyed")
    }
}