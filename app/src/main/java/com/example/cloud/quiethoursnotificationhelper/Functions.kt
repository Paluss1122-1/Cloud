package com.example.cloud.quiethoursnotificationhelper

import android.app.NotificationManager
import android.content.Context
import com.example.cloud.ERRORINSERT
import com.example.cloud.ERRORINSERTDATA
import com.example.cloud.showSimpleNotificationExtern
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.time.Duration


fun markMessageAsRead(messageId: String, readMessageIds:  MutableSet<String>, context: Context) {
    try {
        readMessageIds.add(messageId)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val parts = messageId.split("_")
        if (parts.size >= 2) {
            val sender = parts[0]

            val senderHash = sender.hashCode()
            for (i in 0..10) {
                val partNotificationId = senderHash + 10000 + i
                notificationManager.cancel(partNotificationId)
            }
        }
    } catch (e: Exception) {
        CoroutineScope(Dispatchers.IO).launch {
            ERRORINSERT(
                ERRORINSERTDATA(
                    "markMessageAsRead",
                    "Fehler bei Nachricht als gelesen markieren: ${e.message}",
                    Instant.now().toString(),
                    "Error"
                )
            )
        }
    }
}