package com.tabslify.tabs.focusguard.data

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

private val ymdFormatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

fun todayYmd(): String = LocalDate.now().toString()

fun ymdOf(ms: Long): String =
    LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), ZoneId.systemDefault())
        .toLocalDate().toString()

fun ymdDaysAgo(days: Int): String = LocalDate.now().minusDays(days.toLong()).toString()

fun parseYmd(value: String): LocalDate = LocalDate.parse(value, ymdFormatter)

fun minuteOfDay(ms: Long): Int {
    val t = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(ms), ZoneId.systemDefault())
    return t.hour * 60 + t.minute
}

fun weekdayIndex(date: LocalDate): Int = (date.dayOfWeek.value - 1)

fun daysOfWeekMaskFor(date: LocalDate): Int = 1 shl weekdayIndex(date)
