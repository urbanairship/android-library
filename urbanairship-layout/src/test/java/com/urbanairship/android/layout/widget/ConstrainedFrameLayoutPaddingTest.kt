/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.property.ConstrainedSize
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Verifies that a wrap-content [ConstrainedFrameLayout] includes its own padding in its measured
 * size. Children are measured inside the padding, so sizing the view to the largest child left it
 * short by the padding, and the child was then squeezed by that amount at layout time.
 *
 * This is how a modal with `height: auto` and a border stroke lost twice the stroke width of
 * content height: the stroke is applied as padding on the frame, so a scroll layout that exactly
 * fit its content became scrollable by 2x the stroke. A large enough `min_height` masked it by
 * leaving slack above the content's natural height.
 */
@RunWith(RobolectricTestRunner::class)
public class ConstrainedFrameLayoutPaddingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /** 5dp stroke at 3x density, as applied by `LayoutUtils.applyBorderAndBackground`. */
    private val strokePadding = 15

    @Test
    public fun testWrapContentHeightIncludesPadding() {
        val layout = constrainedFrameLayout(size(height = "auto"))
        layout.setPadding(strokePadding, strokePadding, strokePadding, strokePadding)

        val child = View(context)
        layout.addView(child, FrameLayout.LayoutParams(MATCH_PARENT, 1180))

        layout.measure(exactly(864), atMost(2176))

        assertEquals(1180 + strokePadding * 2, layout.measuredHeight)
    }

    @Test
    public fun testWrapContentHeightDoesNotSqueezeChild() {
        val layout = constrainedFrameLayout(size(height = "auto"))
        layout.setPadding(strokePadding, strokePadding, strokePadding, strokePadding)

        // Mirrors the modal: a match-parent container wrapping content of a natural height.
        val container = FrameLayout(context)
        container.addView(View(context), FrameLayout.LayoutParams(MATCH_PARENT, 1180))
        layout.addView(container, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))

        layout.measure(exactly(864), atMost(2176))

        assertEquals(1180 + strokePadding * 2, layout.measuredHeight)
        // The container keeps the full height it asked for, rather than losing the padding.
        assertEquals(1180, container.measuredHeight)
    }

    @Test
    public fun testWrapContentWidthIncludesPadding() {
        val layout = constrainedFrameLayout(size(width = "auto"))
        layout.setPadding(strokePadding, strokePadding, strokePadding, strokePadding)

        val child = View(context)
        layout.addView(child, FrameLayout.LayoutParams(640, MATCH_PARENT))

        layout.measure(atMost(1080), exactly(1180))

        assertEquals(640 + strokePadding * 2, layout.measuredWidth)
    }

    @Test
    public fun testMinHeightStillWinsOverPaddedContent() {
        val layout = constrainedFrameLayout(size(height = "auto", minHeight = "70%"))
        layout.setPadding(strokePadding, strokePadding, strokePadding, strokePadding)

        val child = View(context)
        layout.addView(child, FrameLayout.LayoutParams(MATCH_PARENT, 1180))

        layout.measure(exactly(864), atMost(2176))

        // 70% of the 2176px available, which is taller than the padded content (1210).
        assertEquals(1523, layout.measuredHeight)
    }

    @Test
    public fun testPaddingIsNotClampedToAvailableSpace() {
        val layout = constrainedFrameLayout(size(height = "auto"))
        layout.setPadding(strokePadding, strokePadding, strokePadding, strokePadding)

        // A child with an absolute size takes it regardless of what the parent offered.
        val child = View(context)
        layout.addView(child, FrameLayout.LayoutParams(MATCH_PARENT, 2176))

        layout.measure(exactly(864), atMost(2176))

        // The border grows the view past the space available rather than eating into the
        // content, which is what iOS does with the same scene: `max_height` bounds the
        // content and the border padding is added outside it. Clamping here would put the
        // squeeze back for exactly the layouts that can least afford it.
        assertEquals(2176 + strokePadding * 2, layout.measuredHeight)
    }

    private fun constrainedFrameLayout(size: ConstrainedSize): ConstrainedFrameLayout =
        ConstrainedFrameLayout(context, size).apply {
            layoutParams = ViewGroup.LayoutParams(
                if (size.width.isAuto) WRAP_CONTENT else MATCH_PARENT,
                if (size.height.isAuto) WRAP_CONTENT else MATCH_PARENT
            )
        }

    private fun size(
        width: String = "100%",
        height: String = "100%",
        minHeight: String? = null
    ): ConstrainedSize = ConstrainedSize(width, height, null, minHeight, null, null)

    private fun exactly(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private fun atMost(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.AT_MOST)

    private companion object {
        const val MATCH_PARENT = ViewGroup.LayoutParams.MATCH_PARENT
        const val WRAP_CONTENT = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
