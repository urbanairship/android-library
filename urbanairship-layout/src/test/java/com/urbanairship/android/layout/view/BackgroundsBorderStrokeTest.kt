/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.view.View
import android.view.View.MeasureSpec
import android.webkit.WebChromeClient
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.ViewInfo
import com.urbanairship.android.layout.property.Border
import com.urbanairship.android.layout.ui.LayoutViewModel
import com.urbanairship.android.layout.util.Factory
import com.urbanairship.android.layout.util.LayoutUtils
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
import kotlin.math.sqrt
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import com.urbanairship.android.layout.property.Color as ThomasColor

/**
 * Where a `backgrounds` layer stops when its host draws a border stroke.
 *
 * A stroke reaches a host as padding, so the box a layer fills is the host's own box less that
 * padding -- the stroke stays visible, and a layer never paints over it. iOS pads before it
 * applies the border, and a layer on web is absolutely positioned, which resolves `100%` against
 * the padding box; this is the same box on Android.
 *
 * Both of the host's rendering paths answer to it, which is the part that shipped wrong: a
 * painted layer took the outer box and hid the stroke completely, while a layer hosted as a real
 * child was inset by the padding like any other child. The two then drew the same layer a stroke
 * width apart, leaving whatever sat below the video showing through in a band around it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
public class BackgroundsBorderStrokeTest {

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
     * Rendered rather than computed: the defect was only ever visible in the pixels, and every
     * quantity the painter works from was correct while the stroke was being painted over.
     */
    @Test
    public fun aPaintedLayerLeavesTheStrokeVisible() {
        val view = bordered(linearLayout(COLOR_LAYER))
        val stroke = view.paddingLeft
        val bitmap = render(view)

        assertTrue("the fixture has to actually produce a stroke to leave alone", stroke > 0)
        for ((x, y) in edgeMidpoints(stroke)) {
            assertEquals(
                "the stroke should still be there at ($x, $y)",
                STROKE_COLOR, bitmap.getPixel(x, y)
            )
        }
        assertEquals(
            "and the layer should start where the stroke leaves off",
            LAYER_COLOR, bitmap.getPixel(stroke, BOX / 2)
        )
    }

    /**
     * The box inside the stroke has rounded corners too, with each radius less the stroke: a
     * rectangular layer's corners otherwise cover the curve of the stroke.
     */
    @Test
    public fun aPaintedLayerLeavesARoundedStrokeVisible() {
        val view = bordered(container(COLOR_LAYER), radius = CORNER_RADIUS_DP)
        val stroke = view.paddingLeft
        val radius = CORNER_RADIUS_DP * context.resources.displayMetrics.density
        val bitmap = render(view)

        // Halfway through the stroke, on the diagonal of the top-left corner's curve.
        val depth = radius - stroke / 2f
        val onCurve = (radius - depth / sqrt(2f)).toInt()
        assertEquals(
            "the stroke should still be there at the corner",
            STROKE_COLOR, bitmap.getPixel(onCurve, onCurve)
        )
        assertEquals(
            "and the layer should still fill the rest of the box",
            LAYER_COLOR, bitmap.getPixel(BOX / 2, BOX / 2)
        )
    }

    /** The other path to the same box: a layer that plays, so it has to be a real child view. */
    @Test
    public fun aLayerHostedAsAChildFillsTheSameBox() {
        val view = bordered(linearLayout(VIDEO_LAYER))
        val stroke = view.paddingLeft

        assertTrue(stroke > 0)
        assertEquals(
            "a layer view covers the box inside the stroke, exactly as a painted one does",
            Rect(stroke, stroke, BOX - stroke, BOX - stroke),
            view.getChildAt(0).frame
        )
    }

    /**
     * A scrolling host holds its layer views in a content frame it lays out inside its own
     * padding, so theirs is the one box that is already offset -- placing them at the padding
     * again would inset them twice.
     */
    @Test
    public fun aScrollingHostsLayerFillsTheSameBox() {
        val view = bordered(
            build<VerticalScrollLayoutView>(
                """
                {
                  "type": "scroll_layout", "direction": "vertical",
                  $VIDEO_LAYER
                  "view": { "type": "linear_layout", "direction": "vertical", "items": [] }
                }
                """
            )
        )
        val stroke = view.paddingLeft
        val contentFrame = view.getChildAt(0) as android.view.ViewGroup
        val layer = contentFrame.getChildAt(0)

        assertTrue(stroke > 0)
        assertEquals(
            "the layer view should cover the viewport inside the stroke",
            Rect(stroke, stroke, BOX - stroke, BOX - stroke),
            Rect(
                contentFrame.left + layer.left, contentFrame.top + layer.top,
                contentFrame.left + layer.right, contentFrame.top + layer.bottom
            )
        )
    }

    /** The four edge midpoints, each [stroke] deep into the box from its own side. */
    private fun edgeMidpoints(stroke: Int): List<Pair<Int, Int>> = listOf(
        stroke / 2 to BOX / 2,
        BOX - 1 - stroke / 2 to BOX / 2,
        BOX / 2 to stroke / 2,
        BOX / 2 to BOX - 1 - stroke / 2
    )

    private fun render(view: View): Bitmap =
        Bitmap.createBitmap(BOX, BOX, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }

    private val View.frame: Rect get() = Rect(left, top, right, bottom)

    private fun container(layer: String): ContainerLayoutView = build(
        """
        {
          "type": "container",
          $layer
          "items": []
        }
        """
    )

    private fun linearLayout(layer: String): LinearLayoutView = build(
        """
        {
          "type": "linear_layout", "direction": "vertical",
          $layer
          "items": []
        }
        """
    )

    /**
     * Applies the border the same way a host does when its model hands one over, which is what
     * turns a `stroke_width` into padding in the first place.
     *
     * Laid out afterwards, since the padding is what the layout has to account for.
     */
    private fun <T : View> bordered(view: T, radius: Int = 0): T {
        LayoutUtils.applyBorderAndBackground(
            view,
            null,
            Border(radius = radius, strokeWidth = STROKE_WIDTH_DP, strokeColor = thomasColor(STROKE_COLOR)),
            null
        )
        layOut(view)
        return view
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : View> build(json: String): T {
        val info = ViewInfo.viewInfoFromJson(JsonValue.parseString(json.trimIndent()).requireMap())
        val model = LayoutViewModel().getOrCreateModel(info, mockEnv)
        return (model.createView(context, viewEnv, null) as T).also { layOut(it) }
    }

    private fun layOut(view: View) {
        view.measure(
            MeasureSpec.makeMeasureSpec(BOX, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(BOX, MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, BOX, BOX)
    }

    private fun thomasColor(argb: Int): ThomasColor = ThomasColor.fromJson(
        JsonValue.parseString(
            """{ "default": { "hex": "#%06X", "alpha": 1 } }""".format(argb and 0xFFFFFF)
        )
    )

    private companion object {
        private const val BOX = 300
        private const val STROKE_WIDTH_DP = 8
        private const val CORNER_RADIUS_DP = 60
        private const val STROKE_COLOR = Color.YELLOW
        private const val LAYER_COLOR = Color.MAGENTA

        private val COLOR_LAYER = """
            "backgrounds": [
              { "type": "color", "color": { "default": { "hex": "#FF00FF", "alpha": 1 } } }
            ],
        """.trimIndent()

        private val VIDEO_LAYER = """
            "backgrounds": [
              {
                "type": "media", "media_type": "video",
                "url": "https://test-layout.internal/video.mp4",
                "media_fit": "fit_crop",
                "video": { "autoplay": true, "muted": true, "loop": true }
              }
            ],
        """.trimIndent()
    }
}
