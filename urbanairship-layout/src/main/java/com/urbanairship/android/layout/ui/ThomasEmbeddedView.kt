package com.urbanairship.android.layout.ui

import android.content.Context
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
import com.urbanairship.android.layout.EmbeddedPresentation
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.util.ConstraintSetBuilder
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.widget.ClippableFrameLayout
import com.urbanairship.android.layout.widget.ConstrainedFrameLayout

internal class ThomasEmbeddedView(
    context: Context,
    private val model: AnyModel,
    private val presentation: EmbeddedPresentation,
    private val environment: ViewEnvironment,
    private val fillHeight: Boolean = false,
    private val fillWidth: Boolean = false
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
        val viewId = ConstrainedFrameLayout(context, size).apply {
            id = generateViewId()
            layoutParams = LayoutParams(MATCH_CONSTRAINT, MATCH_CONSTRAINT)
        }.let { frame ->
            this@ThomasEmbeddedView.frame = frame

            LayoutUtils.applyBorderAndBackground(container, null, placement.border, placement.backgroundColor)

            container.addView(model.createView(context, environment, null))
            frame.addView(container)

            addView(frame)

            frame.id
        }

        // Apply constraints
        ConstraintSetBuilder.newBuilder(context)
            .constrainWithinParent(viewId, margin)
            .apply {
                if (fillHeight && fillWidth) {
                    matchConstraintWidth(viewId).matchConstraintHeight(viewId)
                } else if (fillHeight) {
                    width(size, viewId).matchConstraintHeight(viewId)
                } else if (fillWidth) {
                    matchConstraintWidth(viewId).height(size, viewId)
                } else {
                    size(size, viewId)
                }
            }
            .build()
            .applyTo(this)
    }

    @MainThread
    fun dismiss(isInternal: Boolean) {
        removeSelf()

        if (!isInternal) {
            listener?.onDismissed()
        }
    }

    @MainThread
    private fun removeSelf() {
        (this.parent as? ViewGroup)?.let { parent ->
            parent.removeView(this)
            frame = null
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        val inputManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputManager.hideSoftInputFromWindow(windowToken, InputMethodManager.HIDE_NOT_ALWAYS)
    }
}
