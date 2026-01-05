package com.example.cloud

import android.Manifest
import android.R
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import kotlin.time.Duration

fun showSimpleNotificationExtern(
    title: String,
    text: String,
    duration: Duration = Duration.ZERO,
    context: Context
) {
    val notificationId = System.currentTimeMillis().toInt()

    val notification = NotificationCompat.Builder(context, "show_simple_not_channel")
        .setSmallIcon(R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(text)
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setGroup("SSN")
        .build()

    val notificationManager = context.getSystemService(NotificationManager::class.java)

    if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
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