package com.tabslify.tabs.aitab

import android.R
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.tabslify.core.activities.MainActivity
import com.tabslify.core.functions.canNotify

fun isAppInForeground(): Boolean {
    return ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
}

fun sendAITabBackgroundNotification(
    context: Context,
    title: String = "AI Tab Antwort",
    message: String
) {
    if (isAppInForeground()) return

    if (!canNotify(context)) return

    val intent = Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra("target", "aitab")
    }

    val pendingIntent = PendingIntent.getActivity(
        context,
        System.currentTimeMillis().toInt(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, "ai_tab_notification_channel")
        .setSmallIcon(R.drawable.ic_dialog_info)
        .setContentTitle(title)
        .setContentText(message)
        .setStyle(NotificationCompat.BigTextStyle().bigText(message))
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(pendingIntent)
        .setAutoCancel(true)
        .build()

    val notificationManager = context.getSystemService(NotificationManager::class.java)
    notificationManager.notify(
        "ai_response_notification",
        System.currentTimeMillis().toInt(),
        notification
    )
}
