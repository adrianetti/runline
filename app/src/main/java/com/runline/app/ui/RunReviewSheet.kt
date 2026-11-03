package com.runline.app.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.Gravity
import android.widget.LinearLayout
import com.google.android.material.progressindicator.CircularProgressIndicator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.runline.app.R
import com.runline.app.ai.*
import com.runline.app.core.RunSession
import java.text.DateFormat
import java.util.Date

class RunReviewSheet(private val activity: AppCompatActivity, private val run: RunSession, state: Bundle? = null) {
    private val sheet = AiSheet(activity, R.string.ai_review_title)
    private val body = sheet.text("")
    private val status = sheet.text("")
    private val progress = CircularProgressIndicator(activity).apply {
        isIndeterminate = true
        indicatorSize = sheet.dp(24); trackThickness = sheet.dp(2)
        setIndicatorColor(activity.getColor(R.color.primary))
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = View.GONE
    }
    private val store = AiReviewStore(activity)
    private val handler = Handler(Looper.getMainLooper())
    private val generate = sheet.button(activity.getString(R.string.ai_review_generate), R.drawable.ic_speaker, true)
    private var waitingForReview = state?.getBoolean("pendingReview") ?: false
    private var previousReviewTime = state?.getLong("previousReviewTime", -1L) ?: -1L
    private val poll = object : Runnable {
        override fun run() { observeReview() }
    }
    private val lifecycleObserver = LifecycleEventObserver { _, event ->
        when (event) {
            Lifecycle.Event.ON_START -> observeReview()
            Lifecycle.Event.ON_STOP -> handler.removeCallbacks(poll)
            else -> Unit
        }
    }
    val isShowing get() = sheet.isShowing
    init {
        sheet.column.addView(sheet.text(activity.getString(R.string.v8_review_cost)), sheet.row(0, 16))
        sheet.column.addView(body, sheet.row(0, 16))
        renderReview(store.get(run.id))
        sheet.column.addView(generate.apply { setOnClickListener {
            if (!AiSettings.isReady(activity)) { status.setText(R.string.ai_need_setup); return@setOnClickListener }
            if (AiWork.isReviewRunning(run.id)) { observeReview(); return@setOnClickListener }
            previousReviewTime = store.get(run.id)?.createdAtMs ?: -1L
            waitingForReview = true
            isEnabled = false; status.setText(R.string.ai_working)
            AiWork.review(activity, run) { result -> if (!sheet.closed) {
                waitingForReview = false
                handler.removeCallbacks(poll)
                updateAction()
                result.onSuccess { renderReview(it); status.setText(R.string.ai_review_saved) }
                    .onFailure { status.text = sheet.error(it) }
            } }
            observeReview()
        } }, sheet.row())
        status.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        sheet.column.addView(LinearLayout(activity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(progress, LinearLayout.LayoutParams(sheet.dp(32), sheet.dp(32)).apply { marginEnd = sheet.dp(12) })
            addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        }, sheet.row(8))
        sheet.dialog.setOnDismissListener { sheet.closed = true; stopObserving() }
        activity.lifecycle.addObserver(lifecycleObserver)
    }
    fun show() { sheet.show(); observeReview() }
    fun close() { stopObserving(); sheet.close() }
    fun saveState() = Bundle().apply {
        putBoolean("pendingReview", waitingForReview)
        putLong("previousReviewTime", previousReviewTime)
    }

    private fun observeReview() {
        handler.removeCallbacks(poll)
        if (sheet.closed || !isShowing || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if (AiWork.isReviewRunning(run.id)) {
            if (!waitingForReview) previousReviewTime = store.get(run.id)?.createdAtMs ?: -1L
            waitingForReview = true
            progress.visibility = View.VISIBLE
            generate.isEnabled = false
            status.setText(R.string.ai_working)
            handler.postDelayed(poll, 750)
            return
        }
        updateAction()
        if (waitingForReview) {
            waitingForReview = false
            val saved = store.get(run.id)
            renderReview(saved)
            status.setText(if (saved != null && saved.createdAtMs != previousReviewTime) R.string.ai_review_saved else R.string.ai_error_response)
        }
    }

    private fun updateAction() {
        progress.visibility = View.GONE
        val ready = AiSettings.isReady(activity)
        generate.visibility = if (ready) View.VISIBLE else View.GONE
        generate.isEnabled = ready
    }

    private fun stopObserving() {
        progress.visibility = View.GONE
        handler.removeCallbacks(poll)
        activity.lifecycle.removeObserver(lifecycleObserver)
    }

    private fun renderReview(review: RunAiReview?) {
        if (review == null) body.setText(R.string.ai_review_empty)
        else body.text = review.text + "\n\n" + review.model + " · " +
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(review.createdAtMs))
    }
}
