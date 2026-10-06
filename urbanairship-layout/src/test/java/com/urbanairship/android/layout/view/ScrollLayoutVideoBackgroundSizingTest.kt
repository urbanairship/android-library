/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View.MeasureSpec
import android.webkit.WebChromeClient
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.model.VerticalScrollLayoutModel
import com.urbanairship.android.layout.ui.LayoutViewModel
import com.urbanairship.android.layout.util.Factory
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A `scroll_layout` that sizes to its content must keep doing so when it carries a video layer.
 *
 * The layer is hosted in the content frame, which the scroll view clips to, so the frame is held
 * to the viewport or short content would cut the layer off. Taking that viewport from the view's
 * own measured height instead of the one its parent imposes closes a loop: the frame is held to a
 * height that already includes this view's padding, the content grows to match, and the view --
 * which sizes to that content, plus padding again -- comes back larger every pass. With a border
 * (whose stroke is applied as padding) it ratchets up by the stroke width until it fills whatever
 * is offered, and can never shrink back.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class ScrollLayoutVideoBackgroundSizingTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val videoState: SharedState<State.Video> = SharedState(State.Video(identifier = null))

    private val mockEnv: ModelEnvironment = mockk(relaxed = true) {
        every { layoutEvents } returns emptyFlow()
        every { modelScope } returns testScope
        every { layoutState } returns mockk(relaxed = true) {
            every { video } returns videoState
            every { pager } returns null
            every { videoControl } returns null
        }
    }
    private val viewEnv: ViewEnvironment = mockk(relaxed = true) {
        every { isIgnoringSafeAreas } returns false
        every { webChromeClientFactory() } returns Factory { WebChromeClient() }
    }

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { mockEnv.withState(any()) } returns mockEnv
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * A guard, not a reproduction: the ratchet above is a real-framework symptom that Robolectric
     * does not exhibit -- this passes against the old code too. It is kept because the invariant
     * is worth stating and cheap to hold, and it would catch the day the viewport is taken from
     * this view's own size again. The verified regression is the test below.
     */
    @Test
    public fun anAutoHeightScrollLayoutWithABorderedVideoLayerDoesNotGrowEachPass() {
        val view = buildView()
        // A border's stroke reaches the view as padding, which is what the loop compounds.
        view.setPadding(4, 4, 4, 4)

        val heights = (1..4).map {
            // `View.measure` caches against the last spec, so a real relayout is what exposes a
            // height that feeds back into the next pass.
            view.forceLayout()
            view.measure(
                MeasureSpec.makeMeasureSpec(CEILING, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(CEILING, MeasureSpec.AT_MOST)
            )
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            view.measuredHeight
        }

        assertEquals(
            "an auto-height scroll layout should settle at one height, not climb every pass -- got $heights",
            heights.first(), heights.last()
        )
        assertTrue(
            "and it should still be sized by its content, not by the ceiling it was offered " +
                "-- got ${heights.last()} against a $CEILING ceiling",
            heights.last() < CEILING
        )
    }

    /** The reason the frame is held to the viewport at all, which the fix must not undo. */
    @Test
    public fun anImposedViewportStillHoldsTheFrameSoAShortContentLayerIsNotCutOff() {
        val view = buildView()

        view.measure(
            MeasureSpec.makeMeasureSpec(CEILING, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(CEILING, MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, CEILING, CEILING)

        val frame = view.getChildAt(0)
        assertEquals(
            "content shorter than an imposed viewport still gets a full-viewport frame, so the " +
                "layer inside it isn't clipped short",
            CEILING, frame.height
        )
    }

    private fun buildView(): VerticalScrollLayoutView {
        val json = """
            {
              "type": "scroll_layout",
              "direction": "vertical",
              "backgrounds": [
                {
                  "type": "media", "media_type": "video",
                  "url": "https://test-layout.internal/video.mp4",
                  "media_fit": "fit_crop",
                  "video": { "autoplay": true, "muted": true, "loop": true }
                }
              ],
              "view": {
                "type": "linear_layout",
                "direction": "vertical",
                "items": [
                  { "size": { "width": 40, "height": 30 },
                    "view": { "type": "linear_layout", "direction": "vertical", "items": [] } }
                ]
              }
            }
        """.trimIndent()

        val info = ViewInfo.viewInfoFromJson(JsonValue.parseString(json).requireMap())
        val model = LayoutViewModel().getOrCreateModel(info, mockEnv) as VerticalScrollLayoutModel
        return model.createView(context, viewEnv, null) as VerticalScrollLayoutView
    }

    private companion object {
        private const val CEILING = 1000
    }
}
