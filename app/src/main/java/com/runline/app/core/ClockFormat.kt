package com.runline.app.core

import java.util.Locale

object ClockFormat {
    private const val SIXTY = 60
    private const val HOUR_SECONDS = 3_600

    fun pair(
        whole: Long,
        locale: Locale,
    ): String = String.format(locale, "%d:%02d", whole / SIXTY, whole % SIXTY)

    fun duration(
        seconds: Long,
        locale: Locale,
    ): String =
        if (seconds >= HOUR_SECONDS) {
            String.format(locale, "%d:%02d:%02d", seconds / HOUR_SECONDS, seconds / SIXTY % SIXTY, seconds % SIXTY)
        } else {
            pair(seconds, locale)
        }
}
