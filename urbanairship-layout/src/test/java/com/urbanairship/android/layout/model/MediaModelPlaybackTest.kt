/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.info.MediaInfo
import com.urbanairship.android.layout.property.MediaType
import com.urbanairship.android.layout.property.Video
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class MediaModelPlaybackTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Regression test for ADVSEG-10281: a stray JsPause on a non-interactive video that
     * is playing on the current page (e.g. the WebView pausing its own media pipeline
     * during a RecyclerView relayout) must not latch the video off. It should re-assert
     * play instead of leaving it stuck paused.
     */
    @Test
    public fun testSpuriousJsPauseReassertsPlayForNonInteractiveVideo() {
        val (model, videoState, listener) = buildPlayingModel(showControls = false)

        model.handlePlaybackEvent(MediaModel.PlaybackEvent.JsPause)

        // Re-asserted play rather than latching paused.
        io.mockk.verify { listener.onResume() }
        assertTrue(videoState.changes.value.videos[VIDEO_ID]!!.playing)
    }

    /**
     * A video WITH native controls must keep honoring a JsPause as a user-initiated pause,
     * so the fix doesn't hijack manual pausing of interactive videos.
     */
    @Test
    public fun testJsPauseStillPausesInteractiveVideo() {
        val (model, videoState, _) = buildPlayingModel(showControls = true)

        model.handlePlaybackEvent(MediaModel.PlaybackEvent.JsPause)

        assertFalse(videoState.changes.value.videos[VIDEO_ID]!!.playing)
    }

    /**
     * Builds a MediaModel whose video is visible and playing on the current page, with a
     * relaxed listener. Returns the model, its video state, and the listener.
     */
    private fun buildPlayingModel(
        showControls: Boolean
    ): Triple<MediaModel, SharedState<State.Video>, MediaModel.Listener> {
        val videoState = SharedState(
            State.Video(
                identifier = null,
                videos = mapOf(VIDEO_ID to State.Video.VideoMediaState(playing = true, muted = false)),
                currentVideoId = VIDEO_ID
            )
        )

        val video = Video(
            aspectRatio = null,
            showControls = showControls,
            autoplay = true,
            muted = false,
            loop = true,
            autoResetPosition = false
        )

        val viewInfo = mockk<MediaInfo>(relaxed = true) {
            every { identifier } returns VIDEO_ID
            every { mediaType } returns MediaType.VIDEO
            every { this@mockk.video } returns video
            every { eventHandlers } returns null
        }

        val mockEnv = mockk<ModelEnvironment>(relaxed = true) {
            every { modelScope } returns testScope
            every { layoutState } returns LayoutState.EMPTY
        }

        val model = MediaModel(
            viewInfo = viewInfo,
            pagerState = null,
            videoState = videoState,
            environment = mockEnv,
            properties = ModelProperties(pagerPageId = null)
        )
        testScope.testScheduler.runCurrent()

        val listener = mockk<MediaModel.Listener>(relaxed = true)
        model.listener = listener

        // Video is visible and reported playing on the current page.
        model.handlePlaybackEvent(MediaModel.PlaybackEvent.VisibilityChanged(isVisible = true))
        model.handlePlaybackEvent(MediaModel.PlaybackEvent.JsPlay)

        return Triple(model, videoState, listener)
    }

    private companion object {
        private const val VIDEO_ID = "video-1"
    }
}
