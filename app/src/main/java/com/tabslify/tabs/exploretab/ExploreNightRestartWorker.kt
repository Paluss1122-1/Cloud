package com.tabslify.tabs.exploretab

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.tabslify.core.functions.errorInsert
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

private const val RAW_POINT_RETENTION_DAYS = 30L

class ExploreNightRestartWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val ctx = applicationContext
        errorInsert(
            "ExploreTracker",
            "NightRestartWorker - Starting tracker",
            Instant.now().toString(),
            "LOG"
        )
        ExploreLocationTracker.start(ctx)

        runBlocking {
            try {
                ExploreSegmentBuilder.rebuildDay(ctx, LocalDate.now().minusDays(1))
                val cutoff = System.currentTimeMillis() - RAW_POINT_RETENTION_DAYS * 24 * 60 * 60 * 1000
                val repo = ExploreRepository(ctx)
                repo.deleteRawPointsBefore(cutoff)
                repo.deleteTrackerEventsBefore(cutoff)
            } catch (_: Exception) {
            }
        }

        return Result.success()
    }

    companion object {
        private const val TAG = "explore_night_restart"
        private const val RESTART_HOUR = 5
        private const val RESTART_MINUTE = 0
        private const val MIN_DELAY_MS = 60_000L

        fun schedule(context: Context) {
            val appCtx = context.applicationContext
            val now = LocalDateTime.now()
            var nextRestart = now.withHour(RESTART_HOUR).withMinute(RESTART_MINUTE).withSecond(0).withNano(0)

            if (!now.isBefore(nextRestart)) {
                nextRestart = nextRestart.plusDays(1)
            }

            val delay = kotlin.math.max(
                nextRestart.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - System.currentTimeMillis(),
                MIN_DELAY_MS
            )

            val req = OneTimeWorkRequestBuilder<ExploreNightRestartWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()

            WorkManager.getInstance(appCtx)
                .enqueueUniqueWork(TAG, androidx.work.ExistingWorkPolicy.REPLACE, req)

            errorInsert(
                "ExploreTracker",
                "NightRestartWorker - Scheduled for $nextRestart (delay=${delay / 1000}s)",
                Instant.now().toString(),
                "LOG"
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(TAG)
        }
    }
}
