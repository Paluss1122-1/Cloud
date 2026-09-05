package com.tabslify.tabs.focusguard

fun formatMinutes(totalMinutes: Long): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

fun formatDurationMs(ms: Long): String = formatMinutes(ms / 60_000)

