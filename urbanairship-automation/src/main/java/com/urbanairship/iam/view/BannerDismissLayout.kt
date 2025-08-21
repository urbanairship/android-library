/* Copyright Airship and Contributors */
package com.urbanairship.iam.view

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import com.urbanairship.UALog
import com.urbanairship.iam.content.Banner
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The BannerDismissLayout allows dismissing a banner with a vertical swipe gesture.
 */
internal class BannerDismissLayout (
    context: Context,
    attrs: AttributeSet?,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    /**
     * Interface to listen for dismissing the message view.
     */
    interface Listener {

        /**
         * Called when a child view was dismissed from a swipe. It is up to the listener to remove
         * or hide the view from the parent.
         */
        fun onDismissed(view: View)

        /**
         * Called when a child view's drag state changes.
         *
         * @param state The drag state will be either `ViewDragHelper.STATE_IDLE`,
         * `ViewDragHelper.STATE_DRAGGING`, or `ViewDragHelper.STATE_SETTLING`.
         */
        fun onDragStateChanged(view: View, state: Int)
    }

    var placement = Banner.Placement.BOTTOM

    var listener: Listener? = null

    /**
     * The view's y translation as a fraction of its height.
     * Used to animate a view into the screen with a ObjectAnimator.
     */
    var yFraction: Float
        get() = if (height == 0) 0f else translationY / height
        set(value) {
            // The view's height is only populated after the view has been laid out.
            // We can workaround this by adding a OnPreDrawListener and set the yFraction.

            if (visibility == VISIBLE && height == 0) {
                val preDrawListener: ViewTreeObserver.OnPreDrawListener =
                    object : ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            yFraction = value
                            viewTreeObserver.removeOnPreDrawListener(this)
                            return true
                        }
                    }
                viewTreeObserver.addOnPreDrawListener(preDrawListener)
            } else {
                translationY = yFraction * height
            }
        }

    /**
     * The view's x translation as a fraction of its width.
     * Used to animate a view into the screen with a ObjectAnimator.
     */
    var xFraction: Float
        get() = if (width == 0) 0f else translationX / width
        set(value) {
            // The view's width is only populated after the view has been laid out.
            // We can workaround this by adding a OnPreDrawListener and set the xFraction.
            if (visibility == VISIBLE && height == 0) {
                val preDrawListener: ViewTreeObserver.OnPreDrawListener =
                    object : ViewTreeObserver.OnPreDrawListener {
                        override fun onPreDraw(): Boolean {
                            xFraction = value
                            viewTreeObserver.removeOnPreDrawListener(this)
                            return true
                        }
                    }
                viewTreeObserver.addOnPreDrawListener(preDrawListener)
            } else {
                translationX = value * width
            }
        }

    /** The minimum velocity needed to initiate a fling, as measured in pixels per second. */
    private val minFlingVelocity = with(ViewConfiguration.get(context)) {
        scaledMinimumFlingVelocity.toFloat()
    }

    private val overDragAmount =  TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        DEFAULT_OVER_DRAG_DP.toFloat(),
        resources.displayMetrics
    ).roundToInt()

    private val dragHelper: ViewDragHelper = ViewDragHelper.create(this, ViewDragCallback())

    override fun computeScroll() {
        super.computeScroll()
        if (dragHelper.continueSettling(true)) {
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (dragHelper.shouldInterceptTouchEvent(event) || super.onInterceptTouchEvent(event)) {
            return true
        }

        if (dragHelper.viewDragState == ViewDragHelper.STATE_IDLE && event.actionMasked == MotionEvent.ACTION_MOVE) {
            // Check if the touch exceeded the touch slop. If so, check if we can drag and interrupt
            // any children. This breaks any children that are horizontally scrollable, unless they
            // prevent the parent view from intercepting the event.
            if (dragHelper.checkTouchSlop(ViewDragHelper.DIRECTION_VERTICAL)) {
                val child = dragHelper.findTopChildUnder(event.x.toInt(), event.y.toInt()) ?: return false
                if (!child.canScrollVertically(dragHelper.touchSlop)) {
                    dragHelper.captureChildView(child, event.getPointerId(0))
                    return dragHelper.viewDragState == ViewDragHelper.STATE_DRAGGING
                }
            }
        }

        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        dragHelper.processTouchEvent(event)
        if (dragHelper.capturedView == null) {
            if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                if (dragHelper.checkTouchSlop(ViewDragHelper.DIRECTION_VERTICAL)) {
                    val child = dragHelper.findTopChildUnder(event.x.toInt(), event.y.toInt()) ?: return false
                    if (!child.canScrollVertically(dragHelper.touchSlop)) {
                        dragHelper.captureChildView(child, event.getPointerId(0))
                    }
                }
            }
        }
        return dragHelper.capturedView != null
    }

    /** Helper class to handle the the callbacks from the ViewDragHelper. */
    private inner class ViewDragCallback : ViewDragHelper.Callback() {
        private var startTop = 0
        private var startLeft = 0
        private var dragPercent = 0f
        private var capturedView: View? = null
        private var isDismissed = false

        override fun tryCaptureView(view: View, i: Int): Boolean {
            return capturedView == null
        }

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int): Int {
            return child.left
        }

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
            return when (placement) {
                Banner.Placement.TOP -> min(top, startTop + overDragAmount)
                Banner.Placement.BOTTOM -> max(top, startTop - overDragAmount)
            }
        }

        override fun onViewCaptured(view: View, activePointerId: Int) {
            capturedView = view
            startTop = view.top
            startLeft = view.left
            dragPercent = 0f
            isDismissed = false
            UALog.e { "Start top: $startTop" }
        }

        @SuppressLint("NewApi")
        override fun onViewPositionChanged(view: View, left: Int, top: Int, dx: Int, dy: Int) {
            val range = view.height
            val moved = abs(top - startTop)
            if (range > 0) {
                dragPercent = moved / range.toFloat()
            }
            invalidate()
        }

        override fun onViewDragStateChanged(state: Int) {
            val captured = capturedView ?: return

            synchronized(this) {
                listener?.onDragStateChanged(captured, state)
                if (state == ViewDragHelper.STATE_IDLE) {
                    if (isDismissed) {
                        listener?.onDismissed(captured)
                        removeView(capturedView)
                    }
                    capturedView = null
                }
            }
        }

        override fun onViewReleased(view: View, xv: Float, yv: Float) {
            val absYv = abs(yv)
            val isAffected = if (placement == Banner.Placement.TOP) startTop >= view.top else startTop <= view.top
            if (isAffected) {
                isDismissed = dragPercent >= IDLE_MIN_DRAG_PERCENT || absYv > minFlingVelocity || dragPercent > FLING_MIN_DRAG_PERCENT
            }

            if (isDismissed) {
                val top = if (placement == Banner.Placement.TOP) - view.height else height + view.height
                dragHelper.settleCapturedViewAt(startLeft, top)
            } else {
                dragHelper.settleCapturedViewAt(startLeft, startTop)
            }
            invalidate()
        }
    }

    companion object {

        /**
         * The percent of a view's width it must be dragged before it is considered dismissible when the velocity
         * is less then the [.getMinFlingVelocity].
         */
        private const val IDLE_MIN_DRAG_PERCENT = .4f

        /**
         * The percent of a view's width it must be dragged before it is considered dismissible when the velocity
         * is greater then the [.getMinFlingVelocity].
         */
        private const val FLING_MIN_DRAG_PERCENT = .1f
        private const val DEFAULT_OVER_DRAG_DP = 24
    }
}
