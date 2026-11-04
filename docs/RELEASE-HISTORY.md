# Release history

Historical source changes, artifact checksums and bounded verification evidence moved from the README. These records describe the versions tested, not a blanket verification of the current app. See [TESTING.md](TESTING.md) for the current checklist and v0.11 evidence.

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
