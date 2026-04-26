package com.cloud.spotifydownloader_own.domain

sealed class DownloadState {
    object Idle : DownloadState()
    object Searching : DownloadState()
    data class Downloading(val progress: Int) : DownloadState()
    object Converting : DownloadState()
    object Success : DownloadState()
    data class Error(val message: String) : DownloadState()
}
