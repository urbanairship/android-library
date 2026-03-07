/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.MediaInfo
import com.urbanairship.android.layout.property.EventHandler
import com.urbanairship.android.layout.property.hasTapHandler
import com.urbanairship.android.layout.view.MediaView
import kotlinx.coroutines.flow.distinctUntilChanged
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

    val mediaViewId: Int = View.generateViewId()

    val videoId: String? = viewInfo.identifier

    private val playGroup: String? = environment.layoutState.videoControl?.playGroup
    private val muteGroup: String? = environment.layoutState.videoControl?.muteGroup

    val groupPlaying: Boolean?
        get() = playGroup?.let { videoState?.changes?.value?.playGroupState?.get(it) }
    val groupMuted: Boolean?
        get() = muteGroup?.let { videoState?.changes?.value?.muteGroupState?.get(it) }

    private val isPlayableMedia: Boolean = viewInfo.mediaType.isPlayable

    interface Listener : BaseModel.Listener {
        fun onPause()
        fun onResume()
        fun onMute()
        fun onUnmute()
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

        val videoId = videoId ?: return
        if (!isPlayableMedia) return

        videoState?.changes?.value?.videos?.get(videoId)?.let(::applyVideoMediaState)
        observeVideoStateChanges(videoId)
        observeStoryPauseState()
        trackCurrentVideoOnPageChange(videoId)
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
        val videoId = videoId ?: return
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

    private fun observeVideoStateChanges(vid: String) {
        viewScope.launch {
            videoState?.changes
                ?.map { it.videos[vid] }
                ?.distinctUntilChanged()
                ?.collect { state ->
                    if (state != null) applyVideoMediaState(state)
                }
        }
    }

    private fun observeStoryPauseState() {
        if (videoState != null) return
        viewScope.launch {
            pagerState?.changes
                ?.distinctUntilChanged { old, new -> old.isStoryPaused == new.isStoryPaused }
                ?.collect {
                    if (it.isStoryPaused) listener?.onPause() else listener?.onResume()
                }
        }
    }

    private fun trackCurrentVideoOnPageChange(vid: String) {
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
    }

    private fun applyVideoMediaState(state: State.Video.VideoMediaState) {
        if (state.playing) listener?.onResume() else listener?.onPause()
        if (state.muted) listener?.onMute() else listener?.onUnmute()
    }
}
