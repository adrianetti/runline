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
    fontSize: "40sp"
    fontWeight: 500
    fontFeature: "tnum"
  display-detail:
    fontFamily: "sans-serif-medium"
    fontSize: "36sp"
    fontWeight: 500
  headline:
    fontFamily: "sans-serif-medium"
    fontSize: "24sp"
    fontWeight: 500
  history-value:
    fontFamily: "sans-serif-medium"
    fontSize: "26sp"
    fontWeight: 500
  metric-value:
    fontFamily: "sans-serif-medium"
    fontSize: "24sp"
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
    typography: "{typography.display}"
  history-row:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.on-surface}"
    padding: "20dp 4dp"
  satellite-map:
    height: "240dp"
  satellite-map-compact:
    height: "120dp"
  replay-map:
    height: "300dp"
  replay-map-compact:
    height: "160dp"
  replay-control:
    textColor: "{colors.primary}"
    rounded: "{rounded.replay-control}"
    height: "52dp"
  altitude-profile:
    textColor: "{colors.primary}"
    height: "144dp"
  toolbar-strava:
    textColor: "{colors.primary}"
    height: "48dp"
  detail-row:
    textColor: "{colors.on-surface}"
    padding: "12dp 0dp"
---

# Design System: Runline

## Overview

**Direction: Minimal running instrument.** The user requested monotone controls, very little text, easy controls and a single free icon family, followed by real satellite maps, replay and more statistics. The application chrome remains neutral; satellite/aerial imagery retains its actual colors. Runline remains the working app name.

The recording screen puts the map above distance, time and pace, with live speed and GPS altitude during a session. A short status identifies Ready, Recording, Paused or Run recovered. Start/Pause/Resume/Finish remain labeled controls in a bottom action area. A saved run adds playback, altitude and totals; its Strava action stays in the top toolbar instead of below the statistics.

This record follows `MainActivity.kt`, `SatelliteMapView.kt`, `RunReplayView.kt`, the `RouteView.kt` fallback, Tabler drawables and day/night colors. Native values use `dp` and `sp`. Sidecar previews approximate component structure; map placeholders do not claim to render actual imagery. API 34 emulator review covered actual imagery, replay, light/dark mode, 1.3× font scaling and landscape. The final sharing dialog was confirmed monochrome and the map-credit target measured 48dp; physical-phone coverage remains unverified.

## Colors

All application role colors are neutral grays. `primary` is dark ink in light mode and near-white in dark mode. The associated `on-primary` foreground reverses with it. `surface` and `surface-variant` separate screen and route/list structure without a hue accent. `on-surface-variant` carries dates, metric labels and GPS status.

The error role is also neutral. Errors must be identifiable through words and available actions, never through color alone. No Strava orange or colored control accent is added. Real map imagery and its supplied road/place labels are the intentional exception to the monochrome presentation. External Android/Strava screens keep their owners' styling.

`Base.Theme.Runline` supplies shared Material roles. Qualified day/night `Theme.Runline` resources and the window-insets controller keep system-bar icons visible. Keys ending in `-dark` document night-resource overrides; runtime resource names remain the same.

**Pair roles together:** a foreground always uses the matching light/dark surface. The eight-step ramps in the sidecar are neutral documentation previews, not additional Android palette tokens.

## Typography

Android system sans is the only text family, with the medium face for important measurements and headings. Live measurements use tabular numerals. Copy is sentence case and short: Ready, Time, Pace /km, Runs, Splits.

The frontmatter records the actual sizes rather than claiming an unimplemented type scale. The large distance is paired with its unit; the two smaller values have persistent labels. All text follows Android font scaling. Missing measurements use an em dash or a short no-data statement.

Live distance is 40sp; the saved-run distance is 36sp. Replay metrics use 24sp values with 13sp labels. Short headings and an em dash keep data legible without inventing missing measurements.

**Explain on demand:** saved-run totals expose elapsed time and sample count; detailed calculation rules stay in Run info. Retain meaningful permission, map failure, storage and recovery messages even when normal-state copy is brief.

## Layout

The phone uses a 64dp top app bar, 48dp Record/Runs tab row and a scrollable content column with 24dp page insets. The recording controls remain in a separate bottom action area. Ready and recording states show a 240dp satellite panel above the measurements. Without an existing route, a permitted last known location may center it; viewing a map does not start recording.

When the available screen height is below 500dp, the recording map shrinks to 120dp and the replay map to 160dp. The compact saved-run header places distance beside time and pace instead of using the larger separate distance heading. This keeps playback closer to the top in landscape.

History is a chronological flat list. Run details put date, distance and Time/Pace before the 300dp replay map. Play and speed controls, a seek bar, GPS-time clock, replay metrics and a 144dp altitude profile follow. Run totals and splits remain further down the scroll. Strava is a labeled 48dp toolbar action. Export GPX, Share file, Run info and Delete run remain in overflow. Discard is available only for paused/recovered runs and still requires confirmation.

This is a phone layout. Tablet-specific navigation or a separate tablet composition is not implemented. No material layout issue remained in the reviewed emulator configurations; that evidence does not cover every phone size or accessibility setting.

## Elevation & Depth

Use native Material pressed/focus feedback and dialogs. Content has no custom shadow vocabulary. List/detail rows use thin rules rather than elevated cards. White map controls, a short status strip and visible credits sit above imagery. Playback animation communicates recorded position; it is functional, stops when backgrounded and does not add decorative motion. No blur or decorative gradients are used.

## Shapes

Main recording controls use a 16dp radius and at least 64dp height. Replay controls use a 12dp radius, at least 52dp height and 20dp icons. Map fit/follow controls and the map-credit strip use 48dp targets. Icons retain Tabler's 24-unit geometry, 2-unit round stroke, round caps and round joins. The launcher uses the same running figure in white on a dark square.

Route geometry remains distinct from icons. On satellite imagery, a white route line has a black outline; the starting point and moving position use white circles with black outlines. Pauses, interruption boundaries and replay gaps break the route. The native failure fallback uses the existing neutral geometric trace. Neither the background tiles nor interpolated replay positions modify saved GPS samples.

## Components

- **Primary action:** one wide, labeled Material button with a Tabler icon. Start run, Pause or Resume matches the state. Once paused, Finish is the secondary outlined action and saves directly.
- **Record/Runs tabs:** familiar native tabs pair labels with the run/history icons. Keep both labels; the icons are supporting cues.
- **Protection and overflow:** the shield opens battery/notification information and Android settings. The three-dot menu holds occasional actions. Icon-only controls require accessible names.
- **Measurements:** distance is large; Time and Pace /km are a balanced pair. Live speed and GPS altitude add a second pair during a session. Values update without rebuilding unchanged labels.
- **Run rows:** date, distance and concise active-time/pace summary form a single native touch target.
- **Satellite map:** a real geographic background with pan/zoom, fit-route and follow-marker controls. Map credits open source/license/coverage/privacy details. Loading and failure states must not imply that GPS recording stopped; map requests are display-only.
- **Replay:** labeled Play/Pause/Replay and a speed selector for 1×/5×/10×/30×/60×. A seek bar controls position. The UI updates up to 20 times per second, pauses on backgrounding and cuts across unobserved intervals. There is no video export.
- **Altitude and totals:** show GPS estimates, preserve chart gaps and use — for unknown data. A short note identifies GPS estimates; filtering and timing rules are explained in Run info.
- **Strava handoff:** the toolbar saves GPX directly to Downloads on Android 10+ and opens the website only after success. Older devices and failed automatic saves use a file picker. The saved filename remains available to copy. Choosing the file and publishing still happen on Strava; no connected/uploaded state is invented.
- **Recovery:** Run recovered offers Resume/Finish. An unreadable draft exposes Keep draft & reset with a preservation confirmation; completed history is not cleared.

The Tabler license and pinned originals are documented in THIRD_PARTY_NOTICES.md and bundled in app assets, reachable from About. Android system dialogs and external apps are outside Runline's icon-set policy.

## Do's and Don'ts

- Do keep controls achromatic in both themes and use one Tabler outline family; preserve real imagery colors.
- Do pair icons with short labels for primary actions and accessible labels for icon-only actions.
- Do let starting a run take one tap after Android's required permissions/settings are satisfied.
- Do retain state, uncertainty and recovery information in plain language.
- Do keep lower-frequency detail in menus and preserve confirmation for destructive actions.
- Do preserve visible map attribution and disclose visible-area network requests without interrupting every run.
- Do distinguish active time from observed replay time, and estimates from unavailable data.
- Don't reintroduce promotional headings, paragraphs on the recording screen or decorative cards.
- Don't replace critical labels with ambiguous sports glyphs.
- Don't imply that saving a GPX has uploaded or published it on Strava.
- Don't imply a powered-off phone can record, or that local recovery copies are cloud backup.
