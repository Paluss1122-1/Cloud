package com.tabslify.quiethoursnotificationhelper

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.pm.PackageManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.tabslify.R
import com.tabslify.core.functions.errorInsert
import com.tabslify.core.functions.showSimpleNotificationExtern
import com.tabslify.core.objects.Config.COMPLETED_PODCASTS
import com.tabslify.core.objects.Config.PD_QUEUE
import com.tabslify.core.objects.Config.PODCASTS
import com.tabslify.core.objects.tNotify
import com.tabslify.services.PodcastPlayerServiceCompat.startService
import java.io.File
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
            context.getString(R.string.notifications_geloscht),
            context.getString(R.string.alle_podcast_auswahl_notifications_wurden),
            Duration.ZERO,
            context
        )
    } catch (e: Exception) {
        showSimpleNotificationExtern(
            context.getString(R.string.fehler_2),
            context.getString(R.string.konnte_notifications_nicht_loschen, e.message),
            Duration.ZERO,
            context
        )

        errorInsert(
            "clearPodcastSelectionNotification",
            "Konnte Notifications nicht löschen: ${e.message}",
            Instant.now().toString(),
            "ERROR"
        )
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

                val externalRoot = Environment.getExternalStorageDirectory().path
                val podcastTabslifyDir = File("$externalRoot/Podcasts/Tabslify")
                if (!podcastTabslifyDir.exists()) podcastTabslifyDir.mkdirs()

                val externalRootLower = externalRoot.lowercase()
                val isInPodcasts = normalizedPath.contains("/emulated/0/podcasts/tabslify/") ||
                        normalizedPath.contains("$externalRootLower/podcasts/tabslify/")

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
        errorInsert(
            "loadPodcastsFromMediaStore",
            "Fehler bei Laden von Podcasts von MediaStore: ${e.message}",
            Instant.now().toString(),
            "ERROR"
        )
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
                    context.getString(R.string.queue_leer),
                    context.getString(R.string.keine_podcasts_in_der_warteschlange),
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
                .setContentTitle(context.getString(R.string.podcast_queue, queuePaths.size))
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

            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED
            ) {
                tNotify(context, PD_QUEUE, notification)
            }
        }, 300)

    } catch (_: Exception) {
        showSimpleNotificationExtern(
            context.getString(R.string.fehler_2),
            context.getString(R.string.queue_konnte_nicht_angezeigt_werden),
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
                context.getString(R.string.ungultiger_index),
                context.getString(R.string.podcast_existiert_nicht_1, index - 1, allPodcasts.size),
                20.seconds,
                context
            )
            return
        }

        val podcast = allPodcasts[index]
        addToQueueViaService(podcast.path, context)

        showSimpleNotificationExtern(
            context.getString(R.string.zur_queue_hinzugefugt),
            "${index + 1}. ${podcast.name}",
            Duration.ZERO,
            context
        )

    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error adding to queue", e)
        showSimpleNotificationExtern(
            context.getString(R.string.fehler_2),
            context.getString(R.string.konnte_nicht_zur_queue_hinzufugen),
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
                context.getString(R.string.ungultige_position),
                context.getString(R.string.position_existiert_nicht_1, position + 1, queuePaths.size),
                20.seconds,
                context
            )
            return
        }

        val path = queuePaths[position]
        removeFromQueueViaService(path, context)

        val name = path.substringAfterLast("/").substringBeforeLast(".")
        showSimpleNotificationExtern(
            context.getString(R.string.aus_queue_entfernt),
            "${position + 1}. $name",
            Duration.ZERO,
            context
        )

    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error removing from queue", e)
        showSimpleNotificationExtern(
            context.getString(R.string.fehler_2),
            context.getString(R.string.konnte_nicht_aus_queue_entfernen),
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
            context.getString(R.string.queue_geleert),
            context.getString(R.string.alle_podcasts_aus_der_warteschlange),
            Duration.ZERO,
            context
        )

    } catch (e: Exception) {
        Log.e("QuietHoursService", "Error clearing queue", e)
        showSimpleNotificationExtern(
            context.getString(R.string.fehler_2),
            context.getString(R.string.queue_konnte_nicht_geleert_werden),
            Duration.ZERO,
            context
        )
    }
}

fun getPodcastQueueFromService(context: Context): List<String> {
    val prefs = context.getSharedPreferences("podcast_player_prefs", MODE_PRIVATE)
    val queue = prefs.getString("podcast_queue", "")
        ?.takeIf { it.isNotEmpty() }
        ?.split("|||")
        ?: emptyList()

    val existing = queue.filter { File(it).exists() }
    if (existing.size != queue.size) {
        prefs.edit(commit = true) {
            putString("podcast_queue", existing.joinToString("|||"))
        }
    }
    return existing
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
