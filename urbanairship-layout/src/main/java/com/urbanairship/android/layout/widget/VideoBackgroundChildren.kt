/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.MediaModel
import com.urbanairship.android.layout.property.Background
import com.urbanairship.android.layout.property.Background.MediaBackground.VideoMediaBackground
import com.urbanairship.android.layout.property.Parallax
import com.urbanairship.android.layout.view.MediaView

/**
 * The playable `backgrounds` layers a host renders as real child views instead of painting.
 *
 * A color, gradient or image layer is painted straight onto the host's canvas by
 * [BackgroundsPainter]; video can't be, because it needs a real, attached view to play at all. So
 * a host with a playable layer adds one of these children below its own content and hands
 * [paintedByView] to the painter, which then skips those layers. Everything here is host-agnostic
 * -- the host supplies only its own `LayoutParams` type and, if it has one, a parallax offset.
 *
 * @param layers the host's `backgrounds`, each paired with its `parallax` (`null` on a host that
 *   doesn't offer any).
 * @param models the host's own [com.urbanairship.android.layout.model.videoBackgroundModels].
 */
internal class VideoBackgroundChildren(
    layers: List<Pair<Background, Parallax?>>,
    models: Map<VideoMediaBackground, MediaModel>,
    context: Context,
    viewEnvironment: ViewEnvironment
) {

    class Child(
        val layer: VideoMediaBackground,
        val parallax: Parallax?,
        /** What the host hosts: the layer's own view, or a wrapper that won't take touches. */
        val view: View
    )

    /**
     * A layer with no controls of its own is decorative, and a `WebView` takes every touch that
     * reaches it -- on a tappable host (a `button_layout`, a `toggle_layout`) that would swallow
     * the tap the host exists to handle. Refusing dispatch outright lets the event carry on to
     * the host, and a layer that *does* show controls is hosted directly so it still gets them.
     */
    private class UntouchableFrame(context: Context) : FrameLayout(context) {
        init {
            // ConstraintLayout's own `ConstraintSet.clone`/`readFallback` throw on any child
            // without one, and a host is free to use those even though the current ones don't.
            id = generateViewId()
        }

        override fun dispatchTouchEvent(ev: MotionEvent): Boolean = false
    }

    val children: List<Child> = layers.mapNotNull { (background, parallax) ->
        val layer = background as? VideoMediaBackground ?: return@mapNotNull null
        val model = models[layer] ?: return@mapNotNull null
        val mediaView = model.createView(context, viewEnvironment, itemProperties = null)
        Child(
            layer = layer,
            parallax = parallax,
            view = if (mediaView.isNonInteractiveVideo) {
                UntouchableFrame(context).apply { addView(mediaView, MATCH_PARENT, MATCH_PARENT) }
            } else {
                mediaView
            }
        )
    }

    val isEmpty: Boolean get() = children.isEmpty()

    /** The layers [BackgroundsPainter] must leave alone, because these views draw them instead. */
    val paintedByView: Set<Background> = children.map { it.layer }.toSet()

    /**
     * Adds every child to [host], which must happen before the host's own content is added so the
     * content draws on top.
     *
     * Added at a single pixel, and given a real size by [placeInto] once the host's box is
     * known: a host that sizes itself to its content would otherwise measure a `MATCH_PARENT`
     * layer as a content-sized child and grow to fill whatever space was offered. A
     * `backgrounds` layer never contributes to its host's own size.
     *
     * One pixel rather than none, because a `ConstraintLayout` reads a width or height of `0` as
     * `MATCH_CONSTRAINT` and measures such a child against the whole container instead -- which
     * is the very thing being avoided. A pixel is a fixed dimension everywhere.
     */
    fun addTo(host: ViewGroup) {
        children.forEach { host.addView(it.view, 1, 1) }
    }

    /**
     * Sizes and places each child against the host's box, plus whatever spare room its own
     * parallax needs, so its unshifted position lines up with that box.
     *
     * Never writes layout params: they stay at the zero [addTo] gave them, so the host measures
     * as though these children weren't there. That is the whole point -- a `backgrounds` layer
     * must not decide its host's size, and a host that sizes to its content would otherwise be
     * held at whatever size the layer was last given and never able to shrink again. It also
     * keeps a layer from lengthening the scrolling content it lives inside on a scroll host.
     *
     * Call from `onLayout`, after the host has placed its own content.
     *
     * @param width the host's content box -- see [backgroundsBoxWidth].
     * @param height the host's content box -- see [backgroundsBoxHeight].
     * @param left where that box starts in the coordinates of whatever these children were added
     *   to, which is `0` when they were added to the content box itself rather than to the host.
     * @param top as [left], on the other axis.
     */
    fun placeInto(
        painter: BackgroundsPainter,
        width: Int,
        height: Int,
        left: Int = 0,
        top: Int = 0
    ) {
        if (width <= 0 || height <= 0) return

        children.forEach { child ->
            val (oversizeX, oversizeY) = painter.parallaxOversize(child.parallax, width, height)
            val (leadingX, leadingY) = painter.parallaxLeadingOversize(child.parallax, width, height)
            val childWidth = width + oversizeX
            val childHeight = height + oversizeY
            val childLeft = left - leadingX
            val childTop = top - leadingY
            child.view.measure(
                View.MeasureSpec.makeMeasureSpec(childWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(childHeight, View.MeasureSpec.EXACTLY)
            )
            child.view.layout(childLeft, childTop, childLeft + childWidth, childTop + childHeight)
        }
    }

    /**
     * Moves each child by its own share of the host's current content offset.
     *
     * [pinX]/[pinY] are added on top: a host whose children scroll passes its own scroll position,
     * which holds a layer still against the viewport, exactly as [BackgroundsPainter] does for a
     * painted layer by undoing the scroll translate on its canvas. A host whose children don't
     * move passes nothing.
     */
    fun applyParallax(
        painter: BackgroundsPainter,
        width: Int,
        height: Int,
        pinX: Float = 0f,
        pinY: Float = 0f
    ) {
        children.forEach { child ->
            val (dx, dy) = painter.parallaxTranslation(child.parallax, width, height)
            child.view.translationX = dx + pinX
            child.view.translationY = dy + pinY
        }
    }

    /**
     * Which of these children [view] is, or `-1` for anything else -- a host's `drawChild` passes
     * it on so the painter can put down the layers stacked directly above that one.
     */
    fun indexOf(view: View): Int = children.indexOfFirst { it.view === view }

    /** Whether [view] is one of these children, for a host that must skip them in its own layout. */
    fun contains(view: View): Boolean = children.any { it.view === view }
}
