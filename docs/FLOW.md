# App flow

This diagram is the map of every screen, sheet, dialog and menu in Runline, and of the control that leads to each one. It is generated from `AppFlow` in `app/src/sharedTest/java/com/runline/app/atlas/`. A JVM test fails when this file and the graph differ, and prints the diagram to paste here.

Shapes: a rectangle is a full screen, a rounded box is a bottom sheet, a hexagon is a dialog and a slanted box is a menu. The label on an arrow is the name of the control. The three groups are the app states: idle, recording and paused.

```mermaid
flowchart LR
    subgraph idle
        record["record: Start"]
        coach(["coach: Spoken guidance"])
        activities["activities: Share or export all activities"]
        historyMenu[/"historyMenu: Share all"/]
        exportAll{{"exportAll: Summary · CSV"}}
        replay["replay: Activity options"]
        replayMenu[/"replayMenu: Edit activity"/]
        activityFile{{"activityFile: Activity file"}}
        editor(["editor: Edit activity"])
        review(["review: Run review"])
        coachLog(["coachLog: Diagnostics"])
        deleteActivity{{"deleteActivity: Delete activity?"}}
        details(["details: Details"])
        detailsMenu[/"detailsMenu: Edit"/]
        statsInfo{{"statsInfo: About GPS estimates"}}
        routes["routes: Routes"]
        routesMenu[/"routesMenu: Check for new routes"/]
        routesAbout{{"routesAbout: About open routes"}}
        route["route: Route options"]
        routeMenu[/"routeMenu: Directions to start"/]
        routePreview["routePreview: Back to the route"]
        settings["settings: Auto-pause at stops"]
        profile(["profile: Runner profile"])
        ai(["ai: AI"])
        language{{"language: Language"}}
        music(["music: Music"])
        terrain(["terrain: Offline terrain"])
        protection{{"protection: Recording protection"}}
        diagnostics(["diagnostics: Diagnostics"])
        about(["about: About"])
    end
    subgraph recording
        run["run: Pause"]
        runMenu[/"runMenu: Voice coach"/]
    end
    subgraph paused
        paused["paused: Resume"]
        pausedMenu[/"pausedMenu: Discard recording"/]
        discard{{"discard: Discard this recording?"}}
    end
    record -->|Activities| activities
    record -->|Routes| routes
    record -->|Settings| settings
    record -->|Voice coach| coach
    activities -->|Share or export all activities| historyMenu
    activities -->|…, …, … kilometres, …. View activity.| replay
    historyMenu -->|Export all| exportAll
    replay -->|Activity options| replayMenu
    replayMenu -->|Details| details
    replayMenu -->|Export GPX| activityFile
    replayMenu -->|Edit activity| editor
    replayMenu -->|Coach review| review
    replayMenu -->|Coach log| coachLog
    replayMenu -->|Delete activity| deleteActivity
    details -->|Activity options| detailsMenu
    details -->|About GPS estimates| statsInfo
    routes -->|Route options| routesMenu
    routes -->|…. …. …. Open route.| route
    routesMenu -->|About open routes| routesAbout
    route -->|Route options| routeMenu
    routeMenu -->|Preview| routePreview
    settings -->|Runner profile| profile
    settings -->|AI| ai
    settings -->|Language| language
    settings -->|Music| music
    settings -->|Offline terrain| terrain
    settings -->|Recording protection| protection
    settings -->|Diagnostics| diagnostics
    settings -->|About| about
    run -->|Recording options| runMenu
    paused -->|Recording options| pausedMenu
    pausedMenu -->|Discard recording| discard
```

## What the tests verify

`AppFlowTest` checks the graph on every build:

- Every screen is reachable from its root, and every step leads to a screen that exists.
- Nothing is more than four taps from its tab. Full screens are within three.
- A screen has one control for each destination. No control is listed twice.
- Every full screen and sheet outside the tabs has a way back.
- Every control has an English and a Spanish name.

`ScreenAtlasAndroidTest` then walks the real app along this graph on an emulator, in English light and Spanish dark. It fails when a screen cannot be reached or scores under 100 in either group. On each screen it snapshots the view tree and scores it with the rules in `UiAudit`:

| Group | Rule |
| --- | --- |
| Accessibility | Every control is at least 48dp wide and 48dp high. |
| Accessibility | Every control has a spoken name. |
| Accessibility | Text contrast is at least 4.5:1, or 3:1 for large text. |
| Accessibility | Icons on controls have a contrast of at least 3:1. |
| Accessibility | No text is smaller than 12sp. |
| Accessibility | No text is cut off. |
| Accessibility | Controls never overlap. |
| Accessibility | Every screen and sheet has a heading. |
| Intuitiveness | A screen has at most one filled primary action. |
| Intuitiveness | No two controls on a screen share a name. |
| Intuitiveness | No two controls on a screen share an icon. |
| Intuitiveness | A screen explains itself in at most one short sentence. |
| Intuitiveness | A screen offers at most twelve controls at once, besides the navigation bar. |
| Intuitiveness | Every screen that is not a tab shows its way back. |

Each rule has a seeded failure in `UiAuditTest`. The atlas writes `atlas/report.json` with an accessibility score and an intuitiveness score from 0 to 100 for every screen, plus a view-tree dump and optional screenshots.
