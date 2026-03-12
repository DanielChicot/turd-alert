package com.chicot.turdalert.domain

import com.chicot.turdalert.model.BoundingBox
import com.chicot.turdalert.model.OverflowPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

class DebouncedFetcher(
    private val scope: CoroutineScope,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val debounceMs: Long = 500L,
    private val minIntervalMs: Long = 5000L,
    private val fetch: suspend (BoundingBox) -> List<OverflowPoint>
) {
    private var fetchJob: Job? = null
    private var lastFetchStartMs: Long = Long.MIN_VALUE

    private val _results = MutableStateFlow<List<OverflowPoint>?>(null)
    val results: StateFlow<List<OverflowPoint>?> = _results.asStateFlow()

    fun onViewportChanged(bounds: BoundingBox) {
        fetchJob?.cancel()
        fetchJob = scope.launch {
            delay(debounceMs)
            val now = clock()
            val elapsed = now - lastFetchStartMs
            if (elapsed < minIntervalMs) {
                delay(minIntervalMs - elapsed)
            }
            lastFetchStartMs = clock()
            val result = fetch(bounds)
            _results.value = result
        }
    }

    fun fetchImmediately(bounds: BoundingBox) {
        fetchJob?.cancel()
        fetchJob = scope.launch {
            lastFetchStartMs = clock()
            val result = fetch(bounds)
            _results.value = result
        }
    }

    fun cancel() {
        fetchJob?.cancel()
        lastFetchStartMs = clock()
    }
}
