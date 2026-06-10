package com.urbanairship.android.layout.ui

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.annotation.AnimatorRes
import androidx.annotation.CallSuper
import androidx.annotation.Keep
import androidx.annotation.MainThread
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams.MATCH_CONSTRAINT
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.customview.widget.ViewDragHelper
import com.urbanairship.android.layout.BannerPresentation
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.android.layout.property.ConstrainedSize
import com.urbanairship.android.layout.property.VerticalPosition
import com.urbanairship.android.layout.util.ConstraintSetBuilder
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.ResourceUtils
import com.urbanairship.android.layout.util.Timer
import com.urbanairship.android.layout.widget.ConstrainedFrameLayout
import kotlin.math.abs
import kotlin.math.roundToInt

internal class ThomasBannerView(
    context: Context,
    private val model: AnyModel,
    private val presentation: BannerPresentation,
    private val environment: ViewEnvironment
) : ConstraintLayout(context) {

    /**
     * The minimum velocity needed to initiate a fling, as measured in pixels per second.
     * The default value is loaded from the `ViewConfiguration.getScaledMinimumFlingVelocity()`.
     */
    var minFlingVelocity = 0f

    private var overDragAmount = 0f
    private var placement = VerticalPosition.BOTTOM
    private var dragHelper: ViewDragHelper? = null
    private var bannerFrame: ConstrainedFrameLayout? = null

    /** In-app message display timer. */
    val displayTimer: Timer

    @AnimatorRes
    private var animationIn = 0

    @AnimatorRes
    private var animationOut = 0
    private var isDismissed = false
    var isResumed = false
        private set
    private var listener: Listener? = null

    /**
     * Banner view listener.
     */
    interface Listener {

        /**
         * Called when the banner times out.
         *
         * @param view The banner view.
         */
        @MainThread
        fun onTimedOut()

        /**
         * Called when a child view was dismissed from a swipe. It is up to the listener to remove
         * or hide the view from the parent.
         */
        fun onDismissed()

        /**
         * Called when a child view's drag state changes.
         *
         * @param state The drag state will be either `ViewDragHelper.STATE_IDLE`,
         * `ViewDragHelper.STATE_DRAGGING`, or `ViewDragHelper.STATE_SETTLING`.
         */
        fun onDragStateChanged(state: Int)
    }

    init {
        displayTimer = object : Timer(presentation.durationMs.toLong()) {
            override fun onFinish() {
                listener?.onTimedOut()
                dismissAnimated()
            }
        }
        initDragHelper(context)
        id = model.viewId
        configureBanner()
        onResume()
    }

    private fun initDragHelper(context: Context) {
        if (isInEditMode) {
            return
        }
        dragHelper = ViewDragHelper.create(this, ViewDragCallback())
        val vc = ViewConfiguration.get(context)
        minFlingVelocity = vc.scaledMinimumFlingVelocity.toFloat()
        overDragAmount = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            DEFAULT_OVER_DRAG_DP.toFloat(),
            context.resources.displayMetrics
        )
    }

    private fun configureBanner() {
        val placement = presentation.getResolvedPlacement(context)
        val size = placement.size
        val frame = makeFrame(size)
        val containerView = model.createView(context, environment, null)
        frame.addView(containerView)
        addView(frame)
        LayoutUtils.applyBorderAndBackground(frame, null, placement.border, placement.backgroundColor)

        applySizeConstraints()

        if (environment.isIgnoringSafeAreas) {
            var lastAppliedInset: WindowInsetsCompat? = null
            ViewCompat.setOnApplyWindowInsetsListener(frame,
                androidx.core.view.OnApplyWindowInsetsListener { v, insets ->
                    //NOTE: for some reason old android versions keeps calling this method with the same inset
                    if (lastAppliedInset == insets) { return@OnApplyWindowInsetsListener insets }
                    lastAppliedInset = insets

                    ViewCompat.dispatchApplyWindowInsets(v, insets)
                })
        }

        if (animationIn != 0) {
            val animator = AnimatorInflater.loadAnimator(context, animationIn)
            animator.setTarget(bannerFrame)
            animator.start()
        }
    }

    private fun makeFrame(size: ConstrainedSize) =
        ConstrainedFrameLayout(context, size).apply {
            id = generateViewId()
            layoutParams = LayoutParams(MATCH_CONSTRAINT, MATCH_CONSTRAINT)
            elevation = ResourceUtils.dpToPx(context, 16)
        }.also {
            bannerFrame = it
        }

    /** Window bounds the size constraints were last applied for (loop/churn guard). */
    private var lastWindowWidth = 0
    private var lastWindowHeight = 0

    /**
     * Builds and applies the frame's size/position constraints, fitting any overflowing
     * `aspect_ratio` within the window (see [ConstraintSetBuilder.aspectRatioWithinBounds]).
     *
     * Re-resolves the placement because orientation may select a different one. Called from
     * [configureBanner] and [onConfigurationChanged] — the latter matters when the host activity
     * handles its own `configChanges` (no recreate), so the banner view persists and `init` never
     * re-runs; without re-applying here a portrait-fitted px box would stay stale in landscape.
     */
    private fun applySizeConstraints() {
        val frame = bannerFrame ?: return
        val placement = presentation.getResolvedPlacement(context)
        val viewId = frame.id
        val size = placement.size
        val margin = placement.margin

        val ignoreSafeArea = false

        // Percent base: the full window (matches ConstraintLayout's constrainPercent*).
        val windowWidthPx = ResourceUtils.getWindowWidthPixels(context, ignoreSafeArea)
        val windowHeightPx = ResourceUtils.getWindowHeightPixels(context, ignoreSafeArea)

        // Overflow bound / fitted-size target: window minus the frame's outer margins.
        val horizontalMargin = (margin?.start ?: 0) + (margin?.end ?: 0)
        val verticalMargin = (margin?.top ?: 0) + (margin?.bottom ?: 0)
        val horizontalMarginPx = ResourceUtils.dpToPx(context, horizontalMargin).toInt()
        val verticalMarginPx = ResourceUtils.dpToPx(context, verticalMargin).toInt()

        val availableWidthPx = (windowWidthPx - horizontalMarginPx).coerceAtLeast(0)
        val availableHeightPx = (windowHeightPx - verticalMarginPx).coerceAtLeast(0)

        lastWindowWidth = windowWidthPx
        lastWindowHeight = windowHeightPx

        ConstraintSetBuilder.newBuilder(context)
            .position(placement.position, viewId)
            .width(size, ignoreSafeArea, viewId)
            .height(size, ignoreSafeArea, viewId)
            .aspectRatioWithinBounds(
                size = size,
                viewId = viewId,
                windowWidthPx = windowWidthPx,
                windowHeightPx = windowHeightPx,
                availableWidthPx = availableWidthPx,
                availableHeightPx = availableHeightPx,
                ignoreSafeArea = ignoreSafeArea
            )
            .margin(margin, viewId)
            .build()
            .applyTo(this)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration?) {
        super.onConfigurationChanged(newConfig)
        // Re-fit only when the window bounds actually changed, to avoid layout churn.
        val windowWidth = ResourceUtils.getWindowWidthPixels(context, false)
        val windowHeight = ResourceUtils.getWindowHeightPixels(context, false)
        if (windowWidth != lastWindowWidth || windowHeight != lastWindowHeight) {
            applySizeConstraints()
        }
    }

    /**
     * Resumes the banner's timer.
     */
    @MainThread
    @CallSuper
    fun onResume() {
        isResumed = true
        if (!isDismissed) {
            displayTimer.start()
        }
    }

    /**
     * Pauses the banner's timer.
     */
    @MainThread
    @CallSuper
    fun onPause() {
        isResumed = false
        displayTimer.stop()
    }

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
        isDismissed = true
        displayTimer.stop()
        if (animate && bannerFrame != null && animationOut != 0) {
            clearAnimation()
            val animator = AnimatorInflater.loadAnimator(context, animationOut)
            animator.setTarget(bannerFrame)
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
            bannerFrame = null
        }
    }

    /**
     * Sets the animation.
     *
     * @param in The animation in.
     * @param out The animation out.
     */
    fun setAnimations(@AnimatorRes animationIn: Int, @AnimatorRes animationOut: Int) {
        this.animationIn = animationIn
        this.animationOut = animationOut
    }

    /**
     * Sets the banner listener.
     *
     * @param listener The banner listener.
     */
    fun setListener(listener: Listener?) {
        this.listener = listener
    }
    /**
     * The view's y translation as a fraction of its height.
     * Used to animate a view into the screen with a ObjectAnimator.
     */
    @get:Keep
    @set:Keep
    var yFraction: Float
        get() {
            val height = height
            return if (height == 0) {
                0f
            } else translationY / height
        }
        set(value) {
            // The view's height is only populated after the view has been laid out. We can
            // workaround this by adding a OnPreDrawListener and set the yFraction.
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
                translationY = value * height
            }
        }

    /**
     * The view's x translation as a fraction of its width.
     * Used to animate a view into the screen with a ObjectAnimator.
     */
    @get:Keep
    @set:Keep
    var xFraction: Float
        get() {
            val width = width
            return if (width == 0) 0f else translationX / width
        }
        set(value) {
            // The view's width is only populated after the view has been laid out. We can
            // workaround this by adding a ViewTreeObserver.OnPreDrawListener and set the xFraction.
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

    override fun computeScroll() {
        super.computeScroll()
        if (dragHelper?.continueSettling(true) == true) {
            this.postInvalidateOnAnimation()
        }
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        val helper = dragHelper ?: return false

        if (helper.shouldInterceptTouchEvent(event) || super.onInterceptTouchEvent(event)) {
            return true
        }
        if (helper.viewDragState == ViewDragHelper.STATE_IDLE && event.actionMasked == MotionEvent.ACTION_MOVE) { /*
             * Check if the touch exceeded the touch slop. If so, check if we can drag and interrupt
             * any children. This breaks any children that are horizontally scrollable, unless they
             * prevent the parent view from intercepting the event.
             */
            if (helper.checkTouchSlop(ViewDragHelper.DIRECTION_VERTICAL)) {
                helper.findTopChildUnder(event.x.toInt(), event.y.toInt())?.let { child ->
                    if (!child.canScrollVertically(helper.touchSlop)) {
                        helper.captureChildView(child, event.getPointerId(0))
                        return helper.viewDragState == ViewDragHelper.STATE_DRAGGING
                    }
                }
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val helper = dragHelper ?: return false

        helper.processTouchEvent(event)
        if (helper.capturedView == null) {
            if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                if (helper.checkTouchSlop(ViewDragHelper.DIRECTION_VERTICAL)) {
                    helper.findTopChildUnder(event.x.toInt(), event.y.toInt())?.let { child ->
                        if (!child.canScrollVertically(helper.touchSlop)) {
                            helper.captureChildView(child, event.getPointerId(0))
                        }
                    }
                }
            }
        }
        return helper.capturedView != null
    }

    /**
     * Sets the banner placement.
     *
     * @param placement The placement.
     */
    fun setPlacement(placement: VerticalPosition) {
        this.placement = placement
    }

    /**
     * Helper class to handle the the callbacks from the ViewDragHelper.
     */
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
                VerticalPosition.TOP ->
                    top.toFloat().coerceAtMost(startTop + overDragAmount).roundToInt()
                VerticalPosition.BOTTOM,
                VerticalPosition.CENTER ->
                    top.toFloat().coerceAtLeast(startTop - overDragAmount).roundToInt()
            }
        }

        override fun onViewCaptured(view: View, activePointerId: Int) {
            capturedView = view
            startTop = view.top
            startLeft = view.left
            dragPercent = 0f
            isDismissed = false
        }

        override fun onViewPositionChanged(view: View, left: Int, top: Int, dx: Int, dy: Int) {
            val range = height
            val moved = abs(top - startTop)
            if (range > 0) {
                dragPercent = moved / range.toFloat()
            }
            invalidate()
        }

        override fun onViewDragStateChanged(state: Int) {
            val view = capturedView ?: return
            synchronized(this) {
                listener?.onDragStateChanged(state)
                if (state == ViewDragHelper.STATE_IDLE) {
                    if (isDismissed) {
                        listener?.onDismissed()
                        removeView(view)
                    }
                    capturedView = null
                }
            }
        }

        override fun onViewReleased(view: View, xv: Float, yv: Float) {
            val absYv = abs(yv)
            if (
                (VerticalPosition.TOP == placement && startTop >= view.top) || startTop <= view.top
            ) {
                isDismissed = dragPercent >= IDLE_MIN_DRAG_PERCENT ||
                        absYv > minFlingVelocity ||
                        dragPercent > FLING_MIN_DRAG_PERCENT
            }
            if (isDismissed) {
                val top = if (VerticalPosition.TOP == placement) {
                    -view.height
                } else {
                    height + view.height
                }
                dragHelper?.settleCapturedViewAt(startLeft, top)
            } else {
                dragHelper?.settleCapturedViewAt(startLeft, startTop)
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
