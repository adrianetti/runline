# Satellite maps

Runline 0.3 displays interactive satellite/aerial imagery with a GPS route and optional playback marker. Pinch to zoom, drag to pan, use the route icon to fit the whole run and the location icon to follow the marker. The surrounding controls remain monochrome; imagery retains its real colours.

## Imagery and coverage

No provider account or API key is required for this personal, noncommercial build:

- **Spain:** IGN / PNOA aerial imagery. The official service metadata states unrestricted public access and [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/) use. High-resolution coverage is typically 25–50 cm aerial imagery, with differing capture dates. At local zooms, Runline enables this layer when the map centre is within the Spain outline. National borders are approximate, and a viewport crossing a border can include areas outside the detailed imagery. [Official service metadata](https://www.idee.es/csw-codsi-idee/srv/api/records/spaignwmts_pnoa-ma), [WMTS capabilities](https://www.ign.es/wmts/pnoa-ma?REQUEST=GetCapabilities&SERVICE=WMTS).
- **Worldwide:** EOxCloudless 2025 by EOX, containing modified Copernicus Sentinel data 2025. The service explicitly permits use in applications with attribution; the layer is [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/). This is approximately 10-metre imagery, so it is much less detailed at street-level zoom than PNOA. This license fits personal noncommercial use; reassess before commercial distribution. [EOX map service and conditions](https://maps.eox.at/), [layer capabilities](https://tiles.maps.eox.at/wmts/1.0.0/WMTSCapabilities.xml).
- **Roads and place labels:** EOX bright overlay, based on OpenStreetMap data; rendering © EOX and MapServer. The overlay and global imagery are capped at native zoom 14 and enlarged beyond that. PNOA is capped at zoom 19. These caps avoid repeated requests for nonexistent detail.

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
map.setMarker(replayPoint)     // null hides the marker in an unobserved interval
map.setCenter(latitude, longitude) // optional known location, no GPS request
map.setFollowing(true)
map.fitRoute()

map.onHostResume()
map.onHostPause()
map.onLowMemory()
map.dispose()                 // before removing/replacing the screen
```

Pass the original coordinate samples and a separately computed replay position. The SDK is never the authority for recorded distance or time. Each map instance is disposable; do not reuse it after `dispose()`.

The native SDK is pinned to **MapLibre Android 11.13.5** (BSD-2-Clause). Its published dependency metadata requires Kotlin 2.0.20, which is why the build uses Kotlin 2.0.20, AGP 8.5.2 and Gradle 8.7. [MapLibre Android quickstart/lifecycle](https://maplibre.org/maplibre-native/android/examples/getting-started/), [pinned dependency metadata](https://repo.maven.apache.org/maven2/org/maplibre/gl/android-sdk/11.13.5/android-sdk-11.13.5.pom), [Android Kotlin compatibility](https://developer.android.com/build/kotlin-support), [AGP 8.5 compatibility](https://developer.android.com/build/releases/past-releases/agp-8-5-0-release-notes).

## Verification scope

Direct HTTPS requests returned valid imagery for Madrid from PNOA at zoom 18 and EOX at zoom 14, plus the EOX label overlay at zoom 14. Higher label zoom requests returned 404, so they are intentionally capped. The API 34 emulator subsequently rendered actual IGN imagery beneath the synthetic route and moving marker. Route fitting was corrected for display density, and the replay was checked in light/dark modes, at 1.3× text size and in landscape. With Wi-Fi and mobile data disabled, GPS recording continued while backgrounded and screen-locked; a force-stopped session recovered and finished successfully. These checks do not establish map coverage or performance on the user's physical phone. Build and update evidence is recorded in the main README.
