/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.property.ButtonClickBehaviorType
import com.urbanairship.android.layout.property.Outcome
import com.urbanairship.android.layout.property.OutcomeParams
import com.urbanairship.android.layout.property.StateAction
import com.urbanairship.json.JsonValue
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class ProcessOutcomesTest {

    // =========================================================================
    // Mock handler
    // =========================================================================

    private class MockOutcomeHandler : OutcomeHandler {
        data class Call(val name: String, val args: Map<String, Any?> = emptyMap())

        val calls = mutableListOf<Call>()

        override fun runStateActions(actions: List<StateAction>, formValue: Any?) {
            calls.add(Call("runStateActions", mapOf("actions" to actions, "formValue" to formValue)))
        }
        override fun runAirshipActions(actions: Map<String, JsonValue>) {
            calls.add(Call("runAirshipActions", mapOf("actions" to actions)))
        }
        override suspend fun dismiss(cancel: Boolean) { calls.add(Call("dismiss", mapOf("cancel" to cancel))) }
        override suspend fun pagerNext() { calls.add(Call("pagerNext")) }
        override suspend fun pagerPrevious() { calls.add(Call("pagerPrevious")) }
        override suspend fun pagerNextOrDismiss() { calls.add(Call("pagerNextOrDismiss")) }
        override suspend fun pagerNextOrFirst() { calls.add(Call("pagerNextOrFirst")) }
        override suspend fun pagerStart() { calls.add(Call("pagerStart")) }
        override suspend fun pagerEnd() { calls.add(Call("pagerEnd")) }
        override fun pagerPause() { calls.add(Call("pagerPause")) }
        override fun pagerResume() { calls.add(Call("pagerResume")) }
        override fun pagerTogglePause() { calls.add(Call("pagerTogglePause")) }
        override fun videoPlay() { calls.add(Call("videoPlay")) }
        override fun videoPause() { calls.add(Call("videoPause")) }
        override fun videoTogglePlay() { calls.add(Call("videoTogglePlay")) }
        override fun videoMute() { calls.add(Call("videoMute")) }
        override fun videoUnmute() { calls.add(Call("videoUnmute")) }
        override fun videoToggleMute() { calls.add(Call("videoToggleMute")) }
        override suspend fun formSubmit() { calls.add(Call("formSubmit")) }
        override suspend fun formValidate() { calls.add(Call("formValidate")) }
        override suspend fun asyncViewReload(identifier: String) {
            calls.add(Call("asyncViewReload", mapOf("identifier" to identifier)))
        }

        val callNames: List<String> get() = calls.map { it.name }
    }

    // =========================================================================
    // Direct outcome dispatch
    // =========================================================================

    @Test
    fun testDismiss() = runTest {
        val handler = run(Outcome.Dismiss(identifier = "d-1"))
        assertEquals(listOf("dismiss"), handler.callNames)
        assertEquals(false, handler.calls[0].args["cancel"])
    }

    @Test
    fun testDismissCancel() = runTest {
        val handler = run(Outcome.Dismiss(identifier = "d-1", cancel = true))
        assertEquals(listOf("dismiss"), handler.callNames)
        assertEquals(true, handler.calls[0].args["cancel"])
    }

    @Test
    fun testAirshipActions() = runTest {
        val actions = mapOf("add_tags" to JsonValue.wrap("vip"))
        val handler = run(Outcome.AirshipAction(identifier = "aa-1", actions = actions))
        assertEquals(listOf("runAirshipActions"), handler.callNames)
        @Suppress("UNCHECKED_CAST")
        assertEquals(actions, handler.calls[0].args["actions"] as Map<String, JsonValue>)
    }

    @Test
    fun testStateActions() = runTest {
        val action = StateAction.SetState(key = "k", value = JsonValue.wrap("v"))
        val handler = run(Outcome.SetStateAction(identifier = "sa-1", action = action))
        assertEquals(listOf("runStateActions"), handler.callNames)
        @Suppress("UNCHECKED_CAST")
        assertEquals(listOf(action), handler.calls[0].args["actions"] as List<StateAction>)
    }

    @Test
    fun testStateActionsFormValuePassthrough() = runTest {
        val handler = MockOutcomeHandler()
        processOutcomes(
            OutcomeParams(stateActions = listOf(StateAction.SetFormValue(key = "email"))),
            handler,
            formValue = "test@example.com"
        )
        assertEquals("test@example.com", handler.calls[0].args["formValue"])
    }

    // =========================================================================
    // Pager step navigation
    // =========================================================================

    @Test
    fun testPagerNext() = runTest {
        val handler = run(Outcome.PagerStepNavigation(
            identifier = "p-1", direction = Outcome.PagerStepNavigation.Direction.NEXT
        ))
        assertEquals(listOf("pagerNext"), handler.callNames)
    }

    @Test
    fun testPagerNextBoundaryDismiss() = runTest {
        val handler = run(Outcome.PagerStepNavigation(
            identifier = "p-1",
            direction = Outcome.PagerStepNavigation.Direction.NEXT,
            boundaryBehavior = Outcome.PagerStepNavigation.BoundaryBehavior.DISMISS
        ))
        assertEquals(listOf("pagerNextOrDismiss"), handler.callNames)
    }

    @Test
    fun testPagerNextBoundaryWrap() = runTest {
        val handler = run(Outcome.PagerStepNavigation(
            identifier = "p-1",
            direction = Outcome.PagerStepNavigation.Direction.NEXT,
            boundaryBehavior = Outcome.PagerStepNavigation.BoundaryBehavior.WRAP
        ))
        assertEquals(listOf("pagerNextOrFirst"), handler.callNames)
    }

    @Test
    fun testPagerPrevious() = runTest {
        val handler = run(Outcome.PagerStepNavigation(
            identifier = "p-1", direction = Outcome.PagerStepNavigation.Direction.PREVIOUS
        ))
        assertEquals(listOf("pagerPrevious"), handler.callNames)
    }

    // =========================================================================
    // Pager jump navigation
    // =========================================================================

    @Test
    fun testPagerStart() = runTest {
        val handler = run(Outcome.PagerJumpNavigation(identifier = "pj-1", page = Outcome.PagerJumpNavigation.Page.START))
        assertEquals(listOf("pagerStart"), handler.callNames)
    }

    @Test
    fun testPagerEnd() = runTest {
        val handler = run(Outcome.PagerJumpNavigation(identifier = "pj-1", page = Outcome.PagerJumpNavigation.Page.END))
        assertEquals(listOf("pagerEnd"), handler.callNames)
    }

    // =========================================================================
    // Pager playback
    // =========================================================================

    @Test
    fun testPagerPlaybackPause() = runTest {
        val handler = run(Outcome.PagerPlayback(identifier = "pp-1", command = Outcome.PagerPlayback.Command.PAUSE))
        assertEquals(listOf("pagerPause"), handler.callNames)
    }

    @Test
    fun testPagerPlaybackResume() = runTest {
        val handler = run(Outcome.PagerPlayback(identifier = "pp-1", command = Outcome.PagerPlayback.Command.RESUME))
        assertEquals(listOf("pagerResume"), handler.callNames)
    }

    @Test
    fun testPagerPlaybackToggle() = runTest {
        val handler = run(Outcome.PagerPlayback(identifier = "pp-1", command = Outcome.PagerPlayback.Command.TOGGLE))
        assertEquals(listOf("pagerTogglePause"), handler.callNames)
    }

    // =========================================================================
    // Media playback
    // =========================================================================

    @Test
    fun testVideoPlay() = runTest {
        val handler = run(Outcome.MediaPlayback(identifier = "mp-1", command = Outcome.MediaPlayback.Command.PLAY))
        assertEquals(listOf("videoPlay"), handler.callNames)
    }

    @Test
    fun testVideoPause() = runTest {
        val handler = run(Outcome.MediaPlayback(identifier = "mp-1", command = Outcome.MediaPlayback.Command.PAUSE))
        assertEquals(listOf("videoPause"), handler.callNames)
    }

    @Test
    fun testVideoToggle() = runTest {
        val handler = run(Outcome.MediaPlayback(identifier = "mp-1", command = Outcome.MediaPlayback.Command.TOGGLE))
        assertEquals(listOf("videoTogglePlay"), handler.callNames)
    }

    // =========================================================================
    // Media audio
    // =========================================================================

    @Test
    fun testVideoMute() = runTest {
        val handler = run(Outcome.MediaAudio(identifier = "ma-1", command = Outcome.MediaAudio.Command.MUTE))
        assertEquals(listOf("videoMute"), handler.callNames)
    }

    @Test
    fun testVideoUnmute() = runTest {
        val handler = run(Outcome.MediaAudio(identifier = "ma-1", command = Outcome.MediaAudio.Command.UNMUTE))
        assertEquals(listOf("videoUnmute"), handler.callNames)
    }

    @Test
    fun testVideoToggleMute() = runTest {
        val handler = run(Outcome.MediaAudio(identifier = "ma-1", command = Outcome.MediaAudio.Command.TOGGLE))
        assertEquals(listOf("videoToggleMute"), handler.callNames)
    }

    // =========================================================================
    // Form
    // =========================================================================

    @Test
    fun testFormSubmit() = runTest {
        val handler = run(Outcome.Form(identifier = "f-1", command = Outcome.Form.Command.SUBMIT))
        assertEquals(listOf("formSubmit"), handler.callNames)
    }

    @Test
    fun testFormValidate() = runTest {
        val handler = run(Outcome.Form(identifier = "f-1", command = Outcome.Form.Command.VALIDATE))
        assertEquals(listOf("formValidate"), handler.callNames)
    }

    @Test
    fun testAsyncViewReload() = runTest {
        val handler = run(Outcome.AsyncViewReload(identifier = "avr-1"))
        assertEquals(listOf("asyncViewReload"), handler.callNames)
        assertEquals("avr-1", handler.calls[0].args["identifier"])
    }

    // =========================================================================
    // Multiple outcomes – ordering
    // =========================================================================

    @Test
    fun testMultipleOutcomesProcessedInOrder() = runTest {
        val handler = MockOutcomeHandler()
        processOutcomes(OutcomeParams(outcomes = listOf(
            Outcome.SetStateAction(identifier = "sa-1", action = StateAction.SetState(key = "k", value = JsonValue.wrap("v"))),
            Outcome.PagerStepNavigation(identifier = "psn-1", direction = Outcome.PagerStepNavigation.Direction.NEXT),
            Outcome.AirshipAction(identifier = "aa-1", actions = mapOf("x" to JsonValue.wrap("y"))),
            Outcome.Dismiss(identifier = "d-1")
        )), handler)
        assertEquals(listOf("runStateActions", "pagerNext", "runAirshipActions", "dismiss"), handler.callNames)
    }

    @Test
    fun testEmptyOutcomesNoOp() = runTest {
        val handler = MockOutcomeHandler()
        processOutcomes(OutcomeParams(outcomes = emptyList()), handler)
        assertTrue(handler.calls.isEmpty())
    }

    @Test
    fun testEmptyParamsNoOp() = runTest {
        val handler = MockOutcomeHandler()
        processOutcomes(OutcomeParams.EMPTY, handler)
        assertTrue(handler.calls.isEmpty())
    }

    // =========================================================================
    // Legacy behavior → outcome → dispatch pipeline (end-to-end)
    // =========================================================================

    @Test
    fun testLegacyPagerNextDispatch() = runTest {
        val handler = MockOutcomeHandler()
        processOutcomes(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.PAGER_NEXT)), handler)
        assertEquals(listOf("pagerNext"), handler.callNames)
    }

    @Test
    fun testLegacyPagerPreviousDispatch() = runTest {
        val handler = MockOutcomeHandler()
        processOutcomes(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.PAGER_PREVIOUS)), handler)
        assertEquals(listOf("pagerPrevious"), handler.callNames)
    }

    @Test
    fun testLegacyPagerNextOrDismissDispatch() = runTest {
        val handler = MockOutcomeHandler()
        processOutcomes(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.PAGER_NEXT_OR_DISMISS)), handler)
        assertEquals(listOf("pagerNextOrDismiss"), handler.callNames)
    }

    @Test
    fun testLegacyPagerNextOrFirstDispatch() = runTest {
        val handler = MockOutcomeHandler()
        processOutcomes(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.PAGER_NEXT_OR_FIRST)), handler)
        assertEquals(listOf("pagerNextOrFirst"), handler.callNames)
    }

    @Test
    fun testLegacyDismissDispatch() = runTest {
        val handler = MockOutcomeHandler()
        processOutcomes(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.DISMISS)), handler)
        assertEquals(listOf("dismiss"), handler.callNames)
        assertEquals(false, handler.calls[0].args["cancel"])
    }

    @Test
    fun testLegacyCancelDispatch() = runTest {
        val handler = MockOutcomeHandler()
        processOutcomes(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.CANCEL)), handler)
        assertEquals(listOf("dismiss"), handler.callNames)
        assertEquals(true, handler.calls[0].args["cancel"])
    }

    @Test
    fun testLegacyPagerPauseDispatch() = runTest {
        val handler = MockOutcomeHandler()
        processOutcomes(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.PAGER_PAUSE)), handler)
        assertEquals(listOf("pagerPause"), handler.callNames)
    }

    @Test
    fun testLegacyPagerResumeDispatch() = runTest {
        val handler = MockOutcomeHandler()
        processOutcomes(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.PAGER_RESUME)), handler)
        assertEquals(listOf("pagerResume"), handler.callNames)
    }

    @Test
    fun testLegacyPagerPauseToggleDispatch() = runTest {
        val handler = MockOutcomeHandler()
        processOutcomes(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.PAGER_PAUSE_TOGGLE)), handler)
        assertEquals(listOf("pagerTogglePause"), handler.callNames)
    }

    @Test
    fun testLegacyVideoPlayDispatch() = runTest {
        val handler = MockOutcomeHandler()
        processOutcomes(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.VIDEO_PLAY)), handler)
        assertEquals(listOf("videoPlay"), handler.callNames)
    }

    @Test
    fun testLegacyVideoPauseDispatch() = runTest {
        val handler = MockOutcomeHandler()
        processOutcomes(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.VIDEO_PAUSE)), handler)
        assertEquals(listOf("videoPause"), handler.callNames)
    }

    @Test
    fun testLegacyVideoTogglePlayDispatch() = runTest {
        val handler = MockOutcomeHandler()
        processOutcomes(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.VIDEO_TOGGLE_PLAY)), handler)
        assertEquals(listOf("videoTogglePlay"), handler.callNames)
    }

    @Test
    fun testLegacyVideoMuteDispatch() = runTest {
        val handler = MockOutcomeHandler()
        processOutcomes(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.VIDEO_MUTE)), handler)
        assertEquals(listOf("videoMute"), handler.callNames)
    }

    @Test
    fun testLegacyVideoUnmuteDispatch() = runTest {
        val handler = MockOutcomeHandler()
        processOutcomes(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.VIDEO_UNMUTE)), handler)
        assertEquals(listOf("videoUnmute"), handler.callNames)
    }

    @Test
    fun testLegacyVideoToggleMuteDispatch() = runTest {
        val handler = MockOutcomeHandler()
        processOutcomes(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.VIDEO_TOGGLE_MUTE)), handler)
        assertEquals(listOf("videoToggleMute"), handler.callNames)
    }

    @Test
    fun testLegacyFormSubmitDispatch() = runTest {
        val handler = MockOutcomeHandler()
        processOutcomes(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.FORM_SUBMIT)), handler)
        assertEquals(listOf("formSubmit"), handler.callNames)
    }

    @Test
    fun testLegacyFormValidateDispatch() = runTest {
        val handler = MockOutcomeHandler()
        processOutcomes(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.FORM_VALIDATE)), handler)
        assertEquals(listOf("formValidate"), handler.callNames)
    }

    @Test
    fun testLegacyCombinedDispatch() = runTest {
        val handler = MockOutcomeHandler()
        processOutcomes(OutcomeParams(
            stateActions = listOf(StateAction.SetState(key = "k", value = JsonValue.wrap("v"))),
            behaviors = listOf(ButtonClickBehaviorType.PAGER_NEXT, ButtonClickBehaviorType.DISMISS),
            actions = mapOf("deep_link" to JsonValue.wrap("app://x"))
        ), handler)
        assertEquals(listOf("runStateActions", "pagerNext", "dismiss", "runAirshipActions"), handler.callNames)
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private suspend fun run(vararg outcomes: Outcome): MockOutcomeHandler {
        val handler = MockOutcomeHandler()
        processOutcomes(OutcomeParams(outcomes = outcomes.toList()), handler)
        return handler
    }
}
