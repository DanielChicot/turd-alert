package com.chicot.turdalert.viewmodel

import com.chicot.turdalert.api.OverflowRepository
import com.chicot.turdalert.domain.nearbyOverflows
import com.chicot.turdalert.location.Coordinates
import com.chicot.turdalert.location.LocationProvider
import com.chicot.turdalert.model.OverflowPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OverflowViewModel(
    private val repository: OverflowRepository,
    private val locationProvider: LocationProvider,
    private val scope: CoroutineScope
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

    fun refresh() {
        scope.launch {
            val currentState = _state.value
            if (currentState is UiState.Loaded) {
                _state.update { currentState.copy(isRefreshing = true) }
            } else {
                _state.value = UiState.Loading
            }

            try {
                val location = locationProvider.currentLocation()
                if (location == null) {
                    _state.value = UiState.NoLocation
                    return@launch
                }

                val allOverflows = repository.allOverflows(location)
                val nearby = allOverflows.nearbyOverflows(location)
                _state.value = UiState.Loaded(
                    overflows = nearby,
                    location = location
                )
            } catch (e: Exception) {
                _state.value = UiState.Error(
                    message = e.message ?: "Failed to load overflow data"
                )
            }
        }
    }
}
