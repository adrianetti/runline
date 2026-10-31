# Runline

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Implementation choice: native Kotlin Android app. Android Views and Material components, private local files, Android LocationManager. The name Runline is a working name, not a user-approved brand.

## Users

The owner currently records runs using an Android phone and wants their own application to record runs and access session data without paying Strava for analytics.

## Product Purpose

Record runs, retain the original location samples on the phone, and calculate understandable running statistics. Keep a practical way to share runs with friends on Strava.

## Operating Context

Outdoor running with a phone, including a locked screen. Review sessions afterward. A watch is not currently part of the user's setup.

## Capabilities and Constraints

Confirmed: Android phone recording is the first priority; analysis and Strava sharing are desired. Implementation scope for the first version: foreground GPS recording, pause and resume, on-device history, route trace, elapsed/active time, distance, average pace, kilometre splits, and GPX export. Strava upload is manual in this version. Direct API access requires subscription under current Strava policy. No account, cloud sync or live basemap is required for this initial build. Heart rate and cadence are unavailable without a suitable source and must not be fabricated.

## Evidence on Hand

No real run data has been supplied. The workspace was empty. Android SDK 34 and an Android 14 emulator are installed. Real-device GPS and battery behavior require field testing.

## Product Principles

- Preserve the user's original recordings and make export possible.
- Make start, pause, resume and finish unambiguous.
- Distinguish recorded data, estimates, missing data and interrupted recordings.
- Keep personal routes on the device until the user exports them.

## Brand Commitments

User-directed refinement: simple monochrome palette, very short copy, few controls, one free sporty icon set (Tabler outline, MIT). One-tap start after Android permissions. Strong recoverability across UI closure, process termination and power interruption; never claim GPS recording while the phone is off.

## Open Decisions

Final app name, future watch support, automatic Strava integration, distribution and cloud backup.
