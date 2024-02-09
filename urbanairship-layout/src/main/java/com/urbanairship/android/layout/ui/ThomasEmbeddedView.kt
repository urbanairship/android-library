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
import com.urbanairship.android.layout.EmbeddedPresentation
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.android.layout.property.ConstrainedSize
import com.urbanairship.android.layout.util.ConstraintSetBuilder
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.widget.ConstrainedFrameLayout

internal class ThomasEmbeddedView(
    context: Context,
    private val model: AnyModel,
    private val presentation: EmbeddedPresentation,
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
     * Embedded view listener.
     */
    interface Listener {
        /**
         * Called when an embedded child view was dismissed.
         */
        fun onDismissed()
    }

    init {
        id = model.viewId
        configure()
    }

    private fun configure() {
        // Determine embedded view placement
        val placement = presentation.getResolvedPlacement(context)
        val size = placement.size
        val position = placement.position
        val margin = placement.margin

        // Create the layout view
        val layoutView = model.createView(context, environment)

        // Make a frame to display the layout in
        val viewId = makeFrame(size).let { f ->
            this@ThomasEmbeddedView.frame = f

            LayoutUtils.applyBorderAndBackground(f, placement.border, placement.backgroundColor)

            f.addView(layoutView)
            this@ThomasEmbeddedView.addView(f)

            f.id
        }

        // Apply constraints
        ConstraintSetBuilder.newBuilder(context)
            .constrainWithinParent(viewId)
            .size(size, viewId)
            .margin(margin, viewId)
            .position(position, viewId)
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
