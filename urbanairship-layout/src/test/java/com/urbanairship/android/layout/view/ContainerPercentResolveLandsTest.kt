/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
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
 * A resolved percentage has to survive being handed to ConstraintLayout.
 *
 * `onMeasure` writes solved sizes straight onto each frame's LayoutParams. ConstraintLayout copies
 * params into its solver in `setChildrenConstraints()`, which `updateHierarchy()` only calls when
 * one of its *children* reports `isLayoutRequested`. Nothing here goes through `setLayoutParams`,
 * so once a layout has completed and the children are clean, a later pass would solve correctly and
 * have the answer thrown away, leaving the solver on the previous pass's dimensions.
 *
 * Both cases below need a completed [View.layout] between the passes — that is what settles the
 * children. Measuring twice in a row without one leaves them dirty and hides the bug.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class ContainerPercentResolveLandsTest {

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

    /**
     * A `height: auto` modal measures its content unbounded to find its natural height, then
     * re-measures at the height its `min_height` settled on. The second pass is the one that fills
     * a `height: 100%` background image to the modal — it stayed at the unbounded pass's answer.
     */
    @Test
    public fun testBoundedPassCorrectsAnUnboundedOne() {
        val view = overlay()

        measureAndLayout(view, 0, View.MeasureSpec.UNSPECIFIED)
        assertEquals("unbounded pass", CONTENT, frameHeight(view))

        measureAndLayout(view, TALL, View.MeasureSpec.EXACTLY)
        assertEquals("bounded pass", TALL, frameHeight(view))
    }

    /**
     * The IME shrinking a modal: the page has to shrink with it rather than keep its old height and
     * overflow. On device this laid a 1493pt page out at y=-243 inside a 1005pt container.
     */
    @Test
    public fun testPercentItemTracksTheContainerShrinking() {
        val view = overlay()

        measureAndLayout(view, TALL, View.MeasureSpec.EXACTLY)
        assertEquals("initial", TALL, frameHeight(view))

        measureAndLayout(view, SHORT, View.MeasureSpec.EXACTLY)
        assertEquals("after shrinking", SHORT, frameHeight(view))
        assertEquals("laid out flush to the top", 0, view.getChildAt(0).top)
    }

    private fun measureAndLayout(view: View, height: Int, mode: Int) {
        view.measure(
            View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, mode)
        )
        view.layout(0, 0, WIDTH, view.measuredHeight)
    }

    private fun frameHeight(view: ContainerLayoutView): Int = view.getChildAt(0).measuredHeight

    private fun overlay(): ContainerLayoutView {
        val json = JsonValue.parseString("""{"type":"container","items":[$ITEMS]}""").requireMap()
        val info = ViewInfo.viewInfoFromJson(json)
        val model = LayoutViewModel().getOrCreateModel(info, mockEnv) as ContainerLayoutModel
        return model.createView(context, viewEnv, null) as ContainerLayoutView
    }

    private companion object {
        private const val WIDTH = 834
        private const val TALL = 1493
        private const val SHORT = 1005

        /** Stands in for the content the background sits behind. */
        private const val CONTENT = 393

        private val ITEMS = """
            {
              "position": {"horizontal": "center", "vertical": "center"},
              "size": {"width": "100%", "height": "100%"},
              "view": {
                "type": "container",
                "items": [
                  {
                    "position": {"horizontal": "center", "vertical": "center"},
                    "size": {"width": "100%", "height": $CONTENT},
                    "view": {"type": "empty_view"}
                  }
                ]
              }
            },
            {
              "position": {"horizontal": "end", "vertical": "top"},
              "size": {"width": 48, "height": 48},
              "view": {"type": "empty_view"}
            }
        """.trimIndent()
    }
}
