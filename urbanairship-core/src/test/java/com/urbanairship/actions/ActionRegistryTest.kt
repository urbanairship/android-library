/* Copyright Airship and Contributors */
package com.urbanairship.actions

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.BaseTestCase
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ActionRegistryTest : BaseTestCase() {

    private val registry = ActionRegistry()

    @Test
    public fun testRegister() {
        val entry = ActionRegistry.Entry(action = TestAction())
        registry.registerEntry(names = setOf("who", "are", "we?"), entry = entry)
        assertEquals(entry, registry.getEntry("who"))
        assertEquals(entry, registry.getEntry("are"))
        assertEquals(entry, registry.getEntry("we?"))
    }

    @Test
    public fun testRegisterNameConflict() {
        val someAction = TestAction()
        registry.registerEntry(names = setOf("action!"), entry = ActionRegistry.Entry(action = someAction))

        val someOtherAction = TestAction()
        registry.registerEntry(names = setOf("action!", "operation!"), entry = ActionRegistry.Entry(action = someOtherAction))

        assertEquals(someOtherAction, registry.getEntry("action!")?.action)
        assertEquals(someOtherAction, registry.getEntry("operation!")?.action)

        // Register the original action that conflicts with one of the registered names
        registry.registerEntry(names = setOf("action!"), entry = ActionRegistry.Entry(action = someAction))
        assertEquals(someAction, registry.getEntry("action!")?.action)
        assertEquals(someOtherAction, registry.getEntry("operation!")?.action)
    }

    @Test(expected = IllegalArgumentException::class)
    public fun testRegisterEmptyActionNames() {
        registry.registerEntry(names = emptySet(), entry = ActionRegistry.Entry(action = TestAction()))
    }

    @Test
    public fun testRegisterMultipleEmptyActionNames() {
        val entry = ActionRegistry.Entry(action = TestAction())
        registry.registerEntry(names = setOf("what", ""), entry = entry)
        assertEquals(entry, registry.getEntry("what"))
        assertNull(registry.getEntry(""))
    }

    @Test
    public fun testUnregister() {
        registry.registerEntry(names = setOf("actionName", "anotherName"), entry = ActionRegistry.Entry(action = TestAction()))
        assertNotNull(registry.getEntry("actionName"))
        assertNotNull(registry.getEntry("anotherName"))

        registry.removeEntry("actionName")
        assertNull(registry.getEntry("actionName"))
        assertNull(registry.getEntry("anotherName"))
    }

    @Test
    public fun testEntryActionForSituation() {
        val defaultAction = TestAction()
        val openAction = TestAction()
        val receiveAction = TestAction()

        val entry = ActionRegistry.Entry(
            action = defaultAction,
            situationOverrides = mapOf(
                Action.Situation.PUSH_OPENED to openAction,
                Action.Situation.PUSH_RECEIVED to receiveAction
            )
        )
        registry.registerEntry(names = setOf("action"), entry = entry)

        assertEquals(openAction, entry.getActionForSituation(Action.Situation.PUSH_OPENED))
        assertEquals(receiveAction, entry.getActionForSituation(Action.Situation.PUSH_RECEIVED))
        assertEquals(defaultAction, entry.getActionForSituation(Action.Situation.MANUAL_INVOCATION))
    }

    @Test
    public fun testUpdateEntry() {
        val testAction = TestAction()
        val entry = ActionRegistry.Entry(action = testAction)
        registry.registerEntry(names = setOf("action"), entry = entry)

        // Update predicate
        val predicate = ActionPredicate { true }
        registry.updateEntry("action", predicate)
        assertEquals(predicate, registry.getEntry("action")?.predicate)

        // Update action
        val anotherAction = TestAction()
        registry.updateEntry("action", anotherAction)
        assertEquals(anotherAction, registry.getEntry("action")?.action)

        // Update situation override
        val situationAction = TestAction()
        registry.updateEntry("action", situationAction, Action.Situation.AUTOMATION)
        assertEquals(situationAction, registry.getEntry("action")?.getActionForSituation(Action.Situation.AUTOMATION))
    }
}
