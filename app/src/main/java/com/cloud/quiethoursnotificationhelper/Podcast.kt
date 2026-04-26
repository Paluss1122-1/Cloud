package com.cloud.quiethoursnotificationhelper

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.cloud.core.objects.Config.COMPLETED_PODCASTS
import com.cloud.core.objects.Config.PD_QUEUE
import com.cloud.core.objects.Config.PODCASTS
import com.cloud.core.functions.errorInsert
import com.cloud.core.functions.ERRORINSERTDATA
import com.cloud.core.functions.showSimpleNotificationExtern
import com.cloud.services.PodcastPlayerServiceCompat.startService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

data class SimplePodcast(val name: String, val path: String)

fun clearPodcastSelectionNotifications(context: Context) {
    try {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.activeNotifications
            .filter { it.id in PODCASTS..(PODCASTS + 998) || it.id in COMPLETED_PODCASTS..(COMPLETED_PODCASTS + 999) }
            .forEach { notificationManager.cancel(it.id) }

        showSimpleNotificationExtern(
            "✅ Notifications gelöscht",
            "Alle Podcast-Auswahl Notifications wurden entfernt",
            Duration.ZERO,
            context
        )
    } catch (e: Exception) {
        showSimpleNotificationExtern(
            "❌ Fehler",
            "Konnte Notifications nicht löschen: ${e.message}",
            Duration.ZERO,
            context
        )

        CoroutineScope(Dispatchers.IO).launch {
            errorInsert(
                ERRORINSERTDATA(
                    "markMessageAsRead",
                    "Konnte Notifications nicht löschen: ${e.message}",
                    Instant.now().toString(),
                    "ERROR"
                )
            )
        }
    }
}

fun loadPodcastsFromMediaStore(context: Context): List<SimplePodcast> {
    val podcasts = mutableListOf<SimplePodcast>()

    try {
        val projection = arrayOf(
            android.provider.MediaStore.Audio.Media.DISPLAY_NAME,
            android.provider.MediaStore.Audio.Media.DATA,
            android.provider.MediaStore.Audio.Media.TITLE
        )

        val sortOrder = "${android.provider.MediaStore.Audio.Media.DISPLAY_NAME} ASC"

        context.contentResolver.query(
            android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val nameColumn =
                cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DISPLAY_NAME)
            val dataColumn =
                cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
            val titleColumn =
                cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.TITLE)

            while (cursor.moveToNext()) {
                val name = cursor.getString(nameColumn) ?: continue
                val data = cursor.getString(dataColumn) ?: continue
                val title = cursor.getString(titleColumn)

                val normalizedPath = try {
                    java.net.URLDecoder.decode(data, "UTF-8")
                        .replace("\\", "/")
                        .lowercase()
                } catch (_: Exception) {
                    data.replace("\\", "/").lowercase()
                }

                val isInPodcasts = normalizedPath.contains("/download/cloud/podcasts/") ||
                        normalizedPath.contains("/downloads/cloud/podcasts/") ||
                        data.contains("/Cloud/Podcasts/", ignoreCase = true)

                if (isInPodcasts && (name.endsWith(".mp3") || name.endsWith(".m4a"))) {
                    val displayName = if (!title.isNullOrBlank() && title != "<unknown>") {
                        title
                    } else {
                        name.substringBeforeLast('.')
                    }

                    podcasts.add(SimplePodcast(displayName, data))
                }
            }
        }
    } catch (e: Exception) {
        CoroutineScope(Dispatchers.IO).launch {
            errorInsert(
                ERRORINSERTDATA(
                    "loadPodcastsFromMediaStore",
                    "Fehler bei Laden von Podcasts von MediaStore: ${e.message}",
                    Instant.now().toString(),
                    "ERROR"
                )
            )
        }
    }

    return podcasts.sortedBy { it.name }
}

fun getAllPodcastsFromPrefs(context: Context): List<SimplePodcast> {
    return loadPodcastsFromMediaStore(context)
}

fun showPodcastQueue(context: Context) {
    try {
        startService(context)

        Handler(Looper.getMainLooper()).postDelayed({
            val queuePaths = getPodcastQueueFromService(context)

            if (queuePaths.isEmpty()) {
                showSimpleNotificationExtern(
                    "📋 Queue leer",
                    "Keine Podcasts in der Warteschlange",
                    Duration.ZERO,
                    context
                )
                return@postDelayed
            }

            val queueText = queuePaths.mapIndexed { index, path ->
                val name = path.substringAfterLast("/").substringBeforeLast(".")
                "${index + 1}. $name"
            }.joinToString("\n")

            val notification = NotificationCompat.Builder(context, "quiet_hours_channel")
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("📋 Podcast Queue (${queuePaths.size})")
                .setContentText(
                    queuePaths.firstOrNull()?.substringAfterLast("/")?.substringBeforeLast(".")
                        ?: ""
                )
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(queueText)
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                notificationManager.notify(PD_QUEUE, notification)
            }
        }, 300)

    } catch (_: Exception) {
        showSimpleNotificationExtern(
            "❌ Fehler",
            "Queue konnte nicht angezeigt werden",
            Duration.ZERO,
            context
        )
    }
}

fun addPodcastToQueue(index: Int, context: Context) {
    try {
        val allPodcasts = getAllPodcastsFromPrefs(context)

        if (index < 0 || index >= allPodcasts.size) {
            showSimpleNotificationExtern(
                "❌ Ungültiger Index",
                "Podcast ${index - 1} existiert nicht (1-${allPodcasts.size})",
                20.seconds,
                context
            )
            return
        }

        val podcast = allPodcasts[index]
        addToQueueViaService(podcast.path, context)

        showSimpleNotificationExtern(
            "✅ Zur Queue hinzugefügt",
            "${index + 1}. ${podcast.name}",
            Duration.ZERO,
            context
        )

    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error adding to queue", e)
        showSimpleNotificationExtern(
            "❌ Fehler",
            "Konnte nicht zur Queue hinzufügen",
            Duration.ZERO,
            context
        )
    }
}

fun removePodcastFromQueue(position: Int, context: Context) {
    try {
        val queuePaths = getPodcastQueueFromService(context)

        if (position < 0 || position >= queuePaths.size) {
            showSimpleNotificationExtern(
                "❌ Ungültige Position",
                "Position ${position + 1} existiert nicht (1-${queuePaths.size})",
                20.seconds,
                context
            )
            return
        }

        val path = queuePaths[position]
        removeFromQueueViaService(path, context)

        val name = path.substringAfterLast("/").substringBeforeLast(".")
        showSimpleNotificationExtern(
            "✅ Aus Queue entfernt",
            "${position + 1}. $name",
            Duration.ZERO,
            context
        )

    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error removing from queue", e)
        showSimpleNotificationExtern(
            "❌ Fehler",
            "Konnte nicht aus Queue entfernen",
            Duration.ZERO,
            context
        )
    }
}

fun clearPodcastQueue(context: Context) {
    try {
        val prefs = context.getSharedPreferences("podcast_player_prefs", MODE_PRIVATE)
        prefs.edit(commit = true) {
            putString("podcast_queue", "")
        }

        showSimpleNotificationExtern(
            "✅ Queue geleert",
            "Alle Podcasts aus der Warteschlange entfernt",
            Duration.ZERO,
            context
        )

    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error clearing queue", e)
        showSimpleNotificationExtern(
            "❌ Fehler",
            "Queue konnte nicht geleert werden",
            Duration.ZERO,
            context
        )
    }
}

fun getPodcastQueueFromService(context: Context): List<String> {
    val prefs = context.getSharedPreferences("podcast_player_prefs", MODE_PRIVATE)
    return prefs.getString("podcast_queue", "")
        ?.takeIf { it.isNotEmpty() }
        ?.split("|||")
        ?: emptyList()
}

fun addToQueueViaService(path: String, context: Context) {
    val prefs = context.getSharedPreferences("podcast_player_prefs", MODE_PRIVATE)
    val currentQueue = getPodcastQueueFromService(context).toMutableList()

    if (!currentQueue.contains(path)) {
        currentQueue.add(path)
        val queueJson = currentQueue.joinToString("|||")
        prefs.edit(commit = true) {
            putString("podcast_queue", queueJson)
        }
    }
}

fun removeFromQueueViaService(path: String, context: Context) {
    val prefs = context.getSharedPreferences("podcast_player_prefs", MODE_PRIVATE)
    val currentQueue = getPodcastQueueFromService(context).toMutableList()

    currentQueue.remove(path)
    val queueJson = currentQueue.joinToString("|||")
    prefs.edit(commit = true) {
        putString("podcast_queue", queueJson)
    }
}