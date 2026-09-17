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
 * How an image is sized against what the item asked for and what its parent has to offer.
 *
 * A stated length is a box the image is cropped into. An axis left `auto` takes the image's own
 * shape at the width it was offered, unless there is a ceiling to crop into — and a maximum a
 * parent arrived at by measuring is not one, since the image is part of the content it was taken
 * from. iOS decides this in `shouldShowMediaWhole`, web in `autoMedia`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class MediaSizingTest {

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

    /** A stated length on both axes is a box, whatever the image turns out to be. */
    @Test
    public fun testStatedLengthsAreTheBox() {
        val stack = stack(
            itemSize = FULL_WIDTH,
            mediaItemSize = """{"width": "100%", "height": 60}"""
        )
        measure(stack, PAGE, exactly = true)

        val media = requireNotNull(findMedia(stack))
        assertEquals("width", PAGE, media.measuredWidth)
        assertEquals("height", 60, media.measuredHeight)
    }

    /** An `auto` axis opposite a stated one takes the image's proportions when nothing caps it. */
    @Test
    public fun testAutoHeightTakesTheImagesProportions() {
        val stack = stack(
            itemSize = FULL_WIDTH,
            mediaItemSize = """{"width": "100%", "height": "auto"}"""
        )
        stack.measure(spec(PAGE, View.MeasureSpec.EXACTLY), spec(0, View.MeasureSpec.UNSPECIFIED))

        val media = requireNotNull(findMedia(stack))
        assertEquals("width", PAGE, media.measuredWidth)
        assertEquals("height", PAGE * MEDIA_HEIGHT / MEDIA_WIDTH, media.measuredHeight)
    }

    /**
     * The bug: `auto` on both axes inside a box the author stated. The image is cropped to fill
     * the box, where sizing it to its own proportions left it a square of grey to either side.
     */
    @Test
    public fun testAutoInAStatedBoxCropsToFillIt() {
        val stack = stack(
            itemSize = """{"width": "100%", "height": $BOX_HEIGHT}""",
            mediaItemSize = AUTO
        )
        measure(stack, PAGE, exactly = true)

        val media = requireNotNull(findMedia(stack))
        assertEquals("width", PAGE, media.measuredWidth)
        assertEquals("height", BOX_HEIGHT, media.measuredHeight)
    }

    /**
     * A maximum an auto-sized parent measured its way to is the siblings' extent handed back, not
     * a box: the image takes its own shape at the width it was offered, rather than a crop.
     */
    @Test
    public fun testAutoInAnAutoParentTakesTheImagesShape() {
        val stack = stack(itemSize = AUTO, mediaItemSize = AUTO)
        measure(stack, PAGE, exactly = false)

        val media = requireNotNull(findMedia(stack))
        assertEquals("width", PAGE, media.measuredWidth)
        assertEquals("height", PAGE * MEDIA_HEIGHT / MEDIA_WIDTH, media.measuredHeight)
    }

    /**
     * A stack that has settled its own length remeasures its children against it. An item that
     * stated `auto` is one of the children that length was measured from, so an `EXACTLY` spec
     * arriving that way is not a box the author asked the image to be cropped into.
     */
    @Test
    public fun testARemeasureIsNotAStatedBox() {
        val stack = stack(itemSize = AUTO, mediaItemSize = AUTO)
        stack.measure(spec(PAGE, View.MeasureSpec.EXACTLY), spec(PAGE, View.MeasureSpec.EXACTLY))

        val media = requireNotNull(findMedia(stack))
        assertEquals("width", PAGE, media.measuredWidth)
        assertEquals("height", PAGE * MEDIA_HEIGHT / MEDIA_WIDTH, media.measuredHeight)
    }

    /** An image never draws past a ceiling that is really there, whole or cropped. */
    @Test
    public fun testWholeImageIsCappedByARealCeiling() {
        val stack = stack(
            itemSize = """{"width": ${MEDIA_WIDTH / 2}, "height": "auto"}""",
            mediaItemSize = AUTO
        )
        measure(stack, PAGE, exactly = true)

        val media = requireNotNull(findMedia(stack))
        assertEquals("width", MEDIA_WIDTH / 2, media.measuredWidth)
        assertEquals("height", MEDIA_HEIGHT / 2, media.measuredHeight)
    }

    /** A ratio the item declared is the shape the image is cropped to, whatever shape it is. */
    @Test
    public fun testADeclaredRatioIsTheShape() {
        val stack = stack(
            itemSize = FULL_WIDTH,
            mediaItemSize = """{"width": "auto", "height": "auto", "aspect_ratio": 4}"""
        )
        measure(stack, PAGE, exactly = true)

        val media = requireNotNull(findMedia(stack))
        assertEquals("width", PAGE, media.measuredWidth)
        assertEquals("height", PAGE / 4, media.measuredHeight)
    }

    /** An image in a row leaves the label beside it its own width. */
    @Test
    public fun testImageBesideALabelLeavesRoomForIt() {
        val row = row()
        measure(row, PAGE, exactly = true)

        val media = requireNotNull(findMedia(row))
        assertEquals("width", MEDIA_WIDTH, media.measuredWidth)
        assertTrue("label keeps its width", row.getChildAt(1).measuredWidth > 0)
    }

    private fun measure(view: View, width: Int, exactly: Boolean) {
        val mode = if (exactly) View.MeasureSpec.EXACTLY else View.MeasureSpec.AT_MOST
        view.measure(spec(width, mode), spec(PAGE, View.MeasureSpec.AT_MOST))
    }

    private fun spec(size: Int, mode: Int) = View.MeasureSpec.makeMeasureSpec(size, mode)

    private fun findMedia(view: View): MediaView? {
        if (view is MediaView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findMedia(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun findImage(view: View): CropImageView? {
        if (view is CropImageView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findImage(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    /** A vertical stack holding one item, which holds the media. */
    private fun stack(itemSize: String, mediaItemSize: String = AUTO): ViewGroup = build(
        """
        {
          "type": "linear_layout",
          "direction": "vertical",
          "items": [
            {
              "size": $itemSize,
              "view": {
                "type": "linear_layout",
                "direction": "vertical",
                "items": [{"size": $mediaItemSize, "view": $MEDIA}]
              }
            }
          ]
        }
        """.trimIndent()
    )

    /** The media beside a label, both `auto`, in a row as wide as the page. */
    private fun row(): ViewGroup = build(
        """
        {
          "type": "linear_layout",
          "direction": "horizontal",
          "items": [
            {"size": $AUTO, "view": $MEDIA},
            {
              "size": $AUTO,
              "view": {
                "type": "label",
                "text": "A caption beside the photo.",
                "text_appearance": {"font_size": 12, "color": {"default": {"type": "hex", "hex": "#000000", "alpha": 1}}}
              }
            }
          ]
        }
        """.trimIndent()
    )

    private fun build(json: String): ViewGroup {
        val info = ViewInfo.viewInfoFromJson(JsonValue.parseString(json).requireMap())
        val view = LayoutViewModel().getOrCreateModel(info, mockEnv)
            .createView(context, viewEnv, null) as ViewGroup

        // The image view is inflated on attach, and it holds the drawable everything is sized from.
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.setContentView(view)
        findImage(view)?.setImageDrawable(image())

        return view
    }

    private fun image(): Drawable = object : ColorDrawable(Color.RED) {
        override fun getIntrinsicWidth(): Int = MEDIA_WIDTH
        override fun getIntrinsicHeight(): Int = MEDIA_HEIGHT
    }

    private companion object {
        private const val PAGE = 1000
        private const val BOX_HEIGHT = 300

        private const val MEDIA_WIDTH = 400
        private const val MEDIA_HEIGHT = 200

        private const val AUTO = """{"width": "auto", "height": "auto"}"""
        private const val FULL_WIDTH = """{"width": "100%", "height": "auto"}"""

        private val MEDIA = """
            {
              "type": "media",
              "media_type": "image",
              "media_fit": "fit_crop",
              "url": "https://example.com/photo.jpg"
            }
        """.trimIndent()
    }
}
