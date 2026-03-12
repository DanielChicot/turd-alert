# Map View — Design Spec

Replace the current list-based UI with a full-screen interactive map showing nearby storm overflow discharge points as coloured pins, with the user's location marked.

## User Experience

The map is the sole view. On launch (after location is obtained), the map centres on the user's position with pins for nearby overflow points. Pin colours indicate status:

- Red: actively discharging
- Green: not discharging
- Grey: offline/unknown

A blue dot marks "you are here" (standard platform location indicator style).

Tapping a pin shows a floating info card (callout) anchored above it containing:

- Status indicator dot + site name (bold)
- Watercourse, water company, distance from user (computed inline from `uiState.location` using existing `distanceMiles()`)
- Discharge duration (if currently discharging)
- "Directions" button that opens the native maps app with turn-by-turn navigation

Tapping elsewhere on the map dismisses the info card.

A summary chip at the bottom of the screen shows: "N nearby · M discharging" (M in red if > 0).

A floating refresh button in the top-right corner triggers a data reload.

## Hybrid Proximity Filtering

The current radius-only filter (1 mile) is replaced with a hybrid approach:

1. Filter all fetched overflows to those within 1 mile
2. If fewer than 5 results, take the nearest 5 instead (regardless of distance)
3. Sort by distance ascending

This guarantees the user always sees at least 5 overflow points, even in rural areas. The map zoom adjusts to fit all visible points plus the user's location.

The server-side bounding box sent to ArcGIS APIs widens from 2 miles to 5 miles to ensure enough candidates exist for the "nearest 5" fallback. Thames Water fetches its entire service area client-side so the bounding box change does not apply there; the hybrid filter is applied in the shared domain layer after all results are collected.

## Architecture

```
App (Scaffold)
├── MapView (expect/actual in composeApp)
│   ├── Android: Google Maps via maps-compose
│   └── iOS: MKMapView via UIKitView bridge
├── OverflowInfoCard (common Compose)
├── SummaryChip (common Compose)
├── RefreshFAB (common Compose)
└── StatusIndicator (common Compose, reused from existing)
```

The `expect/actual` boundary covers only the map surface (in `composeApp`) and the directions launcher (in `shared`). The info card, summary chip, and FAB are common Compose code overlaid on the map.

### Camera Auto-Fit

Both platforms receive a `BoundingBox` data class computed in commonMain from all visible overflow points plus the user's location, with padding. This keeps the two platform implementations consistent.

```kotlin
data class BoundingBox(
    val minLat: Double, val maxLat: Double,
    val minLon: Double, val maxLon: Double
)

fun boundingBox(points: List<OverflowPoint>, location: Coordinates, padding: Double = 0.005): BoundingBox
```

Android converts to `LatLngBounds`; iOS converts to `MKCoordinateRegion`.

### Platform: Android

- `maps-compose` library (Google's official Compose wrapper for Maps SDK)
- Free for native mobile apps, no usage-based pricing
- `GoogleMap` composable with `Marker` per overflow point
- Marker colours set via `BitmapDescriptorFactory.defaultMarker()` with hue
- Camera bounds from `BoundingBox` → `LatLngBounds` → `CameraUpdateFactory.newLatLngBounds()`
- Directions: `Intent` with `geo:` URI scheme

### Platform: iOS

- `MKMapView` wrapped via Compose Multiplatform `UIKitView` with `UIKitInteropProperties(interactionMode = NonCooperative)` for correct gesture handling
- Free, no API key needed (system framework)
- `MKPointAnnotation` per overflow point with `MKPinAnnotationView` colour
- Camera region from `BoundingBox` → `MKCoordinateRegion`
- Directions: `MKMapItem.openInMaps()` or Apple Maps URL scheme

## Data Flow

```
refresh()
  → LocationProvider.currentLocation()
  → OverflowRepository.allOverflows(location)  [unchanged]
  → hybridFilter(overflows, location, radius=1.0, minCount=5)
  → sort by distance
  → UiState.Loaded(overflows, location)

Pin tap → selectedOverflow updated → InfoCard shown
Map tap → selectedOverflow cleared → InfoCard dismissed
Directions tap → openDirections(lat, lon) [expect/actual]
Refresh tap → refresh()
```

The ViewModel gains a `selectedOverflow: StateFlow<OverflowPoint?>` for tracking which pin's info card is visible.

## Files

### New files

| File | Purpose |
|------|---------|
| `composeApp/.../map/MapView.kt` (commonMain) | `expect` composable for map surface |
| `composeApp/.../map/MapView.kt` (androidMain) | Google Maps `actual` implementation |
| `composeApp/.../map/MapView.kt` (iosMain) | MapKit `actual` via `UIKitView` |
| `shared/.../map/Directions.kt` (commonMain) | `expect fun openDirections(lat, lon)` |
| `shared/.../map/Directions.kt` (androidMain) | Android `actual` with geo: Intent |
| `shared/.../map/Directions.kt` (iosMain) | iOS `actual` with MKMapItem |
| `shared/.../model/BoundingBox.kt` (commonMain) | Camera bounds data class + computation |
| `composeApp/.../ui/OverflowInfoCard.kt` | Floating callout card composable |
| `composeApp/.../ui/SummaryChip.kt` | Bottom summary chip composable |
| `composeApp/.../ui/RefreshFAB.kt` | Floating refresh button composable |

### Modified files

| File | Change |
|------|--------|
| `composeApp/.../App.kt` | Replace list with map + overlays |
| `composeApp/build.gradle.kts` | Add maps-compose dependency (Android), add explicit `iosMain` source set |
| `composeApp/build.gradle.kts` | Add `manifestPlaceholders["MAPS_API_KEY"]` from `local.properties` |
| `composeApp/src/androidMain/AndroidManifest.xml` | Add Maps API key `<meta-data>` |
| `shared/.../domain/NearbyOverflows.kt` | Add hybrid filtering logic |
| `shared/.../viewmodel/OverflowViewModel.kt` | Add `selectedOverflow` state |
| `shared/.../api/WaterCompanyApi.kt` | Widen ArcGIS bounding box from 2mi to 5mi |
| `gradle/libs.versions.toml` | Add maps-compose and play-services-maps versions |

### Deleted files

| File | Reason |
|------|--------|
| `composeApp/.../ui/OverflowList.kt` | Replaced by map view |
| `composeApp/.../ui/OverflowCard.kt` | Replaced by OverflowInfoCard |

`StatusIndicator.kt` is retained and reused in `OverflowInfoCard`.

## Dependencies

### Android

```toml
# gradle/libs.versions.toml
maps-compose = "6.1.0"
play-services-maps = "19.1.0"

# composeApp/build.gradle.kts (androidMain)
com.google.maps.android:maps-compose
com.google.android.gms:play-services-maps
```

### iOS

No new dependencies. MapKit is a system framework available via Kotlin/Native interop.

## API Key

Google Maps SDK for Android requires an API key in `AndroidManifest.xml`:

```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="${MAPS_API_KEY}" />
```

The key is injected at build time via `manifestPlaceholders` in `composeApp/build.gradle.kts`:

```kotlin
android {
    defaultConfig {
        val localProperties = java.util.Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }
        manifestPlaceholders["MAPS_API_KEY"] = localProperties.getProperty("MAPS_API_KEY", "")
    }
}
```

The `local.properties` file is gitignored. Google Maps SDK is free for native mobile apps.

Apple MapKit requires no API key.
