/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.model.ContainerLayoutModel
import com.urbanairship.android.layout.ui.LayoutViewModel
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A container overlaying a `width: auto, height: 100%` background item on a `width: 100%,
 * height: auto` content item — the shape of `_background-image-100pct.yml`, where the background
 * item's view is a `fit_crop` image.
 *
 * The container resolves that percentage against itself on an `EXACTLY` or `AT_MOST` height, but
 * not on `UNSPECIFIED`: there [resolveHeights] only accepts a viewport borrowed from a scroll
 * ancestor, so a container measured unbounded with no scroll layout above it leaves the item at
 * wrap-content — which for an `ImageView` is the drawable's intrinsic *pixel* size. That is what
 * renders on device: a 642x350 JPEG drawn 642x350 in a 1493pt-tall modal.
 *
 * The unbounded pass is reached whenever the length basis runs out above the container, which a
 * `height: auto` modal is enough to do: its root `linear_layout` holds only a `height: 100%` item,
 * so `WeightlessLinearLayout` has nothing to solve a height from and measures it
 * `UNSPECIFIED` ([WeightlessLinearLayout] "measure at content" branch).
 *
 * A non-percent sibling supplies a length in all three cases, so all three should agree.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class ContainerPercentHeightUnboundedTest {

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
    }

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Regression guard: a real ceiling resolves the percentage against the sibling's length. */
    @Test
    public fun testPercentHeightResolvesAgainstTheSiblingWhenBounded() {
        val view = overlay()
        view.measure(exactly(834), spec(1493, View.MeasureSpec.AT_MOST))

        assertEquals("container height", CONTENT_HEIGHT, view.measuredHeight)
        assertEquals("background item height", CONTENT_HEIGHT, view.getChildAt(0).measuredHeight)
    }

    /**
     * The bug. Measured unbounded, the background item keeps its wrap-content height — the
     * drawable's intrinsic size in the real scene — instead of the length its sibling supplies.
     */
    @Test
    public fun testPercentHeightResolvesAgainstTheSiblingWhenUnbounded() {
        val view = overlay()
        view.measure(exactly(834), spec(1493, View.MeasureSpec.UNSPECIFIED))

        assertEquals("container height", CONTENT_HEIGHT, view.measuredHeight)
        assertEquals("background item height", CONTENT_HEIGHT, view.getChildAt(0).measuredHeight)
    }

    /** The nesting the scene actually has, which is what puts the container on the unbounded path. */
    @Test
    public fun testPercentHeightSurvivesAnAutoHeightStack() {
        val stack = stack()
        // A `height: auto` modal measures its content against a ceiling before constraining it.
        stack.measure(exactly(834), spec(2176, View.MeasureSpec.AT_MOST))

        val container = requireNotNull(findContainer(stack)) { "no container" }
        assertEquals("background item height", CONTENT_HEIGHT, container.getChildAt(0).measuredHeight)
    }

    /**
     * The scene's *outer* container: a `100%` item alongside a small fixed one, which is what the
     * dismiss button is. An overlay's size is its largest item, so the full-height item decides the
     * base — taking it from the 48pt sibling instead collapsed a whole page to 48pt.
     *
     * Ran both ways because the two disagreed: `AT_MOST` resolved against the button, `UNSPECIFIED`
     * happened to be right only by declining to resolve at all.
     */
    @Test
    public fun testFullPercentItemIsNotCollapsedByASmallFixedSibling() {
        for (mode in listOf(View.MeasureSpec.AT_MOST, View.MeasureSpec.UNSPECIFIED)) {
            val view = overlayWithDismissButton()
            view.measure(exactly(834), spec(2176, mode))

            assertEquals("percent item height", CONTENT_HEIGHT, view.getChildAt(0).measuredHeight)
            assertEquals("button height", BUTTON, view.getChildAt(1).measuredHeight)
        }
    }

    private fun exactly(size: Int) = spec(size, View.MeasureSpec.EXACTLY)

    private fun spec(size: Int, mode: Int) = View.MeasureSpec.makeMeasureSpec(size, mode)

    private fun findContainer(view: View): ContainerLayoutView? {
        if (view is ContainerLayoutView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findContainer(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private fun overlayWithDismissButton(): ContainerLayoutView = build(
        """
        {
          "position": {"horizontal": "center", "vertical": "center"},
          "size": {"width": "100%", "height": "100%"},
          "view": {
            "type": "container",
            "items": [
              {
                "position": {"horizontal": "center", "vertical": "center"},
                "size": {"width": "100%", "height": $CONTENT_HEIGHT},
                "view": {"type": "empty_view"}
              }
            ]
          }
        },
        {
          "position": {"horizontal": "end", "vertical": "top"},
          "size": {"width": $BUTTON, "height": $BUTTON},
          "view": {"type": "empty_view"}
        }
        """.trimIndent()
    )

    private fun overlay(): ContainerLayoutView = build(ITEMS)

    private fun build(itemsJson: String): ContainerLayoutView {
        val json = JsonValue.parseString("""{"type":"container","items":[$itemsJson]}""").requireMap()
        val info = ViewInfo.viewInfoFromJson(json)
        val model = LayoutViewModel().getOrCreateModel(info, mockEnv) as ContainerLayoutModel
        return model.createView(context, viewEnv, null) as ContainerLayoutView
    }

    /** A vertical `linear_layout` whose only item is `100% x 100%`, as the scene's root is. */
    private fun stack(): View {
        val json = JsonValue.parseString(
            """
            {
              "type": "linear_layout",
              "direction": "vertical",
              "items": [
                {
                  "size": {"width": "100%", "height": "100%"},
                  "view": {"type": "container", "items": [$ITEMS]}
                }
              ]
            }
            """.trimIndent()
        ).requireMap()
        val info = ViewInfo.viewInfoFromJson(json)
        return LayoutViewModel().getOrCreateModel(info, mockEnv).createView(context, viewEnv, null)
    }

    private companion object {
        /** Stands in for the loaded bitmap: the scene's JPEG is 642x350 intrinsic. */
        private const val IMAGE_WIDTH = 214
        private const val IMAGE_HEIGHT = 117

        /** Stands in for the NPS form the background sits behind. */
        private const val CONTENT_HEIGHT = 393

        /** Stands in for the scene's dismiss button, the small fixed sibling. */
        private const val BUTTON = 48

        private val ITEMS = """
            {
              "position": {"horizontal": "center", "vertical": "center"},
              "size": {"width": "auto", "height": "100%"},
              "margin": {"start": 0, "end": 0},
              "view": {
                "type": "container",
                "items": [
                  {
                    "position": {"horizontal": "center", "vertical": "center"},
                    "size": {"width": $IMAGE_WIDTH, "height": $IMAGE_HEIGHT},
                    "view": {"type": "empty_view"}
                  }
                ]
              }
            },
            {
              "position": {"horizontal": "center", "vertical": "center"},
              "size": {"width": "100%", "height": "auto"},
              "view": {
                "type": "container",
                "items": [
                  {
                    "position": {"horizontal": "center", "vertical": "center"},
                    "size": {"width": "100%", "height": $CONTENT_HEIGHT},
                    "view": {"type": "empty_view"}
                  }
                ]
              }
            }
        """.trimIndent()
    }
}
