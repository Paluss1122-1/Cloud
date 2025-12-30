package com.example.cloud.service

import android.Manifest
//noinspection SuspiciousImport
import android.R
import android.app.Activity
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.RemoteInput
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.example.cloud.MyDeviceAdminReceiver
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.core.net.toUri
import com.example.cloud.SupabaseConfig
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class QuietHoursNotificationService : Service() {

    val context = this

    var uploadedCount = 0
    var failedCount = 0

    companion object {
        private const val CHANNEL_ID = "quiet_hours_channel"
        private const val NOTIFICATION_ID = 999999

        private const val ACTION_SHOW_MESSAGES = "com.example.cloud.ACTION_SHOW_MESSAGES"
        private const val ACTION_OPEN_SETTINGS = "com.example.cloud.ACTION_OPEN_SETTINGS"

        private const val ACTION_MESSAGE_SENT = "com.example.cloud.ACTION_MESSAGE_SENT"
        private const val EXTRA_SENDER = "extra_sender"
        private const val CONFIRMATION_CHANNEL_ID = "message_confirmation_channel"

        private lateinit var sharedPreferences: SharedPreferences
        private const val ACTION_OPEN_MUSIC_PLAYER = "com.example.cloud.ACTION_OPEN_MUSIC_PLAYER"
        private const val ACTION_RESTART_MUSIC_PLAYER =
            "com.example.cloud.ACTION_RESTART_MUSIC_PLAYER"

        private const val ACTION_NOTIFICATION_DISMISSED =
            "com.example.cloud.ACTION_NOTIFICATION_DISMISSED"

        private const val ACTION_CHANGE_START = "com.example.cloud.ACTION_CHANGE_START"
        private const val ACTION_CHANGE_END = "com.example.cloud.ACTION_CHANGE_END"
        private const val ACTION_CHECK_LINK_TO_WINDOWS =
            "com.example.cloud.ACTION_CHECK_LINK_TO_WINDOWS"
        private const val ACTION_CHECK_DISCORD = "com.example.cloud.ACTION_CHECK_DISCORD"

        private const val ACTION_UNHIDE_AND_START = "com.example.cloud.ACTION_UNHIDE_AND_START"

        private const val ACTION_PLAY_VOICE_NOTE = "com.example.cloud.ACTION_PLAY_VOICE_NOTE"
        private const val ACTION_NEXT_VOICE_NOTE = "com.example.cloud.ACTION_NEXT_VOICE_NOTE"
        private const val ACTION_PREV_VOICE_NOTE = "com.example.cloud.ACTION_PREV_VOICE_NOTE"
        private const val ACTION_STOP_VOICE_NOTE = "com.example.cloud.ACTION_STOP_VOICE_NOTE"
        private const val EXTRA_SENDER_FOR_VOICE = "extra_sender_for_voice"

        private const val VOICE_NOTE_CHANNEL_ID = "voice_note_player_channel"
        private const val ACTION_EXECUTE_COMMAND = "com.example.cloud.ACTION_EXECUTE_COMMAND"

        private const val PREFS_REPLY_DATA = "reply_data_prefs"
        private const val KEY_SAVED_SENDER = "saved_sender"
        private const val KEY_SAVED_PACKAGE = "saved_package"
        private const val KEY_SAVED_RESULT_KEY = "saved_result_key"
        private const val KEY_HAS_SAVED_DATA = "has_saved_data"

        private val commandHistory = mutableListOf<String>()

        private const val ACTION_SHOW_GALLERY = "com.example.cloud.ACTION_SHOW_GALLERY"
        private const val ACTION_NEXT_GALLERY_IMAGE = "com.example.cloud.ACTION_NEXT_GALLERY_IMAGE"
        private const val ACTION_PREV_GALLERY_IMAGE = "com.example.cloud.ACTION_PREV_GALLERY_IMAGE"
        private const val GALLERY_CHANNEL_ID = "gallery_channel"

        fun startService(context: Context) {
            val intent = Intent(context, QuietHoursNotificationService::class.java)
            context.startForegroundService(intent)
        }

        fun updateSingleSenderNotification(context: Context, sender: String) {
            val intent = Intent(context, QuietHoursNotificationService::class.java).apply {
                action = "ACTION_UPDATE_SINGLE_SENDER"
                putExtra("EXTRA_SENDER", sender)
            }
            context.startService(intent)
        }
    }

    data class Command(
        val name: String,
        val aliases: List<String> = emptyList(),
        val description: String,
        val action: () -> Unit
    )

    data class SavedReplyData(
        val sender: String,
        val packageName: String,
        val resultKey: String
    )

    private val handler = Handler(Looper.getMainLooper())
    private var isCurrentlyQuietHours = false

    private var voiceNotePlayer: MediaPlayer? = null
    private var currentVoiceNoteIndex = 0
    private var voiceNoteFiles: List<File> = emptyList()
    private var currentSenderForVoiceNote: String? = null
    private var galleryImages: List<android.net.Uri> = emptyList()
    private var currentGalleryIndex = 0

    private fun saveReplyDataPermanently(sender: String) {
        try {
            val replyData = WhatsAppNotificationListener.replyActions[sender]

            if (replyData == null) {
                showSimpleNotification(
                    "❌ Fehler",
                    "Keine Reply-Daten für '$sender' gefunden"
                )
                return
            }

            val packageName = replyData.pendingIntent.creatorPackage ?: "unknown"
            val resultKey = replyData.originalResultKey

            val prefs = getSharedPreferences(PREFS_REPLY_DATA, MODE_PRIVATE)
            prefs.edit {
                putString(KEY_SAVED_SENDER, sender)
                putString(KEY_SAVED_PACKAGE, packageName)
                putString(KEY_SAVED_RESULT_KEY, resultKey)
                putBoolean(KEY_HAS_SAVED_DATA, true)
            }

            showSimpleNotification(
                "✅ Gespeichert",
                "Reply-Daten für '$sender' wurden permanent gespeichert"
            )

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error saving reply data", e)
            showSimpleNotification("❌ Fehler", "Konnte Reply-Daten nicht speichern")
        }
    }

    private fun loadSavedReplyData(): SavedReplyData? {
        try {
            val prefs = getSharedPreferences(PREFS_REPLY_DATA, MODE_PRIVATE)

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

    private fun sendMessageViaSavedReplyData(messageText: String) {
        try {
            val savedData = loadSavedReplyData()

            if (savedData == null) {
                showSimpleNotification(
                    "❌ Keine Daten",
                    "Keine gespeicherten Reply-Daten vorhanden. Verwende 'save [kontakt]' zuerst."
                )
                return
            }

            val currentReplyData = WhatsAppNotificationListener.replyActions[savedData.sender]

            if (currentReplyData != null) {
                sendMessageToWhatsApp(savedData.sender, messageText, currentReplyData)
            } else {
                try {
                    val intent = Intent().apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        setPackage(savedData.packageName)
                    }

                    val bundle = Bundle().apply {
                        putCharSequence(savedData.resultKey, messageText)
                    }

                    val reconstructedRemoteInput = RemoteInput.Builder(savedData.resultKey)
                        .setLabel("Antwort")
                        .setAllowFreeFormInput(true)
                        .build()

                    RemoteInput.addResultsToIntent(
                        arrayOf(reconstructedRemoteInput),
                        intent,
                        bundle
                    )

                    showSimpleNotification(
                        "❌ Senden fehlgeschlagen",
                        "Keine aktive Notification von ${savedData.sender}. Warte auf neue Nachricht.",
                        20.seconds
                    )

                    Log.w(
                        "QuietHoursService",
                        "Cannot send without current PendingIntent - saved data alone is not enough"
                    )

                } catch (e: Exception) {
                    Log.e("QuietHoursService", "Failed to reconstruct reply intent", e)
                    showSimpleNotification(
                        "❌ Fehler",
                        "Nachricht konnte nicht gesendet werden: ${e.message}"
                    )
                }
            }

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error sending message via saved data", e)
            showSimpleNotification("❌ Fehler", "Nachricht konnte nicht gesendet werden")
        }
    }

    private fun sendMessageToWhatsApp(
        sender: String,
        messageText: String,
        replyData: WhatsAppNotificationListener.Companion.ReplyData
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

            replyData.pendingIntent.send(this, 0, intent)

            val messagesList =
                WhatsAppNotificationListener.messagesByContact.getOrPut(sender) { mutableListOf() }

            val isDuplicate = messagesList.takeLast(3).any { existingMsg ->
                existingMsg.text == messageText &&
                        existingMsg.isOwnMessage &&
                        (System.currentTimeMillis() - existingMsg.timestamp) < 2000
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

            showSimpleNotification(
                "✅ Gesendet",
                "Nachricht an '$sender': $messageText"
            )

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error sending to WhatsApp", e)
            showSimpleNotification("❌ Fehler", "WhatsApp-Versand fehlgeschlagen")
        }
    }

    private fun showSavedReplyInfo() {
        val savedData = loadSavedReplyData()

        if (savedData == null) {
            showSimpleNotification(
                "ℹ️ Info",
                "Keine gespeicherten Reply-Daten vorhanden"
            )
            return
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dialog_info)
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

        val notificationManager = getSystemService(NotificationManager::class.java)
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(50001, notification)
        }
    }


    private fun getAvailableCommands(): List<Command> {
        return listOf(
            Command(
                name = "whatsapp",
                aliases = listOf("wh", "wa", "messages", "msg", "nachrichten"),
                description = "Zeigt ungelesene Nachrichten"
            ) {
                showUnreadMessages()
            },
            Command(
                name = "music",
                aliases = listOf("m", "play", "player", "musik"),
                description = "Startet Musik Player"
            ) {
                restartMusicPlayer()
            },
            Command(
                name = "podcast",
                aliases = listOf("pd", "pc", "Podcast"),
                description = "Startet PodcastPlayerService"
            ) {
                PodcastPlayerService.startService(this)
                PodcastPlayerService.sendPlayAction(this)
            },
            Command(
                name = "cloud",
                aliases = listOf("app", "start", "unhide"),
                description = "Startet Cloud App"
            ) {
                unhideAndStartLinkToWindows()
            },
            Command(
                name = "voice",
                aliases = listOf("v", "voicenote", "sprachnachricht", "audio"),
                description = "Spielt Voice Notes ab"
            ) {
                playLatestVoiceNote("Manual Command")
            },
            Command(
                name = "help",
                aliases = listOf("h", "?", "commands", "befehle"),
                description = "Zeigt alle verfügbaren Befehle"
            ) {
                showAvailableCommands()
            },
            Command(
                name = "flashlevel",
                aliases = listOf("flashl", "lightlevel", "torchlevel", "helligkeit", "flash", "f"),
                description = "Setze Taschenlampen-Helligkeit (Syntax: flashlevel [1-max])"
            ) {
                showSimpleNotification(
                    "ℹ️ Flashlight Level",
                    "Syntax: flashlevel [1-max]",
                    20.seconds
                )
            },
            Command(
                name = "save",
                aliases = listOf("s", "speichern", "store"),
                description = "Speichert Reply-Daten für Kontakt"
            ) {
                val contacts = WhatsAppNotificationListener.replyActions.keys.toList()
                if (contacts.isEmpty()) {
                    showSimpleNotification(
                        "❌ Keine Kontakte",
                        "Keine Reply-Daten verfügbar. Warte auf WhatsApp-Nachricht.",
                        20.seconds
                    )
                } else {
                    showSimpleNotification(
                        "📋 Verfügbare Kontakte",
                        "Verwende 'save [kontakt]': ${contacts.joinToString(", ")}"
                    )
                }
            },
            Command(
                name = "saved",
                aliases = listOf("info", "gespeichert", "data"),
                description = "Zeigt gespeicherte Reply-Daten"
            ) {
                showSavedReplyInfo()
            },
            Command(
                name = "message",
                aliases = listOf("send", "senden", "write", "schreiben"),
                description = "Sendet Nachricht an gespeicherten Kontakt"
            ) {
                showSimpleNotification(
                    "ℹ️ Verwendung",
                    "Syntax: message [deine nachricht]"
                )
            },
            Command(
                name = "stopmusic",
                aliases = listOf("stopm", "musicstop"),
                description = "Stoppt NUR Musik Player Service"
            ) {
                try {
                    val musicIntent = Intent(this, MusicPlayerService::class.java)

                    stopService(musicIntent)

                    showSimpleNotification(
                        "✅ Musik Player gestoppt",
                        "Nur Music Player wurde gestoppt"
                    )
                } catch (e: Exception) {
                    Log.e("QuietHoursService", "Error in stopmusic command", e)
                    showSimpleNotification(
                        "❌ Fehler",
                        "Music Player konnte nicht gestoppt werden: ${e.message}"
                    )
                }
            },
            Command(
                name = "stoppodcast",
                aliases = listOf("stopp", "podcaststop"),
                description = "Stoppt NUR Podcast Player Service"
            ) {
                try {
                    Log.d("QuietHoursService", "=== STOPPODCAST COMMAND STARTED ===")

                    val podcastIntent = Intent(this, PodcastPlayerService::class.java)

                    stopService(podcastIntent)

                    showSimpleNotification(
                        "✅ Podcast Player gestoppt",
                        "Nur Podcast Player wurde gestoppt"
                    )
                } catch (e: Exception) {
                    Log.e("QuietHoursService", "Error in stoppodcast command", e)
                    showSimpleNotification(
                        "❌ Fehler",
                        "Podcast Player konnte nicht gestoppt werden: ${e.message}"
                    )
                }
            },
            Command(
                name = "weather",
                aliases = listOf("w", "wetter", "forecast"),
                description = "Zeigt Wetter (Syntax: weather [0-2] [0-23])"
            ) {
                showSimpleNotification(
                    "ℹ️ Wetter",
                    "Syntax: weather [0=Heute, 1=Morgen, 2=Übermorgen] [0-23]"
                )
            },
            Command(
                name = "extract",
                aliases = listOf("e", "ex", "extrahieren"),
                description = "Zeigt letzte Nachricht als separate Notification"
            ) {
                extractLastMessage()
            },
            Command(
                name = "timecontrol1",
                aliases = listOf("tc1", "time1", "t1"),
                description = "Startet TimeControl via ComponentName (Methode 1)"
            ) {
                startTimeControlMethod1()
            },

            Command(
                name = "timecontrol3",
                aliases = listOf("tc3", "time3", "t3"),
                description = "Startet TimeControl via PackageManager Enable (Methode 3)"
            ) {
                startTimeControlMethod3()
            },
            Command(
                name = "timecontrol10",
                aliases = listOf("tc10", "time10", "t10"),
                description = "Startet TimeControl via setApplicationEnabledSetting (Methode 10)"
            ) {
                startTimeControlMethod10()
            },
            Command(
                name = "gallerie",
                aliases = listOf("gal", "g", "gallery"),
                description = "Zeigt Gallerie als nots"
            ) {
                showGallery()
            },
            Command(
                name = "uploadsnap",
                aliases = listOf("snap", "upload", "snapupload"),
                description = "Lädt neue Snap-Bilder zu Supabase hoch"
            ) {
                uploadSnapImages()
            },
            Command(
                name = "setdowntime",
                aliases = listOf("set", "dt", "setdr"),
                description = "Lege Downtime fest (setdowntime [Uhrzeit])"
            ) {},
            Command(
                name = "failed",
                aliases = listOf(),
                description = "Zeigt Anzahl fehlgeschlagener Uploads"
            ) {
                showSimpleNotification(
                    "Failed Uploads",
                    "$failedCount"
                )
            },
            Command(
                name = "success",
                aliases = listOf("suc"),
                description = "Zeigt Anzahl erfolgreicher Uploads"
            ) {
                showSimpleNotification(
                    "Successful Uploads",
                    "$uploadedCount"
                )
            },
            Command(
                name = "friendmessages",
                aliases = listOf("fm", "friendmsgs", "lastmsgs", "friend"),
                description = "Zeigt letzte 10 Nachrichten von friend"
            ) {
                showLastFriendMessages()
            },
            Command(
                name = "friendmessages",
                aliases = listOf("fm", "friendmsgs", "lastmsgs", "friend"),
                description = "Zeigt letzte 10 Nachrichten von friend"
            ) {
                try {
                    val chatService = ChatService()
                    val notification = chatService.createServiceNotification()

                    val notificationManager = getSystemService(NotificationManager::class.java)
                    notificationManager.notify(66666, notification)
                } catch (e: Exception) {
                    Log.e("QuietHoursService", "Error showing chat notification", e)
                    showSimpleNotification("❌ Fehler", "Konnte Notification nicht anzeigen")
                }
            },
            Command(
                name = "bluetooth",
                aliases = listOf("bt", "connect", "headphones", "kopfhörer"),
                description = "Verbindet mit gespeicherten Bluetooth-Kopfhörern"
            ) {
                connectToBluetoothHeadphones()
            }
        )
    }

    private fun extractLastMessage() {
        try {
            val messagesByContact = WhatsAppNotificationListener.messagesByContact

            if (messagesByContact.isEmpty()) {
                showSimpleNotification(
                    "❌ Keine Nachrichten",
                    "Keine Nachrichten zum Extrahieren verfügbar"
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
                showSimpleNotification(
                    "❌ Fehler",
                    "Keine Nachricht gefunden"
                )
                return
            }

            val message = newestMessage
            val sender = newestSender
            val timeText =
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(message.timestamp))

            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_menu_info_details)
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
                            contentResolver,
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

            val notificationManager = getSystemService(NotificationManager::class.java)
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(
                    60000 + (message.timestamp % 10000).toInt(),
                    builder.build()
                )

                showSimpleNotification(
                    "✅ Extrahiert",
                    "Nachricht von $sender wurde als separate Notification angezeigt"
                )
            }

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error extracting last message", e)
            showSimpleNotification(
                "❌ Fehler",
                "Nachricht konnte nicht extrahiert werden: ${e.message}"
            )
        }
    }

    private val messageSentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_MESSAGE_SENT) {
                val sender = intent.getStringExtra(EXTRA_SENDER) ?: return

                val replyText = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence("key_text_reply")?.toString()

                setResultCode(Activity.RESULT_OK)

                if (replyText != null && context != null) {
                    Handler(Looper.getMainLooper()).post {
                        handleMessageSent(sender, replyText)
                    }
                }
            }
        }
    }

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_EXECUTE_COMMAND) {
                val commandText = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence("key_command_input")?.toString()

                val notification = createNotification(isCurrentlyQuietHours)
                startForeground(NOTIFICATION_ID, notification)

                if (commandText != null && context != null) {
                    Handler(Looper.getMainLooper()).post {
                        executeCommand(commandText.trim())
                    }
                }
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun executeCommand(commandText: String) {
        val actualCommand = when (commandText) {
            "^" -> {
                if (commandHistory.isEmpty()) {
                    showSimpleNotification(
                        "❌ Keine History",
                        "Kein vorheriger Befehl verfügbar",
                        20.seconds
                    )
                    return
                }
                commandHistory.last()
            }

            "^^" -> {
                if (commandHistory.size < 2) {
                    showSimpleNotification(
                        "❌ Keine History",
                        "Kein vorletzter Befehl verfügbar",
                        20.seconds
                    )
                    return
                }
                commandHistory[commandHistory.size - 2]
            }

            else -> commandText
        }

        if (commandText != "^" && commandText != "^^") {
            commandHistory.add(actualCommand)
            if (commandHistory.size > 10) {
                commandHistory.removeAt(0)
            }
        }

        val parts = actualCommand.split(" ", limit = 2)
        val commandInput = parts[0].lowercase()
        val argument = if (parts.size > 1) parts[1] else null

        when (commandInput) {
            "save", "s", "speichern", "store" -> {
                if (argument != null) {
                    saveReplyDataPermanently(argument)
                } else {
                    val contacts = WhatsAppNotificationListener.replyActions.keys.toList()
                    if (contacts.isEmpty()) {
                        showSimpleNotification(
                            "❌ Keine Kontakte",
                            "Keine Reply-Daten verfügbar",
                            20.seconds
                        )
                    } else {
                        showSimpleNotification(
                            "📋 Kontakte",
                            contacts.joinToString(", ")
                        )
                    }
                }
                return
            }

            "message", "send", "senden", "write", "schreiben" -> {
                if (argument != null) {
                    sendMessageViaSavedReplyData(argument)
                } else {
                    showSimpleNotification(
                        "❌ Fehler",
                        "Syntax: message [deine nachricht]",
                        20.seconds
                    )
                }
                return
            }

            "saved", "info", "gespeichert", "data" -> {
                showSavedReplyInfo()
                return
            }

            "weather", "w", "wetter", "forecast" -> {
                if (argument != null) {
                    val parts = argument.split(" ")
                    if (parts.size == 2) {
                        val dayNum = parts[0].toIntOrNull()
                        val hour = parts[1]

                        val day = when (dayNum) {
                            0 -> "heute"
                            1 -> "morgen"
                            2 -> "übermorgen"
                            else -> null
                        }

                        if (day != null && dayNum in 0..2) {
                            Handler(Looper.getMainLooper()).post {
                                kotlinx.coroutines.GlobalScope.launch {
                                    try {
                                        val loc =
                                            com.example.cloud.weathertab.getLastKnownLocation(this@QuietHoursNotificationService)
                                        if (loc == null) {
                                            showSimpleNotification(
                                                "❌ Standort-Fehler",
                                                "Standort nicht verfügbar",
                                                20.seconds
                                            )
                                            return@launch
                                        }

                                        val weatherData =
                                            com.example.cloud.weathertab.fetchWeatherForecast(
                                                loc.latitude,
                                                loc.longitude,
                                                days = 14
                                            )

                                        com.example.cloud.weathertab.weathernot(
                                            this@QuietHoursNotificationService,
                                            day,
                                            hour,
                                            weatherData
                                        )

                                    } catch (e: Exception) {
                                        Log.e("QuietHoursService", "Weather fetch error", e)
                                        showSimpleNotification(
                                            "❌ Wetter-Fehler",
                                            "Wetterdaten konnten nicht abgerufen werden: ${e.message}",
                                            20.seconds
                                        )
                                    }
                                }
                            }
                        } else {
                            showSimpleNotification(
                                "❌ Ungültiger Tag",
                                "Tag muss 0, 1 oder 2 sein",
                                20.seconds
                            )
                        }
                    } else {
                        showSimpleNotification(
                            "❌ Fehler",
                            "Syntax: weather [0=Heute, 1=Morgen, 2=Übermorgen] [0-23]",
                            20.seconds
                        )
                    }
                } else {
                    showSimpleNotification(
                        "ℹ️ Wetter",
                        "Syntax: weather [0=Heute, 1=Morgen, 2=Übermorgen] [0-23]",
                        20.seconds
                    )
                }
                return
            }

            "flashlevel", "flashl", "lightlevel", "torchlevel", "helligkeit", "flash", "f" -> {
                if (argument != null) {
                    val level = argument.toIntOrNull()
                    if (level != null && level >= 1) {
                        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
                        try {
                            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return

                            val clampedLevel = level.coerceIn(1, 5)
                            cameraManager.turnOnTorchWithStrengthLevel(cameraId, clampedLevel)
                        } catch (e: Exception) {
                            Log.e("QuietHoursService", "Error setting flashlight level", e)
                            showSimpleNotification(
                                "❌ Taschenlampe",
                                "Helligkeit konnte nicht gesetzt werden: ${e.message}",
                                20.seconds
                            )
                        }
                    } else if (level != null && level == 0) {
                        val cameraManager =
                            getSystemService(CAMERA_SERVICE) as CameraManager
                        try {
                            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return
                            cameraManager.setTorchMode(cameraId, false)
                        } catch (e: Exception) {
                            Log.e("QuietHoursService", "Error setting flashlight", e)
                            showSimpleNotification(
                                "❌ Taschenlampe",
                                "Taschenlampe konnte nicht geschaltet werden"
                            )
                        }
                    } else {
                        showSimpleNotification(
                            "❌ Ungültiger Wert",
                            "Bitte eine Zahl >= 1 eingeben",
                            20.seconds
                        )
                    }
                } else {
                    showFlashlightLevelInfo()
                }
                return
            }

            "setdowntime", "set", "dt", "setdt" -> {
                if (argument !== null) {
                    context.getSharedPreferences("quick_settings_prefs", MODE_PRIVATE)
                        .edit(commit = true) { putString("saved_number", argument) }
                } else {
                    showSimpleNotification(
                        "Setdowntime",
                        "setdowntime [Uhrzeit]"
                    )
                }
            }

            "bluetooth", "bt", "connect", "headphones", "kopfhörer" -> {
                when {
                    argument == null -> {
                        connectToBluetoothHeadphones()
                    }
                    argument.equals("save", ignoreCase = true) -> {
                        saveBluetoothHeadphones()
                    }
                    argument.equals("info", ignoreCase = true) -> {
                        showBluetoothInfo()
                    }
                    argument.startsWith("save ", ignoreCase = true) -> {
                        val macAddress = argument.substring(5).trim()
                        val prefs = getSharedPreferences("bluetooth_prefs", MODE_PRIVATE)
                        prefs.edit {
                            putString("headphone_mac", macAddress)
                        }
                        showSimpleNotification(
                            "✅ MAC gespeichert",
                            "Kopfhörer-Adresse: $macAddress"
                        )
                    }
                    else -> {
                        showSimpleNotification(
                            "ℹ️ Bluetooth Befehle",
                            "bluetooth - Verbinden\nbluetooth save - Gerät speichern\nbluetooth info - Info anzeigen"
                        )
                    }
                }
                return
            }
        }

        val commands = getAvailableCommands()

        val matchedCommand = commands.find { cmd ->
            cmd.name.equals(commandInput, ignoreCase = true) ||
                    cmd.aliases.any { it.equals(commandInput, ignoreCase = true) }
        }

        if (matchedCommand != null) {
            try {
                matchedCommand.action()
            } catch (e: Exception) {
                Log.e("QuietHoursService", "Error executing command", e)
                showSimpleNotification(
                    "❌ Fehler",
                    "Befehl '${matchedCommand.name}' konnte nicht ausgeführt werden",
                    20.seconds
                )
            }
        } else {
            val suggestions = commands.filter { cmd ->
                cmd.name.contains(commandInput, ignoreCase = true) ||
                        commandInput.contains(cmd.name, ignoreCase = true) ||
                        cmd.aliases.any { alias ->
                            alias.contains(commandInput, ignoreCase = true) ||
                                    commandInput.contains(alias, ignoreCase = true)
                        }
            }

            if (suggestions.isNotEmpty()) {
                val suggestionText = suggestions.joinToString(", ") { cmd ->
                    if (cmd.aliases.isNotEmpty()) {
                        "${cmd.name} (${cmd.aliases.take(2).joinToString(", ")})"
                    } else {
                        cmd.name
                    }
                }
                showSimpleNotification(
                    "❓ Unbekannter Befehl",
                    "Meintest du: $suggestionText?",
                    20.seconds
                )
            } else {
                showSimpleNotification(
                    "❌ Unbekannter Befehl",
                    "'$commandInput' nicht gefunden. Verwende 'help' für alle Befehle.",
                    20.seconds
                )
            }
        }
    }

    private fun showAvailableCommands() {
        val commands = getAvailableCommands()

        val chunked = commands.chunked(5)

        chunked.forEachIndexed { index, chunk ->
            val commandList = chunk
                .filter { it.name != "help" }
                .joinToString("\n") { cmd ->
                    if (cmd.aliases.isNotEmpty()) {
                        "• ${cmd.name} (${
                            cmd.aliases.take(3).joinToString(", ")
                        }) - ${cmd.description}"
                    } else {
                        "• ${cmd.name} - ${cmd.description}"
                    }
                }

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_menu_help)
                .setContentTitle("📋 Verfügbare Befehle (Seite ${index + 1}/${chunked.size})")
                .setContentText("${chunk.size} Befehle")
                .setStyle(NotificationCompat.BigTextStyle().bigText(commandList))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.notify(50000 + index, notification)
        }
    }

    private val notificationDismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_NOTIFICATION_DISMISSED) {
                val notificationId = intent.getIntExtra("notification_id", -1)

                if (notificationId == NOTIFICATION_ID) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        val notification = createNotification(isCurrentlyQuietHours)
                        startForeground(NOTIFICATION_ID, notification)
                    }, 100)
                }
            }
        }
    }

    private val timeChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context == null || intent == null) return

            val newTime = RemoteInput.getResultsFromIntent(intent)
                ?.getCharSequence("key_time_input")?.toString()

            if (newTime != null) {
                val timeValue = newTime.toIntOrNull()

                if (timeValue != null && timeValue in 0..23) {
                    val prefs = getSharedPreferences("quick_settings_prefs", MODE_PRIVATE)

                    when (intent.action) {
                        ACTION_CHANGE_START -> {
                            prefs.edit {
                                putString(
                                    "saved_number_start",
                                    timeValue.toString()
                                ).commit()
                            }
                            showSimpleNotification(
                                "✓ Startzeit geändert",
                                "Neue Startzeit: $timeValue:00 Uhr"
                            )
                        }

                        ACTION_CHANGE_END -> {
                            prefs.edit { putString("saved_number", timeValue.toString()).commit() }
                            showSimpleNotification(
                                "✓ Endzeit geändert",
                                "Neue Endzeit: $timeValue:00 Uhr"
                            )
                        }
                    }

                    setResultCode(Activity.RESULT_OK)
                } else {
                    showSimpleNotification(
                        "❌ Ungültige Zeit",
                        "Bitte eine Zahl zwischen 0 und 23 eingeben"
                    )
                }
            }
        }
    }

    private val checkRunnable = Runnable {
        checkQuietHours()
        scheduleNextCheck()
    }

    private fun scheduleNextCheck() {
        val now = Calendar.getInstance()

        val quietStart = getQuietStartHour()
        val quietEnd = getQuietEndHour()

        val nextChange = Calendar.getInstance()
        nextChange.set(Calendar.SECOND, 0)
        nextChange.set(Calendar.MILLISECOND, 0)

        if (isCurrentlyQuietHours) {
            nextChange.set(Calendar.HOUR_OF_DAY, quietEnd)
            nextChange.set(Calendar.MINUTE, 0)

            if (nextChange.timeInMillis <= now.timeInMillis) {
                nextChange.add(Calendar.DAY_OF_YEAR, 1)
            }
        } else {
            nextChange.set(Calendar.HOUR_OF_DAY, quietStart)
            nextChange.set(Calendar.MINUTE, 0)

            if (nextChange.timeInMillis <= now.timeInMillis) {
                nextChange.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val timeToNextChange = nextChange.timeInMillis - now.timeInMillis

        val delay: Long = if (timeToNextChange > 3 * 60 * 1000) {
            val calculatedDelay = timeToNextChange - (2 * 60 * 1000)
            calculatedDelay
        } else {
            60 * 1000L
        }

        handler.postDelayed(checkRunnable, delay)

        val nextCheckTime = Calendar.getInstance()
        nextCheckTime.timeInMillis = now.timeInMillis + delay
    }

    private fun getQuietStartHour(): Int {
        val prefs = getSharedPreferences("quick_settings_prefs", MODE_PRIVATE)
        val start = prefs.getString("saved_number_start", null)?.toIntOrNull() ?: 21
        return start
    }

    private fun getQuietEndHour(): Int {
        val prefs = getSharedPreferences("quick_settings_prefs", MODE_PRIVATE)
        val end = prefs.getString("saved_number", null)?.toIntOrNull() ?: 7
        return end
    }

    private fun checkAndShowLinkToWindowsStatus() {
        try {
            val status = getLinkToWindowsStatus(this, "com.microsoft.appmanager")

            val title: String
            val message: String
            val icon: Int

            when {
                !status.isInstalled -> {
                    title = "❌ Link to Windows"
                    message = "App ist nicht installiert"
                    icon = R.drawable.ic_dialog_alert
                }

                status.isHidden -> {
                    title = "🚫 Link to Windows versteckt"
                    message = "Die App wurde durch das System versteckt"
                    icon = R.drawable.ic_menu_close_clear_cancel
                }

                !status.isEnabled -> {
                    title = "⏸️ Link to Windows deaktiviert"
                    message = "Die App ist installiert aber deaktiviert"
                    icon = R.drawable.ic_media_pause
                }

                !status.isVisibleInLauncher -> {
                    title = "👁️ Link to Windows nicht sichtbar"
                    message = "Installiert aber nicht im Launcher"
                    icon = R.drawable.ic_menu_view
                }

                else -> {
                    title = "✅ Link to Windows aktiv"
                    message = "App ist installiert, aktiviert und sichtbar"
                    icon = R.drawable.ic_dialog_info
                }
            }

            val detailsText = """
            Status: ${if (status.isInstalled) "Installiert" else "Nicht installiert"}
            Versteckt: ${if (status.isHidden) "Ja" else "Nein"}
            Aktiviert: ${if (status.isEnabled) "Ja" else "Nein"}
            Im Launcher: ${if (status.isVisibleInLauncher) "Ja" else "Nein"}
            Package: ${status.packageName}
        """.trimIndent()

            val discordCheckIntent = Intent(this, QuietHoursNotificationService::class.java).apply {
                action = ACTION_CHECK_DISCORD
            }
            val discordCheckPendingIntent = PendingIntent.getService(
                this,
                4,
                discordCheckIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(detailsText)
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(discordCheckPendingIntent)
                .build()

            val notificationManager = getSystemService(NotificationManager::class.java)
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(30000, notification)
            }

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error checking Link to Windows status", e)
            showSimpleNotification(
                "❌ Fehler",
                "Link to Windows Status konnte nicht geprüft werden: ${e.message}"
            )
        }
    }

    private fun unhideAndStartLinkToWindows() {
        try {
            val packageName = "com.example.cloud"

            val dpm =
                getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)

            if (dpm.isAdminActive(adminComponent)) {

                try {
                    val wasHidden = dpm.isApplicationHidden(adminComponent, packageName)

                    if (wasHidden) {
                        dpm.setApplicationHidden(adminComponent, packageName, false)
                    }

                    try {
                        val pm = packageManager
                        val appInfo = pm.getApplicationInfo(packageName, 0)

                        if (!appInfo.enabled) {
                            pm.setApplicationEnabledSetting(
                                packageName,
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                0
                            )
                        }
                    } catch (e: Exception) {
                        Log.e("QuietHoursService", "PackageManager enable failed", e)
                    }

                    showSimpleNotification(
                        "✓ Cloud wird aktiviert",
                        "App wurde mit Device Admin aktiviert"
                    )

                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                startActivity(launchIntent)

                                showSimpleNotification(
                                    "✓ Cloud gestartet",
                                    "App wurde erfolgreich geöffnet"
                                )
                            } else {
                                val directIntent = Intent().apply {
                                    setClassName(packageName, "com.example.cloud.MainActivity")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                }
                                startActivity(directIntent)

                                showSimpleNotification(
                                    "✓ Cloud gestartet",
                                    "MainActivity wurde direkt geöffnet"
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("QuietHoursService", "Start failed", e)
                            showSimpleNotification(
                                "⚠️ Start fehlgeschlagen",
                                "App aktiviert, aber Start nicht möglich: ${e.message}"
                            )
                        }
                    }, 800)

                } catch (e: SecurityException) {
                    Log.e("QuietHoursService", "SecurityException mit Device Admin", e)

                    try {
                        val commands = """
                        pm unhide $packageName
                        pm enable $packageName
                        pm unsuspend $packageName
                    """.trimIndent()

                        Runtime.getRuntime().exec(arrayOf("sh", "-c", commands))

                        Handler(Looper.getMainLooper()).postDelayed({
                            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                            launchIntent?.let {
                                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                startActivity(it)
                                showSimpleNotification("✓ Cloud", "App via Shell aktiviert")
                            } ?: showSimpleNotification(
                                "⚠️ Fehler",
                                "Shell erfolgreich, aber Start fehlgeschlagen"
                            )
                        }, 1500)

                    } catch (shellException: Exception) {
                        Log.e("QuietHoursService", "Shell commands failed", shellException)
                        showSimpleNotification(
                            "❌ Alle Methoden fehlgeschlagen",
                            "Device Admin und Shell haben nicht funktioniert"
                        )
                    }
                }

            } else {
                Log.w("QuietHoursService", "❌ Device Admin ist NICHT aktiv!")

                val intent =
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = "package:$packageName".toUri()
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }

                try {
                    startActivity(intent)
                    showSimpleNotification(
                        "⚠️ Device Admin nicht aktiv",
                        "Aktiviere die App manuell in den Einstellungen"
                    )
                } catch (_: Exception) {
                    showSimpleNotification(
                        "❌ Fehler",
                        "Device Admin nicht aktiv und Einstellungen konnten nicht geöffnet werden"
                    )
                }
            }

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Unerwarteter Fehler beim Aktivieren", e)
            e.printStackTrace()
            showSimpleNotification(
                "❌ Kritischer Fehler",
                "Fehler: ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }

    private fun checkAndShowDiscordStatus() {
        try {
            val status = getLinkToWindowsStatus(this, "com.discord")

            val title: String
            val message: String
            val icon: Int

            when {
                !status.isInstalled -> {
                    title = "❌ Discord"
                    message = "App ist nicht installiert"
                    icon = R.drawable.ic_dialog_alert
                }

                status.isHidden -> {
                    title = "🚫 Discord versteckt"
                    message = "Die App wurde durch das System versteckt"
                    icon = R.drawable.ic_menu_close_clear_cancel
                }

                !status.isEnabled -> {
                    title = "⏸️ Discord deaktiviert"
                    message = "Die App ist installiert aber deaktiviert"
                    icon = R.drawable.ic_media_pause
                }

                !status.isVisibleInLauncher -> {
                    title = "👁️ Discord nicht sichtbar"
                    message = "Installiert aber nicht im Launcher"
                    icon = R.drawable.ic_menu_view
                }

                else -> {
                    title = "✅ Discord aktiv"
                    message = "App ist installiert, aktiviert und sichtbar"
                    icon = R.drawable.ic_dialog_info
                }
            }

            val detailsText = """
            Status: ${if (status.isInstalled) "Installiert" else "Nicht installiert"}
            Versteckt: ${if (status.isHidden) "Ja" else "Nein"}
            Aktiviert: ${if (status.isEnabled) "Ja" else "Nein"}
            Im Launcher: ${if (status.isVisibleInLauncher) "Ja" else "Nein"}
            Package: ${status.packageName}
        """.trimIndent()

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(icon)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(detailsText)
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val notificationManager = getSystemService(NotificationManager::class.java)
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(30001, notification)
            }

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error checking Discord status", e)
            showSimpleNotification(
                "❌ Fehler",
                "Discord Status konnte nicht geprüft werden: ${e.message}"
            )
        }
    }

    private val prefChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == "saved_number" || key == "saved_number_start") {
                handler.removeCallbacks(checkRunnable)

                isCurrentlyQuietHours = isQuietHoursNow()

                updateNotification(isCurrentlyQuietHours)

                handler.post(checkRunnable)
            }
        }

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences("quick_settings_prefs", MODE_PRIVATE)
        createNotificationChannel()

        isCurrentlyQuietHours = isQuietHoursNow()

        startForeground(NOTIFICATION_ID, createNotification(isCurrentlyQuietHours))

        sharedPreferences.registerOnSharedPreferenceChangeListener(prefChangeListener)

        handler.post(checkRunnable)

        val filter = IntentFilter(ACTION_MESSAGE_SENT)
        registerReceiver(messageSentReceiver, filter, RECEIVER_NOT_EXPORTED)

        val dismissFilter = IntentFilter(ACTION_NOTIFICATION_DISMISSED)
        registerReceiver(notificationDismissReceiver, dismissFilter, RECEIVER_NOT_EXPORTED)

        val timeChangeFilter = IntentFilter().apply {
            addAction(ACTION_CHANGE_START)
            addAction(ACTION_CHANGE_END)
        }
        registerReceiver(timeChangeReceiver, timeChangeFilter, RECEIVER_NOT_EXPORTED)

        val commandFilter = IntentFilter(ACTION_EXECUTE_COMMAND)
        registerReceiver(commandReceiver, commandFilter, RECEIVER_NOT_EXPORTED)
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_MESSAGES -> {
                showUnreadMessages()
            }

            "ACTION_UPDATE_SINGLE_SENDER" -> {
                val sender = intent.getStringExtra("EXTRA_SENDER")
                if (sender != null) {
                    updateSingleSenderNotification(sender)
                }
            }

            ACTION_OPEN_SETTINGS -> {
                openAndroidSettings()
            }

            ACTION_OPEN_MUSIC_PLAYER -> {
                openMusicPlayer()
            }

            ACTION_RESTART_MUSIC_PLAYER -> {
                restartMusicPlayer()
            }

            ACTION_CHECK_LINK_TO_WINDOWS -> {
                checkAndShowLinkToWindowsStatus()
            }

            ACTION_CHECK_DISCORD -> {
                checkAndShowDiscordStatus()
            }

            ACTION_UNHIDE_AND_START -> {
                unhideAndStartLinkToWindows()
            }

            ACTION_PLAY_VOICE_NOTE -> {
                val sender = intent.getStringExtra(EXTRA_SENDER_FOR_VOICE)
                if (sender != null) {
                    playLatestVoiceNote(sender)
                }
            }

            ACTION_NEXT_VOICE_NOTE -> {
                playNextVoiceNote()
            }

            ACTION_PREV_VOICE_NOTE -> {
                playPreviousVoiceNote()
            }

            ACTION_STOP_VOICE_NOTE -> {
                stopVoiceNote()
            }

            ACTION_SHOW_GALLERY -> {
                showGallery()
            }

            ACTION_NEXT_GALLERY_IMAGE -> {
                showNextGalleryImage()
            }

            ACTION_PREV_GALLERY_IMAGE -> {
                showPreviousGalleryImage()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(checkRunnable)

        try {
            voiceNotePlayer?.release()
            voiceNotePlayer = null
        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error releasing MediaPlayer", e)
        }

        try {
            unregisterReceiver(messageSentReceiver)
            unregisterReceiver(notificationDismissReceiver)
            unregisterReceiver(timeChangeReceiver)
            unregisterReceiver(commandReceiver)
        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error unregistering receivers", e)
        }

        sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefChangeListener)

        val restartIntent = Intent(applicationContext, QuietHoursNotificationService::class.java)
        val pendingIntent = PendingIntent.getService(
            applicationContext, 0, restartIntent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 1000,
            pendingIntent
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val notification = createNotification(isCurrentlyQuietHours)
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

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ruhezeiten Überwachung",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Überwacht Ruhezeiten "
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)

        val confirmationChannel = NotificationChannel(
            CONFIRMATION_CHANNEL_ID,
            "Versandbestätigungen",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Bestätigungen für gesendete WhatsApp Nachrichten"
            setShowBadge(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        notificationManager.createNotificationChannel(confirmationChannel)

        val voiceNoteChannel = NotificationChannel(
            VOICE_NOTE_CHANNEL_ID,
            "Sprachnachrichten Player",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Steuerung für WhatsApp Sprachnachrichten"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        notificationManager.createNotificationChannel(voiceNoteChannel)

        val galleryChannel = NotificationChannel(
            GALLERY_CHANNEL_ID,
            "Galerie",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Deine persönliche Galerie"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(galleryChannel)
    }

    private fun isQuietHoursNow(): Boolean {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val prefs = getSharedPreferences("quick_settings_prefs", MODE_PRIVATE)
        val quietEnd = prefs.getString("saved_number", null)?.toIntOrNull() ?: 21
        val quietStart = prefs.getString("saved_number_start", null)?.toIntOrNull() ?: 7
        return hour < quietStart || hour >= quietEnd
    }

    private fun createNotification(isQuietHours: Boolean): Notification {
        val deleteIntent = Intent(ACTION_NOTIFICATION_DISMISSED).apply {
            putExtra("notification_id", NOTIFICATION_ID)
            `package` = packageName
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            this,
            999,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val launchCloudIntent =
            packageManager.getLaunchIntentForPackage("com.example.cloud")?.apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        val launchCloudPendingIntent = PendingIntent.getActivity(
            this,
            1002,
            launchCloudIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setDeleteIntent(deletePendingIntent)
            .setContentIntent(launchCloudPendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (isQuietHours) {
            builder
                .setContentTitle("🌙 Ruhezeit aktiv")

            val commandInput = RemoteInput.Builder("key_command_input")
                .setLabel("Befehl eingeben...")
                .build()

            val commandIntent = Intent(ACTION_EXECUTE_COMMAND).apply {
                `package` = packageName
            }

            val commandPendingIntent = PendingIntent.getBroadcast(
                this,
                200,
                commandIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val commandAction = NotificationCompat.Action.Builder(
                R.drawable.ic_menu_search,
                "Befehl",
                commandPendingIntent
            )
                .addRemoteInput(commandInput)
                .setShowsUserInterface(false)
                .build()

            builder.addAction(commandAction)

            val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            val settingsPendingIntent = PendingIntent.getActivity(
                this,
                1001,
                settingsIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.addAction(
                R.drawable.ic_menu_preferences,
                "Settings",
                settingsPendingIntent
            )

            val messagesIntent = Intent(this, QuietHoursNotificationService::class.java).apply {
                action = ACTION_SHOW_MESSAGES
            }
            val messagesPendingIntent = PendingIntent.getService(
                this, 0, messagesIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_dialog_email,
                "Messages",
                messagesPendingIntent
            )

            val musicIntent = Intent(this, QuietHoursNotificationService::class.java).apply {
                action = ACTION_RESTART_MUSIC_PLAYER
            }
            val musicPendingIntent = PendingIntent.getService(
                this, 2, musicIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_media_play,
                "Music",
                musicPendingIntent
            )

        } else {
            val prefs = getSharedPreferences("quick_settings_prefs", MODE_PRIVATE)
            val currentStart = prefs.getString("saved_number_start", "21") ?: "21"
            val currentEnd = prefs.getString("saved_number", "7") ?: "7"

            val commandInput = RemoteInput.Builder("key_command_input")
                .setLabel("Befehl eingeben...")
                .build()

            val commandIntent = Intent(ACTION_EXECUTE_COMMAND).apply {
                `package` = packageName
            }

            val commandPendingIntent = PendingIntent.getBroadcast(
                this,
                200,
                commandIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val commandAction = NotificationCompat.Action.Builder(
                R.drawable.ic_menu_search,
                "Befehl",
                commandPendingIntent
            )
                .addRemoteInput(commandInput)
                .setShowsUserInterface(false)
                .build()

            val startRemoteInput = RemoteInput.Builder("key_time_input")
                .setLabel("Startzeit (0-23)")
                .build()

            val startIntent = Intent(ACTION_CHANGE_START).apply {
                `package` = packageName
            }

            val startPending = PendingIntent.getBroadcast(
                this, 100, startIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val startAction = NotificationCompat.Action.Builder(
                R.drawable.ic_menu_recent_history,
                "Start: ${currentStart}h",
                startPending
            )
                .addRemoteInput(startRemoteInput)
                .setShowsUserInterface(false)
                .build()

            val endRemoteInput = RemoteInput.Builder("key_time_input")
                .setLabel("Endzeit (0-23)")
                .build()

            val endIntent = Intent(ACTION_CHANGE_END).apply {
                `package` = packageName
            }

            val endPending = PendingIntent.getBroadcast(
                this, 101, endIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val endAction = NotificationCompat.Action.Builder(
                R.drawable.ic_menu_recent_history,
                "Ende: ${currentEnd}h",
                endPending
            )
                .addRemoteInput(endRemoteInput)
                .setShowsUserInterface(false)
                .build()

            builder
                .setContentTitle("Ruhezeit-Überwachung")
                .setContentText("Aktiv von $currentStart:00 bis $currentEnd:00 Uhr")
                .addAction(commandAction)
                .addAction(startAction)
                .addAction(endAction)
        }

        return builder.build()
    }

    private fun checkQuietHours() {
        val isQuietHours = isQuietHoursNow()

        if (isQuietHours != isCurrentlyQuietHours) {
            isCurrentlyQuietHours = isQuietHours
            updateNotification(isQuietHours)
        }
    }

    private fun updateNotification(isQuietHours: Boolean) {
        val notification = createNotification(isQuietHours)
        startForeground(NOTIFICATION_ID, notification)
    }

    fun showUnreadMessages() {
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
                showSimpleNotification(
                    "Keine unbeantworteten Nachrichten",
                    "Alle Nachrichten wurden beantwortet oder keine Daten verfügbar."
                )
                return
            }

            val notificationManager = getSystemService(NotificationManager::class.java)

            messagesByContact.forEach { (sender, msgs) ->
                val notificationId = sender.hashCode()

                val senderPerson = Person.Builder()
                    .setName(sender)
                    .setKey(sender)
                    .build()

                val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.stat_notify_chat)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setAutoCancel(false)
                    .setOngoing(false)

                val messagingStyle = NotificationCompat.MessagingStyle(mePerson)
                    .setConversationTitle(sender)

                msgs.takeLast(10).forEach { msg ->
                    val timeText =
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                    messagingStyle.addMessage(
                        NotificationCompat.MessagingStyle.Message(
                            "${msg.text} • $timeText",
                            msg.timestamp,
                            if (msg.isOwnMessage) mePerson else senderPerson
                        )
                    )
                }

                builder.setStyle(messagingStyle)

                val replyData = replyActions[sender]
                if (replyData != null) {
                    val replyRemoteInput = RemoteInput.Builder("key_text_reply")
                        .setLabel("Antwort")
                        .build()

                    val sendBroadcastIntent = Intent(ACTION_MESSAGE_SENT).apply {
                        putExtra(EXTRA_SENDER, sender)
                        `package` = packageName
                    }

                    val sendBroadcastPendingIntent = PendingIntent.getBroadcast(
                        this,
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
                } else {
                    Log.w("QuietHoursService", "  No reply action available for $sender")
                }

                val messagesWithImages = msgs.filter { it.imageUri != null }

                if (messagesWithImages.isNotEmpty()) {
                    messagesWithImages.forEachIndexed { index, msg ->
                        val imageNotificationId = notificationId + 1000 + index

                        val imageNotification = NotificationCompat.Builder(this, CHANNEL_ID)
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
                                        android.graphics.ImageDecoder.decodeBitmap(
                                            android.graphics.ImageDecoder.createSource(
                                                contentResolver,
                                                msg.imageUri!!
                                            )
                                        )
                                    )
                                    .bigLargeIcon(null as android.graphics.Bitmap?)
                            )
                            .setLargeIcon(
                                android.graphics.ImageDecoder.decodeBitmap(
                                    android.graphics.ImageDecoder.createSource(
                                        contentResolver,
                                        msg.imageUri
                                    )
                                )
                            )
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setGroup("whatsapp_images_$sender")
                            .setAutoCancel(true)
                            .build()

                        if (ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.POST_NOTIFICATIONS
                            )
                            == PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationManager.notify(imageNotificationId, imageNotification)
                        }
                    }
                }

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    notificationManager.notify(notificationId, builder.build())
                } else {
                    Log.e("QuietHoursService", "  ✗ Missing POST_NOTIFICATIONS permission")
                }
            }
        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error showing messages", e)
            e.printStackTrace()
            showSimpleNotification(
                "Fehler",
                "Nachrichten konnten nicht angezeigt werden: ${e.message}"
            )
        }
    }

    private fun updateSingleSenderNotification(sender: String) {

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

        val voiceNoteIntent = Intent(this, QuietHoursNotificationService::class.java).apply {
            action = ACTION_PLAY_VOICE_NOTE
            putExtra(EXTRA_SENDER_FOR_VOICE, sender)
        }
        val voiceNotePendingIntent = PendingIntent.getService(
            this,
            notificationId + 5000,
            voiceNoteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.stat_notify_chat)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setOngoing(false)
            .setContentIntent(voiceNotePendingIntent)

        val messagingStyle = NotificationCompat.MessagingStyle(mePerson)
            .setConversationTitle(sender)

        messages.takeLast(10).forEach { msg ->
            val timeText =
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
            messagingStyle.addMessage(
                NotificationCompat.MessagingStyle.Message(
                    "${msg.text} • $timeText",
                    msg.timestamp,
                    if (msg.isOwnMessage) mePerson else senderPerson
                )
            )
        }

        builder.setStyle(messagingStyle)

        val replyRemoteInput = RemoteInput.Builder("key_text_reply")
            .setLabel("Antwort")
            .build()

        val sendBroadcastIntent = Intent(ACTION_MESSAGE_SENT).apply {
            putExtra(EXTRA_SENDER, sender)
            `package` = packageName
        }

        val sendBroadcastPendingIntent = PendingIntent.getBroadcast(
            this,
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

        val notificationManager = getSystemService(NotificationManager::class.java)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(notificationId, builder.build())
        }
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
        val notificationId = System.currentTimeMillis().toInt()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(notificationId, notification)

            if (duration > Duration.ZERO) {
                Handler(Looper.getMainLooper()).postDelayed(
                    { notificationManager.cancel(notificationId) },
                    duration.inWholeMilliseconds
                )
            }
        }
    }

    private fun handleMessageSent(sender: String, messageText: String) {
        try {

            val originalReplyData = WhatsAppNotificationListener.replyActions[sender]
            val isRealWhatsApp =
                originalReplyData?.pendingIntent?.creatorPackage == "com.whatsapp" ||
                        originalReplyData?.pendingIntent?.creatorPackage == "com.whatsapp.w4b"

            if (isRealWhatsApp) {
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
                    originalReplyData.pendingIntent.send(this, 0, intent)

                } catch (e: Exception) {
                    Log.e("QuietHoursService", "Failed to send to WhatsApp", e)
                    showSimpleNotification("Fehler", "Nachricht konnte nicht gesendet werden")
                    return
                }
            }

            val messagesList =
                WhatsAppNotificationListener.messagesByContact.getOrPut(sender) { mutableListOf() }

            val isDuplicate = messagesList.takeLast(3).any { existingMsg ->
                existingMsg.text == messageText &&
                        existingMsg.isOwnMessage &&
                        (System.currentTimeMillis() - existingMsg.timestamp) < 2000
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

            updateChatNotification(sender)

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error handling sent message", e)
            e.printStackTrace()
        }
    }

    private fun updateChatNotification(sender: String) {
        try {
            val notificationId = sender.hashCode()

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

            messages.takeLast(10).forEach { msg ->
                val timeText =
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                messagingStyle.addMessage(
                    NotificationCompat.MessagingStyle.Message(
                        "${msg.text} • $timeText",
                        msg.timestamp,
                        if (msg.isOwnMessage) mePerson else senderPerson
                    )
                )
            }

            val newRemoteInput = RemoteInput.Builder("key_text_reply")
                .setLabel("Antwort")
                .build()

            val newSendIntent = Intent(ACTION_MESSAGE_SENT).apply {
                putExtra(EXTRA_SENDER, sender)
                `package` = packageName
            }

            val newSendPendingIntent = PendingIntent.getBroadcast(
                this,
                notificationId,
                newSendIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            val replyAction = NotificationCompat.Action.Builder(
                R.drawable.ic_menu_send,
                "Antworten",
                newSendPendingIntent
            )
                .addRemoteInput(newRemoteInput)
                .setShowsUserInterface(false)
                .setAllowGeneratedReplies(false)
                .build()

            val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.stat_notify_chat)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setStyle(messagingStyle)
                .setAutoCancel(false)
                .setOngoing(false)
                .setOnlyAlertOnce(true)
                .addAction(replyAction)

            val notificationManager = getSystemService(NotificationManager::class.java)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {

                notificationManager.notify(notificationId, builder.build())
            }

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error updating chat notification", e)
        }
    }

    private fun openMusicPlayer() {
        try {
            MusicPlayerService.startService(this)

            MusicPlayerService.sendPlayAction(this)
        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error opening music player", e)
            showSimpleNotification("Fehler", "Musik Player konnte nicht geöffnet werden")
        }
    }

    private fun restartMusicPlayer() {
        try {
            // Service stoppen
            MusicPlayerService.stopService(this)

            // Kurz warten und dann mit Play-Action neu starten
            Handler(Looper.getMainLooper()).postDelayed({
                MusicPlayerService.startAndPlay(this)

                showSimpleNotification(
                    "🎵 Musik Player",
                    "Musik Player wurde neu gestartet"
                )
            }, 500)
        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error restarting music player", e)
            showSimpleNotification(
                "❌ Fehler",
                "Musik Player konnte nicht neu gestartet werden"
            )
        }
    }

    /**
     * Umfassende Status-Prüfung
     */
    fun getLinkToWindowsStatus(context: Context, name: String): AppHiddenStatus {
        val packageName = name

        return try {
            val packageManager = context.packageManager
            val appInfo = packageManager.getApplicationInfo(
                packageName,
                PackageManager.MATCH_UNINSTALLED_PACKAGES
            )

            val flaghidden = 0x08000000
            val isHidden = (appInfo.flags and flaghidden) != 0
            val isEnabled = appInfo.enabled

            val intent = Intent(Intent.ACTION_MAIN, null)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            intent.setPackage(packageName)
            val hasLauncherActivity = packageManager.queryIntentActivities(intent, 0).isNotEmpty()

            AppHiddenStatus(
                isInstalled = true,
                isHidden = isHidden,
                isEnabled = isEnabled,
                isVisibleInLauncher = hasLauncherActivity,
                packageName = packageName
            )

        } catch (_: PackageManager.NameNotFoundException) {
            AppHiddenStatus(
                isInstalled = false,
                isHidden = false,
                isEnabled = false,
                isVisibleInLauncher = false,
                packageName = packageName
            )
        }
    }

    data class AppHiddenStatus(
        val isInstalled: Boolean,
        val isHidden: Boolean,
        val isEnabled: Boolean,
        val isVisibleInLauncher: Boolean,
        val packageName: String
    )

    private fun getVoiceNoteFiles(): List<File> {
        try {
            val possiblePaths = listOf(
                "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes",
                "${Environment.getExternalStorageDirectory()}/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes",
                "/storage/emulated/0/WhatsApp/Media/WhatsApp Voice Notes"
            )

            for (path in possiblePaths) {
                val mainDir = File(path)

                if (mainDir.exists()) {
                    val subDirs = mainDir.listFiles()

                    val allFiles = mutableListOf<File>()

                    subDirs?.forEach { subDir ->
                        if (subDir.isDirectory) {
                            val files = subDir.listFiles { file ->
                                file.extension.lowercase() == "opus"
                            }

                            files?.forEach { file ->
                                allFiles.add(file)
                            }
                        }
                    }

                    if (allFiles.isNotEmpty()) {
                        return allFiles.sortedByDescending { it.lastModified() }
                    }
                }
            }

            Log.e("QuietHoursService", "No voice notes found in any path")
            return emptyList()

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error getting voice note files", e)
            return emptyList()
        }
    }

    private fun playLatestVoiceNote(sender: String) {
        try {
            currentSenderForVoiceNote = sender
            voiceNoteFiles = getVoiceNoteFiles()

            if (voiceNoteFiles.isEmpty()) {
                showSimpleNotification(
                    "Keine Sprachnachrichten",
                    "Keine .opus Dateien gefunden"
                )
                return
            }

            currentVoiceNoteIndex = 0
            playVoiceNoteAtIndex(currentVoiceNoteIndex)

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error playing voice note", e)
            showSimpleNotification("Fehler", "Sprachnachricht konnte nicht abgespielt werden")
        }
    }

    private fun playVoiceNoteAtIndex(index: Int) {
        try {
            if (index < 0 || index >= voiceNoteFiles.size) {
                showSimpleNotification("Fehler", "Ungültiger Index")
                return
            }

            voiceNotePlayer?.release()
            voiceNotePlayer = null

            val file = voiceNoteFiles[index]

            voiceNotePlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp ->
                    mp.start()
                    showVoiceNotePlayerNotification(file, true)
                }
                setOnCompletionListener {
                    showVoiceNotePlayerNotification(file, false)
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("QuietHoursService", "MediaPlayer error: what=$what, extra=$extra")
                    showSimpleNotification("Fehler", "Fehler beim Abspielen")
                    true
                }
                prepareAsync()
            }

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error playing voice note at index $index", e)
            showSimpleNotification(
                "Fehler",
                "Sprachnachricht konnte nicht abgespielt werden: ${e.message}"
            )
        }
    }

    private fun playNextVoiceNote() {
        if (voiceNoteFiles.isEmpty()) return

        currentVoiceNoteIndex = (currentVoiceNoteIndex + 1) % voiceNoteFiles.size
        playVoiceNoteAtIndex(currentVoiceNoteIndex)
    }

    private fun playPreviousVoiceNote() {
        if (voiceNoteFiles.isEmpty()) return

        currentVoiceNoteIndex = if (currentVoiceNoteIndex - 1 < 0) {
            voiceNoteFiles.size - 1
        } else {
            currentVoiceNoteIndex - 1
        }
        playVoiceNoteAtIndex(currentVoiceNoteIndex)
    }

    private fun stopVoiceNote() {
        try {
            voiceNotePlayer?.stop()
            voiceNotePlayer?.release()
            voiceNotePlayer = null

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.cancel(40000)
        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error stopping voice note", e)
        }
    }

    private fun showVoiceNotePlayerNotification(file: File, isPlaying: Boolean) {
        try {
            val fileName = file.name
            val fileDate = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                .format(Date(file.lastModified()))

            val prevIntent = Intent(this, QuietHoursNotificationService::class.java).apply {
                action = ACTION_PREV_VOICE_NOTE
            }
            val prevPendingIntent = PendingIntent.getService(
                this, 41, prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val playStopIntent = Intent(this, QuietHoursNotificationService::class.java).apply {
                if (isPlaying) {
                    action = ACTION_STOP_VOICE_NOTE
                } else {
                    action = ACTION_PLAY_VOICE_NOTE
                    putExtra(EXTRA_SENDER_FOR_VOICE, currentSenderForVoiceNote)
                }
            }
            val playStopPendingIntent = PendingIntent.getService(
                this, 42, playStopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val nextIntent = Intent(this, QuietHoursNotificationService::class.java).apply {
                action = ACTION_NEXT_VOICE_NOTE
            }
            val nextPendingIntent = PendingIntent.getService(
                this, 43, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, VOICE_NOTE_CHANNEL_ID)
                .setSmallIcon(if (isPlaying) R.drawable.ic_media_play else R.drawable.ic_media_pause)
                .setContentTitle("${if (isPlaying) "▶️" else "⏸️"} Sprachnachricht")
                .setContentText("$fileName • $fileDate")
                .setSubText("${currentVoiceNoteIndex + 1} von ${voiceNoteFiles.size}")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(isPlaying)
                .setAutoCancel(!isPlaying)
                .addAction(R.drawable.ic_media_previous, "Zurück", prevPendingIntent)
                .addAction(
                    if (isPlaying) R.drawable.ic_media_pause else R.drawable.ic_media_play,
                    if (isPlaying) "Stop" else "Play",
                    playStopPendingIntent
                )
                .addAction(R.drawable.ic_media_next, "Weiter", nextPendingIntent)
                .build()

            val notificationManager = getSystemService(NotificationManager::class.java)
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(40000, notification)
            }

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error showing voice note player notification", e)
        }
    }

    private fun showFlashlightLevelInfo() {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return

            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val maxLevel =
                characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL)
                    ?: 1

            if (maxLevel > 1) {
                showSimpleNotification(
                    "💡 Taschenlampe Info",
                    "Max. Helligkeit: $maxLevel\nSyntax: flashlevel [1-$maxLevel]"
                )
            } else {
                showSimpleNotification(
                    "⚠️ Taschenlampe Info",
                    "Gerät unterstützt keine Helligkeitssteuerung\nNur Ein/Aus verfügbar",
                    20.seconds
                )
            }
        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error getting flashlight info", e)
            showSimpleNotification(
                "❌ Fehler",
                "Taschenlampen-Info konnte nicht abgerufen werden",
                20.seconds
            )
        }
    }

    private fun startTimeControlMethod1() {
        try {
            showSimpleNotification("🔄 Methode 1", "Starte TimeControl via ComponentName...")

            val intent = Intent().apply {
                component = ComponentName(
                    "com.example.cloud",
                    "com.example.cloud.MainActivity"
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

            startActivity(intent)

            showSimpleNotification(
                "✅ Methode 1 erfolgreich",
                "TimeControl wurde via ComponentName gestartet"
            )

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Method 1 failed", e)
            showSimpleNotification(
                "❌ Methode 1 fehlgeschlagen",
                "Fehler: ${e.message}",
                20.seconds
            )
        }
    }

    private fun startTimeControlMethod3() {
        try {
            showSimpleNotification("🔄 Methode 3", "Aktiviere & starte TimeControl...")

            val packageName = "com.example.cloud"
            val pm = packageManager

            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)

                if (!appInfo.enabled) {
                    pm.setApplicationEnabledSetting(
                        packageName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        0
                    )

                    Handler(Looper.getMainLooper()).postDelayed({
                        launchTimeControlApp(packageName)
                    }, 500)

                    showSimpleNotification(
                        "✅ App aktiviert",
                        "TimeControl wurde aktiviert und wird gestartet..."
                    )
                } else {
                    launchTimeControlApp(packageName)

                    showSimpleNotification(
                        "✅ Methode 3",
                        "TimeControl war bereits aktiv und wurde gestartet"
                    )
                }

            } catch (_: PackageManager.NameNotFoundException) {
                showSimpleNotification(
                    "❌ App nicht gefunden",
                    "TimeControl ist nicht installiert",
                    20.seconds
                )
            }

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Method 3 failed", e)
            showSimpleNotification(
                "❌ Methode 3 fehlgeschlagen",
                "Fehler: ${e.message}",
                20.seconds
            )
        }
    }

    private fun startTimeControlMethod10() {
        try {
            showSimpleNotification("🔄 Methode 10", "Setze Enabled State & starte...")

            val packageName = "com.example.cloud"
            val pm = packageManager

            try {
                pm.getApplicationInfo(packageName, 0)

                pm.setApplicationEnabledSetting(
                    packageName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )

                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        val launchIntent = pm.getLaunchIntentForPackage(packageName)

                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            startActivity(launchIntent)

                            showSimpleNotification(
                                "✅ Methode 10 erfolgreich",
                                "TimeControl wurde via setApplicationEnabledSetting gestartet"
                            )

                        } else {
                            val directIntent = Intent().apply {
                                component = ComponentName(packageName, "$packageName.MainActivity")
                                flags =
                                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            startActivity(directIntent)

                            showSimpleNotification(
                                "✅ Methode 10 (Fallback)",
                                "TimeControl via ComponentName gestartet"
                            )
                        }

                    } catch (e: Exception) {
                        Log.e("QuietHoursService", "Failed to start after enabling", e)
                        showSimpleNotification(
                            "⚠️ Teilweise erfolgreich",
                            "App aktiviert, aber Start fehlgeschlagen: ${e.message}",
                            20.seconds
                        )
                    }
                }, 800)

            } catch (_: PackageManager.NameNotFoundException) {
                showSimpleNotification(
                    "❌ App nicht gefunden",
                    "TimeControl ist nicht installiert",
                    20.seconds
                )
            }

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Method 10 failed", e)
            showSimpleNotification(
                "❌ Methode 10 fehlgeschlagen",
                "Fehler: ${e.message}",
                20.seconds
            )
        }
    }

    private fun launchTimeControlApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(launchIntent)
            } else {
                val directIntent = Intent().apply {
                    component = ComponentName(packageName, "$packageName.MainActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(directIntent)
            }

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Failed to launch TimeControl", e)
            throw e
        }
    }

    private fun loadGalleryImages() {
        try {
            galleryImages = emptyList()
            val images = mutableListOf<android.net.Uri>()

            val projection = arrayOf(
                android.provider.MediaStore.Images.Media._ID,
                android.provider.MediaStore.Images.Media.DATE_MODIFIED,
                android.provider.MediaStore.Images.Media.DATA
            )

            val sortOrder = "${android.provider.MediaStore.Images.Media.DATE_MODIFIED} DESC"

            val cursor = contentResolver.query(
                android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )

            cursor?.use {
                val idColumn =
                    it.getColumnIndexOrThrow(android.provider.MediaStore.Images.Media._ID)

                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val uri = android.net.Uri.withAppendedPath(
                        android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString()
                    )
                    images.add(uri)

                    if (images.size >= 100) break
                }
            }

            galleryImages = images

            if (galleryImages.isEmpty()) {
                showSimpleNotification(
                    "📷 Galerie leer",
                    "Keine Bilder in deiner Galerie gefunden"
                )
                return
            }

            currentGalleryIndex = 0
            showGalleryImage(0)

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error loading gallery images", e)
            showSimpleNotification(
                "❌ Fehler",
                "Galerie konnte nicht geladen werden: ${e.message}",
                20.seconds
            )
        }
    }

    private fun showGalleryImage(index: Int) {
        try {
            if (galleryImages.isEmpty() || index < 0 || index >= galleryImages.size) {
                showSimpleNotification(
                    "❌ Fehler",
                    "Ungültiger Image-Index",
                    20.seconds
                )
                return
            }

            val imageUri = galleryImages[index]

            val originalBitmap = try {
                android.graphics.ImageDecoder.decodeBitmap(
                    android.graphics.ImageDecoder.createSource(contentResolver, imageUri)
                )
            } catch (e: Exception) {
                Log.e("QuietHoursService", "Error decoding image at index $index", e)
                showSimpleNotification(
                    "❌ Fehler",
                    "Bild konnte nicht geladen werden",
                    20.seconds
                )
                return
            }

            // Bild NICHT skalieren - Original verwenden
            val bitmap = originalBitmap

            // Custom RemoteViews für bessere Bilddarstellung
            val customView =
                android.widget.RemoteViews(packageName, R.layout.simple_list_item_1)

            val prevIntent = Intent(this, QuietHoursNotificationService::class.java).apply {
                action = ACTION_PREV_GALLERY_IMAGE
            }
            val prevPendingIntent = PendingIntent.getService(
                this, 71, prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val nextIntent = Intent(this, QuietHoursNotificationService::class.java).apply {
                action = ACTION_NEXT_GALLERY_IMAGE
            }
            val nextPendingIntent = PendingIntent.getService(
                this, 72, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Intent zum Öffnen des Bildes in der Standard-Galerie-App
            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(imageUri, "image/*")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            val openPendingIntent = PendingIntent.getActivity(
                this, 73, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, GALLERY_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_menu_gallery)
                .setContentTitle("📷 Galerie")
                .setContentText("Bild ${index + 1} von ${galleryImages.size} • ${bitmap.width}x${bitmap.height}")
                .setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .bigLargeIcon(null as android.graphics.Bitmap?)
                        .showBigPictureWhenCollapsed(true)
                        .setSummaryText("Wische nach unten für vollständiges Bild")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(openPendingIntent)
                .addAction(R.drawable.ic_media_previous, "◀", prevPendingIntent)
                .addAction(R.drawable.ic_menu_view, "Öffnen", openPendingIntent)
                .addAction(R.drawable.ic_media_next, "▶", nextPendingIntent)
                .build()

            val notificationManager = getSystemService(NotificationManager::class.java)
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(70000, notification)
            }

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error showing gallery image", e)
            showSimpleNotification(
                "❌ Fehler",
                "Galerie konnte nicht angezeigt werden: ${e.message}",
                20.seconds
            )
        }
    }

    private fun showGallery() {
        try {
            if (galleryImages.isEmpty()) {
                loadGalleryImages()
            } else {
                showGalleryImage(currentGalleryIndex)
            }
        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error showing gallery", e)
            showSimpleNotification(
                "❌ Fehler",
                "Galerie konnte nicht geöffnet werden"
            )
        }
    }

    private fun showNextGalleryImage() {
        if (galleryImages.isEmpty()) {
            showSimpleNotification(
                "❌ Galerie leer",
                "Keine Bilder zum Anzeigen",
                20.seconds
            )
            return
        }

        currentGalleryIndex = (currentGalleryIndex + 1) % galleryImages.size
        showGalleryImage(currentGalleryIndex)
    }

    private fun showPreviousGalleryImage() {
        if (galleryImages.isEmpty()) {
            showSimpleNotification(
                "❌ Galerie leer",
                "Keine Bilder zum Anzeigen",
                20.seconds
            )
            return
        }

        currentGalleryIndex = if (currentGalleryIndex - 1 < 0) {
            galleryImages.size - 1
        } else {
            currentGalleryIndex - 1
        }
        showGalleryImage(currentGalleryIndex)
    }

    // Cache für Ordner-Counts (wird während des Uploads aktualisiert)
    private val folderCountCache = mutableMapOf<String, Int>()

    /**
     * Ermittelt den besten Upload-Ordner basierend auf dem Datum
     * Erstellt automatisch (1), (2), (3) etc. wenn ein Ordner >100 Dateien hat
     */
    private suspend fun getOrCreateUploadFolder(baseFolder: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // Prüfe Basis-Ordner
                val currentCount = folderCountCache[baseFolder] ?: countFilesInFolder(baseFolder)
                folderCountCache[baseFolder] = currentCount

                Log.d("SnapUpload", "📊 Folder '$baseFolder' hat $currentCount Dateien")

                // Wenn unter 100, nutze den Basis-Ordner
                if (currentCount < 100) {
                    return@withContext baseFolder
                }

                // Sonst finde den nächsten verfügbaren (1), (2), (3) etc.
                var suffix = 1

                while (suffix < 50) { // Max 50 Unterordner als Sicherheit
                    val targetFolder = "$baseFolder ($suffix)"
                    val count = folderCountCache[targetFolder] ?: countFilesInFolder(targetFolder)
                    folderCountCache[targetFolder] = count

                    Log.d("SnapUpload", "📊 Checking '$targetFolder': $count Dateien")

                    if (count < 100) {
                        return@withContext targetFolder
                    }
                    suffix++
                }

                // Fallback: Nutze letzten Ordner mit Zeitstempel
                Log.w("SnapUpload", "⚠️ Alle Unterordner voll, erstelle neuen mit Zeitstempel")
                "$baseFolder (${System.currentTimeMillis()})"

            } catch (e: Exception) {
                Log.e("SnapUpload", "Fehler bei Ordner-Ermittlung, nutze Basis-Ordner", e)
                baseFolder
            }
        }
    }

    /**
     * Zählt Dateien in einem spezifischen Ordner (schnelle Variante)
     */
    private suspend fun countFilesInFolder(folder: String): Int {
        return withContext(Dispatchers.IO) {
            try {
                val supabaseUrl = SupabaseConfig.SUPABASE_URL
                val supabaseKey = SupabaseConfig.SUPABASE_ANON_KEY
                val bucketName = "snaps"

                val prefix = if (folder.isEmpty()) "" else "$folder/"

                val url = java.net.URL(
                    "$supabaseUrl/storage/v1/object/list/$bucketName?limit=1000"
                )
                val connection = url.openConnection() as java.net.HttpURLConnection

                connection.requestMethod = "POST"
                connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
                connection.setRequestProperty("apikey", supabaseKey)
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 10000
                connection.readTimeout = 10000
                connection.doOutput = true

                val body = """{"prefix":"$prefix"}"""
                connection.outputStream.use { it.write(body.toByteArray()) }

                val responseCode = connection.responseCode

                if (responseCode in 200..299) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val count = parseFileNamesFromJson(response).size
                    Log.d("SnapUpload", "✅ Folder '$folder': $count Dateien gefunden")
                    return@withContext count
                } else {
                    Log.w("SnapUpload", "⚠️ Folder '$folder' nicht gefunden oder leer")
                    return@withContext 0
                }

            } catch (e: Exception) {
                Log.e("SnapUpload", "Fehler beim Zählen von Ordner '$folder'", e)
                0
            }
        }
    }

    /**
     * Holt alle existierenden Dateien aus ALLEN Ordnern
     * (mit mehreren Monaten und Unterordnern)
     */
    private suspend fun getExistingFilesFromSupabase(): Set<String> {
        return withContext(Dispatchers.IO) {
            try {
                val supabaseUrl = SupabaseConfig.SUPABASE_URL
                val supabaseKey = SupabaseConfig.SUPABASE_ANON_KEY
                val bucketName = "snaps"
                val allFileNames = mutableSetOf<String>()

                Log.d("SnapUpload", "🔍 Lade bestehende Dateinamen von Supabase...")

                // Generiere mögliche Ordner (letzte 18 Monate + aktuelle)
                val calendar = Calendar.getInstance()
                val foldersToCheck = mutableListOf<String>()

                for (i in 0..18) {
                    val baseFolder = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                        .format(calendar.time)
                    foldersToCheck.add(baseFolder)

                    // Auch (1), (2), (3) etc. prüfen
                    for (suffix in 1..10) {
                        foldersToCheck.add("$baseFolder ($suffix)")
                    }

                    calendar.add(Calendar.MONTH, -1)
                }

                foldersToCheck.add("") // Root-Ordner

                Log.d("SnapUpload", "Prüfe ${foldersToCheck.size} mögliche Ordner...")

                var totalFilesFound = 0

                foldersToCheck.forEach { folder ->
                    try {
                        val prefix = if (folder.isEmpty()) "" else "$folder/"

                        val url = java.net.URL(
                            "$supabaseUrl/storage/v1/object/list/$bucketName?limit=1000"
                        )
                        val connection = url.openConnection() as java.net.HttpURLConnection

                        connection.requestMethod = "POST"
                        connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
                        connection.setRequestProperty("apikey", supabaseKey)
                        connection.setRequestProperty("Content-Type", "application/json")
                        connection.connectTimeout = 8000
                        connection.readTimeout = 8000
                        connection.doOutput = true

                        val body = """{"prefix":"$prefix"}"""
                        connection.outputStream.use { it.write(body.toByteArray()) }

                        val responseCode = connection.responseCode

                        if (responseCode in 200..299) {
                            val response = connection.inputStream.bufferedReader().use { it.readText() }
                            val files = parseFileNamesFromJson(response)

                            // Extrahiere nur die Dateinamen (ohne Pfad)
                            files.forEach { fullPath ->
                                val fileName = fullPath.substringAfterLast("/")
                                if (fileName.isNotEmpty()) {
                                    allFileNames.add(fileName)
                                }
                            }

                            if (files.isNotEmpty()) {
                                totalFilesFound += files.size
                                Log.d("SnapUpload", "  📁 $folder: ${files.size} Dateien")
                            }
                        }

                        // Kleine Pause um Rate Limits zu vermeiden
                        kotlinx.coroutines.delay(50)

                    } catch (e: Exception) {
                        // Ignoriere Fehler bei einzelnen Ordnern
                    }
                }

                Log.d("SnapUpload", "========================================")
                Log.d("SnapUpload", "✅ $totalFilesFound Dateien in ${allFileNames.size} eindeutigen Namen gefunden")
                Log.d("SnapUpload", "========================================")

                allFileNames

            } catch (e: Exception) {
                Log.e("SnapUpload", "Kritischer Fehler", e)
                emptySet()
            }
        }
    }

    private fun parseFileNamesFromJson(json: String): List<String> {
        val fileNames = mutableListOf<String>()

        try {
            var remaining = json.trim()

            while (remaining.contains("\"name\"")) {
                val nameStart = remaining.indexOf("\"name\"")
                if (nameStart == -1) break

                val valueStart = remaining.indexOf("\"", nameStart + 6) + 1
                val valueEnd = remaining.indexOf("\"", valueStart)

                if (valueEnd == -1) break

                val fileName = remaining.substring(valueStart, valueEnd)

                if (fileName.isNotEmpty() &&
                    !fileName.endsWith("/") &&
                    fileName.contains(".")
                ) {
                    fileNames.add(fileName)
                }

                remaining = remaining.substring(valueEnd + 1)
            }

        } catch (e: Exception) {
            Log.e("SnapUpload", "Error parsing JSON", e)
        }

        return fileNames
    }

    private fun findFilesToUpload(
        localFiles: List<File>,
        existingServerFileNames: Set<String>
    ): List<File> {

        val maxFileSize = 50 * 1024 * 1024L // 50 MB

        return localFiles.filter { file ->
            if (file.length() > maxFileSize) {
                val sizeMB = file.length() / 1024 / 1024
                Log.w("SnapUpload", "⚠️ Skipping ${file.name} - Zu groß: ${sizeMB}MB")
                return@filter false
            }

            // Einfacher Vergleich: Existiert der Dateiname bereits?
            val exists = existingServerFileNames.contains(file.name)

            if (exists) {
                Log.d("SnapUpload", "⏭️ Skipping ${file.name} (bereits hochgeladen)")
            }

            !exists
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun uploadSnapImages() {
        try {
            uploadedCount = 0
            failedCount = 0

            showSimpleNotification(
                "📤 Snap Upload",
                "Suche nach lokalen Medien...",
                3.seconds
            )

            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    // Schritt 1: Lokale Dateien finden
                    val localFiles = findSnapMedia()

                    if (localFiles.isEmpty()) {
                        Handler(Looper.getMainLooper()).post {
                            showSimpleNotification(
                                "ℹ️ Snap Upload",
                                "Keine Medien in DCIM/Snap gefunden"
                            )
                        }
                        return@launch
                    }

                    Handler(Looper.getMainLooper()).post {
                        showSimpleNotification(
                            "🔍 Snap Upload",
                            "Prüfe welche Dateien bereits hochgeladen sind...\nDas kann 10-30 Sek. dauern",
                            10.seconds
                        )
                    }

                    // Schritt 2: Bestehende Dateien holen (aus ALLEN Ordnern)
                    val existingFileNames = getExistingFilesFromSupabase()

                    // Schritt 3: Vergleichen
                    val filesToUpload = findFilesToUpload(localFiles, existingFileNames)

                    if (filesToUpload.isEmpty()) {
                        Handler(Looper.getMainLooper()).post {
                            showSimpleNotification(
                                "✅ Bereits aktuell",
                                "Alle ${localFiles.size} Dateien bereits hochgeladen"
                            )
                        }
                        return@launch
                    }

                    Handler(Looper.getMainLooper()).post {
                        showSimpleNotification(
                            "🚀 Upload startet",
                            "${filesToUpload.size} neue Dateien werden hochgeladen...",
                            5.seconds
                        )
                    }

                    // Schritt 4: Cache zurücksetzen
                    folderCountCache.clear()

                    // Schritt 5: Upload mit intelligentem Batching
                    val batchSize = 20 // Alle 20 Dateien Update-Notification

                    filesToUpload.forEachIndexed { index, file ->
                        try {
                            val progress = "${index + 1}/${filesToUpload.size}"

                            // Bestimme Upload-Ordner (mit Auto-Split bei >100 Dateien)
                            val dateFolder = SimpleDateFormat("yyyy-MM", Locale.getDefault())
                                .format(Date(file.lastModified()))

                            val targetFolder = getOrCreateUploadFolder(dateFolder)

                            val success = uploadMediaToSupabase(file, targetFolder)

                            if (success) {
                                uploadedCount++
                                // Inkrementiere Cache
                                folderCountCache[targetFolder] =
                                    (folderCountCache[targetFolder] ?: 0) + 1

                                Log.d("SnapUpload", "✅ [$progress] ${file.name} → $targetFolder")
                            } else {
                                failedCount++
                                Log.e("SnapUpload", "❌ [$progress] ${file.name} fehlgeschlagen")
                            }

                            // Progress-Updates alle 20 Dateien
                            if ((index + 1) % batchSize == 0) {
                                Handler(Looper.getMainLooper()).post {
                                    showSimpleNotification(
                                        "📤 Upload läuft...",
                                        "✅ $uploadedCount erfolgreich • ❌ $failedCount fehlgeschlagen\n" +
                                                "Fortschritt: $progress",
                                        3.seconds
                                    )
                                }
                            }

                            // Delay zwischen Uploads (angepasst nach Dateityp)
                            if (isVideoFile(file)) {
                                kotlinx.coroutines.delay(1500) // Videos
                            } else {
                                kotlinx.coroutines.delay(500) // Bilder
                            }

                        } catch (e: Exception) {
                            failedCount++
                            Log.e("SnapUpload", "Fehler beim Upload von ${file.name}", e)
                        }
                    }

                    // Finale Zusammenfassung
                    Handler(Looper.getMainLooper()).post {
                        val emoji = if (failedCount == 0) "🎉" else "⚠️"
                        showSimpleNotification(
                            "$emoji Upload abgeschlossen",
                            "✅ $uploadedCount erfolgreich hochgeladen\n" +
                                    "❌ $failedCount fehlgeschlagen\n" +
                                    "📁 Dateien automatisch auf Ordner verteilt"
                        )
                    }

                } catch (e: Exception) {
                    Log.e("SnapUpload", "Fehler im Upload-Prozess", e)
                    Handler(Looper.getMainLooper()).post {
                        showSimpleNotification(
                            "❌ Kritischer Fehler",
                            "Upload abgebrochen: ${e.message}",
                            20.seconds
                        )
                    }
                }
            }

        } catch (e: Exception) {
            Log.e("SnapUpload", "Fehler beim Start", e)
            showSimpleNotification(
                "❌ Start-Fehler",
                "Upload konnte nicht gestartet werden: ${e.message}",
                20.seconds
            )
        }
    }

    private fun findSnapMedia(): List<File> {
        val mediaFiles = mutableSetOf<File>()

        try {
            val possiblePaths = listOf(
                "${Environment.getExternalStorageDirectory()}/DCIM/Snap",
                "${Environment.getExternalStorageDirectory()}/DCIM/Snapchat"
            )

            possiblePaths.forEach { path ->
                val directory = File(path)

                if (directory.exists() && directory.isDirectory) {
                    Log.d("SnapUpload", "📁 Scanne Verzeichnis: $path")

                    directory.listFiles()?.forEach { file ->
                        if (file.isFile && isMediaFile(file)) {
                            mediaFiles.add(file)
                        } else if (file.isDirectory) {
                            // Auch Unterordner durchsuchen
                            file.listFiles()?.forEach { subFile ->
                                if (subFile.isFile && isMediaFile(subFile)) {
                                    mediaFiles.add(subFile)
                                }
                            }
                        }
                    }
                }
            }

            val imageCount = mediaFiles.count { isImageFile(it) }
            val videoCount = mediaFiles.count { isVideoFile(it) }

            Log.d("SnapUpload", "========================================")
            Log.d("SnapUpload", "📊 Gefunden: ${mediaFiles.size} Medien")
            Log.d("SnapUpload", "  📷 Bilder: $imageCount")
            Log.d("SnapUpload", "  🎥 Videos: $videoCount")
            Log.d("SnapUpload", "========================================")

            // Nach Datum sortieren (älteste zuerst für bessere Ordner-Verteilung)
            return mediaFiles.sortedBy { it.lastModified() }

        } catch (e: Exception) {
            Log.e("SnapUpload", "Fehler beim Suchen der Medien", e)
            return emptyList()
        }
    }

    private fun isMediaFile(file: File): Boolean {
        return isImageFile(file) || isVideoFile(file)
    }

    private fun isImageFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in listOf("jpg", "jpeg", "png", "webp")
    }

    private fun isVideoFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in listOf("mp4", "mov", "avi", "mkv", "webm", "3gp")
    }

    /**
     * Upload mit spezifischem Zielordner
     */
    private fun uploadMediaToSupabase(file: File, targetFolder: String): Boolean {
        return try {
            if (!file.exists() || !file.canRead()) {
                Log.e("SnapUpload", "❌ Datei nicht lesbar: ${file.absolutePath}")
                return false
            }

            val supabaseUrl = SupabaseConfig.SUPABASE_URL
            val supabaseKey = SupabaseConfig.SUPABASE_ANON_KEY
            val bucketName = "snaps"

            // Nutze den übergebenen Zielordner
            val fileName = file.name // Original-Dateiname beibehalten
            val storagePath = "$targetFolder/$fileName"

            val mediaBytes = file.readBytes()

            val url = java.net.URL("$supabaseUrl/storage/v1/object/$bucketName/$storagePath")
            val connection = url.openConnection() as java.net.HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Authorization", "Bearer $supabaseKey")
            connection.setRequestProperty("apikey", supabaseKey)
            connection.setRequestProperty("Content-Type", getMimeType(file))
            connection.setRequestProperty("x-upsert", "false")
            connection.setRequestProperty("Content-Length", mediaBytes.size.toString())

            connection.connectTimeout = 30000
            connection.readTimeout = if (isVideoFile(file)) 120000 else 60000
            connection.doOutput = true

            connection.outputStream.use { output ->
                output.write(mediaBytes)
                output.flush()
            }

            val responseCode = connection.responseCode

            when (responseCode) {
                in 200..299 -> {
                    Log.d("SnapUpload", "✅ Hochgeladen: $storagePath")
                    true
                }
                400 -> {
                    // Prüfe ob es ein "Duplicate" Fehler ist
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    if (error.contains("Duplicate") || error.contains("already exists")) {
                        Log.w("SnapUpload", "⚠️ Existiert bereits: $storagePath")
                        true // Als Erfolg werten
                    } else {
                        Log.e("SnapUpload", "❌ Fehler 400: $error")
                        false
                    }
                }
                409 -> {
                    Log.w("SnapUpload", "⚠️ Existiert bereits (409): $storagePath")
                    true
                }
                else -> {
                    val error = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    Log.e("SnapUpload", "❌ Fehler $responseCode: $error")
                    false
                }
            }

        } catch (e: Exception) {
            Log.e("SnapUpload", "❌ Upload-Fehler für ${file.name}", e)
            false
        }
    }

    private fun getMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "mp4" -> "video/mp4"
            "mov" -> "video/quicktime"
            "avi" -> "video/x-msvideo"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            else -> "application/octet-stream"
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun showLastFriendMessages() {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                Handler(Looper.getMainLooper()).post {
                    showSimpleNotification(
                        "📨 Lade Nachrichten...",
                        "Rufe letzte Nachrichten von friend ab",
                        3.seconds
                    )
                }

                val supabase = SupabaseConfig.client

                // Letzte 10 Nachrichten von friend abrufen
                val response = supabase.from("messages")
                    .select {
                        filter {
                            eq("sender_id", "friend")
                            eq("receiver_id", "you")
                        }
                        order("created_at", Order.DESCENDING)
                        limit(10)
                    }

                // Manuelles Parsen falls nötig
                val messages = response.decodeList<ChatService.Message>()
                    .reversed() // Chronologische Reihenfolge (älteste zuerst)

                if (messages.isEmpty()) {
                    Handler(Looper.getMainLooper()).post {
                        showSimpleNotification(
                            "📭 Keine Nachrichten",
                            "Keine Nachrichten von friend gefunden"
                        )
                    }
                    return@launch
                }

                // Nachrichten als Notification anzeigen
                Handler(Looper.getMainLooper()).post {
                    showFriendMessagesNotification(messages)
                }

            } catch (e: Exception) {
                Log.e("QuietHoursService", "Error loading friend messages", e)
                Handler(Looper.getMainLooper()).post {
                    showSimpleNotification(
                        "❌ Fehler",
                        "Nachrichten konnten nicht geladen werden: ${e.message}",
                        20.seconds
                    )
                }
            }
        }
    }

    private fun showFriendMessagesNotification(messages: List<ChatService.Message>) {
        try {
            val messageText = messages.joinToString("\n\n") { msg ->
                val timeStr = try {
                    msg.created_at?.let {
                        // Parse ISO 8601 String zu lesbarem Format
                        val instant = java.time.Instant.parse(it)
                        val formatter = java.time.format.DateTimeFormatter
                            .ofPattern("dd.MM.yyyy HH:mm")
                            .withZone(java.time.ZoneId.systemDefault())
                        formatter.format(instant)
                    }
                } catch (e: Exception) {
                    msg.created_at?.take(16)?.replace("T", " ") // Fallback
                } ?: "Unbekannt"

                "🕐 $timeStr\n${msg.content}"
            }

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_dialog_email)
                .setContentTitle("💬 Letzte ${messages.size} Nachrichten von friend")
                .setContentText(messages.lastOrNull()?.content ?: "")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(messageText)
                        .setBigContentTitle("💬 Chat-Verlauf mit friend")
                        .setSummaryText("${messages.size} Nachrichten")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val notificationManager = getSystemService(NotificationManager::class.java)
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(60100, notification)

                Log.d("QuietHoursService", "✅ Friend messages notification shown (${messages.size} messages)")
            }

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error showing friend messages notification", e)
            showSimpleNotification(
                "❌ Fehler",
                "Benachrichtigung konnte nicht angezeigt werden: ${e.message}",
                20.seconds
            )
        }
    }

    private fun connectToBluetoothHeadphones() {
        try {
            // Prefs für gespeicherte Bluetooth-Adresse
            val prefs = getSharedPreferences("bluetooth_prefs", MODE_PRIVATE)
            val savedAddress = prefs.getString("headphone_mac", null)

            if (savedAddress == null) {
                showSimpleNotification(
                    "ℹ️ Keine Kopfhörer gespeichert",
                    "Verwende 'bluetooth save' um deine Kopfhörer zu speichern"
                )
                return
            }

            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()

            if (bluetoothAdapter == null) {
                showSimpleNotification(
                    "❌ Bluetooth nicht verfügbar",
                    "Dieses Gerät unterstützt kein Bluetooth"
                )
                return
            }

            if (!bluetoothAdapter.isEnabled) {
                showSimpleNotification(
                    "⚠️ Bluetooth ausgeschaltet",
                    "Schalte Bluetooth zuerst ein"
                )
                return
            }

            // Verbindung herstellen
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {

                val device = bluetoothAdapter.getRemoteDevice(savedAddress)

                // Trigger connection via system
                val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK

                // Alternative: Direkte Verbindung (benötigt mehr Permissions)
                try {
                    val method = device.javaClass.getMethod("createBond")
                    method.invoke(device)

                    showSimpleNotification(
                        "🔵 Verbinde...",
                        "Verbindung zu Kopfhörern wird hergestellt"
                    )

                    // Status nach 2 Sekunden prüfen
                    Handler(Looper.getMainLooper()).postDelayed({
                        checkBluetoothConnectionStatus(device)
                    }, 2000)

                } catch (e: Exception) {
                    Log.e("QuietHoursService", "Direct connection failed, opening settings", e)
                    startActivity(intent)
                    showSimpleNotification(
                        "ℹ️ Bluetooth Einstellungen",
                        "Verbinde manuell mit deinen Kopfhörern"
                    )
                }

            } else {
                showSimpleNotification(
                    "❌ Keine Berechtigung",
                    "BLUETOOTH_CONNECT Berechtigung fehlt"
                )
            }

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error connecting bluetooth", e)
            showSimpleNotification(
                "❌ Verbindungsfehler",
                "Bluetooth konnte nicht verbunden werden: ${e.message}"
            )
        }
    }

    private fun checkBluetoothConnectionStatus(device: android.bluetooth.BluetoothDevice) {
        try {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {

                val isConnected = device.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED

                if (isConnected) {
                    showSimpleNotification(
                        "✅ Verbunden",
                        "Kopfhörer erfolgreich verbunden"
                    )
                } else {
                    showSimpleNotification(
                        "⏳ Wird verbunden...",
                        "Verbindung läuft noch"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error checking connection status", e)
        }
    }

    private fun saveBluetoothHeadphones() {
        try {
            val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()

            if (bluetoothAdapter == null) {
                showSimpleNotification(
                    "❌ Bluetooth nicht verfügbar",
                    "Dieses Gerät unterstützt kein Bluetooth"
                )
                return
            }

            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {

                val pairedDevices = bluetoothAdapter.bondedDevices

                if (pairedDevices.isEmpty()) {
                    showSimpleNotification(
                        "ℹ️ Keine Geräte gekoppelt",
                        "Koppele zuerst deine Kopfhörer"
                    )
                    return
                }

                // Zeige gekoppelte Geräte
                val deviceList = pairedDevices.joinToString("\n") { device ->
                    "• ${device.name} (${device.address})"
                }

                // Wenn nur ein Gerät: Automatisch speichern
                if (pairedDevices.size == 1) {
                    val device = pairedDevices.first()
                    val prefs = getSharedPreferences("bluetooth_prefs", MODE_PRIVATE)
                    prefs.edit {
                        putString("headphone_mac", device.address)
                        putString("headphone_name", device.name)
                    }

                    showSimpleNotification(
                        "✅ Kopfhörer gespeichert",
                        "${device.name} wurde als Standard-Kopfhörer gespeichert"
                    )
                } else {
                    // Mehrere Geräte: Liste anzeigen
                    val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_dialog_info)
                        .setContentTitle("🔵 Gekoppelte Bluetooth-Geräte")
                        .setContentText("${pairedDevices.size} Geräte gefunden")
                        .setStyle(
                            NotificationCompat.BigTextStyle()
                                .bigText("$deviceList\n\nVerwende 'bluetooth save [MAC]' um ein Gerät zu speichern")
                        )
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build()

                    val notificationManager = getSystemService(NotificationManager::class.java)
                    if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED) {
                        notificationManager.notify(50100, notification)
                    }
                }

            } else {
                showSimpleNotification(
                    "❌ Keine Berechtigung",
                    "BLUETOOTH_CONNECT Berechtigung fehlt"
                )
            }

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error saving bluetooth headphones", e)
            showSimpleNotification(
                "❌ Fehler",
                "Kopfhörer konnten nicht gespeichert werden: ${e.message}"
            )
        }
    }

    private fun showBluetoothInfo() {
        try {
            val prefs = getSharedPreferences("bluetooth_prefs", MODE_PRIVATE)
            val savedAddress = prefs.getString("headphone_mac", null)
            val savedName = prefs.getString("headphone_name", null)

            if (savedAddress == null) {
                showSimpleNotification(
                    "ℹ️ Keine Kopfhörer gespeichert",
                    "Verwende 'bluetooth save' um deine Kopfhörer zu speichern"
                )
                return
            }

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_dialog_info)
                .setContentTitle("🔵 Gespeicherte Kopfhörer")
                .setContentText(savedName ?: "Unbekannt")
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText("""
                        Name: ${savedName ?: "Unbekannt"}
                        MAC: $savedAddress
                        
                        Verwende 'bluetooth' zum Verbinden
                    """.trimIndent())
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()

            val notificationManager = getSystemService(NotificationManager::class.java)
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(50101, notification)
            }

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error showing bluetooth info", e)
        }
    }

}

class QuietActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("quick_settings_prefs", Context.MODE_PRIVATE)

        when (intent.action) {
            "SET_START" -> {
                prefs.edit { putString("saved_number_start", "21") }
            }

            "SET_END" -> {
                prefs.edit { putString("saved_number", "7") }
            }
        }
    }
}