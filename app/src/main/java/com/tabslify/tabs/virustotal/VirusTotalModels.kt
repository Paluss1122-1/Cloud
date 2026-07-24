package com.tabslify.tabs.virustotal

enum class VirusTotalMode {
    URL, HASH, FILE
}

data class VirusTotalStats(
    val malicious: Int,
    val suspicious: Int,
    val harmless: Int,
    val undetected: Int,
    val timeout: Int
) {
    val total get() = malicious + suspicious + harmless + undetected + timeout
}

sealed class VirusTotalState {
    object Idle : VirusTotalState()
    object Loading : VirusTotalState()
    data class Result(val stats: VirusTotalStats, val permalink: String) : VirusTotalState()
    data class Error(val message: String) : VirusTotalState()
}
