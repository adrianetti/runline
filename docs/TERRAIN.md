# Terrain estimates

Version 0.14 uses an independent **IGN/CNIG MDT05 terrain model** for elevation in the live island, replay profiles, slope labels and coaching. Original GPS observations remain unchanged in storage and GPX/CSV exports. A derived in-memory session carries explicit provenance; the completed-recording store rejects attempts to save it over a GPS recording.

## Why a fuller fix was needed

Horizontal accuracy is not vertical accuracy. Smooth altitude drift can look like a convincing hill even after robust filtering. Repeated visits to the same place can produce substantially different GPS heights. A private field recording confirmed that version 0.13 reduced noise but still accepted false steep descents. Independent terrain data supported the runner's reported uphill direction. No personal route or field fixture is included in this repository.

## Independent source

The initial provider is [IGN's WCS terrain service](https://servicios.idee.es/wcs-inspire/mdt?request=GetCapabilities&service=WCS&version=2.0.1), coverage `Elevacion25830_5`, with [official dataset metadata](https://datos.gob.es/es/catalogo/e00125901-spaignmdt). It covers **mainland Spain and the Balearic Islands**, not worldwide or the Canary Islands. Data is derived from MDT05, IGN/CNIG, under [CC BY 4.0](https://www.ign.es/resources/licencia/Condiciones_licenciaUso_IGN.pdf). Source survey dates vary and are not inferred from the download time.

- Transform WGS84 positions to ETRS89 / UTM 30N and request kilometre-aligned areas with a 50 m border. The provider receives area bounds and the network IP, never a GPX, route-point list, timestamp or API key.
- Download bounded ASCII coverage responses over HTTPS. Use the service's fractional heights rather than integer-quantized TIFF values. Validate dimensions, resolution, finite values and geographic registration. No-data cells remain absent.
- Bilinearly sample the model. Probe the position's horizontal uncertainty footprint (at least 5 m, at most 20 m); missing probes or more than 3 m of height disagreement reject the position. This is a conservative confidence gate, not proof of surveyed path elevation.
- Persist validated areas atomically in private app storage. Cache up to 64 areas / 40 MiB on disk and 48 in memory. No package dependency, backend, API key or subscription is required.
- Use separate network and analysis workers. Downloads and cache failures never block recording durability, alter its clock or supply recording points. Each request has bounded connection/read times, response size and a download deadline. Failed areas have a one-minute retry cooldown; queued work is bounded.

**Settings → Terrain maps** controls downloads (enabled initially) and cache clearing. Previously downloaded areas work offline and across process restarts. Opening a recording requests its areas; starting a new run requests the current area after the GPS fix is saved. No advance whole-country download is implied. A missing area, failed request, unsupported region, disabled setting or ambiguous position produces a gap, never a fallback to unreliable GPS altitude. Coverage/source labels distinguish complete, partial, loading and unavailable data.

## Grade and profiles

All height displays use the derived model; the route, distance, pace, timing and exports use original observations. Never join a terrain-model height to a GPS height with a different vertical reference. Missing heights retain the replay clock and split chart paths. Model coverage can be partial, so totals can undercount; coverage is shown beside attribution and sent with AI summaries. Climb/descent retains its existing 3 m deadband.

Grade uses 5 m distance bins and a robust fit over 55–90 m with at least eight fixes and 20 seconds. A recent 35 m window vetoes old-direction calls at reversals/leveling. Accepted percentages smooth over traveled distance. Model labels enter Uphill/Downhill at ±1.5% and leave at ±0.5%; the old GPS estimator remains available for tests but no longer drives production hill guidance. Supported steep terrain is not clamped to a park-specific maximum.

The labels are **Uphill / Downhill / Flat** and **Subida / Bajada / Llano**, with the existing brief flip and reduced-motion behavior. The interactive elevation chart and island profile use the same model and observed clock. Edit/AI review remain in the details header menu.

## Coaching

The local coach uses the same independent heights. Missing maps and disabled downloads invalidate terrain and pending hill cues even without another GPS fix. Raw lap heights no longer add spoken climb explanations. AI contexts withhold uncorroborated GPS ascent/descent and identify mapped coverage/source. Live hill advice requires an eligible independent estimate; an EN/ES phrase guard drops unsupported or direction-conflicting hill claims. Pending AI advice is invalidated when the terrain generation changes. This guard is a backstop, not a general guarantee about every possible paraphrase.

Diagnostic speech events include `terrain_source`. Original horizontal/vertical GPS accuracy remains separately available in recordings/exports and new diagnostic events; no map value is relabeled as sensor precision.

## Limits

A 5 m bare-earth model can miss bridges, stairs, elevated paths and recent terrain changes. Horizontal GPS error can still place a runner on the wrong feature; a confidence footprint is not a guarantee. Source date/resolution, missing coverage and the grade's trailing window all matter. Barometer fusion and coverage outside the initial region are not implemented. No model is a medical or effort measurement.

See [Testing](TESTING.md) for measured evidence. Field fixtures, cached test areas and developer credentials stay outside Git.
