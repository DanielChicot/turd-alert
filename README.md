# Turd Alert

Cross-platform mobile app showing real-time sewage storm overflow discharge status near the user's GPS location. Built with Kotlin Multiplatform and Compose Multiplatform, targeting Android and iOS from a single codebase.

Data is fetched directly from 10 UK water company public APIs — no backend required.

## Architecture

```mermaid
graph TD
    subgraph composeApp
        App[App.kt] --> VM[OverflowViewModel]
        App --> UI[OverflowList / OverflowCard]
        MA[MainActivity] -->|Android| App
        MVC[MainViewController] -->|iOS| App
    end

    subgraph shared
        VM --> Repo[OverflowRepository]
        VM --> LP[LocationProvider]
        Repo --> WCA[WaterCompanyApi]
        WCA --> AG[ArcGisCompany × 8]
        WCA --> TW[ThamesWaterApi]
        WCA --> WW[WelshWaterApi]
        TW --> OSGB[OsgbConverter]
        WW --> OSGB
        Repo -->|filtered by| NB[NearbyOverflows]
        LP --> ALP[AndroidLocationProvider]
        LP --> ILP[IosLocationProvider]
    end

    AG -->|spatial query| ArcGIS[ArcGIS FeatureServer]
    TW -->|paginated REST| Thames[api.thameswater.co.uk]
    WW -->|spatial query| Welsh[ArcGIS Welsh Water]
```

## Data flow

```mermaid
sequenceDiagram
    participant U as User
    participant V as ViewModel
    participant L as LocationProvider
    participant R as OverflowRepository
    participant A as Water Company APIs

    U->>V: refresh()
    V->>L: currentLocation()
    L-->>V: Coordinates(lat, lon)
    V->>R: allOverflows(location)

    par Parallel fetch from 10 companies
        R->>A: ArcGIS spatial query (bounding box)
        R->>A: Thames Water paginated fetch
        R->>A: Welsh Water spatial query
    end

    A-->>R: List<OverflowPoint>
    R-->>V: merged results
    V->>V: withinRadius(1 mile)
    V-->>U: UiState.Loaded
```

## Water companies

| Company | API type | Spatial filtering |
|---------|----------|-------------------|
| Southern Water | ArcGIS FeatureServer | Server-side bounding box |
| Anglian Water | ArcGIS FeatureServer | Server-side bounding box |
| United Utilities | ArcGIS FeatureServer | Server-side bounding box |
| Severn Trent | ArcGIS FeatureServer | Server-side bounding box |
| Yorkshire Water | ArcGIS FeatureServer | Server-side bounding box |
| Northumbrian Water | ArcGIS FeatureServer | Server-side bounding box |
| South West Water | ArcGIS FeatureServer | Server-side bounding box |
| Wessex Water | ArcGIS FeatureServer | Server-side bounding box |
| Thames Water | Custom REST API | Client-side (service area gate + Haversine) |
| Welsh Water | ArcGIS FeatureServer | Server-side bounding box |

All APIs are unauthenticated and publicly accessible.

## Prerequisites

- **Java 21** — `asdf install java temurin-21.0.7+6.0.LTS`
- **Gradle 8.12** — `asdf install gradle 8.12` (or use `./gradlew`)
- **Android SDK** — `brew install android-commandlinetools`, then accept licences:
  ```bash
  sdkmanager --licenses
  sdkmanager "platforms;android-35" "build-tools;35.0.0"
  ```
- **Xcode** (iOS only) — install from the App Store, then:
  ```bash
  brew install xcodegen
  ```

Create `local.properties` pointing to your Android SDK:

```properties
sdk.dir=/opt/homebrew/share/android-commandlinetools
```

## Build and run

### Android

```bash
./gradlew assembleDebug
adb install composeApp/build/outputs/apk/debug/composeApp-debug.apk
```

### iOS simulator

```bash
# Build the KMP framework
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64

# Generate and build the Xcode project
cd iosApp && xcodegen generate && cd ..
xcodebuild -project iosApp/iosApp.xcodeproj \
  -scheme iosApp \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  build

# Install and run
xcrun simctl boot "iPhone 17 Pro"
APP=$(find ~/Library/Developer/Xcode/DerivedData/iosApp-*/Build/Products/Debug-iphonesimulator -name "iosApp.app" -maxdepth 1)
xcrun simctl install booted "$APP"
xcrun simctl location booted set 53.7133,-2.0974  # Set a fake GPS location
xcrun simctl launch booted com.chicot.turdalert.iosApp
```

## Project structure

```
turd-alert/
├── shared/                          # KMP library (Android + iOS)
│   └── src/
│       ├── commonMain/              # Shared Kotlin code
│       │   └── kotlin/.../
│       │       ├── api/             # HTTP clients, DTOs, coordinate conversion
│       │       ├── domain/          # Proximity filtering
│       │       ├── location/        # LocationProvider interface
│       │       ├── model/           # OverflowPoint, DischargeStatus
│       │       ├── util/            # Haversine distance
│       │       └── viewmodel/       # OverflowViewModel, UiState
│       ├── androidMain/             # Android LocationProvider (LocationManager)
│       └── iosMain/                 # iOS LocationProvider (CLLocationManager)
├── composeApp/                      # Compose Multiplatform UI
│   └── src/
│       ├── commonMain/              # Shared UI (App, OverflowCard, OverflowList)
│       ├── androidMain/             # MainActivity, adaptive icon
│       └── iosMain/                 # MainViewController bridge
└── iosApp/                          # iOS app shell (Swift/SwiftUI)
    ├── iosApp/                      # Swift sources + Info.plist
    └── project.yml                  # xcodegen spec
```

## Status indicators

- **Red** — actively discharging sewage
- **Green** — not discharging
- **Grey** — offline / status unknown

## Key technical details

- **Coordinate conversion**: Thames Water and Welsh Water return OSGB (EPSG:27700) coordinates, converted to WGS84 via Helmert 7-parameter transformation in `OsgbConverter.kt`
- **Bounding box filtering**: ArcGIS companies receive a `geometry` envelope parameter limiting results to ~2 miles around the user, avoiding large payload downloads
- **Thames Water service area gate**: Thames Water lacks server-side spatial queries, so the app skips the API entirely when the user is outside lat 51.0–52.2 / lon -2.2–0.6
- **Per-company error isolation**: Each company is fetched in a separate coroutine; failures return an empty list rather than crashing the whole refresh
- **iOS main thread requirement**: `CLLocationManager` must be created and used on the main thread; the iOS location provider uses `dispatch_async(dispatch_get_main_queue())` and retains strong references to both the manager and delegate to prevent ARC deallocation
