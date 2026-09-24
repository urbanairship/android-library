/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
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
import com.urbanairship.android.layout.model.LinearLayoutModel
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
 * A video `backgrounds` layer is a real child of its host, but never a member of the host's stack.
 *
 * `WeightlessLinearLayout` positions its children end to end, so a layer left in that walk would
 * take a slot: it fills the host's box, so the first real item would start a whole box-length
 * along and everything after it would be pushed out. `stackChildAt` is what keeps it out, and
 * `LinearLayoutView` measures and places it itself afterwards.
 *
 * Every assertion here is differential -- the same scene with and without the layer -- so it
 * pins the invariant ("the stack behaves as though the layer weren't there") rather than any
 * particular figure, and holds regardless of density or how the DSL sizes get resolved.
 *
 * The queries matter more than the positions. A layer is added at zero size and never given
 * layout params, so even left in the walk it would displace nothing; but it is a real child with
 * `lp.width == 0` and no percent, which is exactly the shape `statesLength` reads as "this child
 * states a definite length". Left in, a stack of `auto` content would claim a length it hasn't
 * got, and every ancestor resolving a percentage against it would resolve against the wrong
 * thing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class LinearLayoutVideoBackgroundStackTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private val videoState: SharedState<State.Video> = SharedState(State.Video(identifier = null))

    private val mockEnv: ModelEnvironment = mockk(relaxed = true) {
        every { layoutEvents } returns emptyFlow()
        every { modelScope } returns testScope
        every { layoutState } returns mockk(relaxed = true) {
            // A real one: the layer's MediaModel registers its playback state here on construction.
            every { video } returns videoState
            every { pager } returns null
            every { videoControl } returns null
        }
    }
    private val viewEnv: ViewEnvironment = mockk(relaxed = true) {
        every { isIgnoringSafeAreas } returns false
        // A relaxed mock can't synthesize a real WebChromeClient for the layer's WebView.
        every { webChromeClientFactory() } returns Factory { WebChromeClient() }
    }

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // `ModelFactory` derives a child environment per node; on a mock that hands back a fresh
        // one, losing the video state stubbed above. Point it back at this environment.
        every { mockEnv.withState(any()) } returns mockEnv
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun aVerticalStackLaysOutAsThoughTheLayerWereNotThere() {
        assertStackUnaffectedByTheLayer(direction = "vertical")
    }

    /**
     * `layoutHorizontal` walks the children in its own loop, separate from `layoutVertical`'s --
     * a host that excludes the layer from one and not the other pushes every item a full box
     * width along on that axis alone.
     */
    @Test
    public fun aHorizontalStackLaysOutAsThoughTheLayerWereNotThere() {
        assertStackUnaffectedByTheLayer(direction = "horizontal")
    }

    /**
     * The horizontal walk runs backwards in RTL (`start = count - 1`, `dir = -1`), which is where
     * an index the layer still occupies would go unnoticed the longest.
     */
    @Test
    public fun aRightToLeftHorizontalStackLaysOutAsThoughTheLayerWereNotThere() {
        assertStackUnaffectedByTheLayer(direction = "horizontal", rtl = true)
    }

    /**
     * `statesLength` reads `lp.width`/`lp.height` straight off each child, and a layer's are `0`
     * with no percent -- the shape it treats as a stated length. The stack's answer has to be the
     * one it would give with no layer at all.
     */
    @Test
    public fun theLayerDoesNotMakeTheStackClaimALength() {
        val without = buildAutoSizedView(withLayer = false)
        val with = buildAutoSizedView(withLayer = true)

        assertEquals(
            "a decorative layer must not answer for the stack's own length",
            without.statesLength(horizontal = false), with.statesLength(horizontal = false)
        )
        assertEquals(
            without.statesLength(horizontal = true), with.statesLength(horizontal = true)
        )
    }

    /** Same again for the question an ancestor asks when resolving a percentage. */
    @Test
    public fun theLayerDoesNotMakeTheStackEstablishALength() {
        val without = buildAutoSizedView(withLayer = false)
        val with = buildAutoSizedView(withLayer = true)

        assertEquals(
            without.establishesLength(horizontal = false), with.establishesLength(horizontal = false)
        )
        assertEquals(
            without.establishesLength(horizontal = true), with.establishesLength(horizontal = true)
        )
    }

    /** Excluded from the stack, but still placed -- otherwise it would simply never be drawn. */
    @Test
    public fun theLayerItselfIsStillSizedToTheHostsBox() {
        val view = buildView(direction = "vertical", withLayer = true)

        val layer = view.getChildAt(0)
        assertEquals(
            "the layer is added before the items, so it draws below them",
            0, view.indexOfChild(layer)
        )
        assertEquals(Rect(0, 0, view.width, view.height), layer.frame)
        assertTrue("and the host has real bounds to have filled", view.width > 0 && view.height > 0)
        assertEquals(
            "its layout params stay at zero, so it never decides the host's own size",
            0, layer.layoutParams.width
        )
    }

    private fun assertStackUnaffectedByTheLayer(direction: String, rtl: Boolean = false) {
        val without = buildView(direction, withLayer = false, rtl = rtl)
        val with = buildView(direction, withLayer = true, rtl = rtl)

        assertEquals(
            "the layer must not take a slot in the stack",
            ITEM_COUNT, without.childCount
        )
        assertEquals(
            "the layer is a child, just not a stack item",
            ITEM_COUNT + 1, with.childCount
        )
        assertEquals(
            "a background layer never contributes to its host's own measured size",
            without.measuredWidth to without.measuredHeight,
            with.measuredWidth to with.measuredHeight
        )
        assertEquals(
            "every item should land exactly where it does without the layer",
            without.itemFrames(), with.itemFrames()
        )
    }

    /** The items, which are the last [ITEM_COUNT] children in both shapes. */
    private fun LinearLayoutView.itemFrames(): List<Rect> =
        (childCount - ITEM_COUNT until childCount).map { getChildAt(it).frame }

    private val View.frame: Rect get() = Rect(left, top, right, bottom)

    /**
     * A stack whose content states no length of its own, so the only thing that could answer for
     * it is the layer -- which is the whole point.
     */
    private fun buildAutoSizedView(withLayer: Boolean): LinearLayoutView = buildFromJson(
        """
        {
          "type": "linear_layout",
          "direction": "vertical",
          ${if (withLayer) VIDEO_LAYER else ""}
          "items": [
            { "size": { "width": "auto", "height": "auto" },
              "view": { "type": "linear_layout", "direction": "vertical", "items": [] } }
          ]
        }
        """.trimIndent()
    )

    private fun buildView(direction: String, withLayer: Boolean, rtl: Boolean = false): LinearLayoutView {
        val backgrounds = if (withLayer) VIDEO_LAYER else ""
        val json = JsonValue.parseString(
            """
            {
              "type": "linear_layout",
              "direction": "$direction",
              $backgrounds
              "items": [
                { "size": { "width": 40, "height": 30 },
                  "view": { "type": "linear_layout", "direction": "vertical", "items": [] } },
                { "size": { "width": 20, "height": 50 },
                  "view": { "type": "linear_layout", "direction": "vertical", "items": [] } }
              ]
            }
            """.trimIndent()
        ).requireMap()

        val view = buildFromJson(json.toString())
        if (rtl) {
            view.layoutDirection = View.LAYOUT_DIRECTION_RTL
            view.measure(
                MeasureSpec.makeMeasureSpec(BOX, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(BOX, MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, BOX, BOX)
        }
        return view
    }

    private fun buildFromJson(json: String): LinearLayoutView {
        val info = ViewInfo.viewInfoFromJson(JsonValue.parseString(json).requireMap())
        val model = LayoutViewModel().getOrCreateModel(info, mockEnv) as LinearLayoutModel
        val view = model.createView(context, viewEnv, null) as LinearLayoutView
        view.measure(
            MeasureSpec.makeMeasureSpec(BOX, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(BOX, MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, BOX, BOX)
        return view
    }

    private companion object {
        private const val BOX = 300
        private const val ITEM_COUNT = 2

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
