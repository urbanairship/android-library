/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.graphics.Rect
import android.util.DisplayMetrics
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.UALog
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.PagerModel
import com.urbanairship.android.layout.view.PagerView
import kotlin.math.abs

internal class PagerRecyclerView(
    context: Context,
    private val model: PagerModel,
    private val viewEnvironment: ViewEnvironment
) : RecyclerView(context) {

    private lateinit var adapter: PagerAdapter
    private var layoutManager: LinearLayoutManager? = null
    private lateinit var snapHelper: PagerSnapHelper

    private var isInternalScroll = false
    private var isSettling = false

    private var listener: PagerView.OnScrollListener? = null

    fun refresh() {
        adapter.setItems(model.pages)
    }

    fun configure() {
        isHorizontalScrollBarEnabled = false

        snapHelper = SnapHelper()
        snapHelper.attachToRecyclerView(this)

        layoutManager = if (model.isSinglePage || model.viewInfo.isSwipeDisabled) {
            SwipeDisabledLinearLayoutManager(context, LinearLayoutManager.HORIZONTAL) { isInternalScroll }
        } else {
            ThomasLinearLayoutManager(context, LinearLayoutManager.HORIZONTAL) { isInternalScroll }
        }

        setLayoutManager(layoutManager)

        addOnScrollListener(recyclerScrollListener)

        adapter = PagerAdapter(model, viewEnvironment)
        adapter.setStateRestorationPolicy(Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY)
        adapter.setItems(model.pages)
        setAdapter(adapter)

        // Pass along any calls to apply insets to the view.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _: View, insets: WindowInsetsCompat ->
            for (index in 0..<childCount) {
                ViewCompat.dispatchApplyWindowInsets(getChildAt(index), insets)
            }
            insets
        }

        // This is fixing an eventual measuring issue (depending on the pager size) in the recycler view in RTL
        // It also fixes scroll on restored views
        scrollToPosition(0)
    }

    fun getDisplayedItemPosition(): Int {
        return snapHelper
            .findSnapView(layoutManager)
            ?.let(::getChildAdapterPosition)
            ?: NO_POSITION
    }

    fun scrollTo(position: Int) {
        val displayed = getDisplayedItemPosition()
        UALog.v { "ThomasPager RecyclerView scrollTo($position) displayed=$displayed isInternalScroll=$isInternalScroll" }
        if (position == displayed) {
            return
        }

        // Flag programmatic scrolls to suppress swipe reporting and block
        // touch events until SCROLL_STATE_IDLE. Only set when we can verify
        // the displayed position — during layout (NO_POSITION) the scroll
        // may be a no-op that never idles, which would leave the flag stuck.
        if (displayed != NO_POSITION) {
            isInternalScroll = true
        }

        smoothScrollToPosition(position)
    }

    fun setPagerScrollListener(listener: PagerView.OnScrollListener?) {
        this.listener = listener
    }

    private val recyclerScrollListener: OnScrollListener = object : OnScrollListener() {
        private var previousPosition = 0

        override fun onScrollStateChanged(v: RecyclerView, state: Int) {
            val stateName = when (state) {
                SCROLL_STATE_IDLE -> "IDLE"
                SCROLL_STATE_DRAGGING -> "DRAGGING"
                SCROLL_STATE_SETTLING -> "SETTLING"
                else -> "UNKNOWN($state)"
            }
            UALog.v { "ThomasPager RecyclerView onScrollStateChanged=$stateName isInternalScroll=$isInternalScroll" }

            listener?.onScrollStateChanged(state != SCROLL_STATE_IDLE)

            // Block new user swipes while the snap animation is playing so that
            // rapid swipes can't accumulate multiple page jumps before IDLE.
            isSettling = state == SCROLL_STATE_SETTLING && !isInternalScroll

            // Only report page position changes once the scroll has fully settled.
            // During DRAGGING/SETTLING, findSnapView can momentarily return an
            // adjacent page (e.g. during edge overscroll snap-back), which would
            // cause a false page change and a visible "bounce."
            if (state == SCROLL_STATE_IDLE) {
                val position: Int = getDisplayedItemPosition()
                UALog.v { "ThomasPager RecyclerView IDLE position=$position previousPosition=$previousPosition isInternalScroll=$isInternalScroll" }
                if (position != NO_POSITION && position != previousPosition) {
                    listener?.onScrollTo(position, isInternalScroll)
                    previousPosition = position
                } else if (position != NO_POSITION && isInternalScroll) {
                    // Scroll ended at the same position — the programmatic scroll
                    // was interrupted/snapped back. Notify so state can be corrected.
                    listener?.onScrollTo(position, isInternalScroll)
                }
                isInternalScroll = false
                isSettling = false
            }
        }
    }

    init {
        id = model.recyclerViewId
        configure()
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        // Block touches during programmatic scrolls and while the snap animation
        // is settling after a user swipe — prevents rapid swipes from accumulating
        // multiple page changes before IDLE.
        return isInternalScroll || isSettling || super.onInterceptTouchEvent(e)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (isInternalScroll || isSettling) return true
        return super.onTouchEvent(e)
    }

    private open class ThomasLinearLayoutManager(
        context: Context?,
        orientation: Int,
        private val isInternalScrollInProgress: () -> Boolean
    ) : LinearLayoutManager(context, orientation, false) {

        init {
            // Disable prefetch so that we won't get display events from items that aren't yet visible.
            // TODO: revisit this now that we have a better way for models to determine if they
            //   are displayed in the current pager page.
            isItemPrefetchEnabled = false
        }

        override fun requestChildRectangleOnScreen(
            parent: RecyclerView,
            child: View,
            rect: Rect,
            immediate: Boolean,
            focusedChildVisible: Boolean
        ): Boolean {
            // Prevent TalkBack from scrolling back to focused element during programmatic scrolls
            if (isInternalScrollInProgress()) {
                return false
            }
            return super.requestChildRectangleOnScreen(parent, child, rect, immediate, focusedChildVisible)
        }

        override fun generateDefaultLayoutParams(): LayoutParams {
            return LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        override fun smoothScrollToPosition(
            recyclerView: RecyclerView, state: State?, position: Int
        ) {
            val smoothScroller = ThomasSmoothScroller(recyclerView.context)
            smoothScroller.targetPosition = position
            startSmoothScroll(smoothScroller)
        }
    }

    /**
     * Custom `LinearLayoutManager` that disables scrolling via touch, but can still be scrolled programmatically.
     */
    private class SwipeDisabledLinearLayoutManager(
        context: Context?,
        orientation: Int,
        isInternalScrollInProgress: () -> Boolean
    ) : ThomasLinearLayoutManager(context, orientation, isInternalScrollInProgress) {

        override fun canScrollHorizontally(): Boolean {
            return false
        }

        override fun smoothScrollToPosition(
            recyclerView: RecyclerView, state: State?, position: Int
        ) {
            val smoothScroller = SwipeDisabledSmoothScroller(recyclerView.context)
            smoothScroller.targetPosition = position
            startSmoothScroll(smoothScroller)
        }

        /** Custom `LinearSmoothScroller` with overrides to remain functional when touch swipes are disabled.  */
        private class SwipeDisabledSmoothScroller(context: Context) : ThomasSmoothScroller(context) {

            override fun calculateDxToMakeVisible(view: View, snapPreference: Int): Int {
                val layoutManager = layoutManager ?: return 0
                val params = view.layoutParams as LayoutParams
                val left = layoutManager.getDecoratedLeft(view) - params.leftMargin
                val right = layoutManager.getDecoratedRight(view) + params.rightMargin
                val start = layoutManager.paddingLeft
                val end = layoutManager.width - layoutManager.paddingRight
                return calculateDtToFit(left, right, start, end, snapPreference)
            }
        }
    }

    /** Custom `LinearSmoothScroller` with overrides to customize the animation speed.  */
    private open class ThomasSmoothScroller(context: Context) : LinearSmoothScroller(context) {

        override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
            return MILLISECONDS_PER_INCH / displayMetrics.densityDpi
        }

        companion object {
            private const val MILLISECONDS_PER_INCH = 65f
        }
    }

    /** Custom `PagerSnapHelper` with overrides to remain functional when touch swipes are disabled.  */
    private class SnapHelper : PagerSnapHelper() {

        private var verticalHelper: OrientationHelper? = null
        private var horizontalHelper: OrientationHelper? = null

        override fun findSnapView(layoutManager: LayoutManager): View? {
            if (layoutManager.canScrollVertically()) {
                return findCenterView(layoutManager, getVerticalHelper(layoutManager))
            }
            // Always use horizontal helper pagers with disabled swipes are fixed too.
            return findCenterView(layoutManager, getHorizontalHelper(layoutManager))
        }

        fun findCenterView(layoutManager: LayoutManager, helper: OrientationHelper): View? {
            val childCount = layoutManager.childCount
            if (childCount == 0) {
                return null
            }

            var closestChild: View? = null
            val center = helper.startAfterPadding + helper.totalSpace / 2
            var absClosest = Int.MAX_VALUE

            for (i in 0..<childCount) {
                val child = layoutManager.getChildAt(i)
                val childCenter =
                    helper.getDecoratedStart(child) + (helper.getDecoratedMeasurement(child) / 2)
                val absDistance = abs(childCenter - center)

                /* if child center is closer than previous closest, set it as closest  */
                if (absDistance < absClosest) {
                    absClosest = absDistance
                    closestChild = child
                }
            }
            return closestChild
        }

        fun getVerticalHelper(layoutManager: LayoutManager): OrientationHelper {
            val saved = verticalHelper
            if (saved?.layoutManager == layoutManager) {
                return saved
            }

            val result = OrientationHelper.createVerticalHelper(layoutManager)
            verticalHelper = result
            return result
        }

        fun getHorizontalHelper(
            layoutManager: LayoutManager
        ): OrientationHelper {
            val saved = horizontalHelper
            if (saved?.layoutManager == layoutManager) {
                return saved
            }

            val result = OrientationHelper.createHorizontalHelper(layoutManager)
            horizontalHelper = result
            return result
        }
    }
}
