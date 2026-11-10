# Using Runline

Runs, walks and hikes on Android 8.0+, with local history, maps, replay and optional coaching. English and Spanish are supported.

## Follow a route

Open **Routes** and pick an open route, or tap **+** to add your own GPX (you can also open or share a GPX with Runline). The route page shows distance, ascent, estimated time, difficulty, an elevation profile, waypoints and today's sunset. **Hike this route** puts it on the Record tab; tap Start as usual. While recording, the route is dashed on the map and the island shows what is left. If you stray from the path, the phone vibrates and tells you which way it is. Details: [Hiking and routes](HIKING.md).

## Record an activity

1. Open **Record**, choose **Run**, **Walk** or **Hike**, then tap **Start**. On first use, grant precise location and allow notifications. Location must be enabled on the phone.
2. A loading state stays over the map until a usable GPS fix arrives. Wait outdoors; the status explains disabled location or missing permission. You can lock the screen or switch apps while the independent foreground service records.
3. Tap **Pause**, then **Resume** or **Finish**. The route is saved immediately, then an optional editor offers title, notes, effort, routine type and photos. Close the editor to keep the recording without extra details; edit it later from the saved activity’s **…** menu.

The idle Record tab has an activity picker, a speaker toggle, an options icon and Start. The speaker changes voice guidance in one tap; **… → Voice settings** includes Test voice, while **… → Goal** sets a manual target. The tabs are Record, Activities and, since 0.19, Routes. **Settings** opens Runner profile, AI, Terrain maps, Language, Recording protection and About. The activity choice is remembered and stored through recovery/export; old untyped recordings remain Run.

During recording, the satellite map fills the app content area. A bottom island leads with recent pace in min/km, a rolling terrain profile and signed grade and distance. Time and elevation totals are smaller; portrait retains average pace as supporting context. Pause stays fixed within reach. Paused and recovered sessions offer Resume and Finish. Activities and options float above the map. Landscape moves the island to the lower left. Icons use one [MIT-licensed Tabler outline set](../THIRD_PARTY_NOTICES.md), including the running figure, navigation arrow, controls and notification glyph.

The app follows the **phone’s language** by default, with English fallback and Spanish support. **Language / Idioma** offers Phone language, English and Español. Updating from v0.5 clears its forced-Spanish setting once; later explicit language choices are retained. Controls, messages, recording notifications and exported activity names follow the selected app language. Android permission dialogs and third-party document providers follow their own language settings.

**Settings → Recording protection**, or the same action in the active-run menu, shows notification and battery-optimization status with shortcuts to Android settings. These checks help identify restrictive settings; they do not certify future GPS continuity on a particular phone.

The ongoing notification shows distance, average pace and a live active-time chronometer, with GPS status when expanded. Tap **Pause**, then **Resume** or **Finish**; the timer freezes while paused. Tapping the notification opens the current recording. Allow lock-screen notifications, including silent notifications where the phone has a separate setting, to use the controls there. Samsung may show icons until expanded, and its Now bar supports selected apps; Runline does not claim Now bar integration or pose as a music player. See [Android notification visibility](https://developer.android.com/develop/ui/compose/notifications/create-notification) and [Samsung Now bar guidance](https://www.samsung.com/my/support/mobile-devices/how-to-use-the-now-bar-on-the-lock-screen-of-your-samsung-galaxy-device/).

## Optional voice coach

Voice coaching starts **off**. Tap the speaker on Record to toggle it, or open **… → Voice settings** before/during a run. Select the session target separately through **… → Goal** before Start. Goals use total distance and **active time**, excluding explicit pauses. At Start, the selected target is saved with that session independently of voice enablement; changing global preferences later does not rewrite its intent. You can still turn speech off in one tap.

Use **Test voice** before heading out. Coaching uses an installed offline English or Spanish Android text-to-speech voice for the app language; it does not generate or download a person’s voice. Voice settings opens the phone’s TTS settings when available. If a suitable voice is missing, recording still works and the settings screen explains how to add one.

The recorder service owns the coach, so leaving the screen does not intentionally stop it. Brief prompts can announce kilometre progress, one kilometre remaining, goal completion, or sustained pace feedback. Pace, encouragement, terrain and lap feedback each have a switch and default on inside the explicit voice-coach opt-in. Local and AI speech share one bounded priority scheduler: goal completion and milestones take precedence, and routine advice leaves at least 85 seconds after the previous utterance finishes. A rotating bilingual phrase library avoids immediate repetition. GPS-estimated climbs/descents and a two-minute recovery window suppress pace correction, including when terrain announcements are disabled. Matching repeated loops can produce estimated lap and four-section comparisons, without named-place guesses. Weak/stale GPS, pauses and recovery invalidate old cues. Pace guidance waits for sufficient recent movement and uses tolerance/cooldown rather than speaking on every GPS change. Distance-plus-time guidance uses the target’s average required pace; it does not calculate a changing catch-up pace.

Before speech, Runline requests temporary audio focus that allows music to lower. It releases focus after speech, failure, timeout or interruption and never changes the phone’s global media volume directly. Actual ducking, Bluetooth routing and interruptions depend on Android, the TTS engine and the other player. The owner reported successful voice coaching on their phone with v0.6; controlled v0.7 Spotify/headphone and Samsung field checks remain outstanding. Recording is independent of successful speech. See [coaching behavior and limits](COACHING.md).

## Editable activity details

Finished and older recordings support a title, notes, optional effort, activity type and routine (easy, long, tempo, intervals, recovery, race or other). Effort uses a native slider: **Unrated** at zero, then self-ratings from 1–10 with a short intensity label. Save stays below the scrolling form. Up to six photos are copied into private app storage through Android’s picker, downsampled and stripped of EXIF. The details sheet shows photo previews and stored annotations. These records live separately from GPS samples and use checksummed atomic copies; annotation failure never replaces the original route. Photos are not included in CSV/GPX/ZIP or AI requests.

## Optional Anthropic and Smart goals

**Settings → AI** accepts your own Anthropic key, encrypted with an Android Keystore key on the phone. No key ships in the APK, enters exported files or needs to be sent to the developer. All AI features start off. Enable **Live advice**, **Automatic reviews** and **Daily suggestion** separately, or select all three with **All AI features**. Daily suggestion is initially off, including for existing installations. This shortcut does not enable activity-note or profile sharing. Live speech also requires Voice coach enabled. Provider/model dropdowns offer Anthropic, Haiku for live advice and Sonnet for reviews/Smart goals, plus a Custom model option. Opening setup is local. New reviews require enabled, configured AI; saved reviews remain readable with AI off; The Record suggestion is hidden when its feature is disabled or AI is unconfigured. **Test connection** checks access to both selected models without generating coaching.

Live requests use a configurable 3–10 minute active-time interval and a 24-attempt cap per activity. Shared context includes the optional overall aspiration, the applied distance/time/pace goal, calendar-week and recent running totals, recent activities, map-estimated terrain, self-reported effort/routine and up to six recently spoken cues. The model can return silence when it has nothing useful to add; generated text only enters speech history after Android reports that speech started. Titles and notes require an additional sharing switch. Raw coordinates and photos are never sent. Requests spend the user’s Anthropic API credits; offline recording and coaching remain independent of network/model failures. Late or superseded live responses are dropped after pause/stop/settings changes or intervening feedback. See [AI setup, data flow and limits](AI.md).

**Settings → Runner profile** optionally stores running experience, training preferences and health notes on this phone. Nothing is inferred from pace or appearance. **Include profile in AI** is a separate, initially off switch that also covers the health notes; it does not turn AI on. The profile is excluded from activity exports and Android backup. It can be cleared from the same sheet. Health notes remain self-report, not a diagnosis or a measured fitness assessment.

**Settings → AI → Overall goal** optionally stores a short aspiration such as “Half marathon in April.” There is no calendar planner or automatic plan generation. An existing saved plan contributes its goal once, preserving its original date/month precision; its old file and recordings stay untouched. Clearing the new goal is respected.

Enable **Settings → AI → Daily suggestion** to prepare advice automatically on the first idle Run screen visit each local day. A small spinner appears while Start stays usable. Claude considers your overall goal, comparable runs, weekly workload, effort and elapsed hours since the last run. Record shows a **Recommended** brain label, distance or duration, an optional pace/range and one effort word. Tap **Recommended** to show Claude’s saved explanation and analysis time; tap again to collapse it. Rest uses the same explanation, without assuming that time of day caused the advice. Viewing it makes no new AI request. **Use** selects it for this session without enabling speech or starting capture. Rest has no Use switch. Suggestions survive reopening; a failed/interrupted attempt or changed history/context requires an explicit retry, never another automatic paid attempt that day. A new day can prepare new advice. There is no app-imposed mileage allowance or previous-run cap. See [goals and limits](GOALS.md).

Manual goals remain available without AI: distance, pace, active time, or distance within a time limit. Distance/time goals can include an optional pace range. Accepted Smart recovery intent stays with the goal, and suppresses local speed-up cues. Reaching a goal announces progress; it never automatically finishes the recording. Live AI can return silence, respects the applied target and never invents workout blocks or asks mid-run questions.

The modules are separate: recording/persistence, editable annotations/photos, pure replay/lap/coach calculations, speech/audio focus, vendor-independent `CoachAiProvider`, Anthropic transport/credentials, and goal preparation/validation. OpenAI support can be added at the provider boundary; it is not exposed as a working integration yet.

Settings, coaching, goal, activity-edit and replay-detail sheets keep Close above scrolling content. Scrolling or tapping outside does not dismiss them; use Back or Close.

## History charts

**Activities** starts with distance totals, active time and weekly distance bars for the **last four local calendar weeks**, including the current week so far. Weeks start on Monday. The All / Run / Walk / Hike filter changes the charts, not the saved-activity list or whole-history exports.

Choose one activity type to compare pace: the trend shows the latest eight eligible activities in that period, with faster pace higher on the chart. At least two usable observations are needed. Period average pace is total active time divided by total distance for activities with both values; it is not an unweighted average of their paces. A recording without usable GPS still contributes its count/active time but does not become a zero-pace result. Chart data is derived from saved samples and remains a GPS estimate.

## What is recorded

- Activity type, accepted GPS coordinates, timestamps, accuracy, available altitude and track segments.
- Active time, elapsed time, distance, average pace and kilometre splits.
- GPS speed estimates, available altitude, and filtered ascent/descent estimates calculated from the recorded samples.
- Saved runs and a recoverable unfinished session in the app's private storage.

There is no Runline account, cloud recording sync or Strava connection. Internet access loads map tiles and, only after separate setup/opt-in, Anthropic requests. GPS recording, offline speech, manual run goals and saved-run calculations work without it. Heart rate and cadence are not recorded without a compatible sensor integration.

Distance is an estimate from accepted GPS fixes. Active time includes standing still and waiting for GPS, but excludes explicit pauses and unobserved process downtime. Elapsed time includes pauses. Average pace and average speed use active time. Replay and saved-run splits use observed GPS intervals, skipping pauses and gaps over 15 seconds; their duration can differ from the active timer. **About these stats** explains these details without filling the recording screen with text.

## Maps, replay and run details

The active-run map fills the content area, with its camera centered in the visible space around the island and controls. Follow uses close zoom 18 and a gently turning 46° camera tilt when movement establishes a heading. A screen-anchored Tabler arrow indicates travel direction relative to the camera; a neutral marker remains until movement establishes a reliable heading. It is not a phone compass. The last known heading is retained while stopped, and gaps or new recording segments require a fresh heading.

Drag or pinch to explore, tap the route icon for a flat north-up overview, or the location icon to return to close following. Android Remove animations keeps follow flat/north-up and disables extra live interpolation. If there is no usable position yet, recenter explains the waiting state. The initial loading overlay hides the zoomed-out map until the first fix; it does not delay saving accepted GPS points.

Live movement is visually interpolated between consecutive safe fixes, with the arrow and following camera moving together. This adds a short display delay rather than predicting future positions. Invalid fixes, segment changes, long gaps and implausible jumps snap to the new safe position instead of inventing a connection. Pause/backgrounding stops the animation. Original samples, distance, timing and exports are unchanged by display smoothing.

The full route and marker are drawn locally, preserving segment breaks. In free-camera mode, the native arrow is projected to the displayed GPS coordinate each frame; it disappears outside the viewport instead of sticking to an edge. Road/place labels are omitted for a clearer map. A subdued trace of terrain-map heights sits behind live/replay island statistics, with a cursor at the current replay position or latest observed live sample. Missing heights, poor fixes, jumps and isolated endpoints do not get an invented cursor. Active recording and saved replay both fill the content area; the idle activity picker has no map. Map credits remain visible above or beside the information island.

MapLibre Android 11.13.5 displays worldwide EOX imagery at approximately 10-metre resolution, detailed IGN/PNOA imagery in Spain, without a road/place-label overlay. Global imagery is less detailed when enlarged; local coverage and capture dates vary. EOX imagery is licensed for noncommercial use. This personal build requires no map account or API key. Credits and source information are accessible on the map; see [map coverage, licensing and privacy](MAPS.md).

Map providers receive requests for visible tile areas and the phone's network address. Runline does not send them the raw run file, timestamps or session statistics. Maps can reveal the area being viewed. Tile failures do not stop recording or calculation, and a native map-load failure falls back to the geometric route. Recently viewed tiles may be cached; downloaded offline regions are not implemented.

Open a saved run and drag the speed slider through **1×, 5×, 10×, 30×, 60×, 120×, 240× or 480×**. The multiplier changes immediately while playback keeps its position; the separate timeline seeks through the route. Opening a run chooses the available speed whose playback duration is nearest 30 seconds; the saved GPS timeline determines that choice. A close heading-follow camera tracks the arrow, with observed position interpolation and softened turns; route-fit remains north-up. Playback updates up to 30 times per second, and pauses when the screen goes into the background. It cuts across missing intervals instead of animating an invented connection. Replay is an in-app view; it does not export an MP4 or other video.

Replay puts the map behind the same pace-first island: large min/km digits, distance, a rolling terrain profile and smaller time/elevation totals, with Play/Pause, multiplier and seeking controls. Tap its statistics or the **Details** info icon to open full activity information. A floating download icon exports GPX; Back and options remain accessible above the map.

The details sheet emphasizes total **climb and descent** and an elevation-change profile relative to the first available terrain-map height. It also includes totals and splits; terrain altitude and altitude range are secondary information. Speed uses a trailing five-second GPS estimate, with implausible running speeds omitted. Elevation totals use terrain-map heights with a 3-metre noise threshold. Partial coverage can undercount. The live/replay island separately shows uphill, flat or downhill with an approximate whole-percent mapped grade when a recent 55–90 m moving window supports it. Missing/noisy heights, weak fixes and stops show **Terrain —**. Original GPS data and exports stay unchanged; [MAPS.md](MAPS.md) explains the quality gates and observed-time cursor.

## Recording safeguards

The recorder writes independently of screen lifecycle callbacks:

- Each accepted point is appended with its session identity and timer checkpoint, protected by a checksum and flushed to storage before acknowledgement.
- Session checkpoints are updated about once per second, with two atomic copies and an independent initial session record. A recoverable journal does not depend on one readable mutable header.
- Recovery uses available valid records, separates gaps in the route, and preserves damaged source bytes before repairing an incomplete final write.
- A completed run is committed in two local copies before draft cleanup. The completed run ID prevents an interrupted cleanup from becoming a second unfinished run.
- An unreadable draft can be kept aside for recovery without clearing completed history. A failed write pauses recording and keeps already committed data.

Closing or swiping away the screen does not itself stop the recording service. After ordinary process reclamation, Android may recreate the sticky service; Runline attempts to resume only a previously active draft from the same boot when Android permits it, with a new track segment and no invented downtime. Android does not guarantee when or whether that restart occurs.

**A powered-off or battery-dead phone cannot record new locations.** Force-stop and reboot do not automatically restart recording. Reopen the app to recover committed data and choose Resume or Finish. Shutdown handling is a best-effort extra checkpoint, not the primary save mechanism. An incomplete write, storage failure, device loss or corruption of all local copies can still lose data. These are recovery safeguards, not a guarantee of uninterrupted recording or a verified claim of Strava-equivalent reliability.

See [the official-source reliability review](RELIABILITY-RESEARCH.md) for documented Strava behavior, Android limits and what remains unknown about Strava's implementation.

## Export and share

From a saved activity’s download control, choose **Downloads**, **Save to…** or **Share file**. Downloads saves GPX directly on Android 10+; Android 8/9 and failed automatic saves use the document picker. Save to… always opens that picker. Share file opens Android’s chooser, and a successful save also offers **Share**. File generation and copying run away from the UI thread.

You can select Google Drive in the chooser or document picker **when the installed/configured Drive app exposes a compatible destination**. Other compatible apps work the same way. Runline has no Drive account integration or automatic cloud sync; the selected app/provider handles its own account, network and upload. Showing the chooser is not proof that a recipient finished saving the file.

GPX names remain readable, for example `Caminata 8,42 km - 2026-11-01 10-30.gpx`. They use an edited title when present, otherwise the localized activity, followed by recorded distance and local start time. Repeated Downloads exports keep the extension last, such as `(2).gpx`. No street name is guessed. The success message shows the provider’s actual filename.

In **Activities**, tap **…** beside the activity heading, then **Share all** or **Export all**. Both include the entire saved history and offer:

- **Summary CSV:** one row per saved activity with title, notes, self-rated effort, routine, type, time, distance, pace/speed, ascent/descent and sample/segment counts, without raw route coordinates. This is the smaller option for feedback from ChatGPT or a spreadsheet.
- **All activities ZIP:** the CSV, a README explaining units/missing values, and a readable GPX for each saved activity under `activities/`.

These actions include **all saved activities**, regardless of the chart’s four-week/type filter; an unfinished recording is not included. CSV headers and numeric conventions stay stable in English with decimal points; blank values mean unavailable, and timestamps include UTC plus a local-time/time-zone column. The ZIP includes precise routes and sample timestamps. Photos remain private and are excluded from these exports. User text beginning like a spreadsheet formula gets a documented protective apostrophe in CSV; GPX preserves the XML-escaped title and notes. Neither format is a complete restorable backup of the private recording journal.

Strava remains removed. Cancelled or failed export keeps the source recordings. See [export formats, sharing and lifecycle](EXPORTS.md); previous Strava research is [archived](STRAVA.md).

## Keep your own copy

All recording copies are on the same phone. Android backup is disabled. Uninstalling Runline, clearing app storage or losing the phone removes the recordings, including local recovery copies. Export important runs to a location you control. GPX preserves route samples, timestamps and available altitude; it is not a full backup of every internal timing or recovery field.

## Coach insight

**Settings → Diagnostics** shows local coaching events and AI usage. A saved activity’s **… → Coach log** opens its timeline: what actually started speaking, the exact event time and latest GPS fix/time/accuracy, pace and estimated grade. Tap an entry for details or **Replay here** to inspect that location. Queued, expired and skipped prompts are available separately. Logs cannot reconstruct speech from older versions. Share a diagnostic JSON through Android; precise coordinates are excluded unless explicitly selected. Nothing is uploaded to a developer automatically.

**AI → Usage** shows retained request counts, reported input/output/cache tokens, model, operation, HTTP status, latency and supported-model cost estimates. This is this phone’s local history from v0.12, not an Anthropic account invoice. Opening it is free of API requests. See [diagnostics and privacy](DIAGNOSTICS.md).

When **AI → Automatic reviews** is enabled, finishing a run queues one persistent background analysis after a 90-second journal window. Android waits for connectivity and may defer execution. A notification opens a saved, compact **Win / Focus / Next session** card. The request includes this run, recorded weekly volume without double-counting, comparable recent runs, the optional overall goal and separately consented profile/notes. Failed or interrupted attempts require explicit retry; reopening does not silently charge again. Notification permission is required for the alert. Older text reviews stay readable.

Swipe horizontally between **Record** and **Activities** while idle. Maps, replay, sheets, vertical scrolling and the recording controls keep their own gestures.

In replay Details, drag across Elevation or use its slider to inspect time, distance, relative height and estimated terrain. Vertical swipes scroll the sheet. Edit and AI review are in the header’s “…” menu. See [Terrain estimates](TERRAIN.md).

### Terrain maps (0.14)

Settings → Terrain maps controls independent global elevation downloads, with finer IGN coverage in mainland Spain and the Balearic Islands. Cached areas work offline. Elevation details retain source and partial-coverage information; the island keeps only the essential stats. Missing or uncertain terrain stays unknown and produces no hill-specific cue. See [Terrain](TERRAIN.md).

## Prepare terrain before going offline

For a larger manual offline area, open **Settings → Terrain maps**. Tap **Use current location**, review the 5 km area and maximum download size, then **Download area**. Keep Runline open until **Saved area ready**. Cancel/resume retains validated files; reopening the app checks the saved area from disk. If interrupted or a file is damaged, use **Resume download** with internet. Automatic downloads prepare nearby required tiles when you travel; use manual preparation when you want a larger area saved ahead of time.

This downloads elevation data, not satellite imagery. You can always start without it. Global Mapzen coverage includes Venezuela; Spain receives additional IGN detail. Polar caps are excluded. Missing or uncertain elevation stays unknown and does not justify hill coaching. Clearing app data/uninstalling removes downloads and recordings; ordinary updates preserve them.


## About and terrain

**Settings → About** shows the installed version, measured app storage and terrain's share of that storage. A confirmed newer GitHub release adds an amber update icon; tap it to open the latest release and download the APK. Checks run asynchronously, cache successes for six hours and offer manual refresh with throttling. A failed check stays explicit. The app does not download or install updates automatically. Repository and complete Sources & licenses links stay available. Repository and release links try the installed GitHub Android app first; if it cannot handle the link, Android opens the normal external handler.

Nearby terrain downloads automatically when a usable location is available while the app is open or recording. It uses existing location permission and small tiles, with a 64 MiB disk limit; it never downloads a whole country. **Settings → Terrain maps** controls automatic downloads, optional Wi-Fi only, manual area preparation and cache clearing. Clearing terrain preserves recordings and AI settings. Missing/offline terrain does not block capture. [Terrain coverage and limits](TERRAIN.md).

## Stops and auto-pause (0.17)

Runline notices when you stand still, for example at a traffic light or to tie a shoe, after about 5 seconds. With **Settings → Auto-pause at stops** (on by default), the island shows **Stopped · auto-paused**. The timer, average pace, splits and notification then count moving time only. Recording never stops and no GPS fix is removed, so turning the setting off later restores elapsed-time numbers for every activity. Activity details list moving time, elapsed time and the stops.

## Share everything from one run (0.17)

Open an activity's file menu:
- **Download GPX**, **Share GPX** and **Save GPX to…** handle the route file.
- **Share all data (ZIP)** and **Download all data (ZIP)** add every fix with derived terrain and stop columns, the stops, the coach log, a measured summary and any saved review.

The ZIP includes your exact route; photos and your runner profile are never included.
