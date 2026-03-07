/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ResolvedVideoCommand
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.VideoCommand
import com.urbanairship.android.layout.environment.VideoCommandChannel
import com.urbanairship.android.layout.environment.VideoControlState
import com.urbanairship.android.layout.environment.VideoGroupBroadcastChannel
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.VideoControllerInfo
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestResult
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
public class VideoControllerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var commandChannel: VideoCommandChannel
    private lateinit var broadcastChannel: VideoGroupBroadcastChannel
    private lateinit var videoControlState: VideoControlState
    private lateinit var videoState: SharedState<State.Video>
    private lateinit var controller: VideoController

    private val mockView: AnyModel = mockk(relaxed = true)

    @Before
    public fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    public fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    public fun testCreateView() {
        initController()

        val context: Context = mockk(relaxed = true)
        val viewEnv: ViewEnvironment = mockk(relaxed = true)
        val itemProperties = ItemProperties(size = null)
        controller.createView(context, viewEnv, itemProperties)

        verify { mockView.createView(eq(context), eq(viewEnv), itemProperties) }
    }

    @Test
    public fun testTogglePlayWhenPaused(): TestResult = runTest {
        initControllerWithVideo(playing = false, muted = false)

        broadcastChannel.commands.test {
            commandChannel.send(VideoCommand.TogglePlay)
            testScheduler.runCurrent()

            val resolved = awaitItem()
            assertTrue(resolved is ResolvedVideoCommand.Play)
        }
    }

    @Test
    public fun testTogglePlayWhenPlaying(): TestResult = runTest {
        initControllerWithVideo(playing = true, muted = false)

        broadcastChannel.commands.test {
            commandChannel.send(VideoCommand.TogglePlay)
            testScheduler.runCurrent()

            val resolved = awaitItem()
            assertTrue(resolved is ResolvedVideoCommand.Pause)
        }
    }

    @Test
    public fun testToggleMuteWhenUnmuted(): TestResult = runTest {
        initControllerWithVideo(playing = false, muted = false)

        broadcastChannel.commands.test {
            commandChannel.send(VideoCommand.ToggleMute)
            testScheduler.runCurrent()

            val resolved = awaitItem()
            assertTrue(resolved is ResolvedVideoCommand.Mute)
        }
    }

    @Test
    public fun testToggleMuteWhenMuted(): TestResult = runTest {
        initControllerWithVideo(playing = false, muted = true)

        broadcastChannel.commands.test {
            commandChannel.send(VideoCommand.ToggleMute)
            testScheduler.runCurrent()

            val resolved = awaitItem()
            assertTrue(resolved is ResolvedVideoCommand.Unmute)
        }
    }

    @Test
    public fun testPlayCommand(): TestResult = runTest {
        initControllerWithVideo(playing = false, muted = false)

        broadcastChannel.commands.test {
            commandChannel.send(VideoCommand.Play)
            testScheduler.runCurrent()

            val resolved = awaitItem()
            assertTrue(resolved is ResolvedVideoCommand.Play)
        }
    }

    @Test
    public fun testPauseCommand(): TestResult = runTest {
        initControllerWithVideo(playing = true, muted = false)

        broadcastChannel.commands.test {
            commandChannel.send(VideoCommand.Pause)
            testScheduler.runCurrent()

            val resolved = awaitItem()
            assertTrue(resolved is ResolvedVideoCommand.Pause)
        }
    }

    @Test
    public fun testMuteCommand(): TestResult = runTest {
        initControllerWithVideo(playing = false, muted = false)

        broadcastChannel.commands.test {
            commandChannel.send(VideoCommand.Mute)
            testScheduler.runCurrent()

            val resolved = awaitItem()
            assertTrue(resolved is ResolvedVideoCommand.Mute)
        }
    }

    @Test
    public fun testUnmuteCommand(): TestResult = runTest {
        initControllerWithVideo(playing = false, muted = true)

        broadcastChannel.commands.test {
            commandChannel.send(VideoCommand.Unmute)
            testScheduler.runCurrent()

            val resolved = awaitItem()
            assertTrue(resolved is ResolvedVideoCommand.Unmute)
        }
    }

    @Test
    public fun testBroadcastPlayUpdatesVideoState(): TestResult = runTest {
        initControllerWithVideo(playing = false, muted = false)

        videoState.changes.test {
            val initial = awaitItem()
            assertFalse(initial.videos[VIDEO_ID]!!.playing)

            commandChannel.send(VideoCommand.Play)
            testScheduler.runCurrent()

            val updated = awaitItem()
            assertTrue(updated.videos[VIDEO_ID]!!.playing)
        }
    }

    @Test
    public fun testBroadcastPauseUpdatesVideoState(): TestResult = runTest {
        initControllerWithVideo(playing = true, muted = false)

        videoState.changes.test {
            val initial = awaitItem()
            assertTrue(initial.videos[VIDEO_ID]!!.playing)

            commandChannel.send(VideoCommand.Pause)
            testScheduler.runCurrent()

            val updated = awaitItem()
            assertFalse(updated.videos[VIDEO_ID]!!.playing)
        }
    }

    @Test
    public fun testBroadcastMuteUpdatesVideoState(): TestResult = runTest {
        initControllerWithVideo(playing = false, muted = false)

        videoState.changes.test {
            val initial = awaitItem()
            assertFalse(initial.videos[VIDEO_ID]!!.muted)

            commandChannel.send(VideoCommand.Mute)
            testScheduler.runCurrent()

            val updated = awaitItem()
            assertTrue(updated.videos[VIDEO_ID]!!.muted)
        }
    }

    @Test
    public fun testBroadcastUnmuteUpdatesVideoState(): TestResult = runTest {
        initControllerWithVideo(playing = false, muted = true)

        videoState.changes.test {
            val initial = awaitItem()
            assertTrue(initial.videos[VIDEO_ID]!!.muted)

            commandChannel.send(VideoCommand.Unmute)
            testScheduler.runCurrent()

            val updated = awaitItem()
            assertFalse(updated.videos[VIDEO_ID]!!.muted)
        }
    }

    @Test
    public fun testPlayGroupStateUpdatedOnPlay(): TestResult = runTest {
        initControllerWithVideo(playing = false, muted = false)

        videoState.changes.test {
            awaitItem()

            commandChannel.send(VideoCommand.Play)
            testScheduler.runCurrent()

            val updated = awaitItem()
            assertTrue(updated.playGroupState[PLAY_GROUP] == true)
        }
    }

    @Test
    public fun testMuteGroupStateUpdatedOnMute(): TestResult = runTest {
        initControllerWithVideo(playing = false, muted = false)

        videoState.changes.test {
            awaitItem()

            commandChannel.send(VideoCommand.Mute)
            testScheduler.runCurrent()

            val updated = awaitItem()
            assertTrue(updated.muteGroupState[MUTE_GROUP] == true)
        }
    }

    private fun initController() {
        videoState = spyk(SharedState(State.Video(identifier = null)))

        val mockEnv: ModelEnvironment = mockk(relaxed = true) {
            every { modelScope } returns testScope
            every { layoutState } returns LayoutState.EMPTY
        }

        controller = VideoController(
            viewInfo = mockk<VideoControllerInfo>(relaxed = true),
            view = mockView,
            videoState = videoState,
            environment = mockEnv,
            properties = ModelProperties(pagerPageId = null)
        )
        testScope.runCurrent()
    }

    private fun initControllerWithVideo(playing: Boolean, muted: Boolean) {
        commandChannel = VideoCommandChannel()
        broadcastChannel = VideoGroupBroadcastChannel()

        videoControlState = VideoControlState(
            commandChannel = commandChannel,
            broadcastChannel = broadcastChannel,
            parentCommandChannel = null,
            parentBroadcastChannel = null,
            parentVideoState = null,
            playGroup = PLAY_GROUP,
            muteGroup = MUTE_GROUP
        )

        videoState = spyk(SharedState(State.Video(
            identifier = null,
            videos = mapOf(VIDEO_ID to State.Video.VideoMediaState(playing = playing, muted = muted)),
            currentVideoId = VIDEO_ID,
            currentPlayGroup = PLAY_GROUP,
            currentMuteGroup = MUTE_GROUP
        )))

        val layoutState: LayoutState = mockk(relaxed = true) {
            every { videoControl } returns videoControlState
            every { pager } returns null
        }

        val mockEnv: ModelEnvironment = mockk(relaxed = true) {
            every { modelScope } returns testScope
            every { this@mockk.layoutState } returns layoutState
        }

        controller = VideoController(
            viewInfo = mockk<VideoControllerInfo>(relaxed = true),
            view = mockView,
            videoScope = null,
            muteGroup = MUTE_GROUP,
            playGroup = PLAY_GROUP,
            videoState = videoState,
            environment = mockEnv,
            properties = ModelProperties(pagerPageId = null)
        )
        testScope.runCurrent()
    }

    private companion object {
        private const val VIDEO_ID = "video-1"
        private const val PLAY_GROUP = "play-group-1"
        private const val MUTE_GROUP = "mute-group-1"
    }
}
