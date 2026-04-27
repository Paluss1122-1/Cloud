package com.cloud.quiethoursnotificationhelper

import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.cloud.services.QuietHoursNotificationService
import com.cloud.services.QuietHoursNotificationService.Companion.ACTION_DAILY_MUSIC_SUMMARY
import com.cloud.services.QuietHoursNotificationService.Companion.MAX_MESSAGES_PER_CONTACT
import com.cloud.services.QuietHoursNotificationService.Companion.workerHandler
import com.cloud.services.WhatsAppNotificationListener

class DailySummaryWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {
    override fun doWork(): Result {
        val intent = Intent(applicationContext, QuietHoursNotificationService::class.java).apply {
            action = ACTION_DAILY_MUSIC_SUMMARY
        }

        applicationContext.startService(intent)

        return Result.success()
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