package com.example.cloud.quiethoursnotificationhelper

import android.Manifest
import android.R
import android.app.NotificationManager
import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.cloud.SupabaseConfigALT
import com.example.cloud.mediarecorder.AudioRecorder
import com.example.cloud.service.ChatService
import com.example.cloud.service.QuietHoursNotificationService.Companion.CHANNEL_ID
import com.example.cloud.service.QuietHoursNotificationService.Companion.audioRecorder
import com.example.cloud.service.QuietHoursNotificationService.Companion.currentRecordingFile
import com.example.cloud.showSimpleNotificationExtern
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

fun startAudioRecording(context: Context) {
    try {
        if (audioRecorder != null) {
            showSimpleNotificationExtern(
                "⚠️ Aufnahme läuft",
                "Es läuft bereits eine Aufnahme",
                context = context
            )
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            showSimpleNotificationExtern(
                "❌ Berechtigung fehlt",
                "RECORD_AUDIO fehlt. Bitte gewähre die Berechtigung in den Einstellungen.",
                context = context
            )
            return
        }

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val outFile = File(dir, "rec_${timestamp}.m4a")

        audioRecorder = AudioRecorder()
        audioRecorder?.startRecording(outFile)
        currentRecordingFile = outFile

        showSimpleNotificationExtern(
            "✅ Aufnahme gestartet",
            "Datei: ${outFile.name}",
            context = context
        )
    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error starting audio recording", e)
        showSimpleNotificationExtern(
            "❌ Fehler",
            "Aufnahme konnte nicht gestartet werden",
            context = context
        )
        audioRecorder = null
        currentRecordingFile = null
    }
}

fun stopAudioRecording(context: Context) {
    try {
        if (audioRecorder == null) {
            showSimpleNotificationExtern(
                "ℹ️ Keine Aufnahme",
                "Es läuft keine Aufnahme",
                context = context
            )
            return
        }

        audioRecorder?.stopRecording()
        audioRecorder = null

        val filename = currentRecordingFile?.name ?: "unbekannt"
        showSimpleNotificationExtern(
            "✅ Aufnahme beendet",
            "Datei gespeichert: $filename",
            context = context
        )
        currentRecordingFile = null
    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error stopping audio recording", e)
        showSimpleNotificationExtern(
            "❌ Fehler",
            "Aufnahme konnte nicht gestoppt werden",
            context = context
        )
    }
}

@OptIn(DelicateCoroutinesApi::class)
fun showLastFriendMessages(context: Context) {
    GlobalScope.launch(Dispatchers.IO) {
        try {
            Handler(Looper.getMainLooper()).post {
                showSimpleNotificationExtern(
                    "📨 Lade Nachrichten...",
                    "Rufe letzte Nachrichten von friend ab",
                    3.seconds,
                    context
                )
            }

            val supabase = SupabaseConfigALT.client

            val response = supabase.from("messages")
                .select {
                    filter {
                        eq("sender_id", "friend")
                        eq("receiver_id", "you")
                    }
                    order("created_at", Order.DESCENDING)
                    limit(10)
                }

            val messages = response.decodeList<ChatService.Message>()
                .reversed()

            if (messages.isEmpty()) {
                Handler(Looper.getMainLooper()).post {
                    showSimpleNotificationExtern(
                        "📭 Keine Nachrichten",
                        "Keine Nachrichten von friend gefunden",
                        context = context
                    )
                }
                return@launch
            }

            Handler(Looper.getMainLooper()).post {
                showFriendMessagesNotification(messages, context)
            }

        } catch (e: Exception) {
            Log.e("QuietHoursService", "Error loading friend messages", e)
            Handler(Looper.getMainLooper()).post {
                showSimpleNotificationExtern(
                    "❌ Fehler",
                    "Nachrichten konnten nicht geladen werden: ${e.message}",
                    20.seconds,
                    context
                )
            }
        }
    }
}

private fun showFriendMessagesNotification(messages: List<ChatService.Message>, context: Context) {
    try {
        val messageText = messages.joinToString("\n\n") { msg ->
            val timeStr = try {
                msg.created_at?.let {
                    val instant = Instant.parse(it)
                    val formatter = DateTimeFormatter
                        .ofPattern("dd.MM.yyyy HH:mm")
                        .withZone(ZoneId.systemDefault())
                    formatter.format(instant)
                }
            } catch (_: Exception) {
                msg.created_at?.take(16)?.replace("T", " ")
            } ?: "Unbekannt"

            "🕐 $timeStr\n${msg.content}"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
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

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(60100, notification)
        }

    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error showing friend messages notification", e)
        showSimpleNotificationExtern(
            "❌ Fehler",
            "Benachrichtigung konnte nicht angezeigt werden: ${e.message}",
            20.seconds,
            context
        )
    }
}

fun setSoundMode(mode: String, context: Context) {
    try {
        val audioManager = context.getSystemService(AUDIO_SERVICE) as AudioManager

        if (!canWriteSettings(context)) {
            showSimpleNotificationExtern(
                "❌ Keine Berechtigung",
                "WRITE_SETTINGS Berechtigung fehlt. Aktiviere sie manuell.",
                20.seconds,
                context
            )
            return
        }

        when (mode.lowercase()) {
            "vibrate", "vib", "v" -> {
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE

                try {
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.VIBRATE_ON,
                        1
                    )
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SOUND_EFFECTS_ENABLED,
                        0
                    )
                } catch (e: Exception) {
                    Log.w("QuietHoursService", "Could not set vibration settings", e)
                }

                showSimpleNotificationExtern(
                    "📳 Vibration aktiviert",
                    "Nur Vibrationen, keine Töne",
                    context = context
                )
            }

            "silent", "mute", "m" -> {
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT

                try {
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.VIBRATE_ON,
                        0
                    )
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SOUND_EFFECTS_ENABLED,
                        0
                    )
                } catch (e: Exception) {
                    Log.w("QuietHoursService", "Could not set silent settings", e)
                }

                showSimpleNotificationExtern(
                    "🔇 Stumm",
                    "Keine Töne, keine Vibrationen",
                    context = context
                )
            }

            "normal", "loud", "l", "on" -> {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL

                try {
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.VIBRATE_ON,
                        1
                    )
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SOUND_EFFECTS_ENABLED,
                        1
                    )
                } catch (e: Exception) {
                    Log.w("QuietHoursService", "Could not set normal settings", e)
                }

                showSimpleNotificationExtern(
                    "🔊 Normal",
                    "Töne und Vibrationen aktiviert",
                    context = context
                )
            }

            else -> {
                showSimpleNotificationExtern(
                    "❌ Ungültig",
                    "Nutze: sound [vibrate|silent|normal]",
                    20.seconds,
                    context
                )
            }
        }

    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error setting sound mode", e)
        showSimpleNotificationExtern(
            "❌ Fehler",
            "Sound-Modus konnte nicht geändert werden: ${e.message}",
            20.seconds,
            context
        )
    }
}

private fun canWriteSettings(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}