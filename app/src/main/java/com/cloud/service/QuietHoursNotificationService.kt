package com.example.cloud.service

import android.Manifest
import android.R
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import com.example.cloud.mediarecorder.AudioRecorder
import com.example.cloud.quiethoursnotificationhelper.GalleryImage
import com.example.cloud.quiethoursnotificationhelper.checkQuietHours
import com.example.cloud.quiethoursnotificationhelper.cleanupOldMessages
import com.example.cloud.quiethoursnotificationhelper.commandReceiver
import com.example.cloud.quiethoursnotificationhelper.createNotification
import com.example.cloud.quiethoursnotificationhelper.createNotificationChannel
import com.example.cloud.quiethoursnotificationhelper.deleteGalleryImage
import com.example.cloud.quiethoursnotificationhelper.isQuietHoursNow
import com.example.cloud.quiethoursnotificationhelper.loadGalleryImages
import com.example.cloud.quiethoursnotificationhelper.markReadReceiver
import com.example.cloud.quiethoursnotificationhelper.messageSentReceiver
import com.example.cloud.quiethoursnotificationhelper.notificationDismissReceiver
import com.example.cloud.quiethoursnotificationhelper.playLatestVoiceNote
import com.example.cloud.quiethoursnotificationhelper.playNextVoiceNote
import com.example.cloud.quiethoursnotificationhelper.playPreviousVoiceNote
import com.example.cloud.quiethoursnotificationhelper.restoreSyncIfNeeded
import com.example.cloud.quiethoursnotificationhelper.scheduleNextCheck
import com.example.cloud.quiethoursnotificationhelper.showDeleteConfirmation
import com.example.cloud.quiethoursnotificationhelper.showNextGalleryImage
import com.example.cloud.quiethoursnotificationhelper.showPreviousGalleryImage
import com.example.cloud.quiethoursnotificationhelper.showUnreadMessages
import com.example.cloud.quiethoursnotificationhelper.startTriggerListenerIfHomeWifi
import com.example.cloud.quiethoursnotificationhelper.stopTriggerListener
import com.example.cloud.quiethoursnotificationhelper.stopVoiceNote
import com.example.cloud.quiethoursnotificationhelper.syncTodosWithLaptop
import com.example.cloud.quiethoursnotificationhelper.timeChangeReceiver
import com.example.cloud.quiethoursnotificationhelper.updateNotification
import com.example.cloud.quiethoursnotificationhelper.updateSingleSenderNotification
import com.example.cloud.showSimpleNotificationExtern
import java.io.File
import java.util.Calendar
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class QuietHoursNotificationService : Service() {
    companion object {
        const val CHANNEL_ID = "quiet_hours_channel"
        const val NOTIFICATION_ID = 999999

        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val TELEGRAM_PACKAGES =
            setOf("org.telegram.messenger", "org.telegram.messenger.web")

        fun isSupportedMessenger(packageName: String): Boolean {
            return WHATSAPP_PACKAGES.contains(packageName) || TELEGRAM_PACKAGES.contains(packageName)
        }

        const val ACTION_SHOW_MESSAGES = "com.example.cloud.ACTION_SHOW_MESSAGES"
        const val ACTION_SCHEDULED_START = "com.example.cloud.ACTION_QUIET_SCHEDULED_START"
        const val ACTION_SCHEDULED_STOP = "com.example.cloud.ACTION_QUIET_SCHEDULED_STOP"
        private const val ACTION_OPEN_SETTINGS = "com.example.cloud.ACTION_OPEN_SETTINGS"

        const val ACTION_MESSAGE_SENT = "com.example.cloud.ACTION_MESSAGE_SENT"
        const val EXTRA_SENDER = "extra_sender"
        const val CONFIRMATION_CHANNEL_ID = "message_confirmation_channel"

        private lateinit var sharedPreferences: SharedPreferences
        private const val ACTION_OPEN_MUSIC_PLAYER = "com.example.cloud.ACTION_OPEN_MUSIC_PLAYER"
        const val ACTION_RESTART_MUSIC_PLAYER =
            "com.example.cloud.ACTION_RESTART_MUSIC_PLAYER"

        const val ACTION_NOTIFICATION_DISMISSED =
            "com.example.cloud.ACTION_NOTIFICATION_DISMISSED"

        const val ACTION_CHANGE_START = "com.example.cloud.ACTION_CHANGE_START"
        const val ACTION_CHANGE_END = "com.example.cloud.ACTION_CHANGE_END"


        const val ACTION_PLAY_VOICE_NOTE = "com.example.cloud.ACTION_PLAY_VOICE_NOTE"
        const val ACTION_NEXT_VOICE_NOTE = "com.example.cloud.ACTION_NEXT_VOICE_NOTE"
        const val ACTION_PREV_VOICE_NOTE = "com.example.cloud.ACTION_PREV_VOICE_NOTE"
        const val ACTION_STOP_VOICE_NOTE = "com.example.cloud.ACTION_STOP_VOICE_NOTE"
        const val EXTRA_SENDER_FOR_VOICE = "extra_sender_for_voice"

        const val VOICE_NOTE_CHANNEL_ID = "voice_note_player_channel"
        const val ACTION_EXECUTE_COMMAND = "com.example.cloud.ACTION_EXECUTE_COMMAND"

        const val PREFS_REPLY_DATA = "reply_data_prefs"
        const val KEY_SAVED_SENDER = "saved_sender"
        const val KEY_SAVED_PACKAGE = "saved_package"
        const val KEY_SAVED_RESULT_KEY = "saved_result_key"
        const val KEY_HAS_SAVED_DATA = "has_saved_data"

        val commandHistory = mutableListOf<String>()

        private const val ACTION_SHOW_GALLERY = "com.example.cloud.ACTION_SHOW_GALLERY"
        const val ACTION_NEXT_GALLERY_IMAGE = "com.example.cloud.ACTION_NEXT_GALLERY_IMAGE"
        const val ACTION_PREV_GALLERY_IMAGE = "com.example.cloud.ACTION_PREV_GALLERY_IMAGE"
        const val GALLERY_CHANNEL_ID = "gallery_channel"

        const val SSN_CHANNEL_ID = "show_simple_not_channel"

        const val ACTION_CONFIRM_DELETE_IMAGE =
            "com.example.cloud.ACTION_CONFIRM_DELETE_IMAGE"
        const val ACTION_DELETE_IMAGE = "com.example.cloud.ACTION_DELETE_IMAGE"
        const val ACTION_CANCEL_DELETE = "com.example.cloud.ACTION_CANCEL_DELETE"
        const val EXTRA_IMAGE_INDEX = "extra_image_index"
        const val DELETE_CONFIRMATION_CHANNEL_ID = "delete_confirmation_channel"

        const val ACTION_MARK_PARTS_READ = "com.example.cloud.ACTION_MARK_PARTS_READ"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val ALARM_REQUEST_CODE = 1001

        const val THRESHOLD_MINUTES = 30
        const val MAX_MESSAGES_PER_CONTACT = 50
        const val MAX_VOICE_NOTE_FILES = 20

        var currentSenderForVoiceNote: String? = null
        var voiceNoteFiles: List<File> = emptyList()
        var voiceNotePlayer: MediaPlayer? = null
        var currentVoiceNoteIndex = 0
        val readMessageIds = mutableSetOf<String>()

        val handlerThread = HandlerThread("QuietHoursWorker").apply { start() }
        val workerHandler = Handler(handlerThread.looper)
        val mainHandler = Handler(Looper.getMainLooper())
        var galleryImages: List<GalleryImage> = emptyList()
        var currentGalleryIndex = 0
        var isCurrentlyQuietHours = false
        val handler = Handler(Looper.getMainLooper())

        var audioRecorder: AudioRecorder? = null
        var currentRecordingFile: File? = null

        fun getCheckRunnable(context: Context): Runnable = Runnable {
            checkQuietHours(context)
            scheduleNextCheck(context)
        }

        fun startService(context: Context) {
            val intent = Intent(context, QuietHoursNotificationService::class.java)
            context.startForegroundService(intent)
        }

        fun updateSingleSenderNotification(context: Context, sender: String) {
            val intent = Intent(context, QuietHoursNotificationService::class.java).apply {
                action = "ACTION_UPDATE_SINGLE_SENDER"
                putExtra("EXTRA_SENDER", sender)
            }
            context.startService(intent)
        }

        fun calculateNextStatusChange(
            now: Calendar,
            quietStart: Int,
            quietEnd: Int
        ): Calendar {
            val nextChange = Calendar.getInstance().apply {
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (isCurrentlyQuietHours) {
                nextChange.set(Calendar.HOUR_OF_DAY, quietEnd)
                nextChange.set(Calendar.MINUTE, 0)

                if (nextChange.timeInMillis <= now.timeInMillis) {
                    nextChange.add(Calendar.DAY_OF_YEAR, 1)
                }
            } else {
                nextChange.set(Calendar.HOUR_OF_DAY, quietStart)
                nextChange.set(Calendar.MINUTE, 0)

                if (nextChange.timeInMillis <= now.timeInMillis) {
                    nextChange.add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            return nextChange
        }
    }

    private val checkRunnable = getCheckRunnable(this)

    private val prefChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "saved_number" || key == "saved_number_start") {
                handler.removeCallbacks(checkRunnable)

                isCurrentlyQuietHours = isQuietHoursNow(this)

                updateNotification(this)

                handler.post(checkRunnable)
            }
        }

    override fun onCreate() {
        super.onCreate()
        Log.d("QuietHoursService", "Service created")
        sharedPreferences = getSharedPreferences("quick_settings_prefs", MODE_PRIVATE)
        createNotificationChannel(this)

        isCurrentlyQuietHours = isQuietHoursNow(this)
        startForeground(NOTIFICATION_ID, createNotification(isCurrentlyQuietHours, this))

        sharedPreferences.registerOnSharedPreferenceChangeListener(prefChangeListener)

        handler.post(checkRunnable)
        schedulePeriodicCleanup()
        restoreSyncIfNeeded(this)
        startTriggerListenerIfHomeWifi(this)
        registerWifiCallback()

        val filter = IntentFilter(ACTION_MESSAGE_SENT)
        registerReceiver(messageSentReceiver, filter, RECEIVER_NOT_EXPORTED)

        val dismissFilter = IntentFilter(ACTION_NOTIFICATION_DISMISSED)
        registerReceiver(notificationDismissReceiver, dismissFilter, RECEIVER_NOT_EXPORTED)

        val timeChangeFilter = IntentFilter().apply {
            addAction(ACTION_CHANGE_START)
            addAction(ACTION_CHANGE_END)
        }
        registerReceiver(timeChangeReceiver, timeChangeFilter, RECEIVER_NOT_EXPORTED)

        val commandFilter = IntentFilter(ACTION_EXECUTE_COMMAND)
        registerReceiver(commandReceiver, commandFilter, RECEIVER_NOT_EXPORTED)

        val markReadFilter = IntentFilter(ACTION_MARK_PARTS_READ)
        registerReceiver(markReadReceiver, markReadFilter, RECEIVER_NOT_EXPORTED)
    }

    private fun schedulePeriodicCleanup() {
        workerHandler.postDelayed(object : Runnable {
            override fun run() {
                cleanupReadMessages()
                cleanupOldMessages()
                workerHandler.postDelayed(this, 6 * 60 * 60 * 1000) // 6h
            }
        }, 6 * 60 * 60 * 1000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("QuietHoursService", "Service started with intent: ${intent?.action}")
        when (intent?.action) {
            ACTION_SCHEDULED_STOP -> {
                // Alarm requested that the quiet window ended -> stop the service
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_SCHEDULED_START -> {
                // Alarm requested start: ensure we enter quiet mode
                isCurrentlyQuietHours = isQuietHoursNow(this)
                startForeground(NOTIFICATION_ID, createNotification(isCurrentlyQuietHours, this))
            }

            ACTION_SHOW_MESSAGES -> {
                showUnreadMessages(this)
            }

            "ACTION_RESTORE_NOTIFICATION" -> {
                val notification = createNotification(isCurrentlyQuietHours, this)
                startForeground(NOTIFICATION_ID, notification)
            }

            "ACTION_UPDATE_SINGLE_SENDER" -> {
                val sender = intent.getStringExtra("EXTRA_SENDER")
                if (sender != null) {
                    updateSingleSenderNotification(sender, this)
                }
            }

            "ACTION_CONTENT_INTENT" -> {
                val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
                try {
                    val cameraId = cameraManager.cameraIdList.firstOrNull()

                    if (cameraId != null) {
                        cameraManager.turnOnTorchWithStrengthLevel(cameraId, 1)
                    }
                } catch (e: Exception) {
                    Log.e("QuietHoursService", "Error setting flashlight level", e)
                    showSimpleNotification(
                        "❌ Taschenlampe",
                        "Helligkeit konnte nicht gesetzt werden: ${e.message}",
                        20.seconds
                    )
                }
            }

            ACTION_OPEN_SETTINGS -> {
                openAndroidSettings()
            }

            ACTION_OPEN_MUSIC_PLAYER -> {
                openMusicPlayer()
            }

            ACTION_RESTART_MUSIC_PLAYER -> {
                restartMusicPlayer(context = this)
            }

            ACTION_PLAY_VOICE_NOTE -> {
                val sender = intent.getStringExtra(EXTRA_SENDER_FOR_VOICE)
                if (sender != null) {
                    playLatestVoiceNote(
                        sender,
                        this
                    )
                }
            }

            ACTION_NEXT_VOICE_NOTE -> {
                playNextVoiceNote(this)
            }

            ACTION_PREV_VOICE_NOTE -> {
                playPreviousVoiceNote(this)
            }

            ACTION_STOP_VOICE_NOTE -> {
                stopVoiceNote(this)
            }

            ACTION_SHOW_GALLERY -> {
                loadGalleryImages(0, this)
            }

            ACTION_NEXT_GALLERY_IMAGE -> {
                showNextGalleryImage(this)
            }

            ACTION_PREV_GALLERY_IMAGE -> {
                showPreviousGalleryImage(this)
            }

            ACTION_CONFIRM_DELETE_IMAGE -> {
                val imageIndex = intent.getIntExtra(EXTRA_IMAGE_INDEX, -1)
                if (imageIndex >= 0) {
                    showDeleteConfirmation(imageIndex, this)
                }
            }

            ACTION_DELETE_IMAGE -> {
                val imageIndex = intent.getIntExtra(EXTRA_IMAGE_INDEX, -1)
                if (imageIndex >= 0) {
                    deleteGalleryImage(imageIndex, this)
                }
            }

            ACTION_CANCEL_DELETE -> {
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager.cancel(80000)
            }

            "ACTION_SYNC_LAPTOP" -> {
                syncTodosWithLaptop(this)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    override fun onDestroy() {
        super.onDestroy()

        handler.removeCallbacksAndMessages(null)

        networkCallback?.let {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            cm.unregisterNetworkCallback(it)
            networkCallback = null
        }

        // MediaPlayer
        voiceNotePlayer?.apply {
            if (isPlaying) stop()
            reset()
            release()
        }
        voiceNotePlayer = null

        // AudioRecorder
        audioRecorder?.stopRecording()
        audioRecorder = null

        // Collections leeren
        readMessageIds.clear()
        voiceNoteFiles = emptyList()
        galleryImages = emptyList()
        commandHistory.clear()

        // Receivers
        try {
            unregisterReceiver(messageSentReceiver)
            unregisterReceiver(notificationDismissReceiver)
            unregisterReceiver(timeChangeReceiver)
            unregisterReceiver(commandReceiver)
            unregisterReceiver(markReadReceiver)
        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error unregistering receivers", e)
        }

        // SharedPreferences Listener
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefChangeListener)

        val restartIntent = Intent(applicationContext, QuietHoursNotificationService::class.java)
        val pendingIntent = PendingIntent.getService(
            applicationContext, 0, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 1000,
            pendingIntent
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val notification = createNotification(isCurrentlyQuietHours, this)
        startForeground(NOTIFICATION_ID, notification)

        val restartServiceIntent =
            Intent(applicationContext, QuietHoursNotificationService::class.java)
        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext,
            1,
            restartServiceIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
    }

    private fun openAndroidSettings() {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error opening settings", e)
            showSimpleNotification("Fehler", "Einstellungen konnten nicht geöffnet werden")
        }
    }

    fun showSimpleNotification(
        title: String,
        text: String,
        duration: Duration = Duration.ZERO
    ) {
        val notificationId = System.currentTimeMillis().toInt()

        val notification = NotificationCompat.Builder(this, SSN_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setGroup("group_info")
            .setGroupSummary(false)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(notificationId, notification)

            if (duration > Duration.ZERO) {
                Handler(Looper.getMainLooper()).postDelayed(
                    { notificationManager.cancel(notificationId) },
                    duration.inWholeMilliseconds
                )
            }
        }
    }

    private fun cleanupReadMessages() {
        val cutoffTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000) // 24h

        readMessageIds.removeAll { messageId ->
            val timestamp = messageId.substringAfterLast("_").toLongOrNull() ?: 0
            timestamp < cutoffTime
        }
    }

    private fun openMusicPlayer() {
        try {
            MusicPlayerServiceCompat.startService(this)
            MusicPlayerServiceCompat.startAndPlay(this)
        } catch (e: Exception) {
            showSimpleNotification("Fehler", "Musik Player konnte nicht geöffnet werden")
        }
    }

    private var networkCallback: android.net.ConnectivityManager.NetworkCallback? = null

    private fun registerWifiCallback() {
        val connectivityManager =
            getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val request = android.net.NetworkRequest.Builder()
            .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                Log.d("TodoSync", "📶 WLAN verbunden, prüfe Heim-WLAN...")
                showSimpleNotificationExtern(
                    "📶 WLAN verbunden",
                    "Prüfe Heim-WLAN...",
                    10.seconds,
                    this@QuietHoursNotificationService
                )
                startTriggerListenerIfHomeWifi(this@QuietHoursNotificationService)
            }

            override fun onLost(network: android.net.Network) {
                Log.d("TodoSync", "📵 WLAN getrennt, stoppe Trigger Listener")
                stopTriggerListener()
            }
        }

        connectivityManager.registerNetworkCallback(request, networkCallback!!)
    }
}