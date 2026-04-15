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
 * Tests that all container types (EventHandler, AutomatedAction, PagerGesture,
 * TriggerActions, PagerControllerBranching.Completion) correctly parse `outcomes`
 * from JSON and produce valid [OutcomeParams] that either use outcomes or fall back
 * to legacy fields.
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
        assertNotNull(handler.outcomes)
        assertEquals(2, handler.outcomes!!.size)
        assertTrue(handler.actions.isEmpty())

        val params = handler.outcomeParams
        val resolved = params.resolve()
        assertEquals(2, resolved.size)
        assertTrue(resolved[0] is Outcome.Dismiss)
        assertTrue(resolved[1] is Outcome.PagerStepNavigation)
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
        assertNull(handler.outcomes)
        assertEquals(1, handler.actions.size)

        val params = handler.outcomeParams
        val resolved = params.resolve()
        assertEquals(1, resolved.size)
        assertTrue(resolved[0] is Outcome.SetStateAction)
    }

    @Test
    public fun testEventHandlerOutcomesTakePrecedence() {
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
        val resolved = handler.outcomeParams.resolve()
        assertEquals(1, resolved.size)
        assertTrue(resolved[0] is Outcome.Dismiss)
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
        assertNotNull(action.outcomes)
        assertNull(action.behaviors)
        assertNull(action.actions)

        val resolved = action.outcomeParams.resolve()
        assertEquals(1, resolved.size)
        val outcome = resolved[0] as Outcome.PagerStepNavigation
        assertEquals(Outcome.PagerStepNavigation.BoundaryBehavior.DISMISS, outcome.boundaryBehavior)
    }

    @Test
    public fun testAutomatedActionWithLegacyBehaviors() {
        val json = JsonValue.parseString("""{
            "identifier": "auto-2",
            "delay": 3,
            "behaviors": ["pager_next"],
            "actions": { "add_tags": ["auto"] }
        }""").requireMap()

        val action = AutomatedAction.from(json)
        assertNull(action.outcomes)
        assertNotNull(action.behaviors)
        assertNotNull(action.actions)

        val resolved = action.outcomeParams.resolve()
        assertEquals(2, resolved.size)
        assertTrue(resolved[0] is Outcome.PagerStepNavigation)
        assertTrue(resolved[1] is Outcome.AirshipAction)
    }

    @Test
    public fun testAutomatedActionOutcomesTakePrecedence() {
        val json = JsonValue.parseString("""{
            "identifier": "auto-3",
            "behaviors": ["dismiss"],
            "actions": { "add_tags": ["vip"] },
            "outcomes": [
                { "type": "pager_playback", "identifier": "pp-1", "command": "pause" }
            ]
        }""").requireMap()

        val action = AutomatedAction.from(json)
        val resolved = action.outcomeParams.resolve()
        assertEquals(1, resolved.size)
        assertTrue(resolved[0] is Outcome.PagerPlayback)
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
        assertNull(gesture.behavior)
        assertNotNull(gesture.outcomes)

        val resolved = gesture.outcomeParams.resolve()
        assertEquals(1, resolved.size)
        assertTrue(resolved[0] is Outcome.PagerStepNavigation)
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
        assertNotNull(gesture.behavior)
        assertNull(gesture.outcomes)

        val resolved = gesture.outcomeParams.resolve()
        assertEquals(1, resolved.size)
        assertTrue(resolved[0] is Outcome.PagerStepNavigation)
    }

    @Test
    public fun testPagerGestureTapOutcomesTakePrecedence() {
        val json = JsonValue.parseString("""{
            "type": "tap",
            "identifier": "tap-3",
            "location": "any",
            "behavior": {
                "behaviors": ["dismiss"]
            },
            "outcomes": [
                { "type": "pager_playback", "identifier": "pp-1", "command": "toggle" }
            ]
        }""").requireMap()

        val gesture = PagerGesture.from(json) as PagerGesture.Tap
        val resolved = gesture.outcomeParams.resolve()
        assertEquals(1, resolved.size)
        assertTrue(resolved[0] is Outcome.PagerPlayback)
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
        assertNull(gesture.behavior)
        assertNotNull(gesture.outcomes)

        val resolved = gesture.outcomeParams.resolve()
        assertEquals(1, resolved.size)
        assertTrue(resolved[0] is Outcome.Dismiss)
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
        assertNull(gesture.pressBehavior)
        assertNull(gesture.releaseBehavior)

        val pressResolved = gesture.pressOutcomeParams.resolve()
        assertEquals(1, pressResolved.size)
        assertEquals(Outcome.PagerPlayback.Command.PAUSE, (pressResolved[0] as Outcome.PagerPlayback).command)

        val releaseResolved = gesture.releaseOutcomeParams.resolve()
        assertEquals(1, releaseResolved.size)
        assertEquals(Outcome.PagerPlayback.Command.RESUME, (releaseResolved[0] as Outcome.PagerPlayback).command)
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
        assertNotNull(gesture.pressBehavior)
        assertNotNull(gesture.releaseBehavior)

        val pressResolved = gesture.pressOutcomeParams.resolve()
        assertEquals(1, pressResolved.size)
        assertTrue(pressResolved[0] is Outcome.PagerPlayback)

        val releaseResolved = gesture.releaseOutcomeParams.resolve()
        assertEquals(1, releaseResolved.size)
        assertTrue(releaseResolved[0] is Outcome.PagerPlayback)
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
        assertNull(trigger.stateActions)
        assertNotNull(trigger.outcomes)

        val resolved = trigger.outcomeParams.resolve()
        assertEquals(2, resolved.size)
        assertTrue(resolved[0] is Outcome.SetStateAction)
        assertTrue(resolved[1] is Outcome.Dismiss)
    }

    @Test
    public fun testTriggerActionsWithLegacyStateActions() {
        val json = JsonValue.parseString("""{
            "state_actions": [
                { "type": "set", "key": "triggered", "value": true }
            ]
        }""").requireMap()

        val trigger = TriggerActions.fromJson(json)
        assertNotNull(trigger.stateActions)
        assertNull(trigger.outcomes)

        val resolved = trigger.outcomeParams.resolve()
        assertEquals(1, resolved.size)
        assertTrue(resolved[0] is Outcome.SetStateAction)
    }

    @Test
    public fun testTriggerActionsOutcomesTakePrecedence() {
        val json = JsonValue.parseString("""{
            "state_actions": [
                { "type": "set", "key": "ignored", "value": "should_not_appear" }
            ],
            "outcomes": [
                { "type": "dismiss", "identifier": "d-1" }
            ]
        }""").requireMap()

        val trigger = TriggerActions.fromJson(json)
        val resolved = trigger.outcomeParams.resolve()
        assertEquals(1, resolved.size)
        assertTrue(resolved[0] is Outcome.Dismiss)
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
        assertNotNull(completion.outcomes)

        val resolved = completion.outcomeParams.resolve()
        assertEquals(1, resolved.size)
        assertTrue(resolved[0] is Outcome.SetStateAction)
    }

    @Test
    public fun testCompletionWithLegacyStateActions() {
        val json = JsonValue.parseString("""{
            "state_actions": [
                { "type": "set", "key": "done", "value": true }
            ]
        }""")

        val completion = PagerControllerBranching.Completion.from(json)
        assertNull(completion.outcomes)
        assertNotNull(completion.stateActions)

        val resolved = completion.outcomeParams.resolve()
        assertEquals(1, resolved.size)
        assertTrue(resolved[0] is Outcome.SetStateAction)
    }

    @Test
    public fun testCompletionOutcomesTakePrecedence() {
        val json = JsonValue.parseString("""{
            "state_actions": [
                { "type": "set", "key": "ignored", "value": "no" }
            ],
            "outcomes": [
                { "type": "dismiss", "identifier": "d-1" }
            ]
        }""")

        val completion = PagerControllerBranching.Completion.from(json)
        val resolved = completion.outcomeParams.resolve()
        assertEquals(1, resolved.size)
        assertTrue(resolved[0] is Outcome.Dismiss)
    }
}
