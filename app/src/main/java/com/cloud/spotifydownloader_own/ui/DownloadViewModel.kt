package com.cloud.spotifydownloader_own.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloud.spotifydownloader_own.domain.DownloadRepository
import com.cloud.spotifydownloader_own.domain.DownloadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DownloadViewModel(
    private val repository: DownloadRepository
) : ViewModel() {

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    fun startDownload(url: String) {
        viewModelScope.launch {
            repository.downloadTrack(url).collect { state ->
                _downloadState.value = state
            }
        }
    }
}
