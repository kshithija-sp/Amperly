# Vehicle model pipeline

This document describes the three-tier vehicle system as it is implemented in this native
Android app, together with the data schemas, the reference skeleton values, the validation
gallery, the bulk tooling and the licensing notes for future 3D assets.

The app renders with a procedural Canvas engine (`Car3DView`, `CarMesh`, `CarModelFitter`), not
with three.js or a runtime glTF loader. The brief's GLB tiers are therefore adapted as follows:

| Tier | Brief | Implementation here |
| --- | --- | --- |
| 1 | Curated `assets/models/{make}-{model}.glb` | Curated JSON profile in `assets/vehicle_profiles.json` (dimensions, proportions, paint, wheel face, roof rails, accent, badge, charge port). No GLB runtime. |
| 2 | One template `.glb` per body type, deformed by specs | `BodyTemplate` reference skeletons → `CarModelFitter` → `CarMesh`. Body, glass, wheels, lamps, charge flap and optional roof rails are rebuilt at their real sizes instead of being uniformly stretched. |
| 3 | Specs resolver: DB → API → estimator, cached | `SpecResolver`: curated profile overlay, owned `vehicle_specs.json`, optional `RemoteSpecProvider`, then `SpecEstimator`; results cached in memory and in `SharedPreferences`. |

This keeps the one promise that matters: **no vehicle fails to render**. A car with no owned
specs resolves to a low-confidence estimate and the UI already offers the eight-silhouette picker
(`SpecResolver.confirmBodyType`). No network or asset download is required.

## Module map

| Module | File | Responsibility |
| --- | --- | --- |
| Spec resolver | `vehicle/SpecResolver.java` | Lookup order, curated overlay, memory/disk cache, body-type confirmation |
| Owned database | `vehicle/VehicleSpecDatabase.java`, `assets/vehicle_specs.json` | Primary spec source, HIGH confidence |
| Curated profiles | `vehicle/CuratedProfile.java`, `vehicle/CuratedProfileStore.java`, `assets/vehicle_profiles.json` | Tier 1 curation |
| Third-party API | `vehicle/VehicleSpecProvider.java`, `vehicle/VehicleDatabasesProvider.java`, `vehicle/RemoteSpecProvider.java` | Tier 3(b): direct Vehicle Databases YMMT lookup, or a normalised proxy; both inert without a key |
| Estimator | `vehicle/SpecEstimator.java` | Tier 3(c) name/segment inference, LOW confidence |
| Template fitter | `vehicle/BodyTemplate.java`, `vehicle/CarModelFitter.java`, `vehicle/VehicleGeometry.java` | `fitTemplateToSpecs` |
| Mesh builder | `ui/CarMesh.java` | Body, cabin/glass, wheels, lamps, charge flap, roof rails |
| Scene | `ui/Car3DView.java` | Turntable, shadow, glow, lighting, orbit/zoom, charge pulse, badge, dimension overlay |
| Cache codec | `vehicle/VehicleSpecsJson.java` | Round-trips specs and curation through the disk cache |
| Validation gallery | `feature/consumer/VehicleGalleryFragment.java`, `VehicleGalleryAdapter.java` | Debug-only twelve-car grid |
| Bulk tool | `app/src/test/java/com/amperly/app/vehicle/SpecBulkGenerator.java` | One-time spec generation + review sheet |

## Resolver order and cache

`SpecResolver.resolve(make, model, variant, year)` (the three-argument overload passes year 0):

1. Memory cache, then disk cache (key = normalised `make|model`, plus `|year` when a model year
   is supplied; variants share a silhouette within that year).
2. `VehicleSpecDatabase.find`: exact make+model, then model-only, then a contains match.
3. `VehicleDatabasesProvider.lookup` when `VEHICLE_DATABASES_API_KEY` is configured. Otherwise
   `RemoteSpecProvider.lookup` when both `VEHICLE_SPECS_API_URL` and `VEHICLE_SPECS_API_KEY` are
   configured. Either adapter returns `null` on any failure.
4. `SpecEstimator.estimate` (always succeeds, LOW confidence).
5. If a curated profile exists for make+model, it is applied last:
   - supplied dimensions/tyre/charge port win and set `source = "curated"`, `confidence = HIGH`;
   - visual-only profiles leave the resolved numbers, source and confidence untouched and simply
     attach the profile to the model.
6. The result, including the curated profile, is cached in memory and on disk.

`confirmBodyType` promotes a driver-confirmed silhouette to MEDIUM and copies the curated profile
so the badge/paint survive the correction.

## Data schema

### Owned database: `app/src/main/assets/vehicle_specs.json`

```json
{
  "schemaVersion": 1,
  "units": "millimetres",
  "vehicles": [
    {
      "make": "Tata",
      "model": "Nexon EV",
      "variant": "Empowered+",
      "bodyType": "COMPACT_SUV",
      "lengthMm": 3994,
      "widthMm": 1811,
      "heightMm": 1616,
      "wheelbaseMm": 2498,
      "groundClearanceMm": 190,
      "tyre": { "widthMm": 215, "aspectPercent": 60, "rimDiameterIn": 16 },
      "chargePort": "FRONT_CENTRE",
      "colours": ["#E9EFE3", "#1B211D", "#7A9E9F"],
      "source": "database",
      "confidence": "HIGH"
    }
  ]
}
```

- `bodyType`: `HATCHBACK`, `SEDAN`, `COMPACT_SUV`, `SUV`, `MPV`, `COUPE`, `PICKUP`, `MICROCAR`.
- `chargePort`: `FRONT_LEFT`, `FRONT_CENTRE`, `FRONT_RIGHT`, `REAR_LEFT`, `REAR_CENTRE`,
  `REAR_RIGHT`.
- `confidence` is optional and defaults to `HIGH` for owned entries. Generated proposals carry
  `LOW` so they stay visibly unverified until reviewed.
- `colours` is a paint palette in preference order; the first parseable hex value becomes the body
  paint.

### Curated profile: `app/src/main/assets/vehicle_profiles.json`

```json
{
  "schemaVersion": 1,
  "profiles": [
    {
      "make": "Tata",
      "model": "Nexon EV",
      "badge": "NEXON EV",
      "wheelStyle": "SPOKE",
      "roofRails": true,
      "accentColor": "#C6F94C",
      "proportions": { "beltlineRatio": 0.64, "archGap": 0.19 }
    }
  ]
}
```

Every field except `make`/`model` is optional.

| Field | Effect |
| --- | --- |
| `bodyType` | Overrides the resolved silhouette |
| `lengthMm`, `widthMm`, `heightMm`, `wheelbaseMm`, `groundClearanceMm` | Override the resolved numbers and mark the entry curated/HIGH |
| `tyre` (`widthMm`, `aspectPercent`, `rimDiameterIn`) | Overrides the tyre, which drives wheel diameter/width |
| `chargePort` | Moves the charge flap |
| `colours` | Replaces the paint palette |
| `badge` | Plain text drawn on the tailgate when the rear faces the camera |
| `wheelStyle` | `SPOKE` (default), `AERO`, `DISC` |
| `roofRails` | Adds two rail meshes, inset so overall height stays the real height |
| `accentColor` | Hub and charge-flap highlight (default `#C6F94C`) |
| `proportions` | Any of `beltlineRatio`, `cabinStartRatio`, `cabinEndRatio`, `cabinWidthRatio`, `endTaper`, `hoodDropRatio`, `rearDropRatio`, `frontOverhangShare`, `archGap`. Zero/absent means "use the template value". |

**Do not put manufacturer logos or wordmarks in `badge`.** Plain model text only.

## Template skeleton values

These are the reference cars the eight silhouettes were drawn around. Real dimensions always
come from the resolved specs; these values supply the proportions and the fallback when a spec
field is missing. Millimetres.

| Body type | Reference | L | W | H | Wheelbase | Clearance | Tyre | Beltline ratio | Cabin start/end | Cabin width | Taper | Hood/rear drop | Front overhang share | Arch gap |
| --- | --- | ---: | ---: | ---: | ---: | ---: | --- | ---: | --- | ---: | ---: | --- | ---: | ---: |
| Hatchback | Tata Tiago EV | 3769 | 1677 | 1536 | 2400 | 165 | 175/65 R14 | 0.62 | −0.62 / 0.34 | 0.86 | 0.26 | 0.08 / 0.04 | 0.42 | 0.16 |
| Sedan | Tesla Model 3 | 4720 | 1848 | 1442 | 2875 | 138 | 235/45 R18 | 0.62 | −0.52 / 0.37 | 0.84 | 0.32 | 0.07 / 0.03 | 0.45 | 0.16 |
| Compact SUV | Tata Nexon EV | 3994 | 1811 | 1616 | 2498 | 190 | 215/60 R16 | 0.63 | −0.60 / 0.36 | 0.86 | 0.24 | 0.05 / 0.02 | 0.45 | 0.18 |
| SUV | Rivian R1S | 5100 | 2032 | 1838 | 3075 | 250 | 275/55 R21 | 0.62 | −0.66 / 0.34 | 0.87 | 0.22 | 0.04 / 0.02 | 0.45 | 0.18 |
| MPV | Toyota Innova Crysta | 4735 | 1830 | 1795 | 2750 | 178 | 215/60 R17 | 0.62 | −0.72 / 0.38 | 0.88 | 0.20 | 0.05 / 0.02 | 0.44 | 0.18 |
| Coupe | Porsche Taycan | 4963 | 1966 | 1395 | 2900 | 128 | 225/55 R19 | 0.60 | −0.45 / 0.26 | 0.82 | 0.36 | 0.09 / 0.06 | 0.46 | 0.14 |
| Pickup | Isuzu D-Max V-Cross | 5295 | 1870 | 1855 | 3095 | 190 | 265/60 R18 | 0.64 | −0.30 / 0.46 | 0.85 | 0.12 | 0.05 / 0.34 | 0.40 | 0.20 |
| Microcar | MG Comet EV | 2974 | 1505 | 1640 | 2010 | 165 | 145/70 R12 | 0.63 | −0.70 / 0.35 | 0.88 | 0.22 | 0.05 / 0.04 | 0.40 | 0.18 |

Fitting rules in `CarModelFitter`:

- convert the real dimensions to metres; clamp the wheelbase to 45–78 % of length;
- place axles from the real wheelbase and the template's front/rear overhang split;
- derive wheel radius from the tyre (rim + two sidewalls) and width from the tyre width;
- clamp ground clearance and place the beltline; regenerate the cabin, glass, lamps and wheels at
  their own proportions rather than scaling the whole model;
- wheel-arch openings are computed from the arch radius, so they do not stretch with the body.

## Add a curated profile

1. Add an entry to `app/src/main/assets/vehicle_profiles.json` (or create the file if absent).
2. Keep `make`/`model` spelling aligned with the vehicle profile so normalisation matches.
3. Use only plain-text `badge`; do not use brand logos.
4. Add or update the corresponding spec in `vehicle_specs.json` if the dimensions are not there.
5. Run `./gradlew :app:testDebugUnitTest`. `CuratedProfileTest` checks asset parsing, overrides and
   fallback; `CarMeshTest` checks the 2 % bounding box.
6. Open the debug gallery (profile menu → **Vehicle gallery (debug)**) to eyeball the result.

## Add a body type

1. Add the enum constant to `vehicle/BodyType.java` (label is used by the picker and gallery).
2. Add its reference skeleton to `BodyTemplate.TEMPLATES` with all twelve values.
3. Check `SpecEstimator` keywords so the new silhouette can be inferred from names.
4. Check `CarMesh`: the generic loft handles most changes; add anything silhouette-specific
   (e.g. a pickup bed) as its own build step keyed off `BodyType`.
5. Add the picker label where the eight silhouettes are shown (currently the estimator/confirmation
   flow and documentation).
6. Update `VehicleSpecDatabase` data/tests and the gallery to include a representative car.
7. Run the tests. `CarMeshTest.everyBodyTemplateProducesAUsableMesh` iterates every template and
   checks the bounding box, face counts, tyre count, lamps and that the tyres rest on the plane.

## Curated GLB models (not implemented in this build)

The current renderer is a software Canvas painter. It cannot load glTF/GLB, PBR materials, Draco
or clearcoat. A curated `.glb` tier requires a renderer change, not just an asset drop:

1. Add an OpenGL ES 3.x (or Filament) surface and a glTF/GLB loader, with Draco decompression and
   named-node lookup for `body`, `glass`, `wheels`, `lights`, `chargePort`.
2. Keep `VehicleSpecs` and the resolver as the provider of paint, charge port and dimensions.
3. Add `assets/models/{make}-{model}.glb` (Draco, <2 MB) and a manifest mapping model files to
   makes/models; fall back to the procedural template when absent.
4. Reuse the gallery and the 2 % bounding-box tests against the loaded mesh.
5. Licence-check every asset before shipping (see below).

The rest of this document describes the shipped, native tier.

## Third-party specs API

### Vehicle Databases (direct provider)

Set one key in `local.properties` (gitignored) or as an environment variable:

```properties
VEHICLE_DATABASES_API_KEY=...
```

With no key, `VehicleDatabasesProvider.fromConfig()` returns null and this tier never runs. The
adapter calls:

```text
GET https://api.vehicledatabases.com/ymm-specs/v3/{year}/{make}/{model}/{trim}
x-authkey: <key>
Accept: application/json
```

The profile's `purchaseYear`, make, model and variant fill the four path segments. **The vendor
requires the trim**, so a vehicle with no variant stays on the estimator. The response's nested
`dimensions` tree is searched for `overall_length`, `overall_width`, `overall_height`, `wheelbase`
and `ground_clearance_min` (falling back to `ground_clearance_max`); millimetre values win and
inches are converted. A payload without all three exterior dimensions is rejected. Body type comes
from `data.vehicle.body_type` (falling back to `style` and then the name estimator); a wheelbase is
required for HIGH confidence, otherwise the result is MEDIUM. Tyre sizes and paint colours are not
part of this contract, so the template supplies the tyre and the default paint applies.

Coverage is North America and Europe, model years 1981 onward, so the owned database and curated
profiles remain the India-first source of truth. The API key ships in the APK's `BuildConfig`; for
a production build that matters, route these calls through a server-side proxy instead.

### Normalised proxy (alternative)

If `VEHICLE_DATABASES_API_KEY` is absent, the app can use a normalised endpoint instead. Configure
both values:

```properties
VEHICLE_SPECS_API_URL=https://your-proxy.example.com/vehicle-specs
VEHICLE_SPECS_API_KEY=...
```

With either missing, `RemoteSpecProvider.fromConfig()` returns null and this path never runs. The
adapter sends `GET <url>?make=…&model=…&variant=…` with `X-Api-Key` and `Accept: application/json`
and expects the normalised shape in `RemoteSpecProvider` (vehicles array, bare array or single
object; dimensions either nested under `dimensionsMm` or flat). It rejects responses without
length/width/height and never throws. Vehicle Databases takes precedence over this endpoint when
both are configured.

### Provider comparison (checked October 2026 — verify pricing before purchase)

| Provider | Coverage | Pricing signal | Notes for this app |
| --- | --- | --- | --- |
| **CarsXE** | Claims 50+ countries, ~275 M vehicle records, VIN/plate/specs/value | Free sandbox; pay-per-call / tiered; quote on site | Not usable for this resolver: `/specs` requires a 17-character VIN and its documented payload has no exterior dimensions; `/v1/ymm` returns trim names only. |
| **API Ninjas Cars v2** | 400+ automakers; global-ish | Free 3,000 calls/mo (no commercial, no caching); Developer $39/mo (100k, commercial, **no caching allowed**); Business $99/mo (1M, caching allowed) | Our resolver caches, so Business or above is required. Documented response uses a generic `specs` map, so a small mapping layer is needed. |
| **CarAPI** | Year/make/model/trim, US-centric | Base $199/year (~$16.58/mo), 1,500 calls/day; higher tiers for specs | Cheap if US coverage is enough; not a fit for India-first. |
| **auto.dev** | US VIN/specs | Free 1,000 calls; $299/mo + data fees, ~$0.0015/call overage | Good US VIN depth, not India. |
| **MarketCheck** | US listings and specs | Free 500 calls/mo; Basic $299/mo | Listing/market product, more than this app needs. |
| **Vehicle Databases** (wired directly) | North America + EU specs, 1981–present; VIN or YMMT | Pay-as-you-go / subscription quote | Returns exterior dimensions by year/make/model/trim without a VIN, which CarsXE does not. Direct adapter in `VehicleDatabasesProvider`; no India focus. |
| **NHTSA vPIC** | US VIN decode | Free | Government data, US only; useful for validating US VINs, not dimensions worldwide. |
| **India** | No strong public make/model specs API | — | OEM brochures, dealer feeds or a licensed aggregator (CarDekho/ZigWheels) require a commercial agreement. Registration/VIN services (Surepass, Karza) return registration data, not dimensions. Keep India entries in the owned database/curated profiles. |

Recommendation: keep the owned database as the source of truth for India, use **Vehicle
Databases** (trim required) for NA/EU coverage, and negotiate a licensed India data feed if
coverage matters more than cost. Never let a remote response overwrite a curated/owned entry
without review.

## Validation gallery

Debug builds only: profile menu → **Vehicle gallery (debug)**.

- Twelve catalogue cars covering all eight body types (Nexon EV, Tiago EV, Punch EV, MG ZS EV,
  Comet EV, BYD Atto 3, Tesla Model 3, Rivian R1S, Innova Crysta, D-Max V-Cross, Swift, Taycan).
- Each card shows a still fitted model with the dimension box and L/W/H labels drawn on the mesh,
  plus wheelbase, clearance, tyre, confidence and source.
- Cards render still (`setAnimated(false)`, `setAutoRotate(false)`) so twelve models do not run
  twelve animators; touch is disabled so the list scrolls normally.
- Reference photos are optional and licensing-safe: drop a licensed image at
  `app/src/main/assets/reference_photos/<make>-<model>.png` (or `.jpg`/`.jpeg`/`.webp`), e.g.
  `tata-nexon-ev.png`. Without one, the card says where to add it rather than showing a scraped
  image.

## Bulk generation

```bash
./gradlew :app:generateVehicleSpecs
```

Reads `tools/vehicle_candidates.json`, checks each candidate against the owned database, and
writes:

- `tools/generated/vehicle_specs.generated.json` — DB-shaped proposal, `source: generated`,
  `confidence: LOW`; covered cars are omitted.
- `tools/generated/REVIEW.md` — every candidate with dimensions, body type and the reason it needs
  a human check.

The task runs the generator from the test source set, so the tool is not shipped in the APK. It
never edits `vehicle_specs.json`: promotions are a reviewed manual merge. `SpecBulkGeneratorTest`
guards the two rules that matter: already-covered cars are not regenerated, and generated entries
stay LOW confidence with a review flag.

## Tests

- `CarMeshTest`: every catalogue car and every body template matches length/width/height within
  2 %; checks tyre count, lamps, finite vertices and that tyres sit on the turntable plane.
- `CuratedProfileTest`: asset loads, overrides behave, visual-only profiles do not change specs,
  unknown/malformed assets fall back safely.
- `SpecResolverTest`: database → provider → estimator order, curated precedence, cache identity,
  driver confirmation.
- `VehicleDatabasesProviderTest`: YMMT mapping, comma-separated millimetres, inch conversion,
  body-type fallback, rejected error/partial payloads.
- `RemoteSpecProviderTest`: normalised contract mapping, flat arrays, inferred body type,
  malformed/partial responses.
- `VehicleSpecsJsonTest`: cache round-trip including curation.
- `SpecBulkGeneratorTest`: coverage and review flags.

## Performance and limits

- The procedural mesh is a few thousand quads per car (body loft 30 × 18 plus cabin, four wheels,
  lamps and flap), far below the 100 k triangle budget. The gallery deliberately renders still
  frames.
- The 60 fps target has **not** been measured on a mid-range device in this pass; the frame loop
  stops when the view is hidden and the gallery disables continuous invalidate. Profile on target
  hardware before making a performance claim.
- The charge flap pulses during a charging flow; the badge and dimension overlay are small
  Canvas draw calls.
- No runtime downloads, no GLB, no Draco, no OEM logos, no bundled third-party photos.

## Licensed and free sources for 3D cars and templates

None of these are used at runtime today. If a GLB renderer is added, start here and keep a
per-asset licence record.

| Source | Typical licence | Commercial use | Notes |
| --- | --- | --- | --- |
| **Khronos glTF-Sample-Assets / glTF-Sample-Models** | Mixed per model (often CC BY 4.0 or CC0) | Check each model's README | Best for loader/renderer tests; not a production car library. |
| **Quaternius** (quaternius.com) | CC0 | Yes, no attribution required | Low-poly vehicle packs; good template starting points. Verify the pack page anyway. |
| **Kenney** (kenney.nl) | CC0 | Yes, no attribution required | Car kits and modular pieces; low-poly style. |
| **Poly Haven** | CC0 | Yes | Environments/HDRIs, little vehicle content; useful for studio IBL. |
| **Sketchfab** | Per model: CC0, CC BY, CC BY-NC, etc. | Only CC0/CC BY (with attribution) | Filter by licence; avoid "editorial use", fan art and branded models. |
| **BlenderKit** | Free/royalty-free per asset | Check each asset's licence | Quality varies; verify commercial terms. |
| **OpenGameArt** | Mixed (CC0, CC BY, CC BY-SA, GPL) | Depends; CC BY-SA may infect derivative assets | Keep attribution and share-alike obligations in mind. |
| **TurboSquid / CGTrader / Hum3D / 3DModels.org / Squir** | Paid royalty-free licences | Usually yes | Read the licence: no resale of the model, restrictions on AI training, no logo/trade-dress rights. |
| **OEM press/configurator assets** | Copyrighted | No | Do not scrape manufacturer configurators or press images for shipping assets. |

Practical rules:

- Prefer **CC0** for templates and commissioned work for production-specific cars.
- If using CC BY, ship the attribution in an in-app licences screen and keep the source URL.
- Never use a model that carries manufacturer badges, logos or distinctive trade dress without
  permission; replace them with plain text badges (as this app does).
- Keep a `licences/` record per asset: source URL, author, licence, date, modifications (Draco,
  decimation, repaint).
- Draco compression tooling: Google Draco (Apache 2.0), `gltf-transform` (MIT), `gltfpack` /
  meshoptimizer (MIT). Verify the output loads in the target renderer before shipping.
