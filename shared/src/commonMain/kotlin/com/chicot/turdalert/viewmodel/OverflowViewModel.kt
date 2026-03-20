package com.chicot.turdalert.viewmodel

import com.chicot.turdalert.api.OverflowFetcher
import com.chicot.turdalert.api.SiteHistoryClient
import com.chicot.turdalert.api.SiteStatsResponse
import com.chicot.turdalert.domain.DebouncedFetcher
import com.chicot.turdalert.domain.nearbyOverflows
import com.chicot.turdalert.domain.withRecentDischargeStatus
import com.chicot.turdalert.location.Coordinates
import com.chicot.turdalert.location.LocationProvider
import com.chicot.turdalert.model.BoundingBox
import com.chicot.turdalert.model.OverflowPoint
import com.chicot.turdalert.model.toBoundingBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class OverflowViewModel(
    private val repository: OverflowFetcher,
    private val locationProvider: LocationProvider,
    private val scope: CoroutineScope,
    private val historyClient: SiteHistoryClient? = null
) {

    sealed interface UiState {
        data object Loading : UiState
        data class Loaded(
            val overflows: List<OverflowPoint>,
            val location: Coordinates,
            val isRefreshing: Boolean = false
        ) : UiState
        data class Error(val message: String) : UiState
        data object NoLocation : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _selectedOverflow = MutableStateFlow<OverflowPoint?>(null)
    val selectedOverflow: StateFlow<OverflowPoint?> = _selectedOverflow.asStateFlow()

    private val _selectedSiteStats = MutableStateFlow<SiteStatsResponse?>(null)
    val selectedSiteStats: StateFlow<SiteStatsResponse?> = _selectedSiteStats.asStateFlow()

    private val _hasUserInteracted = MutableStateFlow(false)
    val hasUserInteracted: StateFlow<Boolean> = _hasUserInteracted.asStateFlow()

    private var currentViewportBounds: BoundingBox? = null
    private var initialLoadComplete = false
    private val overflowCache = mutableMapOf<String, OverflowPoint>()

    private val debouncedFetcher = DebouncedFetcher(
        scope = scope
    ) { bounds ->
        repository.allOverflows(bounds)
    }

    init {
        scope.launch {
            debouncedFetcher.results.collect { results ->
                if (results != null) {
                    val currentState = _state.value
                    val location = when (currentState) {
                        is UiState.Loaded -> currentState.location
                        else -> locationProvider.currentLocation() ?: return@collect
                    }
                    results.forEach { overflowCache[it.id] = it }
                    _state.value = UiState.Loaded(
                        overflows = overflowCache.values.toList()
                            .withRecentDischargeStatus(Clock.System.now().toEpochMilliseconds()),
                        location = location
                    )
                }
            }
        }
    }

    fun selectOverflow(overflow: OverflowPoint) {
        _selectedOverflow.value = overflow
        _selectedSiteStats.value = null
        scope.launch {
            val history = historyClient?.siteHistory(overflow.company, overflow.id)
            _selectedSiteStats.value = history?.stats
        }
    }

    fun clearSelection() {
        _selectedOverflow.value = null
        _selectedSiteStats.value = null
    }

    fun onViewportChanged(bounds: BoundingBox) {
        currentViewportBounds = bounds
        if (!initialLoadComplete) return
        _hasUserInteracted.value = true
        if (bounds.maxLat - bounds.minLat > MAX_VIEWPORT_LAT_SPAN) return
        debouncedFetcher.onViewportChanged(bounds)
    }

    private companion object {
        const val MAX_VIEWPORT_LAT_SPAN = 0.5
    }

    fun refresh() {
        debouncedFetcher.cancel()
        scope.launch {
            val currentState = _state.value
            if (currentState is UiState.Loaded) {
                _state.value = currentState.copy(isRefreshing = true)
            } else {
                _state.value = UiState.Loading
            }

            try {
                val location = locationProvider.currentLocation()
                if (location == null) {
                    _state.value = UiState.NoLocation
                    return@launch
                }

                val bounds = currentViewportBounds ?: location.toBoundingBox()
                val allOverflows = repository.allOverflows(bounds)

                val overflows = if (_hasUserInteracted.value) {
                    allOverflows
                } else {
                    allOverflows.nearbyOverflows(location)
                }

                overflowCache.clear()
                overflows.forEach { overflowCache[it.id] = it }
                _state.value = UiState.Loaded(
                    overflows = overflows.withRecentDischargeStatus(Clock.System.now().toEpochMilliseconds()),
                    location = location
                )
                initialLoadComplete = true
            } catch (e: Exception) {
                _state.value = UiState.Error(
                    message = e.message ?: "Failed to load overflow data"
                )
            }
        }
    }
}
