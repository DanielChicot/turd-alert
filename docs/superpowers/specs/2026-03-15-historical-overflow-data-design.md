# Historical Overflow Data: Polling, Storage, and Visualization

**Epic:** turd-alert-14r
**Date:** 2026-03-15
**Status:** Draft

## Overview

A backend service that polls all 10 UK water company APIs every 15 minutes, stores complete snapshots in Postgres, and exposes historical data to the mobile app via a REST API. The app adopts a hybrid fetching strategy — preferring the backend for current data but falling back to direct API calls when the backend is unreachable.

## Architecture

### Gradle Modules

```
turd-alert/
├── shared/        # existing KMP module (API clients, models, coordinate conversion)
├── composeApp/    # existing Compose Multiplatform UI
├── iosApp/        # existing iOS shell
├── poller/        # NEW — scheduled polling + REST API server (JVM)
├── server/        # NEW — Ktor REST API routes and request handling (JVM)
└── db/            # NEW — Postgres schema, migrations (Flyway), repository layer
```

### Deployment

Single JAR deployed on a Raspberry Pi. One process runs both the poller and the REST API server. Modules are separate in code for clean boundaries but packaged together for simple ops.

Migration path: the same JAR runs on a VPS or container platform when the app gains users. No architectural changes required — just a different host and a managed Postgres instance.

### System Diagram

```mermaid
graph LR
    subgraph "Raspberry Pi"
        Poller[Poller<br/>15-min coroutine loop]
        API[REST API<br/>Ktor server]
        PG[(Postgres)]
    end

    subgraph "Water Companies"
        TW[Thames Water]
        WW[Welsh Water]
        AG[8x ArcGIS<br/>companies]
    end

    subgraph "Mobile App"
        App[Turd Alert]
    end

    Poller -->|polls every 15 min| TW
    Poller -->|polls every 15 min| WW
    Poller -->|polls every 15 min| AG
    Poller -->|upsert sites<br/>insert readings| PG
    API -->|query| PG
    App -->|preferred: /api/v1/*| API
    App -.->|fallback: direct calls| TW
    App -.->|fallback: direct calls| WW
    App -.->|fallback: direct calls| AG
```

## Data Model

### `sites` Table

Upserted on each poll cycle. If a water company adds, renames, or moves a site, we pick it up automatically.

```sql
create table sites (
    id          text primary key,
    company     text not null,
    site_name   text,
    watercourse text,
    latitude    double precision not null,
    longitude   double precision not null
);

create index idx_sites_company on sites (company);
create index idx_sites_location on sites (latitude, longitude);
```

### `readings` Table

Append-only, partitioned by month on `polled_at`. At ~14,187 sites x 96 polls/day = ~1.36M rows/day, ~497M rows/year.

```sql
create table readings (
    site_id      text not null references sites(id),
    polled_at    timestamptz not null,
    status       smallint not null,   -- 1=discharging, 0=not discharging, -1=offline
    status_start timestamptz,

    primary key (site_id, polled_at)
) partition by range (polled_at);
```

Monthly partitions created by Flyway migration or a simple partition-management function:

```sql
create table readings_2026_03 partition of readings
    for values from ('2026-03-01') to ('2026-04-01');
```

### Status Values

| Value | Meaning | Notes |
|-------|---------|-------|
| 1 | Discharging | Actively discharging sewage |
| 0 | Not discharging | Normal operation |
| -1 | Offline | Status unknown |

`RECENT_DISCHARGE` is derived at query time (status = 0 and previous status = 1 within the last hour), not stored.

### Storage Estimates

| Metric | Value |
|--------|-------|
| Rows per cycle | ~14,187 |
| Cycles per day | 96 |
| Rows per day | ~1.36M |
| Rows per month | ~41M |
| Rows per year | ~497M |
| Estimated disk per year | 20-30 GB |

## Poller Design

### Scheduling

A single coroutine loop launched at startup. Sequential by design — the next poll cannot start until the current one finishes and the delay elapses. No overlap risk.

```
on startup:
    initialise database connection pool
    loop:
        startTime = now()
        results = pollAllCompanies()       // parallel fetch of 10 APIs
        upsertSites(results)               // batch upsert to sites table
        insertReadings(results, startTime) // batch insert to readings table
        delayUntilNext15MinBoundary()
```

### API Client Reuse

The poller depends on `shared` module via `implementation(project(":shared"))`, reusing:

- All 10 water company API clients (ArcGIS, Thames Water, Welsh Water)
- Response parsing and field mapping
- OSGB-to-WGS84 coordinate conversion
- `OverflowPoint` model

The only difference from the app's usage: the poller passes a UK-wide bounding box instead of the user's viewport.

### Resilience

- **Per-company error isolation** — if one API is down, the other 9 still get polled. Failures are logged, retried next cycle.
- **Idempotent writes** — `ON CONFLICT DO NOTHING` on readings. If the process restarts mid-cycle, re-running is safe.
- **No backfill** — if the poller was down, it resumes from now. Gaps in data are accepted (the data didn't exist while it wasn't polling).
- **Graceful shutdown** — traps SIGTERM, finishes current poll cycle before exiting (systemd integration).

### Database Access

Exposed (JetBrains Kotlin SQL library) with HikariCP connection pool. Batch inserts for readings (~14K rows per cycle).

### Logging

SLF4J + Logback to stdout (captured by systemd journal). Each cycle logs:
- Companies polled successfully / failed
- Readings inserted
- Cycle duration

## REST API Design

Served by Ktor, running in the same process as the poller. All paths prefixed with `/api/v1/`.

### `GET /api/v1/overflows`

Current status for all sites within a bounding box. Replaces the app's direct API calls.

**Parameters:**
- `minLat`, `maxLat`, `minLon`, `maxLon` (required) — viewport bounding box

**Response:** Array of overflow points matching the existing `OverflowPoint` shape:

```json
[
  {
    "id": "PERMIT123",
    "latitude": 51.5074,
    "longitude": -0.1278,
    "status": "DISCHARGING",
    "watercourse": "River Thames",
    "siteName": "Example CSO",
    "statusStart": 1710500000000,
    "company": "THAMES"
  }
]
```

### `GET /api/v1/sites/{id}/history`

Historical readings and statistics for a single site.

**Parameters:**
- `days` (optional, default 30) — number of days of history

**Response:**

```json
{
  "site": {
    "id": "PERMIT123",
    "siteName": "Example CSO",
    "watercourse": "River Thames",
    "company": "THAMES",
    "latitude": 51.5074,
    "longitude": -0.1278
  },
  "stats": {
    "totalDischargeHours": 38.5,
    "eventCount": 14,
    "longestEventHours": 8.25,
    "percentDischarging": 5.3,
    "lastDischargeAt": "2026-03-14T08:30:00Z"
  },
  "timeline": [
    { "timestamp": "2026-03-14T08:00:00Z", "status": 1 },
    { "timestamp": "2026-03-14T08:15:00Z", "status": 1 },
    { "timestamp": "2026-03-14T08:30:00Z", "status": 0 }
  ]
}
```

Stats are computed server-side via SQL aggregation. The timeline contains raw readings for charting.

### `GET /api/v1/sites/worst-offenders`

Top 20 sites ranked by total discharge hours within a radius and time period.

**Parameters:**
- `lat`, `lon` (required) — centre point
- `radius` (optional, default 1) — radius in miles
- `days` (optional, default 30) — time period

**Response:**

```json
[
  {
    "site": {
      "id": "PERMIT456",
      "siteName": "Worst CSO",
      "watercourse": "River Lea",
      "company": "THAMES",
      "latitude": 51.55,
      "longitude": -0.05
    },
    "stats": {
      "totalDischargeHours": 142.0,
      "eventCount": 23,
      "longestEventHours": 18.5,
      "percentDischarging": 19.7,
      "lastDischargeAt": "2026-03-15T02:15:00Z"
    }
  }
]
```

Spatial filtering uses a bounding box pre-filter in SQL followed by Haversine distance check. PostGIS is not needed for this scale.

### Shared Concerns

- **No authentication** — the underlying data is public
- **JSON serialisation** — kotlinx-serialization
- **No rate limiting initially** — can add if needed

## App Integration

### Hybrid Fetching

A new `HybridOverflowRepository` wraps the existing `OverflowRepository`:

```
try:
    response = httpClient.get("/api/v1/overflows?minLat=...&maxLat=...&...")
    return response.map { it.toOverflowPoint() }
catch (timeout / network error):
    return existingRepository.allOverflows(bounds)
```

- Backend request timeout: 3-5 seconds (fast fallback)
- Both paths produce `List<OverflowPoint>` — the rest of the app is unaware of the source
- Config flag `useBackend: Boolean` to disable backend path during development

### Historical Data UI

**Per-site history (tap a pin):**

1. Existing info card remains unchanged (site name, watercourse, current status, status start time)
2. New "History" section below the existing card content
3. Stats row: total discharge hours | event count | "last discharged X ago"
4. Timeline chart: horizontal bar/gantt showing discharge periods over 30 days — red bars for discharge, gaps for clean periods

**Worst offenders screen:**

- Accessible from the main map screen (list icon in toolbar)
- Ranked list: site name, watercourse, total discharge hours, event count
- Tap an entry to navigate to it on the map and open its history

### Graceful Degradation

| Backend state | Current data | Historical data |
|---------------|-------------|-----------------|
| Reachable | From backend (single fast request) | Available (history card, worst offenders) |
| Unreachable | Fallback to direct API calls | "Historical data unavailable" message |
| No network | Cached pins from last session | Unavailable |

The core map experience is never blocked by backend availability. History fetches happen after the info card is opened, not on map load.

## Technology Choices

| Component | Technology | Rationale |
|-----------|-----------|-----------|
| Poller runtime | Kotlin/JVM | Reuses shared module API clients |
| HTTP server | Ktor | Already used in shared module, lightweight |
| Database | Postgres | Runs on Pi and hosted, relational, mature |
| Migrations | Flyway | Standard, works with Gradle |
| DB access | Exposed (JetBrains) | Idiomatic Kotlin, lightweight |
| Connection pool | HikariCP | Industry standard, low overhead |
| Scheduling | Coroutine loop | Simple, no overlap, no framework overhead |
| Logging | SLF4J + Logback | Standard JVM logging |
| Serialisation | kotlinx-serialization | Already used throughout the project |

## Deployment (Raspberry Pi)

- Single JAR built by Gradle (`./gradlew :poller:shadowJar` or similar)
- Managed by systemd unit file (auto-restart on failure, log to journal)
- Postgres installed via `apt install postgresql`
- Data directory on SSD (not SD card) for write endurance
- `shared_buffers = 128MB` for a 4GB Pi

## Future Considerations

- **Migration to hosted:** Same JAR on a VPS, swap Postgres connection string to managed instance
- **PostGIS:** Add if spatial queries become more complex (e.g. downstream impact analysis)
- **Partition management:** Automate monthly partition creation (pg_partman or a simple scheduled SQL function)
- **Data retention:** Drop/archive partitions older than N years if storage becomes a concern
- **Push notifications (Phase B):** The poller already detects status changes — adding FCM/APNs dispatch is a natural extension
