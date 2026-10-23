# Amperly

### Smart reservation and management platform for existing EV chargers

Amperly is an Android-first hyperlocal EV charger-management, route-planning and reservation
platform written in Java. Drivers plan a journey around compatible chargers that already exist,
reserve a specific port, check in with a QR code, talk to the Host through masked channels, and
pay only for **verified meter readings** — with a glanceable version of the same trip on the car
display through **Android Auto**.

The app is wrapped in the **pixel/volt shell**:

- pixel-art **Amperly** logo and tagline in the top app bar;
- a collapsible **vehicle popup** with a shaded 3D model of the signed-in driver's car, floating
  and rotating on a perspective grid (the body style follows the make and model the driver saved
  in their vehicle profile);
- **role chips** — Consumer, Host, and Admin only when the account has admin authority. Switching
  changes the active capacity instantly, never the account;
- **current-user summary** under the chips;
- a **pixel bottom navigation** rebuilt for each capacity;
- **profile badge** opening Account, Vehicle Profile, Safety and Sign Out.

Every screen below the shell is a production screen: it reads and writes through
`AmperlyRepository`, and Supabase is the source of truth.

---

## 1. What this build actually is

| Concern | How it works |
|---|---|
| Persistence | Room database (`AmperlyDatabase`, v3, 23 entities + 23 DAOs) as the read cache |
| Data access | One hub, `AmperlyRepository` (all DAO access, off-main-thread helpers) |
| Data source | **Supabase is the only source of truth.** Stations, vehicles, reservations and readings are rows the signed-in account is authorised to read through Row Level Security. When the account has no rows, the screens say so instead of inventing content |
| Maps & navigation | **Google Maps SDK for Android**, **Google Directions API**, **Place autocomplete**. A missing key shows an actionable setup notice instead of a blank map |
| Payments | `PaymentManager` sandbox: authorize → capture/refund/release, idempotency keys, offline failure path |
| Charger telemetry | None. Availability is schedule-, reservation- and host-update-based, labelled with a freshness timestamp |
| Meter data | `MeterSimulator` / manual entry, always labelled **Demo meter** or **manually entered** |
| Cloud | Supabase/Postgres backend (`supabase/`). Room is only the read cache; `SyncEngine` writes every save straight to Supabase and pulls rows changed since a per-table cursor |
| Car | Android for Cars App Library (`androidx.car.app`): route overview, navigation, charger list, details, battery update, reservation status |

There is no fabricated offline identity and no client-side seeding: without a configured
Supabase project the app shows the sign-in screen and states the problem.

---

## 2. Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│              MainActivity — pixel shell + role switch                │
│   app bar │ vehicle popup │ role chips │ fragment host │ bottom nav  │
└───────────────┬──────────────────────────────────────────────────────┘
                │  fragments call engines directly (no ViewModels)
                ▼
┌──────────────────────────────────────────────────────────────────────┐
│                              Engines                                 │
│ Routing │ SoC │ Billing │ Availability │ Meter │ Booking │ Analytics  │
│                     Enforcement policy                                │
└───────────────┬──────────────────────────────────────────────────────┘
                │                        ┌───────────────────────────┐
                │                        │  NavigationProvider       │
                │                        │  GoogleNavigationProvider │
                │                        │  (Directions + Maps SDK)  │
                │                        └───────────────────────────┘
┌───────────────▼──────────────────────────────────────────────────────┐
│                         AmperlyRepository                            │
│      stations · ports · schedules · reservations · sessions ·         │
│      meters · invoices · payments · reports · disputes · chat ·       │
│      safety · route plans · auto sessions   (Room, background IO)     │
└───────────────┬──────────────────────────────────────────────────────┘
                │
     ┌──────────┴───────────┐
     ▼                      ▼
┌──────────────┐   ┌──────────────────────────────────────────┐
│ AppStateStore│   │            Android Auto layer            │
│ shared trip  │◄──│ CarBridge · AmperlyCarSession · screens  │
│ + SoC state  │   │ RouteOverview · Navigation · ChargerList ·│
└──────────────┘   │ Details · SoCUpdate · ReservationStatus   │
                   └──────────────────────────────────────────┘
```

The phone and the car read the **same** `AppStateStore`, so a reservation or battery update made
on one surface is visible on the other.

---

## 3. Engines and the guarantees they hold

| Engine | Responsibility and hard rules |
|---|---|
| `RoutePlannerEngine` | Corridor filtering, connector compatibility, detour cost, greedy multi-stop plan, backup stops before/after, recorded assumptions, confidence. Uses Google's traffic-aware drive time when available and a labelled estimate otherwise. **Never routes beyond the safe range** |
| `SoCEstimatorEngine` | Estimates a starting SoC from the vehicle profile and treats the driver's dashboard value as authoritative; recalculates the remaining trip and offers alternatives instead of a fake route |
| `BillingEngine` | Blocks paid billing entirely at stations without a verified meter. Itemises readings, verified kWh, energy charge, service fee, gateway fee and total. Zero energy ⇒ zero charge |
| `AvailabilityEngine` | Builds bookable slots from schedules, port status, maintenance blocks and existing reservations; detects conflicts |
| `MeterSimulator` | Cumulative demo readings, session progression, offline state, invalid and implausible readings |
| `ReservationEngine` | Double-booking prevention, port-level connector compatibility, paused/unverified station rejection, price preview, booking code and QR payload, cancellation policy |
| `HostAnalyticsEngine` | Utilisation, energy delivered, revenue, repeat consumers, cancellation rate, disputes and refunds |
| `EnforcementPolicyEngine` | Evidence-based escalation: warning → fine → suspension → ban. An unverified accusation results in `NONE` |

`PaymentManager` (sandbox authorize/capture/refund/release + idempotency) and `ChatManager`
(number masking, text sanitising, off-platform payment detection, 7-day thread expiry) sit
alongside the engines.

### Vehicle model pipeline

The 3D car uses a three-tier native pipeline: curated JSON profiles (Tier 1), eight body
skeletons deformed by real specs (Tier 2), and a specs resolver with owned database → optional
third-party API (direct Vehicle Databases YMMT lookup or a normalised proxy) → name-based
estimator (Tier 3). Every result is cached, and no vehicle fails to
render. See [docs/VEHICLE_MODEL_PIPELINE.md](docs/VEHICLE_MODEL_PIPELINE.md) for the schema,
template values, debug validation gallery, bulk generator, API configuration and 3D asset
licensing notes.

---

## 4. Honesty rules baked into the product

1. **Meter-first billing.** No verified meter ⇒ no automatic energy charge; Level 3 stations are
   discovery only.
2. **Estimates are labelled.** Route energy, SoC and arrival percentages are always estimates.
3. **The consumer's battery value wins** and the remaining route is recalculated from it.
4. **The original plan is never silently rewritten** — original estimate → actual update →
   revised recommendation, with an audit record.
5. **Simulated data says so.**
6. **No emergency-service claim.** SOS offers trusted-contact live-location sharing and local
   emergency numbers.
7. **Free-text chat is phone-only.**
8. **Freshness is visible** wherever availability is presented.
9. **The start is the real position** — no pilot coordinate is used as the user's location.
10. **No external handoff for in-app navigation.**

---

## 5. Package structure (`com.amperly.app`)

```text
com.amperly.app/
├── MainActivity.java        pixel shell: brand bar, vehicle popup, roles, nav
├── model/                   CarModel (body-style presets for the 3D model), Role
├── ui/                      PixelLogoView, Car3DView, BottomNavView, Ux
│   ├── content/             NavItem, NavigationConfig (role → real destinations)
│   ├── adapter/             9 RecyclerView adapters
│   └── dialog/              ReportDialog, MeterEvidenceDialog
├── core/
│   ├── common/              AppConstants, Formatters, GeoUtils, GoogleConfig, ValidationUtils
│   ├── location/            LocationProvider
│   ├── map/                 MapPanel, MapContainerView, StationMarkerModel, StationVisibility
│   ├── places/              PlacesAutocomplete, GoogleGeocoder
│   ├── routing/             GoogleDirectionsClient, DirectionsRoute, Polyline
│   ├── nav/                 Navigator, NavigationProvider, GoogleNavigationProvider,
│   │                        NavigationState, RouteProgress
│   ├── security/            AuthManager, RoleGuard
│   └── state/               AppStateStore, SocStore
├── data/
│   ├── local/               AmperlyDatabase (v3), 23 entities + 23 DAOs
│   ├── remote/              SupabaseClient/Rest/Config, SupabaseAuth, SyncEngine, SyncTables
│   └── repository/          AmperlyRepository
├── domain/enums/            ChargerStatus, ConnectorType, MeterVerificationLevel,
│                            ReservationStatus, UserRole
├── engine/                  admin, analytics, availability, billing, booking, meter,
│                            routing, soc
├── feature/
│   ├── consumer/            ChargerDiscovery, ChargerDetails, InAppNavigation, RoutePlanner,
│   │                        VehicleProfile, Reservation, BookingQr, ActiveSession, Receipt,
│   │                        SafetySOS, HostChat
│   ├── host/                HostDashboard, RegisterStation
│   └── admin/               AdminDashboard
├── car/                     AmperlyCarAppService, AmperlyCarSession, CarBridge,
│                            CarScreenFactory, screen/*
├── payment/                 PaymentManager
└── communication/           ChatManager
```

---

## 6. Build & run

```bash
# Unit tests
./gradlew :app:testDebugUnitTest

# Debug APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleDebug
```

Requires JDK 17 and Android SDK Platform 34. On Windows, if `JAVA_HOME` is not set globally:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:testDebugUnitTest --console=plain
```

### 6.1 Backend and map keys

Keys are read from `local.properties` (gitignored) or environment variables of the same name:

```properties
SUPABASE_URL=https://<project>.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_...
GOOGLE_MAPS_API_KEY=...
```

`app/build.gradle` injects the Supabase pair and the Google key as BuildConfig fields and as the
`com.google.android.geo.API_KEY` manifest placeholder. A missing Google key disables the map,
Place search and routing behind an actionable notice. A missing Supabase pair means there is no
account to authorise against, so the sign-in screen says so. Both halves of the Supabase pair are
required; a URL without a key stays off. A `sb_secret_`/service key fails the build, because it
would bypass Row Level Security.

Create the schema and seed the pilot network with the SQL in [`supabase/`](supabase/README.md).
In the Google Cloud project enable **Maps SDK for Android**, **Places API**, **Directions API**
and **Geocoding API**, and restrict the key to `com.amperly.app` plus the signing SHA-1.

Without a Supabase project the app deliberately has no way in — there is no demo identity and no
local seed, because every screen reads through Row Level Security.

---

## 7. Tests

```bash
./gradlew :app:testDebugUnitTest              # 113 tests, no device
./gradlew :app:connectedDebugAndroidTest      # needs an emulator or device
```

Covering engines, billing and meter rules, booking and availability, enforcement policy, payment
authorization/idempotency, chat masking, role boundaries, polyline decoding, Google Directions
parsing, navigation lifecycle, route progress, station visibility and marker honesty, the 3D
vehicle mesh and its make/model mapping, plus the `public.users` trigger and settlement SQL
contracts. The on-device tests draw the vehicle popup and the 3D model on the real renderer.

---

## 8. Technology stack

| Layer | Technology |
|---|---|
| Language / IDE | Java 17 source level, Android Studio, Gradle 8.7 + AGP 8.5.2 |
| Compile/target SDK | 34 (minSdk 24) |
| UI | Material 3 components, `viewBinding`, RecyclerView, custom pixel views + `Widget.Amperly.*` styles |
| Car UI | Android for Cars App Library (`androidx.car.app:app:1.4.0`) |
| Persistence | Room (`room-runtime 2.6.1`) — local read cache only |
| Backend | Supabase — Postgres with Row Level Security, PostgREST and GoTrue over Retrofit/OkHttp |
| Maps | Google Maps SDK for Android (`play-services-maps`) |
| Places / Location | Google Places SDK + Geocoding API, `play-services-location` |
| Routing | Google Directions API behind `NavigationProvider` |
| QR codes | ZXing `core` (bitmap generated in-app) |
| Payments | In-repo sandbox `PaymentManager` (no card data stored) |
| Tests | JUnit 4 (no device required) |

---

## 9. Out of scope

No new charger installation; no remote charger control; no OCPP; no private-home chargers; no
bidding for slots; no paid billing without a verified meter; no claim that estimates are
measurements; no guarantee of reachability; no emergency-service guarantee. The licence-gated
Google **Navigation SDK** (spoken guidance, `NavigationViewForAuto`) is modelled behind
`NavigationProvider` but not enabled — see `GOOGLE_NAVIGATION_MIGRATION.md`.

---

## 10. License & attribution

Amperly is developed as an open EV charger management platform. Map data and routing © Google.
