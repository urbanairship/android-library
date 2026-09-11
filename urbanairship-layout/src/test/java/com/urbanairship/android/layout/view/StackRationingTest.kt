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

        val info = ViewInfo.viewInfoFromJson(JsonValue.parseString(json).requireMap())
        val view = LayoutViewModel().getOrCreateModel(info, mockEnv)
            .createView(context, viewEnv, null) as ViewGroup

        Robolectric.buildActivity(Activity::class.java).setup().get().setContentView(view)
        forEachImage(view) { it.setImageDrawable(image()) }

        return view
    }

    private fun image(): Drawable = object : ColorDrawable(Color.RED) {
        override fun getIntrinsicWidth(): Int = 400
        override fun getIntrinsicHeight(): Int = 400
    }

    private companion object {
        private const val PAGE = 1000
        private const val BAND = 100
        private const val TRAILING = "What the images left room for."

        private val MEDIA = """
            {"type": "media", "media_type": "image", "media_fit": "fit_crop",
             "url": "https://example.com/photo.jpg"}
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
