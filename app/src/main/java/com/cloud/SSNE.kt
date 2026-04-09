package com.cloud

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.cloud.Config.cms
import com.cloud.service.MediaPlayerService
import com.cloud.service.MediaPlayerService.Companion.ACTION_DELETE_SINGLE
import com.cloud.service.QuietHoursNotificationService
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@SuppressLint("LaunchActivityFromNotification")
fun showSimpleNotificationExtern(
    title: String,
    text: String,
    duration: Duration = 15.seconds,
    context: Context,
    silent: Boolean = true,
    onClick: String? = null
) {

    val notification = NotificationCompat.Builder(context, "show_simple_not_channel")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setGroup("SSN")
        .setSilent(silent)

    if (onClick != null) {
        val intent = PendingIntent.getService(
            context, 70000 + i,
            Intent(context, MediaPlayerService::class.java).apply {
                action = ACTION_DELETE_SINGLE + path.hashCode()
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notification.setContentIntent(intent)
    }

    notification.build()

    val id = cms()

    val notificationManager = context.getSystemService(NotificationManager::class.java)

    if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
        == PackageManager.PERMISSION_GRANTED
    ) {
        notificationManager.notify(id, notification)

        if (duration > Duration.ZERO) {
            Handler(Looper.getMainLooper()).postDelayed(
                { notificationManager.cancel(id) },
                duration.inWholeMilliseconds
            )
        }
    }
}