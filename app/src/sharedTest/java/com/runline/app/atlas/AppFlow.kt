package com.runline.app.atlas

enum class FlowState { IDLE, RECORDING, PAUSED }

data class FlowStep(
    val control: String,
    val to: String,
)

data class FlowScreen(
    val id: String,
    val kind: UiWindowKind,
    val marker: String,
    val state: FlowState = FlowState.IDLE,
    val exit: String? = null,
    val reading: Boolean = false,
    val steps: List<FlowStep> = emptyList(),
    val arrange: List<String> = emptyList(),
) {
    val isTab get() = id in AppFlow.TABS
}

object AppFlow {
    const val RECORD = "record"
    const val ACTIVITIES = "activities"
    const val ROUTES = "routes"
    const val RUN = "run"
    const val PAUSED = "paused"
    const val REPLAY = "replay"
    const val ROUTE = "route"
    const val SETTINGS = "settings"
    private const val CLOSE = "main_close"
    private const val REPLAY_OPTIONS = "replay_options"
    private const val DIAGNOSTICS_TITLE = "diag_v12_title"
    val TABS = setOf(RECORD, ACTIVITIES, ROUTES, SETTINGS)
    val COUNCIL = listOf(RECORD, RUN, ACTIVITIES, REPLAY, ROUTE, SETTINGS)

    val screens: List<FlowScreen> = idleScreens() + activityScreens() + routeScreens() + settingsScreens() + recordingScreens()

    private val byId = screens.associateBy(FlowScreen::id)

    fun screen(id: String): FlowScreen = byId.getValue(id)

    fun root(state: FlowState): FlowScreen =
        when (state) {
            FlowState.IDLE -> screen(RECORD)
            FlowState.RECORDING -> screen(RUN)
            FlowState.PAUSED -> screen(PAUSED)
        }

    fun path(id: String): List<FlowStep>? {
        val target = screen(id)
        val start = root(target.state)
        val trail = mutableMapOf<String, List<FlowStep>>(start.id to emptyList())
        val queue = ArrayDeque(listOf(start.id))
        while (queue.isNotEmpty() && id !in trail) {
            val current = queue.removeFirst()
            for (step in byId[current]?.steps.orEmpty()) {
                if (step.to !in trail && step.to in byId) {
                    trail[step.to] = trail.getValue(current) + step
                    queue += step.to
                }
            }
        }
        return trail[id]
    }

    private fun idleScreens() =
        listOf(
            FlowScreen(
                RECORD,
                UiWindowKind.SCREEN,
                "main_start",
                steps =
                    listOf(
                        FlowStep("main_activities", ACTIVITIES),
                        FlowStep("v19_tab_routes", ROUTES),
                        FlowStep("v8_settings", SETTINGS),
                        FlowStep("coach_ui_title", "coach"),
                    ),
            ),
            FlowScreen("coach", UiWindowKind.SHEET, "coach_ui_enabled", exit = CLOSE),
        )

    private fun activityScreens() =
        listOf(
            FlowScreen(
                ACTIVITIES,
                UiWindowKind.SCREEN,
                "v16_history_options",
                steps = listOf(FlowStep("v16_history_options", "historyMenu"), FlowStep("main_history_row", REPLAY)),
            ),
            FlowScreen("historyMenu", UiWindowKind.MENU, "main_share_all", steps = listOf(FlowStep("main_export_all", "exportAll"))),
            FlowScreen("exportAll", UiWindowKind.DIALOG, "main_summary_csv"),
            FlowScreen(
                REPLAY,
                UiWindowKind.SCREEN,
                REPLAY_OPTIONS,
                exit = "replay_back_to_activities",
                steps = listOf(FlowStep(REPLAY_OPTIONS, "replayMenu")),
                arrange = listOf("replay_play", "replay_pause"),
            ),
            FlowScreen(
                "replayMenu",
                UiWindowKind.MENU,
                "v7_edit_activity",
                steps =
                    listOf(
                        FlowStep("replay_activity_details", "details"),
                        FlowStep("replay_export_file", "activityFile"),
                        FlowStep("v7_edit_activity", "editor"),
                        FlowStep("v7_run_review", "review"),
                        FlowStep("diag_v12_coach", "coachLog"),
                        FlowStep("main_delete_activity", "deleteActivity"),
                    ),
            ),
            FlowScreen("activityFile", UiWindowKind.DIALOG, "main_activity_file"),
            FlowScreen("editor", UiWindowKind.SHEET, "details_edit", exit = CLOSE),
            FlowScreen("review", UiWindowKind.SHEET, "ai_review_title", exit = CLOSE, reading = true),
            FlowScreen("coachLog", UiWindowKind.SHEET, DIAGNOSTICS_TITLE, exit = CLOSE, reading = true),
            FlowScreen("deleteActivity", UiWindowKind.DIALOG, "main_delete_title"),
            FlowScreen(
                "details",
                UiWindowKind.SHEET,
                "replay_activity_details",
                exit = "replay_close_details",
                steps = listOf(FlowStep(REPLAY_OPTIONS, "detailsMenu"), FlowStep("map_v8_stats_info", "statsInfo")),
            ),
            FlowScreen("detailsMenu", UiWindowKind.MENU, "activity_action_edit"),
            FlowScreen("statsInfo", UiWindowKind.DIALOG, "map_v8_stats_info", reading = true),
        )

    private fun routeScreens() =
        listOf(
            FlowScreen(
                ROUTES,
                UiWindowKind.SCREEN,
                "v19_routes_title",
                steps = listOf(FlowStep("v19_routes_options", "routesMenu"), FlowStep("v19_route_row_description", ROUTE)),
            ),
            FlowScreen("routesMenu", UiWindowKind.MENU, "v19_routes_refresh", steps = listOf(FlowStep("v19_routes_about", "routesAbout"))),
            FlowScreen("routesAbout", UiWindowKind.DIALOG, "v19_routes_about", reading = true),
            FlowScreen(
                ROUTE,
                UiWindowKind.SCREEN,
                "v19_route_options",
                exit = "v19_route_back",
                steps = listOf(FlowStep("v19_route_options", "routeMenu")),
            ),
            FlowScreen("routeMenu", UiWindowKind.MENU, "v21_directions", steps = listOf(FlowStep("v20_route_preview", "routePreview"))),
            FlowScreen("routePreview", UiWindowKind.SCREEN, "v20_preview_back_description", exit = "v20_preview_back_description"),
        )

    private fun settingsScreens() =
        listOf(
            FlowScreen(
                SETTINGS,
                UiWindowKind.SCREEN,
                "v17_auto_pause",
                steps =
                    listOf(
                        FlowStep("profile_v9_title", "profile"),
                        FlowStep("v8_ai", "ai"),
                        FlowStep("main_language", "language"),
                        FlowStep("music_title", "music"),
                        FlowStep("terrain_offline_title", "terrain"),
                        FlowStep("main_protection", "protection"),
                        FlowStep(DIAGNOSTICS_TITLE, "diagnostics"),
                        FlowStep("main_about", "about"),
                    ),
            ),
            FlowScreen("profile", UiWindowKind.SHEET, "profile_v9_title", exit = CLOSE),
            FlowScreen("ai", UiWindowKind.SHEET, "ai_v8_title", exit = CLOSE),
            FlowScreen("language", UiWindowKind.DIALOG, "main_language"),
            FlowScreen("music", UiWindowKind.SHEET, "music_title", exit = CLOSE),
            FlowScreen("terrain", UiWindowKind.SHEET, "terrain_offline_title", exit = CLOSE),
            FlowScreen("protection", UiWindowKind.DIALOG, "main_protection", reading = true),
            FlowScreen("diagnostics", UiWindowKind.SHEET, DIAGNOSTICS_TITLE, exit = CLOSE),
            FlowScreen("about", UiWindowKind.SHEET, "main_about", exit = CLOSE),
        )

    private fun recordingScreens() =
        listOf(
            FlowScreen(
                RUN,
                UiWindowKind.SCREEN,
                "active_pause",
                FlowState.RECORDING,
                steps = listOf(FlowStep("active_options", "runMenu")),
            ),
            FlowScreen("runMenu", UiWindowKind.MENU, "coach_ui_title", FlowState.RECORDING),
            FlowScreen(
                PAUSED,
                UiWindowKind.SCREEN,
                "active_resume",
                FlowState.PAUSED,
                steps = listOf(FlowStep("active_options", "pausedMenu")),
            ),
            FlowScreen(
                "pausedMenu",
                UiWindowKind.MENU,
                "main_discard_activity",
                FlowState.PAUSED,
                steps = listOf(FlowStep("main_discard_activity", "discard")),
            ),
            FlowScreen("discard", UiWindowKind.DIALOG, "main_discard_title", FlowState.PAUSED),
        )
}
