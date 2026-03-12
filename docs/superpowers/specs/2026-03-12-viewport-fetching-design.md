# Dynamic Map Viewport Fetching — Design Spec

Fetch overflow data for the visible map region as the user pans and zooms, replacing the fixed user-location radius approach.

## Behaviour

On launch, the app fetches overflows near the user's location as it does today. Once the user pans or zooms the map, subsequent fetches use the map's visible bounding box instead.

### Debouncing

Two layers protect the upstream APIs from excessive requests:

1. **Debounce (500ms)**: After the viewport stops changing, wait 500ms before triggering a fetch.
2. **Minimum interval (5s)**: Enforce at least 5 seconds between successive fetch starts, even if the viewport changes again within that window.

If a fetch is already in flight when a new one is triggered, cancel the in-flight request and start fresh for the latest viewport. Cancelling a structured coroutine scope cancels all 10 in-flight HTTP requests — partial results from completed APIs are discarded. This is intentional: the viewport has moved so the partial results are for the wrong region.

The refresh FAB bypasses debounce/throttle and fetches immediately for the current viewport.

### Debounce Timing Example

```
T=0ms      User pans
T=500ms    Debounce fires, fetch starts (lastFetchStart = 500)
T=3000ms   Fetch completes, UI updates
T=4000ms   User pans again
T=4500ms   Debounce fires, but 4500 - 500 = 4000 < 5000 → throttled, scheduled for T=5500
T=5500ms   Fetch starts (lastFetchStart = 5500)
```

### Data Replacement

Each fetch replaces the current overflow list entirely. There is no accumulation or caching of previous results. This keeps discharge status data fresh at the cost of re-fetching on every viewport change.

### No Maximum Area Cap

There is no cap on viewport size. If the user zooms out to see all of England, the APIs return whatever they return. This can be revisited if performance becomes an issue.

### Loading Indicator

Viewport-triggered fetches do not show a loading spinner — the map already has data from the previous fetch, so a spinner on every pan would be distracting. The loading state is only shown for the initial launch fetch and when the refresh FAB is tapped.

## Data Flow

```
Viewport changes → debounce 500ms → check minimum interval
  → cancel any in-flight fetch → fetch all 10 APIs with viewport bounding box
  → replace current data entirely → update UI
```

The initial launch fetch uses user location to compute the starting viewport. The refresh FAB re-fetches for the current viewport immediately (bypassing debounce).

## Architecture

### Breaking the Camera Feedback Loop

Currently `App.kt` computes `cameraBounds()` from the overflow results and pushes them into `MapView`, which sets the camera region. In the new design, the map viewport drives fetching — the map reports its bounds out via `onViewportChanged`. If `App.kt` also pushed computed bounds back in, this would create a feedback loop:

> map reports viewport → fetch → results arrive → `cameraBounds()` recomputes → pushes new region → map reports viewport → ...

The solution: `cameraBounds()` is only used for the **initial** fetch (to set the starting camera position from user location + nearby results). Once the user has interacted with the map, `App.kt` stops pushing camera bounds. The map owns its own viewport from that point on.

The ViewModel tracks this with a `hasUserInteracted: Boolean` flag, set to `true` on the first `onViewportChanged` call.

### UiState Changes

`UiState.Loaded` retains `location: Coordinates` — it is still needed for:
- Distance display in `OverflowInfoCard` (distance from user, not from viewport centre)
- The initial `cameraBounds()` computation

The `overflows` list in `Loaded` is updated by both the initial fetch and viewport fetches. The `nearbyOverflows()` filter is only applied on the initial fetch; viewport fetches populate `overflows` directly with the full API result.

### New: `DebouncedFetcher`

A coroutine-based component in `shared/commonMain` that owns:

- A debounce timer (500ms)
- Minimum interval enforcement (5s)
- A reference to the current fetch `Job` for cancellation
- Accepts a `BoundingBox`, applies debounce/throttle logic, then calls the repository

```kotlin
class DebouncedFetcher(
    private val repository: OverflowRepository,
    private val scope: CoroutineScope,
    private val debounceMs: Long = 500L,
    private val minIntervalMs: Long = 5000L
)
```

The `scope` is the ViewModel's `viewModelScope` so that cancellation is lifecycle-coordinated.

### Modified: `OverflowRepository.allOverflows()`

Takes a `BoundingBox` instead of `Coordinates`. Each `WaterCompanyApi` implementation converts this bounding box to its native format:

- **ArcGIS APIs (8 companies)**: Use the bounding box directly as the `geometry` parameter (`"xMin,yMin,xMax,yMax"`). The fixed 5-mile radius calculation is removed.
- **Welsh Water**: Use the bounding box directly (single ArcGIS request, no pagination).
- **Thames Water**: See below.

### Thames Water: Full Dataset Reality

Thames Water's API has no spatial query parameter. It paginates the **entire dataset** across all of England on every call, then the app post-filters by bounding box. This is the existing behaviour — the current code downloads everything and filters to 5 miles.

With viewport-based fetching, the post-filter changes from "within 5 miles of user" to "within the viewport bounding box." But the full dataset is still downloaded every time. For wide viewports, the post-filter retains most or all of the data, which is fine. For narrow viewports, most data is discarded after download, which is wasteful but unavoidable without a server-side spatial API.

The `inServiceArea()` check changes from a point-in-rectangle test to a **rectangle overlap** test: does the viewport bounding box overlap Thames Water's service area (51.0–52.2°N, -2.2–0.6°E)? This ensures Thames data loads when the user can see into the Thames area even if the viewport centre is outside it.

### Modified: `OverflowViewModel`

Gains an `onViewportChanged(bounds: BoundingBox)` method that feeds the `DebouncedFetcher`. The existing `refresh()` method re-fetches for the current viewport bounds, bypassing debounce.

Tracks `hasUserInteracted: Boolean` to distinguish initial camera positioning from user-driven viewport changes.

The `selectedOverflow` state, `selectOverflow()`, and `clearSelection()` are unchanged.

### Modified: `MapView` (both platforms)

Gains a new callback parameter:

```kotlin
onViewportChanged: (BoundingBox) -> Unit
```

- **Android**: `GoogleMap` exposes camera idle listener via `cameraPositionState`. When the camera settles, compute the visible `LatLngBounds` and convert to `BoundingBox`.
- **iOS**: `MKMapView` delegate method `mapView(_:regionDidChangeAnimated:)` fires when the map stops moving. Convert `MKCoordinateRegion` to `BoundingBox`.

### Hybrid Filter Adjustment

The `nearbyOverflows()` hybrid filter (1 mile radius or nearest 5) is only applied on the initial location-based fetch. When the user has panned or zoomed (`hasUserInteracted = true`), all results from the APIs for that viewport are shown unfiltered.

## Files

### New files

| File | Purpose |
|------|---------|
| `shared/.../domain/DebouncedFetcher.kt` | Debounce + throttle + cancellation logic |

### Modified files

| File | Change |
|------|--------|
| `shared/.../api/WaterCompanyApi.kt` | Accept `BoundingBox` instead of `Coordinates`; Thames `inServiceArea` becomes overlap test |
| `shared/.../api/OverflowRepository.kt` | Pass `BoundingBox` to API implementations |
| `shared/.../viewmodel/OverflowViewModel.kt` | Add `onViewportChanged()`, `hasUserInteracted`, integrate `DebouncedFetcher` |
| `composeApp/.../map/MapView.kt` (commonMain expect) | Add `onViewportChanged` parameter |
| `composeApp/.../map/MapView.kt` (androidMain) | Report camera idle bounds |
| `composeApp/.../map/MapView.kt` (iosMain) | Report region change bounds via delegate |
| `composeApp/.../App.kt` | Wire `onViewportChanged`; only push `cameraBounds` before user interaction |

### Unchanged

- `OverflowPoint` model
- Pin colours, info card, summary chip, refresh FAB UI
- 10 parallel API call pattern
- Silent error handling per API
- `BoundingBox` data class (reused as-is)
- `cameraBounds()` function (still used for initial fetch, not for viewport mode)
- `Directions` expect/actual
