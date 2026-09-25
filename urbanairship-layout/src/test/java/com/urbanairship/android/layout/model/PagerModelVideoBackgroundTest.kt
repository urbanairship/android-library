/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.info.PagerInfo
import com.urbanairship.android.layout.property.Background
import com.urbanairship.android.layout.property.Color
import com.urbanairship.android.layout.property.ParallaxBackground
import com.urbanairship.json.JsonValue
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A video `backgrounds` layer plays through a real, attached [MediaModel]/`MediaView` -- the same
 * classes an in-flow `media` view uses -- rather than [com.urbanairship.android.layout.widget.BackgroundsPainter]'s
 * own canvas painting (see [PagerModel.videoBackgroundModels]). This covers that wiring: the right
 * layers get a model, and that model reads the pager's own [SharedState] instances rather than
 * building its own -- the same instances a real in-flow video's `video_controller`/pager-selection
 * lifecycle already reads and writes.
 */
@RunWith(RobolectricTestRunner::class)
public class PagerModelVideoBackgroundTest {

    private val pagerState: SharedState<State.Pager> =
        SharedState(State.Pager(identifier = PAGER_ID, pageIds = PAGE_IDS))
    private val videoState: SharedState<State.Video> = SharedState(State.Video(identifier = null))

    private val testScope = TestScope(StandardTestDispatcher())

    private val mockEnv: ModelEnvironment = mockk(relaxed = true, relaxUnitFun = true) {
        every { modelScope } returns testScope
        every { layoutEvents } returns emptyFlow()
        every { actionsRunner } returns mockk(relaxUnitFun = true) {
            coEvery { run(any(), any()) } answers { nothing }
        }
        every { layoutState } returns mockk(relaxed = true) {
            every { pager } returns pagerState
            every { video } returns videoState
            every { reportingContext(any(), any(), any()) } returns mockk()
        }
    }

    /**
     * A pager nested in another pager's page carries that *outer* page's id in its own
     * `properties`, while the state its background layer is scoped against is this pager's.
     * Handing the outer id to the layer compares it against inner page ids it can never equal,
     * which reads as permanently off-page: never playing, and never holding a story.
     */
    @Test
    public fun aNestedPagersBackgroundVideoStillPlaysOnEveryInnerPage() {
        val videoLayer = videoBackground(url = "https://test-layout.internal/video.mp4")
        val model = buildModel(
            listOf(ParallaxBackground(background = videoLayer, parallax = null)),
            properties = ModelProperties(pagerPageId = "a-page-of-the-enclosing-pager")
        )
        val mediaModel = model.videoBackgroundModels.getValue(videoLayer)

        pagerState.update { it.copyWithPageIndex(0) }
        assertTrue(mediaModel.shouldBePlaying())

        pagerState.update { it.copyWithPageIndex(1) }
        assertTrue(mediaModel.shouldBePlaying())

        mediaModel.setMediaLoading(true)
        assertTrue(
            "and it still holds a story on the pager it actually belongs to",
            pagerState.changes.value.isMediaPaused
        )
    }

    private fun buildModel(
        layers: List<ParallaxBackground>?,
        properties: ModelProperties = ModelProperties(pagerPageId = null)
    ): PagerModel {
        val viewInfo = mockk<PagerInfo>(relaxUnitFun = true) {
            every { backgrounds } returns layers
            every { gestures } returns emptyList()
            every { disableSwipeWhen } returns null
        }
        return PagerModel(
            availablePages = ITEMS,
            viewInfo = viewInfo,
            pagerState = pagerState,
            environment = mockEnv,
            properties = properties
        )
    }

    @Test
    public fun onlyVideoMediaLayersGetAModel() {
        val videoLayer = videoBackground(url = "https://test-layout.internal/video.mp4")
        val imageLayer = mediaBackground(mediaType = "image", url = "https://test-layout.internal/image.jpg")
        val colorLayer = Background.ColorBackground(
            color = Color.fromJson(JsonValue.parseString("""{"default":{"type":"hex","hex":"#000000","alpha":1}}""")),
            blendMode = null,
            opacity = null
        )

        val model = buildModel(
            listOf(
                ParallaxBackground(background = videoLayer, parallax = null),
                ParallaxBackground(background = imageLayer, parallax = null),
                ParallaxBackground(background = colorLayer, parallax = null)
            )
        )

        assertEquals(1, model.videoBackgroundModels.size)
        assertTrue(model.videoBackgroundModels.containsKey(videoLayer))
    }

    @Test
    public fun theVideoModelReadsThePagersOwnSharedState() {
        val videoLayer = videoBackground(url = "https://test-layout.internal/video.mp4")
        val model = buildModel(listOf(ParallaxBackground(background = videoLayer, parallax = null)))

        val mediaModel = model.videoBackgroundModels.getValue(videoLayer)

        // Not equal copies -- the exact same instances a real in-flow MediaModel would be given
        // via ModelFactory, so a video_toggle_play/video_toggle_mute button and the pager's own
        // page-change signal reach this model too, rather than a second, disconnected one.
        assertSame(pagerState, mediaModel.pagerState)
        assertSame(videoState, mediaModel.videoState)
        assertEquals("https://test-layout.internal/video.mp4", mediaModel.viewInfo.url)
    }

    @Test
    public fun aPagerLevelVideoBackgroundKeepsPlayingAcrossEveryPage() {
        val videoLayer = videoBackground(url = "https://test-layout.internal/video.mp4")
        val model = buildModel(listOf(ParallaxBackground(background = videoLayer, parallax = null)))
        val mediaModel = model.videoBackgroundModels.getValue(videoLayer)

        // Registered by MediaModel's own init{} against the real, shared videoState -- proves the
        // model this test inspects is the one actually driving playback state, not a mock.
        assertTrue(videoState.changes.value.videos.containsKey(mediaModel.videoId))

        // It's the pager's own background, not any one page's -- unlike an in-flow video, a page
        // turn is never a reason to pause (or, with auto_reset_position, rewind) it.
        pagerState.update { it.copyWithPageIndex(0) }
        assertTrue(mediaModel.shouldBePlaying())

        pagerState.update { it.copyWithPageIndex(1) }
        assertTrue(mediaModel.shouldBePlaying())

        pagerState.update { it.copyWithPageIndex(0) }
        assertTrue(mediaModel.shouldBePlaying())
    }

    /**
     * A story's automated advance waits for the videos its page is showing, and a pager-level
     * background video is showing on all of them.
     *
     * `MediaModel.setMediaLoading` used to open with `properties.pagerPageId ?: return`, which
     * for one of these models -- deliberately scoped to no page, see
     * [aPagerLevelVideoBackgroundKeepsPlayingAcrossEveryPage] -- meant the hold was never taken
     * out at all, and a story advanced off a page whose video hadn't loaded.
     */
    @Test
    public fun aBackgroundVideoStillLoadingHoldsTheStoryOnWhicheverPageIsShowing() {
        val videoLayer = videoBackground(url = "https://test-layout.internal/video.mp4")
        val model = buildModel(listOf(ParallaxBackground(background = videoLayer, parallax = null)))
        val mediaModel = model.videoBackgroundModels.getValue(videoLayer)

        assertFalse(pagerState.changes.value.isMediaPaused)

        mediaModel.setMediaLoading(true)
        assertTrue(pagerState.changes.value.isMediaPaused)

        pagerState.update { it.copyWithPageIndex(1) }
        assertTrue(
            "it's still on screen on page two, so it's still worth waiting for",
            pagerState.changes.value.isMediaPaused
        )

        mediaModel.setMediaLoading(false)
        assertFalse(pagerState.changes.value.isMediaPaused)
    }

    @Test
    public fun noBackgroundsMeansNoVideoModels() {
        val model = buildModel(layers = null)
        assertTrue(model.videoBackgroundModels.isEmpty())
        assertNull(videoState.changes.value.currentVideoId)
    }

    private fun mediaBackground(mediaType: String, url: String): Background.MediaBackground {
        val json = """
            {
              "type": "media",
              "media_type": "$mediaType",
              "url": "$url",
              "media_fit": "fit_crop",
              "video": { "autoplay": true, "muted": true, "loop": true, "show_controls": false }
            }
        """.trimIndent()
        return Background.fromJson(JsonValue.parseString(json).requireMap()) as Background.MediaBackground
    }

    private fun videoBackground(url: String): Background.MediaBackground.VideoMediaBackground =
        mediaBackground(mediaType = "video", url = url) as Background.MediaBackground.VideoMediaBackground

    private companion object {
        private const val PAGER_ID = "video-pager"
        private const val PAGE_1_ID = "page-1"
        private const val PAGE_2_ID = "page-2"
        private val PAGE_IDS = listOf(PAGE_1_ID, PAGE_2_ID)

        private val ITEMS = listOf(
            PagerModel.Item(mockk(relaxed = true), PAGE_1_ID, emptyList(), emptyList(), emptyList(), null),
            PagerModel.Item(mockk(relaxed = true), PAGE_2_ID, emptyList(), emptyList(), emptyList(), null)
        )
    }
}
