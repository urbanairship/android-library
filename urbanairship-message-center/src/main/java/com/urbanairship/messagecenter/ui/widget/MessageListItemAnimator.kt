package com.urbanairship.messagecenter.ui.widget

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorSet
import android.content.Context
import android.view.View
import androidx.annotation.AnimatorRes
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import androidx.core.view.isVisible
import com.urbanairship.messagecenter.R

/** Wrapper for animating transitions between normal and editing states of a [MessageListItem]. */
internal class MessageListItemAnimator(
    private val context: Context,
    private val unreadContainer: View,
    private val checkable: View,
) {
    private val flipRightOut: Animator by lazy {
        loadAnimator(R.animator.flip_right_out).apply { setTarget(unreadContainer) }
    }
    private val flipLeftIn: Animator by lazy {
        loadAnimator(R.animator.flip_left_in).apply { setTarget(unreadContainer) }
    }
    private val flipRightIn: Animator by lazy {
        loadAnimator(R.animator.flip_right_in).apply { setTarget(checkable) }
    }
    private val flipLeftOut: Animator by lazy {
        loadAnimator(R.animator.flip_left_out).apply { setTarget(checkable) }
    }
    private val fadeIn: Animator by lazy {
        loadAnimator(R.animator.fade_in).apply { setTarget(checkable) }
    }
    private val fadeOut: Animator by lazy {
        loadAnimator(R.animator.fade_out).apply { setTarget(checkable) }
    }

    /**
     * Animator set for transitioning from normal to editing state.
     *
     * This set animates:
     * - The unreadContainer flipping out to the right
     * - The checkbox flipping in from the right
     * - The checkbox fading in at the halfway point of the flip animation
     */
    private val editingAnimatorSet = AnimatorSet().apply {
        playTogether(flipRightIn, flipRightOut, fadeIn)

        doOnStart {
            checkable.alpha = 0f
            checkable.isVisible = true
        }
    }

    /**
     * Animator set for transitioning from editing to normal state.
     *
     * This set animates:
     * - The checkbox flipping out to the left
     * - The checkbox fading out at the halfway point of the flip animation
     * - The unread container view flipping in from the left
     */
    private val notEditingAnimatorSet = AnimatorSet().apply {
        playTogether(flipLeftIn, flipLeftOut, fadeOut)

        doOnEnd {
            checkable.isVisible = false
        }
    }

    /**
     * Animates a transition between normal and editing states, based on the value of [isEditing].
     */
    fun animateEditMode(isEditing: Boolean) {
        unreadContainer.isVisible = true

        if (isEditing) {
            editingAnimatorSet.start()
        } else {
            notEditingAnimatorSet.start()
        }
    }

    /** Helper to load an animator resource. */
    private fun loadAnimator(@AnimatorRes animatorRes: Int): Animator =
        AnimatorInflater.loadAnimator(context, animatorRes)
}
