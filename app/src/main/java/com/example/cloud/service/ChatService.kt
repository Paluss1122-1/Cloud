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
import java.text.SimpleDateFormat
import java.time.Clock
import java.util.*

class ChatService : Service() {

    companion object {
        private const val CHANNEL_ID = "chat_service_channel"
        private const val NOTIFICATION_ID = 666666
        private const val ACTION_REPLY = "com.example.cloud.ACTION_CHAT_REPLY"
        private const val KEY_REPLY_TEXT = "key_reply_text"
        private const val KEY_NOTIFICATION_ID = "key_notification_id"
        private const val POLL_INTERVAL_MS = 3000L // Poll alle 3 Sekunden

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

    // Set um bereits gesehene Nachrichten zu tracken
    private val seenMessageIds = mutableSetOf<String>()

    private val replyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_REPLY) {
                val replyText = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_REPLY_TEXT)?.toString()

                val notificationId = intent.getIntExtra(KEY_NOTIFICATION_ID, -1)

                if (replyText != null && context != null) {
                    serviceScope.launch {
                        sendMessage(replyText) // Sendet als INSERT -> Realtime Event auf Website!

                        withContext(Dispatchers.Main) {
                            updateNotificationAfterReply(notificationId, replyText)
                        }
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val filter = IntentFilter(ACTION_REPLY)
        registerReceiver(replyReceiver, filter, RECEIVER_NOT_EXPORTED)

        startForeground(
            NOTIFICATION_ID,
            createServiceNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )

        // Starte Polling
        serviceScope.launch {
            pollForNewMessages()
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
                        notificationManager.notify(NOTIFICATION_ID, notification)
                    } catch (_: Exception) {
                    }
                }, 100)
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

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

     fun createServiceNotification(): Notification {
        val permanentNotificationId = NOTIFICATION_ID

        val replyRemoteInput = RemoteInput.Builder(KEY_REPLY_TEXT)
            .setLabel("Nachricht schreiben...")
            .build()

        val replyIntent = Intent(ACTION_REPLY).apply {
            putExtra(KEY_NOTIFICATION_ID, permanentNotificationId)
            `package` = packageName
        }

        val replyPendingIntent = PendingIntent.getBroadcast(
            this,
            permanentNotificationId,
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
            .setDeleteIntent(deletePendingIntent)
            .build()
    }

    private suspend fun pollForNewMessages() {
        Log.d("ChatService", "Starting message polling...")

        try {
            // Initial: Lade die letzten 10 Nachrichten und markiere sie als "gesehen"
            val initialMessages = supabase.from("messages")
                .select()
                .decodeList<Message>()
                .takeLast(10)

            initialMessages.forEach { msg ->
                msg.id?.let { seenMessageIds.add(it) }
            }

            Log.d("ChatService", "Initial ${seenMessageIds.size} messages marked as seen")

        } catch (e: Exception) {
            Log.e("ChatService", "Error loading initial messages", e)
        }

        // Polling-Loop
        while (true) {
            try {
                // Hole alle Nachrichten
                val messages = supabase.from("messages")
                    .select()
                    .decodeList<Message>()

                // Finde neue Nachrichten (die wir noch nicht gesehen haben)
                val newMessages = messages.filter { msg ->
                    msg.id != null &&
                            !seenMessageIds.contains(msg.id) &&
                            msg.sender_id == friendUserId // Nur vom Freund
                }

                // Zeige Benachrichtigungen für neue Nachrichten
                newMessages.forEach { message ->
                    Log.d("ChatService", "📩 New message: ${message.content}")

                    withContext(Dispatchers.Main) {
                        showMessageNotification(message)
                    }

                    // Markiere als gesehen
                    message.id?.let { seenMessageIds.add(it) }
                }

            } catch (e: Exception) {
                Log.e("ChatService", "Error polling messages", e)
            }

            // Warte bis zum nächsten Poll
            delay(POLL_INTERVAL_MS)
        }
    }

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

    private fun updateNotificationAfterReply(notificationId: Int, replyText: String) {
        try {
            val notification = NotificationCompat.Builder(this, "chat_messages")
                .setSmallIcon(android.R.drawable.ic_menu_send)
                .setContentTitle("✅ Gesendet")
                .setContentText(replyText)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setTimeoutAfter(3000)
                .build()

            val notificationManager = getSystemService(NotificationManager::class.java)

            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(notificationId, notification)
            }

        } catch (e: Exception) {
            Log.e("ChatService", "Error updating notification", e)
        }
    }

    private suspend fun sendMessage(content: String) {
        try {
            val message = Message(
                sender_id = myUserId,
                receiver_id = friendUserId,
                content = content
            )

            // INSERT -> Wird als Realtime Event auf der Website ankommen!
            supabase.from("messages").insert(message)

            Log.d("ChatService", "✅ Message sent (will trigger realtime on website): $content")

        } catch (e: Exception) {
            Log.e("ChatService", "Error sending message", e)
        }
    }

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