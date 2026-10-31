---
name: "Runline"
description: "A minimal monochrome Android running recorder with one outline icon family."
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
    fontSize: "56sp"
    fontWeight: 500
    fontFeature: "tnum"
  display-detail:
    fontFamily: "sans-serif-medium"
    fontSize: "56sp"
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
  route-trace:
    backgroundColor: "{colors.surface-variant}"
    textColor: "{colors.primary}"
    height: "180dp"
  detail-row:
    textColor: "{colors.on-surface}"
    padding: "12dp 0dp"
---

# Design System: Runline

## Overview

**Direction: Minimal running instrument.** The user explicitly requested monotone colors, very little text, easy controls and a single free icon family. This refresh replaces the prototype's green palette and longer introductory copy. Runline remains the working app name; no separate brand approval is implied.

Distance, time and pace lead. A short status identifies Ready, Recording, Paused or Run recovered. Start/Pause/Resume/Finish remain labeled controls; supporting detail moves into the toolbar menu instead of becoming unexplained icons. The saved-run screen gives Share to Strava one clear primary position.

This record follows the implemented `MainActivity.kt`, `RouteView.kt`, Tabler drawables and day/night color resources. Native values use `dp` and `sp`. The sidecar contains browser approximations for documentation, not a competing web interface or new native tokens.

## Colors

All application role colors are neutral grays. `primary` is dark ink in light mode and near-white in dark mode. The associated `on-primary` foreground reverses with it. `surface` and `surface-variant` separate screen and route/list structure without a hue accent. `on-surface-variant` carries dates, metric labels and GPS status.

The error role is also neutral. Errors must be identifiable through words and available actions, never through color alone. No Strava orange, green success color or other accent is added to Runline's own screens. External Android/Strava screens keep their owners' styling.

`Base.Theme.Runline` supplies shared Material roles. Qualified day/night `Theme.Runline` resources and the window-insets controller keep system-bar icons visible. Keys ending in `-dark` document night-resource overrides; runtime resource names remain the same.

**Pair roles together:** a foreground always uses the matching light/dark surface. The eight-step ramps in the sidecar are neutral documentation previews, not additional Android palette tokens.

## Typography

Android system sans is the only text family, with the medium face for important measurements and headings. Live measurements use tabular numerals. Copy is sentence case and short: Ready, Time, Pace /km, Runs, Splits.

The frontmatter records the actual sizes rather than claiming an unimplemented type scale. The large distance is paired with its unit; the two smaller values have persistent labels. All text follows Android font scaling. Missing measurements use an em dash or a short no-data statement.

**Explain on demand:** elapsed time, GPS sample count and calculation details belong in Run info. Retain meaningful permission, storage and recovery messages even when normal-state copy is brief.

## Layout

The phone uses a 64dp top app bar, 48dp Record/Runs tab row and a scrollable content column with 24dp page insets. The recording controls remain in a separate bottom action area. A ready screen does not reserve an empty route panel; once a session exists, its route uses a 180dp panel. Saved runs use that same panel height.

History is a chronological flat list. Run details put date, distance, Time/Pace, route and Share to Strava before the split rows. Export GPX, Share file, Run info and Delete run move to the overflow menu. Discard is available only for a paused/recovered run and still requires confirmation.

This is a phone layout. Tablet-specific navigation or a separate tablet composition is not implemented. Larger fonts and different phone sizes must be checked in native captures before claiming coverage.

## Elevation & Depth

Use native Material pressed/focus feedback and dialogs. Content has no custom shadow vocabulary. Route panels use a flat neutral fill; list/detail rows use thin rules rather than elevated cards. No decorative blur, gradients or animation is part of the design.

## Shapes

Primary and outlined controls use the implemented 16dp radius and at least 64dp height. Icons use Tabler's 24-unit geometry, 2-unit round stroke, round caps and round joins. The launcher adapts the same running figure in white on a dark square.

Route geometry remains distinct from icons: a 3.5dp line draws actual accepted GPS samples, a circle marks the start and a square the finish. Pauses and interruptions break the line. No map tiles or invented route illustration are shown.

## Components

- **Primary action:** one wide, labeled Material button with a Tabler icon. Start run, Pause or Resume matches the state. Once paused, Finish is the secondary outlined action and saves directly.
- **Record/Runs tabs:** familiar native tabs pair labels with the run/history icons. Keep both labels; the icons are supporting cues.
- **Protection and overflow:** the shield opens battery/notification information and Android settings. The three-dot menu holds occasional actions. Icon-only controls require accessible names.
- **Measurements:** distance is large; Time and Pace /km are a balanced pair. Values update without rebuilding unchanged labels.
- **Run rows:** date, distance and concise active-time/pace summary form a single native touch target.
- **Route:** shown only when it serves a recorded/recovered session; missing-fix states say Finding GPS or GPS ready. It is a trace, not a geographic basemap.
- **Strava handoff:** Share to Strava saves a GPX, then opens the website after a successful save. No connected/uploaded state is invented. Runs without usable route data receive a short explanation.
- **Recovery:** Run recovered offers Resume/Finish. An unreadable draft exposes Keep draft & reset with a preservation confirmation; completed history is not cleared.

The Tabler license and pinned originals are documented in THIRD_PARTY_NOTICES.md and bundled in app assets, reachable from About. Android system dialogs and external apps are outside Runline's icon-set policy.

## Do's and Don'ts

- Do keep the palette achromatic in both themes and use one Tabler outline family.
- Do pair icons with short labels for primary actions and accessible labels for icon-only actions.
- Do let starting a run take one tap after Android's required permissions/settings are satisfied.
- Do retain state, uncertainty and recovery information in plain language.
- Do keep lower-frequency detail in menus and preserve confirmation for destructive actions.
- Don't reintroduce promotional headings, paragraphs on the recording screen or decorative cards.
- Don't replace critical labels with ambiguous sports glyphs.
- Don't imply that saving a GPX has uploaded or published it on Strava.
- Don't imply a powered-off phone can record, or that local recovery copies are cloud backup.
