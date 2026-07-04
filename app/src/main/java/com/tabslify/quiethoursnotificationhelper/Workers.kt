package com.tabslify.quiethoursnotificationhelper

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.tabslify.services.QuietHoursNotificationService
import com.tabslify.services.QuietHoursNotificationService.Companion.ACTION_DAILY_MUSIC_SUMMARY
import com.tabslify.services.QuietHoursNotificationService.Companion.MAX_MESSAGES_PER_CONTACT
import com.tabslify.services.QuietHoursNotificationService.Companion.scheduledailysummaryalarm
import com.tabslify.services.QuietHoursNotificationService.Companion.workerHandler
import com.tabslify.services.WhatsAppNotificationListener

class DailySummaryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.startService(
            Intent(context, QuietHoursNotificationService::class.java).apply {
                action = ACTION_DAILY_MUSIC_SUMMARY
            }
        )
        scheduledailysummaryalarm(context)
    }
}

class CleanupWorker(
    context: Context,
    params: WorkerParameters,

    ) : Worker(context, params) {
    private fun cleanupReadMessages() {
        val cutoffTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000)

        QuietHoursNotificationService.readMessageIds.removeAll { messageId ->
            val timestamp = messageId.substringAfterLast("_").toLongOrNull() ?: 0
            timestamp < cutoffTime
        }
    }

    fun cleanupOldMessages() {
        workerHandler.post {
            val cutoff = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
            WhatsAppNotificationListener.messagesByContact.forEach { (_, msgs) ->
                msgs.removeAll { it.timestamp < cutoff }
                if (msgs.size > MAX_MESSAGES_PER_CONTACT)
                    msgs.subList(0, msgs.size - MAX_MESSAGES_PER_CONTACT).clear()
            }
            WhatsAppNotificationListener.messagesByContact.entries.removeIf { it.value.isEmpty() }
        }
    }

    override fun doWork(): Result {
        return try {
            cleanupReadMessages()
            cleanupOldMessages()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}

class SyncTriggerWorker(
    private val context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        return try {
            val calendar = java.util.Calendar.getInstance()
            val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
            
            if (hour in 21..22) {
                if (!isTriggerPortOpen()) {
                    syncTodosWithLaptop(context)
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}