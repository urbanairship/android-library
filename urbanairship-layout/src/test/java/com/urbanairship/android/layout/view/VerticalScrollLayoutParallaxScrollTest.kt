/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View.MeasureSpec
import android.widget.FrameLayout
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Ryan's real-device report on `d1686602d`'s fix: "scrolling still reveals undersized." Unlike
 * [VerticalScrollLayoutBackgroundsRenderTest] and `BackgroundsPainterParallaxSizingTest`, this
 * drives the real host: a real `NestedScrollView.scrollTo`, not a hand-picked `offsetPx` fed
 * straight to `BackgroundsPainter`.
 *
 * `View.draw()` pre-translates its canvas by `(-scrollX, -scrollY)` before calling `dispatchDraw`
 * -- the mechanism that makes a `ScrollView`'s children appear to scroll at all -- and
 * `VerticalScrollLayoutView.dispatchDraw` painted the background straight into that same,
 * already-shifted canvas. So every layer moved with the scroll at 1x, with any of its own
 * `parallax` translate landing on top of that, uncompensated: a `factor: 0` layer (pinned in
 * place, [Parallax] resolves this to zero translate of its own) still moved 1:1 with the scroll,
 * and a `factor: 1` layer (meant to move in lockstep with the content, i.e. also 1:1) moved at 2x.
 * Since `parallaxOversize`'s oversize is only ever sized to cover [BackgroundsPainter]'s own
 * clamped, bounded translate, it was never going to also cover an unrelated, unbounded scroll
 * translate riding along with it -- a long scroll runs the layer out of margin no matter how
 * large the margin is.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
public class VerticalScrollLayoutParallaxScrollTest {

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

    /** `factor: 0` -- [Parallax]'s own doc says this "pins the layer in place." */
    @Test
    public fun aPinnedLayerStaysFullyCoveredAndUnmovedAfterARealScroll() {
        val view = buildView(factor = 0f)

        assertEquals(Color.RED, pixelAfterScrollingTo(view, 0))
        // Far past one viewport-height of scroll (200) -- exactly what exposed the gap.
        assertEquals(Color.RED, pixelAfterScrollingTo(view, 700))
    }

    /** `factor: 1` -- documented as moving "in lockstep with the content," i.e. staying covered
     * throughout, same as the pinned case above. */
    @Test
    public fun aLockstepLayerStaysFullyCoveredAfterARealScroll() {
        val view = buildView(factor = 1f)

        assertEquals(Color.RED, pixelAfterScrollingTo(view, 0))
        assertEquals(Color.RED, pixelAfterScrollingTo(view, 700))
    }

    /**
     * Draws through a real parent, not `view.draw(canvas)` directly -- only the parent's
     * `drawChild` applies `View`'s own scroll pretranslate before calling `dispatchDraw`; drawing
     * a view standalone skips that wrapper entirely and would pass even with the bug this test
     * exists to catch.
     */
    private fun pixelAfterScrollingTo(view: VerticalScrollLayoutView, scrollY: Int): Int {
        view.scrollTo(0, scrollY)
        val bitmap = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        // Called more than once per test (scroll=0, then scroll=700) -- drop any parent from a
        // previous call so re-adding it here doesn't throw.
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        val parent = FrameLayout(context)
        parent.addView(view, FrameLayout.LayoutParams(300, 200))
        parent.measure(
            MeasureSpec.makeMeasureSpec(300, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(200, MeasureSpec.EXACTLY)
        )
        parent.layout(0, 0, 300, 200)
        parent.draw(Canvas(bitmap))
        // The viewport's own center -- covered at rest by construction; any regression here is
        // the box losing coverage after a real scroll, not a pre-existing edge-of-box question.
        return bitmap.getPixel(150, 100)
    }

    private fun buildView(factor: Float): VerticalScrollLayoutView {
        val json = JsonValue.parseString(
            """
            {
              "type": "scroll_layout",
              "direction": "vertical",
              "backgrounds": [
                {
                  "type": "color",
                  "color": { "default": { "hex": "#FF0000", "alpha": 1 } },
                  "parallax": { "factor": $factor }
                }
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

        view.measure(
            MeasureSpec.makeMeasureSpec(300, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(200, MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 300, 200)
        return view
    }
}
