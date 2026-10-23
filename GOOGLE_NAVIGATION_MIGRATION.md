# Google Maps / Directions navigation migration

This document explains what replaced the OpenStreetMap/OSRM stack, why, and exactly where the
Google integration lives. Every claim here is checked against the code that ships in this repository.

---

## 1. Before and after

| Concern | Previous implementation | New implementation | Why the change was required |
|---|---|---|---|
| Base map | `core/map/OsmTileMapView` — OpenStreetMap raster tiles fetched with the app's own OkHttp client and drawn on a `Canvas` | `core/map/MapPanel` — **Google Maps SDK for Android** `SupportMapFragment` | The product needs Google map data, Google quality and Google terms; a hand-drawn tile renderer cannot carry traffic, POIs or street-level detail |
| Projection / tile maths | `core/map/Mercator`, `core/map/MapTiles` | Removed — the SDK owns projection and tile loading | Dead code once the SDK renders |
| Charger markers | `MapHelper.plotStations` drawing `Pin` objects on the canvas | `MapPanel.plotStations` adding Google `Marker`s | Google markers give info windows, tap handling, clustering-ready behaviour |
| Marker visibility | inline in `MapHelper` | `core/map/StationVisibility` (+ `StationMarkerModel` for style/text) | Make the "approved, non-banned, non-paused, valid coordinates" rule testable on the JVM |
| Road routing | `core/routing/OsrmRouter` against the public OSRM demo server | `core/routing/GoogleDirectionsClient` against the **Google Directions API** | Real road data, traffic-aware duration and authoritative turn-by-turn steps |
| Route geometry | OSRM GeoJSON `[lng,lat]` | Google **encoded polyline** decoded by `core/routing/Polyline` | The Directions API returns polylines, not GeoJSON |
| Destination entry | `PilotPlaces` — a hard-coded list of ~12 Indian towns, falling back to a pilot coordinate | **Google Place autocomplete** (`core/places/PlacesAutocomplete`) with a free-text fallback via Directions | A driver must be able to search any real shop, hotel or landmark; the pilot list was a prototype crutch |
| Current location | Typed origin field defaulting to `"Mysore"`; `PilotPlaces.resolveOrPilot` silently substituted the pilot centre | The **device's real current position** via `LocationProvider.requestCurrentLocation`; when unavailable, the screen says so and offers an explicit **map-pin origin** | Non-negotiable product rule: never silently use a pilot coordinate |
| Distance input | Manual "Distance km (blank = auto)" field | Removed — Google measures the distance and the drive time | The consumer must not have to know the trip distance |
| Navigation screen | `Navigator.navigateTo` handing the trip to an external maps app via a `geo:` intent | `feature/consumer/InAppNavigationFragment` — in-app navigation on the Google map, driven by `NavigationProvider` | Navigation must stay inside Amperly |
| Navigation abstraction | none (routing was called directly) | `core/nav/NavigationProvider` + `GoogleNavigationProvider` + `NavigationState` + `RouteProgress` | One seam for route calculation, session lifecycle, rerouting, waypoints and the Android Auto bridge |
| Android Auto | `RouteOverviewCarScreen` + list templates only | Added `car/screen/NavigationCarScreen` using the car app library **navigation template** | A navigation-category `CarAppService` must project a driver-safe navigation view |
| Reverse geocoding | none | `core/places/GoogleGeocoder` (Geocoding API) for host map taps | A host must confirm a readable address for the exact spot they picked |
| Configuration | `OSM_TILE_URL`, `OSM_USER_AGENT`, `OSRM_BASE_URL` (no keys) | `GOOGLE_MAPS_API_KEY` injected via BuildConfig + manifest placeholder | Google services need a restricted key; it must never be hard-coded |

---

## 2. The `NavigationProvider` seam

```java
public interface NavigationProvider {
    void calculateRoute(RouteRequest request, RouteCallback callback);
    void startNavigation(NavigationRequest request, NavigationCallback callback);
    void recalculate(RouteRequest request, RouteCallback callback);
    void stopNavigation();
    NavigationState getCurrentNavigationState();
}
```

`GoogleNavigationProvider` implements it:

```
GoogleNavigationProvider
    ├── route calculation        → Google Directions API (GoogleDirectionsClient)
    ├── navigation session       → sessionId + NavigationState shared with the UI and Android Auto
    ├── reroute from new SOC     → recalculate() after a battery override
    ├── charger waypoints        → RouteRequest carries waypoints for corridor stops
    ├── voice guidance controls  → AUDIO_VOICE_ALERTS_AND_GUIDANCE | AUDIO_BLUETOOTH flags
    └── Android Auto bridge      → the same NavigationState is projected by NavigationCarScreen
```

Everything that needs a road route — the route planner, the charger details screen and in-app
navigation — goes through this interface, so the vendor behind it can change in one place.

---

## 3. The route planner flow (the requested "route-based checking")

```
Device current location (LocationProvider, real fix, no pilot fallback)
        ↓
Destination typed → Google Place autocomplete → Place ID + address + coordinates
        ↓
NavigationProvider.calculateRoute(...)
        ↓
GoogleDirectionsClient  →  distance, traffic-aware duration, encoded polyline, ordered steps
        ↓
RoutePlannerEngine.planRoute(..., measuredDriveMinutes = Google's time)
        ↓
Corridor chargers, connector compatibility, detour, arrival-SoC safety, backups, confidence
        ↓
MapPanel.plotRoute(...) draws the Google road line + Amperly charger markers
        ↓
Directions list rendered from Google's turn-by-turn steps
        ↓
"Start in-app navigation" → InAppNavigationFragment
```

The battery percentage is entered by the consumer and becomes **authoritative** for the remaining
trip; correcting it re-plans the corridor and removes chargers that are no longer safely reachable.

---

## 4. Configuration

One key, injected — never hard-coded:

```properties
# local.properties (gitignored)
GOOGLE_MAPS_API_KEY=...
```

`app/build.gradle.kts` reads it and:
- exposes `BuildConfig.GOOGLE_MAPS_API_KEY` and `BuildConfig.GOOGLE_MAPS_ENABLED`, and
- fills the `googleMapsApiKey` manifest placeholder for `com.google.android.geo.API_KEY`.

Required APIs on the key: **Maps SDK for Android**, **Places API**, **Directions API**, **Geocoding
API**. Restrict the key to package `com.amperly.app` plus the debug/release signing SHA-1 and to
those four APIs. `core/common/GoogleConfig` is the single runtime check; screens call it and show a
setup notice when it is false.

---

## 5. Data changes

`RoutePlan` gained two nullable columns (no constructor change, so existing callers and the seeder
keep working; Room's `fallbackToDestructiveMigration()` resets local demo data on the schema bump):

- `routeProvider` — which provider calculated the route, e.g.
  `"Google Maps Platform (Directions + Maps SDK)"`.
- `googlePlaceId` — the chosen destination's Place ID, when the consumer picked one.

The existing `originLat/originLng/destinationLat/destinationLng`, `totalDistanceKm` and
`driveMinutes` columns now carry Google's snapped coordinates and measured drive time.

---

## 6. What could not be enabled from this repository

The **Google Navigation SDK for Android** (spoken voice guidance, `NavigationViewForAuto`, the
licensed turn-by-turn experience) requires a Google **entitlement/licence** that is granted per Cloud
project and cannot be configured from source. Amperly therefore:

- routes and draws with **Google Maps SDK + Directions API**, which need no special entitlement, and
- models the SDK's audio-guidance flags and navigation state behind `NavigationProvider`, so the
  licensed SDK can be dropped in behind the same interface without touching the screens.

Specifically **not** delivered here, and not claimed as working: spoken voice guidance,
`NavigationViewForAuto`, and the Google Android Auto navigation approval. `NavigationCarScreen`
projects a driver-safe navigation template today; it is written to be replaced by
`NavigationViewForAuto` when the entitlement exists.

---

## 7. Files added

```
core/common/GoogleConfig.java
core/map/MapPanel.java
core/map/MapContainerView.java
core/map/StationMarkerModel.java
core/map/StationVisibility.java
core/places/PlacesAutocomplete.java
core/places/GoogleGeocoder.java
core/routing/GoogleDirectionsClient.java
core/routing/DirectionsRoute.java
core/routing/Polyline.java
core/nav/NavigationProvider.java
core/nav/GoogleNavigationProvider.java
core/nav/NavigationState.java
core/nav/RouteProgress.java
feature/consumer/InAppNavigationFragment.java
car/screen/NavigationCarScreen.java
```

## 8. Files removed

```
core/map/OsmTileMapView.java
core/map/MapHelper.java
core/map/MapTiles.java
core/map/Mercator.java
core/routing/OsrmRouter.java
core/common/PilotPlaces.java
test/MercatorTest.java
test/OsrmRouterTest.java
```

No class was deleted without first checking every caller: the only remaining references were the two
tests, which were replaced by `PolylineTest` and `GoogleDirectionsClientTest`.
