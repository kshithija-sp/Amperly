# Codebase audit — OpenStreetMap/OSRM call sites and migration status

Every place the old map/routing stack was produced, referenced or consumed, and what happened to it.
Status values: **Removed**, **Replaced**, **Migrated**, **Added**.

---

## 1. Provider classes (production code)

| Call site | Previous implementation | Status | New implementation |
|---|---|---|---|
| `core/map/OsmTileMapView.java` | Custom `View` drawing OSM raster tiles | **Removed** | `core/map/MapPanel` (Google Maps SDK `SupportMapFragment`) |
| `core/map/MapHelper.java` | Attached the OSM view and plotted pins/routes | **Removed** | `core/map/MapPanel.attach/plotStations/plotRoute/plotPicker` |
| `core/map/MapTiles.java` | Tile URL template + disk cache (used `BuildConfig.OSM_TILE_URL`, `OSM_USER_AGENT`) | **Removed** | Google Maps SDK loads tiles |
| `core/map/Mercator.java` | Web Mercator projection for the tile view | **Removed** | Google Maps SDK owns projection |
| `core/routing/OsrmRouter.java` | OSRM route fetch + parse (used `BuildConfig.OSRM_BASE_URL`) | **Removed** | `core/routing/GoogleDirectionsClient` |
| `core/common/PilotPlaces.java` | Hard-coded town→coordinate list with pilot-centre fallback | **Removed** | `core/places/PlacesAutocomplete` + free-text destination; no pilot fallback |

Verified before deletion: `grep` for these symbols across `app/src` returned **only** the two unit
tests below, which were replaced.

## 2. Configuration and manifest

| Call site | Previous | Status | New |
|---|---|---|---|
| `app/build.gradle.kts` | `OSM_TILE_URL`, `OSM_USER_AGENT`, `OSRM_BASE_URL` read from `local.properties` into BuildConfig | **Replaced** | `GOOGLE_MAPS_API_KEY` → `BuildConfig.GOOGLE_MAPS_API_KEY`, `GOOGLE_MAPS_ENABLED`, and the `googleMapsApiKey` manifest placeholder |
| `gitignore`d `local.properties` | OSM/OSRM server URLs | **Replaced** | `GOOGLE_MAPS_API_KEY` (still never committed) |
| `app/src/main/AndroidManifest.xml` | Comment stating "no maps API key to declare" | **Replaced** | `com.google.android.geo.API_KEY` meta-data from the placeholder |
| `gradle/libs.versions.toml` | `play-services-maps` declared but unused | **Wired** | `libs.playservicesmaps` + new `libs.places` (`places:3.3.0`) |

## 3. Feature screens

| Call site | Previous | Status | New |
|---|---|---|---|
| `feature/consumer/RoutePlannerFragment` | Typed origin (`"Mysore"` default), manual distance field, `OsrmRouter.route`, `MapHelper.plotRoute` | **Migrated** | Current-location origin, Place-autocomplete destination, battery % only, `NavigationProvider.calculateRoute`, Google directions list, `MapPanel.plotRoute`, "Start in-app navigation" |
| `feature/consumer/ChargerDiscoveryFragment` | `MapHelper.attach/plotStations`, `OsmTileMapView`, distances from `AppConstants.PILOT_CENTER_*`, `Navigator.navigateTo` (external) | **Migrated** | `MapPanel.attach/plotStations`, Google markers, distances from the real position, in-app navigation |
| `feature/consumer/ChargerDetailsFragment` | `OsrmRouter.route/estimate`, `MapHelper.plotRoadRoute`, `OsmTileMapView`, external navigate | **Migrated** | `GoogleDirectionsClient.route`, `MapPanel.plotRoute`, in-app navigation |
| `feature/host/RegisterStationFragment` | `MapHelper.attachPicker/moveTo`, coordinates defaulting to `PILOT_CENTER_*` | **Migrated** | `MapPanel.plotPicker`, map tap → `GoogleGeocoder` reverse geocode, Place-autocomplete address, no pilot default (location required) |
| `feature/consumer/InAppNavigationFragment` | did not exist | **Added** | In-app Google-map navigation with turn card, remaining distance/ETA, battery override, stop |

## 4. Android Auto

| Call site | Previous | Status | New |
|---|---|---|---|
| `car/CarScreenFactory` | screens: RouteOverview, ChargerList, Details, SoCUpdate, ReservationStatus | **Extended** | added `NAVIGATION` → `NavigationCarScreen` |
| `car/screen/NavigationCarScreen` | did not exist | **Added** | Driver-safe `NavigationTemplate` + `RoutingInfo`/`TravelEstimate`, battery + stop actions |
| `car/screen/RouteOverviewCarScreen` | no navigation entry | **Migrated** | adds a "Start navigation" row that pushes the navigation screen |
| `car/AmperlyCarSession` | — | **Unchanged behaviour** | still records connect/disconnect; routes to the overview screen |

`Session.onAutoDriveEnabled()` does **not** exist in `androidx.car.app:app:1.4.0` (checked against the
resolved artifact), so the auto-drive simulation hook could not be overridden on this library
version. This is recorded as a limitation rather than faked.

## 5. User-facing wording

| Location | Previous wording | Status | New |
|---|---|---|---|
| `res/values/strings.xml` | "OSRM", "OpenStreetMap", "straight-line" as the normal path; "Distance km (blank = auto)" | **Replaced** | Google directions heading/attribution, setup notice, current-location/unavailable states; the straight-line string now appears **only** on the labelled fallback |
| `README.md` | §6 "Maps — OpenStreetMap" and "Routing — OSRM" | **Replaced** | §6 Google Maps Platform setup, key restrictions, API list, Navigation SDK note |
| `AndroidManifest.xml` OSM comment | — | **Replaced** | Google key comment |

## 6. Tests

| Call site | Previous | Status | New |
|---|---|---|---|
| `test/MercatorTest.java` | Web Mercator projection | **Removed** | projection is the SDK's now |
| `test/OsrmRouterTest.java` | OSRM parse + walking-estimate rules | **Removed** | `PolylineTest`, `GoogleDirectionsClientTest` |
| `test/RouteProgressTest.java` | did not exist | **Added** | fraction-along, remaining distance/time, next maneuver |
| `test/GoogleNavigationProviderTest.java` | did not exist | **Added** | navigation session lifecycle on the production provider |
| `test/StationVisibilityTest.java` | did not exist | **Added** | approved vs pending/rejected/paused visibility |
| `test/StationMarkerModelTest.java` | did not exist | **Added** | marker styling, meter honesty, no contact-detail leak |
| `test/RoutePlannerGoogleTimeTest.java` | did not exist | **Added** | Google's measured drive time overrides the estimate |

## 7. Out-of-scope items from the brief that remain, and why

| Item | Status | Reason |
|---|---|---|
| Google Navigation SDK (spoken guidance, `NavigationViewForAuto`) | **Not enabled** | Requires a Google entitlement/licence per Cloud project; not configurable from the repository. Modelled behind `NavigationProvider` + audio-guidance flags |
| Android Auto navigation approval with Google | **Manual external step** | Google's review process; cannot be performed from source |
| Live verification of Place autocomplete / Directions / map tiles | **Not run in CI** | Needs the real key, billing and a device/emulator; the parsers and rules are unit tested instead |
| Instrumentation / connected Android Auto tests | **Not run** | No emulator or head unit in this environment |
