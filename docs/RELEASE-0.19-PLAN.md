# Runline 0.19 plan: hiking and open routes

Runline started as a running recorder. Hiking needs something more: a route to follow, chosen before leaving home, without a subscription or a locked GPX download. 0.19 adds a Routes tab, an open route repository in this GitHub project, and the tools to follow a route on the trail.

## The first route

The owner shared screenshots of a hiking route near Granada that its hosting service would not let them download. The route was not copied from that service or traced from its imagery. The screenshots only identified which public streets and paths it uses. The geometry was then rebuilt from OpenStreetMap paths with the open BRouter hiking profile, and elevations come from the IGN MDT05 terrain model.

- **Process.** Each zoomed screenshot was georeferenced from its street labels against named OpenStreetMap ways. Neighbouring screenshots were chained through shared map markers. The orange line was converted to ordered waypoints and routed on OpenStreetMap paths, and the result was compared with the overview image pixel by pixel.
- **Result.** `routes/es/granada/alhambra-dehesa-genil.gpx`: 14.9 km, +296/−316 m, 668–927 m.
  - **Course.** Plaza Nueva, Cuesta de Gomérez, the Alhambra woods, Paseo del Generalife and Paseo de la Sabica. Then the Acequia Real path across the Dehesa del Generalife. Then an out-and-back detour, the badlands viewpoint, Lancha del Genil, and back along the Genil riverside, Paseo Fuente de la Bicha and Paseo de la Bomba.
  - **Fit.** The shape matches the original's overview within about 40 m, and its height range is within 7 m.
- **Licence.** The route data is ODbL 1.0, as it is derived from OpenStreetMap. Elevation is IGN/CNIG CC BY 4.0. Both are credited in the GPX, the index and the app.

## Changes

1. **Open route repository.** `routes/` holds GPX files, an `index.json` catalog and contribution rules. `tools/check_routes.py` validates every route in CI: schema, file hash, distance and bounds. The app bundles the catalog and can refresh it from GitHub; each download is checked against the catalog's SHA-256.
2. **Routes tab.** Catalog and imported routes, each with distance, ascent, estimated time, difficulty and shape. Options: Import GPX, Check for new routes, and About open routes.
3. **Route details.** A satellite map of the whole route, plus:
   - distance, ascent and descent, highest and lowest point, estimated time and difficulty;
   - an elevation profile and named waypoints;
   - today's sunset at the start;
   - Share or Download GPX;
   - Hike this route.
4. **GPX import.** Tracks, routes and waypoints from any GPX file. A strict parser rejects DOCTYPE and entities and caps size and point count. Imported routes stay on the phone and can be deleted.
5. **Follow a route while recording.**
   - The planned route is drawn under your track.
   - The island shows the distance left, ascent left and arrival time against sunset.
   - **Off-route alert:** more than 35–80 m off the path for 10 s, depending on GPS accuracy. The phone vibrates, the notification says so, and, when voice is on, Runline speaks which side the path is on. A second alert confirms you are back on the route.
   - The route survives app restarts, because it is saved with the session.
6. **Estimates.** Moving time uses Tobler's hiking function on the route profile. After the first kilometre it is scaled to your own pace on the route. Difficulty uses effort kilometres: distance plus 1 km per 100 m of ascent.
7. Version 0.19.0, code 22.

## Not in 0.19

- Offline map imagery for a whole route. Terrain heights are prepared automatically.
- Turn-by-turn directions, trail conditions and photos.
- Uploading routes from the app. Contributions are pull requests to `routes/`.
