package com.tabslify.tabs.exploretab

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.tabslify.core.objects.reportError
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class ExploreNightRestartWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        reportError("ExploreTracker", "NightRestartWorker - Starting tracker", Instant.now().toString(), "LOG")
        ExploreLocationTracker.start(ctx)
        return Result.success()
    }

    companion object {
        private const val TAG = "explore_night_restart"

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next5AM = now.withHour(5).withMinute(0).withSecond(0).withNano(0)
            if (now.isAfter(next5AM)) {
                next5AM = next5AM.plusDays(1)
            }
            val delay = next5AM.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - System.currentTimeMillis()
            
            val req = OneTimeWorkRequestBuilder<ExploreNightRestartWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            
            WorkManager.getInstance(context)
                .enqueueUniqueWork(TAG, androidx.work.ExistingWorkPolicy.REPLACE, req)
            
            reportError("ExploreTracker", "NightRestartWorker - Scheduled for $next5AM", Instant.now().toString(), "LOG")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(TAG)
        }
    }
}
