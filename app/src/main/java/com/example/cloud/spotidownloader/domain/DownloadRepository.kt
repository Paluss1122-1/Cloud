package com.example.spotifydownloader.domain

import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun downloadTrack(spotifyUrl: String): Flow<DownloadState>
}
