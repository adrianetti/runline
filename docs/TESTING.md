# Version 0.20.2 test checklist

The checklist is not a blanket pass report. Current v0.20.2 and earlier v0.20.1/v0.20, v0.19, v0.18, v0.17, v0.16, v0.15 and historical v0.14/v0.13/v0.12/v0.11/v0.10.2/v0.10.1/v0.10 evidence are recorded below; historical v0.8/v0.7/v0.6/v0.5/v0.4/v0.3 evidence is retained separately and in [release history](RELEASE-HISTORY.md). Record the build/commit, phone model, Android version, battery settings and pass/fail/not-tested outcome. Use disposable synthetic recordings for destructive and fault-injection cases; keep real route files private.

## Build and unit checks

With JDK 17, SDK 37.0 and Build Tools 36.0.0, use the pinned AGP 9.5.0 / Kotlin 2.4.21 / Gradle 9.8.2 build:

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

## Version 0.15 global offline terrain

Recorded 5 November 2026. Native fixtures use a synthetic Caracas route; personal GPX data and test backups remain outside Git.

- Signed debug/release build: **302 JVM tests, zero failures/errors; lint zero errors / 116 warnings**. Verified **542 bilingual resources**, **34 pinned icons**, 22 security-checker tests, workflow lint and seven Semgrep rules across 92 production Kotlin files. The fresh dependency query covered **236 Maven versions / 39 configurations**, with zero OSV findings. Applicable GitHub checks must pass on the exact PR head before merge.
- New regressions cover fractional/negative Terrarium decoding, malformed/transparent data, known Mercator points, polar limits, Venezuela selecting only global data, date-line interpolation, neighboring tiles and 5 km coverage across hemispheres/zoom bands. Uncertainty probes share one resolution at latitude-band boundaries. Source changes cannot invent ascent or interpolate mismatched heights, and real supported 15% climbs remain detectable with the longer global window.
- Re-ran the private 10K against the same independent IGN fixture: **2,963/2,963** heights available under explicitly assumed 3 m horizontal accuracy, **2,408** grade estimates, **zero below −10%**. Original heights, distance and replay duration remained unchanged. This is retrospective consistency evidence, not ground-truth surveying.
- Native API 34: fresh mock Caracas location produced a nine-file global preview with no Spanish requests. Preview and saved-area state survived Activity recreation; cancellation/resume completed a real public HTTPS download totaling about **0.5 MB**. The first attempt exposed emulator DNS timeouts; repairing emulator DNS allowed the real network path to pass without changing application networking.
- After an in-place update to the final APK, cold offline reopening loaded validated files and the synthetic replay obtained mapped heights. English, Spanish at 1.3× text/dark mode and a 2220×1080 wide layout passed bounded inspection; primary preparation controls and the separate Start action remained usable. Offline corruption of a cached PNG changed readiness to incomplete rather than using invalid data; online Resume replaced it with a validated public tile.
- Offline recording with no terrain for the simulated route accepted **eight fixes** and persisted original GPS heights through Pause/Finish. An initial harness used a fixed one-second save wait and stopped before finalization; reopening recovered all **seven accepted fixes** from that attempt and completed offline. The corrected harness waits for durable completion instead of a fixed sleep; the fresh recording then passed.
- The final APK installed in place and cold-launched. **63/66 original non-cache files** were byte-for-byte unchanged; only the map database/journal and Android profile-install marker differed. Existing recordings, recovery data, annotations, preferences and credentials were unchanged. Removed all owned sessions, terrain files and screenshots; restored original preferences, language/display/network settings and default mock-location permission, removed both harnesses and disabled emulator root.
- Exact artifact: **`dist/Runline-0.15.0.apk`**, **0.15.0 / code 17**, SHA-256 **`7cb2024f8e6530c777efd6dfc4d56347b79d69150ecc6a653ee1a9ba085776ad`**. Signature matches prior releases.
- Limits: one saved preparation area, foreground download with manual resume after interruption, no offline satellite imagery or offline AI, and no polar terrain. Data resolution/age and confidence vary by source; unknown terrain remains unknown. Physical Samsung, field GPS, battery exhaustion and paid AI requests were not revalidated.

## Version 0.14 independent terrain

Recorded 5 November 2026. Personal GPX files, terrain fixtures and native-test backups stay outside Git.

- Final signed debug/release build: **293 JVM tests, zero failures/errors; lint zero errors / 108 warnings**. Verified **517 bilingual resources**, **34 pinned icons**, 22 security-checker tests, workflow lint and seven Semgrep rules across 91 production Kotlin files. A fresh dependency inventory/query covered **236 Maven versions / 39 configurations**, with zero OSV findings. Applicable GitHub checks must pass on the exact PR head before merge.
- Regressions cover independent projection control points, fractional ASCII interpolation/orientation, malformed/oversized/no-data responses, cache-area registration, missing/disabled/ambiguous terrain, a supported steep descent, incremental projection invalidation, raw-height exclusion from AI and local hill cues, EN/ES advice conflicts, and refusal to overwrite a saved GPS recording with derived elevations.
- The restored private 10K GPX was checked against independently downloaded fractional IGN terrain using production projection, replay and trend code. All 2,963 positions had model heights under the test's explicitly assumed 3 m horizontal accuracy; the GPX itself contains no accuracy values. Grades were available at 2,408 positions, with **no estimates below −10%**. The suspect false-descent sections became unknown or a gentle mapped climb. Distance, replay duration and original heights were unchanged. This is retrospective evidence for this file, not ground-truth surveying, the original device's accuracy, or a reconstruction of spoken prompts.
- Native API 34 checks verified a fresh online area download, offline cached replay, immediate invalidation when maps are disabled, horizontal chart scrubbing/replay synchronization, vertical gesture separation, accessible seeking, fixed-header edit/review actions and rapid/reduced-motion terrain labels. An offline foreground recording with no cached terrain accepted eight mock GPS fixes and persisted the original heights through Pause/Finish. No terrain-provider failure interrupted that recording.
- Bounded visual checks covered English, Spanish at 1.3× text in dark mode, and a 2220×1080 wide emulator layout. The chart and source/status line remained usable. The confirmation corrected a harness scroll target and a stale GPS-altitude label; one wide-layout attempt overlapped configuration changes and timed out, then passed after the configuration settled. These are synthetic-session checks, not physical rotation or Samsung frame-rate evidence.
- Exact artifact: **`dist/Runline-0.14.0.apk`**, **0.14.0 / code 16**, SHA-256 **`415667ba0ea79d1060c3772663c2d699cbe725c32e62cf4462eecb07d094b1e2`**. Signature matches prior releases. The final APK updated in place and cold-launched. All original recordings, recovery files, annotations, preferences and credentials remained byte-for-byte unchanged: **63/65** inspected originals matched; only map cache and Android's profile-install marker changed.
- Removed owned synthetic sessions, test terrain, screenshots, mock-provider setup and the temporary harness. Restored the original preferences, phone-language selection, display/network settings and default mock-location permission; disabled emulator root access.
- **Not revalidated:** physical Samsung field GPS, battery exhaustion, headphones/music, native corrupt-cache recovery or paid AI generations. Malformed terrain parsing is unit-tested; prompt/source guards are tested without an API call. Coverage is initially mainland Spain/Balearics, with no barometer fusion. Missing/ambiguous data remains unknown and partial coverage can undercount totals; see [Terrain](TERRAIN.md).

## Version 0.13 terrain confidence and open source

- Signed debug/release builds: **278 JVM tests, zero failures/errors; lint zero errors / 103 warnings**. Verified **504 English/Spanish resources**, **34 pinned icons**, 22 security-checker tests and seven Semgrep rules across 88 production Kotlin files. The current advisory query checked 236 dependency components across 39 configurations with zero findings; exact-head CI remains required before merge.
- Synthetic regressions cover separate vertical versus horizontal accuracy, an abrupt 12 m height relocation, a single noisy altitude spike, gradual grade changes, a genuine −15% descent, deterministic replay seeking, GPX accuracy extensions and accuracy retention through journal recovery/completed storage. Legacy recordings remain readable with absent vertical accuracy.
- Native API 34 instrumentation verified horizontal chart scrubbing changes replay position, vertical movement preserves position and the open sheet, native accessible seeking, fixed-header actions, rapidly interrupted label transitions and Remove animations. Bounded visual checks covered English, Spanish at 1.3× font scale in dark mode, and a 2220×1080 wide emulator layout. The headless emulator's rotation request did not change orientation, so the wide layout was tested with a temporary display-size override instead; physical rotation is not claimed.
- Two visual issues found in the first pass were fixed together: chart axis values now use the recording's height origin, and popup pencil/brain icons use a readable theme tint. A final pass confirmed both. All screenshots use a synthetic session, not a personal route. No provider requests were made.
- Artifact: `dist/Runline-0.13.0.apk`, **0.13.0 / code 15**, SHA-256 `fc1231f1b614974f9d8e07df08d21d3a038de037e2fda1f9afacccd8b6a2a348`. Same app signing certificate as prior releases.
- The exact signed release installed in place and cold-launched on API 34. All original recording, journal, metadata, preferences and credential files were byte-for-byte unchanged: 64 of 65 inspected original files matched; only the map cache changed. Synthetic test data and the temporary harness were removed, display settings restored, app language returned to the phone default, and emulator root access disabled.
- Publication audit: remote branches/tags contained no GPX, signing stores, activity JSONL or unrelated local files. Gitleaks scanned the existing remote/tag history and the implementation commit (36 commits) without findings; subsequent documentation commits are also checked before publication. Release assets are APKs/checksums; the three tracked screenshots have synthetic-data provenance. Human PR descriptions and issue/review comments were checked for route/credential payloads. Developer keys and private native-test backups remain outside Git.
- **Unverified:** the exact reported 10K −15% section could not be reproduced because its GPX was no longer at the previous local path; the private prior test retained summaries only. No independent terrain model, barometer, physical Samsung field run or new battery/headphone audit was used. Smooth altitude drift can still resemble a hill; see [Terrain](TERRAIN.md).

## Version 0.12 coach insight

Recorded 4 November 2026. Private GPX files and provider responses remain outside Git.

- Final signed debug/release build: **271 JVM tests, zero failures/errors; lint zero errors / 103 warnings**. All **499 bilingual resources**, **34 pinned icons**, **22 security-checker tests**, actionlint and seven Semgrep rules across 87 production Kotlin files passed. GitHub checks must also pass on the exact final head before merge.
- Regression coverage includes terrain reversals/leveling, distance/time progress and contextual encouragement, profile gaps/seeks/no future samples, bounded/torn diagnostic logs, coordinate-redacted export, provider usage/cost handling, persistent one-attempt review jobs and strict short review cards.
- Integrated the already-merged SDK 37 / Core 1.19.1 / AppCompat 1.8.0 / Material 1.14.0 maintenance; retained target SDK 34 and the shortened README. Rebuilt and confirmed native review/replay/log screens against those dependencies.
- Native API 34: horizontal idle-tab swipes in both directions and vertical-history scroll separation; spoken-only/all-events logs; export produced four synthetic events with no coordinates; saved-review deep link with AI disabled; rotation, Spanish, dark mode and 1.3× text. Two bounded visual passes fixed review restoration after rotation and smoothed the mini-profile joins. README images use synthetic data only.
- Background integration: one pending network-constrained JobScheduler job survived process death; after restoring connectivity its cached synthetic result completed without a provider call and posted a notification. Duplicate enqueue did not create another job. A temporary same-signature test harness and dummy encrypted key were removed/restored after testing. This is not a real provider run through Android's scheduler.
- Signed update initially retained all 65 existing inspected files byte-for-byte. After the final update and map/UI testing, all original recording, journal, preferences and credential files remained identical; only the map cache and Android profile-install marker changed (63/65 original files identical). Final APK: `dist/Runline-0.12.0.apk`, version 0.12.0/code 14, SHA-256 `4672483cec5f0b8e2a67cc6eae5bf1379cdf522250ee01418083cd06b6bebb61`, same dedicated signing certificate.
- Real-provider validation: three explicitly bounded external requests used production summary/context builders, provider and review validator with the two privately supplied GPX recordings. No raw coordinates/photos were sent; unknown effort/accuracy remained unknown and elapsed GPX time was disclosed as a proxy for active time. The first card failed validation; the second had 121 words despite valid field lengths. Tightened per-field word budgets produced a validated 63-word Spanish review in the final request. It used 3,213 input / 1,179 reported output tokens, HTTP 200, 11.3 seconds and an estimated US$0.0182 at standard list prices. It recognized the completed distance and supported split pattern, suggested steadier pacing and an easy next session. This small sample does not certify training advice or field GPS accuracy. No automatic paid retries were added.
- Physical Samsung motion, battery exhaustion, live terrain ground truth and music/headphone routing have not been re-certified in this release. Logs begin with this version; an old GPX cannot reconstruct what was spoken. Android can defer background work; notifications need permission. Open source and Google Play are readiness plans, not completed publication.

## Version 0.11 pace-first islands

Recorded on 4 November 2026. Live and replay share a pace-first readout with native rolling digits, distance and a smooth signed-grade line. Live headline pace is a display-only trailing ten-second estimate with five seconds of usable continuous movement required. Storage, recorded metrics, exports, speech and AI behavior are unchanged.

- Debug/release builds and **254 JVM tests** pass, with zero failures/errors and **zero lint errors / 106 warnings**. The five new regressions cover recent rather than whole-run pace, acquisition/staleness, pauses/gaps/bad fixes, stopping and weighted window boundaries without sample mutation.
- **466 English/Spanish resources**, **34 pinned Tabler icons**, **22 security-checker tests**, workflow lint, seven Semgrep rule fixtures and the strict source scan of **77 Kotlin files** pass. No animation library or other dependency was added. Applicable GitHub checks must pass on the exact head before merge.
- Bounded API 34 emulator checks used synthetic hill/flat/downhill data and actual production live/replay screens. English/light, Spanish/dark, 1.3× font, portrait/landscape, unknown grade, seeking, playback, Details and live GPS capture/pause/finish were inspected. The first pass exposed hidden distance in larger-text landscape; the confirmation keeps pace, slope and distance visible together, with fixed controls and map attribution. The elevation backdrop stays above controls. Initial captures taken during theme/rotation recreation were black and are not counted as visual evidence; settled confirmation captures are retained locally.
- A temporary same-signature native instrumentation harness verified rapid updates, exact latest-value settlement, unknown values, digit carry and detach, both with animations enabled and system animation scale zero. Both runs passed, and the harness was uninstalled. Grade-line colors exceed 3:1 contrast on both surfaces; color is accompanied by signed grade and angle. Emulator evidence does not establish physical-phone frame rate, sunlight readability or GPS accuracy.
- The initial signed update retained all **59 inspected original files byte-for-byte**. After runtime recording checks, all original recording/journal files remained unchanged; **56 of 59** total original files were identical. Expected changes were the map cache, installation profile marker and completion bookkeeping for synthetic recordings. Settings were restored. The final exact signed APK was installed in place and launched; its signature matches prior releases.
- Artifact: **`dist/Runline-0.11.0.apk`**, **0.11.0 / code 13**, SHA-256 **`813747db511702d83c6a24ee51a96d23defa562ee5c420592bdf8dddef5306e9`**. No physical Samsung, battery-exhaustion, music/headphone or real-provider AI test was repeated for this UI change.

## Version 0.10.2 history-led Smart goals

Recorded on 4 November 2026. Removed the local training-budget engine and its derived distance, duration, pace and recovery restrictions. Smart requests now provide recorded context and software input format; Claude chooses training load, and valid suggestions are applied without clamping. The shared input ranges are the existing manual-goal support, not recommended workload. Four recorded seven-day windows, recording/effort counts, median distance and days since the last run expose sparse history instead of treating an incomplete log as a full training baseline.

- Final debug/release builds pass **249 JVM tests**, zero failures/errors; lint reports **zero errors / 106 warnings**. Obsolete formula tests were replaced by response-format, unchanged-target application and progression regressions. A recorded 3 km run can receive and apply a 5 km suggestion. Missing/future/wrong-type history, effort and sparse windows retain explicit evidence handling.
- **464 bilingual resources**, **34 pinned icons**, **22 security-checker tests**, workflow lint, seven Semgrep rule tests and a strict scan of **73 production Kotlin files** pass. Required GitHub checks must pass on the exact PR head before merge.
- **11 real Sonnet generations** across bounded six-, four- and one-case batches exercised production request builders, strict JSON schema, transport and validators. All HTTP requests succeeded and outputs parsed, with no automatic retries or truncation. Usage was **33,163 input / 2,014 output tokens**. The first batch led to stronger qualitative weighting of hard effort/current workload and a time-based exploratory option when history is absent; a later wording check removed an unsupported safety claim. The original outputs are retained privately as iteration evidence.
- The actual private 3.023 km GPX with unknown effort received **5 km easy, range 4–6 km, no numerical pace** in both progression samples. Separate, explicitly simulated effort/history cases tested different workload choices; they are not the user's actual fitness data. The busy-week follow-up chose 8 km easy instead of a new longest run; the no-history final sample chose 30 minutes at conversational effort without inventing distance, pace or safety. These examples establish neither universal coaching quality nor an individual's readiness.
- Exact signed artifact: **`dist/Runline-0.10.2.apk`**, **0.10.2 / code 12**, SHA-256 **`1283effce055014785d601a30f844a875c617f182dea531bc41bccdcbaf29a7d`**. The signing certificate matches the existing app. Storage schema, recording and speech scheduling are unchanged; these tests do not repeat physical-phone installation, TTS/audio-focus or field checks.

Private GPX files, credentials and raw provider reports remain outside Git. Development-key access follows the local owner-only storage described below. Historical caps and their tests in the following v0.10.1/v0.10 sections no longer describe current Smart behavior.

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

## Routes and hiking

- **Routes tab.** It lists the bundled open routes with distance, ascent, time, difficulty and shape. Import a real GPX from another app or service: track, route points and waypoints must be read, and timestamps and extensions ignored. Corrupt, oversized and DOCTYPE files must be refused with a message. Delete an imported route.
- **Route details.** Check the map fit, the stats, the profile, the waypoints, Share GPX and Save GPX. Check the sunset line before and after sunset, and in landscape and Spanish.
- **Start.** Start in route details must begin recording at once, with the route and its activity. During a recording it opens the recording instead. The Routes list ticks the selected route.
- **Following.** Walk the route. The island must show distance and ascent left, arrival and the next waypoint. Step 60 m off the path: within 15 s the phone vibrates, the notification title shows the distance, and with voice on the side is spoken. Rejoin: a short vibration and "Back on the route". On an out-and-back leg, progress must keep counting forward. Force-stop during a hike, then resume: the route progress returns, and no cue already behind you is spoken.
- **Turns.** With voice on, each turn is spoken about 80 m ahead and again at 20 m. Two turns within 60 m are named together. The end of an out-and-back leg says "turn around"; a ravine hairpin says sharp left or right. The island arrow and distance match the cue. With voice off, a short vibration marks each turn. On an imported GPX, every bend in the geometry is a cue.
- **Climbs and places.** The island shows "Climb ahead" from 2 km out, then the height left inside the climb. The climb is spoken once, about 200 m before. The elevation-ahead profile moves with you. Place notes are spoken once, near their waypoint, in the app language.
- **Preview.** Preview plays the route in 3D with playback speed and a timeline, and records nothing. Its elevation chart follows the route's profile with no gaps, offline too. Back returns to the route.
- **Check for new routes** needs network access. A changed or corrupted catalog file must be rejected by its SHA-256.

## Replay and statistics

- Use a two-hour fixture with thousands of samples. Replay should occupy nearly the full content area, with a compact reachable island. Tap statistics/Details: playback pauses and a sheet exposes totals, prominent climb/descent, relative elevation profile, secondary raw altitude/range and splits. Close it and continue without losing position/speed. Check landscape and larger Spanish text.

- Open a saved run and verify the initial paused frame and the available multiplier whose playback duration is nearest **30 seconds**, computed from observed GPS time. Opening a different run must calculate its own default; rotating the current run must preserve a manual choice. Play at every multiplier (1×, 5×, 10×, 30×, 60×, 120×, 240×, 480×), change speed during playback, seek in both directions, reach the end and restart. Confirm the recorded run itself never changes.
- Background or leave the screen during playback. Playback must pause; returning must not accumulate background playback time. Rotate and verify saved position/speed restore without automatic playback.
- Use synthetic runs with explicit pauses, interruptions, isolated fixes and a GPS gap longer than 15 seconds. Replay time must include only observed intervals and cut across gaps without a fabricated line or animated transit. Average pace/speed still use active time.
- Compare speed/pace with known GPS intervals and the trailing five-second window. Reject implausible running speed, keep stationary/missing speed meaningful and avoid using the active timer as the replay clock.
- Test valid, absent, intermittent, flat and non-finite GPS altitude. Profile gaps must remain visible; missing altitude and segment breaks reset ascent/descent accumulation. Check the 3-metre deadband, first-available-height baseline, accumulated climb/descent, secondary raw altitude range and unavailable-value dashes. Do not label raw GPS estimates as terrain-corrected or display an unsupported grade percentage.
- Check totals and kilometre/final partial splits against known samples. Replay metrics and the elevation-profile cursor should track seeking without persistent UI stalls. Route/profile geometry must not be rebuilt every frame on a long recording; playback has a 30-update-per-second target, not a hardware performance guarantee.
- A no-route/one-point run keeps its saved time; unavailable playback controls must be disabled. Do not claim a video export: no MP4 is produced.
- Since 0.18, nothing is drawn ahead of the arrow: the route grows behind it, ends exactly at it at every speed, shortens on a backward seek and is complete at the end. Pan away: the arrow recenter control appears at the bottom right and hides again once following. The credits pill opens the credits.
- Hillsides: on a mountain path, the elevation chart and slope line keep their heights on steep slopes. Gaps are allowed only at real steps, such as bridges, walls, ravine bottoms and ridge crests, and are a few hundred metres at most.
- Bridges and underpasses: on a run crossing over and under roads, the slope line and elevation chart stay continuous. `points.csv` names `above`, `below` or `ground` for those fixes. A real underpass ramp should not be flattened; report one if it is.

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
- The API 34 signed v0.4 update retained v0.3 and older runs. An unfinished draft survived an additional update and offered recovery. The exact packaged release APK also installed and opened successfully, retaining the new 0.14 km test run and older history. Its signature matched the existing per-app key; its checksum is in [release history](RELEASE-HISTORY.md).
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

CI results are tracked separately in the repository's [Android workflow](https://github.com/adrianetti/runline/actions/workflows/android.yml). No physical-phone field/battery-exhaustion test, Android 8/9 picker runtime check or 16 KB page-size device test has been completed. Offline imagery is not guaranteed. Unlisted checklist cases remain unverified; the results above do not certify every failure path or device. [release history](RELEASE-HISTORY.md) retains the earlier v0.2 results separately.


## Version 0.16 validation

The local 0.16 build runs 330 JVM tests. New regression cases cover durable daily reservations/results, provider failure, storage failure before/after requests, explicit retry cooldown, stale completion, history/consent changes, rest/pace-free goals, midnight/clock rollback/timezones, voice-independent session intent, numeric release versions/expected APK assets, update caching/backoff, and strict terrain disk accounting/eviction. Existing recorder, GPX, terrain, replay and coach suites remain included.

Bounded API 34 native checks use synthetic routes and an encrypted dummy AI key while offline, with original emulator data backed up privately. English/light, Spanish/font 1.3/dark/reduced motion, and wide layouts exercise compact Record controls, cached suggestions, Use/voice independence, recreation, About storage/new-version state/browser intent, terrain settings, Activities sharing menu and the accessible replay speed slider. Paused speed changes preserve exact position; playing changes retain playback and rotation keeps multiplier/position. Process termination after a paused GPS recording preserves its samples and session intent; reopening, resume and finish retain original GPS altitude. No paid AI generation or physical-phone test is claimed.

The signed debug/release build and Android lint passed with **zero errors / 150 warnings**. Resource checks cover 574 English/Spanish entries and 37 pinned Tabler icons. Seven Semgrep rule tests, production scanning, five Android surface tests, 17 advisory-checker tests and actionlint passed. A fresh OSV audit covered 236 Maven components across 39 configurations with no findings; the separately documented bundled-Gradle limitation remains.

The API 34 backend check successfully fetched the real public GitHub latest release, persisted its cache/backoff, and rejected an immediate manual recheck. Automatic foreground terrain preparation downloaded nearby global tiles for a synthetic Venezuela location, stopped its location listener and stayed within the cache budget. Clearing terrain removed its data while retaining the test recording and encrypted credential file. These tests do not establish worldwide path accuracy, physical Samsung background/audio behavior or Play Store readiness.

The final signed APK passed the confirmation batch after the speed endpoint labels/selection refresh. An offline update check reported failure while retaining its known release, rather than claiming a successful check. The in-place update from v0.15 used the existing signing certificate. All 55 original recording/annotation/review files remained byte-for-byte unchanged during native testing. Original emulator preferences/data were restored afterward; all 65 original non-cache files matched the backup, with test-only routes, cache and dummy credentials removed. Physical-phone installation remains user-driven through GitHub.

Release APK: `Runline-0.16.0.apk`, version code 18, SHA-256 `1ffe5b9a518c8878c87822bfa167dbef477025dd6ae75bec01045d712aec51f8`. Signing certificate SHA-256 remains `fc4cf764f78b745c431a632e5769348ec6488e04b4ca92515a9f589564f900ab`.


## Version 0.16.1 validation

Recorded 6 November 2026. This patch groups the pace unit with its digits, explains the cached daily recommendation, and prefers GitHub Mobile for About links.

- Debug/release builds and **330 JVM tests** passed with no failures; Android lint reported **zero errors / 150 warnings**. Verified **578 bilingual resources**, **37 pinned icons**, the 22 security-checker tests, workflow lint, seven Semgrep rule fixtures and the strict scan of 106 production Kotlin files. No dependencies or permissions changed.
- API 34 native checks covered English/light, Spanish/1.3× text/dark/reduced motion, landscape and a narrow 2× text layout. Pace values `5:30`, `11:59`, `99:59` and unknown kept a 6dp gap, aligned baselines, single-line digits and a larger unit. Queued digit animation settled to the final reading. Narrow/large-text replay puts the speed slider on its own row; the final APK passed the confirmation check.
- With an offline synthetic REST recommendation, Recommended/ Recomendado expanded the exact saved rationale and analysis time, survived recreation and collapsed again. Rest had no Use switch, disabled AI stayed hidden, and repeated refresh/viewing did not change the cached recommendation or create another request. The JVM regression also verifies that the original analysis timestamp survives later cache reads.
- About's repository and release controls targeted `com.github.android`. Native fake handlers exercised app success, absent app → unrestricted external handler, no handler and security rejection. This verifies the intents/fallbacks, not the installed GitHub app's behavior on a physical Samsung.
- The signed APK updated v0.16 in place. All **51 inspected original recording/review files** remained byte-identical during testing; after removing synthetic data and restoring preferences, all **65 original non-cache files** matched the private backup. Restored emulator display/language/network settings, removed the harness and disabled root. No paid AI request or new field/battery/audio test was performed.
- Artifact: `Runline-0.16.1.apk`, **0.16.1 / code 19**, SHA-256 `5d2655221f8ce3168a7e85cbb6dbb219ee72fbf89dcc48ffa2474f1b31378d99`. Signing certificate remains `fc4cf764f78b745c431a632e5769348ec6488e04b4ca92515a9f589564f900ab`. Required GitHub checks gate merging the exact PR head.

## Version 0.17 validation

Recorded 6 November 2026. The private 4.7 km street run, its coach log and the earlier 10K stayed outside Git. Regional terrain tiles were fetched to a local cache and used only for the retrospective checks below.

- **Build and checks.**
  - Debug/release builds passed **353 JVM tests**, with no failures or errors. Android lint reported **zero errors / 160 warnings**. The new warnings are Spanish plural-quantity and plurals-candidate notices of the same kind as existing strings.
  - Verified **618 bilingual resources** and **39 pinned Tabler icons**: `folder` and `file-zip` were added at the pinned revision.
  - Seven Semgrep rules found 0 issues across production Kotlin, including new files. Android surface tests and checks passed, and Gitleaks found no leaks. No dependencies or permissions changed.
- **New regression coverage.**
  - Stops: traffic-light and shoelace stops, steady jogging and walking are not stops, short hesitations are ignored, pause boundaries, overlap counting, open live stops, splits excluding stops.
  - Live tracker: Doppler, position fallback, zero-speed providers.
  - Terrain: structure ±15 m guard, gap-limited guard, live guard, a structure ending a trend instead of mixing models, true-aspect slope scale.
  - Coach: silent while stopped, moving-time goals, kilometre splits and their comparison, measured progress instead of generic lines, auto-pause off.
  - Run-data ZIP: entries, columns, structure rows, no photos.
  - Profile: training snapshot, strict insight parsing, update cadence, consent separation, profile compatibility.
- **Retrospective replay of the private runs** (local harness, not committed):
  - **Street run.** Before the change, 28 fixes fell back to global heights and the height trace had 9 jumps of 1.5 m or more. After it, 63 fixes near a highway bridge and an underpass are unknown and no jumps remain. The 10K is unchanged: 0 structures, grades −2.8% to +2.4%.
  - **Stops.** Five stops totalling 2 min 38 s on the street run, so moving pace was 6:23 /km against 6:57 elapsed. Zero stops on the 10K.
  - **Coach.** The new coach spoke 15 cues on the street run. "Settling in" played before the traffic light, not during it. Halfway came at 12:30 moving time with a 3.8–4.0 km projection. Kilometre splits came with comparisons. One hill-recovery cue came after the real climb out of the underpass; the phone log had four after gentle downhills. On the 10K, generic endurance lines were replaced by progress checks.
- **One real AI profile request** used the owner's development key with coordinate-free summaries of the two runs. Sonnet 5.5 returned a valid assessment in English and Spanish in about 4 s, roughly US$0.012 each. The prompt was then tightened so a short history alone is not grounds for STARTING; the model still chose it from the low recorded volume and said the history is short.
- **Native API 34 checks**, with a 0.16.1 emulator and synthetic data backed up first:
  - The signed APK installed in place and cold-launched. **65/66 original non-cache files were byte-identical**; only Android's profile-install marker changed. Nothing was missing.
  - Mock GPS recording: after 45 s moving, a standstill showed **Stopped · auto-paused**, blanked pace and froze the timer at 00:49. Movement resumed the timer.
  - The first pass found that emulator fixes report zero Doppler speed while moving, which made the whole run look stopped. Fixed: leaving GPS accuracy now counts as movement whatever the speed says. A regression test covers it.
  - The saved activity showed moving 01:37, elapsed 02:42, 2 stops · 01:04 and 6:30 /km. **Download all data (ZIP)** saved one file with all expected entries.
  - The activity file menu, settings and runner profile were checked in English and Spanish. The auto-pause row was realigned with the other settings rows. The whole-run elevation chart no longer stretches a 1 m wobble to full height.
  - Afterwards the original app data was restored: **75/75 files identical** to the pre-test snapshot. The test ZIP was deleted, the app locale reset and root disabled.
- **Not verified:** physical Samsung field GPS and Doppler behaviour at real traffic lights, battery, headphones, and the live coach on a phone. AI profile updates were not tested through the in-app job.
- Artifact: `Runline-0.17.0.apk`, **0.17.0 / code 20**, SHA-256 `5fbe4c063b42e3c0e8141514252a9d4e9cefa9991af553e52873cf17f2877859`. A final clean rebuild of the natively tested source; it installed in place and cold-launched without crashes. Signing certificate remains `fc4cf764f78b745c431a632e5769348ec6488e04b4ca92515a9f589564f900ab`. Required GitHub checks gate merging the exact PR head.

## Version 0.18 validation

Recorded 6 November 2026. The private 6 November street run stayed outside Git; it was used for local replays and was briefly loaded into the emulator's app storage, then removed.

- **Build and checks.** Debug/release builds passed **360 JVM tests** with no failures or errors. Android lint reported **zero errors / 160 warnings**, the same count as 0.17. Verified **618 bilingual resources** and **38 pinned Tabler icons**: the unused `current-location` icon was removed with its source and provenance. Seven Semgrep rules found 0 issues in 112 production Kotlin files. Android surface checks and their tests passed, and Gitleaks found no leaks in history or the working tree. No dependencies or permissions changed.
- **New regression coverage.**
  - Synthetic IGN tiles: a deck over a lower road, a passage under an embankment, a walled ramp kept at its model heights, an open end and an over-long stretch staying unknown. Incremental live projection matches the saved view, and recorded GPS heights are untouched.
  - Replay trail: it ends at the arrow, shortens on a backward seek, is complete at the end and never joins across a pause.
  - `points.csv` gains the `terrain_level` column.
- **Retrospective replay of the private run** (local harness, not committed). 0.17 left 63 fixes unknown in 4 stretches. 0.18 resolves all of them, and **0 unknown heights and 0 broken height joins remain**:
  - Above at 1.69 km: the road over another road. The model dipped 2.9 m at 26%.
  - Below at 2.15 km: under the highway. The model humped 5.6 m at 44%.
  - Above at 2.89 km: a ramp.
  - Ground at 2.96 km: a 10% ramp kept at its model heights.

  A Python prototype gave identical decisions. The 10K park loop has no structures and is unchanged.
- **Native API 34 checks.** The 0.17.0 emulator data was backed up first.
  - **Update.** The signed APK installed in place over 0.17.0. **68/68 non-cache files were byte-identical.**
  - **Underpass.** With the private run loaded, replay under the A-44 highway showed a continuous slope line, "≈ −2% Downhill". The details elevation chart had no break and read 652 m there.
  - **Trail.** It grew behind the arrow at 60× and nothing was drawn ahead. The first build left the last stretch undrawn while playing, because MapLibre did not render a GeoJSON source replaced every frame. It is now drawn natively and overlaps the map line, which is re-published twice a second. The fixed build was re-checked.
  - **Controls.** Panning showed the arrow recenter control at the bottom right, level with the credits pill; tapping it resumed following and hid it. A mock-GPS recording showed the same layout on the live map.
  - **Restore.** The app data was restored to the post-update snapshot: **77/77 files identical**. Android's profile-install marker changed on the next launch. SELinux labels were restored, the run list was confirmed readable and the backup was deleted.
- **Final artifact.** A clean rebuild after two lint-only edits (equivalent visibility checks) installed in place over the tested build. It cold-launched with all 23 activities and opened a replay. Opening a synthetic activity added normal terrain and map cache files; recordings were unchanged. Root was disabled afterwards.
- **Not verified:** a physical Samsung, real bridges and underpasses other than this run, frame rate on a phone, dark mode and landscape for the new controls.
- Artifact: `Runline-0.18.0.apk`, **0.18.0 / code 21**, SHA-256 `c6f87a2c52b0d57bbe442d3b720dbe0d25444a580d0d4b0f0084b9a04dbdd133`. Signing certificate remains `fc4cf764f78b745c431a632e5769348ec6488e04b4ca92515a9f589564f900ab`. Required GitHub checks gate merging the exact PR head.

## Version 0.19 validation

Recorded 6–7 November 2026. The owner's route photos and private runs stayed outside Git.

- **The first open route.** It was rebuilt without copying from the paywalled source.
  - **Street labels.** Each zoomed photo was georeferenced from its street labels against named OpenStreetMap ways, with residuals of 15–26 px (about 20 m). Neighbouring photos were chained through shared markers.
  - **Routing.** The orange line became ordered waypoints routed along OpenStreetMap paths with BRouter's hiking profile. Snapping waypoints that caused dead ends or back-and-forths were removed.
  - **Shape.** Against the overview photo at about 6 m/px, 88% of route samples lie on the orange track. The rest are within about 40 m, where parallel riverside paths are a few metres apart.
  - **Figures.** 14.90 km against the original's 15.24 km, which includes GPS wobble. The IGN MDT05 profile is 668–927 m against 674–920 m, with the same climb, plateau and descent.
  - **Sources.** An early georeference was 445 m off: an image fit had locked onto a wrong draft route. Label anchoring found and fixed it.
- **Build and checks.**
  - **Tests.** Debug and release builds passed **371 JVM tests** (18 new) with no failures. They cover the GPX reader (entities, CDATA, namespaces, DOCTYPE/XXE refusal, malformed points), Tobler and difficulty, the real repository catalog and its hashes, catalog path and hash rejection, and sunset date and time.
  - **Follower tests.** Through an out-and-back spur, off route after 10 s and rejoining, side detection at 3 m per fix, approaching the start, and pace adaptation.
  - **Lint.** 0 errors / 165 warnings. The 5 new ones are 3 plural notices on values that are never 1, and the usual tools-constructor notices on new custom views. A byte-order-mark error found by lint was fixed.
  - **Other checks.** Verified 694 bilingual resources, 42 pinned icons (`map-2`, `alert-triangle`, `sunset` and `trekking` added) and `tools/check_routes.py`, which now runs in CI. Seven Semgrep rules found 0 issues in 119 files; the Android surface checks passed and Gitleaks found no leaks. The only permission added is VIBRATE, a normal install-time permission. No dependencies changed.
- **Native API 34 checks.** The signed update from 0.18.0 kept **70/70 non-cache files byte-identical**.
  - **Screens.** The Routes tab, route details (stats, profile, waypoints, attribution) and Hike this route all worked; the last selects Hike and shows the route row on Record.
  - **Mock-GPS hike.** The dashed planned route showed under the track. The island read "14.7 km left · ↑ 276 m · arrive …", warned of arrival after sunset, and showed the next waypoint. Stepping 120 m off gave "Off route · 123 m" in the island and the notification title. Walking back restored progress.
  - **Restart.** Reinstalling mid-hike killed the app. After Resume, route progress was rebuilt from the saved fixes, and a later deviation reported "the path is to your left".
  - **Import.** A GPX picked from Downloads imported with its metadata name, appeared under Imported and was deleted.
  - **Bugs found and fixed.**
    - Sunset was computed for the next day: the sunrise equation was given the noon Julian date. A regression test now checks the date.
    - The details screen showed "One way" for a catalog loop.
    - The list and details screens disagreed on ascent.
    - The off-route side was missing whenever a fix moved less than 8 m.
  - **Restore.** App data was restored to the post-update snapshot, **79/79 files identical**, with SELinux labels re-applied. The test GPX was removed and root disabled. The final artifact then installed in place and its Routes screens were smoke-tested.
- **Not verified:**
  - a physical Samsung on a real trail, with real GPS noise under trees or between walls;
  - vibration and spoken alerts on a phone;
  - Check for new routes, which needs `routes/` on `main` first;
  - dark mode and landscape for the new screens.
- Artifact: `Runline-0.19.0.apk`, **0.19.0 / code 22**, SHA-256 `a2d2f24e1fc2334f1218c868b5f24c3adcde35d62d162a26971192051b774a4f`. Signing certificate remains `fc4cf764f78b745c431a632e5769348ec6488e04b4ca92515a9f589564f900ab`. Required GitHub checks gate merging the exact PR head.

## Version 0.20 validation

Recorded 7 November 2026 on the API 34 emulator. Mock GPS walked the cleaned Granada route at 4 m/s near cues and 12 m/s between them. The emulator's mock fixes report no speed, so auto-pause timing on screen is not representative.

- **Route cleanup.** Every route vertex was checked against current OpenStreetMap ways (Overpass).
  - 13 retraced spurs (up to 104 m each way, two of them zero-length duplicate points) and a 154 m loop that a 28 m track crosses were removed. The real hairpins of the Sendero Acequia del Real and the 420 m out-and-back detour were kept.
  - The route is now 14.0 km, +281/−301 m, 668–927 m.
  - On the 0.19 file, the spur check flags 11 spurs of 2 m or more, plus the deliberate detour. On the new file it finds only the detour, and accepts it because it ends at "Fin del desvío".
- **Build and checks.**
  - **Tests.** **396 JVM tests**, 25 new, with no failures. They cover:
    - manoeuvre kinds, merging, wiggles, retrace against hairpin, zig-zags, junction filtering and the real Granada route;
    - climbs with bumps, flats and thresholds;
    - cue timing, "then" turns, joins, stale cues and notes deferred behind turns;
    - the navigation and route speech priorities, the preview timeline and the catalog's notes and junctions;
    - joining beside an earlier parallel leg;
    - where the replay trail stops behind the arrow.
  - **Lint.** 0 errors / 172 warnings. The 7 new ones are 5 plural notices on metre values that are never 1, and the usual tools-constructor and KTX notices.
  - **Other checks.** Verified 736 bilingual resources, 53 pinned icons (11 added) and the route catalog. Seven Semgrep rules found 0 issues in 124 files. The surface checks passed, and Gitleaks found no leaks in 54 commits. No permissions or dependencies changed.
- **Granada route data.**
  - **Cues.** With junctions there are 35 turn cues, against 72 from geometry alone. Exactly one is "turn around", at the detour's end.
  - **Climbs.** +205 m over 2.7 km at 8%, and −211 m from 5.6 to 9.3 km at 6%. The flat riverside is not counted.
- **Native API 34 checks.**
  - **Screens.** Route details showed fixed Start and Preview. Preview played the route in 3D, 2:57:53 at 480×, with planned pace. Start began a Hike recording with the route in one tap.
  - **Island.** It showed:
    - the arrow, distance and instruction ("570 m · Bear left");
    - "Climbing: 195 m left over 2.6 km, ~8%" and "Descent ahead: 211 m over 3.7 km, ~6%";
    - the moving elevation-ahead profile;
    - "Next: … in 80 m".
  - **Speech, whole route.** The coach log over the whole route recorded:
    - every turn spoken about 80 m ahead and again at 20 m, including "Turn right now, then left" at two street offsets and "Turn around now" at the detour's end;
    - all five place notes, once each;
    - the descent cue 200 m ahead;
    - "Route complete".
  - **First walk.** The host Mac slept twice during it, and the walk script then sent its backlog of positions too fast. The recorder rightly rejected those jumps, and the route follower raced ahead. That stretch (7.2 km to the end) was walked again with sleep prevented.
  - **Bugs found and fixed.**
    - **Watchdog.** The fixed 15 s speech watchdog cut the 240-character Genil note and dropped the pending "Turn right now".
    - **Note during a turn.** A note could start just before a turn.
    - **Joining at 7.2 km.** It latched onto an earlier leg and gave a false "Off route · 100 m".
    - **Waypoint distance.** The next waypoint read "in 0.0 km".
    - **Replay trail.** The owner reported that the trail behind the arrow doubled or went wild. A 0.19 replay at 480× showed two lines splitting at turns, and long straight lines.
      - **Doubling.** The map trail and the native head overlay drew the same stretch, the overlay with a newer camera than the map's last frame.
      - **Straight lines.** Points behind the tilted camera were projected anyway.
      - **Fix.** While playing, the map trail now stops 400 m behind the arrow, and the head skips points outside the visible region. With the head tinted red for diagnosis, it alone drew the stretch behind the arrow at 120×, through the acequia hairpins.
      - **Emulator limit.** At 480× the emulator's renderer can freeze the map for about a second, so a frozen frame still shows stale lines then. A phone renders far faster.
    - **Re-walk.** A re-walk from 7.2 km to 9.8 km with the fixes confirmed the first four: no off-route on joining, both notes completed, the turn spoken before the Genil note, and the next waypoint in metres.
  - **Update and restore.**
    - **Before the walks.** App data was backed up with root. The signed builds installed in place over 0.19.
    - **Update.** The exact final release artifact then updated a restored 0.19 in place and kept **80/80 non-cache files byte-identical**. A replay on it drew one trail behind the arrow.
    - **Restore.** The emulator was returned to 0.19 with its data, **80/80 files identical** and SELinux labels re-applied, and root was disabled.
- **Not verified:**
  - a physical phone on a real trail;
  - Spanish speech on the emulator (the strings pass the bilingual check);
  - an imported GPX walk;
  - dark mode and landscape for the new island rows;
  - real headphones or music.
- Artifact: `Runline-0.20.0.apk`, **0.20.0 / code 23**, SHA-256 `6f6ca22fa32fcbda3aa5af75f2eb4ae24f0519480eb8b7632f8e75b5bb993566`. The signing certificate is unchanged. Required GitHub checks gate merging the exact PR head.

## Version 0.20.1 validation

Recorded 8 November 2026.

- **Cause.** The 0.20 terrain footprint check treated open hillside as a structure. With real IGN tiles over the Granada loop, fixes on the path lost their height on 660, 1316 and 1676 of 2136 samples at 5, 10 and 15 m accuracy. The longest gaps were 2.1, 5.1 and 5.9 km, all on the Dehesa climb.
- **Fix, same data.** With the hillside test, the counts fall to 40, 47 and 187, with longest gaps of 266, 313 and 388 m. At 5 and 10 m the longest remaining gap is where the path crosses the Barranco del Olivar ravine. Shifting the whole walk 6 m off the path moved hillside heights by 0.5 m at the median, 2 m at the 90th percentile and 5 m at most.
- **Build and checks.** **401 JVM tests**, 5 new, with no failures. The new tests cover:
  - hillside, cliff, bank, ravine and ridge footprints;
  - a contour path and a climb across a 40% IGN hillside;
  - a global-terrain hillside against a cliff;
  - preview heights.

  The 0.17 and 0.18 bridge, underpass and walled-ramp fixtures pass unchanged. Lint: 0 errors / 172 warnings, unchanged. Verified 736 bilingual resources, 53 pinned icons and the route catalog. Seven Semgrep rules found 0 issues in 124 files. The surface checks passed and Gitleaks found no leaks.
- **API 34 emulator.** Mock GPS walked 1.7 km of the Dehesa hillside, from 3.3 to 5.0 km along the route, at GPS ±5 m.
  - **Recording replay.** Recorded on 0.20.0, the replay had IGN heights for 7% of the walk, 0 m climbed and an almost empty chart. After updating in place to 0.20.1, the same recording showed 81% mapped, 10 m climbed and 3 m descended. The chart was continuous except at the Barranco del Olivar crossing.
  - **Route preview at 6.2 km.** 0.20.0 showed holes through the mountain section and 165 m climbed. 0.20.1 showed a continuous profile and 264 m.
  - **Update and restore.** The update kept the recording. The emulator was restored to 0.19 with its data (80/80 files), and root was disabled.
- Artifact: `Runline-0.20.1.apk`, **0.20.1 / code 24**, SHA-256 `0dbd86d0357b2acb458a16d40f4b5be792360c45a87418accd13306688b98566`. The signing certificate is unchanged.

## Version 0.20.2 validation

Recorded 9 November 2026 for **0.20.2 / code 25**.

- **Build and checks.** Debug, release and Android test APK builds passed. All **410 JVM tests**, including nine new import regressions, passed. Lint reported **0 errors / 173 warnings**. Checks passed for **746 bilingual resources**, **53 pinned icons**, **one catalog route** and **22 security-checker tests**. The new JVM cases cover supplied-name precedence and cleanup, generic filenames, loop/out-and-back geometry, nearby waypoint labels, namespace/extension isolation, malformed XML and external entities, literal CDATA and parser size/point limits.
- **Native import tests.** All **seven Android tests** passed on the accelerated API 34 runner in [CI run 42544845919](https://github.com/adrianetti/runline/actions/runs/42544845919/job/128927487521). They cover preparation without saving, unchanged original GPX bytes and stable import IDs, content-URI restrictions, Open with intent matching, sharing into an already running app, edited-name retention through recreation and cancellation, blank-name fallback, repeated confirmation and invalid GPX rejection. The sharing test's ActivityScenario cleanup was corrected to restore its launch intent before teardown; the corrected local rerun also passed.
- **Files picker.** The accelerated Android smoke check selected a synthetic GPX from Downloads, proposed its filename, confirmed one saved route and compared the saved GPX byte-for-byte with the original. Native captures show the picker, naming step and saved route.
- **Bounded visual evidence.** Local captures cover English/light Routes and the naming dialog, Spanish/dark at 1.3× text, and the invalid-GPX state. The local software-rendered emulator developed system-wide display ANRs involving SystemUI and other services; the Runline trace was blocked in Android window removal during display recreation. The complete native test suite therefore ran on the accelerated CI emulator.
- **Scope.** The native tests use a synthetic content provider. These results do not establish all third-party provider behavior, landscape/keyboard layouts, an exact signed-update retention check or physical-device/field operation. Independent final review and publication are not asserted by this record. [PR #33](https://github.com/adrianetti/runline/pull/33) and the [v0.20.2 release page](https://github.com/adrianetti/runline/releases/tag/v0.20.2) are the records for final CI, screenshot and signed-artifact evidence.
