# Hiking and routes

Since 0.19 Runline can follow a planned route. Choose one in the **Routes** tab: an open route from this repository, or any GPX file you import. Then go hiking with the route on the map and an alert if you leave it.

## Routes tab

- **Open routes.** The catalog in [`routes/`](../routes/README.md) is bundled with the app. **Routes → ⋯ → Check for new routes** downloads newer routes from GitHub. Every file must match the SHA-256 in the catalog, and the bundled copy stays in use until a download is complete and verified.
- **Import GPX.** Choose any GPX file. Runline reads its track (or route points when there is no track) and named waypoints, and ignores timestamps and device extensions. The parser refuses DOCTYPE and entity declarations, files over 15 MB and tracks over 100,000 points. Imported routes stay on the phone and can be deleted.
- Each route lists its distance, ascent, estimated moving time, difficulty and shape.

## Route details

- **Map.** The whole route on satellite imagery.
- **Figures.** Distance, ascent, descent, highest and lowest point, estimated time, difficulty and shape.
- **Elevation profile.** Distance against height, never squashed below a 40 m range.
- **Daylight.** Today's sunset at the start, and when you would finish if you started now.
- **Along the way.** Named waypoints with their distance along the route.
- **Options.** Share GPX and Save GPX to… give the original file. **Delete** removes an imported route.
- **Terrain.** Opening a route prepares terrain heights for its area, so grades and coaching work offline.

**Hike this route** selects the route and the matching activity on the Record tab. The route shows there with a button to stop following it.

## Estimates

- **Moving time.** [Tobler's hiking function](https://en.wikipedia.org/wiki/Tobler%27s_hiking_function), 6·e^(−3.5·|grade + 0.05|) km/h, applied every 50 m of the route profile. Grades are capped at ±60%. Stops are not included. While you follow a route, the time left is scaled to your own pace on it from the first kilometre onwards, between 0.6× and 2×.
- **Difficulty.** Effort kilometres: distance plus 1 km per 100 m of ascent.

  | Effort kilometres | Difficulty |
  |---|---|
  | under 10 | easy |
  | under 20 | moderate |
  | under 30 | hard |
  | 30 or more | very hard |

  This says nothing about terrain hazards, exposure or weather.
- **Ascent.** A 3 m deadband on the route's heights, as for recordings.
- **Sunset.** The NOAA sunrise equation at the route start, accurate to a minute or two.

## Following a route

- **Map.** The planned route is a dashed line under your recorded track.
- **Live readout.** The island shows the distance and ascent left, your arrival time, and a warning when you would arrive after sunset. It also shows the next named waypoint.
- **Approaching.** Before you reach the route, it shows the distance to its start.
- **Matching.** Each fix is matched to the route by expected progress, not just the nearest point, so out-and-back legs and loops count correctly. You can join the route anywhere.
- **Off route.** You are off route when 3 fixes over at least 10 s are further from the path than 1.5× the GPS accuracy, clamped to 35–80 m.
  - The phone vibrates and the notification title says **Off route · N m**.
  - When voice is on, Runline says how far away the path is, and on which side relative to your direction of travel.
  - The alert repeats every 2 minutes while you stay off route.
  - Two fixes well inside the threshold put you back on route, with a short vibration and "Back on the route".
- **Finishing.** The route is complete within 30 m of its end, once at least half of it is behind you.
- **Restarts.** The route is saved with the session. After an app restart, progress is rebuilt from the recorded fixes.

Routes never change a recording. The activity is saved and exported as usual.

## Limits

- Map imagery for a whole route is not downloaded in advance; terrain heights are.
- There are no turn-by-turn directions, and route alerts are not certified navigation.
- Check conditions, closures and the weather before you go.
