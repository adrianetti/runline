# Runline

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Native Kotlin Android app: Android Views and Material components, private local files, Android LocationManager, and MapLibre Android 11.13.5. Version 0.4.0 (code 4) targets SDK 34 and supports Android 8.0+. Build tools are AGP 8.5.2, Kotlin 2.0.20, Gradle 8.7 and JDK 17. Runline remains a working name.

## Users

The owner currently records runs using an Android phone and wants their own application to record runs and access session data without paying Strava for analytics.

## Product Purpose

Record runs, retain the original location samples on the phone, and calculate understandable running statistics. Review routes over real satellite imagery, replay recorded movement, and share runs with friends through Strava's file uploader.

## Operating Context

Outdoor running with a phone, including a locked screen. Review sessions afterward. A watch is not currently part of the user's setup.

## Capabilities and Constraints

Version 0.4 includes foreground GPS recording, pause/resume, local history and recovery, active/elapsed time, distance, pace, splits and GPX export. Satellite maps use global EOX imagery, detailed IGN/PNOA imagery in Spain and road/place labels. EOX's global imagery is approximately 10-metre resolution and licensed for noncommercial use. Credits, source details and limitations remain accessible on the map. Providers receive visible-area tile requests and the phone's network address, not the run file or timestamps. Map failure does not stop offline recording or local analysis; whole-region offline map downloads are not implemented.

Active runs use a full-content satellite map with a bottom statistics/control island (lower left in landscape). Follow centers the marker in the unobscured map at zoom 18; a movement-derived Tabler arrow indicates GPS course on a north-up map. A neutral marker remains until reliable movement establishes a heading; stops retain the last course, and gaps/segments reset it. Pan/pinch disengages follow, and the location control restores it. This display is independent of GPS capture.

The ongoing notification provides active time, distance, average pace and expanded GPS status. Recording offers Pause; paused/recovered sessions offer Resume and Finish. Actions are checked against both session identity and current recording state. Lock-screen display follows Android/user settings; Samsung Now bar placement is not promised, and no media session is used.

Saved runs have seekable playback at 1×/5×/10×/30×/60×, defaulting to 1× and paused when backgrounded. Replay follows observed GPS intervals, skips pauses and gaps over 15 seconds, and does not export video. Speed, GPS altitude and filtered ascent/descent are estimates; missing measurements remain unavailable and altitude-chart gaps are retained. Heart rate and cadence are unavailable without a suitable source and must not be fabricated.

The toolbar's Strava action saves to Downloads automatically on Android 10+, then opens the browser uploader. Android 8/9 and failed automatic saves use a file picker. Selecting the file, reviewing visibility and publishing remain manual. The user confirmed that this must stay subscription-free, so the current path requires no personal API application. New personal Strava API application registration currently requires a subscription; an existing registered client may offer another path. No OAuth connector, automatic upload, Runline account or cloud recording sync is implemented. See docs/STRAVA.md and docs/MAPS.md for sourced details.

## Evidence on Hand

The private repository is [adrianetti/runline](https://github.com/adrianetti/runline). Version 0.4 passed signed debug/release builds, 53 JVM tests and lint with zero errors (28 warnings). API 34 checks covered signed-update retention and draft recovery, heading/follow behavior, bounded light/dark/large-font/landscape layouts, notification controls and default-1× replay. Lock-screen statistics required allowing silent notifications on the no-PIN emulator; secure lock-screen and physical Samsung/Now bar behavior remain unverified. Detailed evidence, historical v0.2/v0.3 results and remaining device checks are recorded in README.md and docs/TESTING.md. Synthetic checks do not establish field GPS accuracy or battery reliability; Android 8/9 picker runtime, 16 KB devices and actual Strava publication remain unverified.

## Product Principles

- Preserve the user's original recordings and make export possible.
- Make start, pause, resume and finish unambiguous.
- Distinguish recorded data, estimates, missing data and interrupted recordings.
- Keep the recording local; explain that loading maps exposes the viewed area to tile providers and that exporting sends precise route data to a chosen destination.
- Keep maps and replay independent of recording, so a visual or network failure does not stop GPS capture.

## Brand Commitments

User-directed refinement: simple monochrome controls, very short copy, few controls, one free sporty icon set (Tabler outline, MIT). Real satellite imagery intentionally keeps its colors. One-tap start after Android permissions, a full-content live map with a reachable bottom information island, lock-screen recording controls where Android permits, and a visible Strava toolbar action. Strong recoverability across UI closure, process termination and power interruption; never claim GPS recording while the phone is off. Version 0.4 keeps the v0.2 app-specific signing key so updates can retain user data.

## Open Decisions

Final app name, future watch support, automatic Strava integration, video export, broader distribution and cloud backup. Personal APK distribution uses the private GitHub repository; broader use requires reviewing imagery licensing.
