package com.cloud.service

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.DeadObjectException
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.cloud.Config.BLOCKED_MESSAGES
import com.cloud.Config.NOTIFICATION_PORT
import com.cloud.database.WhatsAppMessage
import com.cloud.database.WhatsAppMessageRepository
import com.cloud.objects.NotificationRepository
import com.cloud.quiethoursnotificationhelper.isLaptopConnected
import com.cloud.quiethoursnotificationhelper.laptopIp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

class WhatsAppNotificationListener : NotificationListenerService() {

    @Volatile
    private var listenerConnected = false

    private lateinit var repository: WhatsAppMessageRepository

    val replyActions = java.util.concurrent.ConcurrentHashMap<String, ReplyData>()
    val messagesByContact = java.util.concurrent.ConcurrentHashMap<String, MutableList<ChatMessage>>()

    private val serviceJob = SupervisorJob()
    private val forwardScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private var pendingForwardJob: Job? = null
    private var lastForwardTime = 0L

    companion object {
        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val TELEGRAM_PACKAGES = setOf("org.telegram.messenger", "org.telegram.messenger.web")

        private const val FORWARD_DEBOUNCE_MS = 500L
        private const val PREFS_BLOCKED = "blocked_notifications_prefs"
        private val BLOCKED_SENDERS = setOf("N", "Nico", "E")

        private var instance: WeakReference<WhatsAppNotificationListener>? = null

        val messagesByContact get() = instance?.get()?.messagesByContact ?: java.util.concurrent.ConcurrentHashMap()
        val replyActions get() = instance?.get()?.replyActions ?: java.util.concurrent.ConcurrentHashMap()

        fun forwardNotificationsToLaptop1() {
            val svc = instance?.get() ?: return
            if (!svc.listenerConnected) return
            try {
                svc.activeNotifications?.let {
                    svc.forwardNotificationsToLaptop(it, svc.packageManager)
                }
            } catch (se: SecurityException) {
                Log.w("NotifForwarder", "getActiveNotifications not allowed: ${se.message}")
            }
        }

        fun keyFor(packageName: String, title: String): String = "$packageName|$title"

        fun isSupportedApp(packageName: String): Boolean {
            return WHATSAPP_PACKAGES.contains(packageName) || TELEGRAM_PACKAGES.contains(packageName)
        }

        data class ChatMessage(
            val text: String,
            val timestamp: Long = System.currentTimeMillis(),
            val isOwnMessage: Boolean,
            val imageUri: Uri? = null
        )

        data class ReplyData(
            val pendingIntent: android.app.PendingIntent,
            val remoteInput: RemoteInput,
            val originalResultKey: String,
            val timestamp: Long = System.currentTimeMillis()
        )
    }

    // Instanz-Methode statt Companion-Methode — hat Zugriff auf forwardScope
    private fun forwardNotificationsToLaptop(
        notifications: Array<StatusBarNotification>,
        packageManager: PackageManager
    ) {
        if (!isLaptopConnected) return
        pendingForwardJob?.cancel()
        pendingForwardJob = forwardScope.launch {
            val now = System.currentTimeMillis()
            val timeSinceLast = now - lastForwardTime
            if (timeSinceLast < FORWARD_DEBOUNCE_MS) {
                delay(FORWARD_DEBOUNCE_MS - timeSinceLast)
            }
            lastForwardTime = System.currentTimeMillis()

            val jsonArray = org.json.JSONArray()
            notifications
                .filter { it.packageName != "com.example.cloud" && it.packageName != "com.android.systemui" }
                .sortedByDescending { it.postTime }
                .forEach { sbn ->
                    val extras = sbn.notification.extras
                    val title = extras.getCharSequence("android.title")?.toString() ?: ""
                    val text = extras.getCharSequence("android.text")?.toString() ?: ""
                    if (title.isBlank() && text.isBlank()) return@forEach

                    val appName = try {
                        packageManager.getApplicationLabel(
                            packageManager.getApplicationInfo(sbn.packageName, PackageManager.GET_META_DATA)
                        ).toString()
                    } catch (_: Exception) {
                        sbn.packageName
                    }

                    jsonArray.put(org.json.JSONObject().apply {
                        put("app", appName)
                        put("title", title)
                        put("text", text)
                        put("time", sbn.postTime)
                    })
                }

            if (jsonArray.length() == 0) return@launch

            val targetIp = laptopIp.ifEmpty { null } ?: return@launch
            try {
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(targetIp, NOTIFICATION_PORT), 3000)
                    socket.getOutputStream().bufferedWriter().use { writer ->
                        writer.write(jsonArray.toString())
                        writer.newLine()
                        writer.flush()
                    }
                }
            } catch (e: Exception) {
                Log.w("NotifForwarder", "❌ $targetIp: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        repository = WhatsAppMessageRepository()
    }

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        try {
            super.onNotificationPosted(sbn)

            if (sbn.packageName != "com.example.cloud" && sbn.packageName != "com.android.systemui") {
                if (listenerConnected) {
                    try {
                        activeNotifications?.let { forwardNotificationsToLaptop(it, packageManager) }
                    } catch (se: SecurityException) {
                        Log.w("MessageListener", "getActiveNotifications not allowed yet: ${se.message}")
                    }
                }
            }

            if (!isSupportedApp(sbn.packageName)) return

            val notification = sbn.notification
            val extras = notification.extras
            val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
            val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""

            if (handleBlockedSender(sbn, title, text)) return

            if (title.contains("Backup", ignoreCase = true) ||
                title.contains("Du", ignoreCase = true) ||
                title.contains("WhatsApp", ignoreCase = true) ||
                title.contains("Telegram", ignoreCase = true)
            ) return

            val key = keyFor(sbn.packageName, title)
            val existingMessages = messagesByContact[key] ?: mutableListOf()
            val lastMessage = existingMessages.lastOrNull()

            if (lastMessage != null &&
                !lastMessage.isOwnMessage &&
                lastMessage.text == text &&
                (System.currentTimeMillis() - lastMessage.timestamp) < 5000
            ) {
                Log.d("MessageListener", "Duplicate message detected from $title, ignoring")
                return
            }

            Log.d("MessageListener", "Received message from $title")

            // Alte replyActions bereinigen (älter als 24h)
            val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000
            replyActions.entries.removeAll { it.value.timestamp < cutoff }

            notification.actions?.forEach { action ->
                action.remoteInputs?.firstOrNull()?.let { systemRemoteInput ->
                    val remoteInput = RemoteInput.Builder(systemRemoteInput.resultKey)
                        .setLabel(systemRemoteInput.label)
                        .setChoices(systemRemoteInput.choices)
                        .setAllowFreeFormInput(systemRemoteInput.allowFreeFormInput)
                        .build()

                    replyActions[key] = ReplyData(
                        pendingIntent = action.actionIntent,
                        remoteInput = remoteInput,
                        originalResultKey = systemRemoteInput.resultKey,
                        timestamp = System.currentTimeMillis()
                    )
                    Log.d("MessageListener", "Saved reply action for $title with key: ${systemRemoteInput.resultKey}")
                }
            }

            messagesByContact.getOrPut(key) { mutableListOf() }.add(
                ChatMessage(text, isOwnMessage = false)
            )

            forwardScope.launch {
                val exists = repository.getAll().any { it.sender == title && it.text == text }
                if (!exists && !title.contains("Du")) {
                    repository.insert(
                        WhatsAppMessage(sender = title, text = text, timestamp = System.currentTimeMillis())
                    )
                    val broadcastIntent = Intent("WHATSAPP_MESSAGE_RECEIVED").apply {
                        setPackage(applicationContext.packageName)
                    }
                    sendBroadcast(broadcastIntent)
                }
            }

            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val prefs = getSharedPreferences("quick_settings_prefs", MODE_PRIVATE)
            val quietEnd = prefs.getString("saved_number", null)?.toIntOrNull() ?: 21
            val quietStart = prefs.getString("saved_number_start", null)?.toIntOrNull() ?: 7

            // Fix: außerhalb der aktiven Stunden → zurückhalten
            if (hour !in quietStart..<quietEnd) return

            QuietHoursNotificationService.updateSingleSenderNotification(this, title)
        } catch (_: DeadObjectException) {
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        try {
            super.onNotificationRemoved(sbn)

            if (sbn.packageName != "com.example.cloud" &&
                sbn.packageName != "com.android.systemui" &&
                sbn.packageName != "com.google.android.gms.supervision" &&
                sbn.packageName != "com.spotify.music"
            ) {
                if (listenerConnected) {
                    try {
                        activeNotifications?.let { forwardNotificationsToLaptop(it, packageManager) }
                    } catch (se: SecurityException) {
                        Log.w("MessageListener", "getActiveNotifications not allowed yet: ${se.message}")
                    }
                }
            }

            if (isSupportedApp(sbn.packageName)) {
                val title = sbn.notification.extras.getString(android.app.Notification.EXTRA_TITLE)
                title?.let {
                    val key = keyFor(sbn.packageName, it)
                    replyActions.remove(key)
                    messagesByContact.remove(key)
                    Log.d("MessageListener", "Removed reply action for $title")
                }
            }

            NotificationRepository.removeNotification(sbn)
        } catch (_: DeadObjectException) {
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        listenerConnected = true
        instance = WeakReference(this)
        scheduleBlockedNotificationsAlarm()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        listenerConnected = false
        instance = null
        replyActions.clear()
        messagesByContact.clear()
        NotificationRepository.clear()
    }

    private fun handleBlockedSender(sbn: StatusBarNotification, title: String, text: String): Boolean {
        val normalizedTitle = title.trim()
        if (BLOCKED_SENDERS.none { it.equals(normalizedTitle, ignoreCase = true) }) return false

        val cal = java.util.Calendar.getInstance()
        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val dow = cal.get(java.util.Calendar.DAY_OF_WEEK)

        val isWeekday = dow in java.util.Calendar.MONDAY..java.util.Calendar.FRIDAY
        val isBlockHours = hour in 7..13
        if (!isWeekday || !isBlockHours) return false

        val prefs = getSharedPreferences(PREFS_BLOCKED, MODE_PRIVATE)
        val existingKeys = prefs.getStringSet("all_blocked_keys", mutableSetOf()) ?: mutableSetOf()
        if (existingKeys.size >= 50) return true

        val timestamp = System.currentTimeMillis()
        val entryKey = "blocked_${timestamp}_${title.replace(" ", "_")}"

        prefs.edit().apply {
            putString("${entryKey}_sender", title)
            putString("${entryKey}_text", text)
            putLong("${entryKey}_timestamp", timestamp)
            putString("${entryKey}_package", sbn.packageName)
            putStringSet("all_blocked_keys", existingKeys.toMutableSet().also { it.add(entryKey) })
            apply()
        }

        try {
            cancelNotification(sbn.key)
            Log.d("BlockedSender", "Cancelled notification for $title (key=${sbn.key})")
        } catch (e: Exception) {
            Log.w("BlockedSender", "Could not cancel notification: ${e.message}")
        }

        return true
    }

    private fun scheduleBlockedNotificationsAlarm() {
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 14)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(java.util.Calendar.DAY_OF_YEAR, 1)
            }
        }

        val intent = Intent(this, BlockedNotificationReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
        if (alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.RTC_WAKEUP,
                cal.timeInMillis,
                pendingIntent
            )
        }
        Log.d("BlockedSender", "Alarm gesetzt für 14:00 Uhr (${cal.time})")
    }
}

class BlockedNotificationReceiver : android.content.BroadcastReceiver() {

    override fun onReceive(context: android.content.Context, intent: Intent) {
        val prefs = context.getSharedPreferences(
            "blocked_notifications_prefs",
            android.content.Context.MODE_PRIVATE
        )
        val keys = prefs.getStringSet("all_blocked_keys", emptySet()) ?: emptySet()
        if (keys.isEmpty()) return

        val notificationManager =
            context.getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager

        val channel = android.app.NotificationChannel(
            "blocked_summary_channel",
            "Zurückgehaltene Nachrichten",
            android.app.NotificationManager.IMPORTANCE_HIGH
        )
        notificationManager.createNotificationChannel(channel)

        keys.forEachIndexed { index, key ->
            val sender = prefs.getString("${key}_sender", null) ?: return@forEachIndexed
            val text = prefs.getString("${key}_text", null) ?: return@forEachIndexed
            val timestamp = prefs.getLong("${key}_timestamp", 0L)

            val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(timestamp))

            val notification = NotificationCompat.Builder(context, "blocked_summary_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(sender)
                .setContentText(text)
                .setSubText("Erhalten um $timeStr (zurückgehalten)")
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            notificationManager.notify(BLOCKED_MESSAGES + index, notification)
        }

        prefs.edit().apply {
            keys.forEach { key ->
                remove("${key}_sender")
                remove("${key}_text")
                remove("${key}_timestamp")
                remove("${key}_package")
            }
            remove("all_blocked_keys")
            apply()
        }

        rescheduleAlarm(context)
    }

    private fun rescheduleAlarm(context: android.content.Context) {
        val cal = java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.DAY_OF_YEAR, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 14)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }

        val intent = Intent(context, BlockedNotificationReceiver::class.java)
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            context, 0, intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager =
            context.getSystemService(android.content.Context.ALARM_SERVICE) as android.app.AlarmManager
        if (!alarmManager.canScheduleExactAlarms()) return
        alarmManager.setExactAndAllowWhileIdle(
            android.app.AlarmManager.RTC_WAKEUP,
            cal.timeInMillis,
            pendingIntent
        )
    }
}