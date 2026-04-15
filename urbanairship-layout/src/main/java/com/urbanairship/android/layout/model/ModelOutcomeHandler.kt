/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.VideoCommand
import com.urbanairship.android.layout.property.StateAction
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.launch

/**
 * Default [OutcomeHandler] wired to a model's [ModelEnvironment] and [LayoutState].
 *
 * Pager navigation dispatches via [LayoutEvent]s so the nearest [PagerModel] can handle them.
 * Video commands are sent via the VideoCommandChannel.
 * State actions are processed via [LayoutState.processStateActions].
 * Airship actions are dispatched via ThomasActionRunner.
 * Dismiss/form events are broadcast as [LayoutEvent]s.
 */
internal class ModelOutcomeHandler(
    private val environment: ModelEnvironment,
    private val layoutState: LayoutState
) : OutcomeHandler {

    override fun runStateActions(actions: List<StateAction>, formValue: Any?) {
        layoutState.processStateActions(actions, formValue)
    }

    override fun runAirshipActions(actions: Map<String, JsonValue>) {
        environment.actionsRunner.run(actions, layoutState.reportingContext())
    }

    override suspend fun dismiss(cancel: Boolean) {
        environment.eventHandler.broadcast(LayoutEvent.Finish(cancel = cancel))
    }

    override suspend fun pagerNext() {
        environment.eventHandler.broadcast(LayoutEvent.PagerNext(PagerNextFallback.NONE))
    }

    override suspend fun pagerPrevious() {
        environment.eventHandler.broadcast(LayoutEvent.PagerPrevious)
    }

    override suspend fun pagerNextOrDismiss() {
        environment.eventHandler.broadcast(LayoutEvent.PagerNext(PagerNextFallback.DISMISS))
    }

    override suspend fun pagerNextOrFirst() {
        environment.eventHandler.broadcast(LayoutEvent.PagerNext(PagerNextFallback.FIRST))
    }

    override suspend fun pagerStart() {
        environment.eventHandler.broadcast(LayoutEvent.PagerStart)
    }

    override suspend fun pagerEnd() {
        environment.eventHandler.broadcast(LayoutEvent.PagerEnd)
    }

    override fun pagerPause() {
        environment.modelScope.launch {
            environment.eventHandler.broadcast(LayoutEvent.PagerPause)
        }
    }

    override fun pagerResume() {
        environment.modelScope.launch {
            environment.eventHandler.broadcast(LayoutEvent.PagerResume)
        }
    }

    override fun pagerTogglePause() {
        environment.modelScope.launch {
            environment.eventHandler.broadcast(LayoutEvent.PagerPauseToggle)
        }
    }

    override fun videoPlay() {
        layoutState.videoControl?.commandChannel?.send(VideoCommand.Play)
    }

    override fun videoPause() {
        layoutState.videoControl?.commandChannel?.send(VideoCommand.Pause)
    }

    override fun videoTogglePlay() {
        layoutState.videoControl?.commandChannel?.send(VideoCommand.TogglePlay)
    }

    override fun videoMute() {
        layoutState.videoControl?.commandChannel?.send(VideoCommand.Mute)
    }

    override fun videoUnmute() {
        layoutState.videoControl?.commandChannel?.send(VideoCommand.Unmute)
    }

    override fun videoToggleMute() {
        layoutState.videoControl?.commandChannel?.send(VideoCommand.ToggleMute)
    }

    override suspend fun formSubmit() {
        environment.eventHandler.broadcast(LayoutEvent.SubmitForm(buttonIdentifier = ""))
    }

    override suspend fun formValidate() {
        environment.eventHandler.broadcast(LayoutEvent.ValidateForm(buttonIdentifier = ""))
    }

    override suspend fun asyncViewReload(identifier: String) {
        environment.eventHandler.broadcast(LayoutEvent.AsyncViewReload(identifier))
    }
}
