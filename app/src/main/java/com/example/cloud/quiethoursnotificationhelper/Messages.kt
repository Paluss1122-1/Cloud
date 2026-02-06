package com.example.cloud.quiethoursnotificationhelper

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.cloud.service.QuietHoursNotificationService.Companion.CHANNEL_ID
import com.example.cloud.service.QuietHoursNotificationService.Companion.KEY_HAS_SAVED_DATA
import com.example.cloud.service.QuietHoursNotificationService.Companion.KEY_SAVED_PACKAGE
import com.example.cloud.service.QuietHoursNotificationService.Companion.KEY_SAVED_RESULT_KEY
import com.example.cloud.service.QuietHoursNotificationService.Companion.KEY_SAVED_SENDER
import com.example.cloud.service.QuietHoursNotificationService.Companion.PREFS_REPLY_DATA
import com.example.cloud.service.QuietHoursNotificationService.SavedReplyData
import com.example.cloud.service.WhatsAppNotificationListener
import com.example.cloud.showSimpleNotificationExtern
import kotlin.time.Duration.Companion.seconds

fun showSavedReplyInfo(context: Context) {
    val savedData = loadSavedReplyData(context)

    if (savedData == null) {
        showSimpleNotificationExtern(
            "ℹ️ Info",
            "Keine gespeicherten Reply-Daten vorhanden",
            context = context
        )
        return
    }

    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle("💾 Gespeicherte Reply-Daten")
        .setContentText(savedData.sender)
        .setStyle(
            NotificationCompat.BigTextStyle()
                .bigText(
                    """
                    Kontakt: ${savedData.sender}
                    Package: ${savedData.packageName}
                    Result Key: ${savedData.resultKey}
                    
                    Verwende 'message [text]' zum Senden
                """.trimIndent()
                )
        )
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setAutoCancel(true)
        .build()

    val notificationManager = context.getSystemService(NotificationManager::class.java)
    if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        == PackageManager.PERMISSION_GRANTED
    ) {
        notificationManager.notify(50001, notification)
    }
}



fun loadSavedReplyData(context: Context): SavedReplyData? {
    try {
        val prefs = context.getSharedPreferences(PREFS_REPLY_DATA, MODE_PRIVATE)

        if (!prefs.getBoolean(KEY_HAS_SAVED_DATA, false)) {
            return null
        }

        val sender = prefs.getString(KEY_SAVED_SENDER, null) ?: return null
        val packageName = prefs.getString(KEY_SAVED_PACKAGE, null) ?: return null
        val resultKey = prefs.getString(KEY_SAVED_RESULT_KEY, null) ?: return null

        return SavedReplyData(sender, packageName, resultKey)

    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error loading saved reply data", e)
        return null
    }
}



fun sendMessageViaSavedReplyData(messageText: String, context: Context) {
    try {
        val savedData = loadSavedReplyData(context)

        if (savedData == null) {
            showSimpleNotificationExtern(
                "❌ Keine Daten",
                "Keine gespeicherten Reply-Daten vorhanden. Verwende 'save [kontakt]' zuerst.",
                context = context
            )
            return
        }

        val currentReplyData = WhatsAppNotificationListener.replyActions[savedData.sender]

        if (currentReplyData != null) {
            sendMessageToWhatsApp(savedData.sender, messageText, currentReplyData, context)
        } else {
            showSimpleNotificationExtern(
                "❌ Senden fehlgeschlagen",
                "Keine aktive Notification von ${savedData.sender}. Warte auf neue Nachricht.",
                20.seconds,
                context = context
            )

            Log.w(
                "QuietHoursService",
                "Cannot send without current PendingIntent - saved data alone is not enough"
            )
        }
    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error sending message via saved data", e)
        showSimpleNotificationExtern("❌ Fehler", "Nachricht konnte nicht gesendet werden", context = context)
    }
}

private fun sendMessageToWhatsApp(
    sender: String,
    messageText: String,
    replyData: WhatsAppNotificationListener.Companion.ReplyData,
    context: Context
) {
    try {
        val intent = Intent().apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val bundle = Bundle().apply {
            putCharSequence(replyData.originalResultKey, messageText)
        }

        val remoteInputForSending = RemoteInput.Builder(replyData.originalResultKey)
            .setLabel(replyData.remoteInput.label)
            .setChoices(replyData.remoteInput.choices)
            .setAllowFreeFormInput(replyData.remoteInput.allowFreeFormInput)
            .build()

        RemoteInput.addResultsToIntent(
            arrayOf(remoteInputForSending),
            intent,
            bundle
        )

        replyData.pendingIntent.send(context, 0, intent)

        val messagesList =
            WhatsAppNotificationListener.messagesByContact.getOrPut(sender) { mutableListOf() }

        val lastMessage = messagesList.lastOrNull()
        val isDuplicate = if (lastMessage != null) {
            messagesList.takeLast(3).any { existingMsg ->
                existingMsg.text == messageText
            }
        } else {
            false
        }

        if (!isDuplicate) {
            messagesList.add(
                WhatsAppNotificationListener.Companion.ChatMessage(
                    text = messageText,
                    timestamp = System.currentTimeMillis(),
                    isOwnMessage = true
                )
            )
        }

        showSimpleNotificationExtern(
            "✅ Gesendet",
            "Nachricht an '$sender': $messageText",
            context = context
        )

    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error sending message", e)
        showSimpleNotificationExtern("❌ Fehler", "Versand fehlgeschlagen", context = context)
    }
}