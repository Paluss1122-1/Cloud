package com.cloud.inactive.exclude

import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.util.Calendar

data class AppUsageInfo(
    val packageName: String,
    val totalTimeInForeground: Long,
    val lastTimeUsed: Long,
    val firstTimeStamp: Long
)

/**
 * Liest die App-Nutzungsstatistiken der letzten [days] Tage aus.
 * Benötigt die Berechtigung: android.permission.PACKAGE_USAGE_STATS
 * (muss vom Nutzer manuell in den Einstellungen erteilt werden)
 *
 * @param context  Android Context
 * @param days     Anzahl der Tage, die ausgelesen werden sollen (Standard: 7)
 * @return         Sortierte Liste der App-Nutzung (meistgenutzt zuerst), oder null bei fehlendem Zugriff
 */
fun getAppUsageStats(context: Context, days: Int = 7): List<AppUsageInfo>? {

    if (!hasUsageStatsPermission(context)) {
        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
        return null
    }

    val calendar = Calendar.getInstance()
    val endTime = calendar.timeInMillis
    calendar.add(Calendar.DAY_OF_YEAR, -days)
    val startTime = calendar.timeInMillis

    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    val usageStatsList: List<UsageStats> = usageStatsManager.queryUsageStats(
        UsageStatsManager.INTERVAL_DAILY,
        startTime,
        endTime
    )

    if (usageStatsList.isEmpty()) return emptyList()

    val aggregatedMap = mutableMapOf<String, AppUsageInfo>()

    for (stats in usageStatsList) {
        if (stats.totalTimeInForeground <= 0) continue

        val existing = aggregatedMap[stats.packageName]
        if (existing != null) {
            aggregatedMap[stats.packageName] = existing.copy(
                totalTimeInForeground = existing.totalTimeInForeground + stats.totalTimeInForeground,
                lastTimeUsed = maxOf(existing.lastTimeUsed, stats.lastTimeUsed),
                firstTimeStamp = minOf(existing.firstTimeStamp, stats.firstTimeStamp)
            )
        } else {
            aggregatedMap[stats.packageName] = AppUsageInfo(
                packageName = stats.packageName,
                totalTimeInForeground = stats.totalTimeInForeground,
                lastTimeUsed = stats.lastTimeUsed,
                firstTimeStamp = stats.firstTimeStamp
            )
        }
    }

    return aggregatedMap.values.sortedByDescending { it.totalTimeInForeground }
}

fun getForegroundTimePerApp(context: Context, days: Int = 7): Map<String, Long> {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    val endTime = System.currentTimeMillis()
    val startTime = Calendar.getInstance().apply {
        add(Calendar.DAY_OF_YEAR, -days)
    }.timeInMillis

    return usageStatsManager
        .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        .filter { it.totalTimeInForeground > 0 }
        .groupBy { it.packageName }
        .mapValues { (_, statsList) -> statsList.sumOf { it.totalTimeInForeground } }
        .entries
        .sortedByDescending { it.value }
        .associate { it.key to it.value }
}

/**
 * Prüft, ob die PACKAGE_USAGE_STATS Berechtigung erteilt wurde.
 */
fun hasUsageStatsPermission(context: Context): Boolean {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val now = System.currentTimeMillis()
    val stats = usageStatsManager.queryUsageStats(
        UsageStatsManager.INTERVAL_DAILY,
        now - 1000 * 60,
        now
    )
    return stats != null && stats.isNotEmpty()
}

/**
 * Hilfsfunktion: Millisekunden in lesbares Format umwandeln
 */
fun Long.toReadableTime(): String {
    val hours = this / (1000 * 60 * 60)
    val minutes = (this % (1000 * 60 * 60)) / (1000 * 60)
    val seconds = (this % (1000 * 60)) / 1000
    return "${hours}h ${minutes}m ${seconds}s"
}