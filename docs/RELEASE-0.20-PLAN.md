# Runline 0.20 plan: navigation on a followed route

0.19 lets you follow a route and warns you when you leave it. 0.20 makes the route hard to lose in the first place. It speaks the turns before you reach them, warns about the next climb, and tells you a little about the places you pass. It also previews a route before you go, starts a hike from a route in one tap, and cleans up the first open route.

Everything below works offline. It is derived from the route file and the catalog, and nothing is sent anywhere.

## 1. Turn cues

**Detecting manoeuvres.** `RouteManeuvers` is pure Kotlin, with JVM tests.

- **Sampling.** The route is resampled every 5 m.
- **Turn angle.** At each sample, compare the bearing of the 20 m before a 20 m window with the bearing of the 20 m after it. A change of 35° or more is a turn candidate.
- **Turn point.** Neighbouring candidates form one cluster. The sample with the largest change in a cluster is the turn.
- **Kinds:**

  | Change | Kind |
  |---|---|
  | 35–60° | bear left/right |
  | 60–120° | turn left/right |
  | 120° or more | sharp left/right |

  "Turn around" is used only where the path retraces itself, as at the end of an out-and-back leg. A hairpin bend into a ravine is a sharp turn.
- **Close turns** are merged:
  - Two turns in the same direction less than 30 m apart become one turn with the combined angle.
  - Opposite turns are a jog when they are less than 30 m apart, or both under 60° and less than 50 m apart. A jog is dropped when the net change is under 35°. Otherwise only the larger turn is kept. A street offset (90° then 70°, 40 m apart) stays two cues.
- **Wiggle.** The 20 m arms ignore GPS-style wiggle.
- **Zig-zags.** Three or more alternating hairpins, each 100° or more and less than 80 m apart, become one "zig-zags for N m" cue. Switchbacks are easy to follow, and announcing every bend would be noise.
- **Ends.** No manoeuvres within 15 m of either end of the route.
- **Junctions.** Geometry alone would also announce every bend of a winding path: 72 cues on the Granada loop. Catalog routes therefore list where they meet other OpenStreetMap paths, written by `tools/route_junctions.py`. Only turns within 20 m of a junction are spoken, plus reversals. That leaves 35 cues on the Granada loop. Imported GPX files have no list and use geometry alone.

**Speaking.** `RouteGuidance` turns progress on the route into cues. It is pure Kotlin and tested.

- **When.** About 80 m before a turn ("In 80 metres, turn left") and again at 20 m ("Turn left now").
  - The early cue is skipped when you join the route closer than 40 m to the turn.
  - When another manoeuvre follows within 60 m, the late cue names both: "Turn left now, then right".
  - Turns you have already passed are never spoken, after rejoining the route either.
- **Priority.** `CoachSpeechSchedule` gets a **navigation** priority:
  - 3 s silence after other speech, instead of the milestone's 30 s.
  - 12 s lifetime, because a late turn instruction is wrong.
  - A navigation cue is never superseded by a goal cue. It does not depend on the pace coach's eligibility, so it still plays at a slow walk.
  - Off-route and back-on-route alerts move to this priority too.
  - The existing schedule tests stay unchanged.
- **Island.** A GPS-style arrow, the distance to the next manoeuvre, and the instruction. Tabler arrows are imported with `tools/import_icons.py --fetch`.

## 2. Climb ahead

- **Detection.** `RouteClimbs` finds climbs and descents in the route profile:
  - heights resampled every 25 m;
  - a 10 m hysteresis, so small bumps do not split a climb;
  - a split at any flat stretch of 400 m or more, so a long valley floor is not one endless descent;
  - at least 30 m of height change at an average of 1.5% or more, which is 30 m within 2 km.
- **Island.** "Climb ahead: 110 m over 1.2 km, ~9%" from 2 km before the climb starts. Inside the climb, the height still to climb.
- **Speech.** Once, about 200 m before the start.
- **Elevation ahead.** A small profile from 300 m behind you to 2 km ahead, with a position marker. It never drops below a 40 m height range, so gentle ground stays flat.

## 3. Place notes

- **Catalog format.** `routes/index.json` gets an optional `places` list per route. Each place pairs a GPX waypoint name with a short note in English and Spanish.
  - `tools/check_routes.py` checks that the waypoint exists, both languages are present, and each note is 1–260 characters of plain text.
  - `RouteCatalog` applies the same limits and rejects the whole catalog when one note is malformed.
- **Speech.** A note is spoken once, when you are within about 60 m of the place on the route. Place notes, climbs and finishing use a new **route** priority. It shares the milestone slot with an 8 s quiet interval, comes after turns, and survives a standstill.
- **Route details.** "Along the way" shows each note under its place.
- **Granada notes.** Notes for Puerta de las Granadas, the Acequia Real path, the Dehesa ravines, the badlands near Lancha del Genil and the Genil riverside.
  - Each statement is checked against public sources: the Patronato de la Alhambra y Generalife, Spanish heritage registers and encyclopaedias.
  - Where sources disagree, the note stays general.
- **AI notes.** Deferred. They would be written for routes without notes, opt-in under the existing AI consent, sending only place names and rounded positions. That needs consent, storage and review UI, so it comes in a later release.

## 4. Route preview

Preview a route the way recordings play back:

- the 3D satellite camera following the line;
- playback speed from 1× to 480×, and a timeline you can scrub;
- distance, time and elevation.

The timeline is the estimated moving time from Tobler's hiking function. The route is resampled every 10 m so playback stays continuous. The preview never creates or changes a recording.

## 5. Starting a hike from a route

0.19 hid **Hike this route** at the end of a scrolling panel. It then left you on the Record tab to find **Start**.

- **Route details.** A fixed bar at the bottom holds two buttons:
  - **Start** begins recording with the route straight away, with the matching activity. Permissions are requested as usual.
  - **Preview** opens the route preview.
- **During a recording,** the bar says a recording is in progress instead of offering Start.
- **Routes list.** The route chosen for the next recording is marked, and each row says what it is.

## 6. Cleaning the Granada route

0.19's rebuilt route had short dead-end spurs and one small loop that led nowhere. They were artefacts of routing through intermediate points: the router went into a side path and came straight back.

- **Removed.** Each piece was checked against current OpenStreetMap ways (Overpass). Removed were:
  - 13 exactly retraced spurs, up to 104 m each way;
  - a 150 m loop near Lancha del Genil, where the track runs straight across in 28 m.
- **Kept.** The real hairpins of the Sendero Acequia del Real were kept. No OSM path takes a shorter line there that stays on the acequia path. The deliberate 420 m out-and-back detour was kept too.
- **Moved.** The "Cruce" waypoint moves to the junction where the detour actually leaves the loop.
- **Result.** The route is 14.0 km instead of 14.9 km.
- **New check.** `tools/check_routes.py` now rejects retraced spurs under 50 m each way. A longer out-and-back must end at a named waypoint.

## 7. Fixes

- **Back on route.** The 0.19 back-on-route alert spoke the "Back to routes" button label. It now says "Back on the route".
- **Long speech.** Speech had a fixed 15 s watchdog. A 240-character place note hit it, which cut the note and dropped a pending turn cue. The watchdog now scales with the text: 6 s plus 90 ms per character, never under 15 s.
- **Notes and turns.** A place note no longer starts while a turn's "now" cue is due within 150 m. It plays after the turn, up to 150 m past the place.
- **Joining mid-route.** 0.19 preferred the start of the route by weighting the distance along it. So joining at 7 km could latch onto an earlier leg nearby and report off route.
  - Joining now takes the earliest part of the route within reach.
  - During the first 300 m, a fix that would go backwards or off the route moves to a leg within reach that runs the way you are walking.

## Version

0.20.0, code 23.

## Not in 0.20

- Junction lists for imported GPX files. Those routes use geometry alone.
- AI-written place notes.
- Offline map imagery for a whole route.
