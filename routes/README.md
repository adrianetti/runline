# Runline open routes

Hiking, walking and running routes anyone can follow, download and share. They're free: no account and no subscription. Runline bundles this catalog, and **Routes → Check for new routes** downloads updates from this folder. Every file is checked against the SHA-256 in `index.json`.

## Routes

| Route | Region | Distance | Ascent | Shape |
|---|---|---|---|---|
| [Alhambra, Dehesa del Generalife y río Genil](es/granada/alhambra-dehesa-genil.gpx) | Granada, Spain | 14.9 km | +296 m | Loop |

## Licence and sources

The route data in this folder is licensed under the [Open Database License 1.0](LICENSE.md):

- **Route geometry:** follows public paths in [OpenStreetMap](https://www.openstreetmap.org/copyright), © OpenStreetMap contributors.
- **Elevation:** from the IGN/CNIG MDT05 digital terrain model ([CC BY 4.0](https://creativecommons.org/licenses/by/4.0/)), © Instituto Geográfico Nacional.

You can use, share and adapt the routes, including in other apps, if you credit OpenStreetMap contributors and keep adapted route data under the ODbL. Runline's app code has its own licence; see the repository root.

## Adding a route

Open a pull request that adds:

1. **The GPX file** at `routes/<country>/<region>/<short-name>.gpx`, using lowercase letters, digits and hyphens. It needs:
   - one track, `<trk>`, with elevation;
   - a few named waypoints, `<wpt>`, for places a hiker would recognise;
   - `<metadata>` with a name, a description and the ODbL `<copyright>` element.
2. **An entry in `index.json`**, with `id`, `file`, `sha256`, names, region, activity, shape, distance and ascent. Run `python3 tools/check_routes.py --fix-hashes` to fill in the hash, then run it again without the flag; it must pass.

Rules:

- **Only public paths.** Never copy a track from a service whose terms forbid it.
- **No tracing proprietary maps or imagery.** That includes Google, Apple, Bing, Strava and Wikiloc. Use OpenStreetMap, your own GPS recordings, or other sources with a compatible open licence.
- **No private land, closed paths or protected zones** without legal access.
- **No personal data.** Remove timestamps, heart rate and device extensions.

### How the first route was built

The owner had photos of a route near Granada whose download was paywalled. The photos were used only to identify which public streets and paths it follows. The geometry was then rebuilt from OpenStreetMap with the open [BRouter](https://brouter.de) hiking profile, and elevations were sampled from IGN MDT05. The rebuilt route was checked against the photos for shape, distance and height range. No geometry, text or images were taken from the original service.
