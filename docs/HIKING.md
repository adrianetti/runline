# Hiking and routes

Since 0.19 Runline can follow a planned route. Choose one in the **Routes** tab: an open route from this repository, or any GPX file you import. Then go hiking with the route on the map and an alert if you leave it.

Since 0.20 Runline also navigates the route:
- it speaks each turn before you reach it;
- it warns about the next climb and shows the height ahead;
- it reads short notes about the places you pass.

You can also preview a route in 3D before you go.

## Routes tab

- **Open routes.** The catalog in [`routes/`](../routes/README.md) is bundled with the app. **Routes → ⋯ → Check for new routes** downloads newer routes from GitHub. Every file must match the SHA-256 in the catalog, and the bundled copy stays in use until a download is complete and verified.
- **Add route.** Tap **+** at the top of Routes, then choose a `.gpx` file from Files or a connected document provider. You can also download a GPX elsewhere and choose **Open with → Runline**, or share a GPX attachment to Runline. Android has no MIME type for `.gpx`, so Chrome, Files and My Files pass it as a binary or XML file; Runline accepts those and checks the contents before saving. Providers must grant access to a content URI; if an app still does not offer Runline, use **+**.
- **Name, then add.** Keep or edit the suggested name and tap **Add route**. Tapping the name selects it, so typing replaces it; a blank name uses the suggestion; Cancel saves nothing. A useful GPX title wins, then a readable filename. Default activity titles (“Morning Run”, “Carrera de mañana”), IDs, dates and Runline's own export suffix don't count. Otherwise Runline builds a name from the town, the start, the far point (finish, turnaround, or the point of a loop farthest from the start), the shape and the distance: “Granada, Los Pinos – Puente Romano loop 10.3K”, “Madrid, Gran Vía – Museo del Prado out and back 5K”. Named waypoints within 100 m of those points beat map labels. Places come from Android's built-in geocoder for just those two points, in the app language; offline or without a geocoder the suggestion is shape and distance only (“Loop 10.3K”). Shape is a conservative geometry estimate.
- **GPX contents.** Runline reads track points (or route points when there is no track) and named waypoints, and ignores timestamps and device extensions. The parser refuses malformed XML, DOCTYPE/entity declarations, files over 15 MB and more than 100,000 points. Multiple tracks/segments are read in file order as one planned route, so check its preview before following it. Original GPX bytes are retained for export; the chosen name is stored separately. Imports stay on the phone and never become recorded activities.
- Each route lists its distance, ascent, estimated moving time, difficulty and shape.
- **Start an imported route.** It keeps the activity selected on Record (Run, Walk or Hike); open routes use their own.

## Route details

- **Map.** The whole route on satellite imagery.
- **Figures.** Distance, ascent, descent, highest and lowest point, estimated time, difficulty and shape.
- **Elevation profile.** Distance against height, never squashed below a 40 m range.
- **Daylight.** Today's sunset at the start, and when you would finish if you started now.
- **Along the way.** Named waypoints with their distance along the route.
- **Along the way.** Each named place shows its note under it, when the route has one.
- **Options.** Share GPX and Save GPX to… give the original file. **Delete** removes an imported route.
- **Terrain.** Opening a route prepares terrain heights for its area, so grades and coaching work offline.

Two buttons stay fixed at the bottom of the details:
- **Start** begins recording straight away, with the route attached and the route's activity (hike, walk or run). Location and notification permissions are asked for as usual. During a recording the button opens the recording instead.
- **Preview** plays the route like a recorded activity: the tilted 3D satellite camera, playback speed from 1× to 480×, a timeline you can drag, and the planned pace, distance, time and climb. Time is Tobler's moving time. The elevation is the route's own profile, so it works offline and has no gaps. Nothing is recorded.

The route chosen for the next recording is ticked in the Routes list. It also shows on the Record tab, with a button to stop following it.

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
- **Getting to the start.** Until you reach the route, the island says where its start is, as the crow flies: "Start 450 m to your left", or a compass point ("to the north-east") before you are moving. A thin dotted line on the map joins you to the start.
  - Runline says it too, when following begins, at about 1 km, 500, 200, 100 and 50 m, when the start falls behind you, and every 3 minutes otherwise.
  - For street directions, tap the signpost in the island, or **⋯ → Directions to start** on the route page. Google Maps opens in walking navigation (any maps app otherwise); recording keeps running, and Runline takes over once you are on the route.
- **Matching.** Each fix is matched to the route by expected progress, not just the nearest point, so out-and-back legs and loops count correctly.
- **Joining.** You can join the route anywhere. Runline joins the earliest part of the route within reach, so a loop starts at its start. If that is a neighbouring leg (near a junction, or beside a parallel path), the first 300 m move you to the leg running the way you walk, without an off-route alert.
- **Off route.** You are off route when 3 fixes over at least 10 s are further from the path than 1.5× the GPS accuracy, clamped to 35–80 m.
  - The phone vibrates, Runline says how far away the path is and on which side, and a **Route alerts** notification pops up, so it reaches a locked phone or one showing another app. It clears when you are back on the route.
  - The alert repeats every 2 minutes while you stay off route.
  - Two fixes well inside the threshold put you back on route, with a short vibration and "Back on the route".
- **Wrong way.** On the route but going backwards along it for 60 m (you turned round, or set off round a loop the other way): three vibrations, "Wrong way. The route goes the other way." and the same pop-up. About 20 m the right way clears it. Wobble while standing still does not count, and neither does the way back along an out-and-back leg.
- **Finishing.** The route is complete within 30 m of its end, once at least half of it is behind you.
- **Restarts.** The route is saved with the session. After an app restart, progress is rebuilt from the recorded fixes, and every cue already behind you stays silent.

## Navigation

Cues come from the route itself and work offline. They are spoken whenever you follow a route, even with the voice coach off; the coach switch only controls pace and AI coaching. The island shows them too, and the phone vibrates briefly at each turn.

### Turns

- **Detection.** The route is resampled every 5 m. At each point, Runline compares the direction of the 20 m before a 20 m window with the 20 m after it. A change of 35° or more is a turn:

  | Change | Cue |
  |---|---|
  | 35–60° | bear left/right |
  | 60–120° | turn left/right |
  | 120° or more | turn sharp left/right |

- **Turn around.** Only where the route goes back over the way it came, at the end of an out-and-back leg. A hairpin bend into a ravine is a sharp turn.
- **Noise.** Turns less than 30 m apart become one turn. A small wiggle (two opposite turns under 60°, less than 50 m apart) is ignored. Three or more switchbacks less than 80 m apart become one "zig-zags for N m" cue.
- **Junctions.** Open routes list where they meet other paths, taken from OpenStreetMap, so Runline speaks turns only there. A bend in a single path stays quiet. Imported GPX files have no junction list, so every turn in their geometry is spoken.
- **Speech.** About 80 m before a turn ("In 80 metres, turn left") and again at 20 m ("Turn left now").
  - When a second turn follows within 60 m, both are named: "Turn left now, then right".
  - Joining the route less than 40 m before a turn skips the first cue.
  - A turn waits at most 3 s after other speech and is dropped after 12 s, because a late instruction is a wrong one.
- **Island.** A GPS-style arrow, the distance to the next turn and the instruction.

### Climbs

- **Detection.** Heights are resampled every 25 m. A 10 m hysteresis stops small bumps from splitting a climb, and a flat stretch of 400 m splits it. A climb or descent counts from 30 m of height change at an average of 1.5% or more, which is 30 m within 2 km.
- **Island.** "Climb ahead: 110 m over 1.2 km, ~9%" (or a descent) from 2 km before. Inside the climb, the height still to climb.
- **Speech.** Once, about 200 m before the start.
- **Elevation ahead.** A small profile in the island from 300 m behind you to 2 km ahead, with your position marked. Ground already walked is lighter.

### Place notes

- **Speech.** When the route has a note for a named place, Runline reads it once, from about 60 m before the place. If a turn is coming within 150 m, the note waits until the turn has been called, so it never holds back "Turn left now".
- **Format.** Notes live in the open catalog (see [the routes README](../routes/README.md)). Each one is a short, checked fact in English and Spanish.
- **Queue.** They wait 8 s after other speech, and turns go first. They still play while you stand still.

Routes never change a recording. The activity is saved and exported as usual.

## Limits

- Map imagery for a whole route is not downloaded in advance; terrain heights are.
- Turn cues come from the route's shape and, for open routes, OpenStreetMap junctions. They do not know about closures, new paths or construction.
- Route alerts and cues are not certified navigation.
- Check conditions, closures and the weather before you go.
