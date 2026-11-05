# Activity file exports and sharing

Runline 0.7 saves or shares local GPX, CSV and ZIP files. Strava remains removed. File export connects no external account and does not automatically sync recordings; a destination chosen through Android handles its own account, network and upload.

## One activity

Open a saved activity and use its download control to choose **Downloads**, **Save to…** or **Share file**. The options menu exposes the same actions. Downloads saves GPX directly on Android 10+ without broad storage access. Android 8/9 and failed automatic saves use the document picker; Save to… always opens it. A successful save shows the actual filename and offers **Share**.

Share file opens Android’s chooser with a read-only content URI, matching MIME type and stream grant. Google Drive can appear when an installed/configured Drive app exposes a compatible share target. Save to… can expose Drive through Android’s document-provider UI. Availability is provider/device-specific; an entry in a chooser is not evidence that the recipient completed an upload or can import a workout. The user selects the destination. No real Drive account upload has been verified for this release.

Names use an edited title when present, otherwise localized activity, followed by recorded kilometres and local start time, such as `Caminata 8,42 km - 2026-11-01 10-30.gpx` or `Walk 8.42 km - 2026-11-01 10-30.gpx`. These are examples. Invalid filename characters are removed; streets are not geocoded or guessed. Downloads allocates collision suffixes before the extension, such as `(2).gpx`, and checks the provider’s actual name. Other document providers control their own naming.

## All saved activities

**Share** and **Export** below the Activities heading offer two formats. Both include every saved activity, independent of the chart type filter or four-week window. An unfinished recording is excluded.

| Format | Contents | Intended use |
| --- | --- | --- |
| Summary CSV | One row per saved activity; no raw route coordinates | Compact analysis in ChatGPT or a spreadsheet |
| All activities ZIP | `summary.csv`, `README.txt` and one GPX per activity under `activities/` | Analysis that needs routes, plus a portable collection of GPS files |

ZIP filenames combine the localized collection name and export time. Each internal GPX has a readable activity/distance/start-time name; collisions remain unique. `gpx_file` in the CSV points to the corresponding ZIP entry. A standalone CSV does not include those GPX files.

The CSV uses stable English headers, decimal points, ISO UTC timestamps and additional local start/time-zone columns. Fields include activity identity/type, active/elapsed/GPS-observed seconds, distance in kilometres, average pace in seconds per kilometre, average speed in kilometres per hour, ascent/descent in metres, sample count, exported-segment count, title, description, effort and routine. Formula-like user text is prefixed with an apostrophe in CSV to avoid spreadsheet execution; GPX preserves escaped title/notes without that prefix. Names are bounded and strip unsafe filename/bidi/control characters. Blank cells mean unavailable measurements, not zero. Quoted fields preserve commas, quotes and newlines.

## Meaning and privacy

GPX preserves accepted coordinates, UTC sample timestamps, available GPS altitude and recording segments. Display smoothing never adds exported points. Legacy sessions without a stored type remain Run. Average pace/speed use active time, including standing still and GPS wait but excluding explicit pauses. Observed GPS time skips interruptions and may be shorter. Elevation is a GPS estimate with a 3-metre noise threshold, not terrain-corrected height or slope. Other apps may calculate different totals.

CSV avoids raw routes, but still contains precise activity times and statistics. The ZIP includes precise routes and sample timestamps. Annotations are included; photos remain private and are excluded. Unreadable optional annotations fall back to route-only data without replacing their source files. Neither format is a complete restorable backup of the private recording journal. Uninstalling or clearing Runline still removes internal recordings/recovery copies; keep important exports outside the app.

## Preparation and lifecycle

`sharing/ActivityExporter.kt` prepares each export on a retained worker. GPX encoding, archive generation and destination copying run off the UI thread. Each operation uses a separate cache directory and immutable prepared file, so a later share does not overwrite a file already granted to a recipient. Cache is temporary storage, not a backup; Android can reclaim it.

The worker survives Activity recreation, and pending result delivery waits until the Activity is resumed. A document-picker request stores a small prepared-file reference, filename and format in saved state rather than embedding all activities. After process recreation, the cached source is opened before the destination so an evicted source does not truncate a destination. A missing cached file requires retrying export from intact saved history.

Android 10+ Downloads uses `IS_PENDING=1` until copying succeeds. Name allocation/insertion is serialized within the app. Unexpected provider collision names trigger bounded retry of new empty pending items; failed writes attempt to remove the new row before offering a picker. A process death can still leave a hidden pending item or a partial picker destination, so cleanup after death is not guaranteed. Export never removes the source recordings. Cloud providers retain their own availability, login and transfer requirements.

## Verification

Final integrated v0.6 builds passed all 96 JVM tests and lint with zero errors/37 warnings; pinned-icon and English/Spanish resource checks also passed. On API 34, the exported ZIP contained 17 summary rows, 17 GPX files and README.txt; point and segment comparisons matched the saved activities. Standalone CSV contained 17 rows without latitude/longitude columns. Direct Downloads saving and post-save sharing worked; the chooser listed Drive, but no Drive upload was performed. Save to… completed after rotating with the CSV document picker open. [Release history](RELEASE-HISTORY.md#version-06-verification) and [TESTING.md](TESTING.md) retain the exact verified scope and remaining checks. These results do not establish a real Drive, ChatGPT or other recipient’s end-to-end transfer, older-Android picker behavior or every export-failure path.

Historical v0.5 API 34 checks confirmed direct bilingual GPX Downloads exports, a 7,201-point/two-segment walk export, and a second export matching 13 saved points/three segments. Repeated exports preserved `(2).gpx` and `(3).gpx`. Those results do not validate every new v0.6 archive/share path.

GPX 0.13 exports retain original heights and add optional `runline:horizontalAccuracyMeters` / `runline:verticalAccuracyMeters` extensions in `https://github.com/adrianetti/runline/xmlns/1`. Values are Android-reported metres, not corrected elevation or a guarantee. Legacy vertical accuracy remains absent. Standard GPX readers can ignore these extensions.

Version 0.14 displays mapped elevation in recording/replay and supplies it to coaching, but GPX and archive exports retain the original GPS observations and GPS-derived totals. No corrected values silently replace measured heights. See [Terrain](TERRAIN.md) for source/coverage distinctions.
