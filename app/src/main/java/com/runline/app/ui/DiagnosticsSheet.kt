package com.runline.app.ui

import android.content.ClipData
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.tabs.TabLayout
import com.runline.app.R
import com.runline.app.data.RunStore
import com.runline.app.diagnostics.Diagnostics
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.Executors

/** Optional local tools, separate from the runner's two primary tabs. No network calls. */
class DiagnosticsSheet(private val activity: AppCompatActivity, val runId: String? = null, val usageOnly: Boolean = false,
                       private val openPosition: (String, Long) -> Unit = { _, _ -> }) {
    private val sheet = AiSheet(activity, if (usageOnly) R.string.diag_v12_usage else R.string.diag_v12_title)
    private val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var selected = runId
    private var usage = usageOnly
    private var allEvents = false
    private var limit = 40
    private var generation = 0
    private var recordSwitch: SwitchMaterial? = null
    val isShowing get() = sheet.isShowing
    init {
        sheet.column.addView(sheet.text(activity.getString(R.string.diag_v12_private), 13), sheet.row(0, 12))
        if (!usageOnly && runId == null) {
            sheet.column.addView(TabLayout(activity).apply {
                addTab(newTab().setText(R.string.diag_v12_coach)); addTab(newTab().setText(R.string.diag_v12_usage))
                addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                    override fun onTabSelected(tab: TabLayout.Tab) { usage = tab.position == 1; recordSwitch?.visibility = if (usage) View.GONE else View.VISIBLE; selected = null; load() }
                    override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                    override fun onTabReselected(tab: TabLayout.Tab) = Unit
                })
            }, sheet.row(0, 16))
            sheet.column.addView(SwitchMaterial(activity).apply {
                recordSwitch = this
                setText(R.string.diag_v12_record); isChecked = Diagnostics.enabled(activity); minHeight = sheet.dp(48)
                setOnCheckedChangeListener { _, enabled -> Diagnostics.setEnabled(activity, enabled) }
            }, sheet.row(0, 12))
        }
        sheet.column.addView(body, sheet.row())
        sheet.column.addView(sheet.button(activity.getString(R.string.diag_v12_refresh), R.drawable.ic_history).apply { setOnClickListener { load() } }, sheet.row(8))
        if (runId == null && !usageOnly) sheet.column.addView(sheet.button(activity.getString(R.string.diag_v12_clear), R.drawable.ic_trash).apply {
            setOnClickListener { MaterialAlertDialogBuilder(activity).setTitle(R.string.diag_v12_clear)
                .setMessage(R.string.diag_v12_clear_hint).setNegativeButton(R.string.main_cancel, null)
                .setPositiveButton(R.string.main_delete) { _, _ -> Diagnostics.clear(activity) { if (!sheet.closed) load() } }.show() }
        }, sheet.row(16))
        sheet.dialog.setOnDismissListener { sheet.closed = true; worker.shutdownNow() }
    }
    fun show() { sheet.show(); load() }
    fun close() { generation++; worker.shutdownNow(); sheet.close() }
    private fun load() {
        val token = ++generation; val current = selected; val usageTab = usage
        body.removeAllViews(); body.addView(sheet.text(activity.getString(R.string.ai_working)))
        worker.execute {
            val result = runCatching {
                val log = Diagnostics.log(activity)
                if (usageTab) log.read("usage") else if (current != null) log.read(current)
                else log.ids().map { id ->
                    val run = RunStore(activity).get(id)
                    JSONObject().put("id", id).put("at_ms", run?.startedAtMs ?: 0)
                        .put("title", run?.let { RunStore(activity).details(it.id).title }.orEmpty())
                }
            }
            main.post {
                if (sheet.closed || token != generation) return@post
                body.removeAllViews()
                result.fold({ events ->
                    if (usageTab) renderUsage(events) else if (current != null) renderCoach(events) else renderRuns(events)
                }, { body.addView(sheet.text(activity.getString(R.string.ai_error_storage))) })
            }
        }
    }
    private fun renderRuns(events: List<JSONObject>) {
        if (events.isEmpty()) body.addView(sheet.text(activity.getString(R.string.diag_v12_empty)))
        events.take(100).forEach { e ->
            val title = e.optString("title").ifBlank { date(e.optLong("at_ms")) }
            body.addView(sheet.button(title, R.drawable.ic_history).apply {
                setOnClickListener { selected = e.getString("id"); limit = 40; load() }
            }, sheet.row(0, 8))
        }
    }
    private fun renderCoach(events: List<JSONObject>) {
        if (runId == null) body.addView(sheet.button(activity.getString(R.string.main_back_activities), R.drawable.ic_back).apply {
            setOnClickListener { selected = null; load() }
        }, sheet.row(0, 12))
        body.addView(SwitchMaterial(activity).apply {
            setText(R.string.diag_v12_all_events); isChecked = allEvents; minHeight = sheet.dp(48)
            setOnCheckedChangeListener { _, checked -> allEvents = checked; load() }
        }, sheet.row(0, 12))
        val visible = events.filter { allEvents || it.optString("event") == "started" }.asReversed()
        if (visible.isEmpty()) body.addView(sheet.text(activity.getString(R.string.diag_v12_empty)))
        visible.take(limit).forEach { event ->
            val row = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            row.addView(sheet.text(time(event.optLong("at_ms")) + " · " + event.optString("source") +
                if (allEvents) " · " + event.optString("event") else "", 12))
            row.addView(sheet.text(event.optString("text").ifBlank { event.optString("cue") }, 16, true), sheet.row(4, 8))
            val pace = (event.opt("pace_seconds_per_km") as? Number)?.toDouble()
            val grade = (event.opt("grade_percent") as? Number)?.toDouble()
            val paceText = pace?.toInt()?.let { "%d:%02d /km".format(it / 60, it % 60) } ?: "—"
            row.addView(sheet.text("$paceText · ${grade?.let { "%+.0f%%".format(it) } ?: "—"}", 13))
            val extra = sheet.text(buildString {
                append(date(event.optLong("at_ms"))).append("\n")
                append(activity.getString(R.string.diag_v12_fix, time(event.optLong("fix_at_ms")), (event.opt("accuracy_m") as? Number)?.let { "%.0f".format(it.toDouble()) } ?: "—"))
                if (event.has("latitude")) append("\n").append("%.6f, %.6f".format(java.util.Locale.US, event.getDouble("latitude"), event.getDouble("longitude")))
                append("\n").append(event.optString("terrain")).append(" · ").append(event.optString("event"))
            }, 12).apply { visibility = View.GONE; setTextIsSelectable(true) }
            row.addView(extra, sheet.row(8))
            row.setOnClickListener { extra.visibility = if (extra.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
            row.contentDescription = activity.getString(R.string.diag_v12_expand, time(event.optLong("at_ms")))
            body.addView(row, sheet.row(0, 16))
            if (event.optString("event") == "started") body.addView(sheet.button(activity.getString(R.string.diag_v12_replay), R.drawable.ic_play).apply {
                setOnClickListener { selected?.let { id -> close(); openPosition(id, event.optLong("fix_at_ms")) } }
            }, sheet.row(0, 16))
            divider()
        }
        if (visible.size > limit) body.addView(sheet.button(activity.getString(R.string.diag_v12_earlier), R.drawable.ic_history).apply {
            setOnClickListener { limit += 40; load() }
        }, sheet.row(8))
        shareButton(false)
    }
    private fun renderUsage(events: List<JSONObject>) {
        fun total(key: String) = events.sumOf { (it.opt(key) as? Number)?.toLong() ?: 0L }
        val known = events.filter { it.opt("estimated_usd") is Number }
        val cost = known.sumOf { it.getDouble("estimated_usd") }
        body.addView(sheet.text(activity.getString(R.string.diag_v12_requests, events.size), 24, true), sheet.row(0, 16))
        body.addView(sheet.text(activity.getString(R.string.diag_v12_tokens, total("input_tokens"), total("output_tokens"), total("cache_read_tokens"), total("cache_write_tokens"))), sheet.row(0, 12))
        body.addView(sheet.text(activity.getString(R.string.diag_v12_cost, "%.4f".format(java.util.Locale.US, cost), known.size, events.size), 17, true), sheet.row(0, 8))
        body.addView(sheet.text(activity.getString(R.string.diag_v12_scope), 12), sheet.row(0, 20))
        events.asReversed().take(40).forEach { event ->
            body.addView(sheet.text(event.optString("model"), 15, true))
            body.addView(sheet.text("${date(event.optLong("at_ms"))} · ${event.optString("operation")}\nHTTP ${if (event.isNull("http_status")) "—" else event.optInt("http_status")} · ${event.optLong("latency_ms")} ms" +
                if (!event.isNull("failure")) " · ${event.optString("failure")}" else "", 12), sheet.row(2, 12))
            divider()
        }
        shareButton(true)
    }
    private fun shareButton(usageTab: Boolean) {
        body.addView(sheet.button(activity.getString(R.string.v7_share_short), R.drawable.ic_share).apply {
            setOnClickListener {
                if (usageTab) share("usage", false)
                else MaterialAlertDialogBuilder(activity).setTitle(R.string.diag_v12_share_title)
                    .setItems(arrayOf(activity.getString(R.string.diag_v12_without_location), activity.getString(R.string.diag_v12_with_location))) { _, index ->
                        selected?.let { share(it, index == 1) }
                    }.show()
            }
        }, sheet.row(16))
    }
    private fun share(id: String, location: Boolean) {
        worker.execute {
            val result = runCatching {
                val events = Diagnostics.log(activity).export(id, location).lineSequence().filter { it.isNotBlank() }.map { JSONObject(it) }.toList()
                File(activity.cacheDir, "exports").apply { mkdirs() }.resolve("Runline-${if (id == "usage") "AI-usage" else "coach-log"}-${System.currentTimeMillis()}.json").apply {
                    writeText(JSONObject().put("coordinates_included", location).put("events", JSONArray(events)).toString(2))
                }
            }
            main.post { if (!sheet.closed) result.onSuccess { file ->
                val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.files", file)
                val intent = Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri(file.name, uri) }
                runCatching { activity.startActivity(Intent.createChooser(intent, activity.getString(R.string.v7_share_short))) }
            }.onFailure { body.addView(sheet.text(activity.getString(R.string.ai_error_storage))) } }
        }
    }
    private fun divider() { body.addView(View(activity).apply { setBackgroundColor(activity.getColor(R.color.surface_variant)) }, sheet.row(0, 12).apply { height = sheet.dp(1) }) }
    private fun time(ms: Long) = if (ms <= 0) "—" else java.text.SimpleDateFormat("HH:mm:ss", activity.resources.configuration.locales[0]).format(Date(ms))
    private fun date(ms: Long) = if (ms <= 0) "—" else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))
}
