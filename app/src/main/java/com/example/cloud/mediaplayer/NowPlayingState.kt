package com.example.cloud.mediaplayer

data class NowPlayingState(
    val isActive: Boolean = false,
    val mode: String = "music",           // "music" | "podcast"
    val title: String = "",
    val subtitle: String = "",            // artist / show name
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val progress: Float = 0f
)