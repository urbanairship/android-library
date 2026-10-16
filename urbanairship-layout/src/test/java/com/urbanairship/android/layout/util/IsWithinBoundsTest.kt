/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.util

import android.content.Context
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
public class IsWithinBoundsTest {

    /** A window's root view, placed on the display at ([screenX], [screenY]). */
    private class WindowRoot(
        context: Context,
        private val screenX: Int,
        private val screenY: Int
    ) : FrameLayout(context) {
        override fun getLocationOnScreen(outLocation: IntArray) {
            outLocation[0] = screenX
            outLocation[1] = screenY
        }
    }

    private val events = mutableListOf<MotionEvent>()

    @After
    public fun tearDown() {
        events.forEach { it.recycle() }
    }

    /**
     * A 100 × 50 child at (10, 20) in a full-size container, in a window placed on the display at
     * ([screenX], [screenY]).
     */
    private fun childOfWindowAt(screenX: Int, screenY: Int, configure: (FrameLayout) -> Unit = {}): View {
        val context = RuntimeEnvironment.getApplication()
        val root = WindowRoot(context, screenX, screenY)
        val container = FrameLayout(context).also(configure)
        val child = View(context)
        container.addView(child, FrameLayout.LayoutParams(100, 50).apply {
            leftMargin = 10
            topMargin = 20
        })
        root.addView(container, FrameLayout.LayoutParams(400, 400))
        root.measure(
            MeasureSpec.makeMeasureSpec(400, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(400, MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, 400, 400)
        return child
    }

    private fun touch(rawX: Float, rawY: Float): MotionEvent =
        MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, rawX, rawY, 0).also { events.add(it) }

    @Test
    public fun testWindowAtDisplayOrigin() {
        val child = childOfWindowAt(0, 0)

        assertTrue(touch(60f, 45f).isWithinBounds(child))
        assertFalse(touch(200f, 45f).isWithinBounds(child))
    }

    @Test
    public fun testWindowOffsetOnDisplay() {
        val child = childOfWindowAt(892, 103)

        assertTrue(touch(952f, 148f).isWithinBounds(child))
        assertFalse(touch(60f, 45f).isWithinBounds(child))
    }

    @Test
    public fun testClickableDescendantInOffsetWindow() {
        val child = childOfWindowAt(892, 103).apply { isClickable = true }
        val container = child.parent as View

        assertTrue(touch(952f, 148f).isWithinClickableDescendantOf(container))
        assertFalse(touch(60f, 45f).isWithinClickableDescendantOf(container))
    }

    @Test
    public fun testDescendantClippedByParent() {
        val child = childOfWindowAt(892, 103).apply { translationX = -60f }

        assertFalse(touch(870f, 148f).isWithinBounds(child))
        assertTrue(touch(920f, 148f).isWithinBounds(child))
    }

    @Test
    public fun testScaledAncestor() {
        val child = childOfWindowAt(892, 103) {
            it.pivotX = 0f
            it.pivotY = 0f
            it.scaleX = 0.5f
            it.scaleY = 0.5f
        }

        assertTrue(touch(932f, 125f).isWithinBounds(child))
        assertFalse(touch(982f, 148f).isWithinBounds(child))
    }
}
