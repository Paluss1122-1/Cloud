package com.example.spotifydownloader.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.spotifydownloader.domain.DownloadRepository
import com.example.spotifydownloader.domain.DownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadViewModel @Inject constructor(
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
