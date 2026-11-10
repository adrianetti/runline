# Release history

Historical source changes, artifact checksums and bounded verification evidence moved from the README. These records describe the versions tested, not a blanket verification of the current app. See [TESTING.md](TESTING.md) for the current checklist and versioned evidence.

## Version 0.10 source changes

- Replaced the calendar planner with one optional overall AI goal and explicit pre-run Smart goal preparation.
- Smart suggestions use measured history, weekly load and saved effort, with validated distance/pace/time limits and an explicit Use/Adjust decision. Errors and stale results leave the existing target alone.
- Added manual active-time goals and optional pace bands, retaining recovery intent for local and AI coaching. Live advice remains brief, context-aware and optional.
- Added native loading indicators for Smart preparation and completed-run reviews. Opening or rotating never starts a paid Smart request.

## Version 0.10 verification

Recorded on 3 November 2026 with synthetic history and an API 34 emulator:

- Signed debug/release builds passed **233 JVM tests**, with zero failures/errors. Lint reported **zero errors and 106 warnings**. All **464 bilingual resources** and **34 pinned icons** passed checks.
- Native checks covered AI-off setup, the overall-goal field and rotation, manual goal choices, loading through rotation, distance/time results, Adjust/Save, rest and error states. **4.0 km with a 6:00–7:00 /km band and Easy intent** survived Adjust and rotation; **25 minutes with that band** applied to the Record screen. Rest/error offered no Apply action and kept existing settings. A temporary external harness injected those response states without contacting a provider; its invalid credential sentinel and preference backups were removed afterward.
- Independent screenshot review found no material clipping in the inspected English/Spanish, dark, 1.3× text and landscape screens. Loading used the native indeterminate indicator, disabled repeat generation and retained Close. These are bounded captures, not a general animation performance benchmark.
- The exact signed release installed in place and retained **all 49 inspected original files byte-for-byte**, including **22 saved activities**, recovery files, annotations, a photo and the old plan. Only the new overall-goal store is used; the old calendar file is not rewritten. Coaching preferences were restored after fixture checks.
- Local security checks passed: **22 scanner regression tests**, Android exposure boundaries, workflow lint and **seven Semgrep rules across 71 Kotlin files**. Dependency audit covered **242 Maven versions / 39 configurations** with zero reported OSV findings. GitHub check and merge evidence is recorded on the release PR. The separate upstream build-tool limitation remains documented in [CHECKS.md](CHECKS.md).
- Exact artifact: **`dist/Runline-0.10.0.apk`**, **0.10.0 / code 10**, SHA-256 **`d8a04c9a956ce5890e2b3797ced5b43f42dfce3ca4aff30878338923a7f36d6f`**. Its certificate matches the stable per-app signing key used since v0.2.

No paid Anthropic generation, prospective validation of Smart goal advice, physical Samsung test or new music-player test was performed. Prior recording/audio evidence retains its versioned scope.

## Version 0.9 source changes

- Optional runner profile with separate AI-sharing consent; shared goal/plan/history/terrain context and feedback that actually started speaking.
- Shared local/AI speech priorities and quiet intervals, slower repeat pace reminders and recovery time after estimated hills.
- Native effort slider with Unrated and 1–10 self-ratings, plus fixed Save.
- Estimated terrain icon/grade, observed-time elevation cursor and a native projected marker while exploring the map.
- **All AI features** selects live advice, automatic reviews and automatic plan updates together while keeping note/profile sharing separate.

## Version 0.9 verification

Recorded on 2 November 2026 using JDK 17 and the API 34 emulator with disposable synthetic activities:

- Final debug/release builds passed **197 JVM tests with zero failures**. Lint reported **zero errors and 117 warnings**. All **494 English/Spanish resources** and **34 pinned Tabler icons** passed verification.
- The **five Android boundary tests** and **17 offline dependency-audit tests** passed. The resolved inventory contained **242 Maven component versions across 39 configurations**, with **zero OSV findings**. Strict Semgrep checked **65 Kotlin files against seven local rules**, with **zero findings**; actionlint passed. The separate [Gradle distribution limitation](../tools/security/README.md#dependency-advisories-and-toolchain-limits) still applies.
- The first in-place v0.9 candidate update retained all **47 inspected files byte-for-byte**, including **21 saved activities**, the private photo and training plan. The exact final release APK was also installed in place; later fixture edits are separate from that initial byte-for-byte comparison.
- **All AI features** selected the three features and enabled the module; turning one feature off updated the aggregate switch. Rotation preserved the choices, note sharing stayed off, and Save rejected missing-key setup. No real Anthropic requests were made.
- An unsaved runner-profile draft survived rotation, saved with **Include profile in AI off**, and was removed with **Clear profile**. Effort **8/10** survived rotation and saving; **Unrated** saved as unset, then the fixture's original **6/10** was restored.
- The two-hour recording opened at **240×**. Native replay rendered the free-camera arrow, estimated terrain label and elevation cursor. These are bounded visual checks, not a frame-rate or field-accuracy benchmark.
- Background GPS capture, force-stop recovery, resume and notification Pause/Finish saved one additional activity with **52 points in three segments**, leaving **22 saved activities** and no active draft. All **45 unedited original files** remained byte-identical; only the two deliberately edited effort metadata copies changed.
- The installed system voice reported ready; audio diagnostics recorded transient navigation-speech focus requests and matching releases for both preview and a live cue. No actual music player or headphones were used.
- English/Spanish, dark mode, 1.3× text and landscape captures exposed a replay-header overlap; applying island limits before child measurement fixed it. The final landscape capture keeps Back/options clear, with the information area scrollable and playback controls fixed.
- The exact **0.9.0 / version code 9** package is `dist/Runline-0.9.0.apk`. APK SHA-256: `97eaed4ae16c43d8d61a94b21cfed742ac2affac4037c26d7e4d73dba2369a77`. Signing-certificate SHA-256: `fc4cf764f78b745c431a632e5769348ec6488e04b4ca92515a9f589564f900ab`, matching the existing app key.

CI and release results are recorded in the private [pull requests](https://github.com/adrianetti/runline/pulls) and [Releases](https://github.com/adrianetti/runline/releases). AI request/response tests use fake transports; they do not establish real model quality or account access. Physical Samsung field/battery, music/headphone ducking and unlisted failure cases remain unverified. [docs/TESTING.md](TESTING.md#version-09-recorded-checks) separates completed evidence from the broader checklist. Historical v0.8 evidence follows unchanged.

## Version 0.8 source changes

- One Settings home; optional AI setup uses provider/model choices, while unconfigured AI actions stay hidden.
- Goal presets, native day/month choices and a history-based training suggestion replace raw date and required baseline entry.
- Fixed sheet headers keep Close reachable while scrolling; local plans omit repeated explanations.
- Tilted heading-follow, clear satellite imagery and GPS elevation traces refine recording/replay islands.

## Version 0.8 verification

Recorded on 2 November 2026 using the API 34 emulator and disposable synthetic activities:

- Integrated debug/release builds passed with **158 JVM tests and zero failures**. Lint reported **zero errors and 117 warnings**. All **466 English/Spanish resources** and **29 pinned Tabler icons** passed verification.
- The **five Android boundary tests** and **17 dependency-audit tests** passed. The resolved project graph contained **242 Maven component versions across 39 configurations**, with **zero OSV findings**. The final source scan checked **55 Kotlin files against seven local security rules**, with **zero findings**. The separate [Gradle distribution limitation](../tools/security/README.md#dependency-advisories-and-toolchain-limits) still applies.
- The in-place update retained **45 saved files byte-for-byte**, including **20 activities**, metadata, a private photo and the existing version 1 training plan.
- AI model choices saved while AI remained off. Disabling AI also succeeded with a malformed draft key; the dummy credential was removed without making requests. Repeated fast sheet swipes kept sheets open, Close remained fixed, and Save stayed visible above the keyboard. The month picker survived rotation and preserved March after switching from an exact March 1 date; Personal progress hid date inputs.
- Background synthetic GPS capture and force-stop recovery finished one additional recording, leaving **21 saved activities**.
- The two-hour fixture selected **240×**. Native replay showed the satellite map without labels, heading-follow and the elevation island. Spanish details/options hid unavailable AI actions, and the GPX export choices remained available.

- A fresh **Personal progress** plan used recorded **7.4 km/week** and three days/week automatically, with an eight-week rolling window and no race date. The previous test plan was restored afterward.
- The exact signed **0.8.0 / version code 8** package installed in place and retained **21 saved activities** and the previous plan, including its Skipped state. APK SHA-256: `be0eaa63018f1d29aa4598d57b63ae3d5529e9559b384eba5cb7e7a769c8b48c`; stable signing-certificate SHA-256: `fc4cf764f78b745c431a632e5769348ec6488e04b4ca92515a9f589564f900ab`.

Independent review of the inspected dark-mode, 1.3× text and landscape captures confirmed the fixes and found no additional control or layout issues. No real Anthropic generation or physical-phone field, battery or headphone test was performed. These results cover the listed cases only; [docs/TESTING.md](TESTING.md#version-08-recorded-checks) records the bounded scope. Earlier results below apply only to their stated versions.

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

These results cover the listed checks only. Real Anthropic requests, physical v0.7 Samsung/Spotify/headphone behavior and field/battery performance remain unverified. [docs/TESTING.md](TESTING.md) distinguishes checklist cases from recorded results.

## Version 0.6 verification

- Final integrated debug/release builds succeeded. All **96 JVM tests passed**, with zero failures or errors; lint reported **zero errors and 37 warnings**. All **24 pinned icons** matched their sources. English/Spanish checks confirmed parity for **270 string/plural resources** and their format placeholders.
- On API 34, a signed debug update retained v0.5 history and cleared its forced locale to follow the English phone setting; Record showed Voice coach Off. Bounded review of **11 screenshots**, including Spanish, larger text and landscape, found no material UI issues in the inspected views.
- The exported archive contained **17 CSV rows, 17 GPX files and README.txt**. GPX point and segment comparisons matched the saved activities; the standalone CSV also had 17 rows and no latitude/longitude columns. Direct Downloads saving worked. The post-save share chooser listed Drive; no Drive upload was performed. Save to… completed after rotating while the CSV document picker was open.
- Coaching rejected an invalid **5 km in 2 minutes** goal and allowed disabling despite that invalid draft. English Test voice reached its TTS start callback using an installed Google offline voice. Spanish correctly showed the missing-offline-voice message because only English voice data was installed. Audio logs recorded a transient MAY_DUCK speech/navigation focus request and release five seconds later; this does not establish audible headphone playback or music ducking.

- With a **0.1 km distance goal**, synthetic movement crossed the goal while the Activity was backgrounded. Audio logs recorded the automatic speech focus request at **23:36:05** and its release at **23:36:09**. Notification Pause/Finish saved the activity with **32 accepted fixes**; the emulator's initial Vancouver fix and Granada test movement remained separated. The coaching draft also survived landscape/portrait rotation. This tests the background cue/focus path, not audible music ducking.
- The new activity's GPX Share file action opened the native chooser with Drive and a readable **Walk 0.22 km** filename; no account transfer was attempted.

- The exact packaged **0.6.0 / version code 6** release installed in place and opened successfully. A fresh CSV export retained all **18 activity IDs**, including the two-hour walk fixture and live coaching test. The release signing certificate matched the stable per-app key: `fc4cf764f78b745c431a632e5769348ec6488e04b4ca92515a9f589564f900ab`. APK SHA-256: `633c91a592ca950d596a4e116342470cfb49f1a6e641c24429b1bd5058fe1447`.

These are bounded local build/emulator checks. Physical Spotify/headphone behavior, a Google Drive account upload and Samsung field/battery behavior remain unverified. See [docs/TESTING.md](TESTING.md) for the checklist and detailed evidence.

## Version 0.5 verification

Historical results, checked locally on 1 November 2026:

- Final signed debug/release builds succeeded. All **71 JVM tests passed** with zero failures; lint reported **zero errors and 28 warnings**. All **23 generated icons** matched the pinned sources. English/Spanish checks confirmed parity for **196 string/plural resources** and their format placeholders.
- The exact packaged **0.5.0 / version code 5** release installed on the API 34 emulator and retained older history, the two-hour fixture and a newly recorded Walk. Its signing certificate matched the per-app key used since v0.2. A preceding debug update from v0.4 also retained existing recordings.
- A **7,201-point, two-hour walk fixture**, including a pause and missing altitude, exercised fullscreen replay at **1×**, seeking, Play/Pause and elevation details. English/Spanish switching and rotation retained position **1:00:19** and **1×** speed. Bounded light/dark, 1.3× font, portrait and landscape review found no material clipping in the inspected views.
- Primary export saved directly to Downloads with localized **Caminata 8,37 km** and **Walk 8.37 km** names plus start date/time. Parsed GPX retained all **7,201 points**, **two segments** and walking type. Export opened no Strava screen or browser. The success message was confirmed monochrome with the full filename visible. A separate export of the newly recorded Walk matched all **13 stored fixes and three segments**, including its activity type.
- The initial location-loading state was captured. Synthetic eastward movement showed the heading arrow centered in the available map area; panning disengaged follow and recenter restored it. Intermediate recording frames showed continuous display movement between fixes. These visual checks do not establish field GPS accuracy.
- With the Activity absent, notification **Resume**, **Pause** and **Finish** worked and saved a Walk with **13 accepted GPS fixes in three segments**. Changing Android's per-app language while the service was paused/backgrounded immediately updated the notification between Spanish and English.

The packaged `Runline-0.5.0.apk` SHA-256 is `e8b8010bc7c6b9f6b3936d47922aed1e090848bf614a8ae25c327129ef43e84d`. Its signing-certificate SHA-256 is `fc4cf764f78b745c431a632e5769348ec6488e04b4ca92515a9f589564f900ab`.

These are bounded local build/emulator checks, not certification of every device or failure path. Physical Samsung field/battery behavior, secure-lock-screen actions, Android 8/9 picker runtime, all export-failure paths and 16 KB page-size devices remain unverified. See [the full checklist and evidence](TESTING.md). CI status is tracked separately in the private [Android workflow](https://github.com/adrianetti/runline/actions/workflows/android.yml).

## Historical version 0.4 verification

Checked locally on 1 November 2026:

- Signed debug/release builds succeeded. All 53 JVM tests passed: the previous 38 calculation/persistence/replay tests, nine GPS-course tests and six notification-action guard tests. Lint reported zero errors and 28 warnings; all 23 generated icon resources matched their pinned sources.
- The API 34 emulator accepted the signed v0.4 update over v0.3 and retained older recordings. An unfinished draft survived an additional update and offered explicit recovery. The exact packaged release APK then installed and opened successfully, retaining the new 0.14 km test run and older history; its signature matched the same per-app key used since v0.2.
- Synthetic GPS movement east, then north, produced the correct heading arrow and close centering. Panning disengaged follow; the location control restored it. Bounded screenshot review covered light/dark, 1.3× font scale and landscape without material clipping. The final paused-state text and lock-screen settings guidance were also visible without clipping.
- Notification Pause, Resume and Finish worked with the app screen absent. Pause froze the timer; Finish removed the notification and saved 45 fixes across three segments. Tapping the notification from history opened the active run; returning to history and rotating stayed in history.
- Lock-screen statistics became visible after allowing silent lock-screen notifications. Expanding the notification opened the shade, where Pause worked. This was an emulator without a PIN; secure-lock-screen and Samsung Now bar behavior remain unverified.
- Saved-run replay opened at 1×. Playing for three seconds advanced to 00:03, and Pause stopped playback. The free Strava handoff was unchanged; its historical official-source audit is archived in [docs/STRAVA.md](STRAVA.md).

The packaged `Runline-0.4.0.apk` SHA-256 is `98a9b82e5106f12810281613435004a7580fe8b226f0174c896b779f43fec786`. CI results are tracked in the private [Android workflow](https://github.com/adrianetti/runline/actions/workflows/android.yml). No physical Samsung field/battery test, secure lock-screen action test, Android 8/9 picker runtime check, 16 KB page-size device test or Strava publication was performed. These results cover the listed build/emulator cases, not every device or failure path.

## Historical version 0.3 verification

Checked locally on 1 November 2026:

- Signed debug and release builds, `testDebugUnitTest` and `lintDebug` succeeded. All 38 JVM tests passed: 10 metrics/GPX, 14 persistence and 14 replay tests, including the peak-speed regression. Lint reported zero errors.
- The API 34 emulator installed v0.3 over the signed v0.2 app and retained the existing runs. The final signed release also opened after installation and retained both prior v0.2 recordings and the v0.3 synthetic/offline runs.
- Actual IGN satellite imagery rendered with the route and moving marker. Route fitting was corrected for display density. Playback at 30×, selecting 60×, seeking halfway, Pause, background/return and position restoration after rotation were exercised.
- Light/dark themes, 1.3× font scale and landscape were reviewed. Compact map heights were added; no other material layout issue was found in those screens. The final sharing dialog was confirmed monochrome; the map-credit target measured 48dp.
- With Wi-Fi and cellular disabled, GPS recording persisted seven synthetic samples while the app was backgrounded and the screen locked. Force-stop/reopen offered **Run recovered**; Finish saved the recovered run.
- The Android 10+ Strava path saved a valid GPX with 121 samples in two segments directly to Downloads, then opened the official browser uploader without a file-save picker. No Strava login, upload or publication was performed.

CI results are tracked separately in the private [Android workflow](https://github.com/adrianetti/runline/actions/workflows/android.yml). The untested v0.3 cases included a controlled physical-phone field run, hardware battery exhaustion, the Android 8/9 picker path, 16 KB page-size devices, and the user's actual Strava browser flow. The [current device checklist](TESTING.md) covers the retained features; the Strava handoff is now archived. Offline imagery is not guaranteed. These specific build/emulator results do not certify GPS accuracy, battery life, every device's background behavior or all failure paths.

## Historical version 0.2 verification

Checked locally on 31 October 2026:

- `assembleDebug`, `assembleRelease`, `testDebugUnitTest` and `lintDebug` succeeded. All 24 tests passed: 10 calculation/GPX tests and 14 persistence failure-injection tests. Lint reported zero errors.
- Android API 34 emulator: a synthetic run kept recording GPS after its UI was swiped away.
- Killing the recording process with `run-as … kill -9` caused Android to recreate the sticky service; capture resumed in a new segment.
- Force-stop followed by reopening preserved the draft and offered explicit recovery. The active timer stayed frozen during the interruption rather than adding downtime.
- Reboot followed by reopening retained the recorded points and recovered the run paused. Finish wrote the completed session.

The [initial clean GitHub Actions build](https://github.com/adrianetti/runline/actions/runs/41228763717) also passed. The Strava handoff saved a valid GPX containing all 15 test samples in three separate track segments, then opened the official uploader URL. The app did not publish an activity. The recording, recovery, history, run detail and sharing screens were reviewed in light mode and in dark mode at 1.3× font scale, with no material clipping found. A misleading no-route message was corrected and the local build/checks rerun. The signed release also installed as an update while retaining the completed test run.

No physical-phone field test, hardware battery-exhaustion test or Strava publication has been performed. Before using Runline as your only recorder, complete the [device checklist](TESTING.md), especially a screen-locked outdoor recording on your own phone. These emulator and file tests do not establish real GPS accuracy, battery consumption, device-specific background behavior or uninterrupted capture while a phone is powered off.


## Version 0.16 verification

0.16.0 / code 18 simplifies preparation and shared readouts, adds opt-in durable daily suggestions/session intent, automatic bounded nearby terrain, About updates/storage/credits, history transfer overflow and a native replay-speed slider. Local validation passed 330 JVM tests, debug/release compilation and Android lint (zero errors, 150 warnings), 574 bilingual resources, 37 pinned icons, workflow/source/secret checks and a 236-component/39-configuration advisory audit with no findings. [Testing](TESTING.md#version-016-validation) records bounded native UI, recovery, real public release-check and automatic terrain/cache checks. No paid AI or physical-device field test was performed for this release.


## Version 0.16.1 verification

0.16.1 / code 19 places a larger min/km directly beside pace in shared live/replay islands, adds a Recommended brain disclosure with the saved rationale and analysis time, and prefers GitHub Mobile for About links with an external-handler fallback. Replay speed gets a full row at narrow widths/large text. Local checks passed 330 JVM tests, debug/release builds, lint (zero errors, 150 warnings), 578 bilingual resources, 37 icons and the source/workflow/security checks. [Testing](TESTING.md#version-0161-validation) records native layout, cache/disclosure, link fallback and data-retention evidence and its limits.

APK SHA-256: `5d2655221f8ce3168a7e85cbb6dbb219ee72fbf89dcc48ffa2474f1b31378d99`. Signing identity is unchanged. No paid AI or physical-phone test was performed.

## Version 0.17 source changes

- **Stop detection and auto-pause.** Standstills are derived from saved fixes, plus a live signal from all valid fixes and Doppler speed. With auto-pause (default on), the timer, averages, splits, notification, kilometre cues and time goals use moving time. Fixes and exports are never edited.
- **Structure-aware terrain.** In regional coverage, an ambiguous footprint (bridge, underpass, embankment) and 15 m either side stay unknown instead of falling back to global heights. The slope line keeps a true aspect, and the slope label holds briefly instead of flickering.
- **Measured coach.**
  - Silent while stopped.
  - Kilometre splits are compared with the previous kilometre.
  - Halfway, final stretch and goal reached carry measured numbers.
  - Generic lines become progress checks.
  - Hill recovery only follows a measured climb.
- **Run data ZIP.** One activity's GPX, fixes with derived columns, stops, coach log, summary and review export as one ZIP. File options gained Tabler icons and clearer names.
- **Runner profile.** A local training snapshot, plus an optional AI-maintained assessment kept in its own store. It updates after new runs, at most every 6 hours.

## Version 0.17 verification

Recorded 6 November 2026:
- 353 JVM tests; lint 0 errors.
- Retrospective replay of private runs: no model mixing near structures, 5 stops found on the street run, 0 on the 10K.
- One real AI profile request per language.
- API 34 signed update kept 65/66 non-cache originals; mock-GPS live auto-pause; complete ZIP export; emulator data restored 75/75.

Details: [TESTING.md](TESTING.md#version-017-validation). Artifact `Runline-0.17.0.apk`, code 20, SHA-256 `5fbe4c063b42e3c0e8141514252a9d4e9cefa9991af553e52873cf17f2877859`, same signing certificate.

## Version 0.18 source changes

- **Bridge and underpass levels.** Hidden structure stretches are resolved from clean regional heights on both sides. Where the model stays runnable its heights are used (ground). A steep dip means a deck above it, a steep hump an underpass below it; either way the height joins the clean sides. The slope line and elevation charts no longer break at bridges and underpasses. Long or one-sided stretches stay unknown, live coaching keeps its forward-only guard, and `points.csv` gains `terrain_level`.
- **Replay trail.** The arrow draws the route as it moves. The map line is re-published at most twice a second and a native overlay draws the last stretch to the arrow each frame.
- **Map controls.** Recenter uses the marker's arrow at the bottom right and appears only after panning. The credits are one small translucent pill whose 48 dp row opens them. The unused location icon was removed.

## Version 0.18 verification

Recorded 6 November 2026:
- 360 JVM tests; lint 0 errors / 160 warnings; Semgrep, surface checks and Gitleaks clean.
- Retrospective replay of the private street run: all 63 hidden fixes resolved (above, below, above, ground), 0 unknown heights, 0 broken joins. The 10K is unchanged.
- API 34: signed update from 0.17.0 kept 68/68 non-cache files; continuous slope and chart under the A-44; trail behind the arrow; recenter and credits on replay and live maps; data restored 77/77.

Details: [TESTING.md](TESTING.md#version-018-validation). Artifact `Runline-0.18.0.apk`, code 21, SHA-256 `c6f87a2c52b0d57bbe442d3b720dbe0d25444a580d0d4b0f0084b9a04dbdd133`, same signing certificate.

## Version 0.19 source changes

- **Hiking routes.** The Routes tab offers the open catalog (bundled; refreshable from GitHub with SHA-256 checks) and imported GPX files. Route details show distance, ascent and descent, highest and lowest point, Tobler moving time, effort-kilometre difficulty, a profile, waypoints, sunset against the finish, Share GPX and Save GPX.
- **Following.** Hike this route attaches the route to the session, which survives restarts. The planned route is dashed under the track. The island shows what is left, arrival against sunset and the next waypoint. Leaving the path vibrates, updates the notification and, with voice on, says which side the path is on. Progress is matched by expected position, so out-and-back legs and loops count correctly.
- **Open route repository.** `routes/` is ODbL data with an `index.json` catalog, contribution rules and `tools/check_routes.py` in CI. The first route is a Granada loop rebuilt from OpenStreetMap paths with IGN elevations.

## Version 0.19 verification

Recorded 6–7 November 2026:
- 371 JVM tests; lint 0 errors / 165 warnings; resource, icon, route, Semgrep, surface and Gitleaks checks clean.
- **Route rebuild.** Matches the original's shape within about 40 m. Distance 14.90 vs 15.24 km; heights 668–927 vs 674–920 m.
- **API 34.** The signed update from 0.18.0 kept 70/70 non-cache files. Checked: Routes, details, Hike this route, the mock-GPS hike with off-route and rejoin, restart recovery, GPX import and delete. Data restored 79/79.

Details: [TESTING.md](TESTING.md#version-019-validation). Artifact `Runline-0.19.0.apk`, code 22, SHA-256 `a2d2f24e1fc2334f1218c868b5f24c3adcde35d62d162a26971192051b774a4f`, same signing certificate.

## Version 0.20 source changes

- **Turn cues.** Manoeuvres are derived from route geometry: 35° over about 20 m, with close turns merged, wiggles ignored, switchbacks grouped and true reversals as "turn around". Open routes list OpenStreetMap junctions, so a bend in a single path stays quiet. Each turn is spoken about 80 m ahead and at 20 m, with a GPS-style arrow and distance in the island. A navigation speech priority waits 3 s instead of the milestone's 30 s and expires after 12 s.
- **Climb ahead.** The next climb or descent shows in the island and is spoken once, about 200 m before. A profile shows the height from 300 m behind to 2 km ahead.
- **Place notes.** The catalog takes per-waypoint English and Spanish notes, validated by `check_routes.py` and the app. The Granada loop has five sourced notes.
- **Preview and start.** Route details keep Start and Preview fixed. Start records immediately. Preview plays the route like a recording. The selected route is ticked in the list.
- **Granada route cleanup.** 13 dead-end spurs and a 150 m loop left over from routing were removed (14.9 → 14.0 km). `check_routes.py` now refuses such spurs.
- **Fixes.**
  - Rejoining a route spoke the "Back to routes" button label; it now says "Back on the route".
  - Joining mid-route could latch onto an earlier leg nearby and report off route.
  - The speech watchdog now allows for long notes, which also stops a long note from dropping a pending turn cue.
  - In replay, the trail behind the arrow could double on turns or shoot across the screen. It is now drawn once near the arrow, and points outside the view are skipped.

## Version 0.20 verification

Recorded 7 November 2026:
- 395 JVM tests; lint 0 errors / 172 warnings; resource, icon, route, Semgrep, surface and Gitleaks checks clean.
- **API 34 mock-GPS walk of the Granada loop.** Turn, climb and place cues were seen in the island and recorded as spoken in the coach log. Three bugs found on the walk were fixed and re-walked: the long-note watchdog, a note starting before a turn, and a false off-route on a mid-route join.
- **Replay trail.** The doubling was reproduced at 480× on a 0.19 build. With the overlay tinted for diagnosis, the stretch behind the arrow was drawn by the overlay alone at 120×, through the acequia hairpins, with no stray lines.
- **Update.** The release kept 80/80 non-cache files when updating 0.19 in place. The emulator was restored to 0.19, 80/80, and root disabled.

Details: [TESTING.md](TESTING.md#version-020-validation). Artifact `Runline-0.20.0.apk`, code 23, SHA-256 `6f6ca22fa32fcbda3aa5af75f2eb4ae24f0519480eb8b7632f8e75b5bb993566`, same signing certificate.

## Version 0.20.1 source changes

- **Elevation on mountain paths.** The terrain check took any footprint with more than 3 m of height spread for a bridge or wall and hid its height. On a path across a steep hillside that is almost every fix, so replays, the elevation chart and the route preview went blank in the mountains. An even hillside is now told apart from a step: one tilted plane must fit the footprint, no steeper than 70%, and the slope must continue 20 m up and down. Bridges, underpasses and embankments stay hidden and levelled as in 0.18. The same test applies to global terrain. [Terrain](TERRAIN.md#hillsides-0201).
- **Route preview heights.** A route preview shows the route's own heights when every point has one, as route details and the elevation ahead already do. It works offline and has no gaps. Routes without heights still use terrain maps.

## Version 0.20.1 verification

Recorded 8 November 2026:
- 401 JVM tests; lint 0 errors / 172 warnings; resource, icon, route, Semgrep, surface and Gitleaks checks clean.
- **Granada loop, real IGN tiles.** Fixes without height fell from 660, 1316 and 1676 of 2136 to 40, 47 and 187, at 5, 10 and 15 m accuracy. The longest gap fell from 2.1–5.9 km to 266–388 m.
- **API 34.** The same mock-GPS hillside recording went from 7% mapped and 0 m climbed on 0.20.0 to 81% and 10 m on 0.20.1. The route preview's profile has no holes. The emulator was restored to 0.19, 80/80, and root disabled.

Details: [TESTING.md](TESTING.md#version-0201-validation). Artifact `Runline-0.20.1.apk`, code 24, SHA-256 `0dbd86d0357b2acb458a16d40f4b5be792360c45a87418accd13306688b98566`, same signing certificate.

## Version 0.20.2 source changes

- **Add route.** Routes has a visible **Add route** button that opens Android Files. **Open with** and **Share** from other apps use the same flow, also when Runline is already open.
- **Name it.** A dialog reads the GPX, shows shape · distance · ascent and an editable name, and saves only on **Add route**. A useful GPX title or filename wins. Otherwise the name reads “Granada, Los Pinos – Puente Romano loop 10.3K”: town, start and far point from nearby GPX waypoints or Android's built-in geocoder, then shape and distance. Offline it is “Loop 10.3K”. Default activity titles (“Morning Run”), IDs, dates and Runline's own export suffix are skipped. Blank input accepts the suggestion; Cancel saves nothing.
- **Start an imported route.** It keeps the activity chosen on Record instead of always recording a hike.
- **Import handling.** A namespace-aware GPX parser refuses malformed XML, DOCTYPE/entities, over 15 MB and over 100,000 points. Reading and edits survive rotation; one confirmation saves one route. Original GPX bytes are kept; the name is separate metadata.
- **CI.** Every Android build also runs the seven GPX import tests on an API 34 emulator.

## Version 0.20.2 verification

Recorded 9 November 2026:

- **416 JVM tests** passed, lint **0 errors / 173 warnings**, 745 bilingual resources, 53 pinned icons, one catalog route and 22 security-checker tests. Debug, release and Android test APKs built.
- **Seven native import tests** passed on a throwaway API 34 emulator (Google APIs image).
- **By hand on that emulator:** synthetic GPX files in Downloads, opened with Runline and picked through **Add route → Files**. A Madrid out-and-back was named “Madrid, Callao – Paseo del Prado out and back 3.4K” and opened its route page; a Granada loop became “Granada, Callejon Viernes – Calle Eudoxia Piriz circular 1,7K” in Spanish. A readable filename beat a generic “Morning Run” title. Tapping the name selected it for replacement.

Not verified: a physical phone, third-party download apps' Open with behaviour, and the signed update over 0.20.1.

## Version 0.20.3

- **Open with finds Runline.** Android has no MIME type for `.gpx`, so Chrome downloads, Files and My Files pass it as `application/octet-stream` or XML with an opaque URI. Open with and Share now accept those types (and `application/x-gpx`); the import still checks the file and refuses anything that isn't a GPX route.
- **+ to add.** Routes adds a GPX from a **+** beside ⋯ instead of a full-width button.
- **Names.** A filename that is a provider ID glued to words (“1448280673GRANADAPINOSGENIL…”) is skipped, so the place-based suggestion is used.

Verified 10 November 2026: 416 JVM tests, lint 0 errors, resource, icon (53), route and security checks. Seven import tests on a throwaway API 34 emulator. There, a `.gpx` pushed to Downloads was stored as `application/octet-stream`; Runline resolved for it as octet-stream, XML and GPX types but not PDF, opened it, and suggested “Madrid, Callao – Paseo del Prado out and back 3.4K”. **+** opened the Files picker. Not tested on a physical phone or in Chrome's own download list.

