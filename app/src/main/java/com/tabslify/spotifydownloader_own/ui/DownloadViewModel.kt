package com.tabslify.spotifydownloader_own.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tabslify.spotifydownloader_own.domain.DownloadRepository
import com.tabslify.spotifydownloader_own.domain.DownloadState
import com.tabslify.spotifydownloader_own.domain.generateAndSaveHashtags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DownloadViewModel(
    private val repository: DownloadRepository,
    context: Context
) : ViewModel() {

    private val appContext: Context = context.applicationContext

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    fun startDownload(url: String) {
        viewModelScope.launch {
            repository.downloadTrack(url).collect { state ->
                _downloadState.value = state
                if (state is DownloadState.Success) {
                    generateAndSaveHashtags(
                        ctx = appContext,
                        trackId = state.trackId,
                        title = state.title,
                        artist = state.artist,
                        album = state.album,
                        fileUri = state.fileUri
                    )
                }
            }
        }
    }
}
