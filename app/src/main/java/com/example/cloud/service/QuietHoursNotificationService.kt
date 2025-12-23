package com.example.cloud.service

import android.Manifest
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
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class QuietHoursNotificationService : Service() {

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
        private const val ACTION_CHECK_DISCORD = "com.example.cloud.ACTION_CHECK_DISCORD"  // <- NEU

        private const val ACTION_UNHIDE_AND_START = "com.example.cloud.ACTION_UNHIDE_AND_START"

        private const val ACTION_PLAY_VOICE_NOTE = "com.example.cloud.ACTION_PLAY_VOICE_NOTE"
        private const val ACTION_NEXT_VOICE_NOTE = "com.example.cloud.ACTION_NEXT_VOICE_NOTE"
        private const val ACTION_PREV_VOICE_NOTE = "com.example.cloud.ACTION_PREV_VOICE_NOTE"
        private const val ACTION_STOP_VOICE_NOTE = "com.example.cloud.ACTION_STOP_VOICE_NOTE"
        private const val EXTRA_SENDER_FOR_VOICE = "extra_sender_for_voice"

        private const val VOICE_NOTE_CHANNEL_ID = "voice_note_player_channel"

        private const val ACTION_TEST_VOICE_NOTE = "com.example.cloud.ACTION_TEST_VOICE_NOTE"
        private const val ACTION_EXECUTE_COMMAND = "com.example.cloud.ACTION_EXECUTE_COMMAND"

        private const val PREFS_REPLY_DATA = "reply_data_prefs"
        private const val KEY_SAVED_SENDER = "saved_sender"
        private const val KEY_SAVED_PACKAGE = "saved_package"
        private const val KEY_SAVED_RESULT_KEY = "saved_result_key"
        private const val KEY_HAS_SAVED_DATA = "has_saved_data"

        private val commandHistory = mutableListOf<String>()

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
        val aliases: List<String> = emptyList(), // NEU: Alternative Namen
        val description: String,
        val action: () -> Unit
    )

    data class SavedReplyData(
        val sender: String,
        val packageName: String,
        val resultKey: String
    )

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

            Log.d("QuietHoursService", "Reply data saved for: $sender")
            showSimpleNotification(
                "✅ Gespeichert",
                "Reply-Daten für '$sender' wurden permanent gespeichert"
            )

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error saving reply data", e)
            showSimpleNotification("❌ Fehler", "Konnte Reply-Daten nicht speichern")
        }
    }

    // 4. Funktion zum Laden der gespeicherten Reply-Daten:
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

            // Prüfe ob es aktuelle Reply-Daten gibt
            val currentReplyData = WhatsAppNotificationListener.replyActions[savedData.sender]

            if (currentReplyData != null) {
                // Verwende aktuelle Reply-Daten (sicherer & funktioniert immer)
                Log.d("QuietHoursService", "Using current reply data for ${savedData.sender}")
                sendMessageToWhatsApp(savedData.sender, messageText, currentReplyData)
            } else {
                // Fallback: Versuche mit gespeicherten Daten zu rekonstruieren
                Log.d(
                    "QuietHoursService",
                    "No current data, attempting to reconstruct from saved data"
                )

                try {
                    // Erstelle ein Intent mit dem gespeicherten Package
                    val intent = Intent().apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        setPackage(savedData.packageName)
                    }

                    val bundle = Bundle().apply {
                        putCharSequence(savedData.resultKey, messageText)
                    }

                    // Erstelle ein temporäres RemoteInput mit dem gespeicherten Key
                    val reconstructedRemoteInput = RemoteInput.Builder(savedData.resultKey)
                        .setLabel("Antwort")
                        .setAllowFreeFormInput(true)
                        .build()

                    RemoteInput.addResultsToIntent(
                        arrayOf(reconstructedRemoteInput),
                        intent,
                        bundle
                    )

                    // WICHTIG: Hier fehlt das PendingIntent - das ist der Grund warum es nicht funktioniert!
                    // Wir können kein neues PendingIntent erstellen, wir BRAUCHEN das originale von WhatsApp

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

    // 6. Hilfsfunktion zum Senden an WhatsApp:
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
            Log.d("QuietHoursService", "Message sent to WhatsApp: '$messageText' to '$sender'")

            // NEU: Duplikat-Schutz auch hier
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
                Log.d("QuietHoursService", "✓ Message added to local list")
            } else {
                Log.d("QuietHoursService", "⚠ Duplicate message detected, skipping add")
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

    // 7. Funktion zum Anzeigen der gespeicherten Daten:
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
                name = "ltw",
                aliases = listOf("link", "windows", "linktowindows"),
                description = "Prüft Link to Windows Status"
            ) {
                checkAndShowLinkToWindowsStatus()
            },
            Command(
                name = "discord",
                aliases = listOf("dc", "dis"),
                description = "Prüft Discord Status"
            ) {
                checkAndShowDiscordStatus()
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
                description = "Stoppt Musik Player Service"
            ) {
                MusicPlayerService.stopService(this)
                showSimpleNotification(
                    "✅ Musik Player gestoppt",
                    "Musik Player erfolgreich gestoppt"
                )
            },
            Command(
                name = "stoppodcast",
                aliases = listOf("stopp", "podcaststop"),
                description = "Stoppt Podcast Player Service"
            ) {
                PodcastPlayerService.stopService(this)
                showSimpleNotification(
                    "✅ Podcast Player gestoppt",
                    "Podcast Player erfolgreich gestoppt"
                )
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
                name = "podcast",
                aliases = listOf("pd", "pc", "Podcast"),
                description = "Startet PodcastPlayerService"
            ) {
                PodcastPlayerService.startService(this)
                PodcastPlayerService.sendPlayAction(this)
            },
            Command(
                name = "^",
                aliases = listOf(),
                description = "Führt letzten Befehl erneut aus"
            ) {},
            Command(
                name = "^^",
                aliases = listOf(),
                description = "Führt vorletzten Befehl erneut aus"
            ) {},
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

            // Finde die neueste Nachricht über alle Kontakte
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

            // Erstelle separate Notification mit der extrahierten Nachricht
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

            // Wenn es ein Bild gibt, zeige es an
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
                // Verwende Timestamp als Notification ID für Eindeutigkeit
                notificationManager.notify(
                    60000 + (message.timestamp % 10000).toInt(),
                    builder.build()
                )
                Log.d("QuietHoursService", "✓ Message extracted: '$sender' - '${message.text}'")

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

    private val handler = Handler(Looper.getMainLooper())
    private var isCurrentlyQuietHours = false

    // NEU: MediaPlayer für Voice Notes
    private var voiceNotePlayer: MediaPlayer? = null
    private var currentVoiceNoteIndex = 0
    private var voiceNoteFiles: List<File> = emptyList()
    private var currentSenderForVoiceNote: String? = null

    private val messageSentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_MESSAGE_SENT) {
                val sender = intent.getStringExtra(EXTRA_SENDER) ?: return

                Log.d("QuietHoursService", "=== MESSAGE SENT RECEIVER ===")
                Log.d("QuietHoursService", "Sender: $sender")

                // RemoteInput sofort auslesen
                val replyText = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence("key_text_reply")?.toString()

                Log.d("QuietHoursService", "Reply Text: $replyText")

                // Sofort RESULT_OK setzen, damit der Lade-Kreis verschwindet
                setResultCode(Activity.RESULT_OK)

                // Nachricht asynchron verarbeiten
                if (replyText != null && context != null) {
                    Handler(Looper.getMainLooper()).post {
                        handleMessageSent(sender, replyText)
                    }
                }
            }
        }
    }

    private val commandReceiver = object : BroadcastReceiver() {
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
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

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
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

        // Speichere in History (aber nicht die History-Befehle selbst)
        if (commandText != "^" && commandText != "^^") {
            commandHistory.add(actualCommand)
            // Limitiere History auf letzte 10 Befehle
            if (commandHistory.size > 10) {
                commandHistory.removeAt(0)
            }
        }

        val parts = actualCommand.split(" ", limit = 2)
        val commandInput = parts[0].lowercase()
        val argument = if (parts.size > 1) parts[1] else null

        // Spezielle Commands mit Argumenten (werden zuerst geprüft)
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

                        // Konvertiere Zahl zu Tag-String
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
        }

        // Einfache Commands ohne Argumente
        val commands = getAvailableCommands()

        // Suche nach passendem Command (Name oder Alias)
        val matchedCommand = commands.find { cmd ->
            cmd.name.equals(commandInput, ignoreCase = true) ||
                    cmd.aliases.any { it.equals(commandInput, ignoreCase = true) }
        }

        if (matchedCommand != null) {
            try {
                Log.d(
                    "QuietHoursService",
                    "Executing command: ${matchedCommand.name} (triggered by: $commandInput)"
                )
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
            // Zeige ähnliche Befehle basierend auf Name UND Aliases
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

    // Help Command - Zeigt alle verfügbaren Befehle
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

                // Nur wenn es unsere Hauptbenachrichtigung ist
                if (notificationId == NOTIFICATION_ID) {
                    Log.d("QuietHoursService", "Main notification dismissed - recreating")

                    // Benachrichtigung sofort wieder anzeigen
                    Handler(Looper.getMainLooper()).postDelayed({
                        val notification = createNotification(isCurrentlyQuietHours)
                        startForeground(NOTIFICATION_ID, notification)
                    }, 100) // Kurze Verzögerung für bessere UX
                } else {
                    Log.d("QuietHoursService", "NOOOOO")
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
                            Log.d("QuietHoursService", "Startzeit geändert auf: $timeValue")
                        }

                        ACTION_CHANGE_END -> {
                            prefs.edit { putString("saved_number", timeValue.toString()).commit() }
                            showSimpleNotification(
                                "✓ Endzeit geändert",
                                "Neue Endzeit: $timeValue:00 Uhr"
                            )
                            Log.d("QuietHoursService", "Endzeit geändert auf: $timeValue")
                        }
                    }

                    // Sofort RESULT_OK setzen
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
        Log.d("QuietHoursService", "CheckRunnable gestartet")
        checkQuietHours()
        scheduleNextCheck()
    }

    private fun scheduleNextCheck() {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)

        val quietStart = getQuietStartHour()
        val quietEnd = getQuietEndHour()

        Log.d("QuietHoursService", "=== SCHEDULE NEXT CHECK ===")
        Log.d("QuietHoursService", "Aktuelle Zeit: $currentHour:$currentMinute")
        Log.d("QuietHoursService", "Ruhezeiten: Start=$quietStart Uhr, Ende=$quietEnd Uhr")
        Log.d("QuietHoursService", "Aktueller Status: isCurrentlyQuietHours=$isCurrentlyQuietHours")

        val nextChange = Calendar.getInstance()
        nextChange.set(Calendar.SECOND, 0)
        nextChange.set(Calendar.MILLISECOND, 0)

        if (isCurrentlyQuietHours) {
            // Wir sind IN der Ruhezeit -> nächster Wechsel ist das ENDE
            nextChange.set(Calendar.HOUR_OF_DAY, quietEnd)
            nextChange.set(Calendar.MINUTE, 0)

            // Wenn Ende-Zeit schon vorbei ist (oder wir sind schon drüber), muss es morgen sein
            if (nextChange.timeInMillis <= now.timeInMillis) {
                nextChange.add(Calendar.DAY_OF_YEAR, 1)
                Log.d("QuietHoursService", "Ende-Zeit liegt in der Vergangenheit -> morgen")
            }

            Log.d("QuietHoursService", "Nächster Wechsel: ENDE der Ruhezeit um ${quietEnd}:00")
        } else {
            // Wir sind AUSSERHALB der Ruhezeit -> nächster Wechsel ist der START
            nextChange.set(Calendar.HOUR_OF_DAY, quietStart)
            nextChange.set(Calendar.MINUTE, 0)

            // Wenn Start-Zeit schon vorbei ist, muss es morgen sein
            if (nextChange.timeInMillis <= now.timeInMillis) {
                nextChange.add(Calendar.DAY_OF_YEAR, 1)
                Log.d("QuietHoursService", "Start-Zeit liegt in der Vergangenheit -> morgen")
            }

            Log.d("QuietHoursService", "Nächster Wechsel: START der Ruhezeit um ${quietStart}:00")
        }

        val timeToNextChange = nextChange.timeInMillis - now.timeInMillis
        val minutesToChange = timeToNextChange / (60 * 1000)

        Log.d("QuietHoursService", "Zeit bis zum Wechsel: $minutesToChange Minuten")

        // Intelligentes Delay:
        // - Wenn mehr als 3 Minuten bis zum Wechsel: Warte bis 2 Minuten vorher
        // - Wenn weniger als 3 Minuten: Prüfe jede Minute
        val delay: Long = if (timeToNextChange > 3 * 60 * 1000) {
            val calculatedDelay = timeToNextChange - (2 * 60 * 1000)
            Log.d(
                "QuietHoursService",
                "Langfristiges Warten: Nächster Check in ${calculatedDelay / 1000} Sekunden"
            )
            calculatedDelay
        } else {
            Log.d("QuietHoursService", "Engmaschiges Prüfen: Nächster Check in 60 Sekunden")
            60 * 1000L
        }

        handler.postDelayed(checkRunnable, delay)

        // Debug: Wann ist der nächste Check?
        val nextCheckTime = Calendar.getInstance()
        nextCheckTime.timeInMillis = now.timeInMillis + delay
        Log.d(
            "QuietHoursService",
            "Nächster Check: ${nextCheckTime.get(Calendar.HOUR_OF_DAY)}:${nextCheckTime.get(Calendar.MINUTE)}"
        )
    }

    private fun getQuietStartHour(): Int {
        val prefs = getSharedPreferences("quick_settings_prefs", MODE_PRIVATE)
        val start = prefs.getString("saved_number_start", null)?.toIntOrNull() ?: 21
        Log.d("QuietHoursService", "Lese Ruhezeit Start aus prefs: $start")
        return start
    }

    private fun getQuietEndHour(): Int {
        val prefs = getSharedPreferences("quick_settings_prefs", MODE_PRIVATE)
        val end = prefs.getString("saved_number", null)?.toIntOrNull() ?: 7
        Log.d("QuietHoursService", "Lese Ruhezeit Ende aus prefs: $end")
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

            // Erweiterte Notification mit Details
            val detailsText = """
            Status: ${if (status.isInstalled) "Installiert" else "Nicht installiert"}
            Versteckt: ${if (status.isHidden) "Ja" else "Nein"}
            Aktiviert: ${if (status.isEnabled) "Ja" else "Nein"}
            Im Launcher: ${if (status.isVisibleInLauncher) "Ja" else "Nein"}
            Package: ${status.packageName}
        """.trimIndent()

            // NEU: PendingIntent für Discord-Check erstellen
            val discordCheckIntent = Intent(this, QuietHoursNotificationService::class.java).apply {
                action = ACTION_CHECK_DISCORD  // <- Neue Action
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
                .setContentIntent(discordCheckPendingIntent) // <- Beim Klick Discord prüfen
                .build()

            val notificationManager = getSystemService(NotificationManager::class.java)
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(30000, notification)
                Log.d("QuietHoursService", "Link to Windows status: $status")
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

            // Verwende deinen existierenden DeviceAdminReceiver
            val dpm =
                getSystemService(DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)

            if (dpm.isAdminActive(adminComponent)) {
                Log.d("QuietHoursService", "✓ Device Admin ist aktiv")

                try {
                    // Versuche App mit Device Admin zu unhiden
                    val wasHidden = dpm.isApplicationHidden(adminComponent, packageName)
                    Log.d("QuietHoursService", "App war hidden: $wasHidden")

                    if (wasHidden) {
                        dpm.setApplicationHidden(adminComponent, packageName, false)
                        Log.d("QuietHoursService", "✓ App unhidden via Device Admin")
                    }

                    // Versuche App zu aktivieren (falls disabled)
                    try {
                        val pm = packageManager
                        val appInfo = pm.getApplicationInfo(packageName, 0)

                        if (!appInfo.enabled) {
                            pm.setApplicationEnabledSetting(
                                packageName,
                                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                0
                            )
                            Log.d("QuietHoursService", "✓ App enabled via PackageManager")
                        }
                    } catch (e: Exception) {
                        Log.e("QuietHoursService", "PackageManager enable failed", e)
                    }

                    showSimpleNotification(
                        "✓ Cloud wird aktiviert",
                        "App wurde mit Device Admin aktiviert"
                    )

                    // Warte kurz und starte die App
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
                            if (launchIntent != null) {
                                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                startActivity(launchIntent)
                                Log.d("QuietHoursService", "✓ Cloud app started")

                                showSimpleNotification(
                                    "✓ Cloud gestartet",
                                    "App wurde erfolgreich geöffnet"
                                )
                            } else {
                                // Fallback: Versuche MainActivity direkt zu starten
                                val directIntent = Intent().apply {
                                    setClassName(packageName, "com.example.cloud.MainActivity")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                }
                                startActivity(directIntent)
                                Log.d("QuietHoursService", "✓ Cloud MainActivity started directly")

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

                    // Fallback: Shell-Befehle als letzter Versuch
                    try {
                        val commands = """
                        pm unhide $packageName
                        pm enable $packageName
                        pm unsuspend $packageName
                    """.trimIndent()

                        Runtime.getRuntime().exec(arrayOf("sh", "-c", commands))
                        Log.d("QuietHoursService", "Shell commands executed")

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

                // Öffne die App-Einstellungen statt Device Admin Einstellungen
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

    // NEU: Funktion für Discord-Check
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
                notificationManager.notify(30001, notification) // <- Andere ID
                Log.d("QuietHoursService", "Discord status: $status")
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
                Log.d(
                    "QuietHoursService",
                    "SharedPreference geändert: $key - Neuberechnung des Check-Intervalls"
                )
                // Stoppe aktuellen geplanten Check
                handler.removeCallbacks(checkRunnable)

                // Status ggf. neu berechnen (Optional: könnte nochmal isQuietHoursNow frisch holen)
                isCurrentlyQuietHours = isQuietHoursNow()

                // Notification sofort updaten, da Uhrzeit sich geändert hat
                updateNotification(isCurrentlyQuietHours)

                // Neuen Check planen mit neuem Intervall
                handler.post(checkRunnable)
            }
        }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()
        sharedPreferences = getSharedPreferences("quick_settings_prefs", MODE_PRIVATE)
        createNotificationChannel()

        isCurrentlyQuietHours = isQuietHoursNow()
        Log.d(
            "QuietHoursService",
            "Service gestartet. Initialer Ruhezeit-Status: $isCurrentlyQuietHours"
        )

        startForeground(NOTIFICATION_ID, createNotification(isCurrentlyQuietHours))

        // Listener auf Pref-Änderungen registrieren
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefChangeListener)

        handler.post(checkRunnable) // Starte Initial-Check und Planung

        Log.d("QuietHoursService", "Service created and checkRunnable gestartet")

        val filter = IntentFilter(ACTION_MESSAGE_SENT)
        registerReceiver(messageSentReceiver, filter, RECEIVER_NOT_EXPORTED)

        val dismissFilter = IntentFilter(ACTION_NOTIFICATION_DISMISSED)
        registerReceiver(notificationDismissReceiver, dismissFilter, RECEIVER_NOT_EXPORTED)

        Log.d("QuietHoursService", "Receiver registriert")

        val timeChangeFilter = IntentFilter().apply {
            addAction(ACTION_CHANGE_START)
            addAction(ACTION_CHANGE_END)
        }
        registerReceiver(timeChangeReceiver, timeChangeFilter, RECEIVER_NOT_EXPORTED)

        Log.d("QuietHoursService", "TimeChangeReceiver registriert")

        val commandFilter = IntentFilter(ACTION_EXECUTE_COMMAND)
        registerReceiver(commandReceiver, commandFilter, RECEIVER_NOT_EXPORTED)
    }


    // Füge den neuen Action-Handler in onStartCommand hinzu (ca. Zeile 233):
    // In onStartCommand() hinzufügen
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

            ACTION_TEST_VOICE_NOTE -> {  // NEU
                createTestVoiceNoteNotification()
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
            unregisterReceiver(commandReceiver)  // NEU
        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error unregistering receivers", e)
        }

        sharedPreferences.unregisterOnSharedPreferenceChangeListener(prefChangeListener)

        // Restart logic...
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
        startForeground(NOTIFICATION_ID, notification) // sofort wieder foreground

        // Service neu starten nach 1 Sekunde
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

        // Channel für Versandbestätigungen
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

        // NEU: Channel für Voice Note Player
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

        // Content Intent zum Starten der Cloud App
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
            // COMMAND LINE INPUT während Ruhezeit
            builder
                .setContentTitle("🌙 Ruhezeit aktiv")

            // RemoteInput für Command Line
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

            // Add as action button
            builder.addAction(
                R.drawable.ic_menu_preferences,
                "Settings",
                settingsPendingIntent  // Direct PendingIntent, not service call
            )

            // Quick Access Buttons (optional - kannst du auch weglassen)
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
            // Außerhalb der Ruhezeit - Zeit-Einstellungen wie bisher
            val prefs = getSharedPreferences("quick_settings_prefs", MODE_PRIVATE)
            val currentStart = prefs.getString("saved_number_start", "21") ?: "21"
            val currentEnd = prefs.getString("saved_number", "7") ?: "7"

            // RemoteInput für Command Line auch außerhalb der Ruhezeit
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

            // RemoteInput für Startzeit
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

            // RemoteInput für Endzeit
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
                .addAction(commandAction)  // Command Line zuerst
                .addAction(startAction)
                .addAction(endAction)
        }

        return builder.build()
    }

    private fun checkQuietHours() {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val isQuietHours = isQuietHoursNow()

        Log.d(
            "QuietHoursService",
            "Prüfe Ruhezeit. Aktuelle Stunde: $hour, liegt Ruhezeit vor? $isQuietHours"
        )

        if (isQuietHours != isCurrentlyQuietHours) {
            Log.d(
                "QuietHoursService",
                "Ruhezeit Status hat sich geändert von $isCurrentlyQuietHours auf $isQuietHours"
            )
            isCurrentlyQuietHours = isQuietHours
            updateNotification(isQuietHours)
        }
    }

    private fun updateNotification(isQuietHours: Boolean) {
        val notification = createNotification(isQuietHours)
        startForeground(NOTIFICATION_ID, notification) // <- statt nur notify()
    }

    fun showUnreadMessages() {
        Log.d("QuietHoursService", "=== SHOW UNREAD MESSAGES ===")
        val replyActions = WhatsAppNotificationListener.replyActions
        val messagesByContact = WhatsAppNotificationListener.messagesByContact

        Log.d("QuietHoursService", "replyActions size: ${replyActions.size}")
        Log.d("QuietHoursService", "messagesByContact size: ${messagesByContact.size}")
        Log.d("QuietHoursService", "replyActions keys: ${replyActions.keys.joinToString()}")
        Log.d(
            "QuietHoursService",
            "messagesByContact keys: ${messagesByContact.keys.joinToString()}"
        )

        val mePerson = Person.Builder()
            .setName("Du")
            .setKey("me")
            .build()

        try {
            val unreadMessages = WhatsAppNotificationListener.getMessages()
            Log.d("QuietHoursService", "Total unread messages: ${unreadMessages.size}")

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
                Log.d(
                    "QuietHoursService",
                    "Creating notification for: $sender (ID: $notificationId)"
                )
                Log.d("QuietHoursService", "  Messages count: ${msgs.size}")

                val senderPerson = Person.Builder()
                    .setName(sender)
                    .setKey(sender)
                    .build()

                // NEU: ContentIntent für Voice Note Player
                val voiceNoteIntent =
                    Intent(this, QuietHoursNotificationService::class.java).apply {
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
                    .setGroup("whatsapp_unread_group")
                    .setAutoCancel(false)
                    .setOngoing(false)
                    .setContentIntent(voiceNotePendingIntent)

                val messagingStyle = NotificationCompat.MessagingStyle(mePerson)
                    .setConversationTitle(sender)

                msgs.takeLast(10).forEach { msg ->
                    val timeText =
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                    Log.d("QuietHoursService", "  Adding message: ${msg.text} at $timeText")
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
                    Log.d("QuietHoursService", "  Adding reply action")
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

// BILD-BENACHRICHTIGUNGEN (außerhalb des if-Blocks!)
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
                            Log.d(
                                "QuietHoursService",
                                "  ✓ Image notification posted for $sender (${index + 1}/${messagesWithImages.size})"
                            )
                        }
                    }
                }

                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    notificationManager.notify(notificationId, builder.build())
                    Log.d("QuietHoursService", "  ✓ Notification posted for $sender")
                } else {
                    Log.e("QuietHoursService", "  ✗ Missing POST_NOTIFICATIONS permission")
                }
            }

            // Summary notification
            val summaryNotification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.stat_notify_chat)
                .setContentTitle("WhatsApp Nachrichten")
                .setContentText("${unreadMessages.size} unbeantwortete Nachricht(en)")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup("whatsapp_unread_group")
                .setGroupSummary(true)
                .setAutoCancel(true)
                .build()

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(10000, summaryNotification)
                Log.d("QuietHoursService", "✓ Summary notification posted")
            }

            Log.d("QuietHoursService", "=== FINISHED SHOWING ${unreadMessages.size} MESSAGES ===")
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
        Log.d("QuietHoursService", "=== UPDATE SINGLE SENDER: $sender ===")

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

        // Voice Note Intent
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
            .setGroup("whatsapp_unread_group")
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

        // Reply Action hinzufügen
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
            Log.d("QuietHoursService", "✓ Updated notification for $sender")
        }
    }

    private fun openAndroidSettings() {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            Log.d("QuietHoursService", "Android Settings opened")
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
        val notificationId = 20000

        val testVoiceIntent = Intent(this, QuietHoursNotificationService::class.java).apply {
            action = ACTION_TEST_VOICE_NOTE
        }

        val testVoicePendingIntent = PendingIntent.getService(
            this,
            999,
            testVoiceIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(
                R.drawable.ic_btn_speak_now,
                "Test Voice",
                testVoicePendingIntent
            )
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
            Log.d("QuietHoursService", "=== HANDLE MESSAGE SENT ===")
            Log.d("QuietHoursService", "Sender: $sender")
            Log.d("QuietHoursService", "Message: $messageText")

            val originalReplyData = WhatsAppNotificationListener.replyActions[sender]
            val isRealWhatsApp =
                originalReplyData?.pendingIntent?.creatorPackage == "com.whatsapp" ||
                        originalReplyData?.pendingIntent?.creatorPackage == "com.whatsapp.w4b"

            Log.d("QuietHoursService", "Is real WhatsApp: $isRealWhatsApp")

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
                    Log.d("QuietHoursService", "Message sent to WhatsApp successfully")

                } catch (e: Exception) {
                    Log.e("QuietHoursService", "Failed to send to WhatsApp", e)
                    showSimpleNotification("Fehler", "Nachricht konnte nicht gesendet werden")
                    return
                }
            } else {
                Log.d("QuietHoursService", "Test notification - skipping WhatsApp send")
            }

            // NEU: Prüfe ob die Nachricht bereits existiert (Duplikat-Schutz)
            val messagesList =
                WhatsAppNotificationListener.messagesByContact.getOrPut(sender) { mutableListOf() }

            // Prüfe die letzten 3 Nachrichten auf Duplikate (gleicher Text innerhalb von 2 Sekunden)
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
                Log.d("QuietHoursService", "✓ Message added to local list")
            } else {
                Log.d("QuietHoursService", "⚠ Duplicate message detected, skipping add")
            }

            // Aktualisiere NUR unsere eigene Benachrichtigung
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

            // Alle Nachrichten dieser Konversation hinzufügen
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

            // Reply-Action hinzufügen
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
                .setGroup("whatsapp_unread_group")
                .setOnlyAlertOnce(true)
                .addAction(replyAction)

            val notificationManager = getSystemService(NotificationManager::class.java)
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {

                notificationManager.notify(notificationId, builder.build())
                Log.d("QuietHoursService", "✓ Chat notification updated for $sender")
            }

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error updating chat notification", e)
        }
    }

    private fun openMusicPlayer() {
        try {
            // Starte MusicPlayerService
            MusicPlayerService.startService(this)

            // Sende Play-Action zum Service
            MusicPlayerService.sendPlayAction(this)

            Log.d("QuietHoursService", "Music Player opened and play command sent")
        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error opening music player", e)
            showSimpleNotification("Fehler", "Musik Player konnte nicht geöffnet werden")
        }
    }

    private fun restartMusicPlayer() {
        try {
            // Stoppe den alten Service komplett
            MusicPlayerService.stopService(this)

            // Kurze Verzögerung um sicherzustellen, dass der Service komplett gestoppt ist
            Handler(Looper.getMainLooper()).postDelayed({
                // Starte den Service neu
                MusicPlayerService.startService(this)

                // Sende Play-Action
                Handler(Looper.getMainLooper()).postDelayed({
                    MusicPlayerService.sendPlayAction(this)
                }, 300)
            }, 500)

            Log.d("QuietHoursService", "Music Player restarted")
        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error restarting music player", e)
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

            // Prüfe ob im Launcher sichtbar
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
            // Verschiedene mögliche Pfade testen
            val possiblePaths = listOf(
                "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes",
                "${Environment.getExternalStorageDirectory()}/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes",
                "/sdcard/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes",
                "/storage/emulated/0/WhatsApp/Media/WhatsApp Voice Notes"
            )

            Log.d("QuietHoursService", "=== VOICE NOTE SEARCH ===")

            for (path in possiblePaths) {
                Log.d("QuietHoursService", "Trying path: $path")
                val mainDir = File(path)

                if (mainDir.exists()) {
                    Log.d("QuietHoursService", "✓ Path exists: $path")
                    Log.d("QuietHoursService", "Is directory: ${mainDir.isDirectory}")

                    val subDirs = mainDir.listFiles()
                    Log.d("QuietHoursService", "Subdirectories found: ${subDirs?.size ?: 0}")

                    subDirs?.forEach { subDir ->
                        Log.d(
                            "QuietHoursService",
                            "  - ${subDir.name} (isDir: ${subDir.isDirectory})"
                        )
                    }

                    val allFiles = mutableListOf<File>()

                    subDirs?.forEach { subDir ->
                        if (subDir.isDirectory) {
                            val files = subDir.listFiles { file ->
                                file.extension.lowercase() == "opus"
                            }

                            Log.d(
                                "QuietHoursService",
                                "    Files in ${subDir.name}: ${files?.size ?: 0}"
                            )

                            files?.forEach { file ->
                                Log.d("QuietHoursService", "      Found: ${file.name}")
                                allFiles.add(file)
                            }
                        }
                    }

                    if (allFiles.isNotEmpty()) {
                        Log.d("QuietHoursService", "Total .opus files found: ${allFiles.size}")
                        // Sortiere nach letzter Änderung (neueste zuerst)
                        return allFiles.sortedByDescending { it.lastModified() }
                    }
                } else {
                    Log.d("QuietHoursService", "✗ Path does not exist: $path")
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

            // Stoppe vorherigen Player
            voiceNotePlayer?.release()
            voiceNotePlayer = null

            val file = voiceNoteFiles[index]

            voiceNotePlayer = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnPreparedListener { mp ->
                    mp.start()
                    showVoiceNotePlayerNotification(file, true)
                    Log.d("QuietHoursService", "Playing voice note: ${file.name}")
                }
                setOnCompletionListener {
                    showVoiceNotePlayerNotification(file, false)
                    Log.d("QuietHoursService", "Voice note completed")
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
            notificationManager.cancel(40000) // Voice Note Player Notification ID

            Log.d("QuietHoursService", "Voice note stopped")
        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error stopping voice note", e)
        }
    }

    private fun showVoiceNotePlayerNotification(file: File, isPlaying: Boolean) {
        try {
            val fileName = file.name
            val fileDate = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                .format(Date(file.lastModified()))

            // Previous Button
            val prevIntent = Intent(this, QuietHoursNotificationService::class.java).apply {
                action = ACTION_PREV_VOICE_NOTE
            }
            val prevPendingIntent = PendingIntent.getService(
                this, 41, prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Play/Stop Button - FIX: Korrektes if-else
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

            // Next Button
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
                // FIX: Entferne MediaStyle oder verwende einfachen Style
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

    // Test-Funktion am Ende der Klasse hinzufügen
    private fun createTestVoiceNoteNotification() {
        try {
            val testSender = "Test Voice Note"
            val notificationId = 99999

            // Voice Note Intent
            val voiceNoteIntent = Intent(this, QuietHoursNotificationService::class.java).apply {
                action = ACTION_PLAY_VOICE_NOTE
                putExtra(EXTRA_SENDER_FOR_VOICE, testSender)
            }
            val voiceNotePendingIntent = PendingIntent.getService(
                this,
                notificationId,
                voiceNoteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_btn_speak_now)
                .setContentTitle("🎤 Test Voice Note")
                .setContentText("Klick hier um Voice Notes abzuspielen")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(false)
                .setContentIntent(voiceNotePendingIntent)
                .build()

            val notificationManager = getSystemService(NotificationManager::class.java)
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(notificationId, notification)
                Log.d("QuietHoursService", "Test Voice Note notification created")
            }

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error creating test voice note notification", e)
        }
    }

    private fun showFlashlightLevelInfo() {
        val cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList.firstOrNull() ?: return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
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
            } else {
                showSimpleNotification(
                    "⚠️ Taschenlampe Info",
                    "Android Version zu alt (< 13)\nNur Ein/Aus verfügbar",
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
            Log.d("QuietHoursService", "✓ TimeControl started via Method 1 (ComponentName)")

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

            // Schritt 1: App aktivieren falls disabled
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)

                if (!appInfo.enabled) {
                    pm.setApplicationEnabledSetting(
                        packageName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        0
                    )
                    Log.d("QuietHoursService", "✓ App enabled via PackageManager")

                    // Kurze Verzögerung nach Aktivierung
                    Handler(Looper.getMainLooper()).postDelayed({
                        launchTimeControlApp(packageName)
                    }, 500)

                    showSimpleNotification(
                        "✅ App aktiviert",
                        "TimeControl wurde aktiviert und wird gestartet..."
                    )
                } else {
                    Log.d("QuietHoursService", "App is already enabled, launching directly")
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
                // Prüfe ob App installiert ist
                pm.getApplicationInfo(packageName, 0)

                // Setze explizit den Enabled State
                pm.setApplicationEnabledSetting(
                    packageName,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )

                Log.d("QuietHoursService", "✓ Application enabled state set")

                // Verzögerung für State Change
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        // Versuche Launch Intent
                        val launchIntent = pm.getLaunchIntentForPackage(packageName)

                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            startActivity(launchIntent)

                            showSimpleNotification(
                                "✅ Methode 10 erfolgreich",
                                "TimeControl wurde via setApplicationEnabledSetting gestartet"
                            )
                            Log.d("QuietHoursService", "✓ TimeControl started via Method 10")

                        } else {
                            // Fallback: Direkte ComponentName
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
                            Log.d(
                                "QuietHoursService",
                                "✓ TimeControl started via Method 10 (fallback)"
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

    // Hilfsfunktion zum Starten der App
    private fun launchTimeControlApp(packageName: String) {
        try {
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)

            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                startActivity(launchIntent)
                Log.d("QuietHoursService", "✓ TimeControl launched via LaunchIntent")
            } else {
                // Fallback: Direkte ComponentName
                val directIntent = Intent().apply {
                    component = ComponentName(packageName, "$packageName.MainActivity")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(directIntent)
                Log.d("QuietHoursService", "✓ TimeControl launched via ComponentName (fallback)")
            }

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Failed to launch TimeControl", e)
            throw e
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