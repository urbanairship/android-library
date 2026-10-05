/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.property.EnvironmentConditionsContext
import com.urbanairship.android.layout.ui.EnvironmentConditionsWatcher
import android.content.Context
import android.content.res.Configuration
import android.graphics.Rect
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT
import androidx.core.view.ViewCompat
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import androidx.core.view.WindowInsetsCompat
import com.urbanairship.android.layout.environment.EnvironmentFrameTracker
import com.urbanairship.android.layout.ModalPresentation
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.android.layout.property.ModalPlacement
import com.urbanairship.android.layout.util.ConstraintSetBuilder
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.util.WindowPixels
import com.urbanairship.android.layout.widget.ClippableFrameLayout
import com.urbanairship.android.layout.widget.ConstrainedFrameLayout

internal class ModalView(
    context: Context,
    private val model: AnyModel,
    private val presentation: ModalPresentation,
    viewEnvironment: ViewEnvironment
) : ConstraintLayout(context) {

    private val windowTouchSlop by lazy {
        ViewConfiguration.get(context).scaledWindowTouchSlop
    }

    private val modalFrame: ViewGroup

    /** The modal frame. Exposed so the activity can scope the window transition to it. */
    internal val frameView: View get() = modalFrame

    /**
     * Full-bleed scrim drawn behind the modal frame. Exposed so the window transition can
     * fade it independently of the modal frame's slide/explode.
     */
    internal val shadeView: View

    /** What the placement's colors resolve against, so their selectors see the layout's environment conditions. */
    private val conditionsContext = EnvironmentConditionsContext.wrap(context) { model.environmentConditionValues }

    private val placement: ModalPlacement

    /**
     * Called once the layout's environment conditions select a different placement; this view stops
     * applying and reporting [placement] from then on, and the activity recreates.
     */
    var onPlacementSuperseded: (() -> Unit)? = null


    /**
     * Set once a configuration change resolves a different placement. `ModalActivity` recreates
     * for that, but asynchronously, so until then this view stops applying and reporting the old one.
     */
    private var isPlacementSuperseded = false

    /** The window [applyConstraints] last sized the frame against. */
    private var constrainedWindow: WindowPixels? = null

    /** The modal frame's min height as declared by the layout (e.g. `min_height: 100%`). */
    private var initialModalFrameMinHeight: Int = 0

    private var lastAppliedImeBottom: Int = 0

    private var clickOutsideListener: OnClickListener? = null

    /** Reports this view's bounds as `$environment.frame`: the space the host gives the scene. */
    private val environmentFrame = EnvironmentFrameTracker { frame, isFirst ->
        model.publishEnvironment(this, frame, immediate = isFirst)
    }

    init {
        model.seedEnvironment(this)
        placement = resolvePlacement()
        clipToPadding = false

        val size = placement.size
        val margin = placement.margin
        modalFrame = ConstrainedFrameLayout(conditionsContext, size).apply {
            id = generateViewId()
            layoutParams = LayoutParams(MATCH_CONSTRAINT, MATCH_CONSTRAINT).apply {
                margin?.let {
                    setMargins(
                        ResourceUtils.dpToPx(context, it.start).toInt(),
                        ResourceUtils.dpToPx(context, it.top).toInt(),
                        ResourceUtils.dpToPx(context, it.end).toInt(),
                        ResourceUtils.dpToPx(context, it.bottom).toInt()
                    )
                }
            }
            clipChildren = false
            clipToPadding = false
            outlineProvider = ViewOutlineProvider.BOUNDS

            LayoutUtils.applyBorderAndBackground(this,null, placement.border, placement.backgroundColor)
        }

        val containerView = ClippableFrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)

            placement.border
                ?.innerRadii { ResourceUtils.dpToPx(context, it) }
                ?.let {
                    setClipPathBorderRadius(it)
                }
        }

        containerView.addView(model.createView(context, viewEnvironment, null))

        modalFrame.addView(containerView)
        addView(modalFrame)

        shadeView = View(conditionsContext).apply {
            layoutParams = LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        addView(shadeView, 0)
        applyShadeAndShadow()

        applyConstraints()
        EnvironmentConditionsWatcher(this, model) { onEnvironmentConditionsChanged() }

        if (viewEnvironment.isIgnoringSafeAreas) {
            setOnApplyWindowInsetsListener(modalFrame) { _: View, insets: WindowInsetsCompat ->
                ViewCompat.dispatchApplyWindowInsets(containerView, insets)
            }
        }
    }

    /** Sizes and positions the modal frame against the current window. */
    private fun applyConstraints() {
        val size = placement.size
        val margin = placement.margin
        val viewId = modalFrame.id
        val ignoreSafeArea = placement.shouldIgnoreSafeArea()

        // Percent base: the full window (matches ConstraintLayout's constrainPercent*).
        val window = ResourceUtils.getWindowPixels(context, this)
        constrainedWindow = window
        val windowWidthPx = window.width(ignoreSafeArea)
        val windowHeightPx = window.height(ignoreSafeArea)

        // Overflow bound / fitted-size target: window minus the frame's outer margins.
        val horizontalMargin = (margin?.start ?: 0) + (margin?.end ?: 0)
        val verticalMargin = (margin?.top ?: 0) + (margin?.bottom ?: 0)
        val horizontalMarginPx = ResourceUtils.dpToPx(context, horizontalMargin).toInt()
        val verticalMarginPx = ResourceUtils.dpToPx(context, verticalMargin).toInt()

        val availableWidthPx = (windowWidthPx - horizontalMarginPx).coerceAtLeast(0)
        val availableHeightPx = (windowHeightPx - verticalMarginPx).coerceAtLeast(0)

        val constraints = ConstraintSetBuilder.newBuilder(context, this)
            .constrainWithinParent(viewId)
            .width(size, ignoreSafeArea, viewId, margin = margin)
            .height(size, ignoreSafeArea, viewId, margin = margin)
            .aspectRatioWithinBounds(
                size = size,
                viewId = viewId,
                windowWidthPx = windowWidthPx,
                windowHeightPx = windowHeightPx,
                availableWidthPx = availableWidthPx,
                availableHeightPx = availableHeightPx,
                ignoreSafeArea = ignoreSafeArea
            )
            .position(placement.position, viewId)
            // applyTo overwrites the frame's layout-param margins with the set's, so they go in the set too.
            .margin(margin, viewId)
            .build()

        constraints.applyTo(this)
        initialModalFrameMinHeight =
            constraints.getConstraint(modalFrame.id).layout.heightMin.coerceAtLeast(0)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        // Lay the shade out over the full view, ignoring the system-bar padding, so it dims
        // edge-to-edge. ConstraintLayout still insets the modal frame within the padding.
        shadeView.layout(0, 0, right - left, bottom - top)
        if (!isPlacementSuperseded) environmentFrame.onLayout(right - left, bottom - top)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        environmentFrame.onMeasure(widthMeasureSpec, heightMeasureSpec)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    override fun isOpaque(): Boolean = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN ->
                return true
            MotionEvent.ACTION_UP ->
                if (isTouchOutside(event) && clickOutsideListener != null) {
                    clickOutsideListener?.onClick(this)
                    return true
                }
        }
        return super.onTouchEvent(event)
    }

    fun setOnClickOutsideListener(listener: OnClickListener?) {
        clickOutsideListener = listener
    }

    /**
     * Called by [ModalActivity] when the IME bottom inset changes.
     *
     * The activity already accounts for the IME by adding it to the ModalView's bottom
     * padding, so we don't need to move the modal frame ourselves here. What we *do*
     * need to do is prevent the frame's declared `min_height` (e.g. `100%`) from
     * forcing the MATCH_CONSTRAINT height to overflow the shrunken inner area. We
     * clamp `matchConstraintMinHeight` directly on the LayoutParams so the change
     * lands in the current measure pass without relying on a ConstraintSet
     * clone/applyTo round-trip (which wasn't reliably round-tripping the override).
     */
    internal fun applyKeyboardInset(imeBottom: Int) {
        if (lastAppliedImeBottom == imeBottom) return
        lastAppliedImeBottom = imeBottom

        clampModalFrameMinHeight()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // On API < 28 without ignoreSafeArea, adjustResize shrinks this view when the IME
        // shows, but onApplyWindowInsets is not re-dispatched. Re-clamp here so the modal
        // frame's min height tracks the resized window.
        post {
            if (isPlacementSuperseded) return@post
            // Below API 30 the window is only readable once laid out, after init's constraints.
            if (ResourceUtils.getWindowPixels(context, this) != constrainedWindow) applyConstraints()
            clampModalFrameMinHeight()
        }
    }

    private fun clampModalFrameMinHeight() {
        val lp = modalFrame.layoutParams as? LayoutParams ?: return
        if (height <= 0) return

        // The space available to the modal frame inside the ModalView (after padding
        // and the frame's own margins). When the IME is up, ModalView.paddingBottom
        // already includes the IME height, so this naturally shrinks.
        val availableHeight =
            height - paddingTop - paddingBottom - lp.topMargin - lp.bottomMargin

        val newMinHeight = when {
            availableHeight <= 0 -> initialModalFrameMinHeight
            availableHeight < initialModalFrameMinHeight -> availableHeight
            else -> initialModalFrameMinHeight
        }

        if (lp.matchConstraintMinHeight != newMinHeight) {
            lp.matchConstraintMinHeight = newMinHeight
            modalFrame.layoutParams = lp
        }
    }

    private fun applyShadow(view: View, color: Int, elevation: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            view.outlineAmbientShadowColor = color
            view.outlineSpotShadowColor = color
        }
        view.elevation = ResourceUtils.dpToPx(context, elevation.toInt())
    }

    private fun isTouchOutside(event: MotionEvent): Boolean {
        // Get the bounds of the modal
        val r = Rect()
        modalFrame.getHitRect(r)
        // Expand the bounds by the amount of slop needed to be considered an outside touch
        r.inset(-windowTouchSlop, -windowTouchSlop)
        return !r.contains(event.x.toInt(), event.y.toInt())
    }

    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        if (isPlacementSuperseded) return
        if (resolvePlacement() !== placement) {
            isPlacementSuperseded = true
            return
        }
        applyConstraints()
        clampModalFrameMinHeight()
        environmentFrame.onConfigurationChanged()
        requestLayout()
    }

    private fun resolvePlacement(): ModalPlacement =
        presentation.getResolvedPlacement(context, model.environmentConditionValues)

    /** Resolves the shade and the frame's shadow colors, against the layout's environment conditions now. */
    private fun applyShadeAndShadow() {
        placement.shadeColor?.let { shadeView.setBackgroundColor(it.resolve(conditionsContext)) }
        placement.shadow?.androidShadow?.let { shadow ->
            applyShadow(modalFrame, shadow.color.resolve(conditionsContext), shadow.elevation)
        }
    }

    /**
     * Hands the activity a placement the new values select, or resolves the placement's colors
     * again; the scene's own views refresh themselves.
     */
    private fun onEnvironmentConditionsChanged() {
        if (isPlacementSuperseded) return
        if (resolvePlacement() !== placement) {
            isPlacementSuperseded = true
            onPlacementSuperseded?.invoke()
            return
        }
        if (!model.viewsSelectOnEnvironmentConditions) return
        val background = Background(placement.backgroundColor, placement.border)
        LayoutUtils.updateBackground(modalFrame, background, background)
        applyShadeAndShadow()
    }
}
