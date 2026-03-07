/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model

import android.content.Context
import android.view.View
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.ResolvedVideoCommand.Mute
import com.urbanairship.android.layout.environment.ResolvedVideoCommand.Pause
import com.urbanairship.android.layout.environment.ResolvedVideoCommand.Play
import com.urbanairship.android.layout.environment.ResolvedVideoCommand.Unmute
import com.urbanairship.android.layout.environment.SharedState
import com.urbanairship.android.layout.environment.State
import com.urbanairship.android.layout.environment.VideoCommand
import com.urbanairship.android.layout.environment.VideoControlState
import com.urbanairship.android.layout.environment.ViewEnvironment
import com.urbanairship.android.layout.info.VideoControllerInfo
import java.util.UUID
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Controller that manages video playback for VideoMedia descendants within its view.
 * Buttons with video control behaviors (video_play, video_pause, etc.) must have a VideoController ancestor.
 * When a video control behavior is triggered, it applies to the current video (tracked by currentVideoId).
 *
 * In a nested hierarchy, child controllers forward commands up to the root.
 * The root resolves toggle commands and broadcasts resolved commands down to children by group.
 * Children also sync their video state up to the parent so the root can resolve toggles
 * and button view_overrides can reflect the current state.
 */
internal class VideoController(
    viewInfo: VideoControllerInfo,
    val view: AnyModel,
    val videoScope: List<String>? = null,
    val muteGroup: String? = null,
    val playGroup: String? = null,
    private val videoState: SharedState<State.Video>,
    environment: ModelEnvironment,
    properties: ModelProperties
) : BaseModel<View, VideoControllerInfo, BaseModel.Listener>(
    viewInfo = viewInfo,
    environment = environment,
    properties = properties
) {
    private val videoControls: VideoControlState? = environment.layoutState.videoControl
    private val effectivePlayGroup: String =
        videoControls?.playGroup ?: playGroup ?: UUID.randomUUID().toString()
    private val effectiveMuteGroup: String =
        videoControls?.muteGroup ?: muteGroup ?: UUID.randomUUID().toString()

    init {
        videoControls?.let(::initVideoController)
    }

    override fun onCreateView(
        context: Context,
        viewEnvironment: ViewEnvironment,
        itemProperties: ItemProperties?
    ) = view.createView(context, viewEnvironment, itemProperties)

    private fun initVideoController(controlState: VideoControlState) {
        // Command handling: forward to parent or resolve at root
        modelScope.launch {
            controlState.commandChannel.commands.collect { command ->
                val parentChannel = controlState.parentCommandChannel
                if (parentChannel != null) {
                    parentChannel.send(command)
                } else {
                    handleAsRoot(command)
                }
            }
        }

        // Listen for resolved commands broadcast from parent, or own broadcast when root
        val broadcastChannel = controlState.parentBroadcastChannel ?: controlState.broadcastChannel

        modelScope.launch {
            broadcastChannel.commands.collect { resolved ->
                when (resolved) {
                    is Play -> if (resolved.targetGroup == effectivePlayGroup) handleVideoPlay()
                    is Pause -> if (resolved.targetGroup == effectivePlayGroup) handleVideoPause()
                    is Mute -> if (resolved.targetGroup == effectiveMuteGroup) handleVideoMute()
                    is Unmute -> if (resolved.targetGroup == effectiveMuteGroup) handleVideoUnmute()
                }
            }
        }

        // Sync state up to parent so the root can resolve toggles and button icons update.
        // Combines video state with pager state so the parent is updated both when
        // video state changes (e.g. mute broadcast) AND when the page changes back
        // to this controller (even if video state didn't change).
        val parentVideoState = controlState.parentVideoState
        if (parentVideoState != null) {
            modelScope.launch {
                combine(
                    videoState.changes,
                    environment.layoutState.pager?.changes ?: flowOf(null)
                ) { childState, pagerState ->
                    val isCurrentPage = pagerState?.let {
                        it.pageIds.getOrNull(it.pageIndex) == properties.pagerPageId
                    } ?: true
                    Pair(childState, isCurrentPage)
                }.collect { (childState, isCurrentPage) ->
                    parentVideoState.update { parentState ->
                        parentState.copy(
                            videos = parentState.videos + childState.videos,
                            currentVideoId =
                                if (isCurrentPage) childState.currentVideoId ?: parentState.currentVideoId
                                else parentState.currentVideoId,
                            currentPlayGroup =
                                if (isCurrentPage && childState.currentVideoId != null) effectivePlayGroup
                                else parentState.currentPlayGroup,
                            currentMuteGroup =
                                if (isCurrentPage && childState.currentVideoId != null) effectiveMuteGroup
                                else parentState.currentMuteGroup,
                            playGroupState = parentState.playGroupState + childState.playGroupState,
                            muteGroupState = parentState.muteGroupState + childState.muteGroupState
                        )
                    }
                }
            }
        }
    }

    private fun handleAsRoot(command: VideoCommand) {
        val broadcast = videoControls?.broadcastChannel ?: return
        val currentState = videoState.changes.value
        val targetPlayGroup = currentState.currentPlayGroup ?: effectivePlayGroup
        val targetMuteGroup = currentState.currentMuteGroup ?: effectiveMuteGroup
        when (command) {
            is VideoCommand.TogglePlay -> {
                val isPlaying = currentState.current?.playing ?: false
                val resolved = if (isPlaying) Pause(targetPlayGroup) else Play(targetPlayGroup)
                broadcast.send(resolved)
            }
            is VideoCommand.ToggleMute -> {
                val isMuted = currentState.current?.muted ?: false
                val resolved = if (isMuted) Unmute(targetMuteGroup) else Mute(targetMuteGroup)
                broadcast.send(resolved)
            }
            is VideoCommand.Play -> broadcast.send(Play(targetPlayGroup))
            is VideoCommand.Pause -> broadcast.send(Pause(targetPlayGroup))
            is VideoCommand.Mute -> broadcast.send(Mute(targetMuteGroup))
            is VideoCommand.Unmute -> broadcast.send(Unmute(targetMuteGroup))
        }
    }

    private fun updateVideos(
        groupStateUpdate: (State.Video) -> State.Video,
        transform: (State.Video.VideoMediaState) -> State.Video.VideoMediaState
    ) {
        videoState.update { state ->
            val idsToUpdate = videoScope ?: state.videos.keys.toList()
            val updated = state.videos.toMutableMap()
            for (id in idsToUpdate) {
                val current = updated[id] ?: continue
                updated[id] = transform(current)
            }
            groupStateUpdate(state.copy(videos = updated))
        }
    }

    private fun handleVideoPlay() = updateVideos(
        groupStateUpdate = { it.copy(playGroupState = it.playGroupState + (effectivePlayGroup to true)) },
        transform = { it.copy(playing = true) }
    )

    private fun handleVideoPause() = updateVideos(
        groupStateUpdate = { it.copy(playGroupState = it.playGroupState + (effectivePlayGroup to false)) },
        transform = { it.copy(playing = false) }
    )

    private fun handleVideoMute() = updateVideos(
        groupStateUpdate = { it.copy(muteGroupState = it.muteGroupState + (effectiveMuteGroup to true)) },
        transform = { it.copy(muted = true) }
    )

    private fun handleVideoUnmute() = updateVideos(
        groupStateUpdate = { it.copy(muteGroupState = it.muteGroupState + (effectiveMuteGroup to false)) },
        transform = { it.copy(muted = false) }
    )
}
