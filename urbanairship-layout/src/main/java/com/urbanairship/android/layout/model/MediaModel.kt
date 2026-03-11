/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.UALog
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.MediaInfo
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.view.MediaView
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class MediaModel(
    viewInfo: MediaInfo,
    val pagerState: SharedState<State.Pager>?,
    val videoState: SharedState<State.Video>?,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<MediaView, MediaInfo, MediaModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {

    internal sealed interface PlaybackEvent {
        data object VideoReady : PlaybackEvent
        data object JsPlay : PlaybackEvent
        data object JsPause : PlaybackEvent
        data object VideoEnded : PlaybackEvent
        data object VisibilityVisible : PlaybackEvent
        data class PageVisibilityChanged(val isCurrentPage: Boolean) : PlaybackEvent
        data class VideoStateChanged(val state: State.Video.VideoMediaState) : PlaybackEvent
        data class StoryPauseChanged(val isPaused: Boolean) : PlaybackEvent
        data object BackgroundPause : PlaybackEvent
        data object BackgroundResume : PlaybackEvent
    }

    val mediaViewId: Int = View.generateViewId()

    val videoId: String = viewInfo.identifier

    private val playGroup: String? = environment.layoutState.videoControl?.playGroup
    private val muteGroup: String? = environment.layoutState.videoControl?.muteGroup

    val groupPlaying: Boolean?
        get() = playGroup?.let { videoState?.changes?.value?.playGroupState?.get(it) }
    val groupMuted: Boolean?
        get() = muteGroup?.let { videoState?.changes?.value?.muteGroupState?.get(it) }

    private val isPlayableMedia: Boolean = viewInfo.mediaType.isPlayable

    /**
     * Local play intent used when there is no VideoController (videoState is null).
     * Three states: null = initial (autoplay setting decides), true = playing/was playing,
     * false = user explicitly paused. Updated by user interaction (play/pause controls)
     * but NOT by system pauses (swipe-away, backgrounding).
     */
    var desiredPlayState: Boolean? = null

    private var isSystemPausing = false
    private var jsPlaying = false
    private var jsMuted = false

    private var events = Channel<PlaybackEvent>(Channel.UNLIMITED)

    fun sendEvent(event: PlaybackEvent) {
        events.trySend(event)
    }

    interface Listener : BaseModel.Listener {
        fun onPause()
        fun onResume()
        fun onMute()
        fun onUnmute()
        fun onSeekToBeginning()
    }

    /** Media views are shrinkable by default. */
    override var isShrinkable: Boolean = true

    init {
        if (isPlayableMedia) { registerVideoState() }
    }

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = MediaView(context, this, viewEnvironment, itemProperties).apply {
        id = viewId
    }

    override fun onViewAttached(view: MediaView) {
        handleTapEvents(view)

        if (!isPlayableMedia) return

        isSystemPausing = isStoryPaused() || !isOnCurrentPage()
        jsPlaying = false
        jsMuted = false
        events = Channel(Channel.UNLIMITED)

        viewScope.launch {
            for (event in events) {
                handlePlaybackEvent(event)
            }
        }

        startEventSources(videoId)
    }

    private fun handleTapEvents(view: MediaView) {
        if (viewInfo.eventHandlers.hasTapHandler()) {
            viewScope.launch {
                view.taps().collect { handleViewEvent(EventHandler.Type.TAP) }
            }
        }
    }

    /**
     * Registers this video's initial state in the shared video state so that sibling views
     * (e.g. play/pause buttons with view_overrides) can resolve the current state before the
     * media view is attached to the window.
     */
    private fun registerVideoState() {
        val videoState = videoState ?: return
        val currentState = videoState.changes.value

        val defaultPlaying = viewInfo.video?.autoplay ?: false
        val defaultMuted = viewInfo.video?.muted ?: false

        val myState = currentState.videos[videoId]
            ?: currentState.current?.let { inherited ->
                State.Video.VideoMediaState(
                    playing = inherited.playing,
                    muted = inherited.muted
                )
            }
            ?: State.Video.VideoMediaState(
                playing = playGroup?.let { currentState.playGroupState[it] } ?: defaultPlaying,
                muted = muteGroup?.let { currentState.muteGroupState[it] } ?: defaultMuted
            )

        videoState.update { state ->
            val updatedPlayState = playGroup?.takeIf { it !in state.playGroupState }
                ?.let { state.playGroupState + (it to myState.playing) }
                ?: state.playGroupState

            val updatedMuteState = muteGroup?.takeIf { it !in state.muteGroupState }
                ?.let { state.muteGroupState + (it to myState.muted) }
                ?: state.muteGroupState

            state.copy(
                videos = state.videos + (videoId to myState),
                currentVideoId = state.currentVideoId ?: videoId,
                playGroupState = updatedPlayState,
                muteGroupState = updatedMuteState
            )
        }
    }

    private fun startEventSources(vid: String) {
        viewScope.launch {
            videoState?.changes
                ?.map { it.videos[vid] }
                ?.distinctUntilChanged()
                ?.drop(1)
                ?.collect { state ->
                    if (state != null) {
                        events.trySend(PlaybackEvent.VideoStateChanged(state))
                    }
                }
        }

        viewScope.launch {
            pagerState?.changes
                ?.distinctUntilChanged { old, new -> old.isStoryPaused == new.isStoryPaused }
                ?.collect {
                    events.trySend(PlaybackEvent.StoryPauseChanged(it.isStoryPaused))
                }
        }

        viewScope.launch {
            pagerState?.changes
                ?.map { it.pageIds.getOrNull(it.pageIndex) }
                ?.distinctUntilChanged()
                ?.collect { currentPageId ->
                    if (currentPageId == properties.pagerPageId) {
                        videoState?.update { it.copy(currentVideoId = vid) }
                    }
                }
        }

        val pagerPageId = properties.pagerPageId
        if (pagerPageId != null) {
            viewScope.launch {
                pagerState?.changes
                    ?.map { it.pageIds.getOrNull(it.pageIndex) == pagerPageId }
                    ?.distinctUntilChanged()
                    ?.collect { isCurrentPage ->
                        events.trySend(PlaybackEvent.PageVisibilityChanged(isCurrentPage))
                    }
            }
        }
    }

    private fun handlePlaybackEvent(event: PlaybackEvent) {
        UALog.v { "Media playback event: $event" }
        when (event) {
            is PlaybackEvent.VideoReady -> {
                pagerState?.update { state -> state.copyWithMediaPaused(false) }
                val myState = videoState?.changes?.value?.videos?.get(videoId)
                if (myState != null) {
                    reconcileState()
                } else if (shouldBePlaying()) {
                    listener?.onResume()
                }
            }

            is PlaybackEvent.JsPlay -> {
                jsPlaying = true
                if (isSystemPausing) return
                if (videoState != null) {
                    videoState.update { state ->
                        val current = state.videos[videoId] ?: return@update state
                        state.copy(videos = state.videos + (videoId to current.copy(playing = true)))
                    }
                } else {
                    desiredPlayState = true
                }
            }

            is PlaybackEvent.JsPause -> {
                jsPlaying = false
                if (isSystemPausing) return
                if (videoState != null) {
                    videoState.update { state ->
                        val current = state.videos[videoId] ?: return@update state
                        state.copy(videos = state.videos + (videoId to current.copy(playing = false)))
                    }
                } else {
                    desiredPlayState = false
                }
            }

            is PlaybackEvent.VideoEnded -> {
                if (viewInfo.video?.loop == true) return

                desiredPlayState = false
                videoState?.update { state ->
                    val current = state.videos[videoId] ?: return@update state
                    state.copy(videos = state.videos + (videoId to current.copy(playing = false)))
                }
            }

            is PlaybackEvent.VisibilityVisible -> {
                isSystemPausing = false
                reconcileState()
            }

            is PlaybackEvent.PageVisibilityChanged -> {
                if (event.isCurrentPage) {
                    if (!isStoryPaused()) {
                        isSystemPausing = false
                        reconcileState()
                    }
                } else {
                    if (viewInfo.video?.autoResetPosition == true) {
                        listener?.onSeekToBeginning()
                    }
                    isSystemPausing = true
                    listener?.onPause()
                }
            }

            is PlaybackEvent.VideoStateChanged -> {
                reconcileState()
            }

            is PlaybackEvent.StoryPauseChanged -> {
                if (event.isPaused) {
                    isSystemPausing = true
                    listener?.onPause()
                } else if (isOnCurrentPage()) {
                    isSystemPausing = false
                    reconcileState()
                }
            }

            is PlaybackEvent.BackgroundPause -> {
                isSystemPausing = true
                listener?.onPause()
            }

            is PlaybackEvent.BackgroundResume -> {
                isSystemPausing = false
                reconcileState()
            }
        }
    }

    private fun reconcileState() {
        if (isSystemPausing) {
            if (jsPlaying) listener?.onPause()
            return
        }

        val desiredPlaying = shouldBePlaying()
        if (desiredPlaying && !jsPlaying) {
            listener?.onResume()
        } else if (!desiredPlaying && jsPlaying) {
            listener?.onPause()
        }

        val desiredMuted = videoState?.changes?.value?.videos?.get(videoId)?.muted ?: return
        if (desiredMuted && !jsMuted) {
            jsMuted = true
            listener?.onMute()
        } else if (!desiredMuted && jsMuted) {
            jsMuted = false
            listener?.onUnmute()
        }
    }

    private fun isStoryPaused(): Boolean {
        return pagerState?.changes?.value?.isStoryPaused == true
    }

    private fun isOnCurrentPage(): Boolean {
        val pageId = properties.pagerPageId ?: return true
        val pager = pagerState?.changes?.value ?: return true
        return pager.pageIds.getOrNull(pager.pageIndex) == pageId
    }

    internal fun shouldBePlaying(): Boolean {
        if (isStoryPaused() || isSystemPausing || !isOnCurrentPage()) {
            return false
        }

        if (videoState != null) {
            return videoState.changes.value.videos[videoId]?.playing == true
        }

        val autoplay = viewInfo.video?.autoplay ?: false
        return if (autoplay) {
            desiredPlayState != false
        } else {
            desiredPlayState == true
        }
    }
}
