package com.tabslify.tabs.focusguard.monitoring

import com.tabslify.tabs.focusguard.data.parseYmd
import java.time.LocalDate

object BavarianSchoolCalendar {
    private val SCHOOL_HOLIDAY_RANGES = listOf(
        "2024-12-23" to "2025-01-03",
        "2025-03-03" to "2025-03-07",
        "2025-04-14" to "2025-04-25",
        "2025-06-10" to "2025-06-20",
        "2025-08-01" to "2025-09-15",
        "2025-11-03" to "2025-11-07",
        "2025-11-19" to "2025-11-19",
        "2025-12-22" to "2026-01-05",
        "2026-02-16" to "2026-02-20",
        "2026-03-30" to "2026-04-10",
        "2026-05-26" to "2026-06-05",
        "2026-08-03" to "2026-09-14",
        "2026-11-02" to "2026-11-06",
        "2026-11-18" to "2026-11-18",
        "2026-12-24" to "2027-01-08",
        "2027-02-08" to "2027-02-12",
        "2027-03-22" to "2027-04-02",
        "2027-05-18" to "2027-05-28",
        "2027-08-02" to "2027-09-13",
        "2027-11-02" to "2027-11-05",
        "2027-11-17" to "2027-11-17",
        "2027-12-24" to "2028-01-07",
        "2028-02-28" to "2028-03-03",
        "2028-04-10" to "2028-04-21",
        "2028-06-06" to "2028-06-16",
        "2028-07-31" to "2028-09-11",
        "2028-10-30" to "2028-11-03",
        "2028-11-22" to "2028-11-22",
        "2028-12-23" to "2029-01-05"
    )

    private val MOVABLE_HOLIDAYS = setOf(
        "2025-04-18", "2025-04-21", "2025-05-29", "2025-06-09", "2025-06-19",
        "2026-04-03", "2026-04-06", "2026-05-14", "2026-05-25", "2026-06-04",
        "2027-03-26", "2027-03-29", "2027-05-06", "2027-05-17", "2027-05-27",
        "2028-04-14", "2028-04-17", "2028-05-25", "2028-06-05", "2028-06-15"
    )

    private val FIXED_HOLIDAYS = listOf(
        1 to 1, 1 to 6, 5 to 1, 8 to 15, 10 to 3, 11 to 1, 12 to 25, 12 to 26
    )

    fun isBavarianHoliday(date: LocalDate): Boolean {
        if (FIXED_HOLIDAYS.any { it.first == date.monthValue && it.second == date.dayOfMonth }) {
            return true
        }
        if (date.toString() in MOVABLE_HOLIDAYS) return true
        return SCHOOL_HOLIDAY_RANGES.any { (start, end) ->
            val s = parseYmd(start)
            val e = parseYmd(end)
            !date.isBefore(s) && !date.isAfter(e)
        }
    }

    fun isSchoolDay(date: LocalDate): Boolean =
        date.dayOfWeek.value <= 5 && !isBavarianHoliday(date)
}
