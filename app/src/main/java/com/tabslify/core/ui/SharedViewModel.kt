package com.tabslify.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class SharedViewModel : ViewModel() {
    private val _uiEvent = MutableSharedFlow<Boolean>()
    val uiEvent = _uiEvent.asSharedFlow()

    fun fireEvent(value: Boolean) {
        viewModelScope.launch { _uiEvent.emit(value) }
    }
}