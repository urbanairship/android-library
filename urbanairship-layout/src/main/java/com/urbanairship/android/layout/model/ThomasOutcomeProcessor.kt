/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.environment.LayoutEvent
import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.environment.VideoCommand
import com.urbanairship.android.layout.property.Outcome
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.launch

/**
 * Outcomes that the processor cannot resolve internally and must be handled
 * by the caller (dismissal, form actions, running Airship actions, async view reload).
 */
internal sealed class HandlerOutcome {
    data class Dismiss(val cancel: Boolean) : HandlerOutcome()
    data class FormAction(val command: Outcome.Form.Command) : HandlerOutcome()
    data class RunActions(val actions: Map<String, JsonValue>) : HandlerOutcome()
    data class AsyncViewReload(val identifier: String) : HandlerOutcome()
}

/**
 * Resolves [Outcome] values into state updates (pager, video, state actions) and external work.
 *
 * Pager navigation, playback, video, and state actions are handled directly by the processor.
 * Dismiss, form, airship-action, and async-view-reload outcomes are forwarded to the
 * caller-supplied [handlerOutcome] callback.
 *
 * Subclasses (e.g. inside [PagerModel]) can override the protected pager methods to manipulate
 * pager state synchronously instead of going through the layout-event bus.
 */
internal open class ThomasOutcomeProcessor(
    protected val environment: ModelEnvironment,
    protected val layoutState: LayoutState
) {
    open suspend fun process(
        outcomes: List<Outcome>?,
        formValue: Any? = null,
        handlerOutcome: suspend (HandlerOutcome) -> Unit = {}
    ) {
        if (outcomes.isNullOrEmpty()) return
        for (outcome in outcomes) {
            resolve(outcome, formValue, handlerOutcome)
        }
    }

    private suspend fun resolve(
        outcome: Outcome,
        formValue: Any?,
        handlerOutcome: suspend (HandlerOutcome) -> Unit
    ) {
        when (outcome) {
            is Outcome.SetStateAction ->
                layoutState.processStateActions(listOf(outcome.action), formValue)

            is Outcome.AirshipAction ->
                handlerOutcome(HandlerOutcome.RunActions(outcome.actions))

            is Outcome.Dismiss ->
                handlerOutcome(HandlerOutcome.Dismiss(outcome.cancel))

            is Outcome.Form ->
                handlerOutcome(HandlerOutcome.FormAction(outcome.command))

            is Outcome.AsyncViewRetry ->
                handlerOutcome(HandlerOutcome.AsyncViewReload(outcome.identifier))

            is Outcome.PagerStepNavigation -> handlePagerStep(outcome)
            is Outcome.PagerJumpNavigation -> handlePagerJump(outcome)
            is Outcome.PagerPlayback -> handlePagerPlayback(outcome)

            is Outcome.MediaPlayback -> handleMediaPlayback(outcome)
            is Outcome.MediaAudio -> handleMediaAudio(outcome)
        }
    }

    // -- Pager: overridable for PagerModel's synchronous handling --

    protected open suspend fun handlePagerStep(outcome: Outcome.PagerStepNavigation) {
        when (outcome.direction) {
            Outcome.PagerStepNavigation.Direction.NEXT -> {
                when (outcome.boundaryBehavior) {
                    Outcome.PagerStepNavigation.BoundaryBehavior.DISMISS ->
                        broadcast(LayoutEvent.Pager.Next(PagerNextFallback.DISMISS))
                    Outcome.PagerStepNavigation.BoundaryBehavior.WRAP ->
                        broadcast(LayoutEvent.Pager.Next(PagerNextFallback.FIRST))
                    Outcome.PagerStepNavigation.BoundaryBehavior.IGNORE ->
                        broadcast(LayoutEvent.Pager.Next(PagerNextFallback.NONE))
                }
            }
            Outcome.PagerStepNavigation.Direction.PREVIOUS ->
                broadcast(LayoutEvent.Pager.Previous)
        }
    }

    protected open suspend fun handlePagerJump(outcome: Outcome.PagerJumpNavigation) {
        when (outcome.page) {
            Outcome.PagerJumpNavigation.Page.START ->
                broadcast(LayoutEvent.Pager.Start)
            Outcome.PagerJumpNavigation.Page.END ->
                broadcast(LayoutEvent.Pager.End)
        }
    }

    protected open fun handlePagerPlayback(outcome: Outcome.PagerPlayback) {
        environment.modelScope.launch {
            when (outcome.command) {
                Outcome.PagerPlayback.Command.PAUSE ->
                    broadcast(LayoutEvent.Pager.Pause)
                Outcome.PagerPlayback.Command.RESUME ->
                    broadcast(LayoutEvent.Pager.Resume)
                Outcome.PagerPlayback.Command.TOGGLE ->
                    broadcast(LayoutEvent.Pager.PauseToggle)
            }
        }
    }

    // -- Media: always handled directly via VideoCommandChannel --

    private fun handleMediaPlayback(outcome: Outcome.MediaPlayback) {
        when (outcome.command) {
            Outcome.MediaPlayback.Command.PLAY ->
                sendVideoCommand(VideoCommand.Play)
            Outcome.MediaPlayback.Command.PAUSE ->
                sendVideoCommand(VideoCommand.Pause)
            Outcome.MediaPlayback.Command.TOGGLE ->
                sendVideoCommand(VideoCommand.TogglePlay)
        }
    }

    private fun handleMediaAudio(outcome: Outcome.MediaAudio) {
        when (outcome.command) {
            Outcome.MediaAudio.Command.MUTE ->
                sendVideoCommand(VideoCommand.Mute)
            Outcome.MediaAudio.Command.UNMUTE ->
                sendVideoCommand(VideoCommand.Unmute)
            Outcome.MediaAudio.Command.TOGGLE ->
                sendVideoCommand(VideoCommand.ToggleMute)
        }
    }

    private suspend fun broadcast(event: LayoutEvent) {
        environment.eventHandler.broadcast(event)
    }

    private fun sendVideoCommand(command: VideoCommand) {
        layoutState.videoControl?.commandChannel?.send(command)
    }
}
