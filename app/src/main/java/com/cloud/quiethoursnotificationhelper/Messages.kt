package com.cloud.quiethoursnotificationhelper

import android.Manifest
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
import com.cloud.Config.cms
import com.cloud.ERRORINSERT
import com.cloud.ERRORINSERTDATA
import com.cloud.service.QuietHoursNotificationService.Companion.ACTION_MARK_PARTS_READ
import com.cloud.service.QuietHoursNotificationService.Companion.ACTION_MESSAGE_SENT
import com.cloud.service.QuietHoursNotificationService.Companion.EXTRA_MESSAGE_ID
import com.cloud.service.QuietHoursNotificationService.Companion.EXTRA_SENDER
import com.cloud.service.QuietHoursNotificationService.Companion.KEY_HAS_SAVED_DATA
import com.cloud.service.QuietHoursNotificationService.Companion.KEY_SAVED_PACKAGE
import com.cloud.service.QuietHoursNotificationService.Companion.KEY_SAVED_RESULT_KEY
import com.cloud.service.QuietHoursNotificationService.Companion.KEY_SAVED_SENDER
import com.cloud.service.QuietHoursNotificationService.Companion.MAX_MESSAGES_PER_CONTACT
import com.cloud.service.QuietHoursNotificationService.Companion.PREFS_REPLY_DATA
import com.cloud.service.QuietHoursNotificationService.Companion.isSupportedMessenger
import com.cloud.service.QuietHoursNotificationService.Companion.readMessageIds
import com.cloud.service.QuietHoursNotificationService.Companion.workerHandler
import com.cloud.service.WhatsAppNotificationListener
import com.cloud.showSimpleNotificationExtern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

private const val NVIDIA_CHAT_PREFIX = "NVIDIA Chat"

data class SavedReplyData(
    val sender: String,
    val packageName: String,
    val resultKey: String
)

private fun resolveKey(sender: String): String? {
    if (sender.contains("|")) return sender
    if (sender.startsWith(NVIDIA_CHAT_PREFIX)) return sender
    return WhatsAppNotificationListener.messagesByContact.keys
        .firstOrNull { it.endsWith("|$sender") }
        ?: WhatsAppNotificationListener.replyActions.keys
            .firstOrNull { it.endsWith("|$sender") }
}

private fun buildReplyAction(
    key: String,
    notificationId: Int,
    context: Context
): NotificationCompat.Action {
    val pi = PendingIntent.getBroadcast(
        context, notificationId,
        Intent(ACTION_MESSAGE_SENT).apply {
            putExtra(EXTRA_SENDER, key)
            `package` = context.packageName
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )
    return NotificationCompat.Action.Builder(
        android.R.drawable.ic_menu_send, "Antworten", pi
    )
        .addRemoteInput(RemoteInput.Builder("key_text_reply").setLabel("Antwort").build())
        .setShowsUserInterface(false)
        .setAllowGeneratedReplies(false)
        .build()
}

private fun postChatNotification(key: String, context: Context, sourceLabel: String) {
    try {
        val displayName = if (key.contains("|")) key.substringAfter("|") else key
        val notifId = key.hashCode() and 0x0FFFFFFF
        val summaryId = notifId + 1000000
        val messagingId = notifId + 2000000
        val nm = context.getSystemService(NotificationManager::class.java)

        val mePerson = Person.Builder().setName("Du").setKey("me").build()
        val senderPerson = Person.Builder().setName(displayName).setKey(displayName).build()
        val messages = WhatsAppNotificationListener.messagesByContact[key] ?: emptyList()

        val style = NotificationCompat.MessagingStyle(mePerson)
            .setConversationTitle("$sourceLabel · $displayName")

        var partIndex = 0

        messages.takeLast(5).forEach { msg ->
            val msgId = "${key}_${msg.timestamp}"
            if (readMessageIds.contains(msgId)) return@forEach

            val timeText =
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
            val text = msg.text

            if (text.length > 200) {
                val parts = text.chunked(200)
                parts.reversed().forEachIndexed { idx, part ->
                    val partId = notifId + partIndex
                    partIndex++
                    val markPi = PendingIntent.getBroadcast(
                        context, partId + 500000,
                        Intent(ACTION_MARK_PARTS_READ).apply {
                            putExtra(EXTRA_MESSAGE_ID, msgId)
                            `package` = context.packageName
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val partNum = parts.size - idx
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        )
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        nm.notify(
                            partId, NotificationCompat.Builder(context, "Nachrichten")
                                .setSmallIcon(android.R.drawable.stat_notify_chat)
                                .setContentTitle("$sourceLabel · $displayName (Teil $partNum/${parts.size})")
                                .setContentText(part.take(100) + if (part.length > 100) "..." else "")
                                .setStyle(
                                    NotificationCompat.BigTextStyle()
                                        .bigText(part)
                                        .setBigContentTitle("$sourceLabel · $displayName (Teil $partNum/${parts.size})")
                                        .setSummaryText("⏰ $timeText")
                                )
                                .setPriority(NotificationCompat.PRIORITY_HIGH)
                                .setGroup("long_$key")
                                .setAutoCancel(false)
                                .addAction(
                                    android.R.drawable.ic_menu_close_clear_cancel,
                                    "Als gelesen",
                                    markPi
                                )
                                .build()
                        )
                    }
                }
            } else {
                style.addMessage(
                    NotificationCompat.MessagingStyle.Message(
                        "$text • $timeText",
                        msg.timestamp,
                        if (msg.isOwnMessage) mePerson else senderPerson
                    )
                )
            }
        }

        if (partIndex > 0) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                nm.notify(
                    summaryId, NotificationCompat.Builder(context, "Nachrichten")
                        .setSmallIcon(android.R.drawable.stat_notify_chat)
                        .setContentTitle("$sourceLabel · Lange Nachricht von $displayName")
                        .setContentText("$partIndex Teile")
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setGroup("long_$key")
                        .setGroupSummary(true)
                        .setAutoCancel(true)
                        .build()
                )
            }
        }

        val notification = NotificationCompat.Builder(context, "Nachrichten")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(style)
            .setAutoCancel(false)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .addAction(buildReplyAction(key, messagingId, context))
            .build()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            nm.notify(messagingId, notification)
        }
    } catch (e: Exception) {
        Log.e("Messages", "postChatNotification failed: ${e.message}")
    }
}

fun updateSingleSenderNotification(sender: String, context: Context) {
    val key = resolveKey(sender) ?: run {
        Log.w("Messages", "No key for sender: $sender")
        return
    }
    if (WhatsAppNotificationListener.messagesByContact[key] == null) {
        Log.w("Messages", "No messages for key: $key")
        return
    }
    postChatNotification(key, context, "📨 Neu")
}

fun updateChatNotification(key: String, context: Context) {
    postChatNotification(key, context, "💬 Chat")
}

fun showUnreadMessages(context: Context) {
    val msgs = WhatsAppNotificationListener.messagesByContact
    if (msgs.isEmpty()) {
        showSimpleNotificationExtern(
            "Keine unbeantworteten Nachrichten",
            "Alle Nachrichten wurden beantwortet oder keine Daten verfügbar.",
            context = context,
            silent = false
        )
        return
    }
    msgs.keys.forEach { key -> postChatNotification(key, context, "📋 Ungelesen") }
}

fun handleMessageSent(sender: String, messageText: String, context: Context) {
    val key = resolveKey(sender) ?: sender
    val displayName = if (key.contains("|")) key.substringAfter("|") else key

    try {
        if (displayName == NVIDIA_CHAT_PREFIX || displayName.startsWith("$NVIDIA_CHAT_PREFIX:")) {
            val list =
                WhatsAppNotificationListener.messagesByContact.getOrPut(key) { mutableListOf() }
            val trimmed = messageText.trim()
            if (trimmed.isNotEmpty()) {
                list.add(
                    WhatsAppNotificationListener.Companion.ChatMessage(
                        trimmed,
                        System.currentTimeMillis(),
                        true
                    )
                )
            }
            if (list.size > MAX_MESSAGES_PER_CONTACT)
                list.subList(0, list.size - MAX_MESSAGES_PER_CONTACT).clear()
            updateChatNotification(key, context)

            CoroutineScope(Dispatchers.IO).launch {
                val snapshot = list.toList()
                val answer = sendNvidiaChatMessage(snapshot, trimmed)
                if (!answer.isNullOrBlank()) {
                    list.add(
                        WhatsAppNotificationListener.Companion.ChatMessage(
                            answer,
                            System.currentTimeMillis(),
                            false
                        )
                    )
                    if (list.size > MAX_MESSAGES_PER_CONTACT)
                        list.subList(0, list.size - MAX_MESSAGES_PER_CONTACT).clear()
                    withContext(Dispatchers.Main) { updateChatNotification(key, context) }
                } else {
                    withContext(Dispatchers.Main) {
                        showSimpleNotificationExtern(
                            "❌ NVIDIA Chat",
                            "Antwort konnte nicht geladen werden.",
                            context = context
                        )
                    }
                }
            }
            return
        }

        val replyData = WhatsAppNotificationListener.replyActions[key]
        val supported =
            replyData?.pendingIntent?.creatorPackage?.let { isSupportedMessenger(it) } ?: false

        if (!supported) {
            Log.w("Messages", "Unsupported messenger: ${replyData?.pendingIntent?.creatorPackage}")
            showSimpleNotificationExtern(
                "⚠️ Nicht unterstützt",
                "Messenger wird nicht unterstützt: ${replyData?.pendingIntent?.creatorPackage}",
                20.seconds, context,
                silent = false
            )
            return
        }

        try {
            val intent = Intent().apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            val bundle =
                Bundle().apply { putCharSequence(replyData.originalResultKey, messageText) }
            val ri = RemoteInput.Builder(replyData.originalResultKey)
                .setLabel(replyData.remoteInput.label)
                .setChoices(replyData.remoteInput.choices)
                .setAllowFreeFormInput(replyData.remoteInput.allowFreeFormInput)
                .build()
            RemoteInput.addResultsToIntent(arrayOf(ri), intent, bundle)
            replyData.pendingIntent.send(context, 0, intent)
        } catch (e: Exception) {
            Log.e("Messages", "Send failed: ${e.message}")
            showSimpleNotificationExtern(
                "Fehler", "Nachricht konnte nicht gesendet werden", context = context,
                silent = false
            )
            return
        }

        val list = WhatsAppNotificationListener.messagesByContact.getOrPut(key) { mutableListOf() }
        val isDup = list.takeLast(3).any { it.text == messageText && messageText.length > 5 }
        if (!isDup) {
            list.add(
                WhatsAppNotificationListener.Companion.ChatMessage(
                    messageText,
                    System.currentTimeMillis(),
                    true
                )
            )
        }

        updateChatNotification(key, context)

        if (list.size > MAX_MESSAGES_PER_CONTACT)
            list.subList(0, list.size - MAX_MESSAGES_PER_CONTACT).clear()

    } catch (e: Exception) {
        Log.e("Messages", "handleMessageSent error: ${e.message}")
    }
}

fun cleanupOldMessages() {
    workerHandler.post {
        val cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        WhatsAppNotificationListener.messagesByContact.forEach { (_, msgs) ->
            msgs.removeAll { it.timestamp < cutoff }
            if (msgs.size > MAX_MESSAGES_PER_CONTACT)
                msgs.subList(0, msgs.size - MAX_MESSAGES_PER_CONTACT).clear()
        }
        WhatsAppNotificationListener.messagesByContact.entries.removeIf { it.value.isEmpty() }
    }
}

fun markMessageAsRead(messageId: String, readMessageIds: MutableSet<String>, context: Context) {
    try {
        readMessageIds.add(messageId)
        val key = messageId.substringBeforeLast("_")
        val hash = key.hashCode()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        for (i in 0 until 100) nm.cancel(hash + i)
        nm.cancel(hash)
    } catch (e: Exception) {
        CoroutineScope(Dispatchers.IO).launch {
            ERRORINSERT(
                ERRORINSERTDATA(
                    "markMessageAsRead",
                    "ERROR: ${e.message}",
                    Instant.now().toString(),
                    "ERROR"
                )
            )
        }
    }
}

fun createNvidiaChat(name: String?, context: Context) {
    val title = if (name.isNullOrBlank()) NVIDIA_CHAT_PREFIX else "$NVIDIA_CHAT_PREFIX: $name"
    val list = WhatsAppNotificationListener.messagesByContact.getOrPut(title) { mutableListOf() }
    if (list.isEmpty()) {
        list.add(
            WhatsAppNotificationListener.Companion.ChatMessage(
                "Neuer NVIDIA-Chat \"$title\" erstellt. Antworte auf diese Nachricht, um zu schreiben.",
                System.currentTimeMillis(), false
            )
        )
    }
    updateChatNotification(title, context)
}

fun saveReplyDataPermanently(sender: String, context: Context) {
    try {
        val key = resolveKey(sender) ?: sender
        val replyData = WhatsAppNotificationListener.replyActions[key]
        if (replyData == null) {
            showSimpleNotificationExtern(
                "❌ Fehler",
                "Keine Reply-Daten für '$sender' gefunden",
                context = context
            )
            return
        }
        val displayName = key.substringAfter("|")
        context.getSharedPreferences(PREFS_REPLY_DATA, MODE_PRIVATE).edit(commit = true) {
            putString(KEY_SAVED_SENDER, displayName)
            putString(KEY_SAVED_PACKAGE, replyData.pendingIntent.creatorPackage ?: "unknown")
            putString(KEY_SAVED_RESULT_KEY, replyData.originalResultKey)
            putBoolean(KEY_HAS_SAVED_DATA, true)
        }
        showSimpleNotificationExtern(
            "✅ Gespeichert",
            "Reply-Daten für '$displayName' gespeichert",
            context = context
        )
    } catch (_: Exception) {
        showSimpleNotificationExtern(
            "❌ Fehler",
            "Konnte Reply-Daten nicht speichern",
            context = context
        )
    }
}

private fun loadSavedReplyData(context: Context): SavedReplyData? {
    return try {
        val prefs = context.getSharedPreferences(PREFS_REPLY_DATA, MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_HAS_SAVED_DATA, false)) return null
        SavedReplyData(
            prefs.getString(KEY_SAVED_SENDER, null) ?: return null,
            prefs.getString(KEY_SAVED_PACKAGE, null) ?: return null,
            prefs.getString(KEY_SAVED_RESULT_KEY, null) ?: return null
        )
    } catch (_: Exception) {
        null
    }
}

fun sendMessageViaSavedReplyData(messageText: String, context: Context) {
    val savedData = loadSavedReplyData(context) ?: run {
        showSimpleNotificationExtern(
            "❌ Keine Daten",
            "Keine gespeicherten Reply-Daten. Verwende 'save [kontakt]'.",
            context = context
        )
        return
    }
    val key = resolveKey(savedData.sender) ?: savedData.sender
    val replyData = WhatsAppNotificationListener.replyActions[key]
    if (replyData != null) {
        handleMessageSent(key, messageText, context)
    } else {
        showSimpleNotificationExtern(
            "❌ Senden fehlgeschlagen",
            "Keine aktive Notification von ${savedData.sender}. Warte auf neue Nachricht.",
            20.seconds, context = context
        )
    }
}

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
    val nm = context.getSystemService(NotificationManager::class.java)
    if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
        nm.notify(
            cms(), NotificationCompat.Builder(context, "Nachrichten")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("💾 Gespeicherte Reply-Daten")
                .setContentText(savedData.sender)
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Kontakt: ${savedData.sender}\nPackage: ${savedData.packageName}\nResult Key: ${savedData.resultKey}\n\nVerwende 'message [text]' zum Senden"
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
        )
    }
}

fun extractLastMessage(context: Context) {
    val msgs = WhatsAppNotificationListener.messagesByContact
    if (msgs.isEmpty()) {
        showSimpleNotificationExtern(
            "❌ Keine Nachrichten",
            "Keine Nachrichten verfügbar",
            context = context
        )
        return
    }
    var newestMsg: WhatsAppNotificationListener.Companion.ChatMessage? = null
    var newestKey = ""
    msgs.forEach { (key, list) ->
        val last = list.lastOrNull()
        if (last != null && (newestMsg == null || last.timestamp > newestMsg.timestamp)) {
            newestMsg = last; newestKey = key
        }
    }
    val msg = newestMsg ?: return
    val displayName = if (newestKey.contains("|")) newestKey.substringAfter("|") else newestKey
    val timeText = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(msg.timestamp))
    val builder = NotificationCompat.Builder(context, "Nachrichten")
        .setSmallIcon(android.R.drawable.ic_menu_info_details)
        .setContentTitle("📋 Extrahierte Nachricht")
        .setContentText("Von: $displayName um $timeText")
        .setStyle(
            NotificationCompat.BigTextStyle()
                .bigText("Von: $displayName\nZeit: $timeText\n\n${msg.text}")
        )
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
    if (msg.imageUri != null) {
        try {
            val bmp = ImageDecoder.decodeBitmap(
                ImageDecoder.createSource(
                    context.contentResolver,
                    msg.imageUri
                )
            )
            builder.setStyle(
                NotificationCompat.BigPictureStyle().bigPicture(bmp).bigLargeIcon(null as Bitmap?)
            )
            builder.setLargeIcon(bmp)
        } catch (e: Exception) {
            Log.e("Messages", "extractLastMessage image error: ${e.message}")
        }
    }
    val nm = context.getSystemService(NotificationManager::class.java)
    if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
        nm.notify(cms(), builder.build())
    }
}