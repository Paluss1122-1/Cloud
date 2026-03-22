package com.cloud.spotidownloader.domain

import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun downloadTrack(spotifyUrl: String): Flow<DownloadState>
}
