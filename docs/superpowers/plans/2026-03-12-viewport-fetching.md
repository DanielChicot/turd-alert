# Viewport Fetching Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fetch overflow data dynamically based on the visible map viewport as the user pans and zooms, with debounce + throttle to protect upstream APIs.

**Architecture:** A `DebouncedFetcher` in commonMain handles debounce (500ms), minimum interval (5s), and in-flight cancellation. The API layer changes from `Coordinates` to `BoundingBox`. The ViewModel tracks whether the user has interacted with the map to break the camera feedback loop.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, kotlinx.coroutines (with coroutines-test for testing), Google Maps Compose (Android), MapKit (iOS)

---

## File Structure

### New files

| File | Responsibility |
|------|---------------|
| `shared/src/commonMain/kotlin/com/chicot/turdalert/domain/DebouncedFetcher.kt` | Debounce + throttle + cancellation for viewport-triggered fetches |
| `shared/src/commonTest/kotlin/com/chicot/turdalert/domain/DebouncedFetcherTest.kt` | Tests for debounce, throttle, cancellation behaviour |

### Modified files

| File | Change |
|------|--------|
| `gradle/libs.versions.toml` | Add `kotlinx-coroutines-test` and `kotlin-test` libraries |
| `shared/build.gradle.kts` | Add `commonTest` dependencies |
| `shared/src/commonMain/kotlin/com/chicot/turdalert/api/WaterCompanyApi.kt` | Accept `BoundingBox` instead of `Coordinates`; Thames `inServiceArea` becomes overlap test |
| `shared/src/commonMain/kotlin/com/chicot/turdalert/api/OverflowRepository.kt` | Pass `BoundingBox` to API implementations |
| `shared/src/commonMain/kotlin/com/chicot/turdalert/viewmodel/OverflowViewModel.kt` | Add `onViewportChanged()`, `hasUserInteracted`, integrate `DebouncedFetcher` |
| `composeApp/src/commonMain/kotlin/com/chicot/turdalert/map/MapView.kt` | Add `onViewportChanged` parameter, make `bounds` nullable |
| `composeApp/src/androidMain/kotlin/com/chicot/turdalert/map/MapView.kt` | Report camera idle bounds, conditionally set camera |
| `composeApp/src/iosMain/kotlin/com/chicot/turdalert/map/MapView.kt` | Report region change bounds via delegate, conditionally set region |
| `composeApp/src/commonMain/kotlin/com/chicot/turdalert/App.kt` | Wire `onViewportChanged`, only push bounds before user interaction |
| `shared/src/commonMain/kotlin/com/chicot/turdalert/model/BoundingBox.kt` | Add `BoundingBox.overlaps()` extension and `Coordinates.toBoundingBox()` |

---

## Chunk 1: DebouncedFetcher

### Task 1: Add test dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `shared/build.gradle.kts`

- [ ] **Step 1: Add libraries to version catalog**

In `gradle/libs.versions.toml`, add to the `[libraries]` section:

```toml
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
```

- [ ] **Step 2: Add commonTest dependencies to shared module**

In `shared/build.gradle.kts`, add `kotlinx-datetime` to `commonMain` (needed by `DebouncedFetcher`'s default clock) and add a `commonTest` block:

```kotlin
commonMain.dependencies {
    // ... existing dependencies ...
    implementation(libs.kotlinx.datetime)
}

commonTest.dependencies {
    implementation(libs.kotlin.test)
    implementation(libs.kotlinx.coroutines.test)
}
```

Note: `kotlinx-datetime` is already in `gradle/libs.versions.toml` — just not yet a dependency of the `shared` module.

- [ ] **Step 3: Verify project syncs**

Run: `./gradlew :shared:dependencies --configuration commonMainApi 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml shared/build.gradle.kts
git commit -m "Add test dependencies for shared module"
```

---

### Task 2: Add BoundingBox helpers

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/chicot/turdalert/model/BoundingBox.kt`

The `DebouncedFetcher` and updated API layer need two helpers on `BoundingBox`:

1. `overlaps()` — rectangle intersection test for Thames Water service area check
2. `Coordinates.toBoundingBox()` — convert user location to a bounding box with a radius (for initial fetch)

- [ ] **Step 1: Add helpers to BoundingBox.kt**

Add these after the existing `cameraBounds` function in `shared/src/commonMain/kotlin/com/chicot/turdalert/model/BoundingBox.kt`:

```kotlin
private const val DEGREES_PER_MILE_LAT = 1.0 / 69.0
private const val DEGREES_PER_MILE_LON_AT_55 = 1.0 / 39.0

fun BoundingBox.overlaps(other: BoundingBox): Boolean =
    minLat <= other.maxLat && maxLat >= other.minLat &&
        minLon <= other.maxLon && maxLon >= other.minLon

fun Coordinates.toBoundingBox(radiusMiles: Double = 5.0): BoundingBox {
    val dLat = radiusMiles * DEGREES_PER_MILE_LAT
    val dLon = radiusMiles * DEGREES_PER_MILE_LON_AT_55
    return BoundingBox(
        minLat = latitude - dLat,
        maxLat = latitude + dLat,
        minLon = longitude - dLon,
        maxLon = longitude + dLon
    )
}
```

- [ ] **Step 2: Add import for Coordinates**

The file already imports `com.chicot.turdalert.location.Coordinates` (used by `cameraBounds`). No new import needed.

- [ ] **Step 3: Verify it compiles**

Run: `./gradlew :shared:compileKotlinMetadata 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/chicot/turdalert/model/BoundingBox.kt
git commit -m "Add BoundingBox.overlaps and Coordinates.toBoundingBox helpers"
```

---

### Task 3: Create DebouncedFetcher with tests

**Files:**
- Create: `shared/src/commonMain/kotlin/com/chicot/turdalert/domain/DebouncedFetcher.kt`
- Create: `shared/src/commonTest/kotlin/com/chicot/turdalert/domain/DebouncedFetcherTest.kt`

- [ ] **Step 1: Write the tests**

Create `shared/src/commonTest/kotlin/com/chicot/turdalert/domain/DebouncedFetcherTest.kt`:

```kotlin
package com.chicot.turdalert.domain

import com.chicot.turdalert.model.BoundingBox
import com.chicot.turdalert.model.OverflowPoint
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
        status = com.chicot.turdalert.model.DischargeStatus.NOT_DISCHARGING,
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :shared:allTests 2>&1 | tail -20`
Expected: Compilation error — `DebouncedFetcher` class does not exist yet.

- [ ] **Step 3: Create DebouncedFetcher**

Create `shared/src/commonMain/kotlin/com/chicot/turdalert/domain/DebouncedFetcher.kt`:

```kotlin
package com.chicot.turdalert.domain

import com.chicot.turdalert.model.BoundingBox
import com.chicot.turdalert.model.OverflowPoint
import kotlinx.coroutines.CancellationException
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
    private var lastFetchStartMs: Long = 0L

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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :shared:allTests 2>&1 | tail -20`
Expected: All 6 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/chicot/turdalert/domain/DebouncedFetcher.kt \
       shared/src/commonTest/kotlin/com/chicot/turdalert/domain/DebouncedFetcherTest.kt
git commit -m "Add DebouncedFetcher with debounce, throttle, and cancellation"
```

---

## Chunk 2: API Layer Changes

### Task 4: Change WaterCompanyApi to accept BoundingBox

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/chicot/turdalert/api/WaterCompanyApi.kt`
- Modify: `shared/src/commonMain/kotlin/com/chicot/turdalert/api/OverflowRepository.kt`

This is the largest single change. The sealed interface, all three implementation types, and the repository must change together — they won't compile independently.

- [ ] **Step 1: Update WaterCompanyApi sealed interface**

In `shared/src/commonMain/kotlin/com/chicot/turdalert/api/WaterCompanyApi.kt`:

Change the interface method signature from:
```kotlin
suspend fun fetchOverflows(client: HttpClient, location: Coordinates): List<OverflowPoint>
```
to:
```kotlin
suspend fun fetchOverflows(client: HttpClient, bounds: BoundingBox): List<OverflowPoint>
```

Add this import at the top:
```kotlin
import com.chicot.turdalert.model.BoundingBox
import com.chicot.turdalert.model.overlaps
```

- [ ] **Step 2: Remove the old `boundingBox()` function, add `BoundingBox.toArcGisGeometry()`**

Delete the existing private `boundingBox()` function (which takes `Coordinates` and `radiusMiles`).

Delete the three constants it used:
```kotlin
private const val SEARCH_RADIUS_MILES = 5.0
private const val DEGREES_PER_MILE_LAT = 1.0 / 69.0
private const val DEGREES_PER_MILE_LON_AT_55 = 1.0 / 39.0
```

Add a simple extension in its place:
```kotlin
private fun BoundingBox.toArcGisGeometry(): String =
    "$minLon,$minLat,$maxLon,$maxLat"
```

- [ ] **Step 3: Update ArcGisCompany**

Change the `fetchOverflows` override and both private methods from `location: Coordinates` to `bounds: BoundingBox`. Replace all calls to `boundingBox(location)` with `bounds.toArcGisGeometry()`.

The override:
```kotlin
override suspend fun fetchOverflows(client: HttpClient, bounds: BoundingBox): List<OverflowPoint> =
    if (isSouthWestWater) fetchSouthWestWater(client, bounds) else fetchStandard(client, bounds)
```

In `fetchStandard`, change parameter to `bounds: BoundingBox` and the geometry parameter to:
```kotlin
parameter("geometry", bounds.toArcGisGeometry())
```

Same change in `fetchSouthWestWater`.

Remove the `Coordinates` import if it is no longer used in this file. (It will still be used by Thames Water — check before removing.)

- [ ] **Step 4: Update ThamesWaterApi**

Change `inServiceArea` from a point-in-rectangle test to a rectangle overlap test:

```kotlin
private val serviceArea = BoundingBox(
    minLat = SERVICE_AREA_LAT_MIN,
    maxLat = SERVICE_AREA_LAT_MAX,
    minLon = SERVICE_AREA_LON_MIN,
    maxLon = SERVICE_AREA_LON_MAX
)

private fun inServiceArea(bounds: BoundingBox): Boolean =
    bounds.overlaps(serviceArea)
```

Change `fetchOverflows` signature to `bounds: BoundingBox`. Update the `inServiceArea` call and the post-filter:

```kotlin
override suspend fun fetchOverflows(client: HttpClient, bounds: BoundingBox): List<OverflowPoint> {
    if (!inServiceArea(bounds)) return emptyList()
    return paginate { offset ->
        client.get(BASE_URL) {
            parameter("limit", PAGE_SIZE)
            parameter("offset", offset)
        }.body<ThamesWaterResponse>().items
    }.mapNotNull { item ->
        val x = item.x ?: return@mapNotNull null
        val y = item.y ?: return@mapNotNull null
        val id = item.permitNumber ?: return@mapNotNull null
        val (lat, lon) = osgbToWgs84(x, y)
        OverflowPoint(
            id = id,
            latitude = lat,
            longitude = lon,
            status = thamesStatus(item.alertStatus),
            watercourse = item.receivingWaterCourse ?: "Unknown",
            siteName = item.locationName ?: "Unknown",
            statusStart = null,
            company = companyName
        )
    }.filter { point ->
        point.latitude in bounds.minLat..bounds.maxLat &&
            point.longitude in bounds.minLon..bounds.maxLon
    }
}
```

Note: the post-filter changes from `distanceMiles <= SEARCH_RADIUS_MILES` to a simple bounding box containment check. This means the `distanceMiles` import can be removed from this file.

- [ ] **Step 5: Update WelshWaterApi**

Change `fetchOverflows` signature to `bounds: BoundingBox`. Replace `boundingBox(location)` with `bounds.toArcGisGeometry()`:

```kotlin
override suspend fun fetchOverflows(client: HttpClient, bounds: BoundingBox): List<OverflowPoint> =
    client.get(URL) {
        parameter("where", "1=1")
        parameter("outFields", "*")
        parameter("geometry", bounds.toArcGisGeometry())
        parameter("geometryType", "esriGeometryEnvelope")
        parameter("spatialRel", "esriSpatialRelIntersects")
        parameter("inSR", "4326")
        parameter("f", "json")
    }.body<WelshWaterResponse>()
        // ... rest unchanged
```

- [ ] **Step 6: Update OverflowRepository**

In `shared/src/commonMain/kotlin/com/chicot/turdalert/api/OverflowRepository.kt`:

Change:
```kotlin
import com.chicot.turdalert.location.Coordinates
```
to:
```kotlin
import com.chicot.turdalert.model.BoundingBox
```

Change the method signature:
```kotlin
suspend fun allOverflows(bounds: BoundingBox): List<OverflowPoint> =
    coroutineScope {
        apis.map { api ->
            async {
                try {
                    api.fetchOverflows(client, bounds)
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }
```

- [ ] **Step 7: Clean up imports in WaterCompanyApi.kt**

Remove any now-unused imports:
- `com.chicot.turdalert.location.Coordinates` (if no longer referenced)
- `com.chicot.turdalert.util.distanceMiles` (was used by Thames post-filter, now replaced with bounding box check)

- [ ] **Step 8: Verify compilation**

Run: `./gradlew :shared:compileKotlinMetadata 2>&1 | tail -10`
Expected: Compilation errors in `OverflowViewModel.kt` — it still calls `repository.allOverflows(location)` with `Coordinates`. This is expected and will be fixed in the next task.

- [ ] **Step 9: Commit**

```bash
git add shared/src/commonMain/kotlin/com/chicot/turdalert/api/WaterCompanyApi.kt \
       shared/src/commonMain/kotlin/com/chicot/turdalert/api/OverflowRepository.kt
git commit -m "Change API layer to accept BoundingBox instead of Coordinates"
```

---

## Chunk 3: ViewModel and UI Integration

### Task 5: Update OverflowViewModel

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/chicot/turdalert/viewmodel/OverflowViewModel.kt`

- [ ] **Step 1: Rewrite OverflowViewModel**

Replace the full contents of `shared/src/commonMain/kotlin/com/chicot/turdalert/viewmodel/OverflowViewModel.kt` with:

```kotlin
package com.chicot.turdalert.viewmodel

import com.chicot.turdalert.api.OverflowRepository
import com.chicot.turdalert.domain.DebouncedFetcher
import com.chicot.turdalert.domain.nearbyOverflows
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

    private val _selectedOverflow = MutableStateFlow<OverflowPoint?>(null)
    val selectedOverflow: StateFlow<OverflowPoint?> = _selectedOverflow.asStateFlow()

    private val _hasUserInteracted = MutableStateFlow(false)
    val hasUserInteracted: StateFlow<Boolean> = _hasUserInteracted.asStateFlow()

    private var currentViewportBounds: BoundingBox? = null
    private var initialLoadComplete = false

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
                    _state.value = UiState.Loaded(
                        overflows = results,
                        location = location
                    )
                }
            }
        }
    }

    fun selectOverflow(overflow: OverflowPoint) {
        _selectedOverflow.value = overflow
    }

    fun clearSelection() {
        _selectedOverflow.value = null
    }

    fun onViewportChanged(bounds: BoundingBox) {
        currentViewportBounds = bounds
        if (!initialLoadComplete) return
        _hasUserInteracted.value = true
        debouncedFetcher.onViewportChanged(bounds)
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

                _state.value = UiState.Loaded(
                    overflows = overflows,
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
```

Key changes from the current version:
- `DebouncedFetcher` created in constructor, collects results in `init` block
- `onViewportChanged()` method feeds the debounced fetcher — but only after `initialLoadComplete` is true, preventing programmatic camera positioning from triggering viewport fetches
- `hasUserInteracted` tracks whether the user has panned/zoomed (only set after initial load)
- `currentViewportBounds` stores the last known viewport for refresh FAB
- `refresh()` calls `debouncedFetcher.cancel()` first to prevent race conditions with in-flight debounced fetches, then fetches directly
- `refresh()` uses `currentViewportBounds` if available (viewport mode) or computes from location (initial mode)
- `refresh()` sets `initialLoadComplete = true` after first success, enabling viewport-driven fetching
- `nearbyOverflows()` filter only applied when `!hasUserInteracted`

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :shared:compileKotlinMetadata 2>&1 | tail -10`
Expected: BUILD SUCCESSFUL (or errors only in composeApp, which still passes old args to MapView).

- [ ] **Step 3: Run tests**

Run: `./gradlew :shared:allTests 2>&1 | tail -10`
Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add shared/src/commonMain/kotlin/com/chicot/turdalert/viewmodel/OverflowViewModel.kt
git commit -m "Integrate DebouncedFetcher into ViewModel with viewport tracking"
```

---

### Task 6: Update MapView expect and Android actual

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/chicot/turdalert/map/MapView.kt`
- Modify: `composeApp/src/androidMain/kotlin/com/chicot/turdalert/map/MapView.kt`

- [ ] **Step 1: Update the expect declaration**

Replace `composeApp/src/commonMain/kotlin/com/chicot/turdalert/map/MapView.kt` with:

```kotlin
package com.chicot.turdalert.map

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chicot.turdalert.location.Coordinates
import com.chicot.turdalert.model.BoundingBox
import com.chicot.turdalert.model.OverflowPoint

@Composable
expect fun MapView(
    overflows: List<OverflowPoint>,
    userLocation: Coordinates,
    bounds: BoundingBox?,
    onMarkerClick: (OverflowPoint) -> Unit,
    onMapClick: () -> Unit,
    onViewportChanged: (BoundingBox) -> Unit,
    modifier: Modifier = Modifier
)
```

Changes: `bounds` is now `BoundingBox?` (nullable — null means don't reposition camera), and `onViewportChanged` added.

- [ ] **Step 2: Update Android actual**

Replace `composeApp/src/androidMain/kotlin/com/chicot/turdalert/map/MapView.kt` with:

```kotlin
package com.chicot.turdalert.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.chicot.turdalert.location.Coordinates
import com.chicot.turdalert.model.BoundingBox
import com.chicot.turdalert.model.DischargeStatus
import com.chicot.turdalert.model.OverflowPoint
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter

private fun DischargeStatus.markerHue(): Float = when (this) {
    DischargeStatus.DISCHARGING -> BitmapDescriptorFactory.HUE_RED
    DischargeStatus.NOT_DISCHARGING -> BitmapDescriptorFactory.HUE_GREEN
    DischargeStatus.OFFLINE -> BitmapDescriptorFactory.HUE_VIOLET
}

@Composable
actual fun MapView(
    overflows: List<OverflowPoint>,
    userLocation: Coordinates,
    bounds: BoundingBox?,
    onMarkerClick: (OverflowPoint) -> Unit,
    onMapClick: () -> Unit,
    onViewportChanged: (BoundingBox) -> Unit,
    modifier: Modifier
) {
    val cameraPositionState = rememberCameraPositionState()

    LaunchedEffect(bounds) {
        if (bounds != null) {
            val latLngBounds = LatLngBounds(
                LatLng(bounds.minLat, bounds.minLon),
                LatLng(bounds.maxLat, bounds.maxLon)
            )
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(latLngBounds, 64))
        }
    }

    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.isMoving }
            .filter { !it }
            .collectLatest {
                val projection = cameraPositionState.projection ?: return@collectLatest
                val visibleRegion = projection.visibleRegion
                val latLngBounds = visibleRegion.latLngBounds
                onViewportChanged(
                    BoundingBox(
                        minLat = latLngBounds.southwest.latitude,
                        maxLat = latLngBounds.northeast.latitude,
                        minLon = latLngBounds.southwest.longitude,
                        maxLon = latLngBounds.northeast.longitude
                    )
                )
            }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = false),
        onMapClick = { onMapClick() }
    ) {
        Circle(
            center = LatLng(userLocation.latitude, userLocation.longitude),
            radius = 40.0,
            fillColor = androidx.compose.ui.graphics.Color(0x554285F4),
            strokeColor = androidx.compose.ui.graphics.Color(0xFF4285F4),
            strokeWidth = 2f
        )

        overflows.forEach { overflow ->
            Marker(
                state = rememberMarkerState(
                    key = overflow.id,
                    position = LatLng(overflow.latitude, overflow.longitude)
                ),
                title = overflow.siteName,
                icon = BitmapDescriptorFactory.defaultMarker(overflow.status.markerHue()),
                onClick = {
                    onMarkerClick(overflow)
                    true
                }
            )
        }
    }
}
```

Key changes:
- `bounds` is nullable — `LaunchedEffect` skips camera animation when null
- Second `LaunchedEffect` observes `cameraPositionState.isMoving` — when the camera stops moving (`false`), it reads the visible bounds and calls `onViewportChanged`
- Uses `snapshotFlow` + `filter { !it }` to only fire when camera settles

- [ ] **Step 3: Verify Android compilation**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid 2>&1 | tail -10`
Expected: Errors in `App.kt` (doesn't pass `onViewportChanged` yet). The MapView files themselves should compile if the expect/actual signatures match.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/chicot/turdalert/map/MapView.kt \
       composeApp/src/androidMain/kotlin/com/chicot/turdalert/map/MapView.kt
git commit -m "Update MapView expect and Android actual with viewport change reporting"
```

---

### Task 7: Update MapView iOS actual

**Files:**
- Modify: `composeApp/src/iosMain/kotlin/com/chicot/turdalert/map/MapView.kt`

- [ ] **Step 1: Replace iOS MapView**

Replace `composeApp/src/iosMain/kotlin/com/chicot/turdalert/map/MapView.kt` with:

```kotlin
package com.chicot.turdalert.map

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropInteractionMode
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.chicot.turdalert.location.Coordinates
import com.chicot.turdalert.model.BoundingBox
import com.chicot.turdalert.model.DischargeStatus
import com.chicot.turdalert.model.OverflowPoint
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.Foundation.NSSelectorFromString
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKCoordinateRegionMake
import platform.MapKit.MKCoordinateSpanMake
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKPinAnnotationView
import platform.MapKit.MKPointAnnotation
import platform.UIKit.UITapGestureRecognizer
import platform.darwin.NSObject

@OptIn(ExperimentalForeignApi::class)
private class OverflowAnnotation(
    val overflow: OverflowPoint
) : MKPointAnnotation() {
    init {
        setCoordinate(CLLocationCoordinate2DMake(overflow.latitude, overflow.longitude))
        setTitle(overflow.siteName)
    }
}

private class MapDelegate(
    private val onMarkerClick: (OverflowPoint) -> Unit,
    private val onMapClick: () -> Unit,
    private val onViewportChanged: (BoundingBox) -> Unit
) : NSObject(), MKMapViewDelegateProtocol {

    @ObjCAction
    fun handleMapTap() {
        onMapClick()
    }

    override fun mapView(
        mapView: MKMapView,
        viewForAnnotation: MKAnnotationProtocol
    ): MKAnnotationView? {
        if (viewForAnnotation is OverflowAnnotation) {
            val id = "overflow"
            val view = mapView.dequeueReusableAnnotationViewWithIdentifier(id) as? MKPinAnnotationView
                ?: MKPinAnnotationView(annotation = viewForAnnotation, reuseIdentifier = id)
            view.annotation = viewForAnnotation
            view.pinTintColor = when (viewForAnnotation.overflow.status) {
                DischargeStatus.DISCHARGING -> platform.UIKit.UIColor.redColor
                DischargeStatus.NOT_DISCHARGING -> platform.UIKit.UIColor.greenColor
                DischargeStatus.OFFLINE -> platform.UIKit.UIColor.grayColor
            }
            view.canShowCallout = false
            return view
        }
        return null
    }

    override fun mapView(mapView: MKMapView, didSelectAnnotationView: MKAnnotationView) {
        val annotation = didSelectAnnotationView.annotation
        if (annotation is OverflowAnnotation) {
            onMarkerClick(annotation.overflow)
        }
        mapView.deselectAnnotation(annotation, animated = false)
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    override fun mapView(mapView: MKMapView, regionDidChangeAnimated: Boolean) {
        val region = mapView.region
        val center = region.center
        val span = region.span
        onViewportChanged(
            BoundingBox(
                minLat = center.latitude - span.latitudeDelta / 2.0,
                maxLat = center.latitude + span.latitudeDelta / 2.0,
                minLon = center.longitude - span.longitudeDelta / 2.0,
                maxLon = center.longitude + span.longitudeDelta / 2.0
            )
        )
    }
}

@OptIn(ExperimentalForeignApi::class, ExperimentalComposeUiApi::class)
@Composable
actual fun MapView(
    overflows: List<OverflowPoint>,
    userLocation: Coordinates,
    bounds: BoundingBox?,
    onMarkerClick: (OverflowPoint) -> Unit,
    onMapClick: () -> Unit,
    onViewportChanged: (BoundingBox) -> Unit,
    modifier: Modifier
) {
    val delegate = remember(onMarkerClick, onMapClick, onViewportChanged) {
        MapDelegate(onMarkerClick, onMapClick, onViewportChanged)
    }

    UIKitView(
        factory = {
            MKMapView().apply {
                this.delegate = delegate
                showsUserLocation = true
                val tapGesture = UITapGestureRecognizer(
                    target = delegate,
                    action = NSSelectorFromString("handleMapTap")
                )
                addGestureRecognizer(tapGesture)
            }
        },
        update = { mapView ->
            mapView.removeAnnotations(mapView.annotations)

            overflows.forEach { overflow ->
                mapView.addAnnotation(OverflowAnnotation(overflow))
            }

            if (bounds != null) {
                val centerLat = (bounds.minLat + bounds.maxLat) / 2.0
                val centerLon = (bounds.minLon + bounds.maxLon) / 2.0
                val spanLat = (bounds.maxLat - bounds.minLat) * 1.2
                val spanLon = (bounds.maxLon - bounds.minLon) * 1.2
                val region = MKCoordinateRegionMake(
                    CLLocationCoordinate2DMake(centerLat, centerLon),
                    MKCoordinateSpanMake(spanLat, spanLon)
                )
                mapView.setRegion(region, animated = true)
            }
        },
        properties = UIKitInteropProperties(
            interactionMode = UIKitInteropInteractionMode.NonCooperative
        ),
        modifier = modifier
    )
}
```

Key changes from previous version:
- `MapDelegate` gains `onViewportChanged` parameter
- New delegate override `mapView(_:regionDidChangeAnimated:)` converts `MKCoordinateRegion` to `BoundingBox` and calls the callback
- `bounds` is nullable — the `update` block only calls `setRegion` when non-null
- `remember` key list includes `onViewportChanged`

**Important note on `regionDidChangeAnimated`:** This delegate method fires both for user-driven map movements AND for programmatic `setRegion` calls. The ViewModel handles this with an `initialLoadComplete` flag — `onViewportChanged` calls are ignored until the first `refresh()` succeeds. This prevents programmatic camera positioning from prematurely setting `hasUserInteracted = true`. The same applies to Android's camera idle listener.

- [ ] **Step 2: Commit**

```bash
git add composeApp/src/iosMain/kotlin/com/chicot/turdalert/map/MapView.kt
git commit -m "Update MapView iOS actual with viewport change reporting"
```

---

### Task 8: Update App.kt

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/chicot/turdalert/App.kt`

- [ ] **Step 1: Wire viewport changes and conditional camera bounds**

Replace `composeApp/src/commonMain/kotlin/com/chicot/turdalert/App.kt` with:

```kotlin
package com.chicot.turdalert

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chicot.turdalert.api.createOverflowRepository
import com.chicot.turdalert.location.LocationProvider
import com.chicot.turdalert.map.MapView
import com.chicot.turdalert.map.openDirections
import com.chicot.turdalert.model.cameraBounds
import com.chicot.turdalert.ui.OverflowInfoCard
import com.chicot.turdalert.ui.RefreshFAB
import com.chicot.turdalert.ui.SummaryChip
import com.chicot.turdalert.viewmodel.OverflowViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.Clock

@Composable
fun App(locationProvider: LocationProvider) {
    val viewModel = remember {
        val repository = createOverflowRepository()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        OverflowViewModel(repository, locationProvider, scope)
    }

    val uiState by viewModel.state.collectAsState()
    val selectedOverflow by viewModel.selectedOverflow.collectAsState()
    val hasUserInteracted by viewModel.hasUserInteracted.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    MaterialTheme {
        when (val state = uiState) {
            is OverflowViewModel.UiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            is OverflowViewModel.UiState.Loaded -> {
                val bounds = if (hasUserInteracted) null
                    else cameraBounds(state.overflows, state.location)

                Box(modifier = Modifier.fillMaxSize()) {
                    MapView(
                        overflows = state.overflows,
                        userLocation = state.location,
                        bounds = bounds,
                        onMarkerClick = { viewModel.selectOverflow(it) },
                        onMapClick = { viewModel.clearSelection() },
                        onViewportChanged = { viewModel.onViewportChanged(it) },
                        modifier = Modifier.fillMaxSize()
                    )

                    RefreshFAB(
                        onClick = { viewModel.refresh() },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    )

                    SummaryChip(
                        overflows = state.overflows,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                    )

                    selectedOverflow?.let { overflow ->
                        OverflowInfoCard(
                            overflow = overflow,
                            userLocation = state.location,
                            currentTimeMillis = Clock.System.now().toEpochMilliseconds(),
                            onDirectionsClick = {
                                openDirections(overflow.latitude, overflow.longitude)
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 64.dp)
                        )
                    }
                }
            }

            is OverflowViewModel.UiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text("Retry")
                        }
                    }
                }
            }

            is OverflowViewModel.UiState.NoLocation -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Unable to determine your location",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Please enable location services and try again",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}
```

The only changes from the previous version:
- Collects `hasUserInteracted` from ViewModel
- `bounds` is `null` when `hasUserInteracted` is true (stops pushing camera bounds)
- `onViewportChanged` callback wired to `viewModel.onViewportChanged(it)`

- [ ] **Step 2: Build both platforms**

Run: `./gradlew :composeApp:assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

Run: `./gradlew :composeApp:iosSimulatorArm64MainBinaries 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run all tests**

Run: `./gradlew :shared:allTests 2>&1 | tail -10`
Expected: All tests PASS.

- [ ] **Step 4: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/chicot/turdalert/App.kt
git commit -m "Wire viewport-driven fetching in App with camera feedback loop prevention"
```

---

## Chunk 4: Manual Testing

### Task 9: Test on Android

- [ ] **Step 1: Build and install**

```bash
./gradlew :composeApp:assembleDebug
adb install -r composeApp/build/outputs/apk/debug/composeApp-debug.apk
adb shell am start -n com.chicot.turdalert/.MainActivity
```

- [ ] **Step 2: Verify behaviour**

1. App launches, shows loading spinner, then map with pins near user location
2. Pan the map — after ~500ms pause, new pins load for the visible area
3. Zoom out — more pins appear (wider bounding box sent to APIs)
4. Rapid panning — only one fetch fires after movement stops (debounce working)
5. Pan, wait 1 second, pan again — second fetch delayed until 5s after first (throttle working)
6. Tap refresh FAB — immediate reload for current viewport
7. Pin tap still shows info card, map tap dismisses it

- [ ] **Step 3: Commit verification note (optional)**

No code change. Move to next task.

---

### Task 10: Test on iOS simulator

- [ ] **Step 1: Build and install**

```bash
./gradlew :composeApp:iosSimulatorArm64MainBinaries
cd iosApp && xcodegen generate
xcodebuild -project iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator -destination "platform=iOS Simulator,name=iPhone 17 Pro" -configuration Debug build
xcrun simctl install booted iosApp.app
xcrun simctl launch booted com.chicot.turdalert.iosApp
```

Note: Set simulator location to a UK location via Features → Location → Custom Location (e.g. 51.5074, -0.1278 for London).

- [ ] **Step 2: Verify same behaviour as Android test above**

- [ ] **Step 3: Final commit if any fixes needed**
