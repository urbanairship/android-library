/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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

    private var modalFrame: ConstrainedFrameLayout? = null
    private var containerView: View? = null
    private var clickOutsideListener: OnClickListener? = null

    init {
        val placement = presentation.getResolvedPlacement(context)
        val size = placement.size
        val position = placement.position
        val margin = placement.margin
        @ColorInt val shadeColor = placement.shadeColor?.resolve(context)

        val frame = ConstrainedFrameLayout(context, size).apply {
            id = View.generateViewId()
            layoutParams = LayoutParams(MATCH_CONSTRAINT, MATCH_CONSTRAINT)
            elevation = ResourceUtils.dpToPx(context, 16)
        }
        modalFrame = frame

        val container = ClippableFrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT).apply {
                margin?.let {
                    setMargins(
                        ResourceUtils.dpToPx(context, it.start).toInt(),
                        ResourceUtils.dpToPx(context, it.top).toInt(),
                        ResourceUtils.dpToPx(context, it.end).toInt(),
                        ResourceUtils.dpToPx(context, it.bottom).toInt()
                    )
                }
            }
        }

        container.addView(model.createView(context, viewEnvironment, null))

        LayoutUtils.applyBorderAndBackground(container, placement.border, placement.backgroundColor)
        containerView = container

        frame.addView(container)
        addView(frame)

        val viewId = frame.id
        val constraints = ConstraintSetBuilder.newBuilder(context)
            .constrainWithinParent(viewId)
            .size(size, placement.shouldIgnoreSafeArea(), viewId)
            .position(position, viewId)
            .build()

        constraints.applyTo(this)
        shadeColor?.let { setBackgroundColor(it) }

        if (viewEnvironment.isIgnoringSafeAreas) {
            setOnApplyWindowInsetsListener(frame) { _: View, insets: WindowInsetsCompat ->
                ViewCompat.dispatchApplyWindowInsets(container, insets)
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

    private fun isTouchOutside(event: MotionEvent): Boolean {
        // Get the bounds of the modal
        val r = Rect()
        modalFrame?.getHitRect(r)
        // Expand the bounds by the amount of slop needed to be considered an outside touch
        r.inset(-windowTouchSlop, -windowTouchSlop)
        return !r.contains(event.x.toInt(), event.y.toInt())
    }
}
