package com.runline.app.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.runline.app.R
import com.runline.app.ai.*
import com.runline.app.core.RunSession
import java.text.DateFormat
import java.util.Date

class RunReviewSheet(private val activity: AppCompatActivity, private val run: RunSession, state: Bundle? = null) {
    private val sheet = AiSheet(activity, R.string.ai_review_title)
    private val body = sheet.text("")
    private val status = sheet.text("")
    private var settings: AiSettingsSheet? = null
    val isShowing get() = sheet.isShowing
    init {
        sheet.column.addView(sheet.text(activity.getString(R.string.ai_review_disclosure)), sheet.row(0, 16))
        sheet.column.addView(body, sheet.row(0, 16))
        AiReviewStore(activity).get(run.id)?.let { body.text = it.text + "\n\n" + it.model + " · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it.createdAtMs)) }
            ?: run { body.setText(R.string.ai_review_empty) }
        sheet.column.addView(sheet.button(activity.getString(R.string.ai_review_generate), R.drawable.ic_speaker, true).apply { setOnClickListener {
            if (!AiSettings.load(activity).enabled || !AiSettings.hasKey(activity)) { status.setText(R.string.ai_need_setup); return@setOnClickListener }
            isEnabled = false; status.setText(R.string.ai_working)
            AiWork.review(activity, run) { result -> if (!sheet.closed) {
                isEnabled = true
                result.onSuccess { body.text = it.text + "\n\n" + it.model; status.setText(R.string.ai_review_saved) }
                    .onFailure { status.text = sheet.error(it) }
            } }
        } }, sheet.row())
        sheet.column.addView(sheet.button(activity.getString(R.string.ai_title), R.drawable.ic_settings).apply { setOnClickListener { settings?.close(); settings = AiSettingsSheet(activity).also { it.show() } } }, sheet.row())
        sheet.column.addView(status, sheet.row(8))
    }
    fun show() = sheet.show()
    fun close() { settings?.close(); sheet.close() }
    fun saveState() = Bundle()
}
