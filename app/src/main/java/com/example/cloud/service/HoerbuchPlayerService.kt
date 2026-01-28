package com.example.cloud.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
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

class HoerbuchPlayerService : MediaSessionService() {

    companion object {
        private const val CHANNEL_ID = "hoerbuch_player_channel"
        private const val NOTIFICATION_ID = 777777
        private const val HOERBUCH_PREFS = "hoerbuch_prefs"

        private const val ACTION_PLAY = "com.example.cloud.ACTION_HOERBUCH_PLAY"
        private const val ACTION_PAUSE = "com.example.cloud.ACTION_HOERBUCH_PAUSE"
        private const val ACTION_NEXT = "com.example.cloud.ACTION_HOERBUCH_NEXT"
        private const val ACTION_PREVIOUS = "com.example.cloud.ACTION_HOERBUCH_PREVIOUS"
        private const val ACTION_FORWARD_30 = "com.example.cloud.ACTION_HOERBUCH_FORWARD_30"
        private const val ACTION_BACKWARD_30 = "com.example.cloud.ACTION_HOERBUCH_BACKWARD_30"
        private const val ACTION_SELECT_HOERBUCH = "com.example.cloud.ACTION_SELECT_HOERBUCH"
        private const val ACTION_SHOW_HOERBUECHER = "com.example.cloud.ACTION_SHOW_HOERBUECHER"

        private const val EXTRA_HOERBUCH_NAME = "extra_hoerbuch_name"
        private const val EXTRA_FILE_INDEX = "extra_file_index"

        private const val KEY_CURRENT_HOERBUCH = "current_hoerbuch"
        private const val KEY_CURRENT_FILE_INDEX = "current_file_index"
        private const val KEY_CURRENT_POSITION = "current_position"

        private const val MEDIA_STATE_PREFS = "media_state_prefs"
        private const val KEY_ACTIVE_SERVICE = "active_media_service"
        private const val SERVICE_HOERBUCH = "hoerbuch"

        private const val HOERBUCH_BASE_PATH = "/storage/emulated/0/Download/Cloud/Hoerbuecher"

        fun startService(context: Context) {
            val intent = Intent(context, HoerbuchPlayerService::class.java)
            context.startForegroundService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, HoerbuchPlayerService::class.java)
            context.stopService(intent)
        }

        fun sendPlayAction(context: Context) {
            val intent = Intent(context, HoerbuchPlayerService::class.java).apply {
                action = ACTION_PLAY
            }
            context.startService(intent)
        }

        fun showHoerbuecher(context: Context) {
            val intent = Intent(context, HoerbuchPlayerService::class.java).apply {
                action = ACTION_SHOW_HOERBUECHER
            }
            context.startService(intent)
        }
    }

    data class HoerbuchFile(
        val file: File,
        val displayName: String,
        val index: Int
    )

    private var mediaPlayer: MediaPlayer? = null
    private var mediaSession: MediaSession? = null
    private var isPlaying = false

    private var currentHoerbuch: String? = null
    private var currentFiles: List<HoerbuchFile> = emptyList()
    private var currentFileIndex = 0
    private var currentPosition = 0L

    private lateinit var hoerbuchPrefs: SharedPreferences
    private lateinit var mediaStatePrefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private val positionSaveRunnable = object : Runnable {
        override fun run() {
            saveCurrentPosition()
            handler.postDelayed(this, 5000)
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        hoerbuchPrefs = getSharedPreferences(HOERBUCH_PREFS, MODE_PRIVATE)
        mediaStatePrefs = getSharedPreferences(MEDIA_STATE_PREFS, MODE_PRIVATE)

        createNotificationChannel()
        loadSavedState()

        mediaSession = MediaSession.Builder(this, DummyPlayer())
            .setId("HoerbuchPlayerSession")
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

        Log.d("HoerbuchPlayerService", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_PLAY -> playHoerbuch()
            ACTION_PAUSE -> pauseHoerbuch()
            ACTION_NEXT -> nextFile()
            ACTION_PREVIOUS -> previousFile()
            ACTION_FORWARD_30 -> seekForward()
            ACTION_BACKWARD_30 -> seekBackward()
            ACTION_SELECT_HOERBUCH -> {
                val hoerbuchName = intent.getStringExtra(EXTRA_HOERBUCH_NAME)
                val fileIndex = intent.getIntExtra(EXTRA_FILE_INDEX, 0)
                if (hoerbuchName != null) {
                    selectHoerbuch(hoerbuchName, fileIndex)
                }
            }
            ACTION_SHOW_HOERBUECHER -> {
                showAvailableHoerbuecher()
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
            "Hörbuch Player",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Steuerung für Hörbuch-Wiedergabe"
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

    private fun loadSavedState() {
        currentHoerbuch = hoerbuchPrefs.getString(KEY_CURRENT_HOERBUCH, null)
        currentFileIndex = hoerbuchPrefs.getInt(KEY_CURRENT_FILE_INDEX, 0)
        currentPosition = hoerbuchPrefs.getLong(KEY_CURRENT_POSITION, 0L)

        if (currentHoerbuch != null) {
            loadHoerbuchFiles(currentHoerbuch!!)
        }

        Log.d("HoerbuchPlayerService", "Loaded state: Hörbuch=$currentHoerbuch, File=$currentFileIndex, Position=$currentPosition")
    }

    private fun saveCurrentState() {
        hoerbuchPrefs.edit(commit = true) {
            putString(KEY_CURRENT_HOERBUCH, currentHoerbuch)
            putInt(KEY_CURRENT_FILE_INDEX, currentFileIndex)
            putLong(KEY_CURRENT_POSITION, currentPosition)
        }
    }

    private fun saveCurrentPosition() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    currentPosition = player.currentPosition.toLong()
                    hoerbuchPrefs.edit(commit = true) {
                        putLong(KEY_CURRENT_POSITION, currentPosition)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("HoerbuchPlayerService", "Error saving position", e)
        }
    }

    private fun getAvailableHoerbuecher(): List<String> {
        try {
            val baseDir = File(HOERBUCH_BASE_PATH)

            if (!baseDir.exists() || !baseDir.isDirectory) {
                Log.w("HoerbuchPlayerService", "Hörbücher directory does not exist: $HOERBUCH_BASE_PATH")
                return emptyList()
            }

            val hoerbuecher = baseDir.listFiles { file ->
                file.isDirectory && file.listFiles { f ->
                    f.extension.lowercase() == "mp3"
                }?.isNotEmpty() == true
            }?.map { it.name }?.sorted() ?: emptyList()

            Log.d("HoerbuchPlayerService", "Found ${hoerbuecher.size} Hörbücher: $hoerbuecher")
            return hoerbuecher

        } catch (e: Exception) {
            Log.e("HoerbuchPlayerService", "Error getting available Hörbücher", e)
            return emptyList()
        }
    }

    private fun showAvailableHoerbuecher() {
        try {
            val hoerbuecher = getAvailableHoerbuecher()

            if (hoerbuecher.isEmpty()) {
                showSimpleNotification(
                    "📚 Keine Hörbücher",
                    "Keine Hörbücher in $HOERBUCH_BASE_PATH gefunden"
                )
                return
            }

            val notificationManager = getSystemService(NotificationManager::class.java)

            hoerbuecher.forEachIndexed { index, hoerbuchName ->
                val fileCount = loadHoerbuchFiles(hoerbuchName).size

                val selectIntent = Intent(this, HoerbuchPlayerService::class.java).apply {
                    action = ACTION_SELECT_HOERBUCH
                    putExtra(EXTRA_HOERBUCH_NAME, hoerbuchName)
                    putExtra(EXTRA_FILE_INDEX, 0)
                }

                val selectPendingIntent = PendingIntent.getService(
                    this,
                    90000 + index,
                    selectIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val isCurrent = hoerbuchName == currentHoerbuch
                val icon = if (isCurrent) "▶️" else "📖"

                val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_media_play)
                    .setContentTitle("$icon $hoerbuchName")
                    .setContentText("$fileCount Dateien • Tippen zum Auswählen")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setContentIntent(selectPendingIntent)
                    .setGroup("hoerbuecher_selection")
                    .build()

                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    notificationManager.notify(90000 + index, notification)
                }
            }

            val summaryNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("📚 ${hoerbuecher.size} Hörbücher verfügbar")
                .setContentText("Tippe auf ein Hörbuch zum Auswählen")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setGroup("hoerbuecher_selection")
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()

            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(89999, summaryNotification)
            }

        } catch (e: Exception) {
            Log.e("HoerbuchPlayerService", "Error showing Hörbücher", e)
            showSimpleNotification(
                "❌ Fehler",
                "Hörbücher konnten nicht angezeigt werden"
            )
        }
    }

    private fun loadHoerbuchFiles(hoerbuchName: String): List<HoerbuchFile> {
        try {
            val hoerbuchDir = File(HOERBUCH_BASE_PATH, hoerbuchName)

            if (!hoerbuchDir.exists() || !hoerbuchDir.isDirectory) {
                Log.w("HoerbuchPlayerService", "Hörbuch directory does not exist: ${hoerbuchDir.absolutePath}")
                return emptyList()
            }

            val files = hoerbuchDir.listFiles { file ->
                file.extension.lowercase() == "mp3"
            }?.sortedBy { it.name }?.mapIndexed { index, file ->
                HoerbuchFile(
                    file = file,
                    displayName = file.nameWithoutExtension,
                    index = index
                )
            } ?: emptyList()

            Log.d("HoerbuchPlayerService", "Loaded ${files.size} files for Hörbuch: $hoerbuchName")
            return files

        } catch (e: Exception) {
            Log.e("HoerbuchPlayerService", "Error loading Hörbuch files", e)
            return emptyList()
        }
    }

    private fun selectHoerbuch(hoerbuchName: String, fileIndex: Int = 0) {
        try {
            pauseHoerbuch()

            currentHoerbuch = hoerbuchName
            currentFiles = loadHoerbuchFiles(hoerbuchName)
            currentFileIndex = fileIndex.coerceIn(0, currentFiles.size - 1)
            currentPosition = 0L

            saveCurrentState()

            clearHoerbuchSelectionNotifications()

            showSimpleNotification(
                "📖 Hörbuch ausgewählt",
                "$hoerbuchName (${currentFiles.size} Dateien)"
            )

            updateNotification()

            Log.d("HoerbuchPlayerService", "Selected Hörbuch: $hoerbuchName with ${currentFiles.size} files")

        } catch (e: Exception) {
            Log.e("HoerbuchPlayerService", "Error selecting Hörbuch", e)
            showSimpleNotification(
                "❌ Fehler",
                "Hörbuch konnte nicht ausgewählt werden"
            )
        }
    }

    private fun clearHoerbuchSelectionNotifications() {
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)

            for (i in 89999..90100) {
                notificationManager.cancel(i)
            }

        } catch (e: Exception) {
            Log.e("HoerbuchPlayerService", "Error clearing selection notifications", e)
        }
    }

    private fun playHoerbuch() {
        try {
            if (currentFiles.isEmpty() || currentHoerbuch == null) {
                showSimpleNotification(
                    "❌ Kein Hörbuch",
                    "Wähle zuerst ein Hörbuch mit 'hb' aus"
                )
                return
            }

            setActiveService()

            if (mediaPlayer != null && !isPlaying) {
                mediaPlayer?.start()
                isPlaying = true
                startPositionTracking()
                updateNotification()
                Log.d("HoerbuchPlayerService", "▶ Resumed playback")
                return
            }

            if (mediaPlayer == null) {
                loadFile(currentFileIndex)
            }

        } catch (e: Exception) {
            Log.e("HoerbuchPlayerService", "Error playing Hörbuch", e)
            showSimpleNotification("❌ Fehler", "Wiedergabe konnte nicht gestartet werden")
        }
    }

    private fun loadFile(index: Int) {
        try {
            if (currentFiles.isEmpty() || index < 0 || index >= currentFiles.size) {
                Log.w("HoerbuchPlayerService", "Invalid file index: $index")
                return
            }

            mediaPlayer?.release()
            mediaPlayer = null

            val hoerbuchFile = currentFiles[index]
            Log.d("HoerbuchPlayerService", "Loading file [$index]: ${hoerbuchFile.displayName}")

            mediaPlayer = MediaPlayer().apply {
                try {
                    setDataSource(hoerbuchFile.file.absolutePath)
                    prepare()

                    if (index == currentFileIndex && currentPosition > 0) {
                        seekTo(currentPosition.toInt())
                        Log.d("HoerbuchPlayerService", "Restored position: $currentPosition ms")
                    }

                    setOnCompletionListener {
                        onFileComplete()
                    }

                    start()

                } catch (e: Exception) {
                    Log.e("HoerbuchPlayerService", "Failed to load file", e)
                    throw e
                }
            }

            isPlaying = true
            currentFileIndex = index
            startPositionTracking()
            saveCurrentState()
            updateNotification()

            Log.d("HoerbuchPlayerService", "▶ Now playing: ${hoerbuchFile.displayName}")

        } catch (e: Exception) {
            Log.e("HoerbuchPlayerService", "Error loading file at index $index", e)
            showSimpleNotification(
                "❌ Fehler",
                "Datei konnte nicht geladen werden"
            )

            val nextIndex = (index + 1) % currentFiles.size
            if (nextIndex != index && nextIndex < currentFiles.size) {
                handler.postDelayed({
                    loadFile(nextIndex)
                }, 500)
            } else {
                isPlaying = false
                updateNotification()
            }
        }
    }

    private fun pauseHoerbuch() {
        try {
            if (isPlaying && mediaPlayer?.isPlaying == true) {
                currentPosition = mediaPlayer?.currentPosition?.toLong() ?: 0L
                mediaPlayer?.pause()
                isPlaying = false
                stopPositionTracking()
                saveCurrentState()
                updateNotification()
                Log.d("HoerbuchPlayerService", "⏸ Hörbuch paused at position $currentPosition")
            }
        } catch (e: Exception) {
            Log.e("HoerbuchPlayerService", "Error pausing Hörbuch", e)
        }
    }

    private fun nextFile() {
        try {
            if (currentFiles.isEmpty()) return

            currentFileIndex = (currentFileIndex + 1) % currentFiles.size
            currentPosition = 0L

            mediaPlayer?.release()
            mediaPlayer = null

            loadFile(currentFileIndex)

            Log.d("HoerbuchPlayerService", "⏭ Next file: index $currentFileIndex")

        } catch (e: Exception) {
            Log.e("HoerbuchPlayerService", "Error skipping to next file", e)
        }
    }

    private fun previousFile() {
        try {
            if (currentFiles.isEmpty()) return

            val currentPos = mediaPlayer?.currentPosition ?: 0

            if (currentPos > 3000) {
                mediaPlayer?.seekTo(0)
                currentPosition = 0L
                saveCurrentState()
            } else {
                currentFileIndex = if (currentFileIndex - 1 < 0) {
                    currentFiles.size - 1
                } else {
                    currentFileIndex - 1
                }
                currentPosition = 0L

                mediaPlayer?.release()
                mediaPlayer = null

                loadFile(currentFileIndex)
            }

            Log.d("HoerbuchPlayerService", "⏮ Previous file: index $currentFileIndex")

        } catch (e: Exception) {
            Log.e("HoerbuchPlayerService", "Error skipping to previous file", e)
        }
    }

    private fun seekForward() {
        try {
            mediaPlayer?.let { player ->
                val newPosition = (player.currentPosition + 30000).coerceAtMost(player.duration)
                player.seekTo(newPosition)
                currentPosition = newPosition.toLong()
                saveCurrentState()

                showSimpleNotification("⏩ +30s", "Position: ${formatTime(newPosition.toLong())}")
                updateNotification()
            }
        } catch (e: Exception) {
            Log.e("HoerbuchPlayerService", "Error seeking forward", e)
        }
    }

    private fun seekBackward() {
        try {
            mediaPlayer?.let { player ->
                val newPosition = (player.currentPosition - 30000).coerceAtLeast(0)
                player.seekTo(newPosition)
                currentPosition = newPosition.toLong()
                saveCurrentState()

                showSimpleNotification("⏪ -30s", "Position: ${formatTime(newPosition.toLong())}")
                updateNotification()
            }
        } catch (e: Exception) {
            Log.e("HoerbuchPlayerService", "Error seeking backward", e)
        }
    }

    private fun onFileComplete() {
        try {
            Log.d("HoerbuchPlayerService", "File completed at index $currentFileIndex")

            if (currentFileIndex + 1 >= currentFiles.size) {
                currentFileIndex = 0
                currentPosition = 0L
                isPlaying = false
                stopPositionTracking()
                saveCurrentState()
                updateNotification()

                showSimpleNotification(
                    "✅ Hörbuch beendet",
                    "${currentHoerbuch} wurde komplett abgespielt"
                )

                Log.d("HoerbuchPlayerService", "Hörbuch completed, stopped at beginning")
            } else {
                currentFileIndex++
                currentPosition = 0L

                mediaPlayer?.release()
                mediaPlayer = null

                loadFile(currentFileIndex)
            }

        } catch (e: Exception) {
            Log.e("HoerbuchPlayerService", "Error handling file completion", e)
        }
    }

    private fun startPositionTracking() {
        handler.removeCallbacks(positionSaveRunnable)
        handler.post(positionSaveRunnable)
    }

    private fun stopPositionTracking() {
        handler.removeCallbacks(positionSaveRunnable)
        saveCurrentPosition()
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
        val currentFile = if (currentFiles.isNotEmpty() && currentFileIndex < currentFiles.size) {
            currentFiles[currentFileIndex].displayName
        } else {
            "Kein Hörbuch ausgewählt"
        }

        val hoerbuchName = currentHoerbuch ?: "Keine Auswahl"

        val position = mediaPlayer?.currentPosition?.toLong() ?: currentPosition
        val duration = mediaPlayer?.duration?.toLong() ?: 0L
        val progress = if (duration > 0) "${formatTime(position)} / ${formatTime(duration)}" else ""

        val playPauseIntent = Intent(this, HoerbuchPlayerService::class.java).apply {
            action = if (isPlaying) ACTION_PAUSE else ACTION_PLAY
        }
        val playPausePendingIntent = PendingIntent.getService(
            this, 0, playPauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val previousIntent = Intent(this, HoerbuchPlayerService::class.java).apply {
            action = ACTION_PREVIOUS
        }
        val previousPendingIntent = PendingIntent.getService(
            this, 1, previousIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(this, HoerbuchPlayerService::class.java).apply {
            action = ACTION_NEXT
        }
        val nextPendingIntent = PendingIntent.getService(
            this, 2, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val backwardIntent = Intent(this, HoerbuchPlayerService::class.java).apply {
            action = ACTION_BACKWARD_30
        }
        val backwardPendingIntent = PendingIntent.getService(
            this, 3, backwardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val forwardIntent = Intent(this, HoerbuchPlayerService::class.java).apply {
            action = ACTION_FORWARD_30
        }
        val forwardPendingIntent = PendingIntent.getService(
            this, 4, forwardIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("📖 $hoerbuchName")
            .setContentText("$currentFile (${currentFileIndex + 1}/${currentFiles.size})")
            .setSubText(progress)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (currentFiles.isNotEmpty()) {
            builder
                .addAction(
                    android.R.drawable.ic_media_previous,
                    "Zurück",
                    previousPendingIntent
                )
                .addAction(
                    android.R.drawable.ic_media_rew,
                    "-30s",
                    backwardPendingIntent
                )
                .addAction(
                    if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                    if (isPlaying) "Pause" else "Spielen",
                    playPausePendingIntent
                )
                .addAction(
                    android.R.drawable.ic_media_ff,
                    "+30s",
                    forwardPendingIntent
                )
                .addAction(
                    android.R.drawable.ic_media_next,
                    "Weiter",
                    nextPendingIntent
                )
        }

        return builder.build()
    }

    private fun formatTime(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    private fun showSimpleNotification(title: String, text: String) {
        try {
            val notificationId = System.currentTimeMillis().toInt()

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            val notificationManager = getSystemService(NotificationManager::class.java)

            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(notificationId, notification)
            }

        } catch (e: Exception) {
            Log.e("HoerbuchPlayerService", "Error showing notification", e)
        }
    }

    private fun setActiveService() {
        mediaStatePrefs.edit(commit = true) {
            putString(KEY_ACTIVE_SERVICE, SERVICE_HOERBUCH)
        }
        Log.d("HoerbuchPlayerService", "✓ Set as active media service")
    }

    private fun clearActiveService() {
        val currentActive = mediaStatePrefs.getString(KEY_ACTIVE_SERVICE, null)
        if (currentActive == SERVICE_HOERBUCH) {
            mediaStatePrefs.edit(commit = true) {
                remove(KEY_ACTIVE_SERVICE)
            }
            Log.d("HoerbuchPlayerService", "✓ Cleared active service state")
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        clearActiveService()
        stopPositionTracking()

        mediaPlayer?.release()
        mediaPlayer = null
        mediaSession?.release()
        mediaSession = null

        Log.d("HoerbuchPlayerService", "Service destroyed")
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
    }
}