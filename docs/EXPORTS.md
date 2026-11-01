# Activity file exports

Runline 0.5 exports a local GPX file. The Strava button, website launch and account handoff are removed. Export does not upload or publish an activity. The previous integration research is archived in [STRAVA.md](STRAVA.md).

## Save a file

Open a saved activity and tap the download control, **Export GPX** / **Exportar GPX**. Android 10+ saves to **Downloads** without a broad storage permission or save dialog. Android 8/9 uses `ACTION_CREATE_DOCUMENT`; an automatic Downloads failure also offers that picker. The source recording remains in Runline after success, cancellation or failure.

The filename combines the localized activity type, distance in kilometres and local start date/time. For example, `Caminata 8,42 km - 2026-11-01 10-30.gpx` in Spanish or `Walk 8.42 km - 2026-11-01 10-30.gpx` in English. These are illustrative values, not bundled recordings. Invalid filename characters are removed. Runline does not geocode or guess a street name. Downloads reserves duplicate names with a suffix before the extension, such as `(2).gpx`; other document providers control their own naming; the success message reads back the actual saved name and offers **Copy name** only after an explicit tap.

**Share file** in the activity menu opens Android's generic chooser. A receiving app must support GPX; the presence of an app in the chooser is not a guarantee that it accepts a completed workout. No particular destination or published state is assumed.

## Contents and privacy

GPX preserves accepted coordinates, UTC sample timestamps, available GPS altitude and track segments. The stored activity type is included; legacy sessions without one remain Run. Display smoothing never changes exported points. The filename uses local time for readability; GPX point timestamps remain UTC. Distance is Runline's GPS estimate, so another app may calculate a different total.

The file includes precise start/end locations and timestamps. Choose its destination deliberately. GPX is a portable route export, not a full backup of every timing, recovery or internal storage field. Uninstalling Runline or clearing its storage still removes internal recordings and recovery copies; save important files outside the app.

## Implementation and lifecycle

`sharing/ActivityExporter.kt` owns saving, `ActivityExportName.kt` provides localized names, and `core/ActivityExportFileName.kt` formats them. `MainActivity` invokes this exporter from replay and the menu. GPX generation and destination writes use a single background worker retained by a `ViewModel`; repeated requests while saving/picking are rejected. An in-flight write survives Activity rotation, and one-shot results wait until the Activity is resumed. The pending run ID is saved while a document picker is open, allowing the source session to be reloaded after recreation.

On Android 10+, `MediaStore.Downloads` creates an item with `IS_PENDING=1`; after writing and closing the stream, the exporter clears pending status. Failed writes attempt to delete the newly created row before offering the picker. Downloads name allocation and insertion are serialized within the app, and the actual provider name is checked to retain `.gpx` before writing. Unexpected collision names trigger a bounded retry of a new empty pending item. No browser is launched and no account tokens or credentials are used.

Process death can interrupt a write. A pending Downloads item may remain hidden until the provider expires it; a picker destination may contain a partial file. Cleanup after process death is not guaranteed. The original activity is never deleted by export, so the user can retry. Cloud document providers have their own availability, login and network requirements.

## Verification status

Version 0.5 API 34 checks confirmed direct Downloads exports in Spanish and English without opening a browser/account flow. A parsed two-hour walk export retained 7,201 points, two segments and walking type; a separate live-recorded Walk export matched all 13 stored fixes and three segments. Readable names used the selected language and date/time. Two repeated exports produced `(2).gpx` and `(3).gpx`, each with all 7,201 points and two segments, while keeping the earlier exports. See [release evidence](../README.md#version-05-verification). The broader [checklist](TESTING.md) includes older-Android picker runtime, failure injection, duplicate names and lifecycle cases; unlisted cases are not implied to have passed. Earlier Downloads/browser results remain historical v0.2–v0.4 evidence.
