# Version 0.10.1 test checklist

The checklist is not a blanket pass report. Current v0.10.1 and historical v0.10 evidence are recorded below; historical v0.8/v0.7/v0.6/v0.5/v0.4/v0.3 evidence is retained separately and in README.md. Record the build/commit, phone model, Android version, battery settings and pass/fail/not-tested outcome. Use disposable synthetic recordings for destructive and fault-injection cases; keep real route files private.

## Build and unit checks

With JDK 17, SDK 34 and Build Tools 36.0.0, use the pinned AGP 9.5.0 / Kotlin 2.4.21 / Gradle 9.8.2 build:

```sh
python3 tools/import_icons.py --check
./gradlew testDebugUnitTest assembleDebug assembleRelease lintDebug
```

Inspect unit-test and lint reports. Persistence tests should exercise actual temporary files with injected failures, including incomplete journal appends, corrupt metadata, finalization/cleanup interruption and duplicate completion. A passing calculation test does not validate Android service lifecycle or physical storage durability.

Toolchain upgrade validation on 2 November 2026 (AGP 9.5.0 / Kotlin 2.4.21 / Gradle 9.8.2):

- Debug and signed release builds passed, with **137 JVM tests**, zero failures, and **zero lint errors / 78 warnings** from the newer lint engine. No lint errors were suppressed. The 401 bilingual resources and 27 pinned icons passed verification.
- A standalone audit resolved **242 Maven component versions across 39 configurations**, including 68 lint-tool components, and OSV returned **zero findings**. The same checker detected all 60 original GitHub alert/package pairs against their old versions. Its 17 offline tests passed. Deliberately removing lint's default dependency made the exporter fail and remove its stale inventory.
- The wrapper JAR and distribution checksums matched upstream. A runtime classloader check confirmed the Android plugin uses patched Guava 33.7.2-jre; Gradle core still uses its separate affected bundled copy. See the [remaining tooling limitation](../tools/security/README.md#dependency-advisories-and-toolchain-limits).
- Gitleaks found no secrets in the inspected history/current source. All seven Semgrep rule fixtures and five Android surface guard tests passed; the strict Semgrep scan of 49 production Kotlin files found no issues.
- On the API 34 emulator, the signed candidate updated the installed v0.7 release without uninstalling. All **47 recording, draft, annotation, photo and plan files** were initially byte-for-byte unchanged, including **19 saved activities**. The paused draft reopened as recovered with its original time and distance. Resume, background GPS capture, and notification Pause/Resume/Finish saved exactly one additional activity with **19 points in three segments**, removed the draft and retained all prior saved data. Native satellite rendering also worked in this bounded check.
- A fresh ZIP export contained **20 GPX files** and a **21-column CSV with 20 activities**. The annotated two-hour fixture retained **7,201 points in two segments**. These runtime checks use Kotlin's upgraded 2.4.21 standard library as well as the new compiler.

These toolchain checks do not repeat the entire field checklist or certify physical Samsung, battery, real-provider AI or headphone behavior. That maintenance check retained the published v0.7 release and did not publish a new app release.

Activity-type/persistence tests should cover all three types, legacy fallback to Run, recovery/finalization and GPX typing. Filename tests should cover locale formatting, distance/date/time and forbidden characters. Elevation-progress tests should cover incremental updates, reset/shrink, missing height and segment/gap breaks. Marker-motion tests should cover valid interpolation, invalid/implausible samples, accuracy, gaps, segments, shortest longitude/bearing and endpoints.

Notification-action unit tests should reject wrong/missing run IDs, stale Pause/Resume/Finish actions and Finish after recording has resumed. Heading tests should cover movement, stationary jitter, accuracy, turns, gaps/segments, invalid samples and date-line crossing. Unit tests do not establish lock-screen behavior.

Replay tests should cover time/distance interpolation, explicit segments, gaps over 15 seconds, missing/non-finite altitude, elevation filtering, speed windows, isolated points, seek boundaries and date-line crossing. Check dependency/license notices for the pinned MapLibre SDK and map data. Successful HTTP tile requests alone are not evidence of native map rendering.

## Version 0.10.1 real-provider validation

On 3 November 2026, a bounded external JVM harness exercised the production provider, request builders and validators. Its initial eight generations used four Smart cases and four live cases, including private GPX-backed summaries. The route and raw reports remain outside Git. No coordinates or photos entered the provider requests. Missing accuracy, effort and active-timer data remained explicitly unknown; observed GPX elapsed time was a disclosed import proxy, and the hypothetical live goal was labelled as simulated.

All eight initial generations returned HTTP 200 and normal completion, but this was **not a coaching-quality pass**: two Smart responses violated the output contract, one live response confused a partial split's duration with speed, and one routine cue should have been silence. The fixes require Smart JSON schema output while retaining strict numeric validation, normalize split paces, compare adjacent continuous GPS windows and skip clearly unnecessary live requests locally. Whole-second pace bounds also ignore sub-microsecond floating-point noise.

The updated signed debug/release builds pass **252 JVM tests**, including regressions for those failures, with **zero failures/errors** and **zero lint errors / 106 warnings**. All **464 bilingual resources**, **34 pinned icons**, **22 security-checker tests**, workflow lint and **seven Semgrep rules across 73 production Kotlin files** pass. Independent review verified that unknown or unusually long/high-volume runs remain eligible for AI advice. The applicable GitHub checks must pass on the exact PR head before merging.

The five-case follow-up evaluated three Smart scenarios, the GPX-backed live replay and the stable-run silence case. All three Smart responses used the requested JSON schema and passed local validation on the first attempt. The live replay returned explicit provider `NONE`; the stable case returned local silence with **zero API requests**. This used **four generations**, all HTTP 200 / normal completion, with no retries or truncation. The first follow-up still overstated what sparse history could say about a desired goal's timing, so the prompt was tightened and **one final Smart generation** verified that it explicitly distinguishes an app data limit from fitness and leaves the user's aspiration/timing unchanged. That final structured response passed validation. Across the original batch and the two follow-ups, **13 generations** used **28,649 input / 4,110 output tokens**; typical observed latency was 1–5.4 seconds. These are a small set of examples, not statistical reliability or training-readiness evidence. The sparse-history distance cap itself was not relaxed.

The exact candidate is **`dist/Runline-0.10.1.apk`**, **0.10.1 / code 11**, SHA-256 **`07b9b55eccfbab4000bae89e6f4947f4e30ff74427278ffd51f592a92b07a44f`**. Its signing certificate matches the existing stable certificate. These changes do not migrate recording storage. Physical-phone installation, Android TTS/music ducking and field coaching are not established by the headless provider test.

For authorized future local tests, the developer key is stored separately in `~/.config/runline-dev/anthropic.env` (directory `0700`, file `0600`). It is outside this repository and is never added to CI, APKs, logs or reports. The local test runner checks ownership/permissions, reads the credential without printing it and passes it to its child through stdin, rather than command arguments. Every paid test batch has an explicit request cap, no automatic retries and an exclusive attempt ledger to prevent accidental reruns.

## Version 0.10 Smart goal checks

- Preserve recordings, annotations/photos and the legacy plan file through an in-place signed update. Migrate only the old goal once; clearing the optional goal must not bring it back. No calendar UI or automatic plan requests remain.
- Smart preparation must be explicit, single-flight and rotation-safe. Verify disabled/setup, loading, result, rest, error and stale-input states with no generation on opening. Fake transports cover privacy, bounds, malformed output, truncation, network errors and discarded results.
- Use/Adjust must recheck the current result. Use saves a concrete goal without starting capture; Adjust is a draft until Save. Errors do not change a previous manual target. Profile/notes consent stays separate.
- Manual Distance/Pace/Time/Distance within time and optional pace ranges must validate, persist and survive rotation. Time uses active recording duration, excluding pause; GPS loss cannot fabricate distance/pace, and recovery must not replay an old completion.
- Review English/Spanish, light/dark, 1.3× text, keyboard and landscape; inspect fixed Save and real indeterminate loading indicators. Live AI must respect accepted recovery intent and return silence when no useful new fact exists.

## Version 0.10 recorded checks

Recorded on 3 November 2026 with synthetic history and an API 34 emulator:

- Signed debug/release builds passed **233 JVM tests**, with zero failures/errors. Lint reported **zero errors and 106 warnings**. All **464 bilingual resources** and **34 pinned icons** passed checks.
- Native checks covered AI-off setup, the overall-goal field and rotation, manual goal choices, loading through rotation, distance/time results, Adjust/Save, rest and error states. **4.0 km with a 6:00–7:00 /km band and Easy intent** survived Adjust and rotation; **25 minutes with that band** applied to the Record screen. Rest/error offered no Apply action and kept existing settings. A temporary external harness injected those response states without contacting a provider; its invalid credential sentinel and preference backups were removed afterward.
- Independent screenshot review found no material clipping in the inspected English/Spanish, dark, 1.3× text and landscape screens. Loading used the native indeterminate indicator, disabled repeat generation and retained Close. These are bounded captures, not a general animation performance benchmark.
- The exact signed release installed in place and retained **all 49 inspected original files byte-for-byte**, including **22 saved activities**, recovery files, annotations, a photo and the old plan. Only the new overall-goal store is used; the old calendar file is not rewritten. Coaching preferences were restored after fixture checks.
- Local security checks passed: **22 scanner regression tests**, Android exposure boundaries, workflow lint and **seven Semgrep rules across 71 Kotlin files**. Dependency audit covered **242 Maven versions / 39 configurations** with zero reported OSV findings. GitHub check and merge evidence is recorded on the release PR. The separate upstream build-tool limitation remains documented in [CHECKS.md](CHECKS.md).
- Exact artifact: **`dist/Runline-0.10.0.apk`**, **0.10.0 / code 10**, SHA-256 **`d8a04c9a956ce5890e2b3797ced5b43f42dfce3ca4aff30878338923a7f36d6f`**. Its certificate matches the stable per-app signing key used since v0.2.

No paid Anthropic generation, prospective validation of Smart goal advice, physical Samsung test or new music-player test was performed. Prior recording/audio evidence retains its versioned scope.

Historical results below retain their original scope.

## Version 0.9 recorded checks

Recorded on 2 November 2026. The final builds used JDK 17; native checks used the API 34 emulator and disposable synthetic fixtures.

- Final debug and release builds passed **197 JVM tests with zero failures**. Lint reported **zero errors and 117 warnings**. Checks passed for **494 bilingual resources** and **34 pinned icons**.
- The **five Android boundary tests** and **17 offline OSV checker tests** passed. The project inventory resolved **242 Maven versions across 39 configurations**, with **zero OSV findings**. Strict Semgrep checked **65 production Kotlin files against seven local rules**, with **zero findings**. Actionlint passed. These project checks do not resolve the separately documented [bundled-Gradle limitation](../tools/security/README.md#dependency-advisories-and-toolchain-limits).
- The first candidate installed over the existing app without uninstalling. All **47 inspected files were byte-identical** immediately afterward, including **21 activities**, the private photo and plan. This comparison precedes the intentional profile/effort fixture edits below. The exact final release APK was subsequently installed in place as well.
- Native AI settings: **All AI features** enabled live advice, automatic reviews and automatic adaptation plus the module. Turning an individual feature off updated the aggregate switch; choices survived rotation. Activity-note sharing remained off, and missing-key Save was rejected. No real Anthropic request was made.
- Native profile: the unsaved draft survived rotation without storing sensitive fields in a Bundle; saving retained **Include profile in AI = false**, and explicit **Clear profile** removed it. Profile/context and response behavior are additionally covered by pure and fake-transport tests, not paid generation.
- Native activity editor: **effort 8/10** survived rotation and saving. Slider zero saved as **Unrated**, and the fixture's prior **6/10** was restored afterward.
- Native two-hour replay opened at **240×**, with a free-camera arrow, estimated terrain label and observed-time elevation cursor rendered. No frame-rate or physical GPS/elevation accuracy claim follows from these captures.
- Background GPS capture, force-stop recovery, resume and notification Pause/Finish saved one additional activity with **52 points in three segments**, leaving **22 saved activities** and no active draft. All **45 unedited original files** remained byte-identical; only the two deliberately edited effort metadata copies changed.
- The installed system voice reported ready; audio diagnostics recorded transient navigation-speech focus requests and matching releases for both preview and a live cue. No actual music player or headphones were used.
- English/Spanish, dark mode, 1.3× text and landscape captures exposed a replay-header overlap; applying island limits before child measurement fixed it. The final landscape capture keeps Back/options clear, with the information area scrollable and playback controls fixed.
- Exact artifact: **`dist/Runline-0.9.0.apk`**, version **0.9.0 / code 9**, SHA-256 **`97eaed4ae16c43d8d61a94b21cfed742ac2affac4037c26d7e4d73dba2369a77`**. Signing-certificate SHA-256 **`fc4cf764f78b745c431a632e5769348ec6488e04b4ca92515a9f589564f900ab`** matches the stable per-app key.

CI and release results are tracked in the private repository; require every applicable check to pass for the exact PR head before merging. No real-provider generation, controlled Samsung field/battery test or actual Spotify/headphone ducking has been performed in this round. Unlisted checklist cases remain unverified.

## Version 0.9 feature checks

- Profile: start blank with sharing off; edit experience/preferences/health notes, rotate, save, reopen, clear and dismiss. Sensitive drafts may survive rotation in memory but must not enter saved-state Bundles, autofill, logs, backups or activity exports. Test no profile fields in live/review/plan requests while sharing is off, bounded fields when explicitly on, and revocation while a request or speech item is pending. Clearing/changing privacy settings must remove earlier profile-derived speech history from future context.
- AI features: bulk selection controls the three feature switches and module, individual changes update the aggregate, and neither bulk operation opts into notes or profile sharing. Restore choices after rotation. Missing-key setup must fail clearly; disabling remains possible with invalid drafts. Use fake transport for context, silence (`NONE`), malformed response and revocation cases; a fake pass does not validate a real provider.
- Context: retain exact-day versus approximate-month versus rolling-goal meaning, today's intended session, recent adherence, similar activities and optional self-reported effort. Omit raw coordinates/photos and avoid invented health/heart-rate/effort. Only matching TTS start callbacks count as actually spoken feedback; generated, rejected or expired speech must not enter that history.
- Effort: exercise native slider zero/unset and every rating from 1–10 with touch, keyboard and TalkBack; rotate and save. Keep Save reachable with the IME and large text. Error text must become visible after an idle state, including a missing photo picker or failed save.
- Map: compare current elevation cursor with the replay's observed clock across pauses, GPS gaps, missing/poor heights and isolated endpoints. Terrain stays unknown for insufficient/noisy/stopped data; switching between live/replay must not invent a grade. Pan, pinch, fit, recenter and replay/seek with the projected arrow; an offscreen fix must disappear instead of clamping to the edge. Background/dispose must stop pending display work; a stationary map must not keep an idle marker-polling loop running.

## Version 0.8 recorded checks

Recorded on 2 November 2026. Builds and the following runtime checks used the integrated v0.8 source and the API 34 emulator; recordings were disposable synthetic fixtures.

- Debug/release builds completed with **158 JVM tests**, **zero failures**, and **zero lint errors / 117 warnings**. The **466 bilingual resources** and **29 pinned icons** passed their checks. Five Android surface-boundary tests and 17 offline OSV checker tests passed. The complete project inventory resolved 242 Maven versions across 39 configurations; OSV returned zero findings. The final source-security scan checked 55 Kotlin files against seven local rules with zero findings. This inventory does not cover the known bundled-Gradle finding described in [security checks](../tools/security/README.md#dependency-advisories-and-toolchain-limits).
- An in-place update preserved all **45 inspected saved files byte-for-byte**, including **20 activities**, metadata, the private photo and the existing version 1 plan. This is update-retention evidence; it does not establish every storage or recovery failure path.
- With AI off, model choices saved locally. A malformed unsaved draft key did not prevent disabling AI; the dummy credential was removed without making requests. Repeated fast swipes within settings/forms kept sheets open; the fixed Close action remained available. The latest Save layout remained visible above the IME. No real Anthropic generation was performed.
- The training month picker and its selection survived rotation. Switching from an exact March 1 date to month precision preserved March. Selecting Personal progress hid date controls. Existing version 1 plan data survived the update; this check does not claim a generated AI plan or paid adaptation.
- Background synthetic GPS recording and force-stop recovery completed one additional saved recording, bringing the history to **21 activities**. This bounded case does not establish every shutdown, battery or storage failure path.
- The two-hour replay fixture opened at **240×**. Native views showed the label-free imagery, heading-follow camera and elevation island. Spanish replay details/options hid unavailable AI actions. Downloads, Share file and Save to… remained available from the GPX export control; this round does not establish a new recipient upload or full export-content comparison.

- A fresh **Personal progress** plan used recorded **7.4 km/week** and three days/week automatically, with an eight-week rolling window and no race date. The previous test plan was restored afterward.
- The exact signed **0.8.0 / version code 8** package installed in place and retained **21 saved activities** and the previous plan, including its Skipped state. APK SHA-256: `be0eaa63018f1d29aa4598d57b63ae3d5529e9559b384eba5cb7e7a769c8b48c`; stable signing-certificate SHA-256: `fc4cf764f78b745c431a632e5769348ec6488e04b4ca92515a9f589564f900ab`.

Independent review of the inspected dark-mode, 1.3× text and landscape captures confirmed the fixes and found no additional control or layout issues. No physical Samsung field/battery, actual music/headphone ducking or real-provider generation is claimed. Unlisted checklist cases remain unverified for v0.8.

## Installation and signing

- Verify the packaged `dist/Runline-0.9.0.apk` signature and version name/code (0.9.0 / 9); install that exact artifact. Update an existing signed v0.8 build with saved runs and an unfinished draft, using the same per-app key. Confirm recordings survive and no uninstall is needed.
- Do not treat the old v0.1 debug signature as compatible. If migration requires uninstalling v0.1, export recordings first and verify the exports before uninstalling.
- Main-branch CI may receive signing secrets; pull-request builds must not. Never log key/password contents or put signing material in test artifacts.

## Start and controls

- Idle Record must show a Run/Walk/Hike dropdown and Start, without an idle map. Select each type, navigate/rotate/relaunch and confirm the choice is remembered. Record, pause, recover and finish each type; history and GPX must retain it. Legacy untyped recordings stay Run rather than being classified from speed.

- Fresh installation: choose an activity and tap **Start**, deny location, then retry with approximate-only location. Neither case should claim usable GPS recording; precise location should have a clear recovery path.
- Grant precise location and notifications. With location enabled, subsequent **Start** taps should start without an extra confirmation. Rapid repeated taps must not create multiple drafts or services.
- Start outdoors and distinguish loading/finding GPS from accepted fixes. Hide the initial zoomed-out map until a usable fix exists; disabled-location/permission status must stay actionable. Recenter before a fix must explain waiting, then work once the fix arrives. No distance should appear before usable movement is recorded.
- Pause, resume, then finish. Pause freezes active time and route capture; Finish creates one saved session. Repeated Finish/reopen must not duplicate it.
- Deny notifications and verify the service handles Android's foreground-notification policy without crashing. Restore notifications for normal runs.
- Inspect **Recording protection**, then open app settings. Report actual battery and notification status rather than treating those labels as a recording guarantee.

## Notification and lock-screen controls

- While recording, expand the ongoing notification. Verify distance, average pace, active-time chronometer and relevant GPS status. Metrics refresh periodically; only the chronometer needs to advance each second.
- Tap Pause from the notification while the app is backgrounded. Confirm capture and active time stop, the timer freezes, and Resume/Finish replace Pause. Tap Resume and verify capture restarts in a new segment; pause again, then Finish and confirm one saved run and removal of the ongoing notification.
- Keep a stale action available where the test harness permits. Wrong-session actions must not alter another run; an old Finish must not finish a session that has resumed. No stale action should disable sticky recovery of a currently active session.
- From Activities or a saved detail screen, tap the notification body and confirm the active recording screen opens. Rotate, return to history and repeat.
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
- A no-fix run may retain its timer, but route/pace must remain unavailable. Unavailable replay controls must be disabled, with no fabricated route.
- In airplane mode with GPS enabled, record and reopen a run. Recording/history/replay calculations require no network; a cold fix can take longer. Maps may have no imagery outside cached tiles, but recording and geometric route display must remain usable. Cloud document providers have separate connection requirements.
- Change the wall clock during a test and exercise recovery across a simulated boot change. Active time uses a monotonic clock; recovery must not infer unobserved elapsed downtime as activity.

## Physical-phone field check — required

Record a known route for at least 30 minutes with the screen locked for most of the session. Include stationary time, a pause/resume and weak-signal sections. Note battery consumption, GPS gaps and distance against an independent route/reference. Repeat under the phone's normal and restrictive battery settings, then under the intended setting for real runs. Add a longer test representative of normal use.

Do not deliberately run down a phone that is needed for navigation or communication. Test power-loss recovery in a controlled setting, or use abrupt emulator termination for the crash-consistency check. A dead battery cannot produce further GPS samples; evaluate retention and recovery of prior committed data separately from continuity.

## UI, localization and accessibility

- On a fresh install, verify phone language is followed, with English fallback and Spanish support. Upgrade v0.5 and confirm its forced-Spanish override is cleared once. Select explicit English/Spanish, rotate/relaunch and ensure later choices are retained. Return to Phone language and change the system locale. On Android 13+, also check per-app system settings; test the pre-13 custom-storage path separately.
- Inspect both languages in the activity picker, history/charts, active/recovered/error states, replay/details, map credits/status, coaching settings/preview, protection guidance, notification actions and exporter. Activity names, dates and decimal separators must follow the app locale. GPX numeric XML/UTC timestamps must remain standards-compliant.
- Confirm Record, Activities, Start, Pause, Resume and Finish are easy to find. The saved replay has Back/options and an accessible download control. There is no Strava button or automatic website navigation. Use one Tabler family and neutral controls; imagery stays in real colors.
- Check light/dark mode, 1.3× font scale and a smaller supported phone in both languages. No clipped values/labels/actions; system-bar icons retain contrast. Spanish playback labels should align without truncating the metrics.
- Inspect full-content live and replay maps in portrait and landscape. Islands sit bottom or lower left; scrollable information must not hide the recording/playback controls. Marker, credits and map controls avoid overlays. System bars remain visible. The idle screen has no map.
- Check TalkBack names/order for activity selection, floating controls, fit/follow, playback speed, seeking, Details, elevation profile, export and map credits. Decorative icons should not repeat adjacent labels. The whole statistics area and explicit Details action must open the sheet.
- Rotate during recording, replay, details and file export. Preserve the session and replay position/speed; avoid duplicate exports or result messages. Back from details/history stays coherent. Discard/Delete still require confirmation even though Finish is direct.

## Satellite maps and network failure

- Check detailed IGN/PNOA imagery in Spain, global EOX imagery outside Spain, and road/place labels. Compare the visible area with the intended route; record tile provider, zoom and coverage instead of assuming street-level global detail.
- Pan, pinch, fit the entire route and follow the moving marker. Pan/pinch should stop following until explicitly re-enabled. Live follow returns to zoom 18 with the marker centered in the unobscured area; the map stays north-up. Include a route crossing the date line and a viewport near Spain's boundary.
- Begin stationary: the marker stays neutral until accepted movement exceeds GPS noise. Move along a known bearing and turn; inspect arrow orientation and stability during stops. Resume across a pause/gap and require a fresh movement baseline. Phone rotation alone must not pretend to change GPS course.
- Feed consecutive safe GPS fixes and confirm marker and following camera move together smoothly with a bounded display delay. Pause/background/dispose must stop animation. Segments, long gaps, invalid/poor fixes and implausible jumps must snap without animated bridges. Compare stored points and exported coordinates before/after to prove display-only behavior. Include date-line and shortest-bearing cases.
- Keep the map credit strip visible and verify its source/license/coverage/privacy dialog. No colored application accents should be introduced by controls.
- Disable network, provoke tile errors and separately exercise a native map-load failure. Confirm a visible map status, the route/fallback, unchanged saved samples, and uninterrupted recording while Android permits. Restore connectivity and check the display recovers without restarting the run.
- Background, rotate, navigate repeatedly between Record/Activities/detail, and reopen. Map lifecycle cleanup must avoid crashes, duplicate views or continued hidden rendering. No whole-region downloads should start.
- Inspect map requests using disposable synthetic data: hosts receive requested tile areas, not a GPX, timestamps, run ID or metrics. Route overlays remain local; viewing a map can still reveal the visible area to its provider.

## Replay and statistics

- Use a two-hour fixture with thousands of samples. Replay should occupy nearly the full content area, with a compact reachable island. Tap statistics/Details: playback pauses and a sheet exposes totals, prominent climb/descent, relative elevation profile, secondary raw altitude/range and splits. Close it and continue without losing position/speed. Check landscape and larger Spanish text.

- Open a saved run and verify the initial paused frame and the available multiplier whose playback duration is nearest **30 seconds**, computed from observed GPS time. Opening a different run must calculate its own default; rotating the current run must preserve a manual choice. Play at every multiplier (1×, 5×, 10×, 30×, 60×, 120×, 240×, 480×), change speed during playback, seek in both directions, reach the end and restart. Confirm the recorded run itself never changes.
- Background or leave the screen during playback. Playback must pause; returning must not accumulate background playback time. Rotate and verify saved position/speed restore without automatic playback.
- Use synthetic runs with explicit pauses, interruptions, isolated fixes and a GPS gap longer than 15 seconds. Replay time must include only observed intervals and cut across gaps without a fabricated line or animated transit. Average pace/speed still use active time.
- Compare speed/pace with known GPS intervals and the trailing five-second window. Reject implausible running speed, keep stationary/missing speed meaningful and avoid using the active timer as the replay clock.
- Test valid, absent, intermittent, flat and non-finite GPS altitude. Profile gaps must remain visible; missing altitude and segment breaks reset ascent/descent accumulation. Check the 3-metre deadband, first-available-height baseline, accumulated climb/descent, secondary raw altitude range and unavailable-value dashes. Do not label raw GPS estimates as terrain-corrected or display an unsupported grade percentage.
- Check totals and kilometre/final partial splits against known samples. Replay metrics and the elevation-profile cursor should track seeking without persistent UI stalls. Route/profile geometry must not be rebuilt every frame on a long recording; playback has a 30-update-per-second target, not a hardware performance guarantee.
- A no-route/one-point run keeps its saved time; unavailable playback controls must be disabled. Do not claim a video export: no MP4 is produced.

## GPX file export

1. Save Run, Walk and Hike recordings and reopen; sessions and activity types remain distinct. Older untyped sessions remain Run.
2. Tap the saved replay's download control or Export GPX menu item. No Strava introduction, browser launch, OAuth screen or account connection should occur anywhere in the export flow.
3. On Android 10+, choose Downloads and confirm saving without broad storage permission or a save dialog. Save to… must always open the document picker. The Downloads item becomes visible only after writing succeeds. Validate GPX coordinates, UTC timestamps, available altitude, activity type and pause/interruption segments.
4. Check readable filenames in both app languages: activity, recorded kilometre distance, local start date/time and `.gpx`. No inferred street/location or opaque UUID filename. The persistent success message must match the actual provider filename, including duplicate suffixes, and its Share action must open the chooser only after a tap.
5. On Android 8/9, use the document picker. Provoke automatic Downloads failure and verify the picker fallback. Cancellation leaves the source intact and launches nothing else.
6. Export the two-hour fixture. GPX encoding/destination I/O must remain off the UI thread; panning/navigation should remain responsive. Repeated export taps while saving/picking must not start concurrent writes.
7. Simulate failed/partial destination writes. Preserve the source, clean up the newly created Downloads row where possible and allow retry. Process death may leave a hidden pending item or partial picker destination; inspect/remove disposable leftovers.
8. Rotate during encoding/writing and while the picker is open. Background the app during completion: deliver a single result only after returning to resumed state. Kill/recreate during the picker; restore the prepared cached file reference, filename and MIME type. Evict that cache file and confirm failure leaves source history and an existing chosen destination intact. A killed write may require retry but must never delete a session.
9. Test Share file separately through Android's chooser, including cancellation and a recipient that reads GPX. Receive a first shared URI after preparing another export and confirm its content remains unchanged. Check read-only grants and matching GPX/CSV/ZIP MIME types. A listed recipient is not proof it can import a completed workout; do not claim an account upload was completed.
10. Preserve both source recording and a previously exported file after a later export fails. Check local/cloud document providers separately; the latter may need their own network/account.

GPX contains exact route coordinates and timestamps and is not a complete backup of every internal field. [EXPORTS.md](EXPORTS.md) describes the implementation; [STRAVA.md](STRAVA.md) is a historical archive only.

## History charts and whole-history files

- Check four Monday-start local calendar weeks across a DST boundary, year/month changes, the current partial week and future timestamps. Empty weeks show honest zero distance; an empty period does not invent a trend.
- Filter All/Run/Walk/Hike. Filters change charts only; history rows and Share/Export all still include every saved activity. Preserve the selected chart type across rotation and language recreation.
- Compare period pace with total eligible active time divided by total distance, not the mean of individual paces. No-GPS recordings contribute count/time but no zero-pace observation. Pace trends require a single activity type, at least two eligible observations and at most the latest eight in the window.
- Inspect English/Spanish, light/dark, 1.3× text, narrow portrait and landscape. Test horizontal filter scrolling and TalkBack descriptions of every chart value.
- Export and share both standalone CSV and full ZIP from history. Parse CSV quoting/headers/decimal conventions; inspect units, unavailable blanks, UTC/local timestamps, activity types and sample/segment counts. CSV must contain no raw route-coordinate columns.
- Inspect ZIP entries: README, CSV and a unique readable GPX per saved activity. Every CSV `gpx_file` must match a ZIP entry; no unfinished draft is included. Compare GPX samples/segments/altitude with saved sessions, including the two-hour fixture.
- Exercise direct Downloads, post-save Share and Save to… for all three formats, repeated filenames, cancellation and failed writes. Test Google Drive only when installed/configured; record a real recipient upload separately from showing an Android chooser/picker. Do not infer ChatGPT import success without actually testing the target.

## Optional voice coach

- Verify guidance defaults off and no TTS initialization/focus request is needed for ordinary recording. Open settings before and during recording; preserve unsaved draft fields through rotation. Save valid goals, expose distance/time/pace validation errors and allow disabling despite an invalid draft.
- Preview English and Spanish with installed offline voices. Exercise missing voice data, absent/broken engine, initialization timeout, repeated preview taps, settings navigation and cancellation/backgrounding. Source recordings and the service must remain intact.
- Simulate none/distance/pace/distance-with-active-time goals. Cover exact boundaries, within/over time completion, every-kilometre enabled/disabled and one kilometre remaining. Explicit pauses must not consume goal time.
- Feed sufficient fresh movement to exercise warmup, rolling pace, stable-band confirmation, tolerance and cooldown. Test poor accuracy, stale GPS, stops, invalid coordinates, timestamp regressions and new segments; no stale reminder should fire when data becomes unreliable.
- Enable/change coaching mid-activity or recover a draft. Do not speak previous kilometre/goal catch-up. Busy speech may retain at most three replaceable slots: goal, newest milestone and one routine cue, never a FIFO backlog. Verify priority, validity and expiry: goal 90 seconds, milestone/terrain/morale 60, AI 75 and pace 25. Pause/error/focus loss must invalidate pending work. Routine feedback waits 85 seconds after speech finishes; waiting holds no audio focus.
- Use a real music player and headphones: confirm temporary lowering or player-appropriate pause, restore after completion, and no global volume change. Repeat across foreground/background/screen lock, wired/Bluetooth, another voice prompt, calls, denied focus and engine error/watchdog timeout. Log Android/player/TTS/route versions. An emulator/fake TTS pass is not Spotify or Samsung evidence.
- Pause/resume/finish and close/reopen the Activity while coaching. Only recording sessions can produce live cues; speech failure must not block point persistence, timer checkpoints or notification controls.

## Version 0.7 checks

- Replay: select the available speed nearest a 30-second playback, including 240× for a two-hour observed timeline. Preserve manual speed/position through rotation. Follow an arrow at close zoom, pan/fit/recenter, and inspect movement between frames. The position feature and camera must update together. Pause/gap boundaries and isolated endpoints must never create a connecting trace or heading. Inspect portrait/landscape and English/Spanish.
- Details: Finish must commit the route before showing its optional editor; reopen older recordings to edit title/notes/effort/activity/routine. Save, cancel, rotate and return from the photo picker. Import/view/remove private photos, exercise a failed import, and compare original GPS-file checksums before/after. Include nullable effort and missing/corrupt annotation copies. Export title/notes/type and 21-column CSV; verify photo exclusion and safe filenames/formula-like text.
- Coach: new subfeatures default on only inside enabled voice guidance. Test their independent toggles, sustained pace/hysteresis, terrain suppression, repeated-loop confidence and nonrepeating phrases in both languages. Lifecycle/priority rules remain necessary with AI enabled or unavailable.
- AI: all options begin off. Empty-key setup must be actionable. Save a disposable dummy key locally, inspect ciphertext/no plaintext preferences, rotate and remove it; never send a real key to logs or test artifacts. Fake transports cover auth/model/rate/network/malformed responses. Check disabled/settings-changed/deleted-activity results are rejected, network work never blocks persistence, and live attempts remain capped across coordinator recovery. Actual paid provider requests require a user-configured key and separate evidence.
- Plans: create an explicit local distance/date/frequency/baseline schedule, persist/reopen it and mark sessions. Completed/missed reconciliation must not stack catch-up work or match one activity twice. Reject invalid/malformed AI changes, preserve manual changes that race a response, enforce bounds in code and retain the last valid plan on failure. Automatic adaptation is separately opted in and capped per day.
- Layout: verify Spanish Share/Export stay aligned at ordinary and 1.3× font size; larger text may stack both actions equally. Inspect new forms, model picker, photos, review and schedule in light/dark and landscape. Secret text must not be restored in a Bundle.

V0.7 evidence recorded on 2 November 2026 on the API 34 emulator, using disposable synthetic data:

- **137 JVM tests passed**. Lint reported **zero errors and 54 warnings**. The **27 pinned icons** matched their sources, and **401 English/Spanish string, plural and array resources** passed parity checks.
- The two-hour fixture’s editable title **Park laps**, notes, **effort 6/10**, **Easy** routine and **one private photo** persisted. The photo-picker flow survived rotation, and a later debug update retained those details and the photo. The original recording file’s hash was unchanged after editing metadata.
- Parsed GPX retained **7,201 points and two segments**; title and notes matched the saved annotations. This is evidence for the inspected fixture, not every export/provider failure path.
- Creating a local **21.1 km** plan for **1 March 2027** produced **51 sessions**. Reopening retained the plan and a manually marked **Skipped** session. No AI-generated plan or live adaptation is claimed from this local check.
- A disposable dummy API key was stored encrypted, settings reopened/rotated, and the key removed. Empty-key validation was exercised locally. **No HTTP request or paid Anthropic call was made**. These checks cover local key handling and form behavior, not real credentials, model availability or generated advice.

- Finishing a brief synthetic Walk saved the route before automatically opening the optional editor. Closing without edits kept it, and portrait/landscape recreation did not reopen the editor or duplicate the run.
- Bounded English/Spanish review covered activity details, private photos, coaching, AI settings, the training plan and history actions, including 1.3× text, dark mode and landscape. Spanish Share/Export actions remained equal-sized, stacking at larger text. No material clipping was found in the inspected views.
- The two-hour fixture automatically selected **240×**. Accelerated replay kept its arrow centered on the route through sampled turns; pan and recenter worked. A native follow marker avoids asynchronous map-symbol lag. These are bounded visual checks, not a frame-rate benchmark.

- The exact **0.7.0 / version code 7** packaged release installed in place. A new **21-column CSV** contained all **19 saved activity IDs**, including the new finish-flow test and edited metadata. The saved photo and local plan, including its Skipped status, remained visible after updating.
- APK SHA-256: `41b138be24f517529e8c7ff099766db082cfd714ae0702758bc01deb43d81798`. The signing certificate matched the stable app key: `fc4cf764f78b745c431a632e5769348ec6488e04b4ca92515a9f589564f900ab`.

The checklist above remains a list of test cases, not a blanket pass report. Real Anthropic requests, physical v0.7 Samsung/Spotify/headphone behavior and field/battery performance remain unverified. Historical results below retain their original scope.

## Data retention and results

Export any test recording that must survive uninstalling or clearing app storage. All internal mirrors/recovery copies reside on the same phone; they do not protect against device loss or a wiped app.

Historical v0.6 checks on the API 34 emulator, using disposable synthetic data:

- Final integrated debug/release builds succeeded. All **96 JVM tests passed**, with zero failures or errors; lint reported **zero errors and 37 warnings**. All **24 pinned icons** matched their sources. English/Spanish checks confirmed parity for **270 string/plural resources** and their format placeholders.
- The signed debug update retained v0.5 history, reset its forced locale override to follow the English phone setting and showed Voice coach Off. Bounded review of **11 screenshots**, including the current Spanish UI, larger text and landscape layouts, found no material issues in the inspected views.
- The exported ZIP contained **17 summary rows, 17 GPX files and README.txt**. Comparing its GPX point and segment data with the saved activities passed. The standalone CSV contained **17 rows** and no latitude/longitude columns. Direct Downloads saving worked; the post-save Share action opened Android's chooser with Drive present. No Drive account upload was performed. Save to… also saved a CSV successfully after rotating with the document picker open.
- Coaching rejected **5 km in 2 minutes** with a validation error. Disabling coaching still worked with that invalid draft present.
- English Test voice reported Voice ready from the TTS start callback using an installed Google offline voice. Spanish correctly displayed the missing-offline-voice message: the installed engine had English offline data only. Spanish spoken preview success is not claimed. Audio logs showed Runline requesting MAY_DUCK speech/navigation focus at **23:22:32** and abandoning it at **23:22:37**. These emulator log events establish the request/release path, not physical playback or music ducking.

- With a **0.1 km distance goal**, synthetic movement crossed the goal while the Activity was backgrounded. Audio logs recorded the automatic speech focus request at **23:36:05** and its release at **23:36:09**. Notification Pause/Finish saved the activity with **32 accepted fixes**; the emulator's initial Vancouver fix and Granada test movement remained separated. The coaching draft also survived landscape/portrait rotation. This tests the background cue/focus path, not audible music ducking.
- The new activity's GPX Share file action opened the native chooser with Drive and a readable **Walk 0.22 km** filename; no account transfer was attempted.

- The exact packaged **0.6.0 / version code 6** release installed in place and opened successfully. A fresh CSV export retained all **18 activity IDs**, including the two-hour walk fixture and live coaching test. The release signing certificate matched the stable per-app key: `fc4cf764f78b745c431a632e5769348ec6488e04b4ca92515a9f589564f900ab`. APK SHA-256: `633c91a592ca950d596a4e116342470cfb49f1a6e641c24429b1bd5058fe1447`.

No real Drive account upload, Spotify/headphone behavior or physical Samsung field/battery test is claimed. The broader checklist remains a set of test cases, not a statement that every device, failure path or lifecycle edge case passed.

Historical v0.5 checks on 1 November 2026, using the API 34 emulator and disposable synthetic data:

- Final integrated debug/release builds completed, including the neutral snackbar inverse-theme adjustment and four-line saved-filename allowance. All **71 JVM tests passed**, with zero failures; lint reported **zero errors and 28 warnings**. All **23 generated icon resources** matched their pinned sources. English/Spanish checks confirmed parity for **196 string/plural resources** and matching format placeholders.
- The debug update from v0.4 to v0.5 retained older recordings. The exact packaged **0.5.0 / version code 5** release then installed and retained all older history, the two-hour walk fixture and the newly recorded Walk. Its signing-certificate SHA-256, `fc4cf764f78b745c431a632e5769348ec6488e04b4ca92515a9f589564f900ab`, matched the stable per-app key used since v0.2. APK SHA-256: `e8b8010bc7c6b9f6b3936d47922aed1e090848bf614a8ae25c327129ef43e84d`.
- A **7,201-point walk fixture covering two hours**, including a pause/segment boundary and missing altitude, exercised fullscreen replay at default **1×**, seeking, Play/Pause and the details/elevation sheet. English/Spanish switching and rotation retained replay position **1:00:19** and **1×** speed. Bounded screenshot review covered light/dark, 1.3× font scale, portrait and landscape without material clipping in the inspected views.
- Primary export saved directly to Downloads in both languages, with readable **Caminata 8,37 km** and **Walk 8.37 km** names plus start date/time. Parsed GPX retained **7,201 points**, **two segments** and **walking** type. No Strava screen or browser opened. The success snackbar was confirmed monochrome with the complete filename visible. A separate GPX export of the newly recorded Walk matched all **13 stored points**, **three segments** and walking type. This comparison uses the actual saved samples, not the pre-quantized emulator input. Repeated exports produced `(2).gpx` and `(3).gpx`, each parsed with 7,201 points and two segments, preserving the original files. These results cover automatic Downloads saving; older-Android picker and fault-injected failures remain separate checklist cases.
- The initial current-location waiting screen was captured. Synthetic eastward movement produced a centered heading arrow; panning and explicit follow/recenter worked. Intermediate screen-recording frames showed continuous displayed movement between fixes. This demonstrates the inspected animation behavior, not physical GPS accuracy or every timing/trajectory case.
- With the Activity absent, notification **Resume**, **Pause** and **Finish** saved a Walk containing **13 accepted GPS fixes in three segments**. The session included the emulator's initial Vancouver fix followed by the Granada test area, separated by a gap/segment. Emulator NMEA coordinates are quantized: these results must not be described as perfect equality with all injected decimal coordinates.
- Changing Android's per-app language while the service was paused and the app backgrounded immediately updated the ongoing notification between Spanish and English.

The results above describe the builds/configurations actually exercised. They do not establish uninterrupted capture, field GPS/elevation accuracy, battery life, every export failure path or every Android lifecycle edge case. No controlled physical Samsung field/battery test, secure-lock-screen action test, Android 8/9 picker runtime or 16 KB page-size device check has been completed for v0.5. CI status is recorded separately in the repository workflow.

Historical v0.4 results on 1 November 2026:

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
