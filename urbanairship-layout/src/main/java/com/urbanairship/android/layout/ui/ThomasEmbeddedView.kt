package com.urbanairship.android.layout.ui

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.annotation.MainThread
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
import com.urbanairship.android.layout.environment.EnvironmentFrameTracker
import com.urbanairship.android.layout.EmbeddedPresentation
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.android.layout.property.EmbeddedPlacement
import com.urbanairship.android.layout.property.Size
import com.urbanairship.android.layout.util.ConstraintSetBuilder
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.util.WindowPixels
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

    /** Reports this view's bounds as `$environment.frame`: the space the host gives the scene. */
    private val environmentFrame = EnvironmentFrameTracker { frame, isFirst ->
        model.publishEnvironment(this, frame, immediate = isFirst)
    }

    /** The placement shown, which the layout's environment conditions can select too. */
    internal lateinit var placement: EmbeddedPlacement
        private set

    /** The scene, created once and moved into a new frame when the placement changes. */
    private var contentView: View

    /** The window [applyConstraints] last sized the frame against. */
    private var constrainedWindow: WindowPixels? = null

    init {
        id = model.viewId
        model.seedEnvironment(this)
        placement = resolvePlacement()
        contentView = model.createView(context, environment, null)
        configure(placement)
        EnvironmentConditionsWatcher(this, model) { onEnvironmentConditionsChanged() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        environmentFrame.onMeasure(widthMeasureSpec, heightMeasureSpec)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        environmentFrame.onLayout(right - left, bottom - top)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Below API 30 the window is only readable once laid out, after init's constraints.
        post { reapplyConstraintsIfWindowChanged() }
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        // A host that handles its own config changes keeps this view, so selectors re-resolve here.
        val resolved = resolvePlacement()
        if (resolved !== placement) {
            placement = resolved
            frame?.let(::removeView)
            (contentView.parent as? ViewGroup)?.removeView(contentView)
            configure(resolved)
        } else {
            reapplyConstraintsIfWindowChanged()
        }
        environmentFrame.onConfigurationChanged()
        requestLayout()
    }

    private fun resolvePlacement(): EmbeddedPlacement =
        presentation.getResolvedPlacement(context, model.environmentConditionValues)

    /**
     * Moves the scene into a new frame when the new values select another placement, building its
     * views again first when its selectors read them.
     */
    private fun onEnvironmentConditionsChanged() {
        val resolved = resolvePlacement()
        val rebuild = model.viewsSelectOnEnvironmentConditions
        if (!rebuild && resolved === placement) return
        placement = resolved
        frame?.let(::removeView)
        (contentView.parent as? ViewGroup)?.removeView(contentView)
        if (rebuild) contentView = model.createView(context, environment, null)
        configure(resolved)
    }

    private fun reapplyConstraintsIfWindowChanged() {
        if (ResourceUtils.getWindowPixels(context, this) != constrainedWindow) applyConstraints()
    }

    private fun configure(placement: EmbeddedPlacement) {
        val size = placement.size

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
        ConstrainedFrameLayout(context, size).apply {
            id = generateViewId()
            layoutParams = LayoutParams(MATCH_CONSTRAINT, MATCH_CONSTRAINT)
        }.let { frame ->
            this@ThomasEmbeddedView.frame = frame

            LayoutUtils.applyBorderAndBackground(container, null, placement.border, placement.backgroundColor)

            container.addView(contentView)
            frame.addView(container)

            addView(frame)
        }

        applyConstraints()
    }

    /** Sizes and positions the frame for [placement] against the current window. */
    private fun applyConstraints() {
        val viewId = frame?.id ?: return
        val size = placement.size
        val margin = placement.margin
        constrainedWindow = ResourceUtils.getWindowPixels(context, this)

        ConstraintSetBuilder.newBuilder(context, this)
            .constrainWithinParent(viewId, margin)
            .apply {
                if (fillHeight && fillWidth) {
                    matchConstraintWidth(viewId).matchConstraintHeight(viewId)
                } else if (fillHeight) {
                    width(size, viewId).matchConstraintHeight(viewId).aspectRatio(size, viewId)
                } else if (fillWidth) {
                    matchConstraintWidth(viewId).height(size, viewId).aspectRatio(size, viewId)
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
