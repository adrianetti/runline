# Version 0.2 test checklist

This is a checklist, not evidence that tests passed. Record the build/commit, phone model, Android version, battery settings and pass/fail/not-tested outcome. Use disposable synthetic recordings for destructive and fault-injection cases; keep real route files private.

## Build and unit checks

With JDK 17 and SDK 34, run:

```sh
python3 tools/import_icons.py --check
./gradlew testDebugUnitTest assembleDebug lintDebug
```

Inspect unit-test and lint reports. Persistence tests should exercise actual temporary files with injected failures, including incomplete journal appends, corrupt metadata, finalization/cleanup interruption and duplicate completion. A passing calculation test does not validate Android service lifecycle or physical storage durability.

## Installation and signing

- Verify the packaged `dist/Runline-0.2.0.apk` signature and install that exact artifact. Confirm later v0.2+ builds signed with the same per-app key update in place without deleting runs.
- Do not treat the old v0.1 debug signature as compatible. If migration requires uninstalling v0.1, export recordings first and verify the exports before uninstalling.
- Main-branch CI may receive signing secrets; pull-request builds must not. Never log key/password contents or put signing material in test artifacts.

## Start and controls

- Fresh installation: tap **Start run**, deny location, then retry with approximate-only location. Neither case should claim usable GPS recording; precise location should have a clear recovery path.
- Grant precise location and notifications. With location enabled, subsequent **Start run** taps should start without an extra confirmation. Rapid repeated taps must not create multiple drafts or services.
- Start outdoors and distinguish **Finding GPS** from accepted fixes. No distance should appear before usable movement is recorded.
- Pause, resume, then finish. Pause freezes active time and route capture; Finish creates one saved session. Repeated Finish/reopen must not duplicate it.
- Deny notifications and verify the service handles Android's foreground-notification policy without crashing. Restore notifications for normal runs.
- Inspect **Recording protection**, then open app settings. Report actual battery and notification status rather than treating those labels as a recording guarantee.

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
- In airplane mode with GPS enabled, record and reopen a run. Recording/history require no network; a cold fix can take longer. Cloud document providers and the Strava website have separate connection requirements.
- Change the wall clock during a test and exercise recovery across a simulated boot change. Active time uses a monotonic clock; recovery must not infer unobserved elapsed downtime as activity.

## Physical-phone field check — required

Record a known route for at least 30 minutes with the screen locked for most of the session. Include stationary time, a pause/resume and weak-signal sections. Note battery consumption, GPS gaps and distance against an independent route/reference. Repeat under the phone's normal and restrictive battery settings, then under the intended setting for real runs. Add a longer test representative of normal use.

Do not deliberately run down a phone that is needed for navigation or communication. Test power-loss recovery in a controlled setting, or use abrupt emulator termination for the crash-consistency check. A dead battery cannot produce further GPS samples; evaluate retention and recovery of prior committed data separately from continuity.

## UI and accessibility

- Confirm **Record**, **Runs**, **Start run**, **Pause**, **Resume**, **Finish** and **Share to Strava** are easy to find, with one Tabler icon family and neutral colors.
- Check light/dark mode, 1.3 font scale and a smaller supported phone. No clipped measurements, labels or actions; system-bar icons must retain contrast.
- Check TalkBack labels for toolbar actions, icon/text controls and route content. Decorative icons should not repeat adjacent labels.
- Rotate during recording and during a Strava file save. Preserve the current session and avoid duplicate export or browser-launch events.
- Check Android Back from run details and history. Discard/Delete must still ask for confirmation even though Finish is direct.

## GPX and Strava handoff

1. Save two runs, reopen the app and check both sessions remain distinct.
2. Open one and use **Share to Strava**. First use explains the flow; later uses skip that introduction.
3. Cancel the document picker. The source run remains intact and the website does not open.
4. Choose Downloads and save. Inspect the actual file as XML before counting this check as passed. Every exported point should preserve coordinates and UTC timestamp, with available altitude and separate pause/interruption segments.
5. Only after successful file writing should the Strava website uploader open. Opening the website is not an upload and must not mark the run as shared/published.
6. Simulate a failed/partial destination write and an unavailable browser. Keep the source, show a useful recovery route, and do not report an upload. Retry and remove any incomplete test destination.
7. Rotate during export and kill the process while the picker is open. Restore the pending saved-run ID correctly. A process death during writing may require retry, but must never delete the source recording.
8. Verify **Export GPX** and **Share file** in the menu. Test cancellation and recipient readability separately from the Strava handoff.
9. On the user's phone, check whether the browser requires **Desktop site**. If file selection works, review route/date/visibility on Strava; publishing is a separate user action. Development verification must not publish a real activity merely to test navigation.

GPX contains exact start/end coordinates and timestamps. It is a route export, not a complete backup of every internal field. Do not assume an installed Strava Android app accepts GPX via `ACTION_SEND`.

## Data retention and results

Export any test recording that must survive uninstalling or clearing app storage. All internal mirrors/recovery copies reside on the same phone; they do not protect against device loss or a wiped app.

Version 0.2 results are pending final verification. Record completed checks, remaining warnings and untested cases in README.md. Historical v0.1 results do not cover the new recorder/storage implementation or simplified sharing flow.
