# Runline

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Native Kotlin Android app: Android Views and Material components, private local files, Android LocationManager, and MapLibre Android 11.13.5. Version 0.8.0 (code 8) targets SDK 34 and supports Android 8.0+. Build tools are AGP 9.5.0, built-in Kotlin with a 2.4.21 override, Gradle 9.8.2 and JDK 17. Runline remains a working name.

## Users

The owner records outdoor activities with an Android phone and wants their own recordings and session statistics without a paid analytics subscription. They want the app to follow their phone language, with English fallback and Spanish support. They also want natural optional headphone feedback, editable activity journals and portable history for analysis in tools such as ChatGPT. The current personal goal is a half marathon in a few months; its actual date and baseline belong in the app, not guessed from the chat.

## Product Purpose

Record runs, walks and hikes, retain original location samples on the phone, and calculate understandable activity statistics. Review routes over satellite imagery, replay recorded movement, inspect climbs and descents, hear optional progress/goal cues, inspect history charts, and export/share portable GPX, CSV and ZIP files.

## Operating Context

Outdoor activity with a phone, often locked or in a pocket, followed by review at home. A watch is not currently part of the user's setup. Long recordings, including a reported two-hour walk, make efficient playback and clear elevation information important.

## Capabilities and Constraints

Version 0.8 retains foreground GPS recording, pause/resume, local history and recovery, active/elapsed time, distance, pace, splits and GPX export. The idle Record tab offers Run/Walk/Hike selection, Start, Voice coach and Training plan, without a map. Record and Activities remain the only tabs; a single Settings action opens AI, Language, Recording protection and About. The choice is remembered and stored in each session through recovery and export. Legacy sessions without a type remain Run; speed is never used to infer activity type.

Active sessions and replay use a full-content satellite map with a bottom island, lower left in landscape. Live and replay follow use zoom 18, a movement-derived Tabler heading arrow and a gently turning, tilted camera. Fit-route returns to a flat north-up overview; system Remove animations uses flat north-up follow. Raster road/place labels are omitted, while imagery, the complete recorded route and compact attribution remain visible. A loading overlay hides the initial map until a usable position exists. Panning disengages follow; recenter restores it or explains why a position is unavailable. Valid consecutive live fixes animate smoothly with the camera, adding a short visual delay. Gaps, segment boundaries and implausible jumps are not animated across. Smoothing is display-only and never changes recorded samples, metrics or exported coordinates.

Replay automatically selects the available multiplier that puts observed playback closest to 30 seconds, from 1×/5×/10×/30×/60×/120×/240×/480×. Manual selection and seeking remain available; position/speed survive recreation and playback pauses in the background. A movement-derived arrow and close zoom-18 follow camera track the interpolated observed path; fit-route stays available. This is an approximate duration, not a fixed 30-second video. It follows observed GPS intervals, skipping pauses and gaps over 15 seconds; no video file is produced. The compact island shows distance, speed, climb/descent and playback controls. Live and replay islands include a subdued trace of actual GPS elevation samples; missing or unsuitable height data leaves gaps or no trace. Tapping its statistics or Details opens totals, splits and an elevation-change profile. Climb and descent are prominent; raw altitude remains secondary. Elevation uses GPS heights and a 3-metre noise threshold, without terrain correction or grade percentages. Unknown measurements remain unavailable. Heart rate and cadence require a suitable source and must not be fabricated.

The app follows phone language by default, with English fallback and Spanish support. System/English/Spanish can be selected explicitly; v0.6 resets the old forced-Spanish default once. Native controls, errors, notifications and export activity names are localized. Android dialogs and third-party providers retain their own locale and styling.

History charts cover the last four local Monday-starting calendar weeks, including the current partial week. Type filters affect charts only. Distance/time/count totals retain no-GPS activities where meaningful; pace comparisons use one activity type and require usable distance/time. The trend uses the latest eight eligible activities, and the period average is distance-weighted. Saved activity rows and whole-history exports are not limited by chart filters.

Voice coach is optional and off by default, reachable before or during capture. Progress, distance, pace and distance-within-active-time goals use original saved GPS estimates; explicit pauses are excluded from goal time. An installed offline English/Spanish TTS voice is required. The service owns brief, throttled prompts; stale GPS, pause/recovery and disabled guidance do not produce old-cue catch-up. Temporary MAY_DUCK audio focus asks compatible players to lower music, then releases focus; direct global-volume changes and music-player impersonation are prohibited. The user reports v0.6 voice coaching worked well during a run, without a controlled audio-route audit. Version 0.7 adds separately switchable pace guidance, encouragement, terrain and lap feedback, default on within the coach opt-in. Natural phrase alternatives rotate without immediate repetition; stronger pace hysteresis reduces target-boundary chatter. Conservative repeated-loop matching can compare four equal-distance sections, while GPS climb/descent estimates soften pace instructions. No named places, effort measurements or terrain-corrected grades are invented. See docs/COACHING.md.

Completed activities offer a journal editor after the route is safely saved. Title, description, optional effort, activity type, routine type and optional images can also be edited later. Metadata lives in a versioned atomic sidecar; edits do not rewrite accepted GPS samples, timer history or recovery records. Images are selected through Android and copied into private local storage with limits; no broad gallery permission is needed. Exports use edited titles/types and metadata where their format supports them. Photo export is not automatic.

The Anthropic module is separately optional and off by default, configured under Settings → AI with a personal key encrypted through Android Keystore. Provider/model dropdowns offer Anthropic and Haiku/Sonnet presets, with a custom-model option. Review/personalization actions are hidden until the module is enabled and a key is configured; opening settings makes no network request. Live advice, automatic reviews, automatic plan updates and note inclusion are separate choices. Short live advice runs periodically with bounded requests and fresh-response guards; richer reviews use activity/history summaries. No raw coordinates or photos are sent by this module. Training goals are 5K, 10K, Half marathon (21.1 km), Personal progress or a custom distance. Distance goals use a native exact-day picker or target-month choice, preserving month precision; Personal progress creates an eight-week schedule without a race target. Recent Run history supplies a visible 7/28-day recorded average and an adjustable planning base; sparse history uses a separately labelled gentle suggestion, not inferred fitness. Local plans work without AI, then optional Anthropic personalization can adapt future sessions. Future sessions remain bounded; missed sessions are not crammed into later days, and a target date does not certify readiness. Automatic adaptation occurs on app open/completion, not through an always-running scheduler. OpenAI is a future provider; see docs/AI.md.

Single-activity GPX supports Downloads, Save to… and Android Share file; saved outcomes also offer Share. Whole-history actions create a coordinate-free summary CSV or ZIP containing every saved activity’s GPX, the CSV and an explanatory README. Android 10+ Downloads needs no broad storage permission; older devices/failures use a picker. Drive is available only through a compatible installed/configured provider or share target. Runline connects no cloud account and does not verify a recipient’s upload. Encoding and copying run off the UI thread. Names remain readable, extension-safe on collision, and free of guessed street names. Strava remains removed. See docs/EXPORTS.md; docs/STRAVA.md is historical research only.

Satellite maps use global EOX imagery and detailed IGN/PNOA imagery in Spain, without the raster road/place-label overlay. Global imagery is approximately 10-metre resolution and licensed for noncommercial use. Credits and limitations remain accessible. Providers receive visible-area tile requests and the phone's network address, not raw run files or timestamps. Map failure does not stop local recording or analysis. Whole-region offline downloads are not implemented; see docs/MAPS.md.

The ongoing notification provides active time, distance, average pace and expanded GPS status. Recording offers Pause; paused/recovered sessions offer Resume and Finish. Actions validate session identity and state. Lock-screen presentation follows Android/user settings; Samsung Now bar placement is not promised and no media session is used.

Settings, coaching, training, journal and replay-detail sheets keep Close fixed above scrolling content. Scrolling or an outside tap does not dismiss them; Back and Close remain available. Long explanations and AI plan notes are on demand.

## Evidence on Hand

Version 0.8 simplifies navigation, setup and plan inputs, stabilizes sheets and updates map/island presentation. Its integrated debug/release build passed 158 JVM tests and lint with zero errors/117 warnings. Completed API 34 update-retention, recovery and UI checks, independent finish review and unpublished-release status are recorded in [README.md](README.md#version-08-verification); historical v0.7 results are not v0.8 evidence. Version 0.7 introduced the optional AI/training and activity-journal modules. Its build, runtime, signing and release checks are recorded in README.md and docs/TESTING.md only as performed. Do not treat historical evidence below as a v0.8 pass.

The private repository is [adrianetti/runline](https://github.com/adrianetti/runline). Historical final v0.6 debug/release builds passed all 96 JVM tests, with zero failures or errors; lint reported zero errors and 37 warnings. All 24 pinned icons and 270 English/Spanish string/plural resources passed source/parity and placeholder checks. Bounded API 34 checks covered update retention, phone-language migration, history/export UI, CSV/ZIP contents, sharing and coaching validation/preview. [README.md](README.md#version-06-verification) records the exact verified scope, release evidence and remaining checks.

Historical v0.5 passed 71 JVM tests, lint with zero errors, signed-update retention and bounded API 34 replay, localization, map, notification and export checks. [README.md](README.md#version-05-verification) retains its exact artifact and limits. The user’s two-hour walk is product feedback, not a controlled battery or GPS-accuracy test.

Historical v0.4 passed signed debug/release builds, 53 JVM tests and lint with zero errors (28 warnings). API 34 checks covered signed-update retention and draft recovery, heading/follow behavior, bounded light/dark/large-font/landscape layouts, notification controls and default-1× replay. Lock-screen statistics required allowing silent notifications on a no-PIN emulator. Secure lock-screen, physical Samsung field/battery behavior, Android 8/9 picker runtime and 16 KB devices remain unverified. README.md and docs/TESTING.md retain the bounded v0.2–v0.4 evidence; it must not be presented as a v0.6 pass.

## Product Principles

- Preserve original recordings and make export possible.
- Make selecting an activity, starting, pausing, resuming and finishing unambiguous.
- Distinguish recorded data, display interpolation, estimates, missing data and interruptions.
- Keep recording local; disclose visible-area tile requests, precise route exports and explicitly enabled AI summary/notes transfers. Never place API keys in recordings, exports or logs.
- Keep maps and replay independent of capture so a visual or network failure does not stop recording.
- Keep normal screens concise, with additional statistics and optional spoken guidance available on demand.
- Ask other apps for temporary audio focus; never force global music volume or imply a recipient completed an upload.

## Brand Commitments

Simple monochrome controls, very short copy, one free sporty icon family (Tabler outline, MIT) and real-color satellite imagery. One-tap start after required permissions, full-content live/replay maps with reachable islands, elevation-focused details and lock-screen controls where Android permits. Strong local recoverability without claiming a powered-off phone can record or that internal copies are cloud backup. Updates retain the v0.2 per-app signing key so compatible updates can retain recordings.

## Open Decisions

Final app name, future watch support, video export, broader distribution and cloud backup. Strava integration is outside the current scope. Personal APK distribution uses the private GitHub repository; broader use requires reviewing imagery licensing.
