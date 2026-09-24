/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import androidx.core.view.children
import android.webkit.WebChromeClient
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.model.AnyModel
import com.urbanairship.android.layout.model.MediaModel
import com.urbanairship.android.layout.model.ModelProperties
import com.urbanairship.android.layout.model.PagerModel
import com.urbanairship.android.layout.property.Background
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.Parallax
import com.urbanairship.android.layout.property.ParallaxBackground
import com.urbanairship.android.layout.util.Factory
import com.urbanairship.android.layout.widget.PagerRecyclerView
import com.urbanairship.json.JsonValue
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A video `backgrounds` layer, unlike color/gradient/image, needs a real, attached view to play
 * at all -- [PagerModel.videoBackgroundModels] gives [PagerView] a real [MediaModel] for it, and
 * this covers what [PagerView] itself is responsible for: adding that model's [MediaView] as an
 * actual child, positioned below the pager's own content, rather than trying to paint it onto the
 * canvas the way [com.urbanairship.android.layout.widget.BackgroundsPainter] paints everything else.
 */
@RunWith(RobolectricTestRunner::class)
public class PagerViewVideoBackgroundTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val viewEnvironment: ViewEnvironment = mockk(relaxed = true) {
        // A relaxed mock can't synthesize a real WebChromeClient for MediaView's WebView to use --
        // needs a concrete instance, same as the real factory would hand back.
        every { webChromeClientFactory() } returns Factory { WebChromeClient() }
    }

    @Test
    public fun aVideoBackgroundLayerIsAddedAsARealChildBelowThePagerContent() {
        val mediaBackground = videoBackground()
        val mediaModel = buildMediaModel(mediaBackground)

        val pagerModel: PagerModel = mockk(relaxed = true) {
            every { viewInfo } returns mockk(relaxed = true) {
                every { backgrounds } returns listOf(
                    ParallaxBackground(background = mediaBackground, parallax = Parallax(0.3f))
                )
            }
            every { pages } returns emptyList()
            every { videoBackgroundModels } returns mapOf(mediaBackground to mediaModel)
        }

        val pagerView = PagerView(context, pagerModel, viewEnvironment)

        assertEquals(2, pagerView.childCount)
        assertTrue(
            "the video's real MediaView must be added before (below, in z-order) the pager's " +
                "own RecyclerView content, or a blend_mode layer meant to sit on top of it " +
                "would have nothing to composite against",
            pagerView.getChildAt(0).hostsAMediaView()
        )
        assertTrue(pagerView.getChildAt(1) is PagerRecyclerView)
    }

    @Test
    public fun aColorOrGradientOnlyPagerAddsNoExtraChild() {
        val colorBackground = Background.ColorBackground(
            color = Color.fromJson(JsonValue.parseString("""{"default":{"type":"hex","hex":"#000000","alpha":1}}""")),
            blendMode = null,
            opacity = null
        )

        val pagerModel: PagerModel = mockk(relaxed = true) {
            every { viewInfo } returns mockk(relaxed = true) {
                every { backgrounds } returns listOf(ParallaxBackground(background = colorBackground, parallax = null))
            }
            every { pages } returns emptyList()
            every { videoBackgroundModels } returns emptyMap()
        }

        val pagerView = PagerView(context, pagerModel, viewEnvironment)

        // Just the RecyclerView -- a color/gradient layer stays canvas-painted, same as before.
        assertEquals(1, pagerView.childCount)
        assertTrue(pagerView.getChildAt(0) is PagerRecyclerView)
    }

    /**
     * A parallaxing layer's spare room is its share of how far the track can actually scroll,
     * which is every page after the first.
     *
     * Sized for one page more, the layer only ever reaches `(pages - 1) / pages` of its own
     * slack: a strip of it can never be scrolled into view, and `media_fit` crops the rest
     * tighter than it needs to be for nothing. It also makes a page's progress depend on how
     * many pages there are, which is what breaks a branching pager -- see
     * [theSameGroundIsCoveredHoweverManyPagesTheRouteHas].
     */
    @Test
    public fun aParallaxingVideoLayerGetsExactlyTheSpareRoomTheTrackCanScroll() {
        val factor = 0.25f
        val videoChild = layerFor(pageCount = 2, factor = factor)

        assertEquals(
            "spare room should be the layer's share of the one page-width the track scrolls",
            BOX_WIDTH + (factor * 1 * BOX_WIDTH).toInt(), videoChild.width
        )
        assertEquals(
            "at rest the layer sits at the box's leading edge, with all of its spare room " +
                "still ahead of it",
            0, videoChild.left
        )
        assertEquals(
            "and its layout params stay at a pixel, so it never decides the host's own size",
            1, videoChild.layoutParams.width
        )
    }

    /**
     * Branching resolves a route as it goes, so the same page can sit in a two-page route or a
     * four-page one. Spare room is the layer's share of the track's own travel either way, so
     * the layer is exactly used up at the end of whichever route was taken -- the last page
     * looks the same however it was reached, which is what the scene claims and what web gets
     * from sizing `--parallax-x-range` to `pages - 1`.
     *
     * Stated as a ratio rather than two figures: what has to hold is that spare room tracks the
     * travel, not any particular width.
     */
    @Test
    public fun theSameGroundIsCoveredHoweverManyPagesTheRouteHas() {
        val factor = 0.5f

        for (pageCount in intArrayOf(2, 3, 5)) {
            val spareRoom = layerFor(pageCount, factor).width - BOX_WIDTH
            assertEquals(
                "a $pageCount-page route should oversize for the $pageCount-1 pages it scrolls",
                (factor * (pageCount - 1) * BOX_WIDTH).toInt(), spareRoom
            )
        }
    }

    /**
     * Branching can re-resolve a route without turning a page -- a form answer on the page
     * showing can lengthen or shorten what follows it. The layer has to be re-sized then and
     * there, since nothing else is going to ask: the pager is the same size, on the same page,
     * and only its page count moved.
     *
     * iOS re-renders off `@Published pageItems` and web off `pages.length`, so both pick this up
     * with no page turn at all.
     */
    @Test
    public fun aRouteThatGrowsWithoutAPageTurnResizesTheLayer() {
        val factor = 0.5f
        val harness = pagerHarness(pageCount = 2, factor = factor)

        val beforeWidth = harness.layer.width
        harness.setPageCount(5)

        assertEquals(
            "a two-page route's layer should have been sized for the one page it scrolls",
            BOX_WIDTH + (factor * 1 * BOX_WIDTH).toInt(), beforeWidth
        )
        assertEquals(
            "and re-sized for four once branching resolved a longer route",
            BOX_WIDTH + (factor * 4 * BOX_WIDTH).toInt(), harness.layer.width
        )
    }

    /** One page cannot scroll, so there is no travel to oversize for and nothing to zoom. */
    @Test
    public fun aSinglePagePagerOversizesNothing() {
        assertEquals(BOX_WIDTH, layerFor(pageCount = 1, factor = 0.5f).width)
    }

    /** A laid-out pager whose page count can change the way branching changes it. */
    private class Harness(
        private val view: PagerView,
        private val listener: PagerModel.Listener,
        private val setPages: (Int) -> Unit
    ) {
        val layer: View get() = view.getChildAt(0)

        /** What `PagerBranchControl` resolving a new route looks like from the view's side. */
        fun setPageCount(count: Int) {
            setPages(count)
            listener.onDataUpdated()
        }
    }

    /** The layer view of a pager laid out at [BOX_WIDTH] with [pageCount] pages. */
    private fun layerFor(pageCount: Int, factor: Float): View =
        pagerHarness(pageCount, factor).layer

    private fun pagerHarness(pageCount: Int, factor: Float): Harness {
        val mediaBackground = videoBackground()
        val mediaModel = buildMediaModel(mediaBackground)
        fun pageModels(count: Int) = (0 until count).map {
            mockk<AnyModel>(relaxed = true) {
                every { createView(any(), any(), any()) } returns View(context)
            }
        }
        var currentPages = pageModels(pageCount)
        val listenerSlot = slot<PagerModel.Listener>()

        val pagerModel: PagerModel = mockk(relaxed = true) {
            every { isSinglePage } returns (pageCount < 2)
            every { viewInfo } returns mockk(relaxed = true) {
                every { isSwipeDisabled } returns false
                every { backgrounds } returns listOf(
                    ParallaxBackground(background = mediaBackground, parallax = Parallax(factor))
                )
            }
            every { pages } answers { currentPages }
            every { videoBackgroundModels } returns mapOf(mediaBackground to mediaModel)
            every { listener = capture(listenerSlot) } just Runs
        }

        val pagerView = PagerView(context, pagerModel, viewEnvironment)
        pagerView.measure(
            MeasureSpec.makeMeasureSpec(BOX_WIDTH, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(BOX_HEIGHT, MeasureSpec.EXACTLY)
        )
        pagerView.layout(0, 0, BOX_WIDTH, BOX_HEIGHT)
        return Harness(pagerView, listenerSlot.captured) { currentPages = pageModels(it) }
    }

    /**
     * A layer showing no controls of its own is hosted inside a wrapper that refuses touches, so
     * it can't swallow a tap meant for the host -- either shape is the layer being rendered.
     */
    private fun View.hostsAMediaView(): Boolean =
        this is MediaView || (this as? ViewGroup)?.children?.any { it is MediaView } == true

    private fun buildMediaModel(mediaBackground: Background.MediaBackground.VideoMediaBackground): MediaModel {
        val environment: ModelEnvironment = mockk(relaxed = true) {
            every { modelScope } returns TestScope(StandardTestDispatcher())
            every { layoutEvents } returns emptyFlow()
            every { layoutState } returns mockk(relaxed = true)
        }
        return MediaModel(
            viewInfo = mediaBackground.mediaInfo,
            pagerState = null,
            videoState = null,
            environment = environment,
            properties = ModelProperties(pagerPageId = null)
        )
    }

    private companion object {
        private const val BOX_WIDTH = 400
        private const val BOX_HEIGHT = 600
    }

    private fun videoBackground(): Background.MediaBackground.VideoMediaBackground {
        val json = """
            {
              "type": "media",
              "media_type": "video",
              "url": "https://test-layout.internal/video.mp4",
              "media_fit": "fit_crop",
              "video": { "autoplay": true, "muted": true, "loop": true, "show_controls": false }
            }
        """.trimIndent()
        return Background.fromJson(JsonValue.parseString(json).requireMap()) as Background.MediaBackground.VideoMediaBackground
    }
}
