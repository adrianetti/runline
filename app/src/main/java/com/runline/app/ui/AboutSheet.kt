package com.runline.app.ui

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.runline.app.R
import com.runline.app.updates.*
import java.util.concurrent.Executors

internal class AboutSheet(private val activity: AppCompatActivity) {
    private val sheet = AiSheet(activity, R.string.main_about)
    private val installed = activity.packageManager.getPackageInfo(activity.packageName, 0).versionName.orEmpty()
    private val checker = ReleaseChecker.get(activity)
    private val status = sheet.text("", 14)
    private val indicator = sheet.button("", R.drawable.ic_check)
    private val storage = sheet.text(activity.getString(R.string.v16_storage_loading))
    private val terrain = sheet.text("")
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val changed: () -> Unit = { render() }
    private var sources: androidx.appcompat.app.AlertDialog? = null
    val isShowing get() = sheet.isShowing
    init {
        val row = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(sheet.text("Runline $installed", 24, true), LinearLayout.LayoutParams(0, -2, 1f))
        indicator.apply { iconPadding = 0; setPadding(sheet.dp(12), 0, sheet.dp(12), 0); setOnClickListener { open(ReleaseChecker.RELEASES) } }
        row.addView(indicator, LinearLayout.LayoutParams(sheet.dp(48), sheet.dp(48)))
        val refresh = sheet.button("", R.drawable.ic_refresh).apply {
            iconPadding = 0; contentDescription = activity.getString(R.string.v16_check_updates); tooltipText = contentDescription
            setPadding(sheet.dp(12), 0, sheet.dp(12), 0); setOnClickListener { checker.check(force = true) }
        }
        row.addView(refresh, LinearLayout.LayoutParams(sheet.dp(48), sheet.dp(48)))
        sheet.column.addView(row, sheet.row(0, 8)); sheet.column.addView(status, sheet.row(0, 24))
        sheet.column.addView(storage, sheet.row(0, 4)); sheet.column.addView(terrain, sheet.row(0, 20))
        sheet.column.addView(sheet.button(activity.getString(R.string.v16_repository), R.drawable.ic_external_link).apply {
            setOnClickListener { open(ReleaseChecker.REPOSITORY) }
        }, sheet.row())
        sheet.column.addView(sheet.button(activity.getString(R.string.v16_credits), R.drawable.ic_info).apply {
            setOnClickListener {
                sources = MaterialAlertDialogBuilder(activity).setTitle(R.string.v16_credits)
                    .setMessage(activity.assets.open("licenses/app-sources.txt").bufferedReader().use { it.readText() })
                    .setPositiveButton(R.string.main_close, null).show()
            }
        }, sheet.row())
        sheet.dialog.setOnDismissListener { sheet.closed = true; checker.unlisten(changed); worker.shutdownNow(); sources?.dismiss() }
    }
    fun show() {
        checker.listen(changed); sheet.show(); render(); checker.check()
        worker.execute {
            val measured = AppStorage.read(activity.applicationContext)
            main.post { if (!sheet.closed) {
                storage.text = activity.getString(R.string.v16_storage, measured.total?.let { Formatter.formatFileSize(activity, it) } ?: "—")
                terrain.text = activity.getString(R.string.v16_terrain_size, Formatter.formatFileSize(activity, measured.terrain))
            } }
        }
    }
    fun close() { sheet.close() }
    private fun render() {
        if (sheet.closed) return
        val state = checker.state
        val latest = state.latest?.let(ReleaseVersion::parse); val current = ReleaseVersion.parse(installed)
        val newer = latest != null && current != null && latest > current
        indicator.setIconResource(if (newer) R.drawable.ic_update else R.drawable.ic_external_link)
        indicator.iconTint = android.content.res.ColorStateList.valueOf(activity.getColor(if (newer) R.color.grade_uphill else R.color.on_surface))
        indicator.contentDescription = activity.getString(if (newer) R.string.v16_update_available else R.string.v16_releases, state.latest.orEmpty())
        indicator.tooltipText = indicator.contentDescription
        status.text = activity.getString(when { state.checking -> R.string.v16_update_checking; state.failed -> R.string.v16_update_failed
            newer -> R.string.v16_update_available; latest != null -> R.string.v16_up_to_date; else -> R.string.v16_check_updates }, state.latest.orEmpty())
    }
    private fun open(url: String) { runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }
}
