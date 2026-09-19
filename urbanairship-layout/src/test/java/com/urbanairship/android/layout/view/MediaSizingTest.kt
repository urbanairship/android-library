/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView.ScaleType
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

    /**
     * A ceiling is just as real for an item in a container as for one in a stack.
     *
     * `ContainerLayoutView` wraps every item in a frame and gives it `WRAP_CONTENT` for an `auto`
     * length, so the walk looking for a content-sized ancestor finds one on its first hop and stops
     * there. That frame holds this media and nothing else: its `WRAP_CONTENT` is the item restating
     * its own `auto` height, not a parent that measured this image as part of its content.
     */
    @Test
    public fun testAutoInAContainerIsCappedByARealCeiling() {
        val container = container(mediaItemSize = FULL_WIDTH)
        container.measure(
            spec(PAGE, View.MeasureSpec.EXACTLY),
            spec(BOX_HEIGHT, View.MeasureSpec.EXACTLY)
        )

        val media = requireNotNull(findMedia(container))
        assertEquals("width", PAGE, media.measuredWidth)
        assertEquals("height", BOX_HEIGHT, media.measuredHeight)
    }

    /** With no ceiling to crop into, an item in a container still takes the image's proportions. */
    @Test
    public fun testAutoInAContainerWithNoCeilingTakesTheImagesProportions() {
        val container = container(mediaItemSize = FULL_WIDTH)
        container.measure(
            spec(PAGE, View.MeasureSpec.EXACTLY),
            spec(0, View.MeasureSpec.UNSPECIFIED)
        )

        val media = requireNotNull(findMedia(container))
        assertEquals("width", PAGE, media.measuredWidth)
        assertEquals("height", PAGE * MEDIA_HEIGHT / MEDIA_WIDTH, media.measuredHeight)
    }

    /**
     * `center_inside` never crops, so a box cut down to a ceiling is wider than the image painted
     * inside it. That slack is the only room left for the item to be positioned in, so it is the
     * item's own position that decides where in the box the image lands.
     */
    @Test
    public fun testWholeImageIsAnchoredWhereTheItemAsked() {
        val container = container(mediaItemSize = FULL_WIDTH, itemPosition = START, media = WHOLE)
        container.measure(
            spec(PAGE, View.MeasureSpec.EXACTLY),
            spec(BOX_HEIGHT, View.MeasureSpec.EXACTLY)
        )
        container.layout(0, 0, PAGE, BOX_HEIGHT)

        val painted = paintedBounds(requireNotNull(findImage(container)))
        assertEquals("width", BOX_HEIGHT * MEDIA_WIDTH / MEDIA_HEIGHT.toFloat(), painted.width(), 1f)
        assertEquals("left", 0f, painted.left, 1f)
    }

    /**
     * A box the item stated on both axes is laid out at the position the item asked for, so the
     * slack left inside it stays even — `FIT_CENTER` splitting it is the answer, not a fallback.
     */
    @Test
    public fun testAStatedBoxCentresTheWholeImage() {
        val container = container(
            mediaItemSize = """{"width": "100%", "height": "100%"}""",
            itemPosition = START,
            media = WHOLE
        )
        container.measure(
            spec(PAGE, View.MeasureSpec.EXACTLY),
            spec(BOX_HEIGHT, View.MeasureSpec.EXACTLY)
        )
        container.layout(0, 0, PAGE, BOX_HEIGHT)

        val image = requireNotNull(findImage(container))
        assertEquals("box", PAGE, image.width)
        assertEquals("scale type", ScaleType.FIT_CENTER, image.scaleType)
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

    /** Where in its view the image is painted, for a view painting through its image matrix. */
    private fun paintedBounds(image: CropImageView): RectF {
        assertEquals("scale type", ScaleType.MATRIX, image.scaleType)
        return RectF(0f, 0f, MEDIA_WIDTH.toFloat(), MEDIA_HEIGHT.toFloat())
            .apply { image.imageMatrix.mapRect(this) }
    }

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

    /**
     * A `100% x 100%` container holding the media as its only item, in a stack.
     *
     * The container is an item rather than the root so that it has a declared length, as the one
     * wrapping the media in a real scene does.
     */
    private fun container(
        mediaItemSize: String = AUTO,
        itemPosition: String = CENTER_BOTTOM,
        media: String = MEDIA
    ): ViewGroup = build(
        """
        {
          "type": "linear_layout",
          "direction": "vertical",
          "items": [
            {
              "size": {"width": "100%", "height": "100%"},
              "view": {
                "type": "container",
                "items": [
                  {
                    "position": $itemPosition,
                    "size": $mediaItemSize,
                    "view": $media
                  }
                ]
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

        private const val CENTER_BOTTOM = """{"horizontal": "center", "vertical": "bottom"}"""
        private const val START = """{"horizontal": "start", "vertical": "bottom"}"""

        private val MEDIA = media("fit_crop")

        /** Media under a fit that never crops, so a box bigger than its shape leaves slack. */
        private val WHOLE = media("center_inside")

        private fun media(fit: String) = """
            {
              "type": "media",
              "media_type": "image",
              "media_fit": "$fit",
              "url": "https://example.com/photo.jpg"
            }
        """.trimIndent()
    }
}
