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
import com.cloud.ERRORINSERT
import com.cloud.ERRORINSERTDATA
import com.cloud.service.QuietHoursNotificationService
import com.cloud.service.QuietHoursNotificationService.Companion.ACTION_MARK_PARTS_READ
import com.cloud.service.QuietHoursNotificationService.Companion.ACTION_MESSAGE_SENT
import com.cloud.service.QuietHoursNotificationService.Companion.CHANNEL_ID
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

private fun sendMessageToWhatsApp(
    key: String,
    messageText: String,
    replyData: WhatsAppNotificationListener.Companion.ReplyData,
    context: Context
) {
    val displayName = if (key.contains("|")) key.substringAfter("|") else key
    try {
        val intent = Intent().apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        val bundle = Bundle().apply { putCharSequence(replyData.originalResultKey, messageText) }
        val remoteInputForSending = RemoteInput.Builder(replyData.originalResultKey)
            .setLabel(replyData.remoteInput.label)
            .setChoices(replyData.remoteInput.choices)
            .setAllowFreeFormInput(replyData.remoteInput.allowFreeFormInput)
            .build()
        RemoteInput.addResultsToIntent(arrayOf(remoteInputForSending), intent, bundle)
        replyData.pendingIntent.send(context, 0, intent)

        val messagesList = WhatsAppNotificationListener.messagesByContact
            .getOrPut(key) { mutableListOf() }

        val isDuplicate = messagesList.takeLast(3).any { it.text == messageText }
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
            "✅ Gesendet", "Nachricht an '$displayName': $messageText", context = context
        )
    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error sending message", e)
        showSimpleNotificationExtern("❌ Fehler", "Versand fehlgeschlagen", context = context)
    }
}

private const val NVIDIA_CHAT_PREFIX = "NVIDIA Chat"

data class SavedReplyData(
    val sender: String,
    val packageName: String,
    val resultKey: String
)

// "packageName|title"-Key aus plain Sender-Name ableiten
private fun resolveKey(sender: String): String? {
    if (sender.contains("|")) return sender
    if (sender.startsWith(NVIDIA_CHAT_PREFIX)) return sender
    return WhatsAppNotificationListener.messagesByContact.keys
        .firstOrNull { it.endsWith("|$sender") }
        ?: WhatsAppNotificationListener.replyActions.keys
            .firstOrNull { it.endsWith("|$sender") }
}

private fun buildReplyAction(key: String, notificationId: Int, context: Context): NotificationCompat.Action {
    // IMMER eigenen Broadcast verwenden → messageSentReceiver feuert →
    // handleMessageSent() sendet an WhatsApp UND updated Notification → Spinner stoppt
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

// Kern: MessagingStyle + Split-Parts posten. sourceLabel erscheint im Titel.
private fun postChatNotification(key: String, context: Context, sourceLabel: String) {
    try {
        val displayName = if (key.contains("|")) key.substringAfter("|") else key
        val notifId = key.hashCode()
        val nm = context.getSystemService(NotificationManager::class.java)

        // Alte Split-Parts canceln
        for (i in 0 until 100) nm.cancel(notifId + 10000 + i)
        nm.cancel(notifId + 19999)

        val mePerson = Person.Builder().setName("Du").setKey("me").build()
        val senderPerson = Person.Builder().setName(displayName).setKey(displayName).build()
        val messages = WhatsAppNotificationListener.messagesByContact[key] ?: emptyList()

        val style = NotificationCompat.MessagingStyle(mePerson)
            .setConversationTitle("$sourceLabel · $displayName")

        var partIndex = 0

        messages.takeLast(5).forEach { msg ->
            val msgId = "${key}_${msg.timestamp}"
            if (readMessageIds.contains(msgId)) return@forEach

            val timeText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
            val text = msg.text

            if (text.length > 200) {
                val parts = text.chunked(200)
                parts.reversed().forEachIndexed { idx, part ->
                    val partId = notifId + 10000 + partIndex
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
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        nm.notify(partId, NotificationCompat.Builder(context, CHANNEL_ID)
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
                            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Als gelesen", markPi)
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
                nm.notify(notifId + 19999, NotificationCompat.Builder(context, CHANNEL_ID)
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

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setStyle(style)
            .setAutoCancel(false)
            .setOngoing(false)
            .setOnlyAlertOnce(true)
            .addAction(buildReplyAction(key, notifId, context))
            .build()

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            nm.notify(notifId, notification)
        }
    } catch (e: Exception) {
        Log.e("Messages", "postChatNotification failed: ${e.message}")
    }
}

// Neue eingehende Nachricht – nur diese eine Notification aktualisieren
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

// Eigene Antwort abgeschickt – Spinner-Fix: sofort notify() mit gleichem notifId
fun updateChatNotification(key: String, context: Context) {
    postChatNotification(key, context, "💬 Chat")
}

// Alle ungelesenen anzeigen (manueller Aufruf)
fun showUnreadMessages(context: Context) {
    val msgs = WhatsAppNotificationListener.messagesByContact
    if (msgs.isEmpty()) {
        showSimpleNotificationExtern(
            "Keine unbeantworteten Nachrichten",
            "Alle Nachrichten wurden beantwortet oder keine Daten verfügbar.",
            context = context
        )
        return
    }
    // Nur je eine Notification pro Kontakt – kein Massenupdate aller
    msgs.keys.forEach { key -> postChatNotification(key, context, "📋 Ungelesen") }
}

fun handleMessageSent(sender: String, messageText: String, context: Context) {
    val key = resolveKey(sender) ?: sender
    val displayName = if (key.contains("|")) key.substringAfter("|") else key

    try {
        if (displayName == NVIDIA_CHAT_PREFIX || displayName.startsWith("$NVIDIA_CHAT_PREFIX:")) {
            val list = WhatsAppNotificationListener.messagesByContact.getOrPut(key) { mutableListOf() }
            val trimmed = messageText.trim()
            if (trimmed.isNotEmpty()) {
                list.add(WhatsAppNotificationListener.Companion.ChatMessage(trimmed, System.currentTimeMillis(), true))
            }
            if (list.size > MAX_MESSAGES_PER_CONTACT)
                list.subList(0, list.size - MAX_MESSAGES_PER_CONTACT).clear()
            updateChatNotification(key, context)

            CoroutineScope(Dispatchers.IO).launch {
                val snapshot = list.toList()
                val answer = sendNvidiaChatMessage(snapshot, trimmed)
                if (!answer.isNullOrBlank()) {
                    list.add(WhatsAppNotificationListener.Companion.ChatMessage(answer, System.currentTimeMillis(), false))
                    if (list.size > MAX_MESSAGES_PER_CONTACT)
                        list.subList(0, list.size - MAX_MESSAGES_PER_CONTACT).clear()
                    withContext(Dispatchers.Main) { updateChatNotification(key, context) }
                } else {
                    withContext(Dispatchers.Main) {
                        showSimpleNotificationExtern("❌ NVIDIA Chat", "Antwort konnte nicht geladen werden.", context = context)
                    }
                }
            }
            return
        }

        val replyData = WhatsAppNotificationListener.replyActions[key]
        val supported = replyData?.pendingIntent?.creatorPackage?.let { isSupportedMessenger(it) } ?: false

        if (!supported) {
            Log.w("Messages", "Unsupported messenger: ${replyData?.pendingIntent?.creatorPackage}")
            showSimpleNotificationExtern(
                "⚠️ Nicht unterstützt",
                "Messenger wird nicht unterstützt: ${replyData?.pendingIntent?.creatorPackage}",
                20.seconds, context
            )
            return
        }

        try {
            val intent = Intent().apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            val bundle = Bundle().apply { putCharSequence(replyData!!.originalResultKey, messageText) }
            val ri = RemoteInput.Builder(replyData!!.originalResultKey)
                .setLabel(replyData.remoteInput.label)
                .setChoices(replyData.remoteInput.choices)
                .setAllowFreeFormInput(replyData.remoteInput.allowFreeFormInput)
                .build()
            RemoteInput.addResultsToIntent(arrayOf(ri), intent, bundle)
            replyData.pendingIntent.send(context, 0, intent)
        } catch (e: Exception) {
            Log.e("Messages", "Send failed: ${e.message}")
            showSimpleNotificationExtern("Fehler", "Nachricht konnte nicht gesendet werden", context = context)
            return
        }

        val list = WhatsAppNotificationListener.messagesByContact.getOrPut(key) { mutableListOf() }
        val isDup = list.takeLast(3).any { it.text == messageText && messageText.length > 5 }
        if (!isDup) {
            list.add(WhatsAppNotificationListener.Companion.ChatMessage(messageText, System.currentTimeMillis(), true))
        }

        // Sofort Notification updaten → beendet den Spinner
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
        for (i in 0 until 100) nm.cancel(hash + 10000 + i)
        nm.cancel(hash + 19999)
    } catch (e: Exception) {
        CoroutineScope(Dispatchers.IO).launch {
            ERRORINSERT(ERRORINSERTDATA("markMessageAsRead", "Error: ${e.message}", Instant.now().toString(), "Error"))
        }
    }
}

fun createNvidiaChat(name: String?, context: Context) {
    val title = if (name.isNullOrBlank()) NVIDIA_CHAT_PREFIX else "$NVIDIA_CHAT_PREFIX: $name"
    val list = WhatsAppNotificationListener.messagesByContact.getOrPut(title) { mutableListOf() }
    if (list.isEmpty()) {
        list.add(WhatsAppNotificationListener.Companion.ChatMessage(
            "Neuer NVIDIA-Chat \"$title\" erstellt. Antworte auf diese Nachricht, um zu schreiben.",
            System.currentTimeMillis(), false
        ))
    }
    updateChatNotification(title, context)
}

fun saveReplyDataPermanently(sender: String, context: Context) {
    try {
        val key = resolveKey(sender) ?: sender
        val replyData = WhatsAppNotificationListener.replyActions[key]
        if (replyData == null) {
            showSimpleNotificationExtern("❌ Fehler", "Keine Reply-Daten für '$sender' gefunden", context = context)
            return
        }
        val displayName = key.substringAfter("|")
        context.getSharedPreferences(PREFS_REPLY_DATA, MODE_PRIVATE).edit(commit = true) {
            putString(KEY_SAVED_SENDER, displayName)
            putString(KEY_SAVED_PACKAGE, replyData.pendingIntent.creatorPackage ?: "unknown")
            putString(KEY_SAVED_RESULT_KEY, replyData.originalResultKey)
            putBoolean(KEY_HAS_SAVED_DATA, true)
        }
        showSimpleNotificationExtern("✅ Gespeichert", "Reply-Daten für '$displayName' gespeichert", context = context)
    } catch (e: Exception) {
        showSimpleNotificationExtern("❌ Fehler", "Konnte Reply-Daten nicht speichern", context = context)
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
    } catch (e: Exception) { null }
}

fun sendMessageViaSavedReplyData(messageText: String, context: Context) {
    val savedData = loadSavedReplyData(context) ?: run {
        showSimpleNotificationExtern("❌ Keine Daten", "Keine gespeicherten Reply-Daten. Verwende 'save [kontakt]'.", context = context)
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
        showSimpleNotificationExtern("ℹ️ Info", "Keine gespeicherten Reply-Daten vorhanden", context = context)
        return
    }
    val nm = context.getSystemService(NotificationManager::class.java)
    if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
        nm.notify(50001, NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("💾 Gespeicherte Reply-Daten")
            .setContentText(savedData.sender)
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "Kontakt: ${savedData.sender}\nPackage: ${savedData.packageName}\nResult Key: ${savedData.resultKey}\n\nVerwende 'message [text]' zum Senden"
            ))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        )
    }
}

fun extractLastMessage(context: Context) {
    val msgs = WhatsAppNotificationListener.messagesByContact
    if (msgs.isEmpty()) {
        showSimpleNotificationExtern("❌ Keine Nachrichten", "Keine Nachrichten verfügbar", context = context)
        return
    }
    var newestMsg: WhatsAppNotificationListener.Companion.ChatMessage? = null
    var newestKey = ""
    msgs.forEach { (key, list) ->
        val last = list.lastOrNull()
        if (last != null && (newestMsg == null || last.timestamp > newestMsg!!.timestamp)) {
            newestMsg = last; newestKey = key
        }
    }
    val msg = newestMsg ?: return
    val displayName = if (newestKey.contains("|")) newestKey.substringAfter("|") else newestKey
    val timeText = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(msg.timestamp))
    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_menu_info_details)
        .setContentTitle("📋 Extrahierte Nachricht")
        .setContentText("Von: $displayName um $timeText")
        .setStyle(NotificationCompat.BigTextStyle().bigText("Von: $displayName\nZeit: $timeText\n\n${msg.text}"))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
    if (msg.imageUri != null) {
        try {
            val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, msg.imageUri))
            builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bmp).bigLargeIcon(null as Bitmap?))
            builder.setLargeIcon(bmp)
        } catch (e: Exception) { Log.e("Messages", "extractLastMessage image error: ${e.message}") }
    }
    val nm = context.getSystemService(NotificationManager::class.java)
    if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
        nm.notify(60000 + (msg.timestamp % 10000).toInt(), builder.build())
    }
}

// Tests
fun runMessagingTests(context: Context) {
    val TAG = "MessagingTest"
    val results = mutableListOf<TestResult>()
    val fakePackage = "com.whatsapp"
    val fakeSender = "Test Kontakt"
    val fakeKey = "$fakePackage|$fakeSender"
    val fakeTs1 = System.currentTimeMillis() - 60_000
    val fakeTs2 = System.currentTimeMillis() - 30_000
    val fakeTs3 = System.currentTimeMillis()

    val dummyIntent = PendingIntent.getBroadcast(
        context, 99999,
        Intent("com.cloud.TEST_DUMMY").apply { `package` = context.packageName },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )
    val fakeRemoteInput = RemoteInput.Builder("key_text_reply").setLabel("Antwort").setAllowFreeFormInput(true).build()
    val fakeReplyData = WhatsAppNotificationListener.Companion.ReplyData(dummyIntent, fakeRemoteInput, "key_text_reply")

    fun setup() {
        WhatsAppNotificationListener.messagesByContact[fakeKey] = mutableListOf(
            WhatsAppNotificationListener.Companion.ChatMessage("Hallo, wie geht's?", fakeTs1, false),
            WhatsAppNotificationListener.Companion.ChatMessage("Alles gut, danke!", fakeTs2, true),
            WhatsAppNotificationListener.Companion.ChatMessage("Noch eine neue Nachricht.", fakeTs3, false)
        )
        WhatsAppNotificationListener.replyActions[fakeKey] = fakeReplyData
    }

    fun teardown() {
        WhatsAppNotificationListener.messagesByContact.remove(fakeKey)
        WhatsAppNotificationListener.replyActions.remove(fakeKey)
        QuietHoursNotificationService.readMessageIds.removeAll { it.startsWith(fakeKey) }
    }

    fun test(name: String, expected: String, block: () -> Boolean) {
        val passed = try { block() } catch (e: Exception) {
            Log.e(TAG, "💥 $name threw: ${e.message}", e)
            results += TestResult(name, false, expected, "Exception: ${e.message}")
            return
        }
        Log.d(TAG, "${if (passed) "✅" else "❌"} $name | erwartet: $expected")
        results += TestResult(name, passed, expected, if (passed) "OK" else "FEHLGESCHLAGEN")
    }

    setup()
    test("resolveKey – voller Key", "gibt '$fakeKey' zurück") { resolveKey(fakeKey) == fakeKey }
    test("resolveKey – plain Sender", "gibt '$fakeKey' für '$fakeSender' zurück") { resolveKey(fakeSender) == fakeKey }
    test("resolveKey – unbekannter Sender", "null") { resolveKey("ExistiertNicht_XYZ") == null }
    test("resolveKey – NVIDIA Chat", "gibt 'NVIDIA Chat' zurück") { resolveKey("NVIDIA Chat") == "NVIDIA Chat" }
    teardown()

    setup()
    test("updateSingleSenderNotification – postet Notification", "Notification mit ID ${fakeKey.hashCode()} sichtbar") {
        updateSingleSenderNotification(fakeSender, context)
        context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == fakeKey.hashCode() }
    }
    teardown()
    test("updateSingleSenderNotification – kein Crash bei leerem State", "kein Crash") {
        updateSingleSenderNotification("NichtVorhanden_XYZ", context); true
    }

    setup()
    val sentText = "Testnachricht_${System.currentTimeMillis()}"
    test("handleMessageSent – Nachricht in messagesByContact", "ChatMessage mit isOwnMessage=true") {
        WhatsAppNotificationListener.messagesByContact.getOrPut(fakeKey) { mutableListOf() }
            .add(WhatsAppNotificationListener.Companion.ChatMessage(sentText, System.currentTimeMillis(), true))
        WhatsAppNotificationListener.messagesByContact[fakeKey]?.any { it.text == sentText && it.isOwnMessage } == true
    }
    test("handleMessageSent – Duplikat-Schutz", "Nachricht nur einmal in Liste") {
        val before = WhatsAppNotificationListener.messagesByContact[fakeKey]?.count { it.text == sentText } ?: 0
        handleMessageSent(fakeKey, sentText, context)
        val after = WhatsAppNotificationListener.messagesByContact[fakeKey]?.count { it.text == sentText } ?: 0
        after == before
    }
    test("handleMessageSent – Notification aktualisiert", "Notification mit ID ${fakeKey.hashCode()} vorhanden") {
        updateChatNotification(fakeKey, context)
        context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == fakeKey.hashCode() }
    }
    teardown()

    setup()
    test("showUnreadMessages – Notification vorhanden", "Notification mit ID ${fakeKey.hashCode()} nach Aufruf") {
        showUnreadMessages(context)
        context.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == fakeKey.hashCode() }
    }
    teardown()
    test("showUnreadMessages – leer → kein Crash", "kein Crash") { showUnreadMessages(context); true }

    setup()
    val fakeMsgId = "${fakeKey}_$fakeTs1"
    test("markMessageAsRead – ID in readMessageIds", "readMessageIds enthält '$fakeMsgId'") {
        markMessageAsRead(fakeMsgId, QuietHoursNotificationService.readMessageIds, context)
        QuietHoursNotificationService.readMessageIds.contains(fakeMsgId)
    }
    test("markMessageAsRead – Part-Notifications gecancelt", "partId nicht mehr aktiv") {
        val nm = context.getSystemService(NotificationManager::class.java)
        val partId = fakeKey.hashCode() + 10000
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            nm.notify(partId, NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("Test Part").setContentText("wird gecancelt").build())
        }
        markMessageAsRead("${fakeKey}_$fakeTs2", QuietHoursNotificationService.readMessageIds, context)
        !nm.activeNotifications.any { it.id == partId }
    }
    teardown()

    val oldKey = "$fakePackage|Alter Kontakt"
    WhatsAppNotificationListener.messagesByContact[oldKey] = mutableListOf(
        WhatsAppNotificationListener.Companion.ChatMessage("Alte Nachricht", System.currentTimeMillis() - (25 * 60 * 60 * 1000), false),
        WhatsAppNotificationListener.Companion.ChatMessage("Neue Nachricht", System.currentTimeMillis(), false)
    )
    cleanupOldMessages()
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        test("cleanupOldMessages – alte entfernt", "nur 'Neue Nachricht' übrig") {
            val m = WhatsAppNotificationListener.messagesByContact[oldKey]
            m?.size == 1 && m.first().text == "Neue Nachricht"
        }
        val emptyKey = "$fakePackage|Leerer Kontakt"
        WhatsAppNotificationListener.messagesByContact[emptyKey] = mutableListOf(
            WhatsAppNotificationListener.Companion.ChatMessage("Sehr alte", System.currentTimeMillis() - (25 * 60 * 60 * 1000), false)
        )
        cleanupOldMessages()
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            test("cleanupOldMessages – leere Kontakte entfernt", "Key '$emptyKey' weg") {
                !WhatsAppNotificationListener.messagesByContact.containsKey(emptyKey)
            }
            WhatsAppNotificationListener.messagesByContact.remove(oldKey)
            postTestSummary(results, context, TAG)
        }, 300)
    }, 300)
}

private data class TestResult(val name: String, val passed: Boolean, val expected: String, val actual: String)

private fun postTestSummary(results: List<TestResult>, context: Context, TAG: String) {
    val passed = results.count { it.passed }
    val failed = results.count { !it.passed }
    val total = results.size
    val summaryLine = "✅ $passed/$total bestanden" + if (failed > 0) "  ❌ $failed fehlgeschlagen" else ""
    val detail = results.joinToString("\n") { r -> "${if (r.passed) "✅" else "❌"} ${r.name}\n     → ${r.actual}" }
    Log.d(TAG, "═══ TEST SUMMARY ═══")
    Log.d(TAG, summaryLine)
    results.forEach { r -> Log.d(TAG, "${if (r.passed) "✅" else "❌"} ${r.name} | ${r.expected} | ${r.actual}") }
    Log.d(TAG, "════════════════════")
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
        context.getSystemService(NotificationManager::class.java).notify(77777,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("🧪 Messaging Tests: $summaryLine")
                .setContentText(if (failed == 0) "Alle Tests bestanden!" else "$failed Test(s) fehlgeschlagen")
                .setStyle(NotificationCompat.BigTextStyle().bigText(detail).setSummaryText(summaryLine))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()
        )
    }
}