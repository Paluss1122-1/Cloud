package com.tabslify.tabs.focusguard.monitoring

import android.annotation.SuppressLint
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.tabslify.tabs.focusguard.data.AppUsageLog
import com.tabslify.tabs.focusguard.data.CATEGORY_ENTERTAINMENT
import com.tabslify.tabs.focusguard.data.CATEGORY_GAMING
import com.tabslify.tabs.focusguard.data.CATEGORY_OTHER
import com.tabslify.tabs.focusguard.data.CATEGORY_SOCIAL
import com.tabslify.tabs.focusguard.data.FocusGuardConfig
import java.time.LocalDate
import java.time.ZoneId

object UsageTracker {
    private val curatedDefaults = mapOf(
        "com.instagram.android" to CATEGORY_SOCIAL,
        "com.tiktok" to CATEGORY_SOCIAL,
        "com.snapchat.android" to CATEGORY_SOCIAL,
        "com.facebook.katana" to CATEGORY_SOCIAL,
        "com.facebook.orca" to CATEGORY_SOCIAL,
        "com.whatsapp" to CATEGORY_SOCIAL,
        "com.whatsapp.w4b" to CATEGORY_SOCIAL,
        "org.telegram.messenger" to CATEGORY_SOCIAL,
        "com.twitter.android" to CATEGORY_SOCIAL,
        "com.pinterest" to CATEGORY_SOCIAL,
        "com.reddit.frontpage" to CATEGORY_SOCIAL,
        "com.linkedin.android" to CATEGORY_SOCIAL,
        "com.roblox.client" to CATEGORY_GAMING,
        "com.mojang.minecraftpe" to CATEGORY_GAMING,
        "com.tencent.ig" to CATEGORY_GAMING,
        "com.activision.callofduty.shooter" to CATEGORY_GAMING,
        "com.epicgames.fortnite" to CATEGORY_GAMING,
        "com.supercell.clashofclans" to CATEGORY_GAMING,
        "com.google.android.youtube" to CATEGORY_ENTERTAINMENT,
        "com.google.android.apps.youtube.music" to CATEGORY_ENTERTAINMENT,
        "com.spotify.music" to CATEGORY_ENTERTAINMENT,
        "com.netflix.mediaclient" to CATEGORY_ENTERTAINMENT,
        "com.amazon.amazonvideo.livingroom" to CATEGORY_ENTERTAINMENT,
        "com.twitch.tv" to CATEGORY_ENTERTAINMENT,
        "com.disney.disneyplus" to CATEGORY_ENTERTAINMENT
    )

    private const val MIN_SESSION_MS = 3_000L

    @SuppressLint("MissingPermission")
    fun hasUsageAccess(ctx: Context): Boolean {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
        return stats != null && stats.isNotEmpty()
    }

    fun categoryFor(packageName: String): String =
        FocusGuardConfig.restrictedApps()[packageName]
            ?: curatedDefaults[packageName]
            ?: CATEGORY_OTHER

    @SuppressLint("MissingPermission")
    fun currentForegroundPackage(ctx: Context): String? {
        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 120_000, now)
        var last: String? = null
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                last = event.packageName
            }
        }
        return last
    }

    @SuppressLint("MissingPermission")
    fun querySessions(ctx: Context, date: LocalDate): List<AppUsageLog> {
        val zone = ZoneId.systemDefault()
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(dayStart, dayEnd)

        val logs = mutableListOf<AppUsageLog>()
        var currentPackage: String? = null
        var currentStart = 0L
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val pkg = event.packageName
                if (currentPackage != null && currentStart > 0L && currentPackage != pkg) {
                    appendSession(logs, currentPackage, currentStart, event.timeStamp, date)
                }
                currentPackage = pkg
                currentStart = event.timeStamp
            }
        }

        val end = minOf(System.currentTimeMillis(), dayEnd)
        if (currentPackage != null && currentStart > 0L && end > currentStart) {
            appendSession(logs, currentPackage, currentStart, end, date)
        }

        return logs
    }

    private fun appendSession(
        logs: MutableList<AppUsageLog>,
        packageName: String,
        startMs: Long,
        endMs: Long,
        date: LocalDate
    ) {
        val duration = endMs - startMs
        if (duration < MIN_SESSION_MS) return
        logs.add(
            AppUsageLog(
                id = "$packageName-$startMs",
                packageName = packageName,
                category = categoryFor(packageName),
                date = date.toString(),
                sessionStartMs = startMs,
                sessionEndMs = endMs,
                durationMs = duration
            )
        )
    }
}
