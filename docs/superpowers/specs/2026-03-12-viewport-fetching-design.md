# Dynamic Map Viewport Fetching — Design Spec

Fetch overflow data for the visible map region as the user pans and zooms, replacing the fixed user-location radius approach.

## Behaviour

On launch, the app fetches overflows near the user's location as it does today. Once the user pans or zooms the map, subsequent fetches use the map's visible bounding box instead.

### Debouncing

Two layers protect the upstream APIs from excessive requests:

1. **Debounce (500ms)**: After the viewport stops changing, wait 500ms before triggering a fetch.
2. **Minimum interval (5s)**: Enforce at least 5 seconds between successive fetch starts, even if the viewport changes again within that window.

If a fetch is already in flight when a new one is triggered, cancel the in-flight request and start fresh for the latest viewport.

### Data Replacement

Each fetch replaces the current overflow list entirely. There is no accumulation or caching of previous results. This keeps discharge status data fresh at the cost of re-fetching on every viewport change.

### No Maximum Area Cap

There is no cap on viewport size. If the user zooms out to see all of England, the APIs return whatever they return. This can be revisited if performance becomes an issue.

## Data Flow

```
Viewport changes → debounce 500ms → check minimum interval
  → cancel any in-flight fetch → fetch all 10 APIs with viewport bounding box
  → replace current data entirely → update UI
```

The initial launch fetch uses user location to compute the starting viewport. The refresh FAB re-fetches for the current viewport.

## Architecture

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

### Modified: `OverflowRepository.allOverflows()`

Takes a `BoundingBox` instead of `Coordinates`. Each `WaterCompanyApi` implementation converts this bounding box to its native format:

- **ArcGIS APIs**: Use the bounding box directly as the `geometry` parameter (`"xMin,yMin,xMax,yMax"`)
- **Thames Water**: Use the bounding box for its post-filter distance check (replacing the fixed 5-mile radius)
- **Welsh Water**: Use the bounding box directly

The fixed 5-mile radius calculation currently in `WaterCompanyApi` is replaced by passing the viewport bounds through.

### Modified: `OverflowViewModel`

Gains an `onViewportChanged(bounds: BoundingBox)` method that feeds the `DebouncedFetcher`. The existing `refresh()` method re-fetches for the current viewport bounds.

The `selectedOverflow` state, `selectOverflow()`, and `clearSelection()` are unchanged.

### Modified: `MapView` (both platforms)

Gains a new callback parameter:

```kotlin
onViewportChanged: (BoundingBox) -> Unit
```

- **Android**: `GoogleMap` exposes camera idle listener via `cameraPositionState`. When the camera settles, compute the visible `LatLngBounds` and convert to `BoundingBox`.
- **iOS**: `MKMapView` delegate method `mapView(_:regionDidChangeAnimated:)` fires when the map stops moving. Convert `MKCoordinateRegion` to `BoundingBox`.

### Hybrid Filter Adjustment

The `nearbyOverflows()` hybrid filter (1 mile radius or nearest 5) no longer applies when fetching by viewport. It is only used for the initial location-based fetch. When the user has panned or zoomed, all results from the APIs for that viewport are shown.

## Files

### New files

| File | Purpose |
|------|---------|
| `shared/.../domain/DebouncedFetcher.kt` | Debounce + throttle + cancellation logic |

### Modified files

| File | Change |
|------|--------|
| `shared/.../api/WaterCompanyApi.kt` | Accept `BoundingBox` instead of `Coordinates` |
| `shared/.../api/OverflowRepository.kt` | Pass `BoundingBox` to API implementations |
| `shared/.../viewmodel/OverflowViewModel.kt` | Add `onViewportChanged()`, integrate `DebouncedFetcher` |
| `composeApp/.../map/MapView.kt` (commonMain expect) | Add `onViewportChanged` parameter |
| `composeApp/.../map/MapView.kt` (androidMain) | Report camera idle bounds |
| `composeApp/.../map/MapView.kt` (iosMain) | Report region change bounds |
| `composeApp/.../App.kt` | Wire `onViewportChanged` callback |

### Unchanged

- `OverflowPoint` model
- Pin colours, info card, summary chip, refresh FAB
- 10 parallel API call pattern
- Silent error handling per API
- `BoundingBox` data class (reused as-is)
- `Directions` expect/actual
