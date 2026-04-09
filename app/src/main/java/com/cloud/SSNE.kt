package com.cloud

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.cloud.Config.cms
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

fun showSimpleNotificationExtern(
    title: String,
    text: String,
    duration: Duration = 15.seconds,
    context: Context,
    silent: Boolean = true
) {
    val notification = NotificationCompat.Builder(context, "show_simple_not_channel")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setGroup("SSN")
        .setSilent(silent)
        .build()

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