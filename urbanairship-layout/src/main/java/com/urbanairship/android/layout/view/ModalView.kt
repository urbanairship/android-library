/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_PARENT
import androidx.core.view.ViewCompat
import androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener
import androidx.core.view.WindowInsetsCompat
import com.urbanairship.android.layout.ModalPresentation
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.android.layout.util.ConstraintSetBuilder
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.widget.ClippableFrameLayout
import com.urbanairship.android.layout.widget.ConstrainedFrameLayout

internal class ModalView(
    context: Context,
    model: AnyModel,
    presentation: ModalPresentation,
    viewEnvironment: ViewEnvironment
) : ConstraintLayout(context) {

    private val windowTouchSlop by lazy {
        ViewConfiguration.get(context).scaledWindowTouchSlop
    }

    private val modalFrame: ViewGroup

    /** The modal frame's min height as declared by the layout (e.g. `min_height: 100%`). */
    private val initialModalFrameMinHeight: Int

    private var lastAppliedImeBottom: Int = 0

    private var clickOutsideListener: OnClickListener? = null

    init {
        val placement = presentation.getResolvedPlacement(context)
        val size = placement.size
        val position = placement.position
        val margin = placement.margin
        @ColorInt val shadeColor = placement.shadeColor?.resolve(context)

        modalFrame = ConstrainedFrameLayout(context, size).apply {
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

            placement.shadow?.androidShadow?.let { shadow ->
                applyShadow(this, shadow.color.resolve(context), shadow.elevation)
            }
        }

        val containerView = ClippableFrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)

            placement.border
                ?.innerRadii { ResourceUtils.dpToPx(context, it) }
                ?.let {
                    setClipPathBorderRadius(it)
                }
        }

        val contentView = model.createView(context, viewEnvironment, null)
        containerView.addView(contentView)

        modalFrame.addView(containerView)
        addView(modalFrame)

        val viewId = modalFrame.id
        val constraints = ConstraintSetBuilder.newBuilder(context)
            .constrainWithinParent(viewId)
            .size(size, placement.shouldIgnoreSafeArea(), viewId)
            .position(position, viewId)
            .build()

        constraints.applyTo(this)
        initialModalFrameMinHeight =
            constraints.getConstraint(modalFrame.id).layout.heightMin.coerceAtLeast(0)
        shadeColor?.let { setBackgroundColor(it) }

        if (viewEnvironment.isIgnoringSafeAreas) {
            setOnApplyWindowInsetsListener(modalFrame) { _: View, insets: WindowInsetsCompat ->
                ViewCompat.dispatchApplyWindowInsets(containerView, insets)
            }
        }
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
}
