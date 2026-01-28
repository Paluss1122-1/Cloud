package com.example.cloud.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.cloud.database.WhatsAppMessage
import com.example.cloud.database.WhatsAppMessageRepository
import com.example.cloud.objects.NotificationRepository
import kotlinx.coroutines.*
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat

class WhatsAppNotificationListener : NotificationListenerService() {

    companion object {
        private val repository = WhatsAppMessageRepository()
        val replyActions = mutableMapOf<String, ReplyData>()
        val messagesByContact = mutableMapOf<String, MutableList<ChatMessage>>()

        // Unterstützte Apps
        private val WHATSAPP_PACKAGES = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val TELEGRAM_PACKAGES = setOf("org.telegram.messenger", "org.telegram.messenger.web")

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

        fun getMessages(): List<WhatsAppMessage> {
            return repository.getAllFiltered()
        }

        fun getMessagesBySender(sender: String): List<WhatsAppMessage> {
            return repository.getMessagesBySender(sender)
        }

        fun sendReply(sender: String, replyText: String, context: Context): Boolean {
            return synchronized(replyActions) {
                val replyData = replyActions[sender]
                if (replyData == null) {
                    Log.w("MessageListener", "No reply action found for $sender")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Antwort nicht mehr verfügbar", Toast.LENGTH_LONG)
                            .show()
                    }
                    return false
                }

                try {
                    val intent = Intent().apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                    val bundle = android.os.Bundle().apply {
                        putCharSequence(replyData.remoteInput.resultKey, replyText)
                    }

                    RemoteInput.addResultsToIntent(
                        arrayOf(replyData.remoteInput),
                        intent,
                        bundle
                    )

                    replyData.pendingIntent.send(context, 0, intent)

                    // Eigene Nachricht zur Liste hinzufügen
                    messagesByContact.getOrPut(sender) { mutableListOf() }.add(
                        ChatMessage(replyText, isOwnMessage = true)
                    )

                    Log.d("MessageListener", "Reply sent successfully to $sender: $replyText")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Nachricht gesendet ✓", Toast.LENGTH_SHORT).show()
                    }

                    // Benachrichtigung über erfolgreiches Senden
                    val notificationManager = context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

                    val successNotification = NotificationCompat.Builder(context, "quiet_hours_channel")
                        .setSmallIcon(android.R.drawable.stat_notify_chat)
                        .setContentTitle("✓ Nachricht gesendet")
                        .setContentText("An $sender: $replyText")
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setAutoCancel(true)
                        .setTimeoutAfter(3000)
                        .build()

                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationManager.notify(30000 + sender.hashCode(), successNotification)
                    }

                    true
                } catch (e: Exception) {
                    Log.e("MessageListener", "Error sending reply", e)
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Fehler beim Senden: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    false
                }
            }
        }

        private fun isSupportedApp(packageName: String): Boolean {
            return WHATSAPP_PACKAGES.contains(packageName) || TELEGRAM_PACKAGES.contains(packageName)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        super.onNotificationPosted(sbn)

        // Prüfen ob es WhatsApp oder Telegram ist
        if (!isSupportedApp(sbn.packageName)) {
            return
        }

        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString(android.app.Notification.EXTRA_TITLE) ?: ""
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: ""

        // System-Nachrichten filtern
        if (title.contains("Backup", ignoreCase = true) ||
            title.contains("Du", ignoreCase = true) ||
            title.contains("WhatsApp", ignoreCase = true) ||
            title.contains("Telegram", ignoreCase = true)) {
            return
        }

        // Duplikate vermeiden
        val existingMessages = messagesByContact[title] ?: mutableListOf()
        val lastMessage = existingMessages.lastOrNull()

        if (lastMessage != null &&
            !lastMessage.isOwnMessage &&
            lastMessage.text == text &&
            (System.currentTimeMillis() - lastMessage.timestamp) < 5000) {
            Log.d("MessageListener", "Duplicate message detected from $title, ignoring")
            return
        }

        Toast.makeText(this, title, Toast.LENGTH_SHORT).show()

        // Reply-Action extrahieren
        notification.actions?.forEach { action ->
            action.remoteInputs?.firstOrNull()?.let { systemRemoteInput ->
                val remoteInput = RemoteInput.Builder(systemRemoteInput.resultKey)
                    .setLabel(systemRemoteInput.label)
                    .setChoices(systemRemoteInput.choices)
                    .setAllowFreeFormInput(systemRemoteInput.allowFreeFormInput)
                    .build()

                replyActions[title] = ReplyData(
                    pendingIntent = action.actionIntent,
                    remoteInput = remoteInput,
                    originalResultKey = systemRemoteInput.resultKey,
                    timestamp = System.currentTimeMillis()
                )
                Log.d("MessageListener", "Saved reply action for $title with originalResultKey: ${systemRemoteInput.resultKey}")
            }
        }

        // Empfangene Nachricht zur Liste hinzufügen
        messagesByContact.getOrPut(title) { mutableListOf() }.add(
            ChatMessage(text, isOwnMessage = false)
        )

        // In Datenbank speichern
        CoroutineScope(Dispatchers.IO).launch {
            val exists = repository.getAll().any {
                it.sender == title && it.text == text
            }
            if (!exists && !title.contains("Du")) {
                repository.insert(
                    WhatsAppMessage(
                        sender = title,
                        text = text,
                        timestamp = System.currentTimeMillis()
                    )
                )
                val broadcastIntent = Intent("WHATSAPP_MESSAGE_RECEIVED").apply {
                    setPackage(applicationContext.packageName)
                }
                sendBroadcast(broadcastIntent)
            }
        }

        // Uhrzeit prüfen (Quiet Hours)
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val prefs = getSharedPreferences("quick_settings_prefs", MODE_PRIVATE)
        val quietEnd = prefs.getString("saved_number", null)?.toIntOrNull() ?: 21
        val quietStart = prefs.getString("saved_number_start", null)?.toIntOrNull() ?: 7
        if ((hour >= quietStart && hour < quietEnd)) {
            return
        }

        QuietHoursNotificationService.updateSingleSenderNotification(this, title)

        // Notification Channel erstellen
        val channelId = "whatsapp_listener_channel"
        val notificationManager = getSystemService(NotificationManager::class.java)
        if (notificationManager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                "Messenger Listener",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Notification mit Reply-Action erstellen
        val replyData = replyActions[title]
        if (replyData != null) {
            val action = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_send,
                "Antworten",
                replyData.pendingIntent
            )
                .addRemoteInput(replyData.remoteInput)
                .setShowsUserInterface(false)
                .build()

            // Nachrichten für diesen Kontakt holen
            val messages = messagesByContact[title] ?: mutableListOf()

            // MessagingStyle für Chat-Darstellung
            val messagingStyle = NotificationCompat.MessagingStyle("Du")
                .setConversationTitle(title)

            // Nur die letzten 5 Nachrichten anzeigen
            val recentMessages = messages.takeLast(5)

            recentMessages.forEach { msg ->
                messagingStyle.addMessage(
                    NotificationCompat.MessagingStyle.Message(
                        msg.text,
                        msg.timestamp,
                        if (msg.isOwnMessage) "Du" else title
                    )
                )
            }

            val newNotification = NotificationCompat.Builder(this, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setStyle(messagingStyle)
                .addAction(action)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()

            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(title.hashCode(), newNotification)
            } else {
                Log.w("MessageListener", "POST_NOTIFICATIONS permission not granted")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (isSupportedApp(sbn.packageName)) {
            val title = sbn.notification.extras.getString(android.app.Notification.EXTRA_TITLE)
            title?.let {
                replyActions.remove(it)
                messagesByContact.remove(it)
                Log.d("MessageListener", "Removed reply action for $title")
            }
        }
        super.onNotificationRemoved(sbn)
        NotificationRepository.removeNotification(sbn)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationRepository.clear()
    }
}