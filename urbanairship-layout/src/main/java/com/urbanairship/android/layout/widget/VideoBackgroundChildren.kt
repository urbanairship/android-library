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
        val mediaView: MediaView,
        /** What the host actually hosts -- [mediaView], or a wrapper that won't take touches. */
        val view: View
    )

    /**
     * A layer with no controls of its own is decorative, and a `WebView` takes every touch that
     * reaches it -- on a tappable host (a `button_layout`, a `toggle_layout`) that would swallow
     * the tap the host exists to handle. Refusing dispatch outright lets the event carry on to
     * the host, and a layer that *does* show controls is hosted directly so it still gets them.
     */
    private class UntouchableFrame(context: Context) : FrameLayout(context) {
        override fun dispatchTouchEvent(ev: MotionEvent): Boolean = false
    }

    val children: List<Child> = layers.mapNotNull { (background, parallax) ->
        val layer = background as? VideoMediaBackground ?: return@mapNotNull null
        val model = models[layer] ?: return@mapNotNull null
        val mediaView = model.createView(context, viewEnvironment, itemProperties = null)
        Child(
            layer = layer,
            parallax = parallax,
            mediaView = mediaView,
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
     * Added with no size at all, and given one by [layOut] once the host's box is known: a host
     * that sizes itself to its content would otherwise measure a `MATCH_PARENT` layer as a
     * content-sized child and grow to fill whatever space was offered. A `backgrounds` layer
     * never contributes to its host's own size.
     */
    fun addTo(host: ViewGroup) {
        children.forEach { host.addView(it.view, 0, 0) }
    }

    /**
     * Sizes each child to the host's box plus whatever spare room its own parallax needs, placed
     * so its unshifted position lines up with the box.
     *
     * Margins rather than a centering gravity: the spare room can fall unevenly on the leading and
     * trailing side, and a centered child would drift off the box's own position whenever it does.
     * With no parallax this is simply the box's own size at its own origin.
     *
     * @param params builds the host's own `LayoutParams` from `(width, height, leftMargin, topMargin)`.
     */
    fun layOut(
        painter: BackgroundsPainter,
        width: Int,
        height: Int,
        params: (Int, Int, Int, Int) -> ViewGroup.MarginLayoutParams
    ) {
        if (width <= 0 || height <= 0) return

        children.forEach { child ->
            val (oversizeX, oversizeY) = painter.parallaxOversize(child.parallax, width, height)
            val (leadingX, leadingY) = painter.parallaxLeadingOversize(child.parallax, width, height)
            val childWidth = width + oversizeX
            val childHeight = height + oversizeY

            // Reached on every scroll frame, so only a real change is written back -- assigning
            // params unconditionally would relayout the video child on each one.
            val current = child.view.layoutParams as? ViewGroup.MarginLayoutParams
            if (current != null &&
                current.width == childWidth && current.height == childHeight &&
                current.leftMargin == -leadingX && current.topMargin == -leadingY
            ) {
                return@forEach
            }

            child.view.layoutParams = params(childWidth, childHeight, -leadingX, -leadingY)
        }
    }

    /**
     * Measures and positions each child against the host's own box.
     *
     * For a host that leaves these children out of its own measure/layout pass -- a stacking
     * layout has to, or the layer would take a slot in the stack -- and so has to place them
     * itself. A host that lays children out in place already (a `FrameLayout`) needs none of this.
     */
    fun measureAndLayout(hostWidth: Int, hostHeight: Int) {
        children.forEach { child ->
            val lp = child.view.layoutParams as? ViewGroup.MarginLayoutParams ?: return@forEach
            val childWidth = if (lp.width > 0) lp.width else hostWidth
            val childHeight = if (lp.height > 0) lp.height else hostHeight
            child.view.measure(
                View.MeasureSpec.makeMeasureSpec(childWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(childHeight, View.MeasureSpec.EXACTLY)
            )
            child.view.layout(
                lp.leftMargin,
                lp.topMargin,
                lp.leftMargin + childWidth,
                lp.topMargin + childHeight
            )
        }
    }

    /**
     * Sizes and places each child against a viewport [width] x [height] *without* writing layout
     * params, for a host whose children scroll.
     *
     * Their params stay at zero, so a layer can never lengthen the scrolling content it lives
     * inside -- which would feed back into the host's own scroll range. [applyParallax]'s
     * `pinX`/`pinY` then hold them against the viewport as that content moves under them.
     */
    fun layOutPinned(painter: BackgroundsPainter, width: Int, height: Int) {
        if (width <= 0 || height <= 0) return

        children.forEach { child ->
            val (oversizeX, oversizeY) = painter.parallaxOversize(child.parallax, width, height)
            val (leadingX, leadingY) = painter.parallaxLeadingOversize(child.parallax, width, height)
            val childWidth = width + oversizeX
            val childHeight = height + oversizeY
            child.view.measure(
                View.MeasureSpec.makeMeasureSpec(childWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(childHeight, View.MeasureSpec.EXACTLY)
            )
            child.view.layout(-leadingX, -leadingY, -leadingX + childWidth, -leadingY + childHeight)
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
     * Whether [view] is the last of these children, which is the point in a host's `drawChild`
     * where every layer below the content has been drawn and the rest of the stack can go down.
     */
    fun isLast(view: View): Boolean = view === children.lastOrNull()?.view

    /** Whether [view] is one of these children, for a host that must skip them in its own layout. */
    fun contains(view: View): Boolean = children.any { it.view === view }
}
