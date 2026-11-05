package com.runline.app.updates

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri

/** Prefer GitHub Mobile for our fixed repository links, with a normal external-handler fallback. */
object RepositoryLinks {
    const val GITHUB_PACKAGE = "com.github.android"
    fun open(activity: Activity, url: String): Boolean {
        require(url == ReleaseChecker.REPOSITORY || url == ReleaseChecker.RELEASES)
        fun launch(appPackage: String?): Boolean = try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                appPackage?.let { setPackage(it) }
            }); true
        } catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }
        return launch(GITHUB_PACKAGE) || launch(null)
    }
}
