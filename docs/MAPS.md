# Satellite maps

Runline 0.9 displays interactive satellite/aerial imagery with a GPS route and optional playback marker. Pinch to zoom, drag to pan, use the route icon to fit the whole run and the location icon to follow the marker. The surrounding controls remain monochrome; imagery retains its real colours.

The idle Record tab contains the Run/Walk/Hike picker and Start button, with no map. Recording and saved replay both fill the content area around their floating controls and bottom islands.

## Live navigation view

Active, paused and recovered sessions use the full app content area for the map. `ActiveRunView` floats a statistics/control island at the bottom, or lower left in landscape; its measured bounds are passed to the map as physical-pixel insets. Camera padding keeps the marker centered in the remaining visible map, with room for credits and controls. System status/navigation bars remain Android-owned.

Follow uses zoom 18 and a 46° tilt once accepted GPS movement establishes a heading. The camera eases along the shortest angle toward that course using wall time, independent of replay speed. A screen-anchored Tabler navigation arrow stays near the unobscured center and rotates relative to the camera; the initial marker stays neutral until movement is reliable. Pan/fit uses the same native overlay projected from the displayed coordinate each frame and on camera movement, avoiding per-frame GeoJSON symbol uploads. An off-viewport position is hidden, never clamped to the edge. Fit returns a flat north-up overview. Android Remove animations disables extra steering/tilt and live interpolation. `GpsCourse` uses an accuracy-aware distance threshold and rejects jumps, invalid fixes and stale/gapped intervals. Stationary jitter does not continually spin the arrow, and the last established heading survives a stop. A new segment or GPS gap resets the baseline. This is direction of travel, not a magnetic phone compass, and it does not modify stored samples.

A loading-current-location overlay covers the initial map until a usable navigation fix exists, with relevant permission/location status. This hides the initial world-scale camera while the service obtains a position. Map tile readiness does not govern GPS persistence. Panning or pinching disengages follow; the location control restores close following or explains that a usable position is still unavailable. The route control fits the track.

`MarkerMotion` interpolates safe consecutive live fixes and the following camera together. It allows only valid, sufficiently accurate coordinates in the same segment, with a positive interval of at most 15 seconds and a plausibility check on travel distance. Animation duration is bounded to 300–1,500 ms. This creates a short visual delay, not extrapolated location. Longitude and bearing take the shortest path; gaps, segment changes and unsafe jumps snap instead of gliding through invented data. Animation stops on pause/backgrounding/disposal. Stored coordinates, distance/time calculations and exports do not change.

## Replay view

Saved replay also fills the content area, with a compact bottom island and floating Back/options/export controls. Landscape moves the island lower left. Host insets reserve its occupied area and leave map credits visible. The replay route is configured once; frame updates move the heading marker, following camera and relevant statistics. The complete route remains visible in follow and overview. Replay already interpolates observed intervals, so it does not use the live-fix animation layer.

The island shows distance, speed, accumulated climb/descent, the current GPS-estimated terrain trend and playback controls. Tapping its statistics or Details opens a sheet with totals, splits and a relative elevation-change profile. Raw GPS altitude is secondary. The initial multiplier is the available speed closest to a 30-second observed replay, with manual selection retained through recreation. Playback pauses when backgrounded. Detailed calculation notes are available on demand, and sheet Close remains above scrolling content.

Both islands draw a subdued trace from actual GPS heights. `ObservedElevationTrace` uses the same segmented observed clock as `RunReplay`; poor or missing heights break the line without shortening the clock. The cursor interpolates only valid-height edges in the selected replay segment, or marks the latest supported live sample. Gaps, jumps, poor accuracy and isolated endpoints do not receive a fabricated dot. An invalid final live point hides the cursor instead of reusing an older one. Profile construction happens when the input changes, not on each replay frame.

`TerrainTrend` is shared with coaching. It fits height against distance over a trailing 35–55 m window with at least six fixes, ten seconds, horizontal accuracy within 20 m, sufficient net/recent movement and a bounded regression residual. Gaps, missing/nonfinite heights, implausible speed and stopped movement yield unknown. Entering uphill/downhill uses ±3%; exit hysteresis uses ±1.5%. The island shows a pinned Tabler slope/flat icon and approximate whole-percent grade only when supported; unknown is **Terrain —**, without a flat icon or invented zero. Live pause/recovery and fixes older than 15 seconds also show unknown. Replay precomputes causal estimates so seeking does not depend on prior playback. This is an uncorrected GPS-height estimate, not a terrain survey, surface classification or measured effort. Accumulated ascent/descent retains its separate 3-metre noise threshold.

## Imagery and coverage

No provider account or API key is required for this personal, noncommercial build:

- **Spain:** IGN / PNOA aerial imagery. The official service metadata states unrestricted public access and [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) use. High-resolution coverage is typically 25–50 cm aerial imagery, with differing capture dates. At local zooms, Runline enables this layer when the map centre is within the Spain outline. National borders are approximate, and a viewport crossing a border can include areas outside the detailed imagery. [Official service metadata](https://www.idee.es/csw-codsi-idee/srv/api/records/spaignwmts_pnoa-ma), [WMTS capabilities](https://www.ign.es/wmts/pnoa-ma?REQUEST=GetCapabilities&SERVICE=WMTS).
- **Worldwide:** EOxCloudless 2025 by EOX, containing modified Copernicus Sentinel data 2025. The service explicitly permits use in applications with attribution; the layer is [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/). This is approximately 10-metre imagery, so it is much less detailed at street-level zoom than PNOA. This license fits personal noncommercial use; reassess before commercial distribution. [EOX map service and conditions](https://maps.eox.at/), [layer capabilities](https://tiles.maps.eox.at/wmts/1.0.0/WMTSCapabilities.xml).
- **Map labels:** the raster road/place-label overlay is omitted in v0.8. Imagery and the locally drawn recorded route remain visible; attribution is retained in a compact strip with a 48dp information target. EOX imagery remains capped at native zoom 14 and PNOA at zoom 19; enlarging further does not create more detail.

A visible credit strip opens source links, licenses, coverage limitations and privacy information. The geometry used to decide whether the map centre is in Spain is a bundled [Natural Earth country outline](https://github.com/nvkelso/natural-earth-vector/blob/master/geojson/ne_10m_admin_0_countries.geojson), in the [public domain](https://www.naturalearthdata.com/about/terms-of-use/). It is not used to alter or infer the recorded GPS route.

## Privacy, network and recording

The app now needs internet access to load map tiles. A provider can see the requested tile areas and the phone's network address. Runline does not send the run file, timestamps, session identifier or metrics to the map providers. Route lines and replay markers are composed locally over the map.

`SatelliteMapView` is display-only. It does not start location updates, modify a run or change the foreground recording service. Loss of tile connectivity does not stop GPS recording, metrics or the local route display. A tile-failure message indicates missing imagery; a native map-load failure falls back to the geometric route view. No map provider is a backup of the recording.

MapLibre's normal ambient cache has a default limit of 50 MB. Runline does not request whole-region offline downloads or prefetch neighbouring zoom levels. Recently viewed imagery may be cached, but offline maps are not guaranteed. EOX states that its public service is best-effort and rate-limited. Map rendering is limited to 30 frames per second and paused/stopped with the host screen.

## Component contract

`app/src/main/java/com/runline/app/ui/SatelliteMapView.kt` owns map presentation:

```kotlin
val map = SatelliteMapView(context)
map.setRoute(run.points)       // preserves segment breaks
map.setMarker(livePoint)       // live display position; null hides the marker
map.setCenter(latitude, longitude) // optional known location, no GPS request
map.setNavigationMode(true)  // live follow; use setReplayMode() for saved playback
// Replay frames use setReplayMarker(replayPoint, bearingDegrees, smoothHeading).
map.setLiveMotionEnabled(isRecording) // display smoothing only; disabled when paused
map.setLocationWaitingMessage(gpsStatus) // current permission/location guidance before first fix
map.setOverlayInsets(leftPx, topPx, rightPx, bottomPx) // occupied host areas, physical pixels
map.setFollowing(true)       // center within the available viewport
map.fitRoute()               // deliberately leaves follow mode

map.onHostResume()
map.onHostPause()
map.onLowMemory()
map.dispose()                 // before removing/replacing the screen
```

Pass the original coordinate samples and a separately computed replay position. The SDK is never the authority for recorded distance or time. Each map instance is disposable; do not reuse it after `dispose()`.

The native SDK remains pinned to **MapLibre Android 11.13.5** (BSD-2-Clause). Its published dependency metadata lists Kotlin 2.0.20; the current patched build uses Kotlin **2.4.21**, AGP **9.5.0** and Gradle **9.8.2**, with the completed compatibility checks recorded in README.md and docs/TESTING.md. [MapLibre Android quickstart/lifecycle](https://maplibre.org/maplibre-native/android/examples/getting-started/), [pinned dependency metadata](https://repo.maven.apache.org/maven2/org/maplibre/gl/android-sdk/11.13.5/android-sdk-11.13.5.pom), [Android Kotlin compatibility](https://developer.android.com/build/kotlin-support).

## Verification scope

Version 0.9 terrain, cursor and projected-marker behavior is implemented in source; integrated build/runtime verification is pending. Earlier results below do not establish the new behavior.

The integrated v0.8 build passed its 158-test JVM suite, including the new follow-camera and elevation-trace cases. On API 34, the two-hour fixture selected 240× and native replay showed label-free imagery, heading-follow and the elevation island; Spanish details and export choices were also inspected. Independent review of the inspected dark-mode, 1.3× text and landscape captures confirmed the fixes without additional control or layout issues. These bounded emulator checks do not establish physical-phone GPS accuracy or frame-rate performance. See [v0.8 verification](../README.md#version-08-verification); historical evidence below retains its original scope.

Version 0.5 API 34 checks captured initial location loading, centered eastward heading, pan/recenter behavior and intermediate frames of smooth display movement. A 7,201-point two-hour fixture exercised fullscreen replay, seeking and elevation details; English/Spanish switching and rotation retained playback position/speed. Bounded light/dark, 1.3× font and landscape review found no material clipping in inspected views. These are emulator observations, not field GPS or every-device performance guarantees. See [release evidence](../README.md#version-05-verification) and the [full checklist](TESTING.md).

Historical version 0.4 API 34 checks confirmed synthetic east-to-north heading, close centering, pan disengaging follow and the location control restoring it. Light/dark, 1.3× font scale and landscape captures showed no material clipping in the reviewed configurations. The nine GPS-course tests passed as part of the 53-test suite. This does not establish physical-phone heading accuracy or map performance; detailed results are in README.md and docs/TESTING.md.

Historical v0.3 checks: direct HTTPS requests returned valid imagery for Madrid from PNOA at zoom 18 and EOX at zoom 14, plus the EOX label overlay at zoom 14. Higher label zoom requests returned 404, so they are intentionally capped. The API 34 emulator subsequently rendered actual IGN imagery beneath the synthetic route and moving marker. Route fitting was corrected for display density, and the replay was checked in light/dark modes, at 1.3× text size and in landscape. With Wi-Fi and mobile data disabled, GPS recording continued while backgrounded and screen-locked; a force-stopped session recovered and finished successfully. These checks do not establish map coverage or performance on the user's physical phone. Build and update evidence is recorded in the main README.

## Version 0.12 compact profile

The small grade instrument now renders a smooth 32-bin profile of the preceding 45 observed seconds, without future samples or connections across missing heights/segments. Supported heights remain visible when the separate numerical-grade gate is uncertain. Its latest dot and current-grade semantic color remain in the pace-first island. Terrain regression declines to announce a direction when the newest supported short window reverses or levels off, reducing stale direction claims around crests and valleys. GPS altitude can still be inaccurate.
