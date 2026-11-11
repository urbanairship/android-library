/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Looper
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.platform.app.InstrumentationRegistry
import com.urbanairship.android.layout.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Where [FocusRing] draws its two strokes for a focused view, read back from the root's overlay. */
@RunWith(RobolectricTestRunner::class)
public class FocusRingTest {

    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val root = FrameLayout(activity)

    private val density get() = activity.resources.displayMetrics.density
    private val inner get() = 3f * density
    private val outer get() = 3f * density
    private val gap get() = 2f * density
    private val innerOutset get() = gap + inner / 2
    private val outerOutset get() = gap + inner + outer / 2

    @Before
    public fun setUp() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        activity.setContentView(root, FrameLayout.LayoutParams(SIZE, SIZE))
        FocusRing.install(root)
    }

    @Test
    public fun testAViewWithRoomGetsARingOutsideIt() {
        val view = focusableView().also { add(it) }
        view.requestFocus()

        val rects = render().rects
        assertEquals(
            listOf(Shape(around(innerOutset), innerColor()), Shape(around(outerOutset), outerColor())),
            rects
        )
    }

    @Test
    public fun testAPagerFillingTheRootGetsARingInsideItsEdges() {
        val pager = RecyclerView(activity).apply { isFocusable = true }
        root.addView(pager, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        layout()
        pager.requestFocus()

        val outerInset = outer / 2
        val innerInset = outer + inner / 2
        assertEquals(
            listOf(
                Shape(RectF(innerInset, innerInset, SIZE - innerInset, SIZE - innerInset), innerColor()),
                Shape(RectF(outerInset, outerInset, SIZE - outerInset, SIZE - outerInset), outerColor()),
            ),
            render().rects
        )
    }

    @Test
    public fun testAViewFlushWithOneEdgeGetsThatSideInside() {
        val close = focusableView()
        root.addView(close, FrameLayout.LayoutParams(40, 40).apply { leftMargin = 100 })
        layout()
        close.requestFocus()

        assertEquals(
            RectF(100 - outerOutset, outer / 2, 140 + outerOutset, 40 + outerOutset),
            render().rects.last().bounds
        )
    }

    @Test
    public fun testRingFollowsFocus() {
        val first = focusableView().also { add(it) }
        val second = focusableView().also { add(it, top = TOP + 100) }
        first.requestFocus()
        second.requestFocus()

        val rects = render().rects
        assertEquals(2, rects.size)
        assertEquals(TOP + 100 - innerOutset, rects[0].bounds.top)
    }

    @Test
    public fun testRingFollowsTheViewWhenItMoves() {
        val view = focusableView().also { add(it) }
        view.requestFocus()
        render()

        // A pager page slides in, and a scroll view scrolls, by moving its children's bounds.
        view.offsetTopAndBottom(20)
        assertEquals(TOP + 20 - innerOutset, render().rects[0].bounds.top)
    }

    @Test
    public fun testNoRingOnceFocusLeavesTheRoot() {
        val view = focusableView().also { add(it) }
        val outside = focusableView()
        activity.addContentView(outside, FrameLayout.LayoutParams(WIDTH, HEIGHT))
        view.requestFocus()
        render()

        outside.requestFocus()
        assertTrue(render().isEmpty)
    }

    @Test
    public fun testAnOvalViewGetsAnOvalRing() {
        val view = focusableView().apply { setTag(R.id.ua_focus_ring_oval, true) }
        add(view)
        view.requestFocus()

        val canvas = render()
        assertEquals(0, canvas.rects.size)
        assertEquals(2, canvas.ovals.size)
        assertEquals(Shape(around(innerOutset), innerColor()), canvas.ovals[0])
    }

    @Test
    public fun testARoundedViewGetsARoundedRing() {
        val view = focusableView().apply { setTag(R.id.ua_border_radii, FloatArray(8) { 8f }) }
        add(view)
        view.requestFocus()

        val canvas = render()
        assertEquals(0, canvas.rects.size)
        assertEquals(2, canvas.paths.size)
        assertEquals(Shape(around(innerOutset), innerColor()), canvas.paths[0])
    }

    @Test
    public fun testRingStaysInsideAScrollingAncestor() {
        val scroll = ScrollView(activity)
        root.addView(scroll, FrameLayout.LayoutParams(SIZE, 200).apply { topMargin = 50 })
        val content = FrameLayout(activity)
        scroll.addView(content, FrameLayout.LayoutParams(SIZE, 1000))
        val view = focusableView()
        content.addView(view, FrameLayout.LayoutParams(WIDTH, HEIGHT).apply { leftMargin = LEFT })
        layout()
        view.requestFocus()

        val canvas = render()
        // The overlay draws last, so the ring's clip is the last one.
        assertEquals(Rect(0, 50, SIZE, 250), canvas.clips.last())
        assertEquals(50 + outer / 2, canvas.rects.last().bounds.top)
    }

    private fun focusableView() = View(activity).apply { isFocusable = true }

    private fun add(view: View, top: Int = TOP) {
        root.addView(view, FrameLayout.LayoutParams(WIDTH, HEIGHT).apply {
            leftMargin = LEFT
            topMargin = top
        })
        layout()
    }

    private fun layout() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun render(): RecordingCanvas {
        layout()
        root.viewTreeObserver.dispatchOnPreDraw()
        return RecordingCanvas().also { root.draw(it) }
    }

    /** The default view's bounds grown by [outset] on every side. */
    private fun around(outset: Float) =
        RectF(LEFT - outset, TOP - outset, LEFT + WIDTH + outset, TOP + HEIGHT + outset)

    private fun innerColor() = ContextCompat.getColor(activity, R.color.ua_layout_focus_ring_inner)
    private fun outerColor() = ContextCompat.getColor(activity, R.color.ua_layout_focus_ring_outer)

    private data class Shape(val bounds: RectF, val color: Int)

    private class RecordingCanvas : Canvas(Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)) {
        val rects = mutableListOf<Shape>()
        val ovals = mutableListOf<Shape>()
        val paths = mutableListOf<Shape>()
        val clips = mutableListOf<Rect>()

        val isEmpty: Boolean
            get() = rects.isEmpty() && ovals.isEmpty() && paths.isEmpty()

        override fun clipRect(rect: Rect): Boolean {
            clips += Rect(rect)
            return super.clipRect(rect)
        }

        override fun drawRect(rect: RectF, paint: Paint) {
            rects += Shape(RectF(rect), paint.color)
        }

        override fun drawOval(oval: RectF, paint: Paint) {
            ovals += Shape(RectF(oval), paint.color)
        }

        override fun drawPath(path: Path, paint: Paint) {
            val bounds = RectF()
            path.computeBounds(bounds, true)
            paths += Shape(bounds, paint.color)
        }
    }

    private companion object {
        const val SIZE = 400
        const val LEFT = 100
        const val TOP = 100
        const val WIDTH = 100
        const val HEIGHT = 50
    }
}
