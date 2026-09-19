/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.Airship
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.ui.LayoutViewModel
import com.urbanairship.android.layout.widget.CropImageView
import com.urbanairship.images.ImageLoader
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

/**
 * What a stack does when its children ask for more than it has.
 *
 * The overflow comes off the children that can give it, in proportion to what each asked for.
 * Handing out what is left over in order instead pays the first child in full and the last one
 * with the remainder, which is how two images of the same photo ended up different sizes.
 *
 * A percent child asks too: it is sized from what its siblings left, so a child that can give has
 * to divide the stack with it rather than be measured first and hand back the remainder.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class StackRationingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val mockEnv: ModelEnvironment = mockk(relaxed = true) {
        every { layoutState } returns LayoutState.EMPTY
        every { layoutEvents } returns emptyFlow()
        every { modelScope } returns testScope
    }
    private val viewEnv: ViewEnvironment = mockk(relaxed = true) {
        every { isIgnoringSafeAreas } returns false
        every { imageCache() } returns null
    }

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
        mockkObject(Airship)
        every { Airship.imageLoader } returns mockk<ImageLoader>(relaxed = true)
    }

    @After
    public fun tearDown() {
        unmockkObject(Airship)
        Dispatchers.resetMain()
    }

    /** Two images of the same photo, asking for the same room, end up the same size. */
    @Test
    public fun testTheOverflowIsSharedBetweenTheChildrenThatCanGive() {
        val stack = page()
        stack.measure(exactly(PAGE), exactly(PAGE))

        val media = allMedia(stack)
        assertEquals("media count", 3, media.size)
        assertEquals("the two hugging images match", media[1].measuredHeight, media[2].measuredHeight)
        assertTrue("and they gave something", media[1].measuredHeight < PAGE / 2)
    }

    /** A band that states its height has nothing to give, so it keeps it. */
    @Test
    public fun testAStatedLengthIsNotRationed() {
        val stack = page()
        stack.measure(exactly(PAGE), exactly(PAGE))

        assertEquals("band height", BAND, allMedia(stack).first().measuredHeight)
    }

    /** Nothing is laid out past the stack, so nothing is cut off. */
    @Test
    public fun testTheChildrenFitTheStack() {
        val stack = page()
        stack.measure(exactly(PAGE), exactly(PAGE))

        val total = (0 until stack.childCount).sumOf { stack.getChildAt(it).measuredHeight }
        assertTrue("content fits: $total", total <= PAGE)
    }

    /**
     * A child that can't give is measured before the ones that can, whatever the order it is
     * written in: the flexible children divide what is left, not the other way round.
     */
    @Test
    public fun testAChildThatCannotGiveIsNotSqueezedOut() {
        val stack = page()
        stack.measure(exactly(PAGE), exactly(PAGE))

        val trailing = requireNotNull(findLabel(stack, TRAILING)) { "no trailing label" }
        assertTrue("trailing label has a height", trailing.measuredHeight > 0)
    }

    /** A parent hugging an image that gave something back is no wider than the image it holds. */
    @Test
    public fun testAHuggingParentFollowsTheImageItHolds() {
        val stack = page()
        stack.measure(exactly(PAGE), exactly(PAGE))

        for (media in allMedia(stack).drop(1)) {
            val parent = media.parent as View
            assertEquals("parent width", media.measuredWidth, parent.measuredWidth)
        }
    }

    /** A row divides what it has the same way a column does. */
    @Test
    public fun testARowSharesItsOverflowToo() {
        val row = row()
        row.measure(exactly(PAGE), exactly(PAGE))

        val media = allMedia(row)
        assertEquals("media count", 3, media.size)
        assertEquals("band width", BAND, media.first().measuredWidth)
        assertEquals("the two hugging images match", media[1].measuredWidth, media[2].measuredWidth)

        val trailing = requireNotNull(findLabel(row, TRAILING)) { "no trailing label" }
        assertTrue("trailing label has a width", trailing.measuredWidth > 0)
    }

    /** A child at the whole and an image both want all of it, so they take half each. */
    @Test
    public fun testAPercentChildAndAnImageDivideTheStack() {
        val stack = shareBesideImage(vertical = true)
        stack.measure(exactly(PAGE), exactly(PAGE))

        assertEquals("share height", PAGE / 2, stack.getChildAt(0).measuredHeight)
        assertEquals("image height", PAGE / 2, allMedia(stack).single().measuredHeight)
    }

    /** A row divides it the same way. */
    @Test
    public fun testAPercentChildAndAnImageDivideTheRow() {
        val row = shareBesideImage(vertical = false)
        row.measure(exactly(PAGE), exactly(PAGE))

        assertEquals("share width", PAGE / 2, row.getChildAt(0).measuredWidth)
        assertEquals("image width", PAGE / 2, allMedia(row).single().measuredWidth)
    }

    /**
     * A ratio child is measured last of all, off what the percent children leave, so its claim has
     * to be counted while they are being paid. Inside its share it keeps the length the ratio asks
     * for, and the percent child takes the rest.
     */
    @Test
    public fun testARatioChildInsideItsShareKeepsItsLength() {
        val stack = shareBesideRatio(ratio = 4)
        stack.measure(exactly(PAGE), exactly(PAGE))

        assertEquals("ratio height", PAGE / 4, stack.getChildAt(1).measuredHeight)
        assertEquals("share height", PAGE - PAGE / 4, stack.getChildAt(0).measuredHeight)
    }

    /** Past its share it is cut like anything else, and takes the other axis down with it. */
    @Test
    public fun testARatioChildPastItsShareIsCutDownWithTheRest() {
        val stack = shareBesideRatio(ratio = 0.5)
        stack.measure(exactly(PAGE), exactly(PAGE))

        assertEquals("share height", PAGE / 2, stack.getChildAt(0).measuredHeight)
        assertEquals("ratio height", PAGE / 2, stack.getChildAt(1).measuredHeight)
        assertEquals("ratio keeps its shape", PAGE / 4, stack.getChildAt(1).measuredWidth)
    }

    /**
     * A length the ratio takes from the cross axis is still a share of the stack axis, not a claim
     * on it: it divides the row with a percent sibling rather than being paid before one.
     */
    @Test
    public fun testARatioChildDividesARowEvenWhenItsLengthWouldFit() {
        val row = build(
            """
            {
              "type": "linear_layout",
              "direction": "horizontal",
              "items": [
                {"size": {"width": "auto", "height": "100%", "aspect_ratio": 1}, "view": $SHARE},
                {"size": {"width": "100%", "height": "100%"}, "view": $SHARE}
              ]
            }
            """.trimIndent()
        )
        // A square off the row's height would fit the row with room to spare, at 800 of 1000.
        row.measure(exactly(PAGE), exactly(800))

        assertEquals("ratio width", PAGE / 2, row.getChildAt(0).measuredWidth)
        assertEquals("ratio keeps its shape", PAGE / 2, row.getChildAt(0).measuredHeight)
        assertEquals("share width", PAGE / 2, row.getChildAt(1).measuredWidth)
    }

    /** A child that fits inside its share keeps its content, and the percent child takes the rest. */
    @Test
    public fun testAPercentChildTakesWhatACaptionLeaves() {
        val stack = shareBesideCaption()
        stack.measure(exactly(PAGE), exactly(PAGE))

        val caption = requireNotNull(findLabel(stack, TRAILING)) { "no caption" }
        val height = caption.measuredHeight
        assertTrue("caption keeps its line: $height", height > 0 && height < PAGE / 2)
        assertEquals("the share takes the rest", PAGE - height, stack.getChildAt(0).measuredHeight)
    }

    /**
     * A stack whose stated lengths already overrun it between them runs off its own end: nothing
     * can be rationed, so a child that hugs its content keeps it and the last of them is clipped.
     * Zeroing the hugging children instead dropped a caption out of the scene whole.
     */
    @Test
    public fun testAnUnrationableOverflowRunsOffTheEnd() {
        val stack = overfull()
        stack.measure(exactly(PAGE), exactly(BAND + BAND / 2))

        val caption = requireNotNull(findLabel(stack, TRAILING)) { "no caption" }
        assertTrue("caption keeps its height", caption.measuredHeight > 0)
        assertEquals("band keeps its stated length", BAND, stack.getChildAt(1).measuredHeight)
        assertEquals("band keeps its stated length", BAND, stack.getChildAt(2).measuredHeight)
    }

    private fun findLabel(view: View, text: String): View? {
        if (view is android.widget.TextView && view.text?.toString() == text) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findLabel(view.getChildAt(i), text)?.let { return it }
        }
        return null
    }

    private fun exactly(size: Int) = View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY)

    private fun allMedia(view: View, into: MutableList<MediaView> = mutableListOf()): List<MediaView> {
        if (view is MediaView) into.add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) allMedia(view.getChildAt(i), into)
        return into
    }

    private fun forEachImage(view: View, block: (CropImageView) -> Unit) {
        if (view is CropImageView) block(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) forEachImage(view.getChildAt(i), block)
    }

    /** A band of stated height, then two images left to take their own shape. */
    private fun page(): ViewGroup {
        val json = """
            {
              "type": "linear_layout",
              "direction": "vertical",
              "items": [
                {
                  "size": {"width": "100%", "height": "auto"},
                  "view": {
                    "type": "linear_layout",
                    "direction": "vertical",
                    "items": [{"size": {"width": "100%", "height": $BAND}, "view": $MEDIA}]
                  }
                },
                $HUGGING,
                $HUGGING,
                {
                  "size": {"width": "100%", "height": "auto"},
                  "view": {
                    "type": "label", "text": "$TRAILING",
                    "text_appearance": {"font_size": 14,
                      "color": {"default": {"type": "hex", "hex": "#000000", "alpha": 1}}}
                  }
                }
              ]
            }
        """.trimIndent()

        return build(json)
    }

    private fun build(json: String): ViewGroup {
        val info = ViewInfo.viewInfoFromJson(JsonValue.parseString(json).requireMap())
        val view = LayoutViewModel().getOrCreateModel(info, mockEnv)
            .createView(context, viewEnv, null) as ViewGroup

        Robolectric.buildActivity(Activity::class.java).setup().get().setContentView(view)
        forEachImage(view) { it.setImageDrawable(image()) }

        return view
    }

    /** The same shape as [page], laid out along the other axis. */
    private fun row(): ViewGroup = build(
        """
        {
          "type": "linear_layout",
          "direction": "horizontal",
          "items": [
            {"size": {"width": $BAND, "height": "auto"}, "view": $MEDIA},
            $HUGGING_ROW,
            $HUGGING_ROW,
            {
              "size": {"width": "auto", "height": "auto"},
              "view": {
                "type": "label", "text": "$TRAILING",
                "text_appearance": {"font_size": 14,
                  "color": {"default": {"type": "hex", "hex": "#000000", "alpha": 1}}}
              }
            }
          ]
        }
        """.trimIndent()
    )

    /** A child at the whole of the stack axis, beside an image that wants more than it can have. */
    private fun shareBesideImage(vertical: Boolean): ViewGroup = build(
        """
        {
          "type": "linear_layout",
          "direction": "${if (vertical) "vertical" else "horizontal"}",
          "items": [
            {"size": {"width": "100%", "height": "100%"}, "view": $SHARE},
            {
              "size": ${if (vertical) """{"width": "100%", "height": "auto"}"""
                        else """{"width": "auto", "height": "100%"}"""},
              "view": $MEDIA
            }
          ]
        }
        """.trimIndent()
    )

    /** A child at the whole of the stack axis, beside one whose length is its width over [ratio]. */
    private fun shareBesideRatio(ratio: Number): ViewGroup = build(
        """
        {
          "type": "linear_layout",
          "direction": "vertical",
          "items": [
            {"size": {"width": "100%", "height": "100%"}, "view": $SHARE},
            {"size": {"width": "100%", "height": "auto", "aspect_ratio": $ratio}, "view": $SHARE}
          ]
        }
        """.trimIndent()
    )

    /** The same, with a line of text in place of the image. */
    private fun shareBesideCaption(): ViewGroup = build(
        """
        {
          "type": "linear_layout",
          "direction": "vertical",
          "items": [
            {"size": {"width": "100%", "height": "100%"}, "view": $SHARE},
            {
              "size": {"width": "100%", "height": "auto"},
              "view": {
                "type": "label", "text": "$TRAILING",
                "text_appearance": {"font_size": 14,
                  "color": {"default": {"type": "hex", "hex": "#000000", "alpha": 1}}}
              }
            }
          ]
        }
        """.trimIndent()
    )

    /** A caption above two stated lengths that already overrun the stack between them. */
    private fun overfull(): ViewGroup = build(
        """
        {
          "type": "linear_layout",
          "direction": "vertical",
          "items": [
            {
              "size": {"width": "100%", "height": "auto"},
              "view": {
                "type": "label", "text": "$TRAILING",
                "text_appearance": {"font_size": 14,
                  "color": {"default": {"type": "hex", "hex": "#000000", "alpha": 1}}}
              }
            },
            {"size": {"width": "100%", "height": $BAND}, "view": $SHARE},
            {"size": {"width": "100%", "height": $BAND}, "view": $SHARE}
          ]
        }
        """.trimIndent()
    )

    private fun image(): Drawable = object : ColorDrawable(Color.RED) {
        override fun getIntrinsicWidth(): Int = 400
        override fun getIntrinsicHeight(): Int = 400
    }

    private companion object {
        private const val PAGE = 1000
        private const val BAND = 100
        private const val TRAILING = "What the images left room for."

        private val SHARE = """
            {"type": "empty_view"}
        """.trimIndent()

        private val MEDIA = """
            {"type": "media", "media_type": "image", "media_fit": "fit_crop",
             "url": "https://example.com/photo.jpg"}
        """.trimIndent()

        private val HUGGING_ROW = """
            {
              "size": {"width": "auto", "height": "auto"},
              "view": {
                "type": "linear_layout",
                "direction": "horizontal",
                "items": [{"size": {"width": "auto", "height": "auto"}, "view": $MEDIA}]
              }
            }
        """.trimIndent()

        private val HUGGING = """
            {
              "size": {"width": "auto", "height": "auto"},
              "view": {
                "type": "linear_layout",
                "direction": "vertical",
                "items": [{"size": {"width": "auto", "height": "auto"}, "view": $MEDIA}]
              }
            }
        """.trimIndent()
    }
}
