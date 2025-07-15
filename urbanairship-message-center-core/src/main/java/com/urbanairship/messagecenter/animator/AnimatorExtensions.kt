package com.urbanairship.messagecenter.animator

import android.R
import android.animation.ObjectAnimator
import android.view.View
import android.view.ViewPropertyAnimator
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.view.isVisible

public val View.shortAnimTime: Long
    get() = resources.getInteger(R.integer.config_shortAnimTime).toLong()

public val View.slideInBottomAnimator: ObjectAnimator
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

public val View.slideOutBottomAnimator: ObjectAnimator
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

public fun View.animateFadeIn(
    duration: Long = shortAnimTime
): ViewPropertyAnimator = animate().alpha(1f).setDuration(duration)

public fun View.animateFadeOut(
    duration: Long = shortAnimTime
): ViewPropertyAnimator = animate().alpha(0f).setDuration(duration)
