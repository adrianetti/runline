package com.runline.app.ui

import android.content.Context
import android.widget.LinearLayout
import com.runline.app.R
import com.runline.app.core.ActivityType
import com.runline.app.core.WeekProgress
import java.time.ZoneOffset
import java.util.Locale

internal data class RecordHome(
    val type: ActivityType,
    val week: WeekProgress,
    val coach: String,
    val route: String,
)

internal data class RecordHomeActions(
    val type: (ActivityType) -> Unit,
    val coach: () -> Unit,
    val route: () -> Unit,
)

internal class RecordHomeView(
    context: Context,
    home: RecordHome,
    actions: RecordHomeActions,
) : LinearLayout(context) {
    private val locale: Locale = resources.configuration.locales[0]
    val extras = LinearLayout(context).apply { orientation = VERTICAL }

    init {
        orientation = VERTICAL
        val types = ActivityType.entries
        addView(
            SegmentedControl(
                context,
                types.map { context.getString(TYPE_NAMES.getValue(it)) },
                types.indexOf(home.type),
            ) { actions.type(types[it]) }.apply { contentDescription = context.getString(R.string.main_activity_type) },
            row(),
        )
        addView(UiText.label(context, context.getString(R.string.v26_this_week)).apply { UiText.heading(this) }, row(top = Space.XXL))
        val distance = String.format(locale, "%.1f", home.week.distanceMeters / METERS_PER_KM)
        addView(
            DataFieldView(context, "", distance, context.getString(R.string.island_km), TextScale.HERO).apply {
                getChildAt(0).visibility = GONE
            },
            row(),
        )
        addView(WeekStripView(context, home.week, locale), row(top = Space.M))
        addView(extras, row(top = Space.M))
        addView(
            UiRows.hairline(context),
            LayoutParams(LayoutParams.MATCH_PARENT, UiText.dp(context, Space.HAIRLINE)).apply { topMargin = UiText.dp(context, Space.M) },
        )
        addView(UiRows.navigation(context, R.drawable.ic_route, context.getString(R.string.v26_route), home.route, actions.route), row())
        addView(UiRows.hairline(context))
        addView(
            UiRows.navigation(context, R.drawable.ic_speaker, context.getString(R.string.coach_ui_title), home.coach, actions.coach),
            row(),
        )
        addView(UiRows.hairline(context))
    }

    constructor(context: Context) : this(
        context,
        RecordHome(ActivityType.RUN, WeekProgress.of(emptyList(), 0L, ZoneOffset.UTC), "", ""),
        RecordHomeActions({}, {}, {}),
    )

    private fun row(top: Int = 0) =
        LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = UiText.dp(context, top)
        }

    private companion object {
        const val METERS_PER_KM = 1_000.0
        val TYPE_NAMES =
            mapOf(
                ActivityType.RUN to R.string.main_type_run,
                ActivityType.WALK to R.string.main_type_walk,
                ActivityType.HIKE to R.string.main_type_hike,
            )
    }
}
