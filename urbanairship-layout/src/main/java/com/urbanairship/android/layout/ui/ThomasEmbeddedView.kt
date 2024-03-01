package com.urbanairship.android.layout.ui

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.AnimatorRes
import androidx.annotation.MainThread
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
import com.urbanairship.android.layout.EmbeddedPresentation
import com.urbanairship.android.layout.R
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.android.layout.property.ConstrainedSize
import com.urbanairship.android.layout.property.Margin
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.util.ConstraintSetBuilder
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.widget.ClippableFrameLayout
import com.urbanairship.android.layout.widget.ConstrainedFrameLayout

internal class ThomasEmbeddedView(
    context: Context,
    private val model: AnyModel,
    private val presentation: EmbeddedPresentation,
    private val environment: ViewEnvironment,
    @AnimatorRes private val animationIn: Int = android.R.animator.fade_in,
    @AnimatorRes private val animationOut: Int = android.R.animator.fade_out,
) : ConstraintLayout(context) {

    private var frame: ConstrainedFrameLayout? = null

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
        val margin = placement.margin

        val widthSpec = when (size.width.type) {
            Size.DimensionType.AUTO -> FrameLayout.LayoutParams.WRAP_CONTENT
            else -> FrameLayout.LayoutParams.MATCH_PARENT
        }
        val heightSpec = when (size.height.type) {
            Size.DimensionType.AUTO -> FrameLayout.LayoutParams.WRAP_CONTENT
            else -> FrameLayout.LayoutParams.MATCH_PARENT
        }

        // Layout container (Frame -> Container -> Model view)
        val container = ClippableFrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                widthSpec,
                heightSpec
            )
        }

        // Make a frame to display the layout in
        val viewId = makeFrame(size, margin).let { frame ->
            this@ThomasEmbeddedView.frame = frame

            LayoutUtils.applyBorderAndBackground(container, placement.border, placement.backgroundColor)

            container.addView(model.createView(context, environment))
            frame.addView(container)

            this@ThomasEmbeddedView.addView(frame)

            frame.id
        }

        // Apply constraints
        ConstraintSetBuilder.newBuilder(context)
            .constrainWithinParent(viewId, margin)
            .size(size, viewId)
            .build()
            .applyTo(this)
    }

    private fun makeFrame(size: ConstrainedSize, margin: Margin?) =
        ConstrainedFrameLayout(context, size).apply {
            id = generateViewId()
            layoutParams = LayoutParams(MATCH_CONSTRAINT, MATCH_CONSTRAINT)
        }

    @MainThread
    fun show(animate: Boolean) {
        if (animate && animationIn != 0) {
            clearAnimation()
            val animator = AnimatorInflater.loadAnimator(context, animationIn)
            animator.setDuration(300)
            animator.setTarget(frame)
            animator.start()
        } else {
            frame?.alpha = 1f
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
