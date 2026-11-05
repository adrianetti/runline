# Terrain estimates

Version 0.16 uses independent **Mapzen/Tilezen global terrain and IGN/CNIG MDT05 data** for elevation in the live island, replay profiles, slope labels and coaching. Original GPS observations remain unchanged in storage and GPX/CSV exports. A derived in-memory session carries explicit provenance; the completed-recording store rejects attempts to save it over a GPS recording.

## Why a fuller fix was needed

Horizontal accuracy is not vertical accuracy. Smooth altitude drift can look like a convincing hill even after robust filtering. Repeated visits to the same place can produce substantially different GPS heights. A private field recording confirmed that version 0.13 reduced noise but still accepted false steep descents. Independent terrain data supported the runner's reported uphill direction. No personal route or field fixture is included in this repository.

## Global downloads and offline preparation

Nearby required terrain tiles prepare automatically while the app is open or recording, using an already-granted foreground location permission. An idle warm-up takes at most 20 seconds and stops on pause; it accepts only a recent usable fix. No extra location prompt, continuous idle tracker or whole-country download is added. Offline, Data Saver and optional Wi-Fi-only conditions are respected; the recorder's own durable GPS path never waits for terrain.

**Settings → Terrain maps** contains automatic downloads, optional Wi-Fi only, measured cache bytes, Clear terrain and manual 5 km area preparation. Manual preparation retains its current-location/transfer preview, progress and cancel/resume actions. Files persist privately across restarts. The strict **64 MiB** disk budget counts atomic/temporary files, reserves space before downloads and evicts older terrain first. Pinned areas cannot exceed the limit; recordings, photos and credentials are outside eviction. A failed download leaves missing heights unknown, and clearing terrain preserves all recordings.

The global provider is [Mapzen/Tilezen Terrain Tiles on AWS Open Data](https://registry.opendata.aws/terrain-tiles/). Public HTTPS Terrarium PNG tiles require no AWS account. Unlike a direct full-resolution Copernicus GeoTIFF integration, these ready-made small tiles can be decoded locally without adding a server or a large raster library. This source combines regional and global datasets; source resolution and survey dates vary, and raster pixel spacing does not imply ground-truth accuracy. [Encoding](https://github.com/tilezen/joerd/blob/master/docs/formats.md) and [required source credits](https://github.com/tilezen/joerd/blob/master/docs/attribution.md) are documented upstream. The complete attribution is bundled in **Sources and licenses**.

- Global sampling uses validated opaque 256×256 Terrarium images, bounded to 512 KB compressed. Decode RGB numerically without scaling/premultiplication; reject malformed dimensions, missing cells, out-of-range values and bathymetry below −500 m. Negative land elevations remain supported.
- Select zoom 8–12 by latitude (roughly 30–60 m raster spacing) and sample across tile seams/date line. Preparation includes neighboring tiles and both zoom bands near a transition. Model detail can be coarser than raster spacing.
- Mercator tiles stop near ±85.05° latitude; preparation is offered below ±85°. Polar caps remain unsupported terrain, with recording still available. No claim of perfectly measured slopes everywhere is made.
- A saved area gives its global tiles eviction priority within the strict private-cache budget. Spain also receives nearby finer IGN tiles as an enhancement. Global files determine preparation readiness; unavailable optional IGN tiles do not remove the global fallback. Readiness means validated files are present, not that every location has confident height data.
- Satellite imagery, offline speech voice installation and paid AI requests are separate. This is an elevation download, not a satellite-map pack or an offline AI model.
- One selected area is retained for preparation. Other cached areas may remain until eviction. Cache bounds: 64 entries in memory, 96 files / 64 MiB on disk, at most 96 queued requests. A failed request has a one-minute automatic cooldown; explicit Resume retries it. Cancellation, clearing and disabling invalidate pending generations.
- Source services receive coarse tile/area requests and the IP address, never a route file or activity timestamps. The selected area center is stored only in private app preferences. Existing public hosting is sufficient for this release; a managed mirror/CDN is an optional future scaling/reliability decision, subject to source terms, not a Play Store requirement.

## Regional source

The regional enhancement is [IGN's WCS terrain service](https://servicios.idee.es/wcs-inspire/mdt?request=GetCapabilities&service=WCS&version=2.0.1), coverage `Elevacion25830_5`, with [official dataset metadata](https://datos.gob.es/es/catalogo/e00125901-spaignmdt). It covers **mainland Spain and the Balearic Islands**, not worldwide or the Canary Islands. Data is derived from MDT05, IGN/CNIG, under [CC BY 4.0](https://www.ign.es/resources/licencia/Condiciones_licenciaUso_IGN.pdf). Source survey dates vary and are not inferred from the download time.

- Transform WGS84 positions to ETRS89 / UTM 30N and request kilometre-aligned areas with a 50 m border. The provider receives area bounds and the network IP, never a GPX, route-point list, timestamp or API key.
- Download bounded ASCII coverage responses over HTTPS. Use the service's fractional heights rather than integer-quantized TIFF values. Validate dimensions, resolution, finite values and geographic registration. No-data cells remain absent.
- Bilinearly sample the model. Probe the position's horizontal uncertainty footprint (at least 5 m, at most 20 m); missing probes or more than 3 m of height disagreement reject the position. This is a conservative confidence gate, not proof of surveyed path elevation.
- Persist validated areas atomically in private app storage. Share the bounded cache described above. No package dependency, backend, API key or subscription is required.
- Use separate network and analysis workers. Downloads and cache failures never block recording durability, alter its clock or supply recording points. Each request has bounded connection/read times, response size and a download deadline. Failed areas have a one-minute retry cooldown; queued work is bounded.

**Settings → Terrain maps** opens the same preparation screen and controls the source switch and cache clearing. Previously downloaded areas work offline across process restarts. Opening a recording requests its areas; a new run requests current tiles after each GPS fix is saved. No whole-country download is implied. Missing or ambiguous IGN data may use independently sampled global terrain. Missing global data, disabled maps or an ambiguous global position produces a gap, never a fallback to raw GPS altitude. Elevation details identify IGN/CNIG, Mapzen or both; complete credits live in About. The live/replay island omits the duplicate provider footer.

## Grade and profiles

All height displays use the derived model; the route, distance, pace, timing and exports use original observations. Never join heights from different models or vertical references. Derived points carry in-memory model provenance; source changes break height interpolation, totals and grade windows while retaining the original route clock. Missing heights retain the replay clock and split chart paths. Model coverage can be partial, so totals can undercount; coverage is shown beside attribution and sent with AI summaries. Climb/descent retains its existing 3 m deadband.

Grade uses 5 m distance bins and a robust fit over 55–90 m for IGN, or 120–180 m for the coarser global source, with at least eight fixes and 20 seconds. A recent 35 m (IGN) / 70 m (global) window vetoes old-direction calls at reversals/leveling. Accepted percentages smooth over traveled distance. Model labels enter Uphill/Downhill at ±1.5% and leave at ±0.5%; the old GPS estimator remains available for tests but no longer drives production hill guidance. Supported steep terrain is not clamped to a park-specific maximum.

The labels are **Uphill / Downhill / Flat** and **Subida / Bajada / Llano**, with the existing brief flip and reduced-motion behavior. The interactive elevation chart and island profile use the same model and observed clock. Edit/AI review remain in the details header menu.

## Coaching

The local coach uses the same independent heights. Missing maps and disabled downloads invalidate terrain and pending hill cues even without another GPS fix. Raw lap heights no longer add spoken climb explanations. AI contexts withhold uncorroborated GPS ascent/descent and identify mapped coverage/source. Live hill advice requires an eligible independent estimate; an EN/ES phrase guard drops unsupported or direction-conflicting hill claims. Pending AI advice is invalidated when the terrain generation changes. This guard is a backstop, not a general guarantee about every possible paraphrase.

Diagnostic speech events include `terrain_source`. Original horizontal/vertical GPS accuracy remains separately available in recordings/exports and new diagnostic events; no map value is relabeled as sensor precision.

## Limits

Terrain models can miss bridges, stairs, elevated paths and recent terrain changes; global sources can include buildings or vegetation. Horizontal GPS error can still place a runner on the wrong feature; a confidence footprint is not a guarantee. Source date/resolution, missing coverage and the grade's trailing window all matter. Barometer fusion, polar terrain and managed download hosting are not implemented. No model is a medical or effort measurement.

See [Testing](TESTING.md) for measured evidence. Field fixtures, cached test areas and developer credentials stay outside Git.
