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
    private val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
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
        if (AiWork.isReviewRunning(run.id) || AutomaticReviews.queue(activity).get(run.id)?.state == "pending") {
            if (!waitingForReview) previousReviewTime = store.get(run.id)?.createdAtMs ?: -1L
            waitingForReview = true
            progress.visibility = View.VISIBLE
            generate.isEnabled = false
            status.setText(if (AiWork.isReviewRunning(run.id)) R.string.ai_working else R.string.review_v12_waiting)
            handler.postDelayed(poll, 1000)
            return
        }
        updateAction()
        if (!waitingForReview && store.get(run.id) == null && AutomaticReviews.queue(activity).get(run.id)?.state == "failed") {
            status.setText(R.string.review_v12_retry)
        }
        if (waitingForReview) {
            waitingForReview = false
            val saved = store.get(run.id)
            renderReview(saved)
            status.setText(if (saved != null && saved.createdAtMs != previousReviewTime) R.string.ai_review_saved else R.string.review_v12_retry)
        }
    }

    private fun updateAction() {
        progress.visibility = View.GONE
        val ready = AiSettings.isReady(activity)
        generate.visibility = if (ready) View.VISIBLE else View.GONE
        generate.setText(if (store.get(run.id) != null) R.string.review_v12_refresh else R.string.ai_review_generate)
        generate.isEnabled = ready
    }

    private fun stopObserving() {
        progress.visibility = View.GONE
        handler.removeCallbacks(poll)
        activity.lifecycle.removeObserver(lifecycleObserver)
    }

    private fun renderReview(review: RunAiReview?) {
        body.removeAllViews()
        if (review == null) { body.addView(sheet.text(activity.getString(R.string.ai_review_empty))); return }
        val card = review.card
        if (card == null) body.addView(sheet.text(review.text)) else {
            body.addView(sheet.text(card.headline, 26, true), sheet.row(0, 20))
            val distance = com.runline.app.core.RunMetrics.distanceMeters(run.points) / 1000
            body.addView(sheet.text(activity.getString(R.string.review_v12_distance, distance), 14), sheet.row(0, 24))
            fun section(title: Int, text: String, icon: Int, emphasis: Boolean = false) {
                val group = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    if (emphasis) {
                        setPadding(sheet.dp(16), sheet.dp(16), sheet.dp(16), sheet.dp(16))
                        background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(activity.getColor(R.color.surface_variant)); cornerRadius = sheet.dp(16).toFloat()
                        }
                    }
                }
                val label = sheet.text(activity.getString(title), 16, true).apply {
                    compoundDrawablePadding = sheet.dp(8)
                    setCompoundDrawablesRelative(androidx.appcompat.content.res.AppCompatResources.getDrawable(activity, icon)!!.mutate().apply {
                        setTint(activity.getColor(R.color.on_surface)); setBounds(0, 0, sheet.dp(22), sheet.dp(22))
                    }, null, null, null)
                }
                group.addView(label, sheet.row(0, 8)); group.addView(sheet.text(text, 17))
                body.addView(group, sheet.row(0, 24))
            }
            section(R.string.review_v12_win, card.win, R.drawable.ic_check)
            section(R.string.review_v12_focus, card.focus, R.drawable.ic_effort)
            section(R.string.review_v12_next, card.nextSession, R.drawable.ic_route, true)
        }
        body.addView(sheet.text(review.model + " · " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(review.createdAtMs)), 12), sheet.row(0, 8))
    }
}
