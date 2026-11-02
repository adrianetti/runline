# Runline

A personal Android recorder for runs, walks and hikes, with local history, satellite maps, route replay, optional spoken coaching and exportable activity data. Controls stay monochrome; real imagery keeps its colors. Source and build history live in the private [adrianetti/runline](https://github.com/adrianetti/runline) repository.

Version 0.7.0 (version code 7) is a sideloaded prototype for Android 8.0 and later. It uses Kotlin, Android Views, Material 3 and Android's GPS location provider. It targets and compiles against SDK 34; Play Store distribution is not configured. Signed release builds and bounded API 34 emulator checks are recorded below. A controlled physical-phone field and battery test remains outstanding.

## Record an activity

1. Open **Record**, choose **Run**, **Walk** or **Hike**, then tap **Start**. On first use, grant precise location and allow notifications. Location must be enabled on the phone.
2. A loading state stays over the map until a usable GPS fix arrives. Wait outdoors; the status explains disabled location or missing permission. You can lock the screen or switch apps while the independent foreground service records.
3. Tap **Pause**, then **Resume** or **Finish**. The route is saved immediately, then an optional editor offers title, notes, effort, routine type and photos. Close the editor to keep the recording without extra details; edit it later from the saved activity’s **…** menu.

The idle Record tab has an activity picker, a Start button, optional Voice coach settings and a Training plan entry. Your choice is remembered; after Android's permissions are granted, starting takes one tap. Activity type is stored with the session through pause, recovery and export. Older recordings without a stored type remain **Run**; Runline does not guess a type from their speed.

During recording, the satellite map fills the app content area. A bottom island keeps distance, active time, average pace and Pause within reach; portrait also shows climbed and descended elevation. Paused and recovered sessions offer Resume and Finish. Activities and options float above the map. Landscape moves the island to the lower left. Icons use one [MIT-licensed Tabler outline set](THIRD_PARTY_NOTICES.md), including the running figure, navigation arrow, controls and notification glyph.

The app follows the **phone’s language** by default, with English fallback and Spanish support. **Language / Idioma** offers Phone language, English and Español. Updating from v0.5 clears its forced-Spanish setting once; later explicit language choices are retained. Controls, messages, recording notifications and exported activity names follow the selected app language. Android permission dialogs and third-party document providers follow their own language settings.

The shield control, or **Recording protection** in the active-run menu, shows notification and battery-optimization status with shortcuts to Android settings. These checks help identify restrictive settings; they do not certify future GPS continuity on a particular phone.

The ongoing notification shows distance, average pace and a live active-time chronometer, with GPS status when expanded. Tap **Pause**, then **Resume** or **Finish**; the timer freezes while paused. Tapping the notification opens the current recording. Allow lock-screen notifications, including silent notifications where the phone has a separate setting, to use the controls there. Samsung may show icons until expanded, and its Now bar supports selected apps; Runline does not claim Now bar integration or pose as a music player. See [Android notification visibility](https://developer.android.com/develop/ui/compose/notifications/create-notification) and [Samsung Now bar guidance](https://www.samsung.com/my/support/mobile-devices/how-to-use-the-now-bar-on-the-lock-screen-of-your-samsung-galaxy-device/).

## Optional voice coach

Voice coaching starts **off**. Open **Voice coach** before starting, or from the recording **…** menu while an activity is underway. Enable spoken guidance, optionally announce every kilometre, and choose progress only, distance, target pace, or distance within a time limit. Goals apply to the activity’s total distance and **active time**; explicit pauses do not consume the time limit. Disabling guidance remains possible even while an unfinished goal edit is invalid.

Use **Test voice** before heading out. Coaching uses an installed offline English or Spanish Android text-to-speech voice for the app language; it does not generate or download a person’s voice. Voice settings opens the phone’s TTS settings when available. If a suitable voice is missing, recording still works and the settings screen explains how to add one.

The recorder service owns the coach, so leaving the screen does not intentionally stop it. Brief prompts can announce kilometre progress, one kilometre remaining, goal completion, or sustained pace feedback. Pace, encouragement, terrain and lap feedback each have a switch and default on inside the explicit voice-coach opt-in. A rotating bilingual phrase library avoids immediate repetition; prompts remain spaced out. Climb/descent context suppresses speed-up reminders, including when terrain announcements are disabled. Matching repeated loops can produce estimated lap and four-section comparisons, without named-place guesses. Weak/stale GPS, pauses and recovery do not produce a backlog of old cues. Pace guidance waits for sufficient recent movement and uses tolerance/cooldown rather than speaking on every GPS change. Distance-plus-time guidance uses the target’s average required pace; it does not calculate a changing catch-up pace.

Before speech, Runline requests temporary audio focus that allows music to lower. It releases focus after speech, failure, timeout or interruption and never changes the phone’s global media volume directly. Actual ducking, Bluetooth routing and interruptions depend on Android, the TTS engine and the other player. The owner reported successful voice coaching on their phone with v0.6; controlled v0.7 Spotify/headphone and Samsung field checks remain outstanding. Recording is independent of successful speech. See [coaching behavior and limits](docs/COACHING.md).

## Editable activity details

Finished and older recordings support a title, notes, optional effort from 1–10, activity type and routine (easy, long, tempo, intervals, recovery, race or other). Up to six photos are copied into private app storage through Android’s picker, downsampled and stripped of EXIF. The details sheet shows photo previews and stored annotations. These records live separately from GPS samples and use checksummed atomic copies; annotation failure never replaces the original route. Photos are not included in CSV/GPX/ZIP or AI requests.

## Optional Anthropic and training plans

**… → AI connections** accepts your own Anthropic key, encrypted with an Android Keystore key on the phone. No key ships in the APK, enters exported files or needs to be sent to the developer. All AI features start off. Enable only the features you want: live advice, automatic post-run review and automatic plan updates. Live speech also requires Voice coach enabled. Model IDs are editable; current defaults are Haiku for live advice and Sonnet for deeper reviews/plans. **Test connection** checks model access, not the quality of a generated review.

Live requests use a configurable 3–10 minute active-time interval and a 24-attempt cap per activity. They receive activity summaries, splits, estimated laps and self-reported effort/routine. Titles and notes require an additional sharing switch. Raw coordinates and photos are never sent. Requests spend the user’s Anthropic API credits; offline recording and coaching remain independent of network/model failures. Late live responses are dropped after pause/stop/disable. See [AI setup, data flow and limits](docs/AI.md).

**Training plan** starts with an explicit distance goal (21.1 km is prefilled), target date, days per week and usual weekly distance. A conservative local schedule works without a key. Claude can personalize and adapt future sessions inside validated limits; it cannot rewrite completed sessions, add missed distance to tomorrow or guarantee readiness. Session states can be changed manually. Optional automatic adaptation reconciles completed/missed sessions when the app opens or an activity finishes, with a daily attempt cap. A review/error leaves the prior plan available. The displayed explanation records what changed; use the goal editor to change the target rather than expecting a model to silently change it.

The modules are separate: recording/persistence, editable annotations/photos, pure replay/lap/coach calculations, speech/audio focus, vendor-independent `CoachAiProvider`, Anthropic transport/credentials, and training-plan storage/validation. OpenAI support can be added at the provider boundary; it is not exposed as a working integration yet.

## History charts

**Activities** starts with distance totals, active time and weekly distance bars for the **last four local calendar weeks**, including the current week so far. Weeks start on Monday. The All / Run / Walk / Hike filter changes the charts, not the saved-activity list or whole-history exports.

Choose one activity type to compare pace: the trend shows the latest eight eligible activities in that period, with faster pace higher on the chart. At least two usable observations are needed. Period average pace is total active time divided by total distance for activities with both values; it is not an unweighted average of their paces. A recording without usable GPS still contributes its count/active time but does not become a zero-pace result. Chart data is derived from saved samples and remains a GPS estimate.

## What is recorded

- Activity type, accepted GPS coordinates, timestamps, accuracy, available altitude and track segments.
- Active time, elapsed time, distance, average pace and kilometre splits.
- GPS speed estimates, available altitude, and filtered ascent/descent estimates calculated from the recorded samples.
- Saved runs and a recoverable unfinished session in the app's private storage.

There is no Runline account, cloud recording sync or Strava connection. Internet access loads map tiles and, only after separate setup/opt-in, Anthropic requests. GPS recording, offline speech, local training schedules and saved-run calculations work without it. Heart rate and cadence are not recorded without a compatible sensor integration.

Distance is an estimate from accepted GPS fixes. Active time includes standing still and waiting for GPS, but excludes explicit pauses and unobserved process downtime. Elapsed time includes pauses. Average pace and average speed use active time. Replay and saved-run splits use observed GPS intervals, skipping pauses and gaps over 15 seconds; their duration can differ from the active timer. **About these stats** explains these details without filling the recording screen with text.

## Maps, replay and run details

The active-run map fills the content area, with its camera centered in the visible space around the island and controls. Follow uses close zoom 18 and keeps the map north-up. A Tabler arrow points in the direction of accepted GPS movement; a neutral marker remains until movement establishes a reliable heading. It is not a phone compass. The last known heading is retained while stopped, and gaps or new recording segments require a fresh heading.

Drag or pinch to explore, tap the route icon to fit the activity, or the location icon to return to close following. If there is no usable position yet, recenter explains the waiting state. The initial loading overlay hides the zoomed-out map until the first fix; it does not delay saving accepted GPS points.

Live movement is visually interpolated between consecutive safe fixes, with the arrow and following camera moving together. This adds a short display delay rather than predicting future positions. Invalid fixes, segment changes, long gaps and implausible jumps snap to the new safe position instead of inventing a connection. Pause/backgrounding stops the animation. Original samples, distance, timing and exports are unchanged by display smoothing.

The route and marker are drawn locally, preserving segment breaks. Active recording and saved replay both fill the content area; the idle activity picker has no map. Map credits remain visible above or beside the information island.

MapLibre Android 11.13.5 displays worldwide EOX imagery at approximately 10-metre resolution, detailed IGN/PNOA imagery in Spain, and road/place labels. Global imagery is less detailed when enlarged; local coverage and capture dates vary. EOX imagery is licensed for noncommercial use. This personal build requires no map account or API key. Credits and source information are accessible on the map; see [map coverage, licensing and privacy](docs/MAPS.md).

Map providers receive requests for visible tile areas and the phone's network address. Runline does not send them the raw run file, timestamps or session statistics. Maps can reveal the area being viewed. Tile failures do not stop recording or calculation, and a native map-load failure falls back to the geometric route. Recently viewed tiles may be cached; downloaded offline regions are not implemented.

Open a saved run to play or seek through its route at **1×, 5×, 10×, 30×, 60×, 120×, 240× or 480×**. Opening a run chooses the available speed whose playback duration is nearest 30 seconds; the saved GPS timeline determines that choice. A close, north-up camera follows a heading arrow, with observed position interpolation and softened turns. Playback updates up to 30 times per second, and pauses when the screen goes into the background. It cuts across missing intervals instead of animating an invented connection. Replay is an in-app view; it does not export an MP4 or other video.

Replay puts the map behind a compact bottom island with distance, speed, climbed/descended elevation, Play/Pause, speed selection, seeking and pace. Tap its statistics or **Details** to open the full activity information. A floating download icon exports GPX; Back and options remain accessible above the map.

The details sheet emphasizes total **climb and descent** and an elevation-change profile relative to the first available GPS height. It also includes totals and splits; raw GPS altitude and altitude range are secondary information. Speed uses a trailing five-second GPS estimate, with implausible running speeds omitted. Elevation change uses raw GPS heights with a 3-metre noise threshold: it is not terrain-corrected, and slope/grade percentages are not calculated. Missing values show **—**, and the profile preserves missing-data and segment gaps. These are estimates, not sensor-grade measurements.

## Recording safeguards

The recorder writes independently of screen lifecycle callbacks:

- Each accepted point is appended with its session identity and timer checkpoint, protected by a checksum and flushed to storage before acknowledgement.
- Session checkpoints are updated about once per second, with two atomic copies and an independent initial session record. A recoverable journal does not depend on one readable mutable header.
- Recovery uses available valid records, separates gaps in the route, and preserves damaged source bytes before repairing an incomplete final write.
- A completed run is committed in two local copies before draft cleanup. The completed run ID prevents an interrupted cleanup from becoming a second unfinished run.
- An unreadable draft can be kept aside for recovery without clearing completed history. A failed write pauses recording and keeps already committed data.

Closing or swiping away the screen does not itself stop the recording service. After ordinary process reclamation, Android may recreate the sticky service; Runline attempts to resume only a previously active draft from the same boot when Android permits it, with a new track segment and no invented downtime. Android does not guarantee when or whether that restart occurs.

**A powered-off or battery-dead phone cannot record new locations.** Force-stop and reboot do not automatically restart recording. Reopen the app to recover committed data and choose Resume or Finish. Shutdown handling is a best-effort extra checkpoint, not the primary save mechanism. An incomplete write, storage failure, device loss or corruption of all local copies can still lose data. These are recovery safeguards, not a guarantee of uninterrupted recording or a verified claim of Strava-equivalent reliability.

See [the official-source reliability review](docs/RELIABILITY-RESEARCH.md) for documented Strava behavior, Android limits and what remains unknown about Strava's implementation.

## Export and share

From a saved activity’s download control, choose **Downloads**, **Save to…** or **Share file**. Downloads saves GPX directly on Android 10+; Android 8/9 and failed automatic saves use the document picker. Save to… always opens that picker. Share file opens Android’s chooser, and a successful save also offers **Share**. File generation and copying run away from the UI thread.

You can select Google Drive in the chooser or document picker **when the installed/configured Drive app exposes a compatible destination**. Other compatible apps work the same way. Runline has no Drive account integration or automatic cloud sync; the selected app/provider handles its own account, network and upload. Showing the chooser is not proof that a recipient finished saving the file.

GPX names remain readable, for example `Caminata 8,42 km - 2026-11-01 10-30.gpx`. They use an edited title when present, otherwise the localized activity, followed by recorded distance and local start time. Repeated Downloads exports keep the extension last, such as `(2).gpx`. No street name is guessed. The success message shows the provider’s actual filename.

In **Activities**, the **Share** and **Export** buttons under the activity heading include the entire saved history and offer:

- **Summary CSV:** one row per saved activity with title, notes, self-rated effort, routine, type, time, distance, pace/speed, ascent/descent and sample/segment counts, without raw route coordinates. This is the smaller option for feedback from ChatGPT or a spreadsheet.
- **All activities ZIP:** the CSV, a README explaining units/missing values, and a readable GPX for each saved activity under `activities/`.

These actions include **all saved activities**, regardless of the chart’s four-week/type filter; an unfinished recording is not included. CSV headers and numeric conventions stay stable in English with decimal points; blank values mean unavailable, and timestamps include UTC plus a local-time/time-zone column. The ZIP includes precise routes and sample timestamps. Photos remain private and are excluded from these exports. User text beginning like a spreadsheet formula gets a documented protective apostrophe in CSV; GPX preserves the XML-escaped title and notes. Neither format is a complete restorable backup of the private recording journal.

Strava remains removed. Cancelled or failed export keeps the source recordings. See [export formats, sharing and lifecycle](docs/EXPORTS.md); previous Strava research is [archived](docs/STRAVA.md).

## Keep your own copy

All recording copies are on the same phone. Android backup is disabled. Uninstalling Runline, clearing app storage or losing the phone removes the recordings, including local recovery copies. Export important runs to a location you control. GPX preserves route samples, timestamps and available altitude; it is not a full backup of every internal timing or recovery field.

## Build and install

Use JDK 17, Android SDK Platform 34 and Build Tools 34.0.0. The project pins AGP 8.5.2, Kotlin 2.0.20 and Gradle 8.7 for MapLibre compatibility. Set `ANDROID_HOME`, or create an untracked `local.properties` containing `sdk.dir=/absolute/path/to/Android/sdk`.

```sh
./gradlew testDebugUnitTest assembleDebug assembleRelease lintDebug
```

The signed APK is available on the private [v0.7.0 release page](https://github.com/adrianetti/runline/releases/tag/v0.7.0). Its packaged local path is `dist/Runline-0.7.0.apk`. Install it with USB debugging enabled:

```sh
adb install -r dist/Runline-0.7.0.apk
```

Or copy the APK to your phone and open it, allowing installation from the app you use to open the file. It is privately distributed, not a Play Store release.

Version 0.7 retains the dedicated per-app signing key introduced in v0.2, allowing an in-place update that keeps private recordings. The earlier v0.1 APK used a different development key. If v0.1 is already installed, export any runs before uninstalling it to migrate; Android cannot install a differently signed update over it. Uninstalling removes Runline's private data.

GitHub Actions checks pushes and pull requests to `main`. Main-branch builds use repository signing secrets and publish a signed release APK plus check reports as private workflow artifacts. Pull requests do not receive signing secrets. The private key and its local configuration stay outside Git; a fresh clone can build a development APK but does not receive the private release key.

For local development, the command above produces `app/build/outputs/apk/debug/app-debug.apk`. `assembleRelease` produces a signed release only when the authorized signing configuration is available. Keep the app signing key for future updates; do not commit it or share its password.

To verify the pinned icon resources without network access:

```sh
python3 tools/import_icons.py --check
```

## Version 0.7 verification

Checks recorded on 2 November 2026 on the API 34 emulator, using disposable synthetic activities and a dummy key:

- **137 JVM tests passed**. Lint reported **zero errors and 54 warnings**; all **27 pinned icons** and **401 English/Spanish string, plural and array resources** passed source/parity checks.
- Editing the two-hour fixture saved **Park laps**, its notes, **effort 6/10**, **Easy** routine and **one private photo**. The photo-picker flow survived rotation, and the saved details/photo survived a debug update. The original recording file’s hash stayed unchanged after the metadata edit.
- Its exported GPX retained **7,201 points and two segments**. Parsed title and notes matched the saved annotations.
- A local **21.1 km** plan targeting **1 March 2027** contained **51 sessions**. The plan persisted, including a manually marked **Skipped** session.
- A disposable dummy API key was stored encrypted, settings were reopened/rotated, and the key was removed. Empty-key validation worked locally. **No HTTP request or paid Anthropic call was made** during these checks; they do not validate a real account, model response or generated plan.

- Finishing a brief synthetic Walk saved the route before automatically opening the optional editor. Closing without edits kept it, and portrait/landscape recreation did not reopen the editor or duplicate the run.
- Bounded English/Spanish review covered activity details, private photos, coaching, AI settings, the training plan and history actions, including 1.3× text, dark mode and landscape. Spanish Share/Export actions remained equal-sized, stacking at larger text. No material clipping was found in the inspected views.
- The two-hour fixture automatically selected **240×**. Accelerated replay kept its arrow centered on the route through sampled turns; pan and recenter worked. A native follow marker avoids asynchronous map-symbol lag. These are bounded visual checks, not a frame-rate benchmark.

- The exact **0.7.0 / version code 7** packaged release installed in place. A new **21-column CSV** contained all **19 saved activity IDs**, including the new finish-flow test and edited metadata. The saved photo and local plan, including its Skipped status, remained visible after updating.
- APK SHA-256: `41b138be24f517529e8c7ff099766db082cfd714ae0702758bc01deb43d81798`. The signing certificate matched the stable app key: `fc4cf764f78b745c431a632e5769348ec6488e04b4ca92515a9f589564f900ab`.

These results cover the listed checks only. Real Anthropic requests, physical v0.7 Samsung/Spotify/headphone behavior and field/battery performance remain unverified. [docs/TESTING.md](docs/TESTING.md) distinguishes checklist cases from recorded results.

## Version 0.6 verification

- Final integrated debug/release builds succeeded. All **96 JVM tests passed**, with zero failures or errors; lint reported **zero errors and 37 warnings**. All **24 pinned icons** matched their sources. English/Spanish checks confirmed parity for **270 string/plural resources** and their format placeholders.
- On API 34, a signed debug update retained v0.5 history and cleared its forced locale to follow the English phone setting; Record showed Voice coach Off. Bounded review of **11 screenshots**, including Spanish, larger text and landscape, found no material UI issues in the inspected views.
- The exported archive contained **17 CSV rows, 17 GPX files and README.txt**. GPX point and segment comparisons matched the saved activities; the standalone CSV also had 17 rows and no latitude/longitude columns. Direct Downloads saving worked. The post-save share chooser listed Drive; no Drive upload was performed. Save to… completed after rotating while the CSV document picker was open.
- Coaching rejected an invalid **5 km in 2 minutes** goal and allowed disabling despite that invalid draft. English Test voice reached its TTS start callback using an installed Google offline voice. Spanish correctly showed the missing-offline-voice message because only English voice data was installed. Audio logs recorded a transient MAY_DUCK speech/navigation focus request and release five seconds later; this does not establish audible headphone playback or music ducking.

- With a **0.1 km distance goal**, synthetic movement crossed the goal while the Activity was backgrounded. Audio logs recorded the automatic speech focus request at **23:36:05** and its release at **23:36:09**. Notification Pause/Finish saved the activity with **32 accepted fixes**; the emulator's initial Vancouver fix and Granada test movement remained separated. The coaching draft also survived landscape/portrait rotation. This tests the background cue/focus path, not audible music ducking.
- The new activity's GPX Share file action opened the native chooser with Drive and a readable **Walk 0.22 km** filename; no account transfer was attempted.

- The exact packaged **0.6.0 / version code 6** release installed in place and opened successfully. A fresh CSV export retained all **18 activity IDs**, including the two-hour walk fixture and live coaching test. The release signing certificate matched the stable per-app key: `fc4cf764f78b745c431a632e5769348ec6488e04b4ca92515a9f589564f900ab`. APK SHA-256: `633c91a592ca950d596a4e116342470cfb49f1a6e641c24429b1bd5058fe1447`.

These are bounded local build/emulator checks. Physical Spotify/headphone behavior, a Google Drive account upload and Samsung field/battery behavior remain unverified. See [docs/TESTING.md](docs/TESTING.md) for the checklist and detailed evidence.

## Version 0.5 verification

Historical results, checked locally on 1 November 2026:

- Final signed debug/release builds succeeded. All **71 JVM tests passed** with zero failures; lint reported **zero errors and 28 warnings**. All **23 generated icons** matched the pinned sources. English/Spanish checks confirmed parity for **196 string/plural resources** and their format placeholders.
- The exact packaged **0.5.0 / version code 5** release installed on the API 34 emulator and retained older history, the two-hour fixture and a newly recorded Walk. Its signing certificate matched the per-app key used since v0.2. A preceding debug update from v0.4 also retained existing recordings.
- A **7,201-point, two-hour walk fixture**, including a pause and missing altitude, exercised fullscreen replay at **1×**, seeking, Play/Pause and elevation details. English/Spanish switching and rotation retained position **1:00:19** and **1×** speed. Bounded light/dark, 1.3× font, portrait and landscape review found no material clipping in the inspected views.
- Primary export saved directly to Downloads with localized **Caminata 8,37 km** and **Walk 8.37 km** names plus start date/time. Parsed GPX retained all **7,201 points**, **two segments** and walking type. Export opened no Strava screen or browser. The success message was confirmed monochrome with the full filename visible. A separate export of the newly recorded Walk matched all **13 stored fixes and three segments**, including its activity type.
- The initial location-loading state was captured. Synthetic eastward movement showed the heading arrow centered in the available map area; panning disengaged follow and recenter restored it. Intermediate recording frames showed continuous display movement between fixes. These visual checks do not establish field GPS accuracy.
- With the Activity absent, notification **Resume**, **Pause** and **Finish** worked and saved a Walk with **13 accepted GPS fixes in three segments**. Changing Android's per-app language while the service was paused/backgrounded immediately updated the notification between Spanish and English.

The packaged `Runline-0.5.0.apk` SHA-256 is `e8b8010bc7c6b9f6b3936d47922aed1e090848bf614a8ae25c327129ef43e84d`. Its signing-certificate SHA-256 is `fc4cf764f78b745c431a632e5769348ec6488e04b4ca92515a9f589564f900ab`.

These are bounded local build/emulator checks, not certification of every device or failure path. Physical Samsung field/battery behavior, secure-lock-screen actions, Android 8/9 picker runtime, all export-failure paths and 16 KB page-size devices remain unverified. See [the full checklist and evidence](docs/TESTING.md). CI status is tracked separately in the private [Android workflow](https://github.com/adrianetti/runline/actions/workflows/android.yml).

## Historical version 0.4 verification

Checked locally on 1 November 2026:

- Signed debug/release builds succeeded. All 53 JVM tests passed: the previous 38 calculation/persistence/replay tests, nine GPS-course tests and six notification-action guard tests. Lint reported zero errors and 28 warnings; all 23 generated icon resources matched their pinned sources.
- The API 34 emulator accepted the signed v0.4 update over v0.3 and retained older recordings. An unfinished draft survived an additional update and offered explicit recovery. The exact packaged release APK then installed and opened successfully, retaining the new 0.14 km test run and older history; its signature matched the same per-app key used since v0.2.
- Synthetic GPS movement east, then north, produced the correct heading arrow and close centering. Panning disengaged follow; the location control restored it. Bounded screenshot review covered light/dark, 1.3× font scale and landscape without material clipping. The final paused-state text and lock-screen settings guidance were also visible without clipping.
- Notification Pause, Resume and Finish worked with the app screen absent. Pause froze the timer; Finish removed the notification and saved 45 fixes across three segments. Tapping the notification from history opened the active run; returning to history and rotating stayed in history.
- Lock-screen statistics became visible after allowing silent lock-screen notifications. Expanding the notification opened the shade, where Pause worked. This was an emulator without a PIN; secure-lock-screen and Samsung Now bar behavior remain unverified.
- Saved-run replay opened at 1×. Playing for three seconds advanced to 00:03, and Pause stopped playback. The free Strava handoff was unchanged; its historical official-source audit is archived in [docs/STRAVA.md](docs/STRAVA.md).

The packaged `Runline-0.4.0.apk` SHA-256 is `98a9b82e5106f12810281613435004a7580fe8b226f0174c896b779f43fec786`. CI results are tracked in the private [Android workflow](https://github.com/adrianetti/runline/actions/workflows/android.yml). No physical Samsung field/battery test, secure lock-screen action test, Android 8/9 picker runtime check, 16 KB page-size device test or Strava publication was performed. These results cover the listed build/emulator cases, not every device or failure path.

## Historical version 0.3 verification

Checked locally on 1 November 2026:

- Signed debug and release builds, `testDebugUnitTest` and `lintDebug` succeeded. All 38 JVM tests passed: 10 metrics/GPX, 14 persistence and 14 replay tests, including the peak-speed regression. Lint reported zero errors.
- The API 34 emulator installed v0.3 over the signed v0.2 app and retained the existing runs. The final signed release also opened after installation and retained both prior v0.2 recordings and the v0.3 synthetic/offline runs.
- Actual IGN satellite imagery rendered with the route and moving marker. Route fitting was corrected for display density. Playback at 30×, selecting 60×, seeking halfway, Pause, background/return and position restoration after rotation were exercised.
- Light/dark themes, 1.3× font scale and landscape were reviewed. Compact map heights were added; no other material layout issue was found in those screens. The final sharing dialog was confirmed monochrome; the map-credit target measured 48dp.
- With Wi-Fi and cellular disabled, GPS recording persisted seven synthetic samples while the app was backgrounded and the screen locked. Force-stop/reopen offered **Run recovered**; Finish saved the recovered run.
- The Android 10+ Strava path saved a valid GPX with 121 samples in two segments directly to Downloads, then opened the official browser uploader without a file-save picker. No Strava login, upload or publication was performed.

CI results are tracked separately in the private [Android workflow](https://github.com/adrianetti/runline/actions/workflows/android.yml). The untested v0.3 cases included a controlled physical-phone field run, hardware battery exhaustion, the Android 8/9 picker path, 16 KB page-size devices, and the user's actual Strava browser flow. The [current device checklist](docs/TESTING.md) covers the retained features; the Strava handoff is now archived. Offline imagery is not guaranteed. These specific build/emulator results do not certify GPS accuracy, battery life, every device's background behavior or all failure paths.

## Historical version 0.2 verification

Checked locally on 31 October 2026:

- `assembleDebug`, `assembleRelease`, `testDebugUnitTest` and `lintDebug` succeeded. All 24 tests passed: 10 calculation/GPX tests and 14 persistence failure-injection tests. Lint reported zero errors.
- Android API 34 emulator: a synthetic run kept recording GPS after its UI was swiped away.
- Killing the recording process with `run-as … kill -9` caused Android to recreate the sticky service; capture resumed in a new segment.
- Force-stop followed by reopening preserved the draft and offered explicit recovery. The active timer stayed frozen during the interruption rather than adding downtime.
- Reboot followed by reopening retained the recorded points and recovered the run paused. Finish wrote the completed session.

The [initial clean GitHub Actions build](https://github.com/adrianetti/runline/actions/runs/41228763717) also passed. The Strava handoff saved a valid GPX containing all 15 test samples in three separate track segments, then opened the official uploader URL. The app did not publish an activity. The recording, recovery, history, run detail and sharing screens were reviewed in light mode and in dark mode at 1.3× font scale, with no material clipping found. A misleading no-route message was corrected and the local build/checks rerun. The signed release also installed as an update while retaining the completed test run.

No physical-phone field test, hardware battery-exhaustion test or Strava publication has been performed. Before using Runline as your only recorder, complete the [device checklist](docs/TESTING.md), especially a screen-locked outdoor recording on your own phone. These emulator and file tests do not establish real GPS accuracy, battery consumption, device-specific background behavior or uninterrupted capture while a phone is powered off.
