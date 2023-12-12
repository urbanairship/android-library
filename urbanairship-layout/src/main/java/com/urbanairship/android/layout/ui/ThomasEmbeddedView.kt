package com.urbanairship.android.layout.ui

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.view.ViewGroup
import androidx.annotation.AnimatorRes
import androidx.annotation.MainThread
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.android.layout.property.ConstrainedSize
import com.urbanairship.android.layout.property.HorizontalPosition
import com.urbanairship.android.layout.property.Position
import com.urbanairship.android.layout.property.VerticalPosition
import com.urbanairship.android.layout.util.ConstraintSetBuilder
import com.urbanairship.android.layout.widget.ConstrainedFrameLayout

internal class ThomasEmbeddedView(
    context: Context,
    private val model: AnyModel,
    private val environment: ViewEnvironment
) : ConstraintLayout(context) {

    private var frame: ConstrainedFrameLayout? = null

    // TODO: view attrs?
    @AnimatorRes
    internal var animationIn = android.R.animator.fade_in

    @AnimatorRes
    internal var animationOut = android.R.animator.fade_out

    internal var listener: Listener? = null

    /**
     * Banner view listener.
     */
    interface Listener {
        /**
         * Called when a child view was dismissed from a swipe. It is up to the listener to remove
         * or hide the view from the parent.
         */
        fun onDismissed()
    }

    init {
        id = model.viewId
        configure()
    }

    private fun configure() {
        // HACK: make the layout fill the embedded view size, regardless of what may be set in the
        // modal presentation. We will probably want to add a new presentation type for embedded?
        val size = ConstrainedSize("100%", "100%", null, null, null, null)
        val position = Position(HorizontalPosition.CENTER, VerticalPosition.CENTER)
        val margin = null

        val containerView = model.createView(context, environment)

        val viewId = makeFrame(size).let { f ->
            this@ThomasEmbeddedView.frame = f

            f.addView(containerView)
            this@ThomasEmbeddedView.addView(f)

            f.id
        }

        ConstraintSetBuilder.newBuilder(context)
            .position(position, viewId)
            .size(size, viewId)
            .margin(margin, viewId)
            .build()
            .applyTo(this)
    }

    private fun makeFrame(size: ConstrainedSize) =
        ConstrainedFrameLayout(context, size).apply {
            id = generateViewId()
            layoutParams = LayoutParams(MATCH_CONSTRAINT, MATCH_CONSTRAINT)
        }

    @MainThread
    fun showAnimated() {
        if (animationIn != 0) {
            clearAnimation()
            val animator = AnimatorInflater.loadAnimator(context, animationIn)
            animator.setDuration(300)
            animator.setTarget(frame)
            animator.start()
        }
    }

    @MainThread
    fun dismissAnimated() {
        dismiss(animate = true, isInternal = false)
    }

    /**
     * Used to dismiss the message.
     *
     * @param animate `true` to animate the view out, otherwise `false`.
     */
    @MainThread
    fun dismiss(animate: Boolean, isInternal: Boolean) {
        if (animate && frame != null && animationOut != 0) {
            clearAnimation()
            val animator = AnimatorInflater.loadAnimator(context, animationOut)
            animator.setDuration(300)
            animator.setTarget(frame)
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    removeSelf()
                    if (!isInternal) {
                        listener?.onDismissed()
                    }
                }
            })
            animator.start()
        } else {
            removeSelf()

            if (!isInternal) {
                listener?.onDismissed()
            }
        }
    }

    /**
     * Helper method to remove the view from the parent.
     */
    @MainThread
    private fun removeSelf() {
        (this.parent as? ViewGroup)?.let { parent ->
            parent.removeView(this)
            frame = null
        }
    }
}
