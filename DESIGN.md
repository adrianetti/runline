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
  grade-uphill: "#A76A00"
  grade-steep: "#C13B2A"
  grade-downhill: "#247A51"
  grade-uphill-dark: "#E9B94F"
  grade-steep-dark: "#F27868"
  grade-downhill-dark: "#65C591"
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
    fontSize: "32sp"
    fontWeight: 500
    fontFeature: "tnum"
  island-pace:
    fontFamily: "sans-serif-medium"
    fontSize: "56sp portrait / 40sp landscape"
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
    padding: "16dp 20dp 12dp"
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
    padding: "16dp 20dp 12dp"
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

**Direction: Minimal running instrument.** The user requested monotone controls, very little text, one free sporty icon family, satellite maps and replay. Version 0.9 adds a compact terrain trend and trace cursor, smoother free-camera marker, optional runner profile and native effort slider while preserving the existing composition. Version 0.10 simplifies goals and adds clear AI preparation states; versioned verification is recorded in [release history](docs/RELEASE-HISTORY.md) and docs/TESTING.md. Elevation changes lead the detailed analysis. The idle Record screen has a simple activity selector, Start and a clearly optional Voice coach control. Application chrome stays neutral; imagery retains its real colors. Runline remains the working name.

This record follows `MainActivity.kt`, `ActiveRunView.kt`, `SatelliteMapView.kt`, `MarkerMotion.kt`, `GpsCourse.kt`, `RunReplayView.kt`, `ActivityExporter.kt`, `VoiceCoachSheet.kt`, `HistoryInsightsView.kt`, `AppLanguage.kt`, the geometric `RouteView.kt` fallback and the pinned Tabler resources. Native values use dp/sp. Sidecar previews illustrate structure, not native rendering or invented route data. Final integrated v0.6 builds passed 96 JVM tests and lint with zero errors/37 warnings; 24 icons and 270 English/Spanish resources passed source/parity and placeholder checks. A bounded API 34 review of 11 screenshots, including Spanish, larger text and landscape, found no material UI issues in the inspected views. [Verified scope and limits](docs/RELEASE-HISTORY.md#version-06-verification) records runtime and release evidence. Historical v0.5 passed bounded API 34 replay, location-loading/follow, localization, notification and export checks, including light/dark, 1.3× font and landscape review. Historical v0.3/v0.4 API 34 reviews covered bounded light/dark, 1.3× font and landscape configurations; [release history](docs/RELEASE-HISTORY.md) retains the exact results and limitations.

## Colors

Use neutral grays for application chrome. The user-requested grade instrument is the sole semantic color exception: amber through red uphill, green downhill and gray near flat, with matching light/dark tokens. The line angle and signed percentage convey the same information without relying on color. Primary is dark ink in light mode and near-white in dark mode; on-primary reverses with it. Surface/surface-variant separate controls and content without a hue accent. On-surface-variant carries labels and status. Errors use explicit words/actions, never color alone. Satellite imagery and supplied labels retain source colors. Android dialogs and external document providers retain their owners' styling.

`Base.Theme.Runline` supplies shared Material roles. Day/night resources and window-insets handling keep system-bar icons visible. Pair foregrounds with their corresponding surfaces. The sidecar's neutral ramps are documentation previews, not extra runtime palette tokens.

## Typography and language

Use Android system sans, medium for key measurements/headings, with tabular numerals for live values. Preserve font scaling. Normal-state copy is short, with persistent units and labels; — indicates unavailable data. Follow phone language by default, with English fallback and Spanish support; explicit system/English/Spanish choices remain available. The v0.5 forced-Spanish setting is cleared once on upgrade. Translate controls, error/recovery messages, map explanations, notifications and export names. Locale-aware numbers/date labels must not change GPX's numeric/XML conventions or UTC sample timestamps.

The ready heading is 30sp. Both islands share a dominant 56sp pace value (40sp in landscape), a 32sp distance value (24sp in landscape), a smaller 20sp timer and 14sp climb/descent totals. Width fitting and sp-based measured heights preserve larger text. Persistent min/km and km labels distinguish pace from speed. The current live pace uses the last ten seconds of continuous usable GPS, requiring five observed seconds and multiple pairs; stale fixes, stops and gaps clear it. Average timer pace remains a small portrait supporting value. Replay uses its existing recent observed pace. Details retain speed and average pace. Keep meaningful failure/recovery messages visible.

## Layout

Idle Record and history retain the app bar and Record/Activities tabs. One Settings icon opens Runner profile, AI, Language, Recording protection and About; Smart goal lives on idle Run, with no duplicate global overflow entry. Record shows Run/Walk/Hike in an exposed dropdown and one Start action, without a map. Voice coach has an off-by-default speaker control before starting and an entry in the active **…** menu. The choice is remembered and stored in each session. Do not infer a legacy recording's activity type from speed; untyped sessions remain Run.

Active, paused and recovered recording hide the app bar/tabs. The map fills the content area under floating 48dp Activities/options controls and a bottom island inset 16dp. Pace leads beside the slope instrument; distance follows with smaller time and elevation totals. Portrait retains state/GPS status and average pace. Landscape uses a condensed pace/slope and distance/time composition, omits secondary totals and routine status text, and preserves non-routine GPS/storage/recovery messages. Pause, or Resume/Finish, remains below scrollable information. The island is capped at 55% of portrait content height. Landscape places it lower left, capped at 340dp or 45% width; controls remain fixed and at least 48dp. Measured bounds reserve marker and attribution space.

Saved replay uses the same pace-first readout. A filled, accessible Play/Pause icon, playback multiplier and separate Details icon form the control row; seeking and total duration share the next row. Current time is a supporting metric. Statistics and Details both open the existing paused details sheet, with average pace, speeds, totals, profile and splits. Landscape keeps pace, grade and distance visible before secondary data.

History places a four-local-calendar-week summary above the chronological flat list. All/Run/Walk/Hike controls filter only charts; long translated filter labels scroll horizontally. Weekly distance bars have explicit values and local Monday labels. A single activity type enables the latest-eight pace trend when at least two observations exist; no mixed-type or fabricated zero-pace trend. Chart content descriptions expose measurements to accessibility. Period pace is distance-weighted; current-week incompleteness and missing GPS remain explicit.

Share and Export sit under the Activities heading between charts and saved rows. Their accessible names retain “all”; equal 64dp rows use short one-line labels and stack on narrow/larger-font screens so Spanish copy stays aligned. They offer CSV summary without raw coordinates or ZIP with every saved GPX plus CSV/README, independent of the chart filter. The replay download control offers Downloads, Save to… and Share file; the native chooser/document picker owns destination selection, including compatible Drive providers. A saved result displays the real filename and a Share action. Discard/Delete retain confirmation; Finish first saves the route, then offers an optional journal editor. Saved runs can reopen the same editor for title, notes, effort, activity/routine type and private local photos. Strava remains absent. A tablet-specific composition is not implemented.

Voice settings use a scrollable native sheet with an explicit guidance switch, goal dropdown, only relevant goal fields, kilometre switch, independently switchable pace/encouragement/terrain/lap feedback, voice preview/settings and Save. Preserve unsaved fields through recreation. Show field-level validation, including incompatible distance/time combinations, without blocking the user from turning guidance off. Goals use active time and exclude explicit pauses. Do not clutter the recording island with coaching configuration. AI connection/model/automation controls live under Settings → AI, with a separate module switch, masked key entry, provider/model dropdowns and a custom-model escape hatch. Details about data/costs are on demand. Reviews and Smart preparation require enabled, configured AI; manual goals remain available while AI is off. Smart suggestions and post-run AI reviews use focused native sheets with explicit loading/error states; no API secret or technical response blob belongs in normal product flow.

The calendar planner is removed. Settings → AI has one optional Overall goal field, with an initially migrated legacy aspiration and a clear blank state. Run preparation offers Smart goal: explicit Analyze, a native indeterminate spinner with a short status, then target/range/optional pace or time and a short reason. Use goal is fixed below the result; Adjust opens a draft of the ordinary coach editor. Rest has no executable target. Do not populate fake progress or trigger generation on opening/rotation. Manual goals include Distance, Pace, Time and Distance within time, with optional pace ranges kept relevant/collapsed. Record shows a concise applied goal summary. Use neither mid-run questions nor a second planning menu.

Sheets use a fixed Close header above nested scrolling content, with primary Save actions anchored where present. Disable drag/outside-tap dismissal while preserving Android Back. Retain form drafts across recreation, except API-key input.

Effort uses a native 0–10 slider with a persistent value/intensity label; zero displays Unrated and stores no effort. Keep Save fixed below the activity editor. Runner profile is optional: experience dropdown, preferences and health-note fields, separate Include profile in AI consent, Clear profile and fixed Save. Its text survives rotation only in memory, never in saved-state Bundles. All AI features groups live/review switches, without changing profile or activity-note privacy choices. Do not infer measured fitness from this self-report.

## Motion and map behavior

Cover navigation's initial world map with a loading-current-location state until a usable fix exists. Include the current permission/location status. Loading must not imply that saving waits for map tiles. Recenter restores close follow or explains that no usable position is available.

Follow uses zoom 18, a 46° tilt when heading is known and a screen-anchored white Tabler arrow near the unobscured center. The camera turns toward accepted GPS course using shortest-angle wall-time easing; the marker rotates relative to the camera. The initial marker is neutral until reliable movement establishes direction. Route-fit returns to a flat north-up overview, and system Remove animations disables extra steering/tilt and live interpolation. It is not a phone compass. Stationary periods retain the last course, while gaps/segments reset the baseline. Panning/pinching disengages follow; route-fit is an explicit alternative.

Free-camera playback/live display uses the same native arrow projected from the displayed coordinate every frame and on camera movement, with bearing relative to the current camera. Hide it outside the viewport; never pin a distant marker to an edge. Position updates do not wait for an asynchronous GeoJSON symbol source. Host pause/disposal stops frame callbacks.

Interpolate only safe consecutive live fixes and move the follow camera with the displayed marker. The short display delay does not predict future motion. Gaps, invalid data, segment changes and implausible jumps are not animated across. Pause/backgrounding stops animation. Original samples, recorded distance/time and exported GPX remain untouched.

Replay uses observed GPS intervals, selects the nearest-to-30-second speed from 1×/5×/10×/30×/60×/120×/240×/480× and seeks, and updates approximately 30 times per second. It pauses when backgrounded and preserves position/speed through recreation. Missing intervals are cuts, not fabricated travel. Route setup and profile construction are outside the playback-frame loop. This is an in-app replay, not a video export.

## Elevation, shapes and depth

Both islands use a quiet background trace from actual GPS heights, clipped to the visible statistics area so it cannot run underneath controls. Missing heights, poor fixes, segments and gaps split the trace; its dot follows supported observed position only. A rounded miniature profile shows the last 45 observed seconds at the previous slope instrument’s footprint. Midpoint quadratics keep the line smooth; its right-hand dot marks the latest supported height. The number remains an independently quality-gated whole-percent grade. Profile color follows valid current grade: uphill amber/red, downhill green, flat/unknown neutral. Missing heights stay gaps; unknown numerical grade uses a dash without hiding supported height history. Pause, seek and Remove animations settle immediately.

Pace and distance use native 280ms rolling-digit transitions with fixed tabular numerals. Rapid updates keep only the latest pending reading; pause, seek, segment changes, detach and hidden views settle immediately. The slope smoothly approaches new observations without altering measurements. System Remove animations disables rolling and slope movement. Accessible labels expose current values with units without repeated live announcements. No web runtime, extra animation dependency or artificial data oscillation is used.

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

Activity editors preserve draft fields and photo ownership through recreation, and label effort as optional self-report. Keep paired actions equal-height; stack rather than clipping longer localized labels. API connections, live advice, automatic reviews and Smart preparation are explicit opt-ins. Accepted targets remain editable; an aspiration or suggestion never certifies race readiness. AI summaries exclude coordinates/photos, and optional note inclusion is a separate setting.

All app pictograms use the pinned MIT Tabler outline subset; originals/license are in THIRD_PARTY_NOTICES.md and bundled in About. Preserve 24-unit geometry, 2-unit rounded strokes, caps and joins. Android system dialogs and external apps are outside this icon policy.

## Do's and Don'ts

- Keep controls achromatic in both themes; grade alone uses semantic colors with a signed number/angle. Imagery retains real colors.
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

## Version 0.12 secondary surfaces

Post-run reviews use a short headline, recorded distance, two icon-led sections and a tonal Next session panel. Keep total generated copy at most 100 words, with tighter field limits. Legacy text remains readable. Diagnostics is a secondary Settings sheet with Coach log/AI usage tabs, expandable event details, replay shortcuts and explicit share controls. Only the Coach tab exposes the recording switch. Idle tab swipes require clear horizontal intent and avoid system edges, map/replay gestures and vertical scrolling.

## Terrain inspection in 0.13

The island labels the current state (Uphill/Downhill/Flat; Subida/Bajada/Llano) with a 260 ms vertical flip only when the state changes. Rapid changes settle to the latest value; pause, seek, detach and Remove animations leave no half-flipped text. The existing miniature profile eases across 280 ms. Unknown remains distinct from flat.

Replay Details keeps a short title, 48 dp overflow and Close targets with 8 dp separation. The overflow uses the existing Tabler pencil and brain with theme-aware tint. Elevation is a full-width touch chart with a position dot, concise selected time/distance/relative height, terrain reading and native accessible slider. Horizontal gestures inspect; vertical gestures scroll. Gaps retain the observed clock.

Terrain 0.14 adds one muted source/coverage line to the existing island and elevation details. Settings → Terrain maps follows the existing stable sheet, switch and Tabler icon patterns; no new visual system or navigation tab. Gaps stay empty while areas load or confidence is insufficient.

Terrain 0.15 extends the existing sheet with a status heading, compact transfer detail, determinate download progress and one primary action. Record exposes a single Offline terrain entry above the fixed Start control. Current-location acquisition precedes an explicit size preview/download action. Ready means the saved area, not an inferred current position; cancellation, incomplete files, disabled maps, permission denial and unsupported polar areas have actionable states. Preparation follows existing native typography, monochrome controls, Tabler icons, stable headers and large-text stacking.


## Version 0.16 preparation and controls

The 0.16 design supersedes oversized Record preparation buttons and the old Smart sheet. Use a 48dp speaker/muted-speaker toggle with a short Snackbar, plus a 48dp options control for voice settings/Test voice and manual goal. Start stays fixed. An enabled daily AI suggestion appears directly below as distance/time, optional pace range and effort; a Use switch selects it without activating voice. Stack at larger font scales. Loading uses an inline spinner; errors/context changes have a short label and explicit refresh icon.

About is a stable native sheet: installed version, accessible amber newer-release icon and refresh, short storage/terrain lines, repository and sources/licenses. The amber role reuses grade-uphill contrast tokens. Settings owns terrain download controls; none appear on Record. The shared island omits its provider footer and pace heading; a small min/km sits alongside rolling digits before the terrain profile. Activities moves whole-history sharing/export into its heading overflow. Replay speed is a 48dp accessible native slider with endpoint/current multipliers, separate from the timeline. Existing monochrome surfaces, pinned Tabler strokes, large-text/reduced-motion behavior and essential errors remain.
