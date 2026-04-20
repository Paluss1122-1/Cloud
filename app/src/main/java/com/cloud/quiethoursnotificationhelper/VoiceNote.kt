package com.cloud.quiethoursnotificationhelper

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Environment
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cloud.core.objects.Config.VOICE_NOTE
import com.cloud.core.functions.showSimpleNotificationExtern
import com.cloud.services.QuietHoursNotificationService
import com.cloud.services.QuietHoursNotificationService.Companion.ACTION_NEXT_VOICE_NOTE
import com.cloud.services.QuietHoursNotificationService.Companion.ACTION_PLAY_VOICE_NOTE
import com.cloud.services.QuietHoursNotificationService.Companion.ACTION_PREV_VOICE_NOTE
import com.cloud.services.QuietHoursNotificationService.Companion.ACTION_STOP_VOICE_NOTE
import com.cloud.services.QuietHoursNotificationService.Companion.EXTRA_SENDER_FOR_VOICE
import com.cloud.services.QuietHoursNotificationService.Companion.MAX_VOICE_NOTE_FILES
import com.cloud.services.QuietHoursNotificationService.Companion.VOICE_NOTE_CHANNEL_ID
import com.cloud.services.QuietHoursNotificationService.Companion.currentSenderForVoiceNote
import com.cloud.services.QuietHoursNotificationService.Companion.currentVoiceNoteIndex
import com.cloud.services.QuietHoursNotificationService.Companion.mainHandler
import com.cloud.services.QuietHoursNotificationService.Companion.voiceNoteFiles
import com.cloud.services.QuietHoursNotificationService.Companion.voiceNotePlayer
import com.cloud.services.QuietHoursNotificationService.Companion.workerHandler
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun playLatestVoiceNote(sender: String, context: Context) {
    try {
        currentSenderForVoiceNote = sender
        workerHandler.post {
            try {
                val files = getVoiceNoteFiles()

                mainHandler.post {
                    voiceNoteFiles = files

                    if (voiceNoteFiles.isEmpty()) {
                        showSimpleNotificationExtern(
                            "Keine Sprachnachrichten",
                            "Keine .opus Dateien gefunden",
                            context = context
                        )
                        return@post
                    }

                    currentVoiceNoteIndex = 0
                    playVoiceNoteAtIndex(currentVoiceNoteIndex, context)
                }
            } catch (e: Exception) {
                mainHandler.post {
                    showSimpleNotificationExtern(
                        "Fehler",
                        "Sprachnachrichten konnten nicht geladen werden",
                        context = context
                    )
                }
            }
        }
    } catch (e: Exception) {
        showSimpleNotificationExtern(
            "Fehler",
            "Sprachnachricht konnte nicht abgespielt werden",
            context = context
        )
    }
}


fun playVoiceNoteAtIndex(index: Int, context: Context) {
    try {
        if (index < 0 || index >= voiceNoteFiles.size) {
            showSimpleNotificationExtern("Fehler", "Ungültiger Index", context = context)
            return
        }

        voiceNotePlayer?.release()
        voiceNotePlayer = null

        val file = voiceNoteFiles[index]

        voiceNotePlayer = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnPreparedListener { mp ->
                mp.start()
                showVoiceNotePlayerNotification(file, true, context)
            }
            setOnCompletionListener { mp ->
                mp.release()
                voiceNotePlayer = null
                showVoiceNotePlayerNotification(file, false, context)
            }
            setOnErrorListener { _, what, extra ->
                Log.e("QuietHoursService", "MediaPlayer error: what=$what, extra=$extra")
                showSimpleNotificationExtern("Fehler", "Fehler beim Abspielen", context = context)
                true
            }
            prepareAsync()
        }

    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error playing voice note at index $index", e)
        showSimpleNotificationExtern(
            "Fehler",
            "Sprachnachricht konnte nicht abgespielt werden: ${e.message}",
            context = context
        )
    }
}


fun getVoiceNoteFiles(): List<File> {
    try {
        val possiblePaths = listOf(
            "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes",
            "${Environment.getExternalStorageDirectory()}/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes",
            "/storage/emulated/0/WhatsApp/Media/WhatsApp Voice Notes"
        )

        for (path in possiblePaths) {
            val mainDir = File(path)
            if (!mainDir.exists()) continue

            val allFiles = mainDir.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() == "opus" }
                .sortedByDescending { it.lastModified() }
                .take(MAX_VOICE_NOTE_FILES)
                .toList()

            if (allFiles.isNotEmpty()) {
                return allFiles
            }
        }

        return emptyList()
    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error getting voice notes", e)
        return emptyList()
    }
}

fun playNextVoiceNote(context: Context) {
    if (voiceNoteFiles.isEmpty()) return

    currentVoiceNoteIndex = (currentVoiceNoteIndex + 1) % voiceNoteFiles.size

    playVoiceNoteAtIndex(
        currentVoiceNoteIndex,
        context
    )
}

fun playPreviousVoiceNote(context: Context) {
    if (voiceNoteFiles.isEmpty()) return

    currentVoiceNoteIndex = if (currentVoiceNoteIndex - 1 < 0) {
        voiceNoteFiles.size - 1
    } else {
        currentVoiceNoteIndex - 1
    }
    playVoiceNoteAtIndex(
        currentVoiceNoteIndex,
        context
    )
}

fun stopVoiceNote(context: Context) {
    try {
        voiceNotePlayer?.apply {
            if (isPlaying) stop()
            reset()
            release()
        }
        voiceNotePlayer = null
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancel(VOICE_NOTE)
    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error stopping voice note", e)
    }
}


private fun showVoiceNotePlayerNotification(file: File, isPlaying: Boolean, context: Context) {
    try {
        val fileName = file.name
        val fileDate = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            .format(Date(file.lastModified()))

        val prevIntent = Intent(context, QuietHoursNotificationService::class.java).apply {
            action = ACTION_PREV_VOICE_NOTE
        }
        val prevPendingIntent = PendingIntent.getService(
            context, 41, prevIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playStopIntent = Intent(context, QuietHoursNotificationService::class.java).apply {
            if (isPlaying) {
                action = ACTION_STOP_VOICE_NOTE
            } else {
                action = ACTION_PLAY_VOICE_NOTE
                putExtra(EXTRA_SENDER_FOR_VOICE, currentSenderForVoiceNote)
            }
        }
        val playStopPendingIntent = PendingIntent.getService(
            context, 42, playStopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = Intent(context, QuietHoursNotificationService::class.java).apply {
            action = ACTION_NEXT_VOICE_NOTE
        }
        val nextPendingIntent = PendingIntent.getService(
            context, 43, nextIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, VOICE_NOTE_CHANNEL_ID)
            .setSmallIcon(if (isPlaying) android.R.drawable.ic_media_play else android.R.drawable.ic_media_pause)
            .setContentTitle("${if (isPlaying) "▶️" else "⏸️"} Sprachnachricht")
            .setContentText("$fileName • $fileDate")
            .setSubText("${currentVoiceNoteIndex + 1} von ${voiceNoteFiles.size}")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(isPlaying)
            .setAutoCancel(!isPlaying)
            .setGroup("group_media")
            .setGroupSummary(false)
            .addAction(android.R.drawable.ic_media_previous, "Zurück", prevPendingIntent)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Stop" else "Play",
                playStopPendingIntent
            )
            .addAction(android.R.drawable.ic_media_next, "Weiter", nextPendingIntent)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            notificationManager.notify(VOICE_NOTE, notification)
        }

    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error showing voice note player notification", e)
    }
}