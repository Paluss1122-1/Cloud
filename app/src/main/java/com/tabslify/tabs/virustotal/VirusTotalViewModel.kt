package com.tabslify.tabs.virustotal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VirusTotalViewModel(private val repository: VirusTotalRepository) : ViewModel() {

    private val _state = MutableStateFlow<VirusTotalState>(VirusTotalState.Idle)
    val state: StateFlow<VirusTotalState> = _state.asStateFlow()

    fun checkUrl(url: String) {
        if (url.isBlank()) return
        if (!repository.hasApiKey) {
            _state.value = VirusTotalState.Error(MISSING_API_KEY_MESSAGE)
            return
        }
        _state.value = VirusTotalState.Loading
        viewModelScope.launch { _state.value = repository.scanUrl(url.trim()) }
    }

    fun checkHash(hash: String) {
        if (hash.isBlank()) return
        if (!repository.hasApiKey) {
            _state.value = VirusTotalState.Error(MISSING_API_KEY_MESSAGE)
            return
        }
        _state.value = VirusTotalState.Loading
        viewModelScope.launch { _state.value = repository.lookupHash(hash.trim()) }
    }

    fun checkFile(fileName: String, bytes: ByteArray) {
        if (!repository.hasApiKey) {
            _state.value = VirusTotalState.Error(MISSING_API_KEY_MESSAGE)
            return
        }
        _state.value = VirusTotalState.Loading
        viewModelScope.launch { _state.value = repository.scanFile(fileName, bytes) }
    }

    fun reset() {
        _state.value = VirusTotalState.Idle
    }

    private companion object {
        const val MISSING_API_KEY_MESSAGE =
            "Kein VirusTotal API-Key konfiguriert. Bitte VIRUSTOTAL_API_KEY in local.properties eintragen."
    }
}
