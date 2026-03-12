package com.chicot.turdalert.domain

import com.chicot.turdalert.model.BoundingBox
import com.chicot.turdalert.model.DischargeStatus
import com.chicot.turdalert.model.OverflowPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DebouncedFetcherTest {

    private val london = BoundingBox(
        minLat = 51.4, maxLat = 51.6,
        minLon = -0.2, maxLon = 0.1
    )

    private val manchester = BoundingBox(
        minLat = 53.4, maxLat = 53.6,
        minLon = -2.3, maxLon = -2.1
    )

    private fun fakeOverflow(id: String) = OverflowPoint(
        id = id,
        latitude = 51.5,
        longitude = -0.1,
        status = DischargeStatus.NOT_DISCHARGING,
        watercourse = "River Test",
        siteName = "Test Site $id",
        company = "Test Water"
    )

    private fun testFetcher(
        scope: TestScope,
        results: MutableList<BoundingBox> = mutableListOf()
    ): Pair<DebouncedFetcher, MutableList<BoundingBox>> {
        val fetcher = DebouncedFetcher(
            scope = scope,
            clock = { scope.testScheduler.currentTime },
            debounceMs = 500L,
            minIntervalMs = 5000L
        ) { bounds ->
            results.add(bounds)
            listOf(fakeOverflow("1"))
        }
        return fetcher to results
    }

    @Test
    fun debounces_rapid_viewport_changes() = runTest {
        val (fetcher, fetched) = testFetcher(this)

        fetcher.onViewportChanged(london)
        advanceTimeBy(200)
        fetcher.onViewportChanged(manchester)
        advanceTimeBy(200)
        fetcher.onViewportChanged(london)
        advanceTimeBy(600)

        assertEquals(1, fetched.size)
        assertEquals(london, fetched.first())
    }

    @Test
    fun fires_after_debounce_period() = runTest {
        val (fetcher, fetched) = testFetcher(this)

        fetcher.onViewportChanged(london)
        advanceTimeBy(499)
        assertEquals(0, fetched.size)

        advanceTimeBy(2)
        assertEquals(1, fetched.size)
    }

    @Test
    fun throttles_within_minimum_interval() = runTest {
        val (fetcher, fetched) = testFetcher(this)

        fetcher.onViewportChanged(london)
        advanceTimeBy(600)
        assertEquals(1, fetched.size)

        fetcher.onViewportChanged(manchester)
        advanceTimeBy(600)
        assertEquals(1, fetched.size, "Should not have fetched yet — within 5s interval")

        advanceTimeBy(4500)
        assertEquals(2, fetched.size, "Should fetch after minimum interval elapsed")
        assertEquals(manchester, fetched[1])
    }

    @Test
    fun cancels_in_flight_fetch_on_new_viewport() = runTest {
        var cancelled = false
        val fetcher = DebouncedFetcher(
            scope = this,
            clock = { testScheduler.currentTime },
            debounceMs = 500L,
            minIntervalMs = 5000L
        ) { bounds ->
            try {
                kotlinx.coroutines.delay(2000)
                listOf(fakeOverflow("slow"))
            } catch (_: kotlinx.coroutines.CancellationException) {
                cancelled = true
                throw kotlinx.coroutines.CancellationException()
            }
        }

        fetcher.onViewportChanged(london)
        advanceTimeBy(600)

        advanceTimeBy(500)
        fetcher.fetchImmediately(manchester)
        advanceTimeBy(2100)

        assertEquals(true, cancelled)
    }

    @Test
    fun fetchImmediately_bypasses_debounce() = runTest {
        val (fetcher, fetched) = testFetcher(this)

        fetcher.fetchImmediately(london)
        advanceTimeBy(1)
        assertEquals(1, fetched.size)
        assertEquals(london, fetched.first())
    }

    @Test
    fun results_flow_emits_fetch_results() = runTest {
        val (fetcher, _) = testFetcher(this)

        assertEquals(null, fetcher.results.value)

        fetcher.onViewportChanged(london)
        advanceTimeBy(600)

        assertEquals(1, fetcher.results.value?.size)
    }
}
