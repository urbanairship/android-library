/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View.MeasureSpec
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.model.VerticalScrollLayoutModel
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
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RobolectricTestRunner

/**
 * Ryan's report: "android scroll layout size is wrong on backgrounds." Checks concretely whether
 * `VerticalScrollLayoutView.dispatchDraw` sees the scroll view's own resolved *viewport* size (a
 * NestedScrollView's `width`/`height`, which is what it was given by its own parent) or picks up
 * the full scrollable *content* size instead (the inner content view can be, and here deliberately
 * is, much taller than the viewport -- that's the whole point of it being scrollable).
 *
 * This passes: `dispatchDraw` correctly sees the viewport (200), not the content (900). Confirmed
 * by direct measurement, not inference -- `@GraphicsMode(Mode.NATIVE)` matters here, since without
 * it a freshly-set opaque `Paint.color` reads back `getAlpha() == 0` under Robolectric's legacy
 * shadow, which looks identical to "nothing painted" and would misreport this exact size question
 * as a rendering bug. So "size is wrong" isn't this -- see whatever bug report follow-up narrows
 * down what "wrong" actually looked like (crop/scale of the image itself is the next thing to check).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
public class VerticalScrollLayoutBackgroundsRenderTest {

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

    @Test
    public fun dispatchDrawSeesTheViewportSizeNotTheFullScrollableContentSize() {
        // Content (900) is deliberately much taller than the viewport it'll be measured into
        // (200) -- exactly the "Scroll filler so there is somewhere to scroll to" shape from
        // _backgrounds-parallax-pager-scroll.yml.
        val json = JsonValue.parseString(
            """
            {
              "type": "scroll_layout",
              "direction": "vertical",
              "backgrounds": [
                { "type": "color", "color": { "default": { "hex": "#FF0000", "alpha": 1 } } }
              ],
              "view": {
                "type": "linear_layout",
                "direction": "vertical",
                "items": [
                  {
                    "size": { "width": "100%", "height": 900 },
                    "view": { "type": "empty_view" }
                  }
                ]
              }
            }
            """.trimIndent()
        ).requireMap()

        val info = ViewInfo.viewInfoFromJson(json)
        val model = LayoutViewModel().getOrCreateModel(info, mockEnv) as VerticalScrollLayoutModel
        val view = model.createView(context, viewEnv, null) as VerticalScrollLayoutView

        // The viewport this scroll_layout is actually given -- e.g. by a modal/container above it
        // -- is 200, far short of its own content's 900.
        view.measure(
            MeasureSpec.makeMeasureSpec(300, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(200, MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 300, 200)

        assertEquals("view.width should be the viewport, not the content", 300, view.width)
        assertEquals("view.height should be the viewport (200), not the content (900+)", 200, view.height)
        // getChildAt(0) is the scrollable content container; it should be the tall one.
        assertEquals(
            "the content view itself should still be its own full (taller) height",
            900,
            view.getChildAt(0).height
        )

        // dispatchDraw calls backgroundsPainter.draw(canvas, width, height) using exactly the
        // view.width/view.height asserted above, so confirming those already confirms what the
        // painter is given. This also draws it, to check the background actually fills the frame.
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))

        val pixel = bitmap.getPixel(150, 100)
        assertEquals(
            "the background color should fill the visible viewport",
            String.format("#%08X", Color.RED),
            String.format("#%08X", pixel)
        )
    }
}
