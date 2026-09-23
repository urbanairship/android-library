/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.view

import android.content.Context
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.core.view.children
import android.webkit.WebChromeClient
import androidx.test.core.app.ApplicationProvider
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ViewEnvironment
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
import io.mockk.every
import io.mockk.mockk
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
                    ParallaxBackground(background = mediaBackground, parallax = Parallax(Parallax.Axis.HORIZONTAL, 0.3f))
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
     * A parallaxing layer is given a page's worth of spare room beyond what the track can
     * actually scroll, and sits at the leading end of it at rest.
     *
     * Sizing it to the track's own travel instead splits the motion evenly about the box's
     * centre: the layer starts half its travel to one side and ends half to the other, which
     * reads as barely moved at both ends. The extra page holds it fully off-centre on page one
     * and lets it arrive centred on the last page. iOS and web both place it this way, and a
     * real-device comparison against them is what caught this -- "android is the odd one out."
     */
    @Test
    public fun aParallaxingVideoLayerGetsAPageMoreSpareRoomThanTheTrackCanScroll() {
        val boxWidth = 400
        val pageCount = 2
        val factor = 0.25f
        val mediaBackground = videoBackground()
        val mediaModel = buildMediaModel(mediaBackground)

        val pagerModel: PagerModel = mockk(relaxed = true) {
            every { isSinglePage } returns false
            every { viewInfo } returns mockk(relaxed = true) {
                every { isSwipeDisabled } returns false
                every { backgrounds } returns listOf(
                    ParallaxBackground(
                        background = mediaBackground,
                        parallax = Parallax(Parallax.Axis.HORIZONTAL, factor)
                    )
                )
            }
            every { pages } returns (0 until pageCount).map {
                mockk(relaxed = true) { every { createView(any(), any(), any()) } returns View(context) }
            }
            every { videoBackgroundModels } returns mapOf(mediaBackground to mediaModel)
        }

        val pagerView = PagerView(context, pagerModel, viewEnvironment)
        pagerView.measure(
            MeasureSpec.makeMeasureSpec(boxWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(600, MeasureSpec.EXACTLY)
        )
        pagerView.layout(0, 0, boxWidth, 600)

        val params = pagerView.getChildAt(0).layoutParams as MarginLayoutParams
        assertEquals(
            "spare room should be the layer's share of every page's width, not of the " +
                "${pageCount - 1} page-widths the track itself scrolls",
            boxWidth + (factor * pageCount * boxWidth).toInt(), params.width
        )
        assertEquals(
            "at rest the layer sits at the box's leading edge, with all of its spare room " +
                "still ahead of it",
            0, params.leftMargin
        )
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
