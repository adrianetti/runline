# Runline

A personal Android running recorder with local history, a minimal monochrome interface and exportable GPS data. Source and build history live in the private [adrianetti/runline](https://github.com/adrianetti/runline) repository.

Version 0.2 is a sideloaded prototype for Android 8.0 and later. It uses Kotlin, Android Views, Material 3 and Android's GPS location provider. It targets and compiles against SDK 34; Play Store distribution is not configured.

## Record a run

1. Open **Record** and tap **Start run**. On first use, grant precise location and allow notifications. Location must be enabled on the phone.
2. Wait for a GPS fix outdoors. You can lock the screen or switch apps. The foreground recording service is independent of the visible screen.
3. Tap **Pause**, then **Resume** or **Finish**. Finished recordings appear under **Runs**.

After the initial Android permissions are granted, starting a run takes one tap. The main screen keeps distance, time, pace and recording status visible. Less frequent actions live in the toolbar menu. Icons use one [MIT-licensed Tabler outline set](THIRD_PARTY_NOTICES.md), including the running figure, controls and notification glyph.

The shield control opens **Recording protection**, showing notification and battery-optimization status with a shortcut to Android settings. These checks help identify restrictive settings; they do not certify future GPS continuity on a particular phone.

## What is recorded

- Accepted GPS coordinates, timestamps, accuracy, available altitude and track segments.
- Active time, elapsed time, distance, average pace and kilometre splits.
- Saved runs and a recoverable unfinished session in the app's private storage.

There is no account, cloud sync, downloaded basemap or automatic Strava upload. The app does not request internet permission. Heart rate and cadence are not recorded without a compatible sensor integration.

Distance is an estimate from accepted GPS fixes. Active time includes standing still and waiting for GPS, but excludes explicit pauses and unobserved process downtime. Elapsed time includes pauses. Average pace uses active time divided by distance. Splits use time between GPS samples and interpolate kilometre boundaries; pauses and signal gaps are excluded, so split time can differ from the active timer. **Run info** explains these details without filling the recording screen with text.

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

Open a saved run and tap **Share to Strava**. On first use, the app explains the short flow:

1. Save the GPX file, preferably to Downloads.
2. Runline opens Strava's website uploader after the save succeeds.
3. Choose the file, review the activity and its visibility, then publish on Strava.

This remains a manual website handoff. Opening Strava does not send the file, connect an account or publish an activity. A phone browser may need **Desktop site**; the saved file can also be uploaded from a computer. Failed or cancelled export leaves the original run intact. The menu also offers **Export GPX** and **Share file**.

The GPX includes exact route locations and timestamps. Review what you share. Strava may calculate distance, time and pace differently. The app does not target an undocumented GPX-import intent in Strava's Android app. Automatic OAuth/API upload is separate and currently requires a personal Strava API application/subscription. See [sharing details and official sources](docs/STRAVA.md).

## Keep your own copy

All recording copies are on the same phone. Android backup is disabled. Uninstalling Runline, clearing app storage or losing the phone removes the recordings, including local recovery copies. Export important runs to a location you control. GPX preserves route samples, timestamps and available altitude; it is not a full backup of every internal timing or recovery field.

## Build and install

Use JDK 17, Android SDK Platform 34 and Build Tools 34.0.0. Set `ANDROID_HOME`, or create an untracked `local.properties` containing `sdk.dir=/absolute/path/to/Android/sdk`.

```sh
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Download the signed APK from the private [v0.2.0 release](https://github.com/adrianetti/runline/releases/tag/v0.2.0). A local copy is also available at `dist/Runline-0.2.0.apk`. With USB debugging enabled, install that build using:

```sh
adb install -r dist/Runline-0.2.0.apk
```

Or copy the APK to your phone and open it, allowing installation from the app you use to open the file. It is privately distributed, not a Play Store release.

Version 0.2 uses a dedicated per-app signing key so later builds signed with that same key can update in place. The earlier v0.1 APK used a different development key. If v0.1 is already installed, export any runs before uninstalling it to migrate; Android cannot install a differently signed update over it. Uninstalling removes Runline's private data.

GitHub Actions checks pushes and pull requests to `main`. Main-branch builds use repository signing secrets and publish a signed release APK plus check reports as private workflow artifacts. Pull requests do not receive signing secrets. The private key and its local configuration stay outside Git; a fresh clone can build a development APK but does not receive the private release key.

For local development, the command above produces `app/build/outputs/apk/debug/app-debug.apk`. `assembleRelease` produces a signed release only when the authorized signing configuration is available. Keep the app signing key for future updates; do not commit it or share its password.

To verify the pinned icon resources without network access:

```sh
python3 tools/import_icons.py --check
```

## Version 0.2 verification

Checked locally on 31 October 2026:

- `assembleDebug`, `assembleRelease`, `testDebugUnitTest` and `lintDebug` succeeded. All 24 tests passed: 10 calculation/GPX tests and 14 persistence failure-injection tests. Lint reported zero errors.
- Android API 34 emulator: a synthetic run kept recording GPS after its UI was swiped away.
- Killing the recording process with `run-as … kill -9` caused Android to recreate the sticky service; capture resumed in a new segment.
- Force-stop followed by reopening preserved the draft and offered explicit recovery. The active timer stayed frozen during the interruption rather than adding downtime.
- Reboot followed by reopening retained the recorded points and recovered the run paused. Finish wrote the completed session.

The [initial clean GitHub Actions build](https://github.com/adrianetti/runline/actions/runs/41228763717) also passed. The Strava handoff saved a valid GPX containing all 15 test samples in three separate track segments, then opened the official uploader URL. The app did not publish an activity. The recording, recovery, history, run detail and sharing screens were reviewed in light mode and in dark mode at 1.3× font scale, with no material clipping found. A misleading no-route message was corrected and the local build/checks rerun. The signed release also installed as an update while retaining the completed test run.

No physical-phone field test, hardware battery-exhaustion test or Strava publication has been performed. Before using Runline as your only recorder, complete the [device checklist](docs/TESTING.md), especially a screen-locked outdoor recording on your own phone. These emulator and file tests do not establish real GPS accuracy, battery consumption, device-specific background behavior or uninterrupted capture while a phone is powered off.
