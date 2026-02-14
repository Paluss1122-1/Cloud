package com.example.cloud.quiethoursnotificationhelper

import android.Manifest
import android.R
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.cloud.ERRORINSERT
import com.example.cloud.ERRORINSERTDATA
import com.example.cloud.service.QuietHoursNotificationService
import com.example.cloud.service.QuietHoursNotificationService.Companion.ACTION_MARK_PARTS_READ
import com.example.cloud.service.QuietHoursNotificationService.Companion.ACTION_MESSAGE_SENT
import com.example.cloud.service.QuietHoursNotificationService.Companion.CHANNEL_ID
import com.example.cloud.service.QuietHoursNotificationService.Companion.EXTRA_MESSAGE_ID
import com.example.cloud.service.QuietHoursNotificationService.Companion.EXTRA_SENDER
import com.example.cloud.service.QuietHoursNotificationService.Companion.KEY_HAS_SAVED_DATA
import com.example.cloud.service.QuietHoursNotificationService.Companion.KEY_SAVED_PACKAGE
import com.example.cloud.service.QuietHoursNotificationService.Companion.KEY_SAVED_RESULT_KEY
import com.example.cloud.service.QuietHoursNotificationService.Companion.KEY_SAVED_SENDER
import com.example.cloud.service.QuietHoursNotificationService.Companion.MAX_MESSAGES_PER_CONTACT
import com.example.cloud.service.QuietHoursNotificationService.Companion.PREFS_REPLY_DATA
import com.example.cloud.service.QuietHoursNotificationService.Companion.isSupportedMessenger
import com.example.cloud.service.QuietHoursNotificationService.Companion.readMessageIds
import com.example.cloud.service.QuietHoursNotificationService.Companion.workerHandler
import com.example.cloud.service.WhatsAppNotificationListener
import com.example.cloud.showSimpleNotificationExtern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

data class SavedReplyData(
    val sender: String,
    val packageName: String,
    val resultKey: String
)

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


private fun loadSavedReplyData(context: Context): SavedReplyData? {
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
        showSimpleNotificationExtern(
            "❌ Fehler",
            "Nachricht konnte nicht gesendet werden",
            context = context
        )
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

fun saveReplyDataPermanently(sender: String, context: Context) {
    try {
        val replyData = WhatsAppNotificationListener.replyActions[sender]

        if (replyData == null) {
            showSimpleNotificationExtern(
                "❌ Fehler",
                "Keine Reply-Daten für '$sender' gefunden",
                context = context
            )
            return
        }

        val packageName = replyData.pendingIntent.creatorPackage ?: "unknown"
        val resultKey = replyData.originalResultKey

        val prefs = context.getSharedPreferences(PREFS_REPLY_DATA, MODE_PRIVATE)
        prefs.edit(commit = true) {
            putString(KEY_SAVED_SENDER, sender)
            putString(KEY_SAVED_PACKAGE, packageName)
            putString(KEY_SAVED_RESULT_KEY, resultKey)
            putBoolean(KEY_HAS_SAVED_DATA, true)
        }

        showSimpleNotificationExtern(
            "✅ Gespeichert",
            "Reply-Daten für '$sender' wurden permanent gespeichert",
            context = context
        )

    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error saving reply data", e)
        showSimpleNotificationExtern(
            "❌ Fehler",
            "Konnte Reply-Daten nicht speichern",
            context = context
        )
    }
}

fun extractLastMessage(context: Context) {
    try {
        val messagesByContact = WhatsAppNotificationListener.messagesByContact

        if (messagesByContact.isEmpty()) {
            showSimpleNotificationExtern(
                "❌ Keine Nachrichten",
                "Keine Nachrichten zum Extrahieren verfügbar",
                context = context
            )
            return
        }

        var newestMessage: WhatsAppNotificationListener.Companion.ChatMessage? = null
        var newestSender: String? = null
        var newestTimestamp: Long = 0

        messagesByContact.forEach { (sender, messages) ->
            val lastMessage = messages.lastOrNull()
            if (lastMessage != null && lastMessage.timestamp > newestTimestamp) {
                newestMessage = lastMessage
                newestSender = sender
                newestTimestamp = lastMessage.timestamp
            }
        }

        if (newestMessage == null || newestSender == null) {
            showSimpleNotificationExtern(
                "❌ Fehler",
                "Keine Nachricht gefunden",
                context = context
            )
            return
        }

        val message = newestMessage
        val sender = newestSender
        val timeText =
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(message.timestamp))

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("📋 Extrahierte Nachricht")
            .setContentText("Von: $sender um $timeText")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(
                        """
                        Von: $sender
                        Zeit: $timeText
                        
                        ${message.text}
                    """.trimIndent()
                    )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        if (message.imageUri != null) {
            try {
                val bitmap = android.graphics.ImageDecoder.decodeBitmap(
                    android.graphics.ImageDecoder.createSource(
                        context.contentResolver,
                        message.imageUri
                    )
                )

                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .bigLargeIcon(null as android.graphics.Bitmap?)
                )
                builder.setLargeIcon(bitmap)

            } catch (e: Exception) {
                Log.e("QuietHoursService", "Error loading image for extract", e)
            }
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(
                60000 + (message.timestamp % 10000).toInt(),
                builder.build()
            )

            showSimpleNotificationExtern(
                "✅ Extrahiert",
                "Nachricht von $sender wurde als separate Notification angezeigt",
                context = context
            )
        }

    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error extracting last message", e)
        showSimpleNotificationExtern(
            "❌ Fehler",
            "Nachricht konnte nicht extrahiert werden: ${e.message}",
            context = context
        )
    }
}

fun cleanupOldMessages() {
    workerHandler.post {
        val cutoffTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000)

        WhatsAppNotificationListener.messagesByContact.forEach { (_, messages) ->
            messages.removeAll { it.timestamp < cutoffTime }

            if (messages.size > MAX_MESSAGES_PER_CONTACT) {
                val toRemove = messages.size - MAX_MESSAGES_PER_CONTACT
                repeat(toRemove) { messages.removeAt(0) }
            }
        }

        WhatsAppNotificationListener.messagesByContact.entries.removeIf { it.value.isEmpty() }
    }
}

fun handleMessageSent(sender: String, messageText: String, context: Context) {
    try {
        val originalReplyData = WhatsAppNotificationListener.replyActions[sender]

        // Prüfe ob es ein unterstützter Messenger ist (WhatsApp oder Telegram)
        val isSupportedMessenger =
            originalReplyData?.pendingIntent?.creatorPackage?.let { pkg ->
                isSupportedMessenger(pkg)
            } ?: false

        if (isSupportedMessenger) {
            try {
                val intent = Intent().apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val bundle = Bundle().apply {
                    putCharSequence(originalReplyData.originalResultKey, messageText)
                }

                val originalRemoteInputForSending =
                    RemoteInput.Builder(originalReplyData.originalResultKey)
                        .setLabel(originalReplyData.remoteInput.label)
                        .setChoices(originalReplyData.remoteInput.choices)
                        .setAllowFreeFormInput(originalReplyData.remoteInput.allowFreeFormInput)
                        .build()

                RemoteInput.addResultsToIntent(
                    arrayOf(originalRemoteInputForSending),
                    intent,
                    bundle
                )
                originalReplyData.pendingIntent.send(context, 0, intent)

                Log.d(
                    "QuietHoursService",
                    "Message sent via ${originalReplyData.pendingIntent.creatorPackage}"
                )

            } catch (e: Exception) {
                Log.e("QuietHoursService", "Failed to send message", e)
                showSimpleNotificationExtern(
                    "Fehler",
                    "Nachricht konnte nicht gesendet werden",
                    context = context
                )
                return
            }
        } else {
            Log.w(
                "QuietHoursService",
                "Unsupported messenger: ${originalReplyData?.pendingIntent?.creatorPackage}"
            )
            showSimpleNotificationExtern(
                "⚠️ Nicht unterstützt",
                "Messenger wird nicht unterstützt: ${originalReplyData?.pendingIntent?.creatorPackage}",
                20.seconds,
                context
            )
            return
        }

        // Nachricht zur lokalen Liste hinzufügen
        val messagesList =
            WhatsAppNotificationListener.messagesByContact.getOrPut(sender) { mutableListOf() }

        val lastMessage = messagesList.lastOrNull()
        val isDuplicate = if (lastMessage != null) {
            messagesList.takeLast(3).any { existingMsg ->
                existingMsg.text == messageText && messageText.length > 5
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

        updateChatNotification(sender, context)
        if (messagesList.size > MAX_MESSAGES_PER_CONTACT) {
            messagesList.subList(0, messagesList.size - MAX_MESSAGES_PER_CONTACT).clear()
        }
    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error handling sent message", e)
        e.printStackTrace()
    }
}

private fun updateChatNotification(sender: String, context: Context) {
    try {
        val notificationId = sender.hashCode()
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        for (i in 0 until 100) {
            notificationManager.cancel(notificationId + 10000 + i)
        }

        val mePerson = Person.Builder()
            .setName("Du")
            .setKey("me")
            .build()

        val senderPerson = Person.Builder()
            .setName(sender)
            .setKey(sender)
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(mePerson)
            .setConversationTitle(sender)

        val messages = WhatsAppNotificationListener.messagesByContact[sender] ?: emptyList()

        var partIndex = 0

        messages.takeLast(5).forEach { msg ->
            val messageId = "${sender}_${msg.timestamp}"

            if (readMessageIds.contains(messageId)) {
                return@forEach
            }

            val timeText =
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))

            val maxMessageLength = 200
            val messageText = msg.text

            if (messageText.length > maxMessageLength) {
                if (!readMessageIds.contains(messageId)) {
                    val parts = messageText.chunked(maxMessageLength)
                    val totalParts = parts.size

                    parts.forEachIndexed { index, part ->
                        val partNotificationId = notificationId + 10000 + partIndex
                        partIndex++

                        val contentText = part.take(100) + if (part.length > 100) "..." else ""

                        val markReadIntent = Intent(ACTION_MARK_PARTS_READ).apply {
                            putExtra(EXTRA_MESSAGE_ID, messageId)
                            `package` = context.packageName
                        }
                        val markReadPendingIntent = PendingIntent.getBroadcast(
                            context,
                            partNotificationId + 500000,
                            markReadIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        val partNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.stat_notify_chat)
                            .setContentTitle("💬 $sender (Teil ${index + 1}/$totalParts)")
                            .setContentText(contentText)
                            .setStyle(
                                NotificationCompat.BigTextStyle()
                                    .bigText(part)
                                    .setBigContentTitle("💬 $sender (Teil ${index + 1}/$totalParts)")
                                    .setSummaryText("⏰ $timeText")
                            )
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setGroup("whatsapp_long_$sender")
                            .setAutoCancel(false)
                            .addAction(
                                android.R.drawable.ic_menu_close_clear_cancel,
                                "Als gelesen markieren",
                                markReadPendingIntent
                            )
                            .build()

                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationManager.notify(partNotificationId, partNotification)
                        }
                    }
                }
            } else {
                messagingStyle.addMessage(
                    NotificationCompat.MessagingStyle.Message(
                        "$messageText • $timeText",
                        msg.timestamp,
                        if (msg.isOwnMessage) mePerson else senderPerson
                    )
                )
            }
        }

        val newRemoteInput = RemoteInput.Builder("key_text_reply")
            .setLabel("Antwort")
            .build()

        val newSendIntent = Intent(ACTION_MESSAGE_SENT).apply {
            putExtra(EXTRA_SENDER, sender)
            `package` = context.packageName
        }

        val newSendPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            newSendIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Antworten",
            newSendPendingIntent
        )
            .addRemoteInput(newRemoteInput)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(false)
            .build()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(messagingStyle)
            .setAutoCancel(false)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .addAction(replyAction)

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(notificationId, builder.build())
        }

    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error updating chat notification", e)
    }
}

fun showUnreadMessages(context: Context) {
    val replyActions = WhatsAppNotificationListener.replyActions
    val messagesByContact = WhatsAppNotificationListener.messagesByContact
    val mePerson = Person.Builder()
        .setName("Du")
        .setKey("me")
        .build()

    try {
        val unreadMessages = WhatsAppNotificationListener.getMessages()
        if (unreadMessages.isEmpty()) {
            Log.w("QuietHoursService", "No unread messages found")
            showSimpleNotificationExtern(
                "Keine unbeantworteten Nachrichten",
                "Alle Nachrichten wurden beantwortet oder keine Daten verfügbar.",
                context = context
            )
            return
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        messagesByContact.forEach { (sender, msgs) ->
            val notificationId = sender.hashCode()
            val senderPerson = Person.Builder()
                .setName(sender)
                .setKey(sender)
                .build()

            val builder = NotificationCompat.Builder(context, "whatsapp_listener_channel")
                .setSmallIcon(R.drawable.stat_notify_chat)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(false)
                .setOngoing(false)

            val messagingStyle = NotificationCompat.MessagingStyle(mePerson)
                .setConversationTitle(sender)

            var partIndex = 0

            msgs.takeLast(5).forEach { msg ->
                val messageId = "${sender}_${msg.timestamp}"

                // GEÄNDERT: Verwende explizit die Companion Object Variable
                if (QuietHoursNotificationService.readMessageIds.contains(messageId)) {
                    return@forEach
                }

                val timeText =
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))

                val maxMessageLength = 200
                val messageText = msg.text

                if (messageText.length > maxMessageLength) {
                    val parts = messageText.chunked(maxMessageLength)
                    val totalParts = parts.size

                    parts.forEachIndexed { index, part ->
                        val partNotificationId = notificationId + 10000 + partIndex
                        partIndex++

                        val contentText = part.take(100) + if (part.length > 100) "..." else ""

                        val markReadIntent = Intent(ACTION_MARK_PARTS_READ).apply {
                            putExtra(EXTRA_MESSAGE_ID, messageId)
                            `package` = context.packageName
                        }
                        val markReadPendingIntent = PendingIntent.getBroadcast(
                            context,
                            partNotificationId + 500000,
                            markReadIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        val partNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                            .setSmallIcon(R.drawable.stat_notify_chat)
                            .setContentTitle("💬 $sender (Teil ${index + 1}/$totalParts)")
                            .setContentText(contentText)
                            .setStyle(
                                NotificationCompat.BigTextStyle()
                                    .bigText(part)
                                    .setBigContentTitle("💬 $sender (Teil ${index + 1}/$totalParts)")
                                    .setSummaryText("⏰ $timeText")
                            )
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setGroup("whatsapp_long_$sender")
                            .setAutoCancel(false)
                            .addAction(
                                R.drawable.ic_menu_close_clear_cancel,
                                "Als gelesen markieren",
                                markReadPendingIntent
                            )
                            .build()

                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationManager.notify(partNotificationId, partNotification)
                        }
                    }
                } else {
                    messagingStyle.addMessage(
                        NotificationCompat.MessagingStyle.Message(
                            "$messageText • $timeText",
                            msg.timestamp,
                            if (msg.isOwnMessage) mePerson else senderPerson
                        )
                    )
                }
            }

            builder.setStyle(messagingStyle)

            val replyData = replyActions[sender]
            if (replyData != null) {
                val replyRemoteInput = RemoteInput.Builder("key_text_reply")
                    .setLabel("Antwort")
                    .build()

                val sendBroadcastIntent = Intent(ACTION_MESSAGE_SENT).apply {
                    putExtra(EXTRA_SENDER, sender)
                    `package` = context.packageName
                }

                val sendBroadcastPendingIntent = PendingIntent.getBroadcast(
                    context,
                    notificationId,
                    sendBroadcastIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )

                val replyAction = NotificationCompat.Action.Builder(
                    R.drawable.ic_menu_send,
                    "Antworten",
                    sendBroadcastPendingIntent
                )
                    .addRemoteInput(replyRemoteInput)
                    .setShowsUserInterface(false)
                    .setAllowGeneratedReplies(false)
                    .build()

                builder.addAction(replyAction)
            }

            val messagesWithImages = msgs.filter {
                // GEÄNDERT: Auch hier explizit die Companion Object Variable verwenden
                it.imageUri != null && !QuietHoursNotificationService.readMessageIds.contains("${sender}_${it.timestamp}")
            }

            if (messagesWithImages.isNotEmpty()) {
                messagesWithImages.forEachIndexed { index, msg ->
                    val imageNotificationId = notificationId + 1000 + index
                    val messageId = "${sender}_${msg.timestamp}"

                    val markReadIntent = Intent(ACTION_MARK_PARTS_READ).apply {
                        putExtra(EXTRA_MESSAGE_ID, messageId)
                        `package` = context.packageName
                    }
                    val markReadPendingIntent = PendingIntent.getBroadcast(
                        context,
                        imageNotificationId + 500000,
                        markReadIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val imageNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_menu_gallery)
                        .setContentTitle("📷 Bild von $sender")
                        .setContentText(
                            "Empfangen: ${
                                SimpleDateFormat(
                                    "HH:mm",
                                    Locale.getDefault()
                                ).format(Date(msg.timestamp))
                            }"
                        )
                        .setStyle(
                            NotificationCompat.BigPictureStyle()
                                .bigPicture(
                                    ImageDecoder.decodeBitmap(
                                        ImageDecoder.createSource(
                                            context.contentResolver,
                                            msg.imageUri!!
                                        )
                                    )
                                )
                                .bigLargeIcon(null as Bitmap?)
                        )
                        .setLargeIcon(
                            ImageDecoder.decodeBitmap(
                                ImageDecoder.createSource(
                                    context.contentResolver,
                                    msg.imageUri
                                )
                            )
                        )
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setGroup("whatsapp_images_$sender")
                        .setAutoCancel(false)
                        .addAction(
                            R.drawable.ic_menu_close_clear_cancel,
                            "Als gelesen",
                            markReadPendingIntent
                        )
                        .build()

                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationManager.notify(imageNotificationId, imageNotification)
                    }
                }
            }

            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(notificationId, builder.build())
            }
        }
    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error showing messages", e)
        e.printStackTrace()
        showSimpleNotificationExtern(
            "Fehler",
            "Nachrichten konnten nicht angezeigt werden: ${e.message}",
            context = context
        )
    }
}

fun updateSingleSenderNotification(sender: String, context: Context) {

    val messages = WhatsAppNotificationListener.messagesByContact[sender]
    val replyData = WhatsAppNotificationListener.replyActions[sender]

    if (messages == null || replyData == null) {
        Log.w("QuietHoursService", "No messages or reply data for $sender")
        return
    }

    val notificationId = sender.hashCode()

    val mePerson = Person.Builder()
        .setName("Du")
        .setKey("me")
        .build()

    val senderPerson = Person.Builder()
        .setName(sender)
        .setKey(sender)
        .build()

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.stat_notify_chat)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(false)
        .setOngoing(false)
        .setGroup("group_whatsapp")
        .setGroupSummary(false)

    val messagingStyle = NotificationCompat.MessagingStyle(mePerson)
        .setConversationTitle(sender)

    var partIndex = 0

    messages.takeLast(5).forEach { msg ->
        val messageId = "${sender}_${msg.timestamp}"
        // Skip messages that were already marked read
        if (QuietHoursNotificationService.readMessageIds.contains(messageId)) {
            return@forEach
        }
        val timeText =
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))

        val maxMessageLength = 200
        val messageText = msg.text

        if (messageText.length > maxMessageLength) {
            val parts = messageText.chunked(maxMessageLength)
            val totalParts = parts.size

            parts.forEachIndexed { index, part ->
                val partNotificationId = notificationId + 10000 + partIndex
                partIndex++

                val contentText = part.take(100) + if (part.length > 100) "..." else ""

                val markReadIntent = Intent(ACTION_MARK_PARTS_READ).apply {
                    putExtra(EXTRA_MESSAGE_ID, messageId)
                    `package` = context.packageName
                }
                val markReadPendingIntent = PendingIntent.getBroadcast(
                    context,
                    partNotificationId + 500000,
                    markReadIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val partNotification = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.stat_notify_chat)
                    .setContentTitle("💬 $sender (Teil ${index + 1}/$totalParts)")
                    .setContentText(contentText)
                    .setStyle(
                        NotificationCompat.BigTextStyle()
                            .bigText(part)
                            .setBigContentTitle("💬 $sender (Teil ${index + 1}/$totalParts)")
                            .setSummaryText("⏰ $timeText")
                    )
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setGroup("whatsapp_long_$sender")
                    .setAutoCancel(false)
                    .addAction(
                        R.drawable.ic_menu_close_clear_cancel,
                        "Als gelesen markieren",
                        markReadPendingIntent
                    )
                    .build()

                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val notificationManager =
                        context.getSystemService(NotificationManager::class.java)
                    notificationManager.notify(partNotificationId, partNotification)
                }
            }
        } else {
            messagingStyle.addMessage(
                NotificationCompat.MessagingStyle.Message(
                    "$messageText • $timeText",
                    msg.timestamp,
                    if (msg.isOwnMessage) mePerson else senderPerson
                )
            )
        }
    }

    builder.setStyle(messagingStyle)

    val replyRemoteInput = RemoteInput.Builder("key_text_reply")
        .setLabel("Antwort")
        .build()

    val sendBroadcastIntent = Intent(ACTION_MESSAGE_SENT).apply {
        putExtra(EXTRA_SENDER, sender)
        `package` = context.packageName
    }

    val sendBroadcastPendingIntent = PendingIntent.getBroadcast(
        context,
        notificationId,
        sendBroadcastIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    val replyAction = NotificationCompat.Action.Builder(
        R.drawable.ic_menu_send,
        "Antworten",
        sendBroadcastPendingIntent
    )
        .addRemoteInput(replyRemoteInput)
        .setShowsUserInterface(false)
        .setAllowGeneratedReplies(false)
        .build()

    builder.addAction(replyAction)

    val notificationManager = context.getSystemService(NotificationManager::class.java)
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        == PackageManager.PERMISSION_GRANTED
    ) {
        notificationManager.notify(notificationId, builder.build())
    }
}

fun markMessageAsRead(messageId: String, readMessageIds:  MutableSet<String>, context: Context) {
    try {
        readMessageIds.add(messageId)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val parts = messageId.split("_")
        if (parts.size >= 2) {
            val sender = parts[0]

                val senderHash = sender.hashCode()
                // Cancel a larger range to cover split parts (match other places which use up to 100 parts)
                for (i in 0 until 100) {
                    val partNotificationId = senderHash + 10000 + i
                    notificationManager.cancel(partNotificationId)
                }
        }
    } catch (e: Exception) {
        CoroutineScope(Dispatchers.IO).launch {
            ERRORINSERT(
                ERRORINSERTDATA(
                    "markMessageAsRead",
                    "Fehler bei Nachricht als gelesen markieren: ${e.message}",
                    Instant.now().toString(),
                    "Error"
                )
            )
        }
    }
}