package com.urbanairship.android.layout.environment

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

internal sealed interface VideoCommand {
    data object Play : VideoCommand
    data object Pause : VideoCommand
    data object TogglePlay : VideoCommand
    data object Mute : VideoCommand
    data object Unmute : VideoCommand
    data object ToggleMute : VideoCommand
}

internal class VideoCommandChannel {
    private val _commands = MutableSharedFlow<VideoCommand>(extraBufferCapacity = 1)
    val commands: Flow<VideoCommand> = _commands.asSharedFlow()

    fun send(command: VideoCommand) { _commands.tryEmit(command) }
}

internal sealed interface ResolvedVideoCommand {
    val targetGroup: String?

    data class Play(override val targetGroup: String?) : ResolvedVideoCommand
    data class Pause(override val targetGroup: String?) : ResolvedVideoCommand
    data class Mute(override val targetGroup: String?) : ResolvedVideoCommand
    data class Unmute(override val targetGroup: String?) : ResolvedVideoCommand
}

internal class VideoGroupBroadcastChannel {
    private val _commands = MutableSharedFlow<ResolvedVideoCommand>(extraBufferCapacity = 1)
    val commands: Flow<ResolvedVideoCommand> = _commands.asSharedFlow()

    fun send(command: ResolvedVideoCommand) { _commands.tryEmit(command) }
}

/**
 * Aggregates all video-controller-related state for a [LayoutState].
 *
 * The "top-level" fields ([commandChannel], [broadcastChannel]) are always present when a
 * video controller ancestor exists. "Parent" fields are only present when the controller is
 * nested inside another video controller. [playGroup] and [muteGroup] are the resolved group
 * identifiers for this controller (either the explicit group name from the layout JSON, or a
 * generated UUID for anonymous groups).
 */
internal class VideoControlState(
    val commandChannel: VideoCommandChannel,
    val broadcastChannel: VideoGroupBroadcastChannel,
    val parentCommandChannel: VideoCommandChannel?,
    val parentBroadcastChannel: VideoGroupBroadcastChannel?,
    val parentVideoState: SharedState<State.Video>?,
    val playGroup: String,
    val muteGroup: String,
)
