package com.runline.app.ui

import android.animation.ValueAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator

internal object UiMotion {
    private const val EASE = 2f
    private val enabled get() = ValueAnimator.areAnimatorsEnabled()

    fun slide(
        view: View,
        direction: Int,
    ) {
        if (!enabled) return
        view.animate().cancel()
        view.translationX = UiText.dp(view.context, Motion.ENTER_OFFSET_DP).toFloat() * direction
        view.alpha = 0f
        settle(view, Motion.STANDARD_MS)
    }

    fun rise(view: View) {
        if (!enabled) return
        view.animate().cancel()
        view.translationY = UiText.dp(view.context, Motion.ENTER_OFFSET_DP).toFloat()
        view.alpha = 0f
        settle(view, Motion.EMPHASIS_MS)
    }

    fun grow(
        view: View,
        update: (Float) -> Unit,
    ): ValueAnimator? {
        if (!enabled) {
            update(1f)
            return null
        }
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = Motion.EMPHASIS_MS
            interpolator = DecelerateInterpolator(EASE)
            addUpdateListener {
                update(it.animatedValue as Float)
                view.invalidate()
            }
            start()
        }
    }

    private fun settle(
        view: View,
        duration: Long,
    ) {
        view
            .animate()
            .translationX(0f)
            .translationY(0f)
            .alpha(1f)
            .setDuration(duration)
            .setInterpolator(DecelerateInterpolator(EASE))
            .start()
    }
}
