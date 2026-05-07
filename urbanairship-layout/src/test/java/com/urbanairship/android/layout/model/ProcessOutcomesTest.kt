/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.model

import com.urbanairship.android.layout.environment.LayoutState
import com.urbanairship.android.layout.environment.ModelEnvironment
import com.urbanairship.android.layout.property.ButtonClickBehaviorType
import com.urbanairship.android.layout.property.Outcome
import com.urbanairship.android.layout.property.OutcomeResolver
import com.urbanairship.android.layout.property.StateAction
import com.urbanairship.json.JsonValue
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

public class ProcessOutcomesTest {

    private val testScope = TestScope()
    private val mockLayoutState: LayoutState = mockk(relaxed = true)
    private val mockEnv: ModelEnvironment = mockk(relaxed = true) {
        every { layoutState } returns mockLayoutState
        every { modelScope } returns testScope
    }

    data class Call(val name: String, val args: Map<String, Any?> = emptyMap())

    /**
     * Test processor that records pager/video calls directly (since those are handled
     * internally by the processor) and lets handle outcomes be tracked externally.
     */
    private inner class TestProcessor : ThomasOutcomeProcessor(mockEnv, mockLayoutState) {
        val calls = mutableListOf<Call>()

        override suspend fun handlePagerStep(outcome: Outcome.PagerStepNavigation) {
            val name = when (outcome.direction) {
                Outcome.PagerStepNavigation.Direction.NEXT -> when (outcome.boundaryBehavior) {
                    Outcome.PagerStepNavigation.BoundaryBehavior.DISMISS -> "pagerNextOrDismiss"
                    Outcome.PagerStepNavigation.BoundaryBehavior.WRAP -> "pagerNextOrFirst"
                    Outcome.PagerStepNavigation.BoundaryBehavior.IGNORE -> "pagerNext"
                }
                Outcome.PagerStepNavigation.Direction.PREVIOUS -> "pagerPrevious"
            }
            calls.add(Call(name))
        }

        override suspend fun handlePagerJump(outcome: Outcome.PagerJumpNavigation) {
            val name = when (outcome.page) {
                Outcome.PagerJumpNavigation.Page.START -> "pagerStart"
                Outcome.PagerJumpNavigation.Page.END -> "pagerEnd"
            }
            calls.add(Call(name))
        }

        override fun handlePagerPlayback(outcome: Outcome.PagerPlayback) {
            val name = when (outcome.command) {
                Outcome.PagerPlayback.Command.PAUSE -> "pagerPause"
                Outcome.PagerPlayback.Command.RESUME -> "pagerResume"
                Outcome.PagerPlayback.Command.TOGGLE -> "pagerTogglePause"
            }
            calls.add(Call(name))
        }

        val callNames: List<String> get() = calls.map { it.name }
    }

    private fun makehandleOutcome(calls: MutableList<Call>): suspend (HandlerOutcome) -> Unit = { outcome ->
        when (outcome) {
            is HandlerOutcome.Dismiss -> calls.add(Call("dismiss", mapOf("cancel" to outcome.cancel)))
            is HandlerOutcome.RunActions -> calls.add(Call("runAirshipActions", mapOf("actions" to outcome.actions)))
            is HandlerOutcome.FormAction -> calls.add(Call(
                when (outcome.command) {
                    Outcome.Form.Command.SUBMIT -> "formSubmit"
                    Outcome.Form.Command.VALIDATE -> "formValidate"
                }
            ))
            is HandlerOutcome.AsyncViewReload -> calls.add(Call("asyncViewReload", mapOf("identifier" to outcome.identifier)))
        }
    }

    // =========================================================================
    // Handling outcomes
    // =========================================================================

    @Test
    fun testDismiss() = runTest {
        val (processor, handleOutcome, calls) = makeTestSetup()
        processor.process(listOf(Outcome.Dismiss(identifier = "d-1")), handlerOutcome = handleOutcome)
        assertEquals(listOf("dismiss"), calls.map { it.name })
        assertEquals(false, calls[0].args["cancel"])
    }

    @Test
    fun testDismissCancel() = runTest {
        val (processor, handleOutcome, calls) = makeTestSetup()
        processor.process(listOf(Outcome.Dismiss(identifier = "d-1", cancel = true)), handlerOutcome = handleOutcome)
        assertEquals(listOf("dismiss"), calls.map { it.name })
        assertEquals(true, calls[0].args["cancel"])
    }

    @Test
    fun testAirshipActions() = runTest {
        val actions = mapOf("add_tags" to JsonValue.wrap("vip"))
        val (processor, handleOutcome, calls) = makeTestSetup()
        processor.process(listOf(Outcome.AirshipAction(identifier = "aa-1", actions = actions)), handlerOutcome = handleOutcome)
        assertEquals(listOf("runAirshipActions"), calls.map { it.name })
        @Suppress("UNCHECKED_CAST")
        assertEquals(actions, calls[0].args["actions"] as Map<String, JsonValue>)
    }

    @Test
    fun testFormSubmit() = runTest {
        val (processor, handleOutcome, calls) = makeTestSetup()
        processor.process(listOf(Outcome.Form(identifier = "f-1", command = Outcome.Form.Command.SUBMIT)), handlerOutcome = handleOutcome)
        assertEquals(listOf("formSubmit"), calls.map { it.name })
    }

    @Test
    fun testFormValidate() = runTest {
        val (processor, handleOutcome, calls) = makeTestSetup()
        processor.process(listOf(Outcome.Form(identifier = "f-1", command = Outcome.Form.Command.VALIDATE)), handlerOutcome = handleOutcome)
        assertEquals(listOf("formValidate"), calls.map { it.name })
    }

    @Test
    fun testAsyncViewReload() = runTest {
        val (processor, handleOutcome, calls) = makeTestSetup()
        processor.process(listOf(Outcome.AsyncViewRetry(identifier = "avr-1")), handlerOutcome = handleOutcome)
        assertEquals(listOf("asyncViewReload"), calls.map { it.name })
        assertEquals("avr-1", calls[0].args["identifier"])
    }

    // =========================================================================
    // State actions (handled directly by processor via LayoutState)
    // =========================================================================

    @Test
    fun testStateActions() = runTest {
        val action = StateAction.SetState(key = "k", value = JsonValue.wrap("v"))
        val (processor, handleOutcome, _) = makeTestSetup()
        processor.process(listOf(Outcome.SetStateAction(identifier = "sa-1", action = action)), handlerOutcome = handleOutcome)
        verify { mockLayoutState.processStateActions(listOf(action), null) }
    }

    @Test
    fun testStateActionsFormValuePassthrough() = runTest {
        val action = StateAction.SetFormValue(key = "email")
        val (processor, handleOutcome, _) = makeTestSetup()
        processor.process(
            OutcomeResolver.resolve(stateActions = listOf(action)),
            formValue = "test@example.com",
            handlerOutcome = handleOutcome
        )
        verify { mockLayoutState.processStateActions(listOf(action), "test@example.com") }
    }

    // =========================================================================
    // Pager step navigation (handled directly by processor)
    // =========================================================================

    @Test
    fun testPagerNext() = runTest {
        val processor = TestProcessor()
        processor.process(listOf(Outcome.PagerStepNavigation(
            identifier = "p-1", direction = Outcome.PagerStepNavigation.Direction.NEXT
        )))
        assertEquals(listOf("pagerNext"), processor.callNames)
    }

    @Test
    fun testPagerNextBoundaryDismiss() = runTest {
        val processor = TestProcessor()
        processor.process(listOf(Outcome.PagerStepNavigation(
            identifier = "p-1",
            direction = Outcome.PagerStepNavigation.Direction.NEXT,
            boundaryBehavior = Outcome.PagerStepNavigation.BoundaryBehavior.DISMISS
        )))
        assertEquals(listOf("pagerNextOrDismiss"), processor.callNames)
    }

    @Test
    fun testPagerNextBoundaryWrap() = runTest {
        val processor = TestProcessor()
        processor.process(listOf(Outcome.PagerStepNavigation(
            identifier = "p-1",
            direction = Outcome.PagerStepNavigation.Direction.NEXT,
            boundaryBehavior = Outcome.PagerStepNavigation.BoundaryBehavior.WRAP
        )))
        assertEquals(listOf("pagerNextOrFirst"), processor.callNames)
    }

    @Test
    fun testPagerPrevious() = runTest {
        val processor = TestProcessor()
        processor.process(listOf(Outcome.PagerStepNavigation(
            identifier = "p-1", direction = Outcome.PagerStepNavigation.Direction.PREVIOUS
        )))
        assertEquals(listOf("pagerPrevious"), processor.callNames)
    }

    @Test
    fun testPagerStart() = runTest {
        val processor = TestProcessor()
        processor.process(listOf(Outcome.PagerJumpNavigation(identifier = "pj-1", page = Outcome.PagerJumpNavigation.Page.START)))
        assertEquals(listOf("pagerStart"), processor.callNames)
    }

    @Test
    fun testPagerEnd() = runTest {
        val processor = TestProcessor()
        processor.process(listOf(Outcome.PagerJumpNavigation(identifier = "pj-1", page = Outcome.PagerJumpNavigation.Page.END)))
        assertEquals(listOf("pagerEnd"), processor.callNames)
    }

    @Test
    fun testPagerPlaybackPause() = runTest {
        val processor = TestProcessor()
        processor.process(listOf(Outcome.PagerPlayback(identifier = "pp-1", command = Outcome.PagerPlayback.Command.PAUSE)))
        assertEquals(listOf("pagerPause"), processor.callNames)
    }

    @Test
    fun testPagerPlaybackResume() = runTest {
        val processor = TestProcessor()
        processor.process(listOf(Outcome.PagerPlayback(identifier = "pp-1", command = Outcome.PagerPlayback.Command.RESUME)))
        assertEquals(listOf("pagerResume"), processor.callNames)
    }

    @Test
    fun testPagerPlaybackToggle() = runTest {
        val processor = TestProcessor()
        processor.process(listOf(Outcome.PagerPlayback(identifier = "pp-1", command = Outcome.PagerPlayback.Command.TOGGLE)))
        assertEquals(listOf("pagerTogglePause"), processor.callNames)
    }

    // =========================================================================
    // Multiple outcomes – ordering
    // =========================================================================

    @Test
    fun testMultipleOutcomesProcessedInOrder() = runTest {
        val processor = TestProcessor()
        val handleCalls = mutableListOf<Call>()
        val handleOutcome = makehandleOutcome(handleCalls)

        processor.process(listOf(
            Outcome.PagerStepNavigation(identifier = "psn-1", direction = Outcome.PagerStepNavigation.Direction.NEXT),
            Outcome.AirshipAction(identifier = "aa-1", actions = mapOf("x" to JsonValue.wrap("y"))),
            Outcome.Dismiss(identifier = "d-1")
        ), handlerOutcome = handleOutcome)

        assertEquals(listOf("pagerNext"), processor.callNames)
        assertEquals(listOf("runAirshipActions", "dismiss"), handleCalls.map { it.name })
    }

    @Test
    fun testEmptyOutcomesNoOp() = runTest {
        val (processor, handleOutcome, calls) = makeTestSetup()
        processor.process(emptyList(), handlerOutcome = handleOutcome)
        assertTrue(calls.isEmpty())
    }

    @Test
    fun testNullOutcomesNoOp() = runTest {
        val (processor, handleOutcome, calls) = makeTestSetup()
        processor.process(null, handlerOutcome = handleOutcome)
        assertTrue(calls.isEmpty())
    }

    // =========================================================================
    // Legacy behavior → outcome → dispatch pipeline (end-to-end)
    //
    // These exercise the OutcomeResolver path: legacy ButtonClickBehaviorType
    // values are resolved into Outcomes, then handed to the processor. This
    // verifies the same end-to-end behavior the production code follows when
    // a layout uses the legacy `button_click` JSON field instead of `outcomes`.
    // =========================================================================

    @Test
    fun testLegacyPagerNextDispatch() = runTest {
        val processor = TestProcessor()
        processor.process(resolveBehavior(ButtonClickBehaviorType.PAGER_NEXT))
        assertEquals(listOf("pagerNext"), processor.callNames)
    }

    @Test
    fun testLegacyPagerPreviousDispatch() = runTest {
        val processor = TestProcessor()
        processor.process(resolveBehavior(ButtonClickBehaviorType.PAGER_PREVIOUS))
        assertEquals(listOf("pagerPrevious"), processor.callNames)
    }

    @Test
    fun testLegacyPagerNextOrDismissDispatch() = runTest {
        val processor = TestProcessor()
        processor.process(resolveBehavior(ButtonClickBehaviorType.PAGER_NEXT_OR_DISMISS))
        assertEquals(listOf("pagerNextOrDismiss"), processor.callNames)
    }

    @Test
    fun testLegacyPagerNextOrFirstDispatch() = runTest {
        val processor = TestProcessor()
        processor.process(resolveBehavior(ButtonClickBehaviorType.PAGER_NEXT_OR_FIRST))
        assertEquals(listOf("pagerNextOrFirst"), processor.callNames)
    }

    @Test
    fun testLegacyDismissDispatch() = runTest {
        val (processor, handleOutcome, calls) = makeTestSetup()
        processor.process(resolveBehavior(ButtonClickBehaviorType.DISMISS), handlerOutcome = handleOutcome)
        assertEquals(listOf("dismiss"), calls.map { it.name })
        assertEquals(false, calls[0].args["cancel"])
    }

    @Test
    fun testLegacyCancelDispatch() = runTest {
        val (processor, handleOutcome, calls) = makeTestSetup()
        processor.process(resolveBehavior(ButtonClickBehaviorType.CANCEL), handlerOutcome = handleOutcome)
        assertEquals(listOf("dismiss"), calls.map { it.name })
        assertEquals(true, calls[0].args["cancel"])
    }

    @Test
    fun testLegacyPagerPauseDispatch() = runTest {
        val processor = TestProcessor()
        processor.process(resolveBehavior(ButtonClickBehaviorType.PAGER_PAUSE))
        assertEquals(listOf("pagerPause"), processor.callNames)
    }

    @Test
    fun testLegacyPagerResumeDispatch() = runTest {
        val processor = TestProcessor()
        processor.process(resolveBehavior(ButtonClickBehaviorType.PAGER_RESUME))
        assertEquals(listOf("pagerResume"), processor.callNames)
    }

    @Test
    fun testLegacyPagerPauseToggleDispatch() = runTest {
        val processor = TestProcessor()
        processor.process(resolveBehavior(ButtonClickBehaviorType.PAGER_PAUSE_TOGGLE))
        assertEquals(listOf("pagerTogglePause"), processor.callNames)
    }

    @Test
    fun testLegacyFormSubmitDispatch() = runTest {
        val (processor, handleOutcome, calls) = makeTestSetup()
        processor.process(resolveBehavior(ButtonClickBehaviorType.FORM_SUBMIT), handlerOutcome = handleOutcome)
        assertEquals(listOf("formSubmit"), calls.map { it.name })
    }

    @Test
    fun testLegacyFormValidateDispatch() = runTest {
        val (processor, handleOutcome, calls) = makeTestSetup()
        processor.process(resolveBehavior(ButtonClickBehaviorType.FORM_VALIDATE), handlerOutcome = handleOutcome)
        assertEquals(listOf("formValidate"), calls.map { it.name })
    }

    @Test
    fun testLegacyCombinedDispatch() = runTest {
        val processor = TestProcessor()
        val handleCalls = mutableListOf<Call>()
        val handleOutcome = makehandleOutcome(handleCalls)

        val outcomes = OutcomeResolver.resolve(
            stateActions = listOf(StateAction.SetState(key = "k", value = JsonValue.wrap("v"))),
            behaviors = listOf(ButtonClickBehaviorType.PAGER_NEXT, ButtonClickBehaviorType.DISMISS),
            actions = mapOf("deep_link" to JsonValue.wrap("app://x"))
        )
        processor.process(outcomes, handlerOutcome = handleOutcome)

        assertEquals(listOf("pagerNext"), processor.callNames)
        assertEquals(listOf("dismiss", "runAirshipActions"), handleCalls.map { it.name })
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun makeTestSetup(): Triple<ThomasOutcomeProcessor, suspend (HandlerOutcome) -> Unit, MutableList<Call>> {
        val processor = ThomasOutcomeProcessor(mockEnv, mockLayoutState)
        val calls = mutableListOf<Call>()
        val handleOutcome = makehandleOutcome(calls)
        return Triple(processor, handleOutcome, calls)
    }

    private fun resolveBehavior(behavior: ButtonClickBehaviorType): List<Outcome> =
        OutcomeResolver.resolve(behaviors = listOf(behavior))
}
