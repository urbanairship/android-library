/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import com.urbanairship.android.layout.R
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a focus ring around whichever view inside [root] has input focus, while the window isn't
 * in touch mode, which is when focus is being moved with a D-pad or keyboard.
 *
 * Scenes are styled by their authors, so the views' own focused states can't be relied on to be
 * visible. The ring is two-tone so at least one tone contrasts with any background, and follows
 * the focused view's shape just outside its edge, so it doesn't cover the view's own border. Along
 * an edge with no room outside, such as a full-screen pager's, it moves inside instead. Parents clip
 * their children, so it's drawn in [root]'s overlay rather than the focused view's.
 */
internal class FocusRing private constructor(private val root: ViewGroup) :
    ViewTreeObserver.OnGlobalFocusChangeListener,
    ViewTreeObserver.OnTouchModeChangeListener,
    ViewTreeObserver.OnPreDrawListener,
    View.OnAttachStateChangeListener {

    private val drawable = FocusRingDrawable(root.context)

    override fun onViewAttachedToWindow(v: View) {
        root.overlay.add(drawable)
        root.viewTreeObserver.addOnGlobalFocusChangeListener(this)
        root.viewTreeObserver.addOnTouchModeChangeListener(this)
        root.viewTreeObserver.addOnPreDrawListener(this)
        update(root.findFocus())
    }

    override fun onViewDetachedFromWindow(v: View) {
        root.viewTreeObserver.removeOnGlobalFocusChangeListener(this)
        root.viewTreeObserver.removeOnTouchModeChangeListener(this)
        root.viewTreeObserver.removeOnPreDrawListener(this)
        root.overlay.remove(drawable)
        update(null)
    }

    override fun onGlobalFocusChanged(oldFocus: View?, newFocus: View?) = update(newFocus)

    override fun onTouchModeChanged(isInTouchMode: Boolean) = update(root.findFocus())

    // A page sliding in or a scroll moves the focused view without changing focus.
    override fun onPreDraw(): Boolean {
        if (drawable.place(root)) root.invalidate()
        return true
    }

    private fun update(focused: View?) {
        drawable.target = focused?.takeIf { !it.isInTouchMode && it.isInside(root) && it.wantsRing() }
        drawable.place(root)
        root.invalidate()
    }

    private fun View.isInside(ancestor: View): Boolean {
        var view: View? = this
        while (view != null) {
            if (view === ancestor) return true
            view = view.parent as? View
        }
        return false
    }

    // A web page draws its own focus, but a video's player never gets the arrows so doesn't; scroll
    // views and pagers only take focus when the arrows move them.
    private fun View.wantsRing(): Boolean = this !is TouchAwareAirshipWebView

    companion object {

        /** Shows a focus ring on focused views inside [root] for as long as it's attached. */
        fun install(root: ViewGroup) {
            val ring = FocusRing(root)
            root.addOnAttachStateChangeListener(ring)
            if (root.isAttachedToWindow) ring.onViewAttachedToWindow(root)
        }
    }
}

/**
 * Sets [out] to the ring's outer edge: [target] grown by [reach] on every side, with any side that
 * would land outside [clip] pulled back to it.
 *
 * @param target The focused view's bounds.
 * @param clip The area the ring may draw in.
 * @param reach How far past [target] the ring's outer edge sits when there's room.
 * @param out Receives the outer edge.
 */
internal fun fitRing(target: Rect, clip: Rect, reach: Float, out: RectF) {
    out.set(
        max(target.left - reach, clip.left.toFloat()),
        max(target.top - reach, clip.top.toFloat()),
        min(target.right + reach, clip.right.toFloat()),
        min(target.bottom + reach, clip.bottom.toFloat())
    )
}

private fun View.scrollsItsContent(): Boolean =
    this is ScrollView || this is NestedScrollView || this is HorizontalScrollView || this is RecyclerView

private class FocusRingDrawable(context: Context) : Drawable() {

    var target: View? = null

    private val density = context.resources.displayMetrics.density
    private val innerWidth = INNER_WIDTH_DP * density
    private val outerWidth = OUTER_WIDTH_DP * density
    private val gap = GAP_DP * density
    /** How far past the focused view's edge the ring reaches when there's room for it. */
    private val reach = gap + innerWidth + outerWidth

    private val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = outerWidth
        color = ContextCompat.getColor(context, R.color.ua_layout_focus_ring_outer)
    }

    private val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = innerWidth
        color = ContextCompat.getColor(context, R.color.ua_layout_focus_ring_inner)
    }

    /** The focused view's bounds in the root's coordinates, empty when there's nothing to draw. */
    private val targetRect = Rect()
    /**
     * What the ring may draw over: the root, less anything a scrolling or clipping ancestor (the
     * modal's frame) hides.
     */
    private val clipRect = Rect()
    private var isOval = false
    private var radii: FloatArray? = null

    private val scratchRect = Rect()
    private val oldTarget = Rect()
    private val oldClip = Rect()
    private val outline = Outline()
    private val path = Path()
    private val ringBounds = RectF()
    private val ringRect = RectF()

    /** Recomputes where the ring goes; returns whether that changed. */
    fun place(root: ViewGroup): Boolean {
        val view = target
        if (view == null && targetRect.isEmpty) return false

        oldTarget.set(targetRect)
        oldClip.set(clipRect)

        if (view == null || view.width == 0 || view.height == 0 || !view.isShown) {
            targetRect.setEmpty()
        } else {
            // offsetDescendantRectToMyCoords takes a rect in the view's content coordinates, so
            // a view's own scroll has to be put back to land on its bounds.
            view.boundsInContent(targetRect)
            root.offsetDescendantRectToMyCoords(view, targetRect)

            clipRect.set(0, 0, root.width, root.height)
            var ancestor = view.parent as? View
            while (ancestor != null && ancestor !== root) {
                if (ancestor.scrollsItsContent() || ancestor.clipToOutline) {
                    ancestor.boundsInContent(scratchRect)
                    root.offsetDescendantRectToMyCoords(ancestor, scratchRect)
                    if (!clipRect.intersect(scratchRect)) clipRect.setEmpty()
                }
                ancestor = ancestor.parent as? View
            }

            isOval = view.getTag(R.id.ua_focus_ring_oval) == true
            radii = if (isOval) null else view.cornerRadii()
        }

        setBounds(0, 0, root.width, root.height)
        return oldTarget != targetRect || oldClip != clipRect
    }

    private fun View.boundsInContent(out: Rect) {
        out.set(scrollX, scrollY, scrollX + width, scrollY + height)
    }

    override fun draw(canvas: Canvas) {
        if (targetRect.isEmpty || clipRect.isEmpty) return

        fitRing(targetRect, clipRect, reach, ringBounds)

        canvas.save()
        canvas.clipRect(clipRect)
        stroke(canvas, inset = outerWidth + innerWidth / 2, inner)
        stroke(canvas, inset = outerWidth / 2, outer)
        canvas.restore()
    }

    /** Draws a stroke centred [inset] inside the ring's outer edge. */
    private fun stroke(canvas: Canvas, inset: Float, paint: Paint) {
        ringRect.set(ringBounds)
        ringRect.inset(inset, inset)
        val outset = reach - inset

        if (isOval) {
            canvas.drawOval(ringRect, paint)
            return
        }

        val radii = radii
        if (radii == null) {
            canvas.drawRect(ringRect, paint)
            return
        }

        path.reset()
        path.addRoundRect(ringRect, FloatArray(8) { radii[it] + outset }, Path.Direction.CW)
        canvas.drawPath(path, paint)
    }

    /**
     * Clippable views record their radii; anything else is read from its outline, which a
     * rounded or circular background provides. A button or toggle is often a bare wrapper around
     * the bordered view it shows, so a child filling the view stands in for it.
     */
    private fun View.cornerRadii(depth: Int = 0): FloatArray? {
        (getTag(R.id.ua_border_radii) as? FloatArray)?.let { return it }

        outline.setEmpty()
        outlineProvider?.getOutline(this, outline)
        if (outline.getRect(scratchRect) && outline.radius > 0f) {
            return FloatArray(8) { outline.radius }
        }

        val child = (this as? ViewGroup)?.takeIf { it.childCount == 1 }?.getChildAt(0)
        if (child == null || depth >= MAX_WRAPPER_DEPTH) return null
        if (child.width != width || child.height != height) return null
        return child.cornerRadii(depth + 1)
    }

    override fun setAlpha(alpha: Int) {
        outer.alpha = alpha
        inner.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        outer.colorFilter = colorFilter
        inner.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    private companion object {
        const val INNER_WIDTH_DP = 3f
        const val OUTER_WIDTH_DP = 3f
        const val GAP_DP = 2f
        const val MAX_WRAPPER_DEPTH = 3
    }
}
