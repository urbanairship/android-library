/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.view.descendants
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import com.urbanairship.android.layout.environment.ThomasState
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.gestures.PagerGestureDetector
import com.urbanairship.android.layout.gestures.PagerGestureEvent
import com.urbanairship.android.layout.info.AccessibilityAction
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.PagerModel
import com.urbanairship.android.layout.property.Direction
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.util.findTargetDescendant
import com.urbanairship.android.layout.widget.BackgroundsPainter
import com.urbanairship.android.layout.widget.backgroundsBoxHeight
import com.urbanairship.android.layout.widget.backgroundsBoxWidth
import com.urbanairship.android.layout.widget.paintBackgroundsBox
import com.urbanairship.android.layout.widget.Clippable
import com.urbanairship.android.layout.widget.ClippableViewDelegate
import com.urbanairship.android.layout.widget.PagerRecyclerView
import com.urbanairship.android.layout.widget.VideoBackgroundChildren
import com.urbanairship.android.layout.widget.TouchAwareWebView
import com.urbanairship.util.stringResource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class PagerView(
    context: Context,
    val model: PagerModel,
    viewEnvironment: ViewEnvironment
) : FrameLayout(context), BaseView, Clippable, KeyEvent.Callback {

    fun interface OnScrollListener {
        fun onScrollTo(position: Int, isInternalScroll: Boolean)
        fun onScrollStateChanged(isScrolling: Boolean) {}
        /**
         * The pager's absolute horizontal content offset from page 0 -- fires on every scroll
         * step, whether from a drag or a settle/fling, same as `RecyclerView.onScrolled`.
         */
        fun onContentOffsetChanged(offsetPx: Float) {}
    }

    interface OnPagerGestureListener {
        fun onGesture(event: PagerGestureEvent)
    }

    fun setAccessibilityActions(
        actions: List<AccessibilityAction>?,
        onActionPerformed: (AccessibilityAction) -> Unit
    ) {
        ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)

                // Iterate through each action provided
                actions?.forEach { action ->
                    // Get the localized description
                    val localizedDescription = action.localizedContentDescription?.ref
                        ?.stringResource(context)
                        ?: action.localizedContentDescription?.fallback
                        ?: "Unknown" // Should never be hit, should fail gracefully in parsing

                    ViewCompat.addAccessibilityAction(
                        // View to add accessibility action
                        host,
                        // Label surfaced to user by an accessibility service
                        localizedDescription
                    ) { _, _ ->
                        // Pass the current action to the onActionPerformed callback
                        onActionPerformed(action)
                        true // Return true to indicate the action was handled
                    }
                }
            }
        })
    }

    private val _isScrolling = MutableStateFlow(false)
    val isScrolling: StateFlow<Boolean> = _isScrolling.asStateFlow()

    var scrollListener: OnScrollListener? = null
    var gestureListener: OnPagerGestureListener? = null
        set(value) {
            field = value

            // Create a gesture detector if a listener is being set, otherwise clear it.
            gestureDetector = when (value) {
                null -> null
                else -> gestureDetector ?: PagerGestureDetector(this) {
                    gestureListener?.onGesture(it)
                }
            }
        }

    private var gestureDetector: PagerGestureDetector? = null
    private var suppressGesture = false
    private var accessibilityListener: AccessibilityManager.TouchExplorationStateChangeListener? = null

    private val view: PagerRecyclerView = PagerRecyclerView(context, model, viewEnvironment)

    private val clippableViewDelegate: ClippableViewDelegate = ClippableViewDelegate()

    // Never touched by measure/layout above -- see `BackgroundsPainter`.
    private val backgroundsPainter = BackgroundsPainter(context, viewEnvironment) {
        // postInvalidate(), not invalidate() -- the image loader's onImageLoaded callback
        // is not guaranteed to run on the UI thread, and invalidate() silently no-ops when
        // called off it.
        postInvalidate()
    }

    /**
     * A playable `backgrounds` layer, unlike color/gradient/image, is rendered as a real attached
     * child rather than by [backgroundsPainter]'s own canvas painting -- video needs a real,
     * attached WebView to play at all. Added below `view` in z-order (see [dispatchDraw]).
     */
    private val videoBackgrounds = VideoBackgroundChildren(
        layers = model.viewInfo.backgrounds.orEmpty().map { it.background to it.parallax },
        models = model.videoBackgroundModels,
        context = context,
        viewEnvironment = viewEnvironment
    )

    private val modelListener = object : PagerModel.Listener {
        override fun scrollTo(position: Int, animate: Boolean) {
            if (position != NO_POSITION) {
                view.scrollTo(position, animate)
            }
        }

        override fun onDataUpdated() {
            view.refresh()
            // A layer's spare room is a share of how far this pager's route scrolls, and
            // branching resolves that route as it goes. Nothing else will ask: an answer on the
            // page showing can lengthen or shorten what follows it without turning a page, which
            // leaves the pager the same size on the same page and so triggers no layout at all.
            updateTravelBounds()
            placeVideoBackgrounds()
            invalidate()
        }

        override fun setVisibility(visible: Boolean) {
            this@PagerView.isVisible = visible
        }

        override fun setEnabled(enabled: Boolean) {
            this@PagerView.isEnabled = enabled
        }

        override fun setBackground(old: Background?, new: Background) {
            LayoutUtils.updateBackground(this@PagerView, old, new)
        }

        // An image layer resolves its own `view_overrides` against this; a video layer is its
        // own `MediaModel` and gets the state through that.
        override fun onStateUpdated(state: ThomasState) {
            backgroundsPainter.state = state
        }
    }

    init {
        backgroundsPainter.setParallaxBackgrounds(model.viewInfo.backgrounds, Direction.HORIZONTAL)
        backgroundsPainter.attachedViewBackgrounds = videoBackgrounds.paintedByView
        isFocusable = true
        isFocusedByDefault = true
        videoBackgrounds.addTo(this)
        addView(view, MATCH_PARENT, MATCH_PARENT)
        model.listener = modelListener

        view.setPagerScrollListener(object : OnScrollListener {
            override fun onScrollTo(position: Int, isInternalScroll: Boolean) {
                scrollListener?.onScrollTo(position, isInternalScroll)
            }

            override fun onScrollStateChanged(isScrolling: Boolean) {
                _isScrolling.value = isScrolling
            }

            override fun onContentOffsetChanged(offsetPx: Float) {
                // Every scroll frame of every pager reaches here, so a pager with no layers to
                // move must not repaint itself for one.
                if (!backgroundsPainter.hasParallaxLayers && videoBackgrounds.isEmpty) return

                backgroundsPainter.offsetPx = offsetPx
                videoBackgrounds.applyParallax(
                    backgroundsPainter, backgroundsBoxWidth, backgroundsBoxHeight
                )
                invalidate()
            }
        })

        // Pass along any calls to apply insets to the view.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            ViewCompat.dispatchApplyWindowInsets(view, insets)
        }
    }

    /**
     * A video child needs the same oversize a canvas-painted parallax layer gets from
     * [BackgroundsPainter.drawLayer] -- without it, translating it by [Parallax] (see
     * `onContentOffsetChanged`) would slide its edge into view rather than reveal more of an
     * already-larger view, the same gap [BackgroundsPainter.parallaxOversize] exists to prevent.
     * Margins (not `CENTER` gravity) place it at rest (`offsetPx == 0`): the oversize can split
     * unevenly between the leading and trailing side, and a `CENTER` gravity always splits it
     * evenly regardless, which drifts the child from the box's own position whenever it doesn't.
     */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateTravelBounds()
    }

    /**
     * Refreshed on every layout and on every page-list change, not only on a resize: the travel
     * a layer is sized against is a page count as much as a width, and branching moves the count
     * long after the pager has settled at its final size.
     */
    private fun updateTravelBounds() {
        backgroundsPainter.offsetBoundsPx = 0f..pagerTravelRangePx(backgroundsBoxWidth)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // Both of these are read back during `super.onLayout`, not after it: laying the track
        // out reports its content offset straight to the listener above, which moves the layers.
        // Left until afterwards, that first pass runs against a stale direction and stale bounds
        // and nothing re-runs it -- an RTL pager restored onto a later page would place its
        // layer with an unmirrored offset and leave it there.
        backgroundsPainter.isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        updateTravelBounds()
        super.onLayout(changed, l, t, r, b)
        placeVideoBackgrounds()
    }

    private fun placeVideoBackgrounds() {
        videoBackgrounds.placeInto(
            backgroundsPainter, backgroundsBoxWidth, backgroundsBoxHeight, paddingLeft, paddingTop
        )
        // Sized above, then moved -- the same order both scroll hosts use.
        videoBackgrounds.applyParallax(backgroundsPainter, backgroundsBoxWidth, backgroundsBoxHeight)
    }

    /**
     * How far a parallaxing layer travels across this pager: exactly as far as the track can
     * scroll, which is every page after the first.
     *
     * A layer's spare room is its share of this, so it arrives at the far end of that room on the
     * last page and nowhere short of it. Sized for one page more, a layer is only ever
     * `(pages - 1) / pages` of the way through its own slack, which leaves a strip of it
     * permanently unreachable and crops the rest tighter than it needs to be.
     *
     * Read live rather than from the declared item list, since branching resolves a route as it
     * goes. A page's progress is then `index / (pages - 1)`, so the last page is fully travelled
     * however many pages the route turned out to have. Web sizes `--parallax-x-range` the same
     * way.
     */
    private fun pagerTravelRangePx(pageWidth: Int): Float =
        ((model.pages.size - 1).coerceAtLeast(0) * pageWidth).toFloat()

    // A `backgrounds` layer fills the host's box corner to corner, so a rounded host has to clip
    // to its own radius or the layer squares those corners off again.
    override fun setClipPathBorderRadius(borderRadius: Float) {
        clippableViewDelegate.setClipPathBorderRadius(this, borderRadius)
    }

    override fun setClipPathBorderRadius(borderRadii: FloatArray?) {
        clippableViewDelegate.setClipPathBorderRadii(this, borderRadii)
    }

    override fun dispatchDraw(canvas: Canvas) {
        // Layers before the first video child paint here; layers after the last one paint from
        // drawChild, once that child has actually drawn -- otherwise a blend_mode layer meant to
        // sit on top of the video (see PagerModel.videoBackgroundModels) would paint first and
        // land underneath it instead, since children always draw after this.
        paintBackgroundsBox(canvas) { w, h -> backgroundsPainter.drawLayersBefore(canvas, w, h) }
        super.dispatchDraw(canvas)
    }

    override fun drawChild(canvas: Canvas, child: View, drawingTime: Long): Boolean {
        val result = super.drawChild(canvas, child, drawingTime)
        val attachedIndex = videoBackgrounds.indexOf(child)
        if (attachedIndex >= 0) {
            paintBackgroundsBox(canvas) { w, h ->
                backgroundsPainter.drawLayersAfter(canvas, w, h, attachedIndex)
            }
        }
        return result
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return when (keyCode) {
            // Recreate tap events to navigate pages with keyboard
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                gestureDetector?.let { detector ->
                    detector.onTouchEvent(generateMotionEvent(MotionEvent.ACTION_DOWN, 45f))
                    detector.onTouchEvent(generateMotionEvent(MotionEvent.ACTION_UP, 45f))

                }
                true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                gestureDetector?.let { detector ->
                    detector.onTouchEvent(generateMotionEvent(MotionEvent.ACTION_DOWN, 975f))
                    detector.onTouchEvent(generateMotionEvent(MotionEvent.ACTION_UP, 975f))
                }
                true
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // Forward touch events to the gesture detector so it sees the full press/release
        // sequence even when onInterceptTouchEvent returns true (e.g. during a programmatic
        // scroll). Without this, ACTION_UP can be lost and freeze the story.
        // Suppression is decided once at ACTION_DOWN so a swipe that starts on empty space
        // but ends over a button still delivers ACTION_UP to the detector.
        gestureDetector?.let { detector ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                suppressGesture = event.isWithinClickableDescendantOf(view)
            }
            if (!suppressGesture) {
                detector.onTouchEvent(event)
            }
        }
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            suppressGesture = false
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        return if (model.viewInfo.isSwipeDisabled) {
            // prevent touches to stop the scroll animation
            view.onInterceptTouchEvent(event)
        } else {
            // We're just snooping, so always let the event pass through.
            super.onInterceptTouchEvent(event)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return gestureDetector != null || super.onTouchEvent(event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // Register accessibility listener when view is attached
        if (accessibilityListener == null) {
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            val listener = AccessibilityManager.TouchExplorationStateChangeListener { isEnabled ->
                if (isEnabled) {
                    val accessibleView = view.descendants.firstOrNull { it.isImportantForAccessibility }
                    accessibleView?.postDelayed({
                        accessibleView.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
                    }, 1000)
                }
            }
            accessibilityListener = listener
            accessibilityManager.addTouchExplorationStateChangeListener(listener)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Remove the accessibility listener to prevent memory leaks
        accessibilityListener?.let { listener ->
            val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            accessibilityManager.removeTouchExplorationStateChangeListener(listener)
        }
        accessibilityListener = null
    }

    private fun generateMotionEvent(action: Int, xCoordinate: Float): MotionEvent {
        return MotionEvent.obtain(SystemClock.uptimeMillis(),
            SystemClock.uptimeMillis(),
            action,
            xCoordinate,
            1500f,
            0)
    }

    private fun MotionEvent.isWithinClickableDescendantOf(view: View): Boolean =
        findTargetDescendant(view) {
            it.isClickable && it.isEnabled && !it.isNonInteractiveVideoSurface()
        } != null

    private fun View.isNonInteractiveVideoSurface(): Boolean {
        if (this !is TouchAwareWebView) return false
        var ancestor: View? = parent as? View
        while (ancestor != null) {
            if (ancestor is MediaView) return ancestor.isNonInteractiveVideo
            ancestor = ancestor.parent as? View
        }
        return false
    }
}
