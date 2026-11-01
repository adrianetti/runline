# Version 0.4 test checklist

The checklist is not a blanket pass report; completed v0.4 and historical v0.3 checks are summarized at the end and in README.md. Record the build/commit, phone model, Android version, battery settings and pass/fail/not-tested outcome. Use disposable synthetic recordings for destructive and fault-injection cases; keep real route files private.

## Build and unit checks

With JDK 17, SDK 34 and Build Tools 34.0.0, use the pinned AGP 8.5.2 / Kotlin 2.0.20 / Gradle 8.7 build:

```sh
python3 tools/import_icons.py --check
./gradlew testDebugUnitTest assembleDebug assembleRelease lintDebug
```

Inspect unit-test and lint reports. Persistence tests should exercise actual temporary files with injected failures, including incomplete journal appends, corrupt metadata, finalization/cleanup interruption and duplicate completion. A passing calculation test does not validate Android service lifecycle or physical storage durability.

Notification-action unit tests should reject wrong/missing run IDs, stale Pause/Resume/Finish actions and Finish after recording has resumed. Heading tests should cover movement, stationary jitter, accuracy, turns, gaps/segments, invalid samples and date-line crossing. Unit tests do not establish lock-screen behavior.

Replay tests should cover time/distance interpolation, explicit segments, gaps over 15 seconds, missing/non-finite altitude, elevation filtering, speed windows, isolated points, seek boundaries and date-line crossing. Check dependency/license notices for the pinned MapLibre SDK and map data. Successful HTTP tile requests alone are not evidence of native map rendering.

## Installation and signing

- Verify the packaged `dist/Runline-0.4.0.apk` signature and version name/code (0.4.0 / 4); install that exact artifact. Update an existing signed v0.3 build with saved runs and an unfinished draft, using the same per-app key. Confirm recordings survive and no uninstall is needed.
- Do not treat the old v0.1 debug signature as compatible. If migration requires uninstalling v0.1, export recordings first and verify the exports before uninstalling.
- Main-branch CI may receive signing secrets; pull-request builds must not. Never log key/password contents or put signing material in test artifacts.

## Start and controls

- Fresh installation: tap **Start run**, deny location, then retry with approximate-only location. Neither case should claim usable GPS recording; precise location should have a clear recovery path.
- Grant precise location and notifications. With location enabled, subsequent **Start run** taps should start without an extra confirmation. Rapid repeated taps must not create multiple drafts or services.
- Start outdoors and distinguish **Finding GPS** from accepted fixes. No distance should appear before usable movement is recorded.
- Pause, resume, then finish. Pause freezes active time and route capture; Finish creates one saved session. Repeated Finish/reopen must not duplicate it.
- Deny notifications and verify the service handles Android's foreground-notification policy without crashing. Restore notifications for normal runs.
- Inspect **Recording protection**, then open app settings. Report actual battery and notification status rather than treating those labels as a recording guarantee.

## Notification and lock-screen controls

- While recording, expand the ongoing notification. Verify distance, average pace, active-time chronometer and relevant GPS status. Metrics refresh periodically; only the chronometer needs to advance each second.
- Tap Pause from the notification while the app is backgrounded. Confirm capture and active time stop, the timer freezes, and Resume/Finish replace Pause. Tap Resume and verify capture restarts in a new segment; pause again, then Finish and confirm one saved run and removal of the ongoing notification.
- Keep a stale action available where the test harness permits. Wrong-session actions must not alter another run; an old Finish must not finish a session that has resumed. No stale action should disable sticky recovery of a currently active session.
- From Runs or a saved detail screen, tap the notification body and confirm the active recording screen opens. Rotate, return to history and repeat.
- Lock the screen with Runline notifications allowed, including silent lock-screen notifications if separately controlled. Check visible time/distance, expansion and action behavior under the device’s actual privacy settings. Repeat with a secure PIN/password lock; a no-PIN emulator result is not sufficient evidence for that case. Repeat on the user’s Samsung, recording the One UI version and notification style. An emulator notification pass does not establish Samsung Now bar support; Runline does not register as music playback or claim promoted Live Updates.
- Disable lock-screen notification details or deny notification permission and verify the app settings guidance is accurate. Do not label absent system presentation as lost recorded data.

## Screen closure and process interruption

These are separate tests with different expected behavior:

| Event | Expected behavior |
| --- | --- |
| Home, screen lock, switching apps | The foreground service continues while Android permits; reopening displays the same session. |
| Swiping the task from Recents | Removing the screen must not intentionally stop the service. Check timestamps and notification, not just the Activity's disappearance. |
| Ordinary OS process kill/reclamation | Verify the process really ended. Android may recreate the sticky service; only an eligible active same-boot draft may resume automatically. The route starts a new segment and downtime is excluded. If restart is denied, the committed draft remains recoverable. |
| Kill after an explicit pause | The run must remain paused/recovered; no silent active restart or paused-time accumulation. |
| Android foreground-service Task Manager Stop | Respect the user stop; no claim of continued recording. Reopen manually and recover saved data. |
| Android Settings Force stop | No continued GPS or automatic restart. Reopening offers committed data without counting the forced-stop interval as active time. |
| Reboot | No boot-triggered recording. Reopen to recover; Resume creates a new segment with no bridge across the shutdown interval. |
| Abrupt emulator termination | Restart the same emulator without wiping data. Recover the last valid committed records; do not rely on a graceful shutdown callback. |

For each event, compare sample count/coordinates, active time, session ID and segments before and after. Do not use `am kill` as proof of an ordinary process death unless it actually kills the foreground recording process. Record the actual termination method. A process restart is not proof that all intervening points were collected.

## Persistence failure injection

Use isolated test directories or disposable emulator app data, never a user's real history.

1. Truncate the final journal line mid-write. Recover preceding valid samples, retain the damaged original before repair, and resume into a new segment.
2. Corrupt the main mutable checkpoint; repeat with both mutable checkpoints. Confirm valid journal/seed copies can identify and recover the run where sufficient data remains.
3. Corrupt a journal entry in the middle. Do not connect valid points across the missing record or silently present the gap as a continuous route.
4. Corrupt all usable metadata. Confirm **Draft needs recovery** appears and **Keep draft & reset** preserves original bytes before allowing a new run. Saved history must remain intact.
5. Inject failure before and after a point flush, atomic rename, directory sync, completed-run commit and draft cleanup. Check which records were committed; do not assert that a write which never committed must exist.
6. Complete the same run repeatedly, including after a simulated crash during cleanup. One session ID should appear in history and must not restart as an unfinished recording.
7. Make a completed-run copy unreadable and verify fallback to its valid local counterpart. Keep damaged files rather than silently replacing them.
8. Simulate full storage or denied writes. Recording must surface a paused/error state, preserve committed data, and allow safe retry once space is restored. Do not deliberately fill a personal phone's entire disk.
9. Inject deletion/archive-copy failure. Failed archive must keep its original source; failed cleanup must not be reported as successfully removing every recovery file.

## GPS, timing and offline behavior

- Pause for at least a minute and move substantially while paused. Resume elsewhere. Active time excludes the pause; the route and GPX have separate segments; distance does not bridge the gap.
- Stand still without pausing. Active time continues; inspect excessive noise distance rather than expecting a perfectly flat consumer GPS signal.
- Disable location, revoke permission, or move through poor reception. The app must identify the condition, preserve existing samples and avoid stale-fix confidence or a fabricated bridge after signal recovery.
- Record over one kilometre and finish within the next. Check full splits and the final partial split; split time need not equal active time because it uses GPS intervals.
- A no-fix run may retain its timer, but route/pace must remain unavailable and the Strava flow must reject unusable tracks.
- In airplane mode with GPS enabled, record and reopen a run. Recording/history/replay calculations require no network; a cold fix can take longer. Maps may have no imagery outside cached tiles, but recording and geometric route display must remain usable. Cloud document providers and Strava have separate connection requirements.
- Change the wall clock during a test and exercise recovery across a simulated boot change. Active time uses a monotonic clock; recovery must not infer unobserved elapsed downtime as activity.

## Physical-phone field check — required

Record a known route for at least 30 minutes with the screen locked for most of the session. Include stationary time, a pause/resume and weak-signal sections. Note battery consumption, GPS gaps and distance against an independent route/reference. Repeat under the phone's normal and restrictive battery settings, then under the intended setting for real runs. Add a longer test representative of normal use.

Do not deliberately run down a phone that is needed for navigation or communication. Test power-loss recovery in a controlled setting, or use abrupt emulator termination for the crash-consistency check. A dead battery cannot produce further GPS samples; evaluate retention and recovery of prior committed data separately from continuity.

## UI and accessibility

- Confirm **Record**, **Runs**, **Start run**, **Pause**, **Resume** and **Finish** are easy to find. **Strava** belongs in the saved-run toolbar, not below every statistic. Use one Tabler family and neutral controls; real satellite imagery retains its colors.
- Check light/dark mode, 1.3 font scale and a smaller supported phone. No clipped measurements, labels or actions; system-bar icons must retain contrast.
- Check the full-content active map and bottom island in portrait, then the lower-left island in landscape. Distance, time, average pace and recording controls remain reachable; short screens may scroll island information without hiding its actions. The marker, credits, status and map controls must avoid the island. Ready/detail maps remain 240dp/300dp, or 120dp/160dp below 500dp screen height. The compact detail header places distance beside time/pace.
- Check TalkBack labels for toolbar actions, fit/follow controls, playback speed, seek position, map credits and altitude profile. Decorative icons should not repeat adjacent labels.
- Rotate during recording and during a Strava file save. Preserve the current session and avoid duplicate export or browser-launch events.
- Check Android Back from run details and history. Discard/Delete must still ask for confirmation even though Finish is direct.

## Satellite maps and network failure

- Check detailed IGN/PNOA imagery in Spain, global EOX imagery outside Spain, and road/place labels. Compare the visible area with the intended route; record tile provider, zoom and coverage instead of assuming street-level global detail.
- Pan, pinch, fit the entire route and follow the moving marker. Pan/pinch should stop following until explicitly re-enabled. Live follow returns to zoom 18 with the marker centered in the unobscured area; the map stays north-up. Include a route crossing the date line and a viewport near Spain's boundary.
- Begin stationary: the marker stays neutral until accepted movement exceeds GPS noise. Move along a known bearing and turn; inspect arrow orientation and stability during stops. Resume across a pause/gap and require a fresh movement baseline. Phone rotation alone must not pretend to change GPS course.
- Keep the map credit strip visible and verify its source/license/coverage/privacy dialog. No colored application accents should be introduced by controls.
- Disable network, provoke tile errors and separately exercise a native map-load failure. Confirm a visible map status, the route/fallback, unchanged saved samples, and uninterrupted recording while Android permits. Restore connectivity and check the display recovers without restarting the run.
- Background, rotate, navigate repeatedly between Record/Runs/detail, and reopen. Map lifecycle cleanup must avoid crashes, duplicate views or continued hidden rendering. No whole-region downloads should start.
- Inspect map requests using disposable synthetic data: hosts receive requested tile areas, not a GPX, timestamps, run ID or metrics. Route overlays remain local; viewing a map can still reveal the visible area to its provider.

## Replay and statistics

- Open a saved run and verify the initial paused frame and default **1×** speed, including opening a different run after using a faster multiplier. Play at every multiplier (1×, 5×, 10×, 30×, 60×), change speed during playback, seek in both directions, reach the end and restart. Confirm the recorded run itself never changes.
- Background or leave the screen during playback. Playback must pause; returning must not accumulate background playback time. Rotate and verify saved position/speed restore without automatic playback.
- Use synthetic runs with explicit pauses, interruptions, isolated fixes and a GPS gap longer than 15 seconds. Replay time must include only observed intervals and cut across gaps without a fabricated line or animated transit. Average pace/speed still use active time.
- Compare speed/pace with known GPS intervals and the trailing five-second window. Reject implausible running speed, keep stationary/missing speed meaningful and avoid using the active timer as the replay clock.
- Test valid, absent, intermittent, flat and non-finite GPS altitude. Chart gaps must remain visible; missing altitude and segment breaks reset ascent/descent accumulation. Check the 3-metre deadband, raw altitude range and unavailable-value dashes.
- Check totals and kilometre/final partial splits against known samples. Replay metrics and the altitude cursor should track seeking without persistent UI stalls; playback has a 20-update-per-second target, not a hardware performance guarantee.
- A no-route/one-point run keeps its saved time; unavailable playback controls must be disabled. Do not claim a video export: no MP4 is produced.

## GPX and Strava handoff

1. Save two runs, reopen the app and check both sessions remain distinct.
2. Tap **Strava** in the saved-run toolbar. First use explains the flow; cancel and retry. Later uses should skip the introduction.
3. On Android 10+, confirm automatic Downloads save without a storage permission or save dialog. The file must become visible only after writing succeeds. Inspect the actual XML: preserve coordinates, UTC timestamps, available altitude and pause/interruption segments.
4. Compare the persistent saved-name message with the actual provider filename, including a duplicate-name suffix. **Copy name** must copy only after a tap.
5. On Android 8/9, use the document picker. Also provoke an automatic Downloads failure and verify the picker fallback. Canceling the picker must preserve the source and not open the website.
6. Only after successful file writing should the official Strava uploader open in a browser. Test default browser, multiple browsers and no browser; an installed Strava app must not intercept the upload URL. No-browser fallback must preserve the file and offer **Copy upload link**.
7. Opening the website is not an upload and must not mark the run as shared/published. Reject no usable route and over-limit GPX before the handoff.
8. Simulate a failed/partial destination write. Keep the source, clean up the failed new Downloads item where possible, and allow retry. Process death can leave a hidden pending MediaStore item or partial picker destination; inspect and remove disposable leftovers.
9. Rotate during export, background while saving, and kill the process while the picker is open. Restore the pending run ID, defer browser launch until resumed and avoid duplicate launches. A killed write may require retry, but must never delete the source.
10. Verify **Export GPX** and **Share file** in the menu. Test cancellation and recipient readability separately from the Strava handoff.
11. On the user's phone, check whether the browser requires **Desktop site**. If file selection works, review route/date/visibility on Strava; publishing is a separate user action. Development verification must not publish a real activity merely to test navigation.

GPX contains exact start/end coordinates and timestamps. It is a route export, not a complete backup of every internal field. Do not assume an installed Strava Android app accepts GPX via `ACTION_SEND`.

## Data retention and results

Export any test recording that must survive uninstalling or clearing app storage. All internal mirrors/recovery copies reside on the same phone; they do not protect against device loss or a wiped app.

Recorded v0.4 results on 1 November 2026:

- Signed debug/release builds succeeded; 53 JVM tests passed (38 existing, nine GPS-course, six notification-action guard tests). Lint reported zero errors and 28 warnings. All 23 generated icon resources matched the pinned subset.
- The API 34 signed v0.4 update retained v0.3 and older runs. An unfinished draft survived an additional update and offered recovery. The exact packaged release APK also installed and opened successfully, retaining the new 0.14 km test run and older history. Its signature matched the existing per-app key; its checksum is in README.md.
- Synthetic east-to-north movement produced the corresponding arrow and close centering. Pan disengaged follow and the location control restored it. Light/dark, 1.3× font scale and landscape screenshot review found no material clipping in those captures. Final paused-state text and lock-screen guidance were confirmed visible without clipping.
- With the Activity absent, notification Pause/Resume/Finish worked; paused time froze. Finish removed the notification and saved 45 fixes in three segments. Notification tap from history opened recording; navigating back to history and rotating retained history.
- Lock-screen statistics were visible after enabling silent lock-screen notifications (`lock_screen_show_silent_notifications=1` on the emulator). Expanding from the lock screen opened the shade; Pause worked there. No PIN was set, so secure-lock-screen actions and physical Samsung/Now bar presentation remain untested.
- Replay opened at 1×; three seconds of playback advanced to 00:03 and Pause stopped it. Strava implementation remained unchanged; no account upload/publication was attempted.

These are bounded build/emulator checks. Unlisted cases, physical-phone field/battery behavior, Android 8/9 picker runtime and 16 KB page-size devices remain unverified.

Historical v0.3 results on 1 November 2026:

- Signed debug/release builds succeeded; all 38 JVM tests passed (10 metrics/GPX, 14 persistence, 14 replay including the peak-speed regression), and lint reported zero errors.
- API 34 installed v0.3 over the signed v0.2 build while retaining runs. The final signed release opened after installation and retained both older v0.2 recordings and v0.3 synthetic/offline runs. Actual IGN imagery, route and moving marker rendered; the density-aware route fit was corrected.
- Replay at 30×, selecting 60×, halfway seeking, Pause, background/return and rotation restoration were exercised. Light/dark, 1.3× font scale and compact landscape were reviewed. The final sharing dialog was confirmed monochrome and the map-credit target measured 48dp.
- Offline background/screen-locked recording retained seven synthetic GPS samples. Force-stop/reopen recovered the draft, and Finish saved it.
- Automatic Downloads sharing saved valid GPX with 121 samples in two segments and opened the official browser uploader without a save picker. No Strava account login, upload or publication was tested.

CI results are tracked separately in the repository's [Android workflow](https://github.com/adrianetti/runline/actions/workflows/android.yml). No physical-phone field/battery-exhaustion test, Android 8/9 picker runtime check or 16 KB page-size device test has been completed. Offline imagery is not guaranteed. Unlisted checklist cases remain unverified; the results above do not certify every failure path or device. README.md retains the earlier v0.2 results separately.
