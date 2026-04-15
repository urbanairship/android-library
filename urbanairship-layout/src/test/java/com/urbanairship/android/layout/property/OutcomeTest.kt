/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.json.JsonException
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class OutcomeTest {

    // =========================================================================
    // JSON Parsing – Outcome.fromJson
    // =========================================================================

    @Test
    public fun testParseDismiss() {
        val outcome = parseOutcome("""{ "type": "dismiss", "identifier": "d-1" }""")
        assertTrue(outcome is Outcome.Dismiss)
        assertEquals("d-1", outcome.identifier)
        assertFalse((outcome as Outcome.Dismiss).cancel)
    }

    @Test
    public fun testParseDismissCancel() {
        val outcome = parseOutcome("""{ "type": "dismiss", "identifier": "d-2", "cancel": true }""") as Outcome.Dismiss
        assertTrue(outcome.cancel)
    }

    @Test
    public fun testParseAirshipAction() {
        val outcome = parseOutcome("""{ "type": "airship_action", "identifier": "aa-1", "actions": { "add_tags": ["vip"] } }""") as Outcome.AirshipAction
        assertEquals("aa-1", outcome.identifier)
        assertTrue(outcome.actions.containsKey("add_tags"))
    }

    @Test
    public fun testParsePagerStepNavigationNext() {
        val outcome = parseOutcome("""{ "type": "pager_step_navigation", "identifier": "psn-1", "direction": "next" }""") as Outcome.PagerStepNavigation
        assertEquals(Outcome.PagerStepNavigation.Direction.NEXT, outcome.direction)
        assertEquals(Outcome.PagerStepNavigation.BoundaryBehavior.IGNORE, outcome.boundaryBehavior)
    }

    @Test
    public fun testParsePagerStepNavigationPrevious() {
        val outcome = parseOutcome("""{ "type": "pager_step_navigation", "identifier": "psn-2", "direction": "previous" }""") as Outcome.PagerStepNavigation
        assertEquals(Outcome.PagerStepNavigation.Direction.PREVIOUS, outcome.direction)
    }

    @Test
    public fun testParsePagerStepNavigationBoundaryDismiss() {
        val outcome = parseOutcome("""{ "type": "pager_step_navigation", "identifier": "psn-3", "direction": "next", "boundary_behavior": "dismiss" }""") as Outcome.PagerStepNavigation
        assertEquals(Outcome.PagerStepNavigation.BoundaryBehavior.DISMISS, outcome.boundaryBehavior)
    }

    @Test
    public fun testParsePagerStepNavigationBoundaryWrap() {
        val outcome = parseOutcome("""{ "type": "pager_step_navigation", "identifier": "psn-4", "direction": "next", "boundary_behavior": "wrap" }""") as Outcome.PagerStepNavigation
        assertEquals(Outcome.PagerStepNavigation.BoundaryBehavior.WRAP, outcome.boundaryBehavior)
    }

    @Test
    public fun testParsePagerStepNavigationBoundaryIgnore() {
        val outcome = parseOutcome("""{ "type": "pager_step_navigation", "identifier": "psn-5", "direction": "next", "boundary_behavior": "ignore" }""") as Outcome.PagerStepNavigation
        assertEquals(Outcome.PagerStepNavigation.BoundaryBehavior.IGNORE, outcome.boundaryBehavior)
    }

    @Test
    public fun testParsePagerJumpNavigationStart() {
        val outcome = parseOutcome("""{ "type": "pager_jump_navigation", "identifier": "pjn-1", "page": "start" }""") as Outcome.PagerJumpNavigation
        assertEquals(Outcome.PagerJumpNavigation.Page.START, outcome.page)
    }

    @Test
    public fun testParsePagerJumpNavigationEnd() {
        val outcome = parseOutcome("""{ "type": "pager_jump_navigation", "identifier": "pjn-2", "page": "end" }""") as Outcome.PagerJumpNavigation
        assertEquals(Outcome.PagerJumpNavigation.Page.END, outcome.page)
    }

    @Test
    public fun testParsePagerPlaybackCommands() {
        for ((cmd, expected) in listOf(
            "pause" to Outcome.PagerPlayback.Command.PAUSE,
            "resume" to Outcome.PagerPlayback.Command.RESUME,
            "toggle" to Outcome.PagerPlayback.Command.TOGGLE
        )) {
            val outcome = parseOutcome("""{ "type": "pager_playback", "identifier": "pp-$cmd", "command": "$cmd" }""") as Outcome.PagerPlayback
            assertEquals(expected, outcome.command)
        }
    }

    @Test
    public fun testParseMediaPlaybackCommands() {
        for ((cmd, expected) in listOf(
            "play" to Outcome.MediaPlayback.Command.PLAY,
            "pause" to Outcome.MediaPlayback.Command.PAUSE,
            "toggle" to Outcome.MediaPlayback.Command.TOGGLE
        )) {
            val outcome = parseOutcome("""{ "type": "media_playback", "identifier": "mp-$cmd", "command": "$cmd" }""") as Outcome.MediaPlayback
            assertEquals(expected, outcome.command)
        }
    }

    @Test
    public fun testParseMediaAudioCommands() {
        for ((cmd, expected) in listOf(
            "mute" to Outcome.MediaAudio.Command.MUTE,
            "unmute" to Outcome.MediaAudio.Command.UNMUTE,
            "toggle" to Outcome.MediaAudio.Command.TOGGLE
        )) {
            val outcome = parseOutcome("""{ "type": "media_audio", "identifier": "ma-$cmd", "command": "$cmd" }""") as Outcome.MediaAudio
            assertEquals(expected, outcome.command)
        }
    }

    @Test
    public fun testParseStateActionSet() {
        val outcome = parseOutcome("""{ "type": "state_action", "identifier": "sa-1", "action": { "type": "set", "key": "color", "value": "red" } }""") as Outcome.SetStateAction
        val action = outcome.action as StateAction.SetState
        assertEquals("color", action.key)
        assertEquals(JsonValue.wrap("red"), action.value)
    }

    @Test
    public fun testParseStateActionClear() {
        val outcome = parseOutcome("""{ "type": "state_action", "identifier": "sa-2", "action": { "type": "clear" } }""") as Outcome.SetStateAction
        assertTrue(outcome.action is StateAction.ClearState)
    }

    @Test
    public fun testParseFormSubmit() {
        val outcome = parseOutcome("""{ "type": "form", "identifier": "f-1", "command": "submit" }""") as Outcome.Form
        assertEquals(Outcome.Form.Command.SUBMIT, outcome.command)
    }

    @Test
    public fun testParseFormValidate() {
        val outcome = parseOutcome("""{ "type": "form", "identifier": "f-2", "command": "validate" }""") as Outcome.Form
        assertEquals(Outcome.Form.Command.VALIDATE, outcome.command)
    }

    @Test(expected = JsonException::class)
    public fun testParseUnknownTypeThrows() {
        parseOutcome("""{ "type": "unknown_type", "identifier": "x-1" }""")
    }

    @Test(expected = JsonException::class)
    public fun testParseMissingIdentifierThrows() {
        parseOutcome("""{ "type": "dismiss" }""")
    }

    @Test(expected = JsonException::class)
    public fun testParseMissingTypeThrows() {
        parseOutcome("""{ "identifier": "d-1" }""")
    }

    // =========================================================================
    // JSON Parsing – Outcome.fromList
    // =========================================================================

    @Test
    public fun testFromListParsesMultiple() {
        val outcomes = Outcome.fromList(JsonValue.parseString("""[
            { "type": "dismiss", "identifier": "d-1" },
            { "type": "pager_step_navigation", "identifier": "psn-1", "direction": "next" },
            { "type": "form", "identifier": "f-1", "command": "submit" }
        ]""").requireList())

        assertEquals(3, outcomes.size)
        assertTrue(outcomes[0] is Outcome.Dismiss)
        assertTrue(outcomes[1] is Outcome.PagerStepNavigation)
        assertTrue(outcomes[2] is Outcome.Form)
    }

    @Test
    public fun testFromListEmptyArray() {
        val outcomes = Outcome.fromList(JsonValue.parseString("[]").requireList())
        assertTrue(outcomes.isEmpty())
    }

    // =========================================================================
    // OutcomeType enum
    // =========================================================================

    @Test
    public fun testOutcomeTypeFromValidValues() {
        assertEquals(OutcomeType.AIRSHIP_ACTION, OutcomeType.from("airship_action"))
        assertEquals(OutcomeType.DISMISS, OutcomeType.from("dismiss"))
        assertEquals(OutcomeType.PAGER_PLAYBACK, OutcomeType.from("pager_playback"))
        assertEquals(OutcomeType.PAGER_JUMP_NAVIGATION, OutcomeType.from("pager_jump_navigation"))
        assertEquals(OutcomeType.PAGER_STEP_NAVIGATION, OutcomeType.from("pager_step_navigation"))
        assertEquals(OutcomeType.MEDIA_PLAYBACK, OutcomeType.from("media_playback"))
        assertEquals(OutcomeType.MEDIA_AUDIO, OutcomeType.from("media_audio"))
        assertEquals(OutcomeType.STATE_ACTION, OutcomeType.from("state_action"))
        assertEquals(OutcomeType.FORM, OutcomeType.from("form"))
    }

    @Test(expected = JsonException::class)
    public fun testOutcomeTypeFromInvalidThrows() {
        OutcomeType.from("not_a_type")
    }

    // =========================================================================
    // OutcomeParams.resolve – outcomes take precedence
    // =========================================================================

    @Test
    public fun testResolveReturnsOutcomesWhenPresent() {
        val outcomes = listOf(Outcome.Dismiss(identifier = "d-1"))
        val params = OutcomeParams(
            outcomes = outcomes,
            stateActions = listOf(StateAction.ClearState),
            behaviors = listOf(ButtonClickBehaviorType.PAGER_NEXT),
            actions = mapOf("add_tags" to JsonValue.wrap("vip"))
        )
        assertEquals(outcomes, params.resolve())
    }

    @Test
    public fun testResolveIgnoresLegacyWhenOutcomesPresent() {
        val params = OutcomeParams(
            outcomes = emptyList(),
            stateActions = listOf(StateAction.ClearState),
            behaviors = listOf(ButtonClickBehaviorType.DISMISS)
        )
        assertTrue(params.resolve().isEmpty())
    }

    @Test
    public fun testResolveEmptyParams() {
        assertTrue(OutcomeParams().resolve().isEmpty())
        assertTrue(OutcomeParams.EMPTY.resolve().isEmpty())
    }

    // =========================================================================
    // OutcomeParams.resolve – legacy state actions
    // =========================================================================

    @Test
    public fun testResolveConvertsSetState() {
        val params = OutcomeParams(stateActions = listOf(StateAction.SetState(key = "k", value = JsonValue.wrap("v"))))
        val resolved = params.resolve()
        assertEquals(1, resolved.size)
        val outcome = resolved[0] as Outcome.SetStateAction
        val action = outcome.action as StateAction.SetState
        assertEquals("k", action.key)
        assertEquals("state_action_set_k", outcome.identifier)
    }

    @Test
    public fun testResolveConvertsClearState() {
        val params = OutcomeParams(stateActions = listOf(StateAction.ClearState))
        val resolved = params.resolve()
        assertEquals(1, resolved.size)
        assertEquals("state_action_clear", resolved[0].identifier)
        assertTrue((resolved[0] as Outcome.SetStateAction).action is StateAction.ClearState)
    }

    @Test
    public fun testResolveConvertsSetFormValue() {
        val params = OutcomeParams(stateActions = listOf(StateAction.SetFormValue(key = "email")))
        val resolved = params.resolve()
        assertEquals(1, resolved.size)
        assertEquals("state_action_set_form_value_email", resolved[0].identifier)
    }

    // =========================================================================
    // OutcomeParams.resolve – legacy behaviors (every ButtonClickBehaviorType)
    // =========================================================================

    @Test
    public fun testResolveBehaviorPagerNext() {
        val outcome = resolveSingleBehavior(ButtonClickBehaviorType.PAGER_NEXT) as Outcome.PagerStepNavigation
        assertEquals(Outcome.PagerStepNavigation.Direction.NEXT, outcome.direction)
        assertEquals(Outcome.PagerStepNavigation.BoundaryBehavior.IGNORE, outcome.boundaryBehavior)
    }

    @Test
    public fun testResolveBehaviorPagerPrevious() {
        val outcome = resolveSingleBehavior(ButtonClickBehaviorType.PAGER_PREVIOUS) as Outcome.PagerStepNavigation
        assertEquals(Outcome.PagerStepNavigation.Direction.PREVIOUS, outcome.direction)
    }

    @Test
    public fun testResolveBehaviorPagerNextOrDismiss() {
        val outcome = resolveSingleBehavior(ButtonClickBehaviorType.PAGER_NEXT_OR_DISMISS) as Outcome.PagerStepNavigation
        assertEquals(Outcome.PagerStepNavigation.Direction.NEXT, outcome.direction)
        assertEquals(Outcome.PagerStepNavigation.BoundaryBehavior.DISMISS, outcome.boundaryBehavior)
    }

    @Test
    public fun testResolveBehaviorPagerNextOrFirst() {
        val outcome = resolveSingleBehavior(ButtonClickBehaviorType.PAGER_NEXT_OR_FIRST) as Outcome.PagerStepNavigation
        assertEquals(Outcome.PagerStepNavigation.Direction.NEXT, outcome.direction)
        assertEquals(Outcome.PagerStepNavigation.BoundaryBehavior.WRAP, outcome.boundaryBehavior)
    }

    @Test
    public fun testResolveBehaviorPagerPause() {
        val outcome = resolveSingleBehavior(ButtonClickBehaviorType.PAGER_PAUSE) as Outcome.PagerPlayback
        assertEquals(Outcome.PagerPlayback.Command.PAUSE, outcome.command)
    }

    @Test
    public fun testResolveBehaviorPagerResume() {
        val outcome = resolveSingleBehavior(ButtonClickBehaviorType.PAGER_RESUME) as Outcome.PagerPlayback
        assertEquals(Outcome.PagerPlayback.Command.RESUME, outcome.command)
    }

    @Test
    public fun testResolveBehaviorPagerPauseToggle() {
        val outcome = resolveSingleBehavior(ButtonClickBehaviorType.PAGER_PAUSE_TOGGLE) as Outcome.PagerPlayback
        assertEquals(Outcome.PagerPlayback.Command.TOGGLE, outcome.command)
    }

    @Test
    public fun testResolveBehaviorDismiss() {
        val outcome = resolveSingleBehavior(ButtonClickBehaviorType.DISMISS) as Outcome.Dismiss
        assertFalse(outcome.cancel)
    }

    @Test
    public fun testResolveBehaviorCancel() {
        val outcome = resolveSingleBehavior(ButtonClickBehaviorType.CANCEL) as Outcome.Dismiss
        assertTrue(outcome.cancel)
    }

    @Test
    public fun testResolveBehaviorVideoPlay() {
        val outcome = resolveSingleBehavior(ButtonClickBehaviorType.VIDEO_PLAY) as Outcome.MediaPlayback
        assertEquals(Outcome.MediaPlayback.Command.PLAY, outcome.command)
    }

    @Test
    public fun testResolveBehaviorVideoPause() {
        val outcome = resolveSingleBehavior(ButtonClickBehaviorType.VIDEO_PAUSE) as Outcome.MediaPlayback
        assertEquals(Outcome.MediaPlayback.Command.PAUSE, outcome.command)
    }

    @Test
    public fun testResolveBehaviorVideoTogglePlay() {
        val outcome = resolveSingleBehavior(ButtonClickBehaviorType.VIDEO_TOGGLE_PLAY) as Outcome.MediaPlayback
        assertEquals(Outcome.MediaPlayback.Command.TOGGLE, outcome.command)
    }

    @Test
    public fun testResolveBehaviorVideoMute() {
        val outcome = resolveSingleBehavior(ButtonClickBehaviorType.VIDEO_MUTE) as Outcome.MediaAudio
        assertEquals(Outcome.MediaAudio.Command.MUTE, outcome.command)
    }

    @Test
    public fun testResolveBehaviorVideoUnmute() {
        val outcome = resolveSingleBehavior(ButtonClickBehaviorType.VIDEO_UNMUTE) as Outcome.MediaAudio
        assertEquals(Outcome.MediaAudio.Command.UNMUTE, outcome.command)
    }

    @Test
    public fun testResolveBehaviorVideoToggleMute() {
        val outcome = resolveSingleBehavior(ButtonClickBehaviorType.VIDEO_TOGGLE_MUTE) as Outcome.MediaAudio
        assertEquals(Outcome.MediaAudio.Command.TOGGLE, outcome.command)
    }

    @Test
    public fun testResolveBehaviorFormSubmit() {
        val outcome = resolveSingleBehavior(ButtonClickBehaviorType.FORM_SUBMIT) as Outcome.Form
        assertEquals(Outcome.Form.Command.SUBMIT, outcome.command)
    }

    @Test
    public fun testResolveBehaviorFormValidate() {
        val outcome = resolveSingleBehavior(ButtonClickBehaviorType.FORM_VALIDATE) as Outcome.Form
        assertEquals(Outcome.Form.Command.VALIDATE, outcome.command)
    }

    @Test
    public fun testResolveBehaviorAsyncViewRetry() {
        val outcome = resolveSingleBehavior(ButtonClickBehaviorType.ASYNC_VIEW_RETRY)
        assertTrue(outcome is Outcome.AsyncViewReload)
    }

    // =========================================================================
    // OutcomeParams.resolve – legacy airship actions
    // =========================================================================

    @Test
    public fun testResolveConvertsAirshipActions() {
        val params = OutcomeParams(actions = mapOf("add_tags" to JsonValue.wrap("vip"), "deep_link" to JsonValue.wrap("app://home")))
        val resolved = params.resolve()
        assertEquals(1, resolved.size)
        val outcome = resolved[0] as Outcome.AirshipAction
        assertEquals("actions_payload", outcome.identifier)
        assertEquals(2, outcome.actions.size)
    }

    @Test
    public fun testResolveSkipsEmptyActions() {
        val params = OutcomeParams(actions = emptyMap())
        assertTrue(params.resolve().isEmpty())
    }

    // =========================================================================
    // OutcomeParams.resolve – combined legacy fields
    // =========================================================================

    @Test
    public fun testResolveCombinesAllLegacyInOrder() {
        val params = OutcomeParams(
            stateActions = listOf(StateAction.ClearState),
            behaviors = listOf(ButtonClickBehaviorType.PAGER_NEXT, ButtonClickBehaviorType.DISMISS),
            actions = mapOf("deep_link" to JsonValue.wrap("app://x"))
        )
        val resolved = params.resolve()
        assertEquals(4, resolved.size)
        assertTrue(resolved[0] is Outcome.SetStateAction)
        assertTrue(resolved[1] is Outcome.PagerStepNavigation)
        assertTrue(resolved[2] is Outcome.Dismiss)
        assertTrue(resolved[3] is Outcome.AirshipAction)
    }

    // =========================================================================
    // OutcomeParams.hasFormOutcome
    // =========================================================================

    @Test
    public fun testHasFormOutcomeWithOutcomeSubmit() {
        assertTrue(OutcomeParams(outcomes = listOf(
            Outcome.Form(identifier = "f-1", command = Outcome.Form.Command.SUBMIT)
        )).hasFormOutcome)
    }

    @Test
    public fun testHasFormOutcomeWithOutcomeValidate() {
        assertTrue(OutcomeParams(outcomes = listOf(
            Outcome.Form(identifier = "f-1", command = Outcome.Form.Command.VALIDATE)
        )).hasFormOutcome)
    }

    @Test
    public fun testHasFormOutcomeWithBehaviorFormSubmit() {
        assertTrue(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.FORM_SUBMIT)).hasFormOutcome)
    }

    @Test
    public fun testHasFormOutcomeWithBehaviorFormValidate() {
        assertTrue(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.FORM_VALIDATE)).hasFormOutcome)
    }

    @Test
    public fun testHasFormOutcomeFalseWithOutcomes() {
        assertFalse(OutcomeParams(outcomes = listOf(Outcome.Dismiss(identifier = "d-1"))).hasFormOutcome)
    }

    @Test
    public fun testHasFormOutcomeFalseWithBehaviors() {
        assertFalse(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.PAGER_NEXT)).hasFormOutcome)
    }

    @Test
    public fun testHasFormOutcomeFalseEmpty() {
        assertFalse(OutcomeParams.EMPTY.hasFormOutcome)
    }

    // =========================================================================
    // OutcomeParams.hasForwardOutcome
    // =========================================================================

    @Test
    public fun testHasForwardOutcomeStepNext() {
        assertTrue(OutcomeParams(outcomes = listOf(
            Outcome.PagerStepNavigation(identifier = "p-1", direction = Outcome.PagerStepNavigation.Direction.NEXT)
        )).hasForwardOutcome)
    }

    @Test
    public fun testHasForwardOutcomeJumpEnd() {
        assertTrue(OutcomeParams(outcomes = listOf(
            Outcome.PagerJumpNavigation(identifier = "p-1", page = Outcome.PagerJumpNavigation.Page.END)
        )).hasForwardOutcome)
    }

    @Test
    public fun testHasForwardOutcomePlaybackResume() {
        assertTrue(OutcomeParams(outcomes = listOf(
            Outcome.PagerPlayback(identifier = "p-1", command = Outcome.PagerPlayback.Command.RESUME)
        )).hasForwardOutcome)
    }

    @Test
    public fun testHasForwardOutcomeFalseWithPrevious() {
        assertFalse(OutcomeParams(outcomes = listOf(
            Outcome.PagerStepNavigation(identifier = "p-1", direction = Outcome.PagerStepNavigation.Direction.PREVIOUS)
        )).hasForwardOutcome)
    }

    @Test
    public fun testHasForwardOutcomeFalseWithJumpStart() {
        assertFalse(OutcomeParams(outcomes = listOf(
            Outcome.PagerJumpNavigation(identifier = "p-1", page = Outcome.PagerJumpNavigation.Page.START)
        )).hasForwardOutcome)
    }

    @Test
    public fun testHasForwardOutcomeWithLegacyBehaviors() {
        assertTrue(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.PAGER_NEXT)).hasForwardOutcome)
        assertTrue(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.PAGER_NEXT_OR_DISMISS)).hasForwardOutcome)
        assertTrue(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.PAGER_NEXT_OR_FIRST)).hasForwardOutcome)
        assertTrue(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.PAGER_RESUME)).hasForwardOutcome)
    }

    @Test
    public fun testHasForwardOutcomeFalseWithLegacyBehaviors() {
        assertFalse(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.PAGER_PREVIOUS)).hasForwardOutcome)
        assertFalse(OutcomeParams(behaviors = listOf(ButtonClickBehaviorType.DISMISS)).hasForwardOutcome)
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun parseOutcome(json: String): Outcome =
        Outcome.fromJson(JsonValue.parseString(json).requireMap())

    private fun resolveSingleBehavior(behavior: ButtonClickBehaviorType): Outcome {
        val resolved = OutcomeParams(behaviors = listOf(behavior)).resolve()
        assertEquals(1, resolved.size)
        return resolved[0]
    }
}
