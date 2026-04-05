package com.cloud.exploretab

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val EARTH_AREA_KM2 = 510_000_000.0
private val TILE_AREA_KM2 = (TILE_SIZE * 111.32) * (TILE_SIZE * 111.32)

class ExploreViewModel(app: Application) : AndroidViewModel(app) {
    val repo = ExploreRepository(app)

    val tileCount: StateFlow<Long> = repo.countFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val exploredPercent: StateFlow<Double> = tileCount.map { count ->
        count * TILE_AREA_KM2 / EARTH_AREA_KM2 * 100.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val allTiles: StateFlow<List<ExploredTile>> = repo.allTilesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var todayCount by mutableLongStateOf(0L)
        private set

    init {
        viewModelScope.launch { todayCount = repo.todayCount() }
        viewModelScope.launch {
            tileCount.collect { todayCount = repo.todayCount() }
        }
    }
}
