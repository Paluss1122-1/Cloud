package com.cloud.quiethoursnotificationhelper

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Context.AUDIO_SERVICE
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cloud.core.objects.Config.cms
import com.cloud.core.functions.showSimpleNotificationExtern
import com.cloud.core.objects.Config
import com.cloud.services.ChatService
import com.cloud.services.QuietHoursNotificationService.Companion.CHANNEL_ID
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.seconds

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

            val supabase = Config.client

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
            .setSmallIcon(android.R.drawable.ic_dialog_email)
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
            notificationManager.notify(cms(), notification)
        }

    } catch (e: Exception) {
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