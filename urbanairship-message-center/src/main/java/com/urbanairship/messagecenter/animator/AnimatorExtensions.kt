package com.urbanairship.messagecenter.animator

import android.animation.ObjectAnimator
import android.view.View
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.view.isVisible

private val View.shortAnimTime
    get() = resources.getInteger(android.R.integer.config_shortAnimTime).toLong()

internal val View.slideInBottomAnimator: ObjectAnimator
    get() = ObjectAnimator.ofFloat(
        this,
        View.TRANSLATION_Y,
        height.toFloat(),
        0f
    ).apply {
        doOnStart {
            translationY = height.toFloat()
            isVisible = true
        }
    }

internal val View.slideOutBottomAnimator: ObjectAnimator
    get() = ObjectAnimator.ofFloat(
        this,
        View.TRANSLATION_Y,
        0f,
        height.toFloat()
    ).apply {
        doOnStart {
            translationY = 0f
            isVisible = true
        }
        doOnEnd {
            isVisible = false
        }
    }

internal fun View.animateFadeIn(
    duration: Long = shortAnimTime
) = animate().alpha(1f).setDuration(duration)

internal fun View.animateFadeOut(
    duration: Long = shortAnimTime
) = animate().alpha(0f).setDuration(duration)
