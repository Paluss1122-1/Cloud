package com.cloud.spotifydownloader_own.domain

import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun downloadTrack(spotifyUrl: String): Flow<DownloadState>
}
