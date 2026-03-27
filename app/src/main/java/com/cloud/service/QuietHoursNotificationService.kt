package com.cloud.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Context.WINDOW_SERVICE
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.cloud.Config.DEL_GAL_CONF
import com.cloud.Config.cms
import com.cloud.mediarecorder.AudioRecorder
import com.cloud.quiethoursnotificationhelper.GalleryImage
import com.cloud.quiethoursnotificationhelper.checkQuietHours
import com.cloud.quiethoursnotificationhelper.cleanupOldMessages
import com.cloud.quiethoursnotificationhelper.commandReceiver
import com.cloud.quiethoursnotificationhelper.createNotification
import com.cloud.quiethoursnotificationhelper.createNotificationChannel
import com.cloud.quiethoursnotificationhelper.deleteGalleryImage
import com.cloud.quiethoursnotificationhelper.isQuietHoursNow
import com.cloud.quiethoursnotificationhelper.loadGalleryImages
import com.cloud.quiethoursnotificationhelper.markReadReceiver
import com.cloud.quiethoursnotificationhelper.messageSentReceiver
import com.cloud.quiethoursnotificationhelper.notificationDismissReceiver
import com.cloud.quiethoursnotificationhelper.playLatestVoiceNote
import com.cloud.quiethoursnotificationhelper.playNextVoiceNote
import com.cloud.quiethoursnotificationhelper.playPreviousVoiceNote
import com.cloud.quiethoursnotificationhelper.restoreSyncIfNeeded
import com.cloud.quiethoursnotificationhelper.scheduleNextCheck
import com.cloud.quiethoursnotificationhelper.showDeleteConfirmation
import com.cloud.quiethoursnotificationhelper.showNextGalleryImage
import com.cloud.quiethoursnotificationhelper.showPreviousGalleryImage
import com.cloud.quiethoursnotificationhelper.showUnreadMessages
import com.cloud.quiethoursnotificationhelper.startAiResponseListener
import com.cloud.quiethoursnotificationhelper.startDiscoveryListener
import com.cloud.quiethoursnotificationhelper.startTriggerListenerIfHomeWifi
import com.cloud.quiethoursnotificationhelper.stopAllSyncServices
import com.cloud.quiethoursnotificationhelper.stopVoiceNote
import com.cloud.quiethoursnotificationhelper.syncTodosWithLaptop
import com.cloud.quiethoursnotificationhelper.timeChangeReceiver
import com.cloud.quiethoursnotificationhelper.updateNotification
import com.cloud.quiethoursnotificationhelper.updateSingleSenderNotification
import com.cloud.service.AutoClickAccessibilityService.Companion.closeNots
import com.cloud.showSimpleNotificationExtern
import com.cloud.ERRORINSERT
import com.cloud.ERRORINSERTDATA
import rikka.shizuku.SystemServiceHelper.getSystemService
import java.io.File
import java.util.Calendar
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class QuietHoursNotificationService : Service() {
    private val errorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val checkRunnable: Runnable by lazy { getCheckRunnable(this) }

    private fun reportServiceError(where: String, t: Throwable) {
        Log.e("QuietHoursService", "Unhandled error in $where", t)
        val stack = t.stackTraceToString()
        val trimmed = if (stack.length > 8000) stack.take(8000) + "\n...[truncated]" else stack
        errorScope.launch {
            try {
                ERRORINSERT(
                    ERRORINSERTDATA(
                        service_name = "QuietHoursNotificationService:$where",
                        error_message = trimmed,
                        created_at = Instant.now().toString(),
                        severity = "ERROR"
                    )
                )
            } catch (_: Exception) {
                // Nothing: Fehlerreporting darf selbst keinen Crash auslösen.
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "quiet_hours_channel"
        const val NOTIFICATION_ID = 999999

        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val TELEGRAM_PACKAGES =
            setOf("org.telegram.messenger", "org.telegram.messenger.web")

        fun isSupportedMessenger(packageName: String): Boolean {
            return WHATSAPP_PACKAGES.contains(packageName) || TELEGRAM_PACKAGES.contains(packageName)
        }

        const val ACTION_SHOW_MESSAGES = "com.cloud.ACTION_SHOW_MESSAGES"
        const val ACTION_SCHEDULED_START = "com.cloud.ACTION_QUIET_SCHEDULED_START"
        const val ACTION_SCHEDULED_STOP = "com.cloud.ACTION_QUIET_SCHEDULED_STOP"
        private const val ACTION_OPEN_SETTINGS = "com.cloud.ACTION_OPEN_SETTINGS"

        const val ACTION_MESSAGE_SENT = "com.cloud.ACTION_MESSAGE_SENT"
        const val EXTRA_SENDER = "extra_sender"
        const val CONFIRMATION_CHANNEL_ID = "message_confirmation_channel"

        private lateinit var sharedPreferences: SharedPreferences
        private const val ACTION_OPEN_MUSIC_PLAYER = "com.cloud.ACTION_OPEN_MUSIC_PLAYER"
        const val ACTION_RESTART_MUSIC_PLAYER =
            "com.cloud.ACTION_RESTART_MUSIC_PLAYER"

        const val ACTION_NOTIFICATION_DISMISSED =
            "com.cloud.ACTION_NOTIFICATION_DISMISSED"

        const val ACTION_CHANGE_START = "com.cloud.ACTION_CHANGE_START"
        const val ACTION_CHANGE_END = "com.cloud.ACTION_CHANGE_END"


        const val ACTION_PLAY_VOICE_NOTE = "com.cloud.ACTION_PLAY_VOICE_NOTE"
        const val ACTION_NEXT_VOICE_NOTE = "com.cloud.ACTION_NEXT_VOICE_NOTE"
        const val ACTION_PREV_VOICE_NOTE = "com.cloud.ACTION_PREV_VOICE_NOTE"
        const val ACTION_STOP_VOICE_NOTE = "com.cloud.ACTION_STOP_VOICE_NOTE"
        const val EXTRA_SENDER_FOR_VOICE = "extra_sender_for_voice"

        const val VOICE_NOTE_CHANNEL_ID = "voice_note_player_channel"
        const val ACTION_EXECUTE_COMMAND = "com.cloud.ACTION_EXECUTE_COMMAND"

        const val PREFS_REPLY_DATA = "reply_data_prefs"
        const val KEY_SAVED_SENDER = "saved_sender"
        const val KEY_SAVED_PACKAGE = "saved_package"
        const val KEY_SAVED_RESULT_KEY = "saved_result_key"
        const val KEY_HAS_SAVED_DATA = "has_saved_data"

        val commandHistory = mutableListOf<String>()

        private const val ACTION_SHOW_GALLERY = "com.cloud.ACTION_SHOW_GALLERY"
        const val ACTION_NEXT_GALLERY_IMAGE = "com.cloud.ACTION_NEXT_GALLERY_IMAGE"
        const val ACTION_PREV_GALLERY_IMAGE = "com.cloud.ACTION_PREV_GALLERY_IMAGE"
        const val GALLERY_CHANNEL_ID = "gallery_channel"

        const val SSN_CHANNEL_ID = "show_simple_not_channel"

        const val ACTION_CONFIRM_DELETE_IMAGE =
            "com.cloud.ACTION_CONFIRM_DELETE_IMAGE"
        const val ACTION_DELETE_IMAGE = "com.cloud.ACTION_DELETE_IMAGE"
        const val ACTION_CANCEL_DELETE = "com.cloud.ACTION_CANCEL_DELETE"
        const val EXTRA_IMAGE_INDEX = "extra_image_index"
        const val DELETE_CONFIRMATION_CHANNEL_ID = "delete_confirmation_channel"

        const val ACTION_MARK_PARTS_READ = "com.cloud.ACTION_MARK_PARTS_READ"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val ALARM_REQUEST_CODE = 1001

        const val THRESHOLD_MINUTES = 30
        const val MAX_MESSAGES_PER_CONTACT = 50
        const val MAX_VOICE_NOTE_FILES = 20

        const val ACTION_RESTORE_NOTIFICATION = "com.cloud.ACTION_RESTORE_NOTIFICATION"

        const val ACTION_UPDATE_SINGLE_SENDER = "com.cloud.ACTION_UPDATE_SINGLE_SENDER"
        const val ACTION_CONTENT_INTENT = "com.cloud.ACTION_CONTENT_INTENT"
        const val ACTION_SYNC_LAPTOP = "com.cloud.ACTION_SYNC_LAPTOP"
        const val SHOW_OVERLAY = "com.cloud.SHOW_OVERLAY"

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
            try {
                checkQuietHours(context)
                scheduleNextCheck(context)
            } catch (e: Exception) {
                Log.e("QuietHoursService", "checkRunnable failed", e)
                // Nachts ohne Logcat: zumindest via Error-Backend melden.
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    try {
                        ERRORINSERT(
                            ERRORINSERTDATA(
                                service_name = "QuietHoursNotificationService:checkRunnable",
                                error_message = e.stackTraceToString().take(8000),
                                created_at = Instant.now().toString(),
                                severity = "ERROR"
                            )
                        )
                    } catch (_: Exception) {
                    }
                }
            }
        }

        fun startService(context: Context) {
            val intent = Intent(context, QuietHoursNotificationService::class.java)
            context.startForegroundService(intent)
        }

        fun updateSingleSenderNotification(context: Context, sender: String) {
            val intent = Intent(context, QuietHoursNotificationService::class.java).apply {
                action = ACTION_UPDATE_SINGLE_SENDER
                putExtra("EXTRA_SENDER", sender)
            }
            context.startService(intent)
        }

        fun showtestOverlay(context: Context) {
            val intent = Intent(context, QuietHoursNotificationService::class.java).apply {
                action = SHOW_OVERLAY
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
                nextChange.set(Calendar.HOUR_OF_DAY, quietStart)
                nextChange.set(Calendar.MINUTE, 0)

                if (nextChange.timeInMillis <= now.timeInMillis) {
                    nextChange.add(Calendar.DAY_OF_YEAR, 1)
                }
            } else {
                nextChange.set(Calendar.HOUR_OF_DAY, quietEnd)
                nextChange.set(Calendar.MINUTE, 0)

                if (nextChange.timeInMillis <= now.timeInMillis) {
                    nextChange.add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            return nextChange
        }
    }

    private val prefChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "saved_number" || key == "saved_number_start") {
                handler.removeCallbacks(checkRunnable)

                try {
                    isCurrentlyQuietHours = isQuietHoursNow(this)
                    updateNotification(this)
                } catch (e: Exception) {
                    reportServiceError("prefChangeListener:$key", e)
                }

                handler.post(checkRunnable)
            }
        }

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences("quick_settings_prefs", MODE_PRIVATE)
        createNotificationChannel(this)

        isCurrentlyQuietHours = isQuietHoursNow(this)
        try {
            startForeground(NOTIFICATION_ID, createNotification(isCurrentlyQuietHours, this))
        } catch (e: Exception) {
            reportServiceError("onCreate:startForeground", e)
        }

        try {
            sharedPreferences.registerOnSharedPreferenceChangeListener(prefChangeListener)
        } catch (e: Exception) {
            reportServiceError("onCreate:registerOnSharedPreferenceChangeListener", e)
        }

        handler.post(checkRunnable)
        schedulePeriodicCleanup()
        restoreSyncIfNeeded(this)
        startTriggerListenerIfHomeWifi(this)
        startAiResponseListener(this)
        startDiscoveryListener()
        try {
            registerWifiCallback()
        } catch (e: Exception) {
            reportServiceError("onCreate:registerWifiCallback", e)
        }

        val filter = IntentFilter(ACTION_MESSAGE_SENT)
        try {
            registerReceiver(messageSentReceiver, filter, RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
            reportServiceError("onCreate:registerReceiver messageSentReceiver", e)
        }

        val dismissFilter = IntentFilter(ACTION_NOTIFICATION_DISMISSED)
        try {
            registerReceiver(notificationDismissReceiver, dismissFilter, RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
            reportServiceError("onCreate:registerReceiver notificationDismissReceiver", e)
        }

        val timeChangeFilter = IntentFilter().apply {
            addAction(ACTION_CHANGE_START)
            addAction(ACTION_CHANGE_END)
        }
        try {
            registerReceiver(timeChangeReceiver, timeChangeFilter, RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
            reportServiceError("onCreate:registerReceiver timeChangeReceiver", e)
        }

        val commandFilter = IntentFilter(ACTION_EXECUTE_COMMAND)
        try {
            registerReceiver(commandReceiver, commandFilter, RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
            reportServiceError("onCreate:registerReceiver commandReceiver", e)
        }

        val markReadFilter = IntentFilter(ACTION_MARK_PARTS_READ)
        try {
            registerReceiver(markReadReceiver, markReadFilter, RECEIVER_NOT_EXPORTED)
        } catch (e: Exception) {
            reportServiceError("onCreate:registerReceiver markReadReceiver", e)
        }
    }

    private fun schedulePeriodicCleanup() {
        workerHandler.postDelayed(object : Runnable {
            override fun run() {
                try {
                    cleanupReadMessages()
                    cleanupOldMessages()
                } catch (e: Exception) {
                    reportServiceError("schedulePeriodicCleanup", e)
                } finally {
                    // Egal ob Crash-Fall: nächstes Cleanup wieder planen.
                    workerHandler.postDelayed(this, 6 * 60 * 60 * 1000) // 6h
                }
            }
        }, 6 * 60 * 60 * 1000)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return try {
            when (intent?.action) {
                ACTION_SCHEDULED_STOP -> {
                    stopSelf()
                    START_NOT_STICKY
                }

                ACTION_SCHEDULED_START -> {
                    isCurrentlyQuietHours = isQuietHoursNow(this)
                    startForeground(NOTIFICATION_ID, createNotification(isCurrentlyQuietHours, this))
                    START_STICKY
                }

                ACTION_SHOW_MESSAGES -> {
                    showUnreadMessages(this)
                    START_STICKY
                }

                SHOW_OVERLAY -> {
                    showTestOverlay()
                    START_STICKY
                }

                ACTION_RESTORE_NOTIFICATION -> {
                    val notification = createNotification(isCurrentlyQuietHours, this)
                    startForeground(NOTIFICATION_ID, notification)
                    START_STICKY
                }

                ACTION_UPDATE_SINGLE_SENDER -> {
                    val sender = intent.getStringExtra("EXTRA_SENDER")
                    if (sender != null) updateSingleSenderNotification(sender, this)
                    START_STICKY
                }

                ACTION_CONTENT_INTENT -> {
                    val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
                    try {
                        val cameraId = cameraManager.cameraIdList.firstOrNull()
                        if (cameraId != null) cameraManager.turnOnTorchWithStrengthLevel(cameraId, 1)
                    } catch (e: Exception) {
                        showSimpleNotification(
                            "❌ Taschenlampe",
                            "Helligkeit konnte nicht gesetzt werden: ${e.message}",
                            20.seconds
                        )
                    }
                    START_STICKY
                }

                ACTION_OPEN_SETTINGS -> {
                    openAndroidSettings()
                    START_STICKY
                }

                ACTION_OPEN_MUSIC_PLAYER -> {
                    openMusicPlayer()
                    START_STICKY
                }

                ACTION_RESTART_MUSIC_PLAYER -> {
                    restartMusicPlayer(context = this)
                    START_STICKY
                }

                ACTION_PLAY_VOICE_NOTE -> {
                    val sender = intent.getStringExtra(EXTRA_SENDER_FOR_VOICE)
                    if (sender != null) playLatestVoiceNote(sender, this)
                    START_STICKY
                }

                ACTION_NEXT_VOICE_NOTE -> {
                    playNextVoiceNote(this)
                    START_STICKY
                }

                ACTION_PREV_VOICE_NOTE -> {
                    playPreviousVoiceNote(this)
                    START_STICKY
                }

                ACTION_STOP_VOICE_NOTE -> {
                    stopVoiceNote(this)
                    START_STICKY
                }

                ACTION_SHOW_GALLERY -> {
                    loadGalleryImages(0, this)
                    START_STICKY
                }

                ACTION_NEXT_GALLERY_IMAGE -> {
                    showNextGalleryImage(this)
                    START_STICKY
                }

                ACTION_PREV_GALLERY_IMAGE -> {
                    showPreviousGalleryImage(this)
                    START_STICKY
                }

                ACTION_CONFIRM_DELETE_IMAGE -> {
                    val imageIndex = intent.getIntExtra(EXTRA_IMAGE_INDEX, -1)
                    if (imageIndex >= 0) showDeleteConfirmation(imageIndex, this)
                    START_STICKY
                }

                ACTION_DELETE_IMAGE -> {
                    val imageIndex = intent.getIntExtra(EXTRA_IMAGE_INDEX, -1)
                    if (imageIndex >= 0) deleteGalleryImage(imageIndex, this)
                    START_STICKY
                }

                ACTION_CANCEL_DELETE -> {
                    val notificationManager = getSystemService(NotificationManager::class.java)
                    notificationManager.cancel(DEL_GAL_CONF)
                    START_STICKY
                }

                ACTION_SYNC_LAPTOP -> {
                    closeNots()
                    syncTodosWithLaptop(this@QuietHoursNotificationService)
                    START_STICKY
                }

                else -> {
                    try {
                        startForeground(
                            NOTIFICATION_ID,
                            createNotification(isCurrentlyQuietHours, this)
                        )
                    } catch (e: Exception) {
                        reportServiceError("onStartCommand:else:startForeground", e)
                    }
                    START_STICKY
                }
            }
        } catch (e: Exception) {
            reportServiceError("onStartCommand", e)
            START_STICKY
        }
    }

    private var testOverlayView: ComposeView? = null
    private var testOverlayLifecycle: OverlayLifecycleOwner? = null

    @SuppressLint("SetJavaScriptEnabled")
    private fun showTestOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            showSimpleNotification("Fehler", "Overlay-Berechtigung fehlt!")
            return
        }

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        var currentUrl = "https://www.youtube.com"

        var isDesktopMode = false

        testOverlayLifecycle = OverlayLifecycleOwner().also { it.onCreate(); it.onResume() }

        testOverlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(testOverlayLifecycle)
            setViewTreeSavedStateRegistryOwner(testOverlayLifecycle)
            setViewTreeViewModelStoreOwner(testOverlayLifecycle)
            setContent {
                val webView = remember {
                    WebView(context).apply {
                        webChromeClient = object : WebChromeClient() {
                            private var customView: View? = null
                            private var customViewCallback: CustomViewCallback? = null

                            override fun onShowCustomView(
                                view: View?,
                                callback: CustomViewCallback?
                            ) {
                                (context as? Activity)?.let { activity ->
                                    val decor = activity.window.decorView as? FrameLayout ?: return@let
                                    val toAdd = view ?: return@let
                                    decor.addView(
                                        toAdd,
                                        FrameLayout.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    )
                                    activity.requestedOrientation =
                                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    customView = toAdd
                                    customViewCallback = callback

                                    activity.window.insetsController?.apply {
                                        hide(WindowInsets.Type.systemBars())
                                        systemBarsBehavior =
                                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                    }
                                }
                            }

                            override fun onHideCustomView() {
                                (context as? Activity)?.let { activity ->
                                    val decor = activity.window.decorView as? FrameLayout ?: return@let
                                    customView?.let { decor.removeView(it) }
                                    customView = null
                                    customViewCallback?.onCustomViewHidden()
                                    customViewCallback = null

                                    activity.window.insetsController?.show(
                                        WindowInsets.Type.systemBars()
                                    )
                                    activity.requestedOrientation =
                                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean = false

                            override fun onPageFinished(view: WebView?, url: String?) {
                                if (url != null) currentUrl = url
                            }
                        }

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true

                            allowFileAccess = true
                            allowContentAccess = true

                            loadsImagesAutomatically = true
                            blockNetworkLoads = false

                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                            useWideViewPort = true
                            loadWithOverviewMode = true

                            builtInZoomControls = true
                            displayZoomControls = false
                            setSupportZoom(true)

                            javaScriptCanOpenWindowsAutomatically = true
                            setSupportMultipleWindows(true)

                            cacheMode = WebSettings.LOAD_DEFAULT
                            mediaPlaybackRequiresUserGesture = false

                            userAgentString =
                                "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        }

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                        cookieManager.flush()

                        loadUrl(currentUrl)
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { webView },
                        update = { },
                        modifier = Modifier.fillMaxSize()
                    )

                    Button(
                        onClick = {
                            isDesktopMode = !isDesktopMode

                            webView.settings.apply {
                                if (isDesktopMode) {
                                    userAgentString =
                                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                } else {
                                    userAgentString =
                                        "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                }
                            }

                            webView.loadUrl(currentUrl)
                        },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Laptop,
                            contentDescription = null,
                            tint = Color.White
                        )
                    }

                    IconButton(
                        onClick = {
                            try {
                                testOverlayView?.let { windowManager.removeView(it) }
                            } catch (e: Exception) {
                                reportServiceError("showTestOverlay:removeView", e)
                            }
                            try {
                                testOverlayLifecycle?.onDestroy()
                            } catch (e: Exception) {
                                reportServiceError("showTestOverlay:overlayLifecycle:onDestroy", e)
                            }
                            webView.stopLoading()
                            webView.destroy()
                            testOverlayView = null
                            testOverlayLifecycle = null
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(50))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = Color.White
                        )
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            windowManager.addView(testOverlayView, params)
        } catch (e: Exception) {
            reportServiceError("showTestOverlay:addView", e)
            showSimpleNotification("Fehler", "Overlay konnte nicht gestartet werden")
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        testOverlayView?.let { view ->
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }
            try {
                wm.updateViewLayout(view, params)
            } catch (e: Exception) {
                reportServiceError("onConfigurationChanged:updateViewLayout", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    override fun onDestroy() {
        super.onDestroy()

        handler.removeCallbacksAndMessages(null)

        networkCallback?.let {
            try {
                val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                reportServiceError("onDestroy:unregisterNetworkCallback", e)
            } finally {
                networkCallback = null
            }
        }

        // MediaPlayer
        voiceNotePlayer?.apply {
            try {
                if (isPlaying) stop()
                reset()
                release()
            } catch (e: Exception) {
                reportServiceError("onDestroy:voiceNotePlayerRelease", e)
            }
        }
        voiceNotePlayer = null

        // AudioRecorder
        try {
            audioRecorder?.stopRecording()
        } catch (e: Exception) {
            reportServiceError("onDestroy:audioRecorderStop", e)
        }
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
        try {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        } catch (e: Exception) {
            reportServiceError("onDestroy:unregisterOnSharedPreferenceChangeListener", e)
        }

        val restartIntent = Intent(applicationContext, QuietHoursNotificationService::class.java)
        val pendingIntent = PendingIntent.getService(
            applicationContext, 0, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 1000,
                pendingIntent
            )
        }

        errorScope.cancel()
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
        val notification = NotificationCompat.Builder(this, SSN_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
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
            notificationManager.notify(cms(), notification)

            if (duration > Duration.ZERO) {
                Handler(Looper.getMainLooper()).postDelayed(
                    { notificationManager.cancel(cms()) },
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
        } catch (_: Exception) {
            showSimpleNotification("Fehler", "Musik Player konnte nicht geöffnet werden")
        }
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private fun registerWifiCallback() {
        val connectivityManager =
            getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                try {
                    startTriggerListenerIfHomeWifi(this@QuietHoursNotificationService)
                } catch (e: Exception) {
                    reportServiceError("registerWifiCallback:onAvailable", e)
                }
            }

            override fun onLost(network: Network) {
                try {
                    stopAllSyncServices(this@QuietHoursNotificationService)
                } catch (e: Exception) {
                    reportServiceError("registerWifiCallback:onLost", e)
                }
            }
        }

        try {
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            reportServiceError("registerWifiCallback:registerNetworkCallback", e)
        }
    }
}

class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    fun onCreate() {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    fun onResume() {
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    fun onDestroy() {
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
@Preview
fun OVERLAY() {
    val context = LocalContext.current
    if (!Settings.canDrawOverlays(context)) {
        showSimpleNotificationExtern("Fehler", "Overlay-Berechtigung fehlt!", context = context)
        return
    }

    var testOverlayView: ComposeView? = null
    var testOverlayLifecycle: OverlayLifecycleOwner?

    val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

    var currentUrl = "https://www.youtube.com"

    var isDesktopMode = false

    testOverlayLifecycle = OverlayLifecycleOwner().also { it.onCreate(); it.onResume() }

    testOverlayView = ComposeView(context).apply {
        setViewTreeLifecycleOwner(testOverlayLifecycle)
        setViewTreeSavedStateRegistryOwner(testOverlayLifecycle)
        setViewTreeViewModelStoreOwner(testOverlayLifecycle)
        setContent {
            val webView = remember {
                WebView(context).apply {
                    webChromeClient = object : WebChromeClient() {
                        private var customView: View? = null
                        private var customViewCallback: CustomViewCallback? = null

                        override fun onShowCustomView(
                            view: View?,
                            callback: CustomViewCallback?
                        ) {
                            (context as? Activity)?.let { activity ->
                                val decor = activity.window.decorView as FrameLayout
                                decor.addView(
                                    view,
                                    FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                )
                                activity.requestedOrientation =
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                customView = view
                                customViewCallback = callback

                                activity.window.insetsController?.apply {
                                    hide(WindowInsets.Type.systemBars())
                                    systemBarsBehavior =
                                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                }
                            }
                        }

                        override fun onHideCustomView() {
                            (context as? Activity)?.let { activity ->
                                val decor = activity.window.decorView as FrameLayout
                                decor.removeView(customView)
                                customView = null
                                customViewCallback?.onCustomViewHidden()
                                customViewCallback = null

                                activity.window.insetsController?.show(
                                    WindowInsets.Type.systemBars()
                                )
                                activity.requestedOrientation =
                                    ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                            }
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean = false

                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (url != null) currentUrl = url
                        }
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true

                        allowFileAccess = true
                        allowContentAccess = true

                        loadsImagesAutomatically = true
                        blockNetworkLoads = false

                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                        useWideViewPort = true
                        loadWithOverviewMode = true

                        builtInZoomControls = true
                        displayZoomControls = false
                        setSupportZoom(true)

                        javaScriptCanOpenWindowsAutomatically = true
                        setSupportMultipleWindows(true)

                        cacheMode = WebSettings.LOAD_DEFAULT
                        mediaPlaybackRequiresUserGesture = false

                        userAgentString =
                            "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                    }

                    val cookieManager = CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                    cookieManager.flush()

                    loadUrl(currentUrl)
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { webView },
                    update = { },
                    modifier = Modifier.fillMaxSize()
                )

                Button(
                    onClick = {
                        isDesktopMode = !isDesktopMode

                        webView.settings.apply {
                            if (isDesktopMode) {
                                userAgentString =
                                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                                useWideViewPort = true
                                loadWithOverviewMode = true
                            } else {
                                userAgentString =
                                    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                                useWideViewPort = true
                                loadWithOverviewMode = true
                            }
                        }

                        webView.loadUrl(currentUrl)
                    },
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Laptop,
                        contentDescription = null,
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = {
                        testOverlayView?.let { windowManager.removeView(it) }
                        testOverlayLifecycle?.onDestroy()
                        testOverlayView = null
                        testOverlayLifecycle = null
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(50))
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Schließen",
                        tint = Color.White
                    )
                }
            }
        }
    }

    val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.CENTER
    }

    windowManager.addView(testOverlayView, params)
}