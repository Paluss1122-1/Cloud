package com.example.spotifydownloader.domain

data class TrackInfo(
    val title: String,
    val artist: String,
    val album: String,
    val thumbnailUrl: String,
    val spotifyUrl: String
)

sealed class DownloadState {
    object Idle : DownloadState()
    object Searching : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    object Converting : DownloadState()
    object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
}
