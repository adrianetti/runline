# Runline 0.16 — less setup, more running

Status: implemented from the 5 November 2026 feedback; release validation is recorded in TESTING.md. Target version: 0.16.0 / code 18.

## Experience

The runner chooses an activity, optionally uses today's suggestion, and taps Start. Keep Record and Activities as the only tabs, native Android controls, the monochrome palette, and the pinned Tabler icon family. The new update indicator may use accessible amber; terrain keeps its existing meaningful colors. This is an Operate surface: quick comprehension and reliable actions take priority over decoration.

The existing v0.15 screens and shared components are the visual authority. Preserve the map, moving terrain profile, recording safeguards and export behavior. Remove oversized preparation actions and repeated explanatory text. Icon-only controls retain 48 dp touch targets, accessible names/states and tooltips; color alone never conveys a state. English, Spanish, large text and landscape remain supported.

## 1. About: updates, storage and credits

- Replace the plain About dialog with a compact sheet. Show the installed version and a trailing update-status icon. A confirmed newer stable release with an uploaded APK gets an amber update icon; tapping it opens the repository's latest release page. The normal state stays neutral. Give the icon an accessible label including the available version.
- Check GitHub asynchronously when About opens, with a six-hour persisted success cache, conditional requests and a single in-flight request. A small refresh action allows a deliberate recheck, with throttling and server retry limits. Offline/error states must never imply that the app is up to date. Keep a repository/releases link available even when checking fails.
- Read the public `adrianetti/runline` release endpoint without a token. Compare parsed semantic versions, not strings (`0.16` versus `0.9`), ignore drafts/prereleases and malformed tags, and require the expected uploaded APK asset. Open only the fixed HTTPS repository release URL. No automatic APK download/install or installation permission is needed.
- Show two measured lines: **Storage · 84 MB** and **Terrain · 3 MB**, with terrain a subset of the total. These numbers are illustrative, never defaults. Query the app's own Android storage statistics on a worker; count actual terrain files on disk, not only files loaded in memory. Avoid double-counting cache. If the system total is unavailable, show an explicitly estimated total or unavailable value, not zero.
- Put Repository and Sources & licenses here, including the full existing terrain, imagery and icon credits. Keep required map attribution visible on the map. About currently exposes only the icon license, so consolidate credits before removing the duplicate island text.

## 2. Record: compact controls

- Retain the activity dropdown and one prominent Start button. Remove the large Voice coach, Smart goal and Offline terrain buttons and the terrain readiness paragraph.
- Use a compact icon row: speaker toggle plus a preparation/options icon. Speaker and muted-speaker visibly distinguish on/off. One tap changes voice guidance and shows a short native Snackbar: **Voice coach on/off** / **Guía de voz activada/desactivada**. Check persistence before reporting success.
- Voice settings, Test voice and manual goal editing remain discoverable through the options menu; no long-press-only functionality. First-use voice problems get a short actionable message without preventing recording.
- Voice enablement, AI enablement and use of a suggested goal are independent. Selecting an AI goal does not silently turn speech or other AI features on.
- Hide Smart UI when its AI feature is disabled or its connection is unconfigured. Switching between Run, Walk and Hike does not apply a running suggestion to another activity. Manual goals remain available.

## 3. Today's Smart suggestion

- Add **Daily suggestion** under Settings → AI, included in the explicit All AI features control. Its new automatic behavior defaults off for existing installations until selected; key, note-sharing and profile-sharing choices remain separate.
- On the first idle Record visit of the local day, generate automatically if the feature is enabled, a key is configured and connectivity is available. No preliminary drawer or Suggest Run button. Render a small loading indicator in place while keeping Start usable. Never start this preparation during an active/recovered recording.
- Persist the validated result and request reservation before/after provider work. Allow at most one automatic provider attempt per local day, across reopening, rotation, concurrent callers, process death and ordinary clock/time-zone changes. A failed/indeterminate attempt offers a deliberate Retry; reopening does not silently repeat a potentially paid call. Log actual usage through the existing diagnostics path, without storing keys or request bodies in diagnostics.
- Use the overall goal, comparable recent runs, recorded calendar-week and trailing-seven-day volume, reported effort, actual elapsed hours since the last completed run, and optionally shared profile/notes. Missing history or effort remains unknown. Retain history-led ambition without reinstating an arbitrary mileage cap or inventing recovery from elapsed time alone.
- Show one compact recommendation: distance **or** duration, supported pace/range, and one localized effort word. Illustrative layout: **7 km · 6:00–6:20 min/km · Easy [Use]** / **7 km · 6:00–6:20 min/km · Suave [Usar]**. It is an example, not a prescribed workout. Preserve a pace range when one is returned; do not invent an exact average. If pace is unsupported, omit it. Rest is simply **Rest / Descanso**, with no fake distance or runnable target.
- Keep explanatory rationale off the main Record surface. Store it for diagnostics or optional inspection; show no duplicate feature title or cost paragraph around every result. Cost/data information belongs with the AI setting.
- A run completed after generation consumes/invalidates the suggestion. Material history, effort, goal, model or consent changes invalidate it as well. Do not auto-generate again that day or reuse stale advice; show a short changed/used state with an optional explicit refresh. Midnight makes the previous day's suggestion ineligible for a new run. Cached advice never changes an ongoing session.

## 4. Use this goal: explicit, session-specific intent

- Place the Use switch next to the compact recommendation; it begins off for each new session. On selects that suggestion for the upcoming run; off restores a free run unless the user explicitly chooses a manual target. Show only the selected target, avoiding a second conflicting goal summary.
- At Start, snapshot the selected distance/duration, pace band, effort, origin and suggestion identity into a versioned session-intent sidecar with recovery support. Keep original GPS files untouched. Record intent independently of whether voice guidance is on.
- Local and AI coaching use that session snapshot. Exceeding or missing the target does not replace the original intent, stop recording, or cause repetitive corrections. Existing quiet intervals, terrain-aware pace behavior and permission to remain silent stay in effect.
- Reviews can compare the intended session with what actually happened, even if global settings have since changed. Old activities without a snapshot have unknown original intent, not an inferred target. Finishing consumes the pre-run selection; a late response cannot overwrite it.

## 5. Terrain: automatic, bounded, in Settings

- Remove terrain preparation from Record. Keep **Terrain** in Settings with automatic use/downloads, measured cache size, Clear terrain and optional manual area preparation. Respect an existing disabled setting.
- Default to preparing the small tiles needed around a recent, usable location while the app is open or recording. Use an already-granted foreground location permission and a short-lived foreground fix when needed; otherwise wait for the recording's normal permission/fix flow. Do not add background-location permission, continuous idle tracking or a separate location prompt on launch.
- Start with nearby required tiles and prefetch adjacent tiles only as needed; do not silently download a country or repeatedly replace a whole region. Coalesce matching requests, reuse validated files, respect offline/Data Saver conditions, and retain bounded backoff. Network/decode/storage work stays off the recorder and UI threads.
- Retain the existing **64 MiB terrain disk budget**, bounded in-memory grids and bounded request queue. Count temporary files in the budget, preflight free space, and evict older terrain before breaching it. Cache eviction must never touch recordings, photos, preferences or keys. Protect the active vicinity within the budget rather than allowing pinned areas to defeat the cap.
- Atomic writes and validation preserve usable files through cancellation/process death. Resume useful work on a later eligible app/recording event; no wake-up solely to fetch terrain. A failed download leaves existing data usable and missing slopes unknown. Optional Wi-Fi-only behavior belongs in Terrain settings.
- Preserve worldwide provider selection, finer regional data where available, source-boundary guards and polar/ambiguous-data handling. No change to raw GPS recording or exports.

## 6. Shared live/replay island

- Remove the repeated **Pace · min/km** heading. Place the large rolling pace number and smaller **min/km** on a shared baseline, before the terrain profile. Keep the unit readable at large font sizes without shrinking the main value excessively.
- Remove **Elevation · IGN/CNIG** and similar provider/status footers from the island. Full source details belong in About and optional elevation details. Keep unknown terrain visibly unknown, and preserve essential GPS, storage and recovery errors.
- Preserve distance as the second-priority number, the smooth mini terrain profile, slope labels/angle/color, background elevation trace and cursor. Apply the layout once in the shared readout used by active recording and replay. Motion still respects system settings.

## 7. Activities and playback

- Put one accessible overflow icon on the right of the Activities section heading. Its short menu offers **Share all** and **Export all**, followed by the existing CSV/GPX ZIP choices. Remove the two large transfer buttons. Chart filters continue to affect charts only; whole-history exports include all saved activities. Share still opens Android's chooser, including Drive when available.
- Replace the speed-selection dialog with a native stepped slider using the existing tested 1×/5×/10×/30×/60×/120×/240×/480× speeds. Show only the current multiplier and compact slow/fast endpoints. Dragging changes speed immediately, without pausing or jumping playback position.
- Keep speed and timeline as distinct accessible controls. Preserve the initial multiplier nearest a 30-second replay, manual speed/position across recreation, background pause behavior and segment/gap handling. Screen readers can adjust speed without dragging.

## Engineering and delivery

Implement small modules for release checking, storage reporting, daily-suggestion persistence/state, session intent and automatic terrain scheduling. Views observe state rather than start network work during rendering; single-flight work and explicit lifecycle disposal prevent duplicate requests and retained Activities. Reuse the existing provider, diagnostics, terrain cache, export and shared readout components instead of building parallel systems.

Order: (1) persistent daily suggestion and session intent, (2) automatic terrain/cache accounting plus About services, (3) compact Record/history/island/replay controls, (4) integrated validation, (5) signed 0.16 release after required checks pass on the exact PR head. Preserve in-place upgrade signing and existing recordings/settings. Update PRODUCT, DESIGN, surface brief, usage and testing documentation with implemented behavior during delivery; this plan does not declare those changes shipped.

Acceptance checks:

- Version comparison, draft/prerelease/missing-APK handling, cached/offline/rate-limited update checks and safe release links.
- One automatic daily request across rotation/restart/concurrency; stale completion, edited/deleted history, midnight/time-zone changes, disabled/key-cleared state, rest/pace-free results and explicit retry. Fake providers cover timing/failure paths; no paid test requests merely for UI checks.
- Selection/voice independence; start-time intent, exceeding targets, pause/recovery/finish and review comparison without rewriting original recordings.
- Automatic regional selection, stale/missing permissions/location, offline mode, corrupt/partial downloads, repeated area boundaries, low storage and strict cache eviction limits. Recording continues throughout.
- Measured app/terrain storage, no double-counting, refresh after clear; clearing terrain preserves all activity and credential files.
- Replay slider continuity, default speed, accessibility and state restoration; export/share still retain every activity and original GPX data.
- Bounded native visual review, then at most one confirmation pass: English/Spanish, portrait/landscape, light/dark, large text and reduced motion. Verify icon hit targets, snackbars, units and essential errors.
- Relevant JVM regressions, Android build/lint, resources/icons, security/dependency checks, signed-update retention and all applicable GitHub checks before merge. Field terrain precision and physical music/lock-screen behavior need separate device evidence.

## Verified platform references

GitHub's [latest-release endpoint](https://docs.github.com/en/rest/releases/releases#get-the-latest-release) supports public reads without authentication. Its [unauthenticated rate limit](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api#primary-rate-limit-for-unauthenticated-users) is shared by originating IP, so checks need caching/backoff rather than an embedded token.

Android's [StorageStatsManager](https://developer.android.com/reference/android/app/usage/StorageStatsManager#queryStatsForPackage(java.util.UUID,%20java.lang.String,%20android.os.UserHandle)) is available from API 26 and can query this app's own package without Usage Access permission. The query can take seconds and belongs on a worker.
