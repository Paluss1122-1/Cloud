package com.tabslify.tabs.focusguard.monitoring

import com.tabslify.tabs.focusguard.data.AppUsageLog
import com.tabslify.tabs.focusguard.data.FOCUSGUARD_RESTRICTED_CATEGORIES
import com.tabslify.tabs.focusguard.data.RestrictionRule
import com.tabslify.tabs.focusguard.data.daysOfWeekMaskFor
import java.time.LocalDate
import java.time.ZoneId

object GamificationEngine {
    const val ACH_FIRST_DAY = "first_day"
    const val ACH_STREAK_3 = "streak_3"
    const val ACH_STREAK_7 = "streak_7"
    const val ACH_STREAK_14 = "streak_14"
    const val ACH_STREAK_30 = "streak_30"
    const val ACH_POINTS_100 = "points_100"
    const val ACH_POINTS_500 = "points_500"
    const val ACH_POINTS_1000 = "points_1000"
    const val ACH_POINTS_5000 = "points_5000"

    data class DayResult(val points: Int, val success: Boolean)

    fun evaluateDay(
        logs: List<AppUsageLog>,
        rules: List<RestrictionRule>,
        afternoonThresholdMin: Int,
        quotaDone: Boolean,
        schoolDay: Boolean,
        date: LocalDate
    ): DayResult {
        if (schoolDay) return DayResult(points = 0, success = true)

        val restricted = FOCUSGUARD_RESTRICTED_CATEGORIES.toSet()
        val restrictedLogs = logs.filter { it.category in restricted }
        val blockedHours = blockedHourSet(rules, afternoonThresholdMin, date)

        var points = 0
        for (hour in blockedHours) {
            val start = hourStartMs(date, hour)
            val end = start + HOUR_MS
            val overlapped = restrictedLogs.any {
                it.sessionEndMs > start && it.sessionStartMs < end
            }
            if (!overlapped) points++
        }

        val violation = restrictedLogs.any { log ->
            blockedHours.any { hour ->
                val start = hourStartMs(date, hour)
                log.sessionEndMs > start && log.sessionStartMs < start + HOUR_MS
            }
        }

        return DayResult(points = points, success = !violation && quotaDone)
    }

    fun blockedHourSet(
        rules: List<RestrictionRule>,
        afternoonThresholdMin: Int,
        date: LocalDate
    ): Set<Int> {
        val hours = mutableSetOf<Int>()
        for (category in FOCUSGUARD_RESTRICTED_CATEGORIES) {
            val rule = rules.firstOrNull { it.category == category }
            if (rule != null && !rule.enabled) continue
            if (rule != null && (rule.daysOfWeekMask and daysOfWeekMaskFor(date)) == 0) continue
            val start = rule?.startMinute ?: afternoonThresholdMin
            val end = rule?.endMinute ?: (24 * 60)
            hours.addAll(hoursIn(start, end))
        }
        return hours
    }

    fun newlyEarnedAchievements(streak: Int, points: Int): List<Pair<String, Int>> {
        val earned = mutableListOf<Pair<String, Int>>()
        if (streak >= 1) earned += ACH_FIRST_DAY to 10
        if (streak >= 3) earned += ACH_STREAK_3 to 50
        if (streak >= 7) earned += ACH_STREAK_7 to 150
        if (streak >= 14) earned += ACH_STREAK_14 to 300
        if (streak >= 30) earned += ACH_STREAK_30 to 800
        if (points >= 100) earned += ACH_POINTS_100 to 10
        if (points >= 500) earned += ACH_POINTS_500 to 50
        if (points >= 1000) earned += ACH_POINTS_1000 to 100
        if (points >= 5000) earned += ACH_POINTS_5000 to 500
        return earned
    }

    private fun hoursIn(startMinute: Int, endMinute: Int): Set<Int> {
        val hours = mutableSetOf<Int>()
        var m = startMinute
        val limit = if (endMinute > startMinute) endMinute else 24 * 60
        while (m < limit) {
            hours.add(m / 60)
            m += 60
        }
        if (endMinute <= startMinute) {
            var m2 = 0
            while (m2 < endMinute) {
                hours.add(m2 / 60)
                m2 += 60
            }
        }
        return hours
    }

    private fun hourStartMs(date: LocalDate, hour: Int): Long =
        date.atTime(hour, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private const val HOUR_MS = 60L * 60 * 1000
}
