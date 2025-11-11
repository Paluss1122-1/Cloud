package com.example.cloud.service

import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import com.example.cloud.database.WhatsAppMessage
import com.example.cloud.database.WhatsAppMessageRepository
import com.example.cloud.objects.NotificationRepository
import kotlinx.coroutines.*

class WhatsAppNotificationListener : NotificationListenerService() {

    companion object {
        private val repository = WhatsAppMessageRepository() // hierhin verschoben ✅
        private val replyActions = mutableMapOf<String, ReplyData>()

        data class ReplyData(
            val pendingIntent: android.app.PendingIntent,
            val remoteInput: RemoteInput,
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
                    Log.w("WhatsAppListener", "No reply action found for $sender")
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
                    RemoteInput.addResultsToIntent(arrayOf(replyData.remoteInput), intent, bundle)

                    replyData.pendingIntent.send(context, 0, intent)
                    Log.d("WhatsAppListener", "Reply sent successfully")
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, "Nachricht gesendet ✓", Toast.LENGTH_SHORT).show()
                    }
                    true
                } catch (e: Exception) {
                    Log.e("WhatsAppListener", "Error sending reply", e)
                    false
                }
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {

        val notification = sbn.notification
        val extras = notification.extras
        val sender = extras.getString(android.app.Notification.EXTRA_TITLE) ?: return
        val messageText =
            extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString() ?: return

        super.onNotificationPosted(sbn)

        // 🔹 1. Speichere ALLE Benachrichtigungen für den Verlauf
        NotificationRepository.addNotification(sbn)

        if (sbn.packageName == "com.whatsapp" || sbn.packageName == "com.whatsapp.w4b") {
            notification.actions?.forEach { action ->
                action.remoteInputs?.firstOrNull()?.let { remoteInput ->
                    replyActions[sender] = ReplyData(
                        pendingIntent = action.actionIntent,
                        remoteInput = remoteInput,
                        timestamp = System.currentTimeMillis()
                    )

                    CoroutineScope(Dispatchers.IO).launch {
                        val exists = repository.getAll().any {
                            it.sender == sender && it.text == messageText
                        }
                        if (!exists) {
                            repository.insert(
                                WhatsAppMessage(
                                    sender = sender,
                                    text = messageText,
                                    timestamp = System.currentTimeMillis()
                                )
                            )

                            // Broadcast senden für UI-Update
                            val broadcastIntent = Intent("WHATSAPP_MESSAGE_RECEIVED").apply {
                                setPackage(applicationContext.packageName)
                            }
                            sendBroadcast(broadcastIntent)
                        }
                    }
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == "com.whatsapp") {
            val sender = sbn.notification.extras.getString(android.app.Notification.EXTRA_TITLE)
            sender?.let { replyActions.remove(it) }
        }
        super.onNotificationRemoved(sbn)
        NotificationRepository.removeNotification(sbn)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationRepository.clear()
    }
}