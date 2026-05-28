/* Copyright Airship and Contributors */

package com.urbanairship.android.layout.property

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.android.layout.environment.TriggerActions
import com.urbanairship.json.JsonValue
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests that each container type (EventHandler, AutomatedAction, PagerGesture,
 * TriggerActions, PagerControllerBranching.Completion) correctly translates its
 * JSON input — both the new `outcomes` format and the legacy fields — into the
 * resolved [Outcome] list it exposes.
 *
 * The resolver itself (precedence between new/legacy formats, conversion of legacy
 * state actions / behaviors / actions into outcomes) is unit-tested in [OutcomeTest].
 * These tests only verify the JSON-to-resolver wiring at each holder boundary —
 * i.e. that each holder hands the right JSON fields to [OutcomeResolver.resolve].
 */
@RunWith(AndroidJUnit4::class)
public class OutcomeIntegrationTest {

    // =========================================================================
    // EventHandler
    // =========================================================================

    @Test
    public fun testEventHandlerWithOutcomes() {
        val json = JsonValue.parseString("""{
            "type": "tap",
            "outcomes": [
                { "type": "dismiss", "identifier": "d-1" },
                { "type": "pager_step_navigation", "identifier": "psn-1", "direction": "next" }
            ]
        }""").requireMap()

        val handler = EventHandler(json)
        assertEquals(EventHandler.Type.TAP, handler.type)
        assertEquals(2, handler.outcomes.size)
        assertTrue(handler.outcomes[0] is Outcome.Dismiss)
        assertTrue(handler.outcomes[1] is Outcome.PagerStepNavigation)
    }

    @Test
    public fun testEventHandlerWithLegacyStateActions() {
        val json = JsonValue.parseString("""{
            "type": "form_input",
            "state_actions": [
                { "type": "set", "key": "color", "value": "red" }
            ]
        }""").requireMap()

        val handler = EventHandler(json)
        assertEquals(EventHandler.Type.FORM_INPUT, handler.type)
        assertEquals(1, handler.outcomes.size)

        val setState = handler.outcomes.single() as Outcome.SetStateAction
        val action = setState.action as StateAction.SetState
        assertEquals("color", action.key)
        assertEquals(JsonValue.wrap("red"), action.value)
    }

    /**
     * Smoke test that holders route through [OutcomeResolver]: when both the new
     * `outcomes` field and legacy fields are present, the new format wins.
     * The resolver's precedence rule itself is covered by [OutcomeTest]; we only
     * need one end-to-end check here to catch wiring regressions.
     */
    @Test
    public fun testNewOutcomesTakePrecedenceOverLegacyFields() {
        val json = JsonValue.parseString("""{
            "type": "tap",
            "state_actions": [
                { "type": "set", "key": "ignored", "value": "should_not_appear" }
            ],
            "outcomes": [
                { "type": "dismiss", "identifier": "d-1" }
            ]
        }""").requireMap()

        val handler = EventHandler(json)
        assertEquals(1, handler.outcomes.size)
        assertTrue(handler.outcomes[0] is Outcome.Dismiss)
    }

    // =========================================================================
    // AutomatedAction
    // =========================================================================

    @Test
    public fun testAutomatedActionWithOutcomes() {
        val json = JsonValue.parseString("""{
            "identifier": "auto-1",
            "delay": 5,
            "outcomes": [
                { "type": "pager_step_navigation", "identifier": "psn-1", "direction": "next", "boundary_behavior": "dismiss" }
            ]
        }""").requireMap()

        val action = AutomatedAction.from(json)
        assertEquals("auto-1", action.identifier)
        assertEquals(5, action.delay)
        assertEquals(1, action.outcomes.size)

        val outcome = action.outcomes.single() as Outcome.PagerStepNavigation
        assertEquals(Outcome.PagerStepNavigation.BoundaryBehavior.DISMISS, outcome.boundaryBehavior)
    }

    @Test
    public fun testAutomatedActionWithLegacyBehaviorsAndActions() {
        val json = JsonValue.parseString("""{
            "identifier": "auto-2",
            "delay": 3,
            "behaviors": ["pager_next"],
            "actions": { "add_tags": ["auto"] }
        }""").requireMap()

        val action = AutomatedAction.from(json)
        assertEquals(2, action.outcomes.size)
        assertTrue(action.outcomes[0] is Outcome.PagerStepNavigation)
        assertTrue(action.outcomes[1] is Outcome.AirshipAction)
    }

    // =========================================================================
    // AutomatedAction – navigation detection with outcomes
    // =========================================================================

    @Test
    public fun testEarliestNavigationActionWithOutcomePagerStep() {
        val actions = listOf(
            AutomatedAction(
                identifier = "auto-nav",
                delay = 5,
                outcomes = listOf(
                    Outcome.PagerStepNavigation(
                        identifier = "psn-1",
                        direction = Outcome.PagerStepNavigation.Direction.NEXT
                    )
                )
            )
        )
        assertNotNull(actions.earliestNavigationAction)
        assertEquals("auto-nav", actions.earliestNavigationAction!!.identifier)
    }

    @Test
    public fun testEarliestNavigationActionWithOutcomeDismiss() {
        val actions = listOf(
            AutomatedAction(
                identifier = "auto-dismiss",
                delay = 3,
                outcomes = listOf(Outcome.Dismiss(identifier = "d-1"))
            )
        )
        assertNotNull(actions.earliestNavigationAction)
    }

    @Test
    public fun testEarliestNavigationActionWithOutcomePagerJump() {
        val actions = listOf(
            AutomatedAction(
                identifier = "auto-jump",
                delay = 2,
                outcomes = listOf(
                    Outcome.PagerJumpNavigation(identifier = "pjn-1", page = Outcome.PagerJumpNavigation.Page.END)
                )
            )
        )
        assertNotNull(actions.earliestNavigationAction)
    }

    @Test
    public fun testEarliestNavigationActionNoNavOutcomes() {
        val actions = listOf(
            AutomatedAction(
                identifier = "auto-no-nav",
                delay = 1,
                outcomes = listOf(
                    Outcome.PagerPlayback(identifier = "pp-1", command = Outcome.PagerPlayback.Command.PAUSE)
                )
            )
        )
        assertNull(actions.earliestNavigationAction)
    }

    // =========================================================================
    // AutomatedAction – pause/resume detection with outcomes
    // =========================================================================

    @Test
    public fun testHasPagerPauseOrResumeWithOutcomePause() {
        val actions = listOf(
            AutomatedAction(
                identifier = "auto-pause",
                delay = 0,
                outcomes = listOf(
                    Outcome.PagerPlayback(identifier = "pp-1", command = Outcome.PagerPlayback.Command.PAUSE)
                )
            )
        )
        assertTrue(actions.hasPagerPauseOrResumeAction)
    }

    @Test
    public fun testHasPagerPauseOrResumeWithOutcomeResume() {
        val actions = listOf(
            AutomatedAction(
                identifier = "auto-resume",
                delay = 0,
                outcomes = listOf(
                    Outcome.PagerPlayback(identifier = "pp-1", command = Outcome.PagerPlayback.Command.RESUME)
                )
            )
        )
        assertTrue(actions.hasPagerPauseOrResumeAction)
    }

    // =========================================================================
    // PagerGesture.Tap
    // =========================================================================

    @Test
    public fun testPagerGestureTapWithOutcomes() {
        val json = JsonValue.parseString("""{
            "type": "tap",
            "identifier": "tap-1",
            "location": "top",
            "outcomes": [
                { "type": "pager_step_navigation", "identifier": "psn-1", "direction": "next" }
            ]
        }""").requireMap()

        val gesture = PagerGesture.from(json) as PagerGesture.Tap
        assertEquals("tap-1", gesture.identifier)
        assertEquals(1, gesture.outcomes!!.size)
        assertTrue(gesture.outcomes!![0] is Outcome.PagerStepNavigation)
    }

    @Test
    public fun testPagerGestureTapWithLegacyBehavior() {
        val json = JsonValue.parseString("""{
            "type": "tap",
            "identifier": "tap-2",
            "location": "bottom",
            "behavior": {
                "behaviors": ["pager_next"]
            }
        }""").requireMap()

        val gesture = PagerGesture.from(json) as PagerGesture.Tap
        assertEquals(1, gesture.outcomes!!.size)
        assertTrue(gesture.outcomes!![0] is Outcome.PagerStepNavigation)
    }

    // =========================================================================
    // PagerGesture.Swipe
    // =========================================================================

    @Test
    public fun testPagerGestureSwipeWithOutcomes() {
        val json = JsonValue.parseString("""{
            "type": "swipe",
            "identifier": "swipe-1",
            "direction": "up",
            "outcomes": [
                { "type": "dismiss", "identifier": "d-1" }
            ]
        }""").requireMap()

        val gesture = PagerGesture.from(json) as PagerGesture.Swipe
        assertEquals(1, gesture.outcomes!!.size)
        assertTrue(gesture.outcomes!![0] is Outcome.Dismiss)
    }

    // =========================================================================
    // PagerGesture.Hold
    // =========================================================================

    @Test
    public fun testPagerGestureHoldWithOutcomes() {
        val json = JsonValue.parseString("""{
            "type": "hold",
            "identifier": "hold-1",
            "press_outcomes": [
                { "type": "pager_playback", "identifier": "pp-press", "command": "pause" }
            ],
            "release_outcomes": [
                { "type": "pager_playback", "identifier": "pp-release", "command": "resume" }
            ]
        }""").requireMap()

        val gesture = PagerGesture.from(json) as PagerGesture.Hold
        assertEquals(1, gesture.pressOutcomes!!.size)
        assertEquals(Outcome.PagerPlayback.Command.PAUSE, (gesture.pressOutcomes!![0] as Outcome.PagerPlayback).command)

        assertEquals(1, gesture.releaseOutcomes!!.size)
        assertEquals(Outcome.PagerPlayback.Command.RESUME, (gesture.releaseOutcomes!![0] as Outcome.PagerPlayback).command)
    }

    @Test
    public fun testPagerGestureHoldWithLegacyBehaviors() {
        val json = JsonValue.parseString("""{
            "type": "hold",
            "identifier": "hold-2",
            "press_behavior": {
                "behaviors": ["pager_pause"]
            },
            "release_behavior": {
                "behaviors": ["pager_resume"]
            }
        }""").requireMap()

        val gesture = PagerGesture.from(json) as PagerGesture.Hold
        assertEquals(1, gesture.pressOutcomes!!.size)
        assertTrue(gesture.pressOutcomes!![0] is Outcome.PagerPlayback)

        assertEquals(1, gesture.releaseOutcomes!!.size)
        assertTrue(gesture.releaseOutcomes!![0] is Outcome.PagerPlayback)
    }

    // =========================================================================
    // TriggerActions
    // =========================================================================

    @Test
    public fun testTriggerActionsWithOutcomes() {
        val json = JsonValue.parseString("""{
            "outcomes": [
                { "type": "state_action", "identifier": "sa-1", "action": { "type": "set", "key": "triggered", "value": true } },
                { "type": "dismiss", "identifier": "d-1" }
            ]
        }""").requireMap()

        val trigger = TriggerActions.fromJson(json)
        assertEquals(2, trigger.outcomes.size)
        assertTrue(trigger.outcomes[0] is Outcome.SetStateAction)
        assertTrue(trigger.outcomes[1] is Outcome.Dismiss)
    }

    @Test
    public fun testTriggerActionsWithLegacyStateActions() {
        val json = JsonValue.parseString("""{
            "state_actions": [
                { "type": "set", "key": "triggered", "value": true }
            ]
        }""").requireMap()

        val trigger = TriggerActions.fromJson(json)
        assertEquals(1, trigger.outcomes.size)
        assertTrue(trigger.outcomes[0] is Outcome.SetStateAction)
    }

    // =========================================================================
    // PagerControllerBranching.Completion
    // =========================================================================

    @Test
    public fun testCompletionWithOutcomes() {
        val json = JsonValue.parseString("""{
            "outcomes": [
                { "type": "state_action", "identifier": "sa-1", "action": { "type": "set", "key": "done", "value": true } }
            ]
        }""")

        val completion = PagerControllerBranching.Completion.from(json)
        assertNull(completion.predicate)
        assertEquals(1, completion.outcomes.size)
        assertTrue(completion.outcomes[0] is Outcome.SetStateAction)
    }

    @Test
    public fun testCompletionWithLegacyStateActions() {
        val json = JsonValue.parseString("""{
            "state_actions": [
                { "type": "set", "key": "done", "value": true }
            ]
        }""")

        val completion = PagerControllerBranching.Completion.from(json)
        assertEquals(1, completion.outcomes.size)
        assertTrue(completion.outcomes[0] is Outcome.SetStateAction)
    }
}
