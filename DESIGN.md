---
name: "Runline"
description: "An Android running recorder with monochrome controls, real satellite imagery and GPS replay."
colors:
  primary: "#171717"
  on-primary: "#FFFFFF"
  primary-container: "#E7E7E7"
  on-primary-container: "#171717"
  surface: "#FAFAFA"
  on-surface: "#171717"
  surface-variant: "#EEEEEE"
  on-surface-variant: "#5C5C5C"
  outline: "#777777"
  error: "#333333"
  primary-dark: "#F5F5F5"
  on-primary-dark: "#171717"
  primary-container-dark: "#333333"
  on-primary-container-dark: "#F5F5F5"
  surface-dark: "#141414"
  on-surface-dark: "#F5F5F5"
  surface-variant-dark: "#242424"
  on-surface-variant-dark: "#B8B8B8"
  outline-dark: "#888888"
  error-dark: "#E5E5E5"
typography:
  display:
    fontFamily: "sans-serif-medium"
    fontSize: "30sp"
    fontWeight: 500
    fontFeature: "tnum"
  display-detail:
    fontFamily: "sans-serif-medium"
    fontSize: "22sp"
    fontWeight: 500
  headline:
    fontFamily: "sans-serif-medium"
    fontSize: "24sp"
    fontWeight: 500
  history-value:
    fontFamily: "sans-serif-medium"
    fontSize: "26sp"
    fontWeight: 500
  live-metric:
    fontFamily: "sans-serif-medium"
    fontSize: "26sp"
    fontWeight: 500
    fontFeature: "tnum"
  metric-value:
    fontFamily: "sans-serif-medium"
    fontSize: "22sp"
    fontWeight: 500
    fontFeature: "tnum"
  title:
    fontFamily: "sans-serif-medium"
    fontSize: "24sp"
    fontWeight: 500
  body:
    fontFamily: "sans"
    fontSize: "16sp"
    fontWeight: 400
  body-small:
    fontFamily: "sans"
    fontSize: "14sp"
    fontWeight: 400
  label:
    fontFamily: "sans"
    fontSize: "13sp"
    fontWeight: 400
rounded:
  control: "16dp"
  replay-control: "12dp"
  island: "24dp"
spacing:
  inset-small: "4dp"
  gap-small: "8dp"
  gap: "12dp"
  inset: "16dp"
  row: "20dp"
  page: "24dp"
  section: "28dp"
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.on-primary}"
    rounded: "{rounded.control}"
    padding: "12dp 16dp"
    height: "64dp"
  button-outlined:
    textColor: "{colors.primary}"
    rounded: "{rounded.control}"
    padding: "12dp 16dp"
    height: "64dp"
  navigation-tabs:
    textColor: "{colors.on-surface-variant}"
    height: "48dp"
  live-distance:
    textColor: "{colors.on-surface}"
    typography: "{typography.live-metric}"
  active-run-island:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    rounded: "{rounded.island}"
    padding: "16dp 16dp 12dp"
  active-run-map:
    height: "available content height"
  active-run-action:
    height: "60dp"
  active-run-action-compact:
    height: "48dp"
  history-row:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    padding: "20dp 4dp"
  activity-selector:
    height: "64dp"
  satellite-map:
    height: "available content height"
  replay-map:
    height: "available content height"
  replay-island:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.island}"
    padding: "8dp 16dp 12dp"
  replay-control:
    textColor: "{colors.primary}"
    rounded: "{rounded.replay-control}"
    height: "48dp"
  elevation-profile:
    textColor: "{colors.primary}"
    height: "160dp"
  toolbar-export:
    textColor: "{colors.primary}"
    height: "48dp"
  detail-row:
    textColor: "{colors.on-surface}"
    padding: "12dp 0dp"
---

# Design System: Runline

## Overview

**Direction: Minimal running instrument.** The user requested monotone controls, very little text, one free sporty icon family, satellite maps and replay. Version 0.5 makes recording and replay maps fill the content area, with information in reachable islands. Elevation changes lead the detailed analysis. The idle Record screen is a simple activity selector and Start button. Application chrome stays neutral; imagery retains its real colors. Runline remains the working name.

This record follows `MainActivity.kt`, `ActiveRunView.kt`, `SatelliteMapView.kt`, `MarkerMotion.kt`, `GpsCourse.kt`, `RunReplayView.kt`, `ActivityExporter.kt`, `AppLanguage.kt`, the geometric `RouteView.kt` fallback and the pinned Tabler resources. Native values use dp/sp. Sidecar previews illustrate structure, not native rendering or invented route data. Version 0.5 passed bounded API 34 replay, location-loading/follow, Spanish/English, notification and export checks, including light/dark, 1.3× font and landscape review; see [verified scope and limits](README.md#version-05-verification). Historical v0.3/v0.4 API 34 reviews covered bounded light/dark, 1.3× font and landscape configurations; README.md retains the exact results and limitations.

## Colors

Use neutral grays for every application role. Primary is dark ink in light mode and near-white in dark mode; on-primary reverses with it. Surface/surface-variant separate controls and content without a hue accent. On-surface-variant carries labels and status. Errors use explicit words/actions, never color alone. Satellite imagery and supplied labels retain source colors. Android dialogs and external document providers retain their owners' styling.

`Base.Theme.Runline` supplies shared Material roles. Day/night resources and window-insets handling keep system-bar icons visible. Pair foregrounds with their corresponding surfaces. The sidecar's neutral ramps are documentation previews, not extra runtime palette tokens.

## Typography and language

Use Android system sans, medium for key measurements/headings, with tabular numerals for live values. Preserve font scaling. Normal-state copy is short, with persistent units and labels; — indicates unavailable data. Spanish is the personal-build default; English is selectable in the app menu. Translate controls, error/recovery messages, map explanations, notifications and export names. Locale-aware numbers/date labels must not change GPX's numeric/XML conventions or UTC sample timestamps.

The ready heading is 30sp. Active values are nominally 26sp and fit 16–26sp; labels are 12sp and the state heading 16sp. Replay distance and compact metric values are nominally 22sp and fit down where needed. Replay metric labels reserve two lines so Spanish labels align. Detail-sheet values use a larger 26sp treatment. Prefer on-demand calculation information to paragraphs in the island, but retain meaningful failure and recovery messages.

## Layout

Idle Record and history retain the app bar and Record/Activities tabs. Record shows Run/Walk/Hike in an exposed dropdown and one Start action, without a map. The choice is remembered and stored in each session. Do not infer a legacy recording's activity type from speed; untyped sessions remain Run.

Active, paused and recovered recording hide the app bar/tabs. The map fills the content area under floating 48dp Activities/options controls and a bottom island inset 16dp. Three columns show distance, active time and average pace; portrait adds climbed/descended elevation. Pause, or Resume/Finish, remains below scrollable information. The island is capped at 55% of portrait content height. Landscape places it lower left with width capped at 340dp or 45% of available width. Its measured bounds reserve map space for the marker and credits. Android status/navigation bars remain visible.

Saved replay uses the same full-content composition: floating Back/options/download controls, map, and a compact bottom island. Replayed distance and Details sit above speed/climbed/descended columns. Play/Pause, speed, seek, time and pace remain reachable below. Tap the statistics or Details for a scrollable bottom sheet. It pauses playback and exposes total distance/time/pace, prominent climb/descent and a 160dp elevation-change profile, then secondary raw altitude, speed totals and splits. Landscape moves the island lower left rather than shrinking the map to a fixed panel.

History remains a chronological flat list with activity type/date, distance and active-time/pace summary. The export icon stays above the replay map, with Export GPX and Share file also available in options. Discard/Delete retain confirmation; Finish is a direct save. There is no Strava toolbar or browser flow. A tablet-specific composition is not implemented.

## Motion and map behavior

Cover navigation's initial world map with a loading-current-location state until a usable fix exists. Include the current permission/location status. Loading must not imply that saving waits for map tiles. Recenter restores close follow or explains that no usable position is available.

North-up follow uses zoom 18. A white, dark-outlined Tabler navigation arrow turns with accepted GPS course; the initial marker is neutral until reliable movement establishes direction. It is not a phone compass. Stationary periods retain the last course, while gaps/segments reset the baseline. Panning/pinching disengages follow; route-fit is an explicit alternative.

Interpolate only safe consecutive live fixes and move the follow camera with the displayed marker. The short display delay does not predict future motion. Gaps, invalid data, segment changes and implausible jumps are not animated across. Pause/backgrounding stops animation. Original samples, recorded distance/time and exported GPX remain untouched.

Replay uses observed GPS intervals, defaults to 1×, supports 5×/10×/30×/60× and seeks, and updates up to 20 times per second. It pauses when backgrounded and preserves position/speed through recreation. Missing intervals are cuts, not fabricated travel. Route setup and profile construction are outside the playback-frame loop. This is an in-app replay, not a video export.

## Elevation, shapes and depth

Elevation information means climbed/descended change first. The details profile is relative to the first available GPS height and preserves missing-height and segment gaps. A 3-metre noise threshold reduces small GPS fluctuations; no corrected terrain elevations or grade percentages are provided. Raw altitude/range remain secondary and unknown values stay unavailable.

Use native pressed/focus feedback and dialogs. Islands use 24dp corners and 3dp native elevation; flat history/detail rows use thin rules. Start uses a 16dp radius and at least 64dp height. Active actions use 60dp, or 48dp in landscape. Replay controls use 12dp corners and 48dp height. Floating map/export controls and credits use 48dp targets. No blur, decorative gradients or decorative motion.

Route geometry stays distinct from icons: white line with dark outline, circular start/replay markers, movement-derived live arrow. Explicit pauses and interruptions break the route. The native failure fallback is a neutral geometric trace. Real tile imagery and display interpolation never alter recordings.

## Components

- **Activity picker and Start:** native dropdown, remembered Run/Walk/Hike choice, one labeled Start action after required permissions.
- **Recording island:** short state/GPS status, primary metrics, elevation change and state-appropriate actions. Information can scroll while controls stay reachable.
- **Replay island and details sheet:** compact playback controls and climb/descent; tap for totals, relative profile and splits. Raw altitude is secondary.
- **Export:** accessible download icon opens the file-only export. Android 10+ saves to Downloads; older devices and failures use a picker. Encoding/writes stay off the UI thread. Show actual filename and explicit Copy name. Names use localized activity, distance and local date/time, with no guessed street. Cancel/failure preserves the original session; no browser, account or publication status.
- **Satellite map:** pan/zoom, fit/follow, location-loading and clear missing-imagery states. Keep source/license/coverage/privacy credits visible around islands. Requests expose the visible area, not raw recordings; map failure does not stop capture.
- **Protection and overflow:** expose battery/notification status and Android settings without implying continuity is guaranteed. Icon-only actions have localized accessible names.
- **Ongoing notification:** distance, average pace, chronometer and GPS status with Pause or Resume/Finish. Paused time freezes; actions validate session/state. Android/user settings govern lock-screen visibility. Samsung Now bar and music/media-session behavior are not claimed.
- **Recovery:** recovered sessions offer Resume/Finish. Unreadable drafts offer preservation before reset; completed history is not cleared.

All app pictograms use the pinned MIT Tabler outline subset; originals/license are in THIRD_PARTY_NOTICES.md and bundled in About. Preserve 24-unit geometry, 2-unit rounded strokes, caps and joins. Android system dialogs and external apps are outside this icon policy.

## Do's and Don'ts

- Keep controls achromatic in both themes and imagery in real colors.
- Label primary actions; give icon-only actions accessible localized names.
- Keep start, pause, resume and finish reachable, including landscape and larger Spanish text.
- Keep climbs/descents prominent and unfamiliar calculation details on demand.
- Preserve uncertainty, missing-data gaps, original samples and recovery messages.
- Keep credits visible and loading/recenter feedback explicit.
- Do not infer activity types, place names, missing sensor data or terrain-corrected elevation.
- Do not animate through GPS gaps or represent visual smoothing as better GPS accuracy.
- Do not imply export uploads an activity, or that local mirrors are cloud backup.
- Do not claim a powered-off phone records, or historical emulator checks validate every device.
