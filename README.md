# Runline

A personal Android running recorder with local history, satellite maps, route replay and exportable GPS data. Controls stay monochrome; real imagery keeps its colors. Source and build history live in the private [adrianetti/runline](https://github.com/adrianetti/runline) repository.

Version 0.4.0 (version code 4) is a sideloaded prototype for Android 8.0 and later. It uses Kotlin, Android Views, Material 3 and Android's GPS location provider. It targets and compiles against SDK 34; Play Store distribution is not configured. Local build and API 34 emulator checks are recorded below; physical-phone testing remains outstanding.

## Record a run

1. Open **Record** and tap **Start run**. On first use, grant precise location and allow notifications. Location must be enabled on the phone.
2. Wait for a GPS fix outdoors. You can lock the screen or switch apps. The foreground recording service is independent of the visible screen.
3. Tap **Pause**, then **Resume** or **Finish**. Finished recordings appear under **Runs**.

After the initial Android permissions are granted, starting a run takes one tap. During a run, the satellite map fills the app content area. A bottom island keeps distance, active time, average pace and Pause within reach; portrait also shows GPS speed and altitude. Paused and recovered runs offer Resume and Finish in the same island. Runs and the options menu float above the map. In landscape the island sits at the lower left, leaving the map visible beside it. Icons use one [MIT-licensed Tabler outline set](THIRD_PARTY_NOTICES.md), including the running figure, navigation arrow, controls and notification glyph (22 pinned glyphs, 23 generated VectorDrawables including the launcher).

The shield control, or **Recording protection** in the active-run menu, shows notification and battery-optimization status with shortcuts to Android settings. These checks help identify restrictive settings; they do not certify future GPS continuity on a particular phone.

The ongoing notification shows distance, average pace and a live active-time chronometer, with GPS status when expanded. Tap **Pause**, then **Resume** or **Finish**; the timer freezes while paused. Tapping the notification opens the current recording. Allow lock-screen notifications, including silent notifications where the phone has a separate setting, to use the controls there. Samsung may show icons until expanded, and its Now bar supports selected apps; Runline does not claim Now bar integration or pose as a music player. See [Android notification visibility](https://developer.android.com/develop/ui/compose/notifications/create-notification) and [Samsung Now bar guidance](https://www.samsung.com/my/support/mobile-devices/how-to-use-the-now-bar-on-the-lock-screen-of-your-samsung-galaxy-device/).

## What is recorded

- Accepted GPS coordinates, timestamps, accuracy, available altitude and track segments.
- Active time, elapsed time, distance, average pace and kilometre splits.
- GPS speed estimates, available altitude, and filtered ascent/descent estimates calculated from the recorded samples.
- Saved runs and a recoverable unfinished session in the app's private storage.

There is no Runline account, cloud recording sync or automatic Strava upload. Internet access loads map tiles; GPS recording and saved-run analysis work without it. Heart rate and cadence are not recorded without a compatible sensor integration.

Distance is an estimate from accepted GPS fixes. Active time includes standing still and waiting for GPS, but excludes explicit pauses and unobserved process downtime. Elapsed time includes pauses. Average pace and average speed use active time. Replay and saved-run splits use observed GPS intervals, skipping pauses and gaps over 15 seconds; their duration can differ from the active timer. **Run info** explains these details without filling the recording screen with text.

## Maps, replay and run details

The active-run map fills the content area, with its camera centered in the visible space around the island and controls. Follow uses close zoom 18 and keeps the map north-up. A Tabler arrow points in the direction of accepted GPS movement; a neutral marker remains until movement establishes a reliable heading. It is not a phone compass. The last known heading is retained while stopped, and gaps or new recording segments require a fresh heading.

Drag or pinch to explore, tap the route icon to fit the run, or the location icon to return to close following. The ready screen retains a 240dp map and a saved run a 300dp playback map; below 500dp screen height these become 120dp and 160dp. The route and marker are drawn locally from the recording, preserving segment breaks. Map credits remain visible above or beside the recording island.

MapLibre Android 11.13.5 displays worldwide EOX imagery at approximately 10-metre resolution, detailed IGN/PNOA imagery in Spain, and road/place labels. Global imagery is less detailed when enlarged; local coverage and capture dates vary. EOX imagery is licensed for noncommercial use. This personal build requires no map account or API key. Credits and source information are accessible on the map; see [map coverage, licensing and privacy](docs/MAPS.md).

Map providers receive requests for visible tile areas and the phone's network address. Runline does not send them the raw run file, timestamps or session statistics. Maps can reveal the area being viewed. Tile failures do not stop recording or calculation, and a native map-load failure falls back to the geometric route. Recently viewed tiles may be cached; downloaded offline regions are not implemented.

Open a saved run to play or seek through its route at **1×, 5×, 10×, 30× or 60×**. Playback defaults to 1×, updates up to 20 times per second, and pauses when the screen goes into the background. It cuts across missing intervals instead of animating an invented connection. Replay is an in-app view; it does not export an MP4 or other video.

Replay shows speed, pace, GPS altitude, replayed distance and accumulated ascent/descent alongside an altitude chart. Run totals include average and top GPS speed, ascent/descent, altitude range, elapsed time and sample count. Speed uses a trailing five-second GPS estimate, with implausible running speeds omitted. Altitude is raw GPS altitude; ascent/descent ignore changes below 3 metres and are not terrain-corrected. Missing values show **—**, and the altitude chart preserves missing-data and segment gaps. These are estimates, not sensor-grade measurements.

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

## Share to Strava

Open a saved run and tap **Strava** in the top toolbar. On first use, the app explains the short flow:

1. On Android 10+, Runline saves the GPX directly to **Downloads** and opens Strava's website uploader after the save succeeds. Android 8/9 uses a file picker first; a failed automatic save also offers the picker.
2. Choose the saved file on Strava, review the activity and its visibility, then publish there.

Later uses skip the introduction. A message in Runline shows the actual saved filename and offers **Copy name**. The uploader opens in a browser rather than an installed Strava app that may intercept the link. If no browser is available, the file stays saved and the uploader URL can be copied.

This remains a manual website handoff. Opening Strava does not send the file, connect an account or publish an activity. A phone browser may need **Desktop site**; the saved file can also be uploaded from a computer. Failed or cancelled export leaves the original run intact. The menu also offers **Export GPX** and **Share file**.

The GPX includes exact route locations and timestamps. Review what you share. Strava may calculate distance, time and pace differently. The app does not target an undocumented GPX-import intent in Strava's Android app. **The current sharing flow stays subscription-free:** manual file upload needs no personal API application. Creating a new personal API application currently requires a subscription; an existing registered client may offer a separate integration path. No OAuth connector or automatic upload is implemented here. See [sharing details and official sources](docs/STRAVA.md).

## Keep your own copy

All recording copies are on the same phone. Android backup is disabled. Uninstalling Runline, clearing app storage or losing the phone removes the recordings, including local recovery copies. Export important runs to a location you control. GPX preserves route samples, timestamps and available altitude; it is not a full backup of every internal timing or recovery field.

## Build and install

Use JDK 17, Android SDK Platform 34 and Build Tools 34.0.0. The project pins AGP 8.5.2, Kotlin 2.0.20 and Gradle 8.7 for MapLibre compatibility. Set `ANDROID_HOME`, or create an untracked `local.properties` containing `sdk.dir=/absolute/path/to/Android/sdk`.

```sh
./gradlew testDebugUnitTest assembleDebug assembleRelease lintDebug
```

Download the signed [Runline 0.4.0 APK](https://github.com/adrianetti/runline/releases/download/v0.4.0/Runline-0.4.0.apk) from the private release, or use the local `dist/Runline-0.4.0.apk`. Install the local artifact with USB debugging enabled:

```sh
adb install -r dist/Runline-0.4.0.apk
```

Or copy the APK to your phone and open it, allowing installation from the app you use to open the file. It is privately distributed, not a Play Store release.

Version 0.4 retains the dedicated per-app signing key introduced in v0.2, allowing an in-place update that keeps private recordings. The earlier v0.1 APK used a different development key. If v0.1 is already installed, export any runs before uninstalling it to migrate; Android cannot install a differently signed update over it. Uninstalling removes Runline's private data.

GitHub Actions checks pushes and pull requests to `main`. Main-branch builds use repository signing secrets and publish a signed release APK plus check reports as private workflow artifacts. Pull requests do not receive signing secrets. The private key and its local configuration stay outside Git; a fresh clone can build a development APK but does not receive the private release key.

For local development, the command above produces `app/build/outputs/apk/debug/app-debug.apk`. `assembleRelease` produces a signed release only when the authorized signing configuration is available. Keep the app signing key for future updates; do not commit it or share its password.

To verify the pinned icon resources without network access:

```sh
python3 tools/import_icons.py --check
```

## Version 0.4 verification

Checked locally on 1 November 2026:

- Signed debug/release builds succeeded. All 53 JVM tests passed: the previous 38 calculation/persistence/replay tests, nine GPS-course tests and six notification-action guard tests. Lint reported zero errors and 28 warnings; all 23 generated icon resources matched their pinned sources.
- The API 34 emulator accepted the signed v0.4 update over v0.3 and retained older recordings. An unfinished draft survived an additional update and offered explicit recovery. The exact packaged release APK then installed and opened successfully, retaining the new 0.14 km test run and older history; its signature matched the same per-app key used since v0.2.
- Synthetic GPS movement east, then north, produced the correct heading arrow and close centering. Panning disengaged follow; the location control restored it. Bounded screenshot review covered light/dark, 1.3× font scale and landscape without material clipping. The final paused-state text and lock-screen settings guidance were also visible without clipping.
- Notification Pause, Resume and Finish worked with the app screen absent. Pause froze the timer; Finish removed the notification and saved 45 fixes across three segments. Tapping the notification from history opened the active run; returning to history and rotating stayed in history.
- Lock-screen statistics became visible after allowing silent lock-screen notifications. Expanding the notification opened the shade, where Pause worked. This was an emulator without a PIN; secure-lock-screen and Samsung Now bar behavior remain unverified.
- Saved-run replay opened at 1×. Playing for three seconds advanced to 00:03, and Pause stopped playback. The free Strava handoff was unchanged; its current official-source audit is in [docs/STRAVA.md](docs/STRAVA.md).

The packaged `Runline-0.4.0.apk` SHA-256 is `98a9b82e5106f12810281613435004a7580fe8b226f0174c896b779f43fec786`. CI results are tracked in the private [Android workflow](https://github.com/adrianetti/runline/actions/workflows/android.yml). No physical Samsung field/battery test, secure lock-screen action test, Android 8/9 picker runtime check, 16 KB page-size device test or Strava publication was performed. These results cover the listed build/emulator cases, not every device or failure path.

## Historical version 0.3 verification

Checked locally on 1 November 2026:

- Signed debug and release builds, `testDebugUnitTest` and `lintDebug` succeeded. All 38 JVM tests passed: 10 metrics/GPX, 14 persistence and 14 replay tests, including the peak-speed regression. Lint reported zero errors.
- The API 34 emulator installed v0.3 over the signed v0.2 app and retained the existing runs. The final signed release also opened after installation and retained both prior v0.2 recordings and the v0.3 synthetic/offline runs.
- Actual IGN satellite imagery rendered with the route and moving marker. Route fitting was corrected for display density. Playback at 30×, selecting 60×, seeking halfway, Pause, background/return and position restoration after rotation were exercised.
- Light/dark themes, 1.3× font scale and landscape were reviewed. Compact map heights were added; no other material layout issue was found in those screens. The final sharing dialog was confirmed monochrome; the map-credit target measured 48dp.
- With Wi-Fi and cellular disabled, GPS recording persisted seven synthetic samples while the app was backgrounded and the screen locked. Force-stop/reopen offered **Run recovered**; Finish saved the recovered run.
- The Android 10+ Strava path saved a valid GPX with 121 samples in two segments directly to Downloads, then opened the official browser uploader without a file-save picker. No Strava login, upload or publication was performed.

CI results are tracked separately in the private [Android workflow](https://github.com/adrianetti/runline/actions/workflows/android.yml). The [device checklist](docs/TESTING.md) still includes untested cases: a physical-phone field run, hardware battery exhaustion, the Android 8/9 picker path, 16 KB page-size devices, and the user's actual Strava browser flow. Offline imagery is not guaranteed. These specific build/emulator results do not certify GPS accuracy, battery life, every device's background behavior or all failure paths.

## Historical version 0.2 verification

Checked locally on 31 October 2026:

- `assembleDebug`, `assembleRelease`, `testDebugUnitTest` and `lintDebug` succeeded. All 24 tests passed: 10 calculation/GPX tests and 14 persistence failure-injection tests. Lint reported zero errors.
- Android API 34 emulator: a synthetic run kept recording GPS after its UI was swiped away.
- Killing the recording process with `run-as … kill -9` caused Android to recreate the sticky service; capture resumed in a new segment.
- Force-stop followed by reopening preserved the draft and offered explicit recovery. The active timer stayed frozen during the interruption rather than adding downtime.
- Reboot followed by reopening retained the recorded points and recovered the run paused. Finish wrote the completed session.

The [initial clean GitHub Actions build](https://github.com/adrianetti/runline/actions/runs/41228763717) also passed. The Strava handoff saved a valid GPX containing all 15 test samples in three separate track segments, then opened the official uploader URL. The app did not publish an activity. The recording, recovery, history, run detail and sharing screens were reviewed in light mode and in dark mode at 1.3× font scale, with no material clipping found. A misleading no-route message was corrected and the local build/checks rerun. The signed release also installed as an update while retaining the completed test run.

No physical-phone field test, hardware battery-exhaustion test or Strava publication has been performed. Before using Runline as your only recorder, complete the [device checklist](docs/TESTING.md), especially a screen-locked outdoor recording on your own phone. These emulator and file tests do not establish real GPS accuracy, battery consumption, device-specific background behavior or uninterrupted capture while a phone is powered off.
