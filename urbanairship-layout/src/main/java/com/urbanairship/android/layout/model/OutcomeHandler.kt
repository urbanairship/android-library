/* Copyright Airship and Contributors */
package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.property.Outcome
import com.urbanairship.android.layout.property.OutcomeParams
import com.urbanairship.android.layout.property.StateAction
import com.urbanairship.json.JsonValue

/**
 * Unified handler for dispatching resolved outcomes to the appropriate subsystems.
 * Models create an implementation wired to their environment (pager state, video,
 * layout events, etc.) and pass it to [processOutcomes].
 */
internal interface OutcomeHandler {
    fun runStateActions(actions: List<StateAction>, formValue: Any? = null)
    fun runAirshipActions(actions: Map<String, JsonValue>)
    suspend fun dismiss(cancel: Boolean)
    suspend fun pagerNext()
    suspend fun pagerPrevious()
    suspend fun pagerNextOrDismiss()
    suspend fun pagerNextOrFirst()
    suspend fun pagerStart()
    suspend fun pagerEnd()
    fun pagerPause()
    fun pagerResume()
    fun pagerTogglePause()
    fun videoPlay()
    fun videoPause()
    fun videoTogglePlay()
    fun videoMute()
    fun videoUnmute()
    fun videoToggleMute()
    suspend fun formSubmit()
    suspend fun formValidate()
    suspend fun asyncViewReload(identifier: String)
}

/**
 * Resolves the given [OutcomeParams] into a list of [Outcome]s and dispatches each
 * to the appropriate method on [handler].
 */
internal suspend fun processOutcomes(
    params: OutcomeParams,
    handler: OutcomeHandler,
    formValue: Any? = null
) {
    val resolved = params.resolve()
    for (outcome in resolved) {
        when (outcome) {
            is Outcome.SetStateAction ->
                handler.runStateActions(listOf(outcome.action), formValue)

            is Outcome.AirshipAction ->
                handler.runAirshipActions(outcome.actions)

            is Outcome.Dismiss ->
                handler.dismiss(outcome.cancel)

            is Outcome.PagerStepNavigation -> when (outcome.direction) {
                Outcome.PagerStepNavigation.Direction.NEXT -> when (outcome.boundaryBehavior) {
                    Outcome.PagerStepNavigation.BoundaryBehavior.DISMISS ->
                        handler.pagerNextOrDismiss()
                    Outcome.PagerStepNavigation.BoundaryBehavior.WRAP ->
                        handler.pagerNextOrFirst()
                    Outcome.PagerStepNavigation.BoundaryBehavior.IGNORE ->
                        handler.pagerNext()
                }
                Outcome.PagerStepNavigation.Direction.PREVIOUS ->
                    handler.pagerPrevious()
            }

            is Outcome.PagerJumpNavigation -> when (outcome.page) {
                Outcome.PagerJumpNavigation.Page.START -> handler.pagerStart()
                Outcome.PagerJumpNavigation.Page.END -> handler.pagerEnd()
            }

            is Outcome.PagerPlayback -> when (outcome.command) {
                Outcome.PagerPlayback.Command.PAUSE -> handler.pagerPause()
                Outcome.PagerPlayback.Command.RESUME -> handler.pagerResume()
                Outcome.PagerPlayback.Command.TOGGLE -> handler.pagerTogglePause()
            }

            is Outcome.MediaPlayback -> when (outcome.command) {
                Outcome.MediaPlayback.Command.PLAY -> handler.videoPlay()
                Outcome.MediaPlayback.Command.PAUSE -> handler.videoPause()
                Outcome.MediaPlayback.Command.TOGGLE -> handler.videoTogglePlay()
            }

            is Outcome.MediaAudio -> when (outcome.command) {
                Outcome.MediaAudio.Command.MUTE -> handler.videoMute()
                Outcome.MediaAudio.Command.UNMUTE -> handler.videoUnmute()
                Outcome.MediaAudio.Command.TOGGLE -> handler.videoToggleMute()
            }

            is Outcome.Form -> when (outcome.command) {
                Outcome.Form.Command.SUBMIT -> handler.formSubmit()
                Outcome.Form.Command.VALIDATE -> handler.formValidate()
            }

            is Outcome.AsyncViewReload ->
                handler.asyncViewReload(outcome.identifier)
        }
    }
}
