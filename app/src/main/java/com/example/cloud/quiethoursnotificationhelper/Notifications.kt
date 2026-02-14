package com.example.cloud.quiethoursnotificationhelper

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Context.ALARM_SERVICE
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.cloud.service.QuietHoursNotificationService
import com.example.cloud.service.QuietHoursNotificationService.Companion.ACTION_CHANGE_END
import com.example.cloud.service.QuietHoursNotificationService.Companion.ACTION_CHANGE_START
import com.example.cloud.service.QuietHoursNotificationService.Companion.ACTION_EXECUTE_COMMAND
import com.example.cloud.service.QuietHoursNotificationService.Companion.ACTION_NOTIFICATION_DISMISSED
import com.example.cloud.service.QuietHoursNotificationService.Companion.ACTION_RESTART_MUSIC_PLAYER
import com.example.cloud.service.QuietHoursNotificationService.Companion.ACTION_SHOW_MESSAGES
import com.example.cloud.service.QuietHoursNotificationService.Companion.ALARM_REQUEST_CODE
import com.example.cloud.service.QuietHoursNotificationService.Companion.CHANNEL_ID
import com.example.cloud.service.QuietHoursNotificationService.Companion.CONFIRMATION_CHANNEL_ID
import com.example.cloud.service.QuietHoursNotificationService.Companion.DELETE_CONFIRMATION_CHANNEL_ID
import com.example.cloud.service.QuietHoursNotificationService.Companion.GALLERY_CHANNEL_ID
import com.example.cloud.service.QuietHoursNotificationService.Companion.NOTIFICATION_ID
import com.example.cloud.service.QuietHoursNotificationService.Companion.SSN_CHANNEL_ID
import com.example.cloud.service.QuietHoursNotificationService.Companion.THRESHOLD_MINUTES
import com.example.cloud.service.QuietHoursNotificationService.Companion.VOICE_NOTE_CHANNEL_ID
import com.example.cloud.service.QuietHoursNotificationService.Companion.calculateNextStatusChange
import com.example.cloud.service.QuietHoursNotificationService.Companion.handler
import com.example.cloud.service.QuietHoursNotificationService.Companion.isCurrentlyQuietHours
import com.example.cloud.service.QuietHoursNotificationService.Companion.workerHandler
import com.example.cloud.showSimpleNotificationExtern
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

fun checkQuietHours(context: Context) {
    val wasQuietHours = isCurrentlyQuietHours
    val nowQuietHours = isQuietHoursNow(context)

    if (wasQuietHours != nowQuietHours) {
        isCurrentlyQuietHours = nowQuietHours
        updateNotification(context)

        Log.d("QuietHoursService", "🔄 Status changed: $wasQuietHours → $nowQuietHours")

        showSimpleNotificationExtern(
            if (nowQuietHours) "🌙 Ruhezeit aktiviert" else "☀️ Ruhezeit beendet",
            if (nowQuietHours) {
                "Benachrichtigungen werden gesammelt"
            } else {
                "Normale Benachrichtigungen aktiv"
            },
            3.seconds,
            context
        )
    } else {
        Log.d("QuietHoursService", "✓ Status unchanged: $nowQuietHours")
    }
}

private fun getQuietStartHour(context: Context): Int {
    val prefs = context.getSharedPreferences("quick_settings_prefs", MODE_PRIVATE)
    val start = prefs.getString("saved_number_start", null)?.toIntOrNull() ?: 21
    return start
}

private fun getQuietEndHour(context: Context): Int {
    val prefs = context.getSharedPreferences("quick_settings_prefs", MODE_PRIVATE)
    val end = prefs.getString("saved_number", null)?.toIntOrNull() ?: 7
    return end
}

fun scheduleNextCheck(context: Context) {
    val now = Calendar.getInstance()
    val quietStart = getQuietStartHour(context)
    val quietEnd = getQuietEndHour(context)

    val nextChange = calculateNextStatusChange(now, quietStart, quietEnd)
    val delayMillis = nextChange.timeInMillis - now.timeInMillis
    val delayMinutes = delayMillis / 1000 / 60

    val checkRunnable = QuietHoursNotificationService.getCheckRunnable(context)

    if (delayMinutes < THRESHOLD_MINUTES) {
        workerHandler.removeCallbacks(checkRunnable)
        workerHandler.postDelayed(checkRunnable, maxOf(delayMillis, 30_000L))
    } else {
        scheduleWithAlarmManager(nextChange.timeInMillis, context, checkRunnable)
    }
}

@RequiresPermission(Manifest.permission.SCHEDULE_EXACT_ALARM)
private fun scheduleWithAlarmManager(triggerAtMillis: Long, context: Context, checkRunnable: Runnable) {
    try {
        val alarmManager = context.getSystemService(ALARM_SERVICE) as AlarmManager

        // Handler stoppen
        handler.removeCallbacks(checkRunnable)

        // AlarmManager Intent
        val intent = Intent(context, QuietHoursAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )

        Log.d(
            "QuietHoursService", "⏰ AlarmManager scheduled: ${
                SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(triggerAtMillis))
            }"
        )

    } catch (e: Exception) {
        Log.e("QuietHoursService", "AlarmManager failed, falling back to Handler", e)
        // Fallback: Handler nutzen
        val delayMillis = triggerAtMillis - System.currentTimeMillis()
        scheduleWithHandler(delayMillis, checkRunnable)
    }
}

private fun scheduleWithHandler(delayMillis: Long, checkRunnable: Runnable) {
    // Sicherheitscheck: Mindestens 30 Sekunden
    val finalDelay = maxOf(delayMillis, 30_000L)

    handler.removeCallbacks(checkRunnable)
    handler.postDelayed(checkRunnable, finalDelay)

    Log.d("QuietHoursService", "📱 Handler scheduled: ${finalDelay / 1000}s")
}

fun createNotificationChannel(context: Context) {
    val channel = NotificationChannel(
        CHANNEL_ID,
        "Ruhezeiten Überwachung",
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = "Überwacht Ruhezeiten "
        setShowBadge(false)
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
    }

    val notificationManager = context.getSystemService(NotificationManager::class.java)
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

    val showsimplenotChannel = NotificationChannel(
        SSN_CHANNEL_ID,
        "Show Simple Notification",
        NotificationManager.IMPORTANCE_LOW
    ).apply {
        description = "Einfache Benachrichtigung"
        setShowBadge(false)
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
    }
    notificationManager.createNotificationChannel(showsimplenotChannel)

    val deleteConfirmationChannel = NotificationChannel(
        DELETE_CONFIRMATION_CHANNEL_ID,
        "Löschbestätigungen",
        NotificationManager.IMPORTANCE_HIGH
    ).apply {
        description = "Bestätigungen zum Löschen von Bildern"
        setShowBadge(true)
        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
    }
    notificationManager.createNotificationChannel(deleteConfirmationChannel)
}

fun createNotification(isQuietHours: Boolean, context: Context): Notification {
    val deleteIntent = Intent(ACTION_NOTIFICATION_DISMISSED).apply {
        putExtra("notification_id", NOTIFICATION_ID)
        `package` = context.packageName
    }
    val deletePendingIntent = PendingIntent.getBroadcast(
        context,
        999,
        deleteIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val contentIntent = Intent(context, QuietHoursNotificationService::class.java).apply {
        action = "ACTION_CONTENT_INTENT"
    }
    val contentPendingIntent = PendingIntent.getService(
        context,
        1002,
        contentIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val commandInput = RemoteInput.Builder("key_command_input")
        .setLabel("Befehl eingeben...")
        .build()

    val commandIntent = Intent(ACTION_EXECUTE_COMMAND).apply {
        `package` =  context.packageName
    }

    val commandPendingIntent = PendingIntent.getBroadcast(
        context,
        200,
        commandIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
    )

    val commandAction = NotificationCompat.Action.Builder(
        android.R.drawable.ic_menu_search,
        "Befehl",
        commandPendingIntent
    )
        .addRemoteInput(commandInput)
        .setShowsUserInterface(false)
        .build()

    val builder = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .setDeleteIntent(deletePendingIntent)
        .setContentIntent(contentPendingIntent)
        .setRequestPromotedOngoing(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        .setGroup("group_services")
        .setGroupSummary(false)
        .addAction(commandAction)

    if (isQuietHours) {
        builder
            .setContentTitle("🌙 Ruhezeit aktiv")

        val settingsIntent = Intent(Settings.ACTION_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val settingsPendingIntent = PendingIntent.getActivity(
            context,
            1001,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        builder.addAction(
            android.R.drawable.ic_menu_preferences,
            "Settings",
            settingsPendingIntent
        )

        val messagesIntent = Intent(context, QuietHoursNotificationService::class.java).apply {
            action = ACTION_SHOW_MESSAGES
        }
        val messagesPendingIntent = PendingIntent.getService(
            context, 0, messagesIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            android.R.drawable.ic_dialog_email,
            "Messages",
            messagesPendingIntent
        )

        val musicIntent = Intent(context, QuietHoursNotificationService::class.java).apply {
            action = ACTION_RESTART_MUSIC_PLAYER
        }
        val musicPendingIntent = PendingIntent.getService(
            context, 2, musicIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(
            android.R.drawable.ic_media_play,
            "Music",
            musicPendingIntent
        )

    } else {
        val prefs = context.getSharedPreferences("quick_settings_prefs", MODE_PRIVATE)
        val currentStart = prefs.getString("saved_number_start", "21") ?: "21"
        val currentEnd = prefs.getString("saved_number", "7") ?: "7"

        val startRemoteInput = RemoteInput.Builder("key_time_input")
            .setLabel("Startzeit (0-23)")
            .build()

        val startIntent = Intent(ACTION_CHANGE_START).apply {
            `package` =  context.packageName
        }

        val startPending = PendingIntent.getBroadcast(
            context, 100, startIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val startAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_recent_history,
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
            `package` = context.packageName
        }

        val endPending = PendingIntent.getBroadcast(
            context, 101, endIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val endAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_recent_history,
            "Ende: ${currentEnd}h",
            endPending
        )
            .addRemoteInput(endRemoteInput)
            .setShowsUserInterface(false)
            .build()

        builder
            .setContentTitle("Ruhezeit-Überwachung")
            .setContentText("Aktiv von $currentStart:00 bis $currentEnd:00 Uhr")
            .addAction(startAction)
            .addAction(endAction)
    }

    return builder.build()
}

fun updateNotification(context: Context) {
    val serviceIntent = Intent(context, QuietHoursNotificationService::class.java).apply {
        action = "ACTION_RESTORE_NOTIFICATION"
    }
    context.startForegroundService(serviceIntent)
}

fun isQuietHoursNow(context: Context): Boolean {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val prefs = context.getSharedPreferences("quick_settings_prefs", MODE_PRIVATE)
    val quietEnd = prefs.getString("saved_number", null)?.toIntOrNull() ?: 21
    val quietStart = prefs.getString("saved_number_start", null)?.toIntOrNull() ?: 7
    return hour !in quietStart..<quietEnd
}