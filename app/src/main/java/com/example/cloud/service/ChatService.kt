package com.example.cloud.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.cloud.SupabaseConfig
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.content.edit
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class ChatService : Service() {

    companion object {
        private const val CHANNEL_ID = "chat_service_channel"
        private const val ACTION_REPLY = "com.example.cloud.ACTION_CHAT_REPLY"
        private const val ACTION_SHOW_HISTORY = "com.example.cloud.ACTION_SHOW_HISTORY"
        private const val KEY_REPLY_TEXT = "key_reply_text"
        private const val KEY_NOTIFICATION_ID = "key_notification_id"
        private const val POLL_INTERVAL_MS = 3000L
        private const val SERVICE_NOTIFICATION_ID = 1

        private const val PREFS_NAME = "ChatServicePrefs"
        private const val KEY_SEEN_MESSAGES = "seen_message_ids"

        fun startService(context: Context) {
            val intent = Intent(context, ChatService::class.java)
            context.startForegroundService(intent)
        }
    }

    @Serializable
    data class Message(
        val id: String? = null,
        val sender_id: String,
        val receiver_id: String,
        val content: String,
        val created_at: String? = null
    )

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val supabase = SupabaseConfig.client
    private val myUserId = "you"
    private val friendUserId = "friend"

    private lateinit var sharedPreferences: SharedPreferences
    private val seenMessageIds = mutableSetOf<String>()
    private val messageHistory = mutableListOf<Message>() // Letzte 5 Nachrichten

    private val replyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_REPLY -> {
                    val replyText = RemoteInput.getResultsFromIntent(intent)
                        ?.getCharSequence(KEY_REPLY_TEXT)?.toString()
                    val notificationId = intent.getIntExtra(KEY_NOTIFICATION_ID, -1)

                    if (replyText != null && context != null) {
                        serviceScope.launch {
                            sendMessage(replyText)
                            withContext(Dispatchers.Main) {
                                if (notificationId != SERVICE_NOTIFICATION_ID && notificationId != -1) {
                                    val notificationManager = getSystemService(NotificationManager::class.java)
                                    notificationManager.cancel(notificationId)
                                    Log.d("ChatService", "🗑️ Notification $notificationId removed after reply")
                                }
                                updateServiceNotification()
                            }
                        }
                    }
                }
                ACTION_SHOW_HISTORY -> {
                    Log.d("ChatService", "History angefordert: ${messageHistory.size} Nachrichten")
                    serviceScope.launch {
                        withContext(Dispatchers.Main) {
                            showHistoryNotification()
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        loadSeenMessageIds()

        createNotificationChannel()

        val filter = IntentFilter().apply {
            addAction(ACTION_REPLY)
            addAction(ACTION_SHOW_HISTORY)
        }
        registerReceiver(replyReceiver, filter, RECEIVER_NOT_EXPORTED)

        startForeground(
            SERVICE_NOTIFICATION_ID,
            createServiceNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        serviceScope.launch {
            setupRealtimeListener()
        }

        Log.d("ChatService", "Service created - Polling every ${POLL_INTERVAL_MS}ms")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "ACTION_NOTIFICATION_DELETED" -> {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        val notification = createServiceNotification()
                        val notificationManager = getSystemService(NotificationManager::class.java)
                        notificationManager.notify(SERVICE_NOTIFICATION_ID, notification)
                    } catch (_: Exception) {
                    }
                }, 100)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ============ SharedPreferences Funktionen ============

    private fun loadSeenMessageIds() {
        val json = sharedPreferences.getString(KEY_SEEN_MESSAGES, "[]")
        try {
            val ids = Json.decodeFromString<List<String>>(json ?: "[]")
            seenMessageIds.addAll(ids)
            Log.d("ChatService", "✅ Loaded ${seenMessageIds.size} seen messages from prefs")
        } catch (e: Exception) {
            Log.e("ChatService", "Error loading seen messages", e)
        }
    }

    private fun saveSeenMessageIds() {
        try {
            val json = Json.encodeToString(seenMessageIds.toList())
            sharedPreferences.edit { putString(KEY_SEEN_MESSAGES, json) }
        } catch (e: Exception) {
            Log.e("ChatService", "Error saving seen messages", e)
        }
    }

    // ============ Notification Channel ============

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Chat Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Hintergrund-Service für Chat-Nachrichten"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(null, null)
        }

        val messageChannel = NotificationChannel(
            "chat_messages",
            "Chat Nachrichten",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Benachrichtigungen für neue Chat-Nachrichten"
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            enableVibration(true)
            enableLights(true)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(serviceChannel)
        notificationManager.createNotificationChannel(messageChannel)
    }

    // ============ Service Notification ============

    fun createServiceNotification(): Notification {
        val replyRemoteInput = RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel("Nachricht schreiben...")
            .build()

        val replyIntent = Intent(ACTION_REPLY).apply {
            putExtra(KEY_NOTIFICATION_ID, SERVICE_NOTIFICATION_ID)
            `package` = packageName
        }

        val replyPendingIntent = PendingIntent.getBroadcast(
            this,
            SERVICE_NOTIFICATION_ID,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Schreiben",
            replyPendingIntent
        )
            .addRemoteInput(replyRemoteInput)
            .setShowsUserInterface(false)
            .build()

        // ========== HISTORY ACTION ==========
        val historyIntent = Intent(ACTION_SHOW_HISTORY).apply {
            `package` = packageName
        }

        val historyPendingIntent = PendingIntent.getBroadcast(
            this,
            9999,
            historyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val historyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_view,
            "Letzte 5",
            historyPendingIntent
        ).build()

        // ========== DELETE INTENT ==========
        val deleteIntent = Intent(this, ChatService::class.java).apply {
            action = "ACTION_NOTIFICATION_DELETED"
        }

        val deletePendingIntent = PendingIntent.getService(
            this,
            999,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle("💬 Chat Service")
            .setContentText("Tippe auf 'Schreiben' um eine Nachricht zu senden")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .addAction(replyAction)
            .addAction(historyAction)
            .setDeleteIntent(deletePendingIntent)
            .build()
    }

    private fun updateServiceNotification() {
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)
            val notification = createServiceNotification()
            notificationManager.notify(SERVICE_NOTIFICATION_ID, notification)
            Log.d("ChatService", "Service notification updated")
        } catch (e: Exception) {
            Log.e("ChatService", "Error updating service notification", e)
        }
    }

    // ============ Message History Notification ============

    private fun showHistoryNotification() {
        try {
            val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

            val sortedHistory = messageHistory
                .filter { it.sender_id == friendUserId }
                .sortedBy { msg ->
                    try {
                        isoFormat.parse(msg.created_at?.substring(0, 19) ?: "")?.time ?: 0L
                    } catch (_: Exception) {
                        0L
                    }
                }
                .takeLast(5)

            val historyText = sortedHistory.joinToString("\n\n") { msg ->
                val dateTime = try {
                    val date = isoFormat.parse(msg.created_at?.substring(0, 19) ?: "")
                    val dateFormatter = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    if (date != null) {
                        dateFormatter.format(date)
                    } else {
                        "??:??"
                    }
                } catch (_: Exception) {
                    "??:??"
                }
                "[$dateTime] ${msg.sender_id}:\n${msg.content}"
            }

            val notification = NotificationCompat.Builder(this, "chat_messages")
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("📜 Letzte Nachrichten")
                .setContentText("${messageHistory.size} Nachrichten")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(historyText.ifEmpty { "Keine Nachrichten" })
                )
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .build()

            val notificationManager = getSystemService(NotificationManager::class.java)

            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(2000, notification)
                Log.d("ChatService", "✅ History notification shown")
            }

        } catch (e: Exception) {
            Log.e("ChatService", "Error showing history notification", e)
        }
    }

    // ============ Message Polling ============

    private suspend fun setupRealtimeListener() {
        try {
            Log.d("ChatService", "Setting up Realtime listener...")

            // Initiale Nachrichten laden
            val initialMessages = supabase.from("messages")
                .select()
                .decodeList<Message>()
                .takeLast(10)

            initialMessages.forEach { msg ->
                msg.id?.let { seenMessageIds.add(it) }
            }
            messageHistory.addAll(initialMessages.takeLast(5))
            saveSeenMessageIds()

            // Realtime Channel erstellen
            val channel = supabase.channel("chat:messages")

            // INSERT Listener für neue Nachrichten
            val insertFlow = channel
                .postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = "messages"
                }

            insertFlow.onEach { change ->
                val message = Json.decodeFromString<Message>(change.record.toString())

                // Nur Nachrichten vom Friend verarbeiten
                if (message.sender_id == friendUserId &&
                    message.id != null &&
                    !seenMessageIds.contains(message.id)) {

                    Log.d("ChatService", "📩 Realtime message: ${message.content}")

                    withContext(Dispatchers.Main) {
                        showMessageNotification(message)
                    }

                    seenMessageIds.add(message.id)
                    messageHistory.add(message)
                    if (messageHistory.size > 5) {
                        messageHistory.removeAt(0)
                    }
                    saveSeenMessageIds()
                }
            }.launchIn(serviceScope)

            channel.subscribe(blockUntilSubscribed = true)
            Log.i("ChatService", "✅ Realtime channel subscribed")

        } catch (e: Exception) {
            Log.e("ChatService", "❌ Realtime setup failed, falling back to polling", e)
        }
    }

    // ============ New Message Notification ============

    private fun showMessageNotification(message: Message) {
        try {
            Log.d("ChatService", "🔔 Showing notification: ${message.content}")

            val notificationId = Date().time.toInt()

            val replyRemoteInput = RemoteInput.Builder(KEY_REPLY_TEXT)
                .setLabel("Antwort")
                .build()

            val replyIntent = Intent(ACTION_REPLY).apply {
                putExtra(KEY_NOTIFICATION_ID, notificationId)
                `package` = packageName
            }

            val replyPendingIntent = PendingIntent.getBroadcast(
                this,
                notificationId,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val replyAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_send,
                "Antworten",
                replyPendingIntent
            )
                .addRemoteInput(replyRemoteInput)
                .setShowsUserInterface(false)
                .build()

            val timeText = SimpleDateFormat("HH:mm", Locale.getDefault())
                .format(Date())

            val notification = NotificationCompat.Builder(this, "chat_messages")
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentTitle("💬 ${message.sender_id}")
                .setContentText(message.content)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("${message.content}\n\n$timeText")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .addAction(replyAction)
                .build()

            val notificationManager = getSystemService(NotificationManager::class.java)

            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(notificationId, notification)
                Log.d("ChatService", "✅ Notification shown")
            } else {
                Log.e("ChatService", "❌ Missing POST_NOTIFICATIONS permission")
            }

        } catch (e: Exception) {
            Log.e("ChatService", "Error showing notification", e)
        }
    }

    // ============ Send Message ============

    private suspend fun sendMessage(content: String) {
        try {
            val message = Message(
                sender_id = myUserId,
                receiver_id = friendUserId,
                content = content
            )

            supabase.from("messages").insert(message)

            Log.d("ChatService", "✅ Message sent: $content")

        } catch (e: Exception) {
            Log.e("ChatService", "Error sending message", e)
        }
    }

    // ============ Lifecycle ============

    override fun onDestroy() {
        super.onDestroy()

        try {
            unregisterReceiver(replyReceiver)
        } catch (e: Exception) {
            Log.e("ChatService", "Error unregistering receiver", e)
        }

        serviceScope.cancel()
        Log.d("ChatService", "Service destroyed")
    }
}