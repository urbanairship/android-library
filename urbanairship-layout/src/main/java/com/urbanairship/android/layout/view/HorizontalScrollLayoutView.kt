/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import android.widget.FrameLayout.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout.LayoutParams.WRAP_CONTENT
import android.widget.HorizontalScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import com.urbanairship.android.layout.Thomas
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.Background
import com.urbanairship.android.layout.model.BaseModel
import com.urbanairship.android.layout.model.HorizontalScrollLayoutModel
import com.urbanairship.android.layout.util.LayoutUtils
import com.urbanairship.android.layout.widget.BackgroundsPainter
import com.urbanairship.android.layout.widget.backgroundsBoxHeight
import com.urbanairship.android.layout.widget.backgroundsBoxWidth
import com.urbanairship.android.layout.widget.paintBackgroundsBox
import com.urbanairship.android.layout.widget.ScrollContentFrame
import com.urbanairship.android.layout.widget.VideoBackgroundChildren
import com.urbanairship.android.layout.widget.PercentBaseProvider

internal class HorizontalScrollLayoutView(
    context: Context,
    model: HorizontalScrollLayoutModel,
    viewEnvironment: ViewEnvironment
) : HorizontalScrollView(context), BaseView, PercentBaseProvider {

    private val contentView: View

    // A scene at the DSL floor predates the viewport: it was laid out with a scrolled percentage
    // falling back to its own content, and handing it a viewport now would give an empty `100%` a
    // screenful of blank its author never saw. So it's left with no base, same as before there was
    // one to offer.
    private val offersViewport = viewEnvironment.layoutVersion > Thomas.MIN_SUPPORTED_VERSION

    override var percentBaseWidth: Int = 0
        private set
    override val percentBaseHeight: Int = 0

    // Never touched by measure/layout above -- see `BackgroundsPainter`.
    private val backgroundsPainter = BackgroundsPainter(context, viewEnvironment) {
        // postInvalidate(), not invalidate() -- the image loader's onImageLoaded callback
        // is not guaranteed to run on the UI thread, and invalidate() silently no-ops when
        // called off it.
        postInvalidate()
    }

    private val videoBackgrounds = VideoBackgroundChildren(
        layers = model.viewInfo.backgrounds.orEmpty().map { it.background to it.parallax },
        models = model.videoBackgroundModels,
        context = context,
        viewEnvironment = viewEnvironment
    )

    /** The one direct child a scroll view allows -- see [ScrollContentFrame]. */
    private val contentFrame = ScrollContentFrame(context)

    init {
        backgroundsPainter.setParallaxBackgrounds(model.viewInfo.backgrounds, model.viewInfo.direction)
        backgroundsPainter.attachedViewBackgrounds = videoBackgrounds.paintedByView
        isFillViewport = false
        clipToOutline = true

        contentView = model.view.createView(context, viewEnvironment, null)
        videoBackgrounds.addTo(contentFrame)
        contentFrame.videoBackgrounds = videoBackgrounds
        // Already drawing into the content frame, which this view lays out inside its own padding
        // -- so this canvas starts at the content box, and only the scroll has to be undone.
        contentFrame.drawLayersAfter = { canvas, attachedIndex ->
            canvas.save()
            canvas.translate(scrollX.toFloat(), scrollY.toFloat())
            try {
                backgroundsPainter.drawLayersAfter(
                    canvas, backgroundsBoxWidth, backgroundsBoxHeight, attachedIndex
                )
            } finally {
                canvas.restore()
            }
        }
        contentFrame.addView(contentView, FrameLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT))
        addView(contentFrame, LayoutParams(WRAP_CONTENT, MATCH_PARENT))

        // Fires on every scroll step, drag or fling/settle alike -- see `BackgroundsPainter`'s
        // class doc for why that's what keeps a parallax layer in step with no separate animation.
        setOnScrollChangeListener { _, _, _, _, _ ->
            backgroundsPainter.offsetPx = contentOffset()
            pinVideoBackgrounds()
            invalidate()
        }

        model.listener = object : BaseModel.Listener {
            override fun setVisibility(visible: Boolean) {
                this@HorizontalScrollLayoutView.isVisible = visible
            }
            override fun setEnabled(enabled: Boolean) {
                this@HorizontalScrollLayoutView.isEnabled = enabled
            }

            override fun setBackground(old: Background?, new: Background) {
                LayoutUtils.updateBackground(this@HorizontalScrollLayoutView, old, new)
            }
        }

        // Pass along any calls to apply insets to the view.
        ViewCompat.setOnApplyWindowInsetsListener(this) { _: View, insets: WindowInsetsCompat ->
            ViewCompat.dispatchApplyWindowInsets(contentView, insets)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // HorizontalScrollView measures its content with an UNSPECIFIED width so it can scroll,
        // which leaves percent-width descendants with nothing to resolve against. Record the
        // viewport for them to find. Taken from the incoming spec rather than measuredWidth, which
        // is still the previous pass's value while our content is being measured.
        //
        // Left at 0 for a scene at the DSL floor -- see `offersViewport`.
        if (offersViewport) {
            percentBaseWidth = (MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight)
                .coerceAtLeast(0)
        }
        // A layer is sized to the viewport but hosted in the content frame, which this view
        // clips to -- content shorter than the viewport would cut it off. Held to the viewport
        // the parent imposes, never to our own measured size: taken from the latter, a host that
        // sizes to its content would grow by its own padding on every pass and never shrink.
        // Nothing to hold when the size isn't imposed -- then the viewport *is* the content.
        if (!videoBackgrounds.isEmpty) {
            val frameMinimum = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
                (MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd).coerceAtLeast(0)
            } else {
                0
            }
            // `setMinimum*` always requests a layout, so writing the same value back would
            // dirty the frame on every measure pass.
            if (contentFrame.minimumWidth != frameMinimum) {
                contentFrame.minimumWidth = frameMinimum
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    /**
     * How far the content has travelled from its own start, which is `0` at rest either way.
     *
     * A right-to-left scroll view rests at its physical end, so its raw `scrollX` starts at the
     * full range and counts down -- taken as-is, a layer would start fully travelled. iOS
     * measures from the trailing edge here for the same reason.
     */
    private fun contentOffset(): Float {
        if (layoutDirection != LAYOUT_DIRECTION_RTL) return scrollX.toFloat()

        val range = (computeHorizontalScrollRange() - computeHorizontalScrollExtent())
            .coerceAtLeast(0).toFloat()
        return range - scrollX.toFloat()
    }

    /**
     * How far the content can travel, which is what a parallaxing layer's spare room is a share of.
     *
     * Read from the measured content rather than on scroll: content that already fits has no
     * travel to oversize a layer for and never fires a scroll event to say so, and content can
     * grow after the first layout (an image loading in) without resizing this view.
     */
    private fun updateTravelBounds() {
        backgroundsPainter.offsetBoundsPx =
            0f..(computeHorizontalScrollRange() - computeHorizontalScrollExtent()).coerceAtLeast(0).toFloat()
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        backgroundsPainter.isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        // Before sizing below, which reads these bounds to work out a layer's spare room --
        // `dispatchDraw` refreshes them too, but that runs after this.
        updateTravelBounds()
        // This view can already be scrolled before any scroll event reaches us: RTL rests a
        // horizontal scroll view at its far end, and a restored instance state starts wherever
        // it left off. Read here, the layer starts where the content actually is rather than
        // snapping there on the first scroll.
        backgroundsPainter.offsetPx = contentOffset()
        // Sized against the viewport, not the scrolling content it sits inside. Placed at the
        // content frame's own origin, which already sits inside this view's padding.
        videoBackgrounds.placeInto(backgroundsPainter, backgroundsBoxWidth, backgroundsBoxHeight)
        pinVideoBackgrounds()
    }

    private fun pinVideoBackgrounds() {
        videoBackgrounds.applyParallax(
            backgroundsPainter, backgroundsBoxWidth, backgroundsBoxHeight, scrollX.toFloat(), 0f
        )
    }

    override fun dispatchDraw(canvas: Canvas) {
        // View.draw() pre-translates this canvas by (-scrollX, -scrollY) before dispatchDraw runs,
        // the same way it does for onDraw -- undo it here so the painter draws against the fixed
        // viewport box it was sized against, the same way View's own drawBackground() stays put
        // regardless of scroll. Without this, every layer -- parallaxing or not -- would scroll
        // with the content at 1:1 on top of whatever parallax offset it already applies.
        // Read here rather than on scroll: content that already fits has no travel to oversize a
        // parallaxing layer for, and never fires a scroll event to say so. Content can also grow
        // after the first layout (an image loading in), which doesn't necessarily resize us.
        updateTravelBounds()

        canvas.save()
        canvas.translate(scrollX.toFloat(), scrollY.toFloat())
        try {
            paintBackgroundsBox(canvas) { w, h -> backgroundsPainter.drawLayersBefore(canvas, w, h) }
        } finally {
            canvas.restore()
        }
        super.dispatchDraw(canvas)
    }
}
