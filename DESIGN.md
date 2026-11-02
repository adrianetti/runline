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
  voice-coach-control:
    textColor: "{colors.primary}"
    rounded: "{rounded.control}"
    height: "64dp"
  history-filter:
    textColor: "{colors.primary}"
    height: "48dp"
  history-chart:
    textColor: "{colors.on-surface}"
    backgroundColor: "{colors.surface}"
  history-transfer:
    textColor: "{colors.primary}"
    rounded: "{rounded.control}"
    height: "64dp"
  detail-row:
    textColor: "{colors.on-surface}"
    padding: "12dp 0dp"
---

# Design System: Runline

## Overview

**Direction: Minimal running instrument.** The user requested monotone controls, very little text, one free sporty icon family, satellite maps and replay. Version 0.8 refines full-content recording/replay maps, elevation islands, navigation and optional AI/training setup while retaining charts, journals and explicit file sharing. Source behavior is described here; [v0.8 verification](README.md#version-08-verification) records the completed bounded API 34 checks, independent finish review and remaining device/release work. Elevation changes lead the detailed analysis. The idle Record screen has a simple activity selector, Start and a clearly optional Voice coach control. Application chrome stays neutral; imagery retains its real colors. Runline remains the working name.

This record follows `MainActivity.kt`, `ActiveRunView.kt`, `SatelliteMapView.kt`, `MarkerMotion.kt`, `GpsCourse.kt`, `RunReplayView.kt`, `ActivityExporter.kt`, `VoiceCoachSheet.kt`, `HistoryInsightsView.kt`, `AppLanguage.kt`, the geometric `RouteView.kt` fallback and the pinned Tabler resources. Native values use dp/sp. Sidecar previews illustrate structure, not native rendering or invented route data. Final integrated v0.6 builds passed 96 JVM tests and lint with zero errors/37 warnings; 24 icons and 270 English/Spanish resources passed source/parity and placeholder checks. A bounded API 34 review of 11 screenshots, including Spanish, larger text and landscape, found no material UI issues in the inspected views. [Verified scope and limits](README.md#version-06-verification) records runtime and release evidence. Historical v0.5 passed bounded API 34 replay, location-loading/follow, localization, notification and export checks, including light/dark, 1.3× font and landscape review. Historical v0.3/v0.4 API 34 reviews covered bounded light/dark, 1.3× font and landscape configurations; README.md retains the exact results and limitations.

## Colors

Use neutral grays for every application role. Primary is dark ink in light mode and near-white in dark mode; on-primary reverses with it. Surface/surface-variant separate controls and content without a hue accent. On-surface-variant carries labels and status. Errors use explicit words/actions, never color alone. Satellite imagery and supplied labels retain source colors. Android dialogs and external document providers retain their owners' styling.

`Base.Theme.Runline` supplies shared Material roles. Day/night resources and window-insets handling keep system-bar icons visible. Pair foregrounds with their corresponding surfaces. The sidecar's neutral ramps are documentation previews, not extra runtime palette tokens.

## Typography and language

Use Android system sans, medium for key measurements/headings, with tabular numerals for live values. Preserve font scaling. Normal-state copy is short, with persistent units and labels; — indicates unavailable data. Follow phone language by default, with English fallback and Spanish support; explicit system/English/Spanish choices remain available. The v0.5 forced-Spanish setting is cleared once on upgrade. Translate controls, error/recovery messages, map explanations, notifications and export names. Locale-aware numbers/date labels must not change GPX's numeric/XML conventions or UTC sample timestamps.

The ready heading is 30sp. Active values are nominally 26sp and fit 16–26sp; labels are 12sp and the state heading 16sp. Replay distance and compact metric values are nominally 22sp and fit down where needed. Replay metric labels reserve two lines so Spanish labels align. Detail-sheet values use a larger 26sp treatment. Prefer on-demand calculation information to paragraphs in the island, but retain meaningful failure and recovery messages.

## Layout

Idle Record and history retain the app bar and Record/Activities tabs. One Settings icon opens AI, Language, Recording protection and About; Training plan lives on idle Record, with no duplicate global overflow entry. Record shows Run/Walk/Hike in an exposed dropdown and one Start action, without a map. Voice coach has an off-by-default speaker control before starting and an entry in the active **…** menu. The choice is remembered and stored in each session. Do not infer a legacy recording's activity type from speed; untyped sessions remain Run.

Active, paused and recovered recording hide the app bar/tabs. The map fills the content area under floating 48dp Activities/options controls and a bottom island inset 16dp. Three columns show distance, active time and average pace; portrait adds climbed/descended elevation. Pause, or Resume/Finish, remains below scrollable information. The island is capped at 55% of portrait content height. Landscape places it lower left with width capped at 340dp or 45% of available width. Its measured bounds reserve map space for the marker and credits. Android status/navigation bars remain visible.

Saved replay uses the same full-content composition: floating Back/options/download controls, map, and a compact bottom island. Replayed distance and Details sit above speed/climbed/descended columns. Play/Pause, speed, seek, time and pace remain reachable below. Tap the statistics or Details for a scrollable bottom sheet. It pauses playback and exposes total distance/time/pace, prominent climb/descent and a 160dp elevation-change profile, then secondary raw altitude, speed totals and splits. Landscape moves the island lower left rather than shrinking the map to a fixed panel.

History places a four-local-calendar-week summary above the chronological flat list. All/Run/Walk/Hike controls filter only charts; long translated filter labels scroll horizontally. Weekly distance bars have explicit values and local Monday labels. A single activity type enables the latest-eight pace trend when at least two observations exist; no mixed-type or fabricated zero-pace trend. Chart content descriptions expose measurements to accessibility. Period pace is distance-weighted; current-week incompleteness and missing GPS remain explicit.

Share and Export sit under the Activities heading between charts and saved rows. Their accessible names retain “all”; equal 64dp rows use short one-line labels and stack on narrow/larger-font screens so Spanish copy stays aligned. They offer CSV summary without raw coordinates or ZIP with every saved GPX plus CSV/README, independent of the chart filter. The replay download control offers Downloads, Save to… and Share file; the native chooser/document picker owns destination selection, including compatible Drive providers. A saved result displays the real filename and a Share action. Discard/Delete retain confirmation; Finish first saves the route, then offers an optional journal editor. Saved runs can reopen the same editor for title, notes, effort, activity/routine type and private local photos. Strava remains absent. A tablet-specific composition is not implemented.

Voice settings use a scrollable native sheet with an explicit guidance switch, goal dropdown, only relevant goal fields, kilometre switch, independently switchable pace/encouragement/terrain/lap feedback, voice preview/settings and Save. Preserve unsaved fields through recreation. Show field-level validation, including incompatible distance/time combinations, without blocking the user from turning guidance off. Goals use active time and exclude explicit pauses. Do not clutter the recording island with coaching configuration. AI connection/model/automation controls live under Settings → AI, with a separate module switch, masked key entry, provider/model dropdowns and a custom-model escape hatch. Details about data/costs are on demand. Review and personalization actions require enabled, configured AI; the local plan remains available while AI is off. Training plans and post-run AI reviews each use a focused native sheet with explicit loading/error states; no API secret or technical response blob belongs in normal product flow.

Training offers 5K, 10K, Half marathon (21.1 km), Personal progress and Custom distance. Exact day uses Android's date picker; Target month uses a month picker and retains that precision. Personal progress uses an eight-week horizon. Show recorded Run averages immediately with their 7/28-day window; distinguish a sparse-history starting suggestion from measured history. Keep the weekly-distance override collapsed behind Adjust. Local schedules show concise date/distance/kind/status rows; show AI explanations and notes only on demand.

Sheets use a fixed Close header above nested scrolling content, with primary Save actions anchored where present. Disable drag/outside-tap dismissal while preserving Android Back. Retain form drafts across recreation, except API-key input.

## Motion and map behavior

Cover navigation's initial world map with a loading-current-location state until a usable fix exists. Include the current permission/location status. Loading must not imply that saving waits for map tiles. Recenter restores close follow or explains that no usable position is available.

Follow uses zoom 18, a 46° tilt when heading is known and a screen-anchored white Tabler arrow near the unobscured center. The camera turns toward accepted GPS course using shortest-angle wall-time easing; the marker rotates relative to the camera. The initial marker is neutral until reliable movement establishes direction. Route-fit returns to a flat north-up overview, and system Remove animations disables extra steering/tilt and live interpolation. It is not a phone compass. Stationary periods retain the last course, while gaps/segments reset the baseline. Panning/pinching disengages follow; route-fit is an explicit alternative.

Interpolate only safe consecutive live fixes and move the follow camera with the displayed marker. The short display delay does not predict future motion. Gaps, invalid data, segment changes and implausible jumps are not animated across. Pause/backgrounding stops animation. Original samples, recorded distance/time and exported GPX remain untouched.

Replay uses observed GPS intervals, selects the nearest-to-30-second speed from 1×/5×/10×/30×/60×/120×/240×/480× and seeks, and updates approximately 30 times per second. It pauses when backgrounded and preserves position/speed through recreation. Missing intervals are cuts, not fabricated travel. Route setup and profile construction are outside the playback-frame loop. This is an in-app replay, not a video export.

## Elevation, shapes and depth

Both islands use a quiet background trace from actual GPS elevation samples; insufficient data produces no invented shape, and missing height, poor accuracy, segments and gaps split the trace. Elevation information means climbed/descended change first. The details profile is relative to the first available GPS height and preserves missing-height and segment gaps. A 3-metre noise threshold reduces small GPS fluctuations; no corrected terrain elevations or grade percentages are provided. Raw altitude/range remain secondary and unknown values stay unavailable.

Use native pressed/focus feedback and dialogs. Islands use 24dp corners and 3dp native elevation; flat history/detail rows use thin rules. Start uses a 16dp radius and at least 64dp height. Active actions use 60dp, or 48dp in landscape. Replay controls use 12dp corners and 48dp height. Floating map/export controls and credits use 48dp targets. No blur, decorative gradients or decorative motion.

Raster road/place labels are omitted to keep the imagery clear. Compact attribution and its 48dp information target remain visible. Route geometry stays distinct from icons: the full white route with dark outline, circular start markers and movement-derived live/replay arrows remain available in follow and overview. Replay follows at zoom 18 and resets direction across observed gaps; stationary/isolated fixes do not invent direction. Explicit pauses and interruptions break the route. The native failure fallback is a neutral geometric trace. Real tile imagery and display interpolation never alter recordings.

## Components

- **Activity picker and Start:** native dropdown, remembered Run/Walk/Hike choice, one labeled Start action after required permissions.
- **Recording island:** short state/GPS status, primary metrics, elevation change and state-appropriate actions. Information can scroll while controls stay reachable.
- **Replay island and details sheet:** close-follow arrow/camera, speed selected near 30 seconds, compact controls and climb/descent. Tap for journal details/photos, totals, relative profile, splits and estimated matched laps. Edit remains findable; AI review appears only when enabled and configured. Raw altitude is secondary.
- **File transfer:** accessible download icon exposes Downloads, Save to… and Share file. Native pickers/choosers handle destinations; show the saved name and explicit Share action. Names remain readable and extension-safe. CSV/ZIP history actions have concise format labels. Preparation/copying stays off the UI thread; cancelled/failed transfer preserves originals. Do not claim the recipient uploaded or imported a file.
- **History insights:** four labelled weekly bars, totals and type-specific pace trend with a clear empty state. Period/type filters never silently narrow whole-history export.
- **Voice coach:** off-by-default speaker entry, scrollable goal/feedback sheet, varied local cues, preview and actionable unavailable-voice states. Installed offline English/Spanish TTS; brief optional prompts independent of recording. Temporary audio focus may lower another player; do not directly set global volume or promise Spotify/headphone behavior.
- **Satellite map:** pan/zoom, fit/follow, location-loading and clear missing-imagery states. Keep source/license/coverage/privacy credits visible around islands. Requests expose the visible area, not raw recordings; map failure does not stop capture.
- **Protection and overflow:** expose battery/notification status and Android settings without implying continuity is guaranteed. Icon-only actions have localized accessible names.
- **Ongoing notification:** distance, average pace, chronometer and GPS status with Pause or Resume/Finish. Paused time freezes; actions validate session/state. Android/user settings govern lock-screen visibility. Samsung Now bar and music-player impersonation are not claimed; optional speech uses temporary audio focus separately.
- **Recovery:** recovered sessions offer Resume/Finish. Unreadable drafts offer preservation before reset; completed history is not cleared.

Activity editors preserve draft fields and photo ownership through recreation, and label effort as optional self-report. Keep paired actions equal-height; stack rather than clipping longer localized labels. API connections, live advice, automatic reviews and automatic adaptation are explicit opt-ins. Training-session state is visible and manually correctable; changed plans explain their reasoning without presenting the goal date as a guarantee. AI summaries exclude coordinates/photos, and optional note inclusion is a separate setting.

All app pictograms use the pinned MIT Tabler outline subset; originals/license are in THIRD_PARTY_NOTICES.md and bundled in About. Preserve 24-unit geometry, 2-unit rounded strokes, caps and joins. Android system dialogs and external apps are outside this icon policy.

## Do's and Don'ts

- Keep controls achromatic in both themes and imagery in real colors.
- Label primary actions; give icon-only actions accessible localized names.
- Keep start, pause, resume and finish reachable, including landscape and larger Spanish text.
- Keep climbs/descents prominent and unfamiliar calculation details on demand.
- Make voice guidance optional, keep its settings out of live metrics and explain timed goals using active time.
- Distinguish chart filters from all-history export, and name CSV versus route-bearing ZIP clearly.
- Preserve uncertainty, missing-data gaps, original samples and recovery messages.
- Keep credits visible and loading/recenter feedback explicit.
- Do not infer activity types, place names, missing sensor data or terrain-corrected elevation.
- Do not animate through GPS gaps or represent visual smoothing as better GPS accuracy.
- Do not imply export uploads an activity, or that local mirrors are cloud backup.
- Do not claim a powered-off phone records, or historical emulator checks validate every device.
