# Runline

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Native Kotlin Android app: Android Views and Material components, private local files, Android LocationManager, and MapLibre Android 11.13.5. Version 0.5.0 (code 5) targets SDK 34 and supports Android 8.0+. Build tools are AGP 8.5.2, Kotlin 2.0.20, Gradle 8.7 and JDK 17. Runline remains a working name.

## Users

The owner records outdoor activities with an Android phone and wants their own recordings and session statistics without a paid analytics subscription. They use Spanish and have requested English as the other supported app language.

## Product Purpose

Record runs, walks and hikes, retain original location samples on the phone, and calculate understandable activity statistics. Review routes over satellite imagery, replay recorded movement, inspect climbs and descents, and export a portable GPX file.

## Operating Context

Outdoor activity with a phone, often locked or in a pocket, followed by review at home. A watch is not currently part of the user's setup. Long recordings, including a reported two-hour walk, make efficient playback and clear elevation information important.

## Capabilities and Constraints

Version 0.5 includes foreground GPS recording, pause/resume, local history and recovery, active/elapsed time, distance, pace, splits and GPX export. The idle Record tab offers Run/Walk/Hike selection and Start, without a map. The choice is remembered and stored in each session through recovery and export. Legacy sessions without a type remain Run; speed is never used to infer activity type.

Active sessions and replay use a full-content satellite map with a bottom island, lower left in landscape. Live follow uses zoom 18 and a movement-derived Tabler heading arrow on a north-up map. A loading overlay hides the initial map until a usable position exists. Panning disengages follow; recenter restores it or explains why a position is unavailable. Valid consecutive live fixes animate smoothly with the camera, adding a short visual delay. Gaps, segment boundaries and implausible jumps are not animated across. Smoothing is display-only and never changes recorded samples, metrics or exported coordinates.

Replay defaults to 1×, offers 5×/10×/30×/60× and seeking, and pauses in the background. It follows observed GPS intervals, skipping pauses and gaps over 15 seconds; no video file is produced. The compact island shows distance, speed, climb/descent and playback controls. Tapping its statistics or Details opens totals, splits and an elevation-change profile. Climb and descent are prominent; raw altitude remains secondary. Elevation uses GPS heights and a 3-metre noise threshold, without terrain correction or grade percentages. Unknown measurements remain unavailable. Heart rate and cadence require a suitable source and must not be fabricated.

The personal build defaults to Spanish, with English selectable in the app menu and per-app locale support. Native controls, errors, notifications and export activity names are localized. Android dialogs and third-party providers retain their own locale and styling.

Export GPX saves directly to Downloads on Android 10+; Android 8/9 and failed automatic saves use a document picker. Encoding and file writes run off the UI thread. Names combine localized activity, distance and local start date/time, without a guessed street name. The actual saved name can be copied. Share file remains available through Android's chooser. The Strava action, browser handoff and integration have been removed; export neither opens a browser nor connects or uploads to an account. There is no Runline account or cloud recording sync. See docs/EXPORTS.md; docs/STRAVA.md is historical research only.

Satellite maps use global EOX imagery, detailed IGN/PNOA imagery in Spain and road/place labels. Global imagery is approximately 10-metre resolution and licensed for noncommercial use. Credits and limitations remain accessible. Providers receive visible-area tile requests and the phone's network address, not raw run files or timestamps. Map failure does not stop local recording or analysis. Whole-region offline downloads are not implemented; see docs/MAPS.md.

The ongoing notification provides active time, distance, average pace and expanded GPS status. Recording offers Pause; paused/recovered sessions offer Resume and Finish. Actions validate session identity and state. Lock-screen presentation follows Android/user settings; Samsung Now bar placement is not promised and no media session is used.

## Evidence on Hand

The private repository is [adrianetti/runline](https://github.com/adrianetti/runline). Version 0.5 passed 69 JVM tests, lint with zero errors, signed-update retention and bounded API 34 replay, localization, map, notification and export checks. [README.md](README.md#version-05-verification) records the exact artifact and limits. The user reported reviewing a two-hour walk; this is product feedback, not a controlled battery or GPS-accuracy test.

Historical v0.4 passed signed debug/release builds, 53 JVM tests and lint with zero errors (28 warnings). API 34 checks covered signed-update retention and draft recovery, heading/follow behavior, bounded light/dark/large-font/landscape layouts, notification controls and default-1× replay. Lock-screen statistics required allowing silent notifications on a no-PIN emulator. Secure lock-screen, physical Samsung field/battery behavior, Android 8/9 picker runtime and 16 KB devices remain unverified. README.md and docs/TESTING.md retain the bounded v0.2–v0.4 evidence; it must not be presented as a v0.5 pass.

## Product Principles

- Preserve original recordings and make export possible.
- Make selecting an activity, starting, pausing, resuming and finishing unambiguous.
- Distinguish recorded data, display interpolation, estimates, missing data and interruptions.
- Keep recording local; disclose visible-area tile requests and precise route data in exports.
- Keep maps and replay independent of capture so a visual or network failure does not stop recording.
- Keep normal screens concise, with additional statistics available on demand.

## Brand Commitments

Simple monochrome controls, very short copy, one free sporty icon family (Tabler outline, MIT) and real-color satellite imagery. One-tap start after required permissions, full-content live/replay maps with reachable islands, elevation-focused details and lock-screen controls where Android permits. Strong local recoverability without claiming a powered-off phone can record or that internal copies are cloud backup. Version 0.5 retains the v0.2 per-app signing key so compatible updates can retain recordings.

## Open Decisions

Final app name, future watch support, video export, broader distribution and cloud backup. Strava integration is outside the current scope. Personal APK distribution uses the private GitHub repository; broader use requires reviewing imagery licensing.
