package com.tabslify.tabs.exploretab

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tabslify.R
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.time.Duration.Companion.milliseconds

private const val EARTH_AREA_KM2 = 510_000_000.0
private const val TILE_AREA_KM2 = (TILE_SIZE * 111.32) * (TILE_SIZE * 111.32)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
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

    private var exportRunning = false

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    private val _exportStatus = MutableStateFlow<String?>(null)
    val exportStatus: StateFlow<String?> = _exportStatus.asStateFlow()

    val daySegments: StateFlow<List<Segment>> = _selectedDate.flatMapLatest { day ->
        val (start, end) = dayBounds(day)
        repo.segmentsForDay(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dayStays: StateFlow<List<Stay>> = _selectedDate.flatMapLatest { day ->
        val (start, end) = dayBounds(day)
        repo.staysForDay(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dayPoints: StateFlow<List<RawPoint>> = _selectedDate.flatMapLatest { day ->
        val (start, end) = dayBounds(day)
        repo.pointsForDayFlow(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalDistanceKm: StateFlow<Double> = daySegments.map { segments ->
        segments.sumOf { it.distanceMeters.toDouble() } / 1000.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val timeMovingMs: StateFlow<Long> = daySegments.map { segments ->
        segments.sumOf { it.endTime - it.startTime }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    init {
        viewModelScope.launch { todayCount = repo.todayCount() }
        viewModelScope.launch {
            tileCount.collect { todayCount = repo.todayCount() }
        }
        viewModelScope.launch {
            _selectedDate.collect { day ->
                ExploreSegmentBuilder.rebuildDay(app, day)
            }
        }
        viewModelScope.launch {
            repo.rawPointCountFlow.debounce(10_000.milliseconds).collect {
                if (_selectedDate.value == LocalDate.now()) {
                    ExploreSegmentBuilder.rebuildDay(app, LocalDate.now(), force = true)
                }
            }
        }
    }

    fun previousDay() {
        _selectedDate.value = _selectedDate.value.minusDays(1)
    }

    fun nextDay() {
        val next = _selectedDate.value.plusDays(1)
        if (!next.isAfter(LocalDate.now())) {
            _selectedDate.value = next
        }
    }

    fun exportData(days: Int) {
        if (exportRunning) return
        exportRunning = true
        viewModelScope.launch {
            val app = getApplication<Application>()
            _exportStatus.value = app.getString(R.string.explore_export_laeuft)
            try {
                ExploreSegmentBuilder.rebuildDay(app, _selectedDate.value, force = true)
                val file = ExploreExport.export(app, _selectedDate.value, days)
                _exportStatus.value = app.getString(
                    R.string.explore_export_fertig,
                    file.name,
                    "%.0f KB".format(file.length() / 1024.0)
                )
                ExploreExport.share(app, file)
            } catch (e: Exception) {
                _exportStatus.value = app.getString(
                    R.string.explore_export_fehler,
                    e.message ?: e::class.java.simpleName
                )
            } finally {
                exportRunning = false
            }
        }
    }

    suspend fun deleteTile(x: Long, y: Long) = repo.deleteTile(x, y)
}
