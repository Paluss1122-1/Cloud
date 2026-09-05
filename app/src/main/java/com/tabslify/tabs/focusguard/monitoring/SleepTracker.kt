package com.tabslify.tabs.focusguard.monitoring

import android.content.Context
import com.tabslify.tabs.focusguard.data.SleepRecord
import java.time.LocalDate

object SleepTracker {
    private const val MIN_SLEEP_MS = 60L * 60 * 1000
    private const val MAX_SLEEP_MS = 18L * 60 * 60 * 1000
    private const val MIN_GAP_MS = 4L * 60 * 60 * 1000

    fun computeSleep(context: Context, nightOf: LocalDate): SleepRecord? {
        val daySessions = UsageTracker.querySessions(context, nightOf)
        val nextDaySessions = UsageTracker.querySessions(context, nightOf.plusDays(1))
        val all = (daySessions + nextDaySessions).sortedBy { it.sessionStartMs }
        if (all.isEmpty()) return null

        var bestStart = 0L
        var bestEnd = 0L
        var bestGap = 0L

        for (i in 0 until all.size - 1) {
            val gap = all[i + 1].sessionStartMs - all[i].sessionEndMs
            if (gap > bestGap) {
                bestGap = gap
                bestStart = all[i].sessionEndMs
                bestEnd = all[i + 1].sessionStartMs
            }
        }

        if (bestGap < MIN_GAP_MS) return null
        val duration = bestEnd - bestStart
        if (duration !in MIN_SLEEP_MS..MAX_SLEEP_MS) return null

        return SleepRecord(
            date = nightOf.toString(),
            bedtimeMs = bestStart,
            wakeMs = bestEnd,
            durationMs = duration
        )
    }

}
