/* Copyright Airship and Contributors */
package com.urbanairship.actions

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.urbanairship.ApplicationMetrics
import com.urbanairship.BaseTestCase
import com.urbanairship.TestApplication
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
public class ActionRegistryTest {

    private val registry = ActionRegistry()

    private val metrics: ApplicationMetrics = mockk()

    @Before
    public fun setup() {
        TestApplication.getApplication().setApplicationMetrics(metrics)
    }

    /**
     * Tests that the default actions are registered under the correct names
     */
    @Test
    public fun testDefaultActions() {
        registry.registerDefaultActions(TestApplication.getApplication())
        assertEquals("Default entries changed", 16, registry.entries.size)

        validateEntry(registry.getEntry("^d"), "^d", "deep_link_action")
        validateEntry(registry.getEntry("^+t"), "^+t", "add_tags_action")
        validateEntry(registry.getEntry("^t"), "^t", "tag_action")
        validateEntry(registry.getEntry("^-t"), "^-t", "remove_tags_action")
        validateEntry(registry.getEntry("^u"), "^u", "open_external_url_action")
        validateEntry(registry.getEntry("add_custom_event_action"), "^+ce", "add_custom_event_action")
        validateEntry(registry.getEntry("^s"), "^s", "share_action")
        validateEntry(registry.getEntry("^c"), "^c", "clipboard_action")
        validateEntry(registry.getEntry("toast_action"), "toast_action")
        validateEntry(registry.getEntry("^w"), "^w", "wallet_action")
        validateEntry(registry.getEntry("^fdi"), "^fdi", "fetch_device_info")
        validateEntry(registry.getEntry("^ef"), "^ef", "enable_feature")
        validateEntry(registry.getEntry("^ra"), "^ra", "rate_app_action")
        validateEntry(registry.getEntry("^a"), "^a", "set_attributes_action")
        validateEntry(
            registry.getEntry("^sla"),
            "^sla",
            "subscription_list_action",
            "^sl",
            "edit_subscription_list_action"
        )
        validateEntry(registry.getEntry("^pp"), "^pp", "prompt_permission_action")
    }

    /**
     * Test the add custom event default predicate rejects [Action.Situation.PUSH_RECEIVED]
     */
    @Test
    public fun testAddCustomEventDefaultPredicateReject() {
        registry.registerDefaultActions(TestApplication.getApplication())

        val entry = registry.getEntry("add_custom_event_action")
        assertNotNull("Add custom event should have a default predicate", entry?.predicate)

        assert(
            entry?.predicate?.apply(
                ActionTestUtils.createArgs(Action.Situation.PUSH_RECEIVED, "value", null)
            ) == false
        ) { "Add custom event should reject PUSH_RECEIVED." }
    }

    /**
     * Test that the fetch device info default predicate rejects [Action.Situation.PUSH_RECEIVED] and
     * [Action.Situation.PUSH_OPENED].
     */
    @Test
    public fun testFetchDeviceInfoDefaultPredicateReject() {
        registry.registerDefaultActions(TestApplication.getApplication())

        val entry = registry.getEntry("fetch_device_info")
        assertNotNull("Fetch device info should have a default predicate", entry?.predicate)

        assert(
            entry?.predicate?.apply(
                ActionTestUtils.createArgs(Action.Situation.PUSH_RECEIVED, "value", null)
            ) == false
        ) { "Fetch device info should reject PUSH_RECEIVED." }

        assert(
            entry?.predicate?.apply(
                ActionTestUtils.createArgs(
                    Action.Situation.PUSH_OPENED, "value", null
                )
            ) == false
        ) { "Fetch device info should reject PUSH_OPENED." }
    }

    /**
     * Test the add custom event default predicate accepts [Action.Situation.MANUAL_INVOCATION] and
     * [Action.Situation.WEB_VIEW_INVOCATION].
     */
    @Test
    public fun testFetchDeviceInfoDefaultPredicateAccepts() {
        registry.registerDefaultActions(TestApplication.getApplication())

        val entry = registry.getEntry("fetch_device_info")
        assertNotNull("Fetch device info should have a default predicate", entry?.predicate)

        assertTrue(
            "Fetch device info should accept MANUAL_INVOCATION.", entry?.predicate?.apply(
                ActionTestUtils.createArgs(
                    Action.Situation.MANUAL_INVOCATION, "value", null
                )
            ) == true
        )

        assertTrue(
            "Fetch device info should accept WEB_VIEW_INVOCATION.", entry?.predicate?.apply(
                ActionTestUtils.createArgs(
                    Action.Situation.WEB_VIEW_INVOCATION, "value", null
                )
            ) == true
        )
    }

    /**
     * Test registering an action.
     */
    @Test
    public fun testRegisterAction() {
        // Register without a predicate
        val entry = registry.registerAction(TestAction(), "who", "are", "we?")
        validateEntry(entry, "who", "are", "we?")
    }

    /**
     * Test registering an action with a an existing
     * registered name.
     */
    @Test
    public fun testRegisterActionNameConflict() {
        val someAction: Action = TestAction()
        registry.registerAction(someAction, "action!")

        val someOtherAction: Action = TestAction()
        registry.registerAction(someOtherAction, "action!", "operation!")

        validateEntry(registry.getEntry("action!"), someOtherAction, "action!", "operation!")

        // Register the original action that conflicts with one of the registered names
        registry.registerAction(someAction, "action!")
        validateEntry(registry.getEntry("action!"), someAction, "action!")
        validateEntry(registry.getEntry("operation!"), someOtherAction, "operation!")
    }

    /**
     * Test registering a empty action name
     */
    @Test(expected = IllegalArgumentException::class)
    public fun testRegisterEmptyActionNames() {
        registry.registerAction(TestAction(), "")
    }

    /**
     * Test trying to register multiple names where one is empty.
     */
    @Test(expected = IllegalArgumentException::class)
    public fun testRegisterMultipleEmptyActionNames() {
        registry.registerAction(TestAction(), "what", "")
    }

    /**
     * Test unregistering an action.
     */
    @Test
    public fun testUnregisterAction() {
        registry.registerAction(TestAction(), "actionName", "anotherName")

        validateEntry(registry.getEntry("actionName"), "actionName", "anotherName")

        registry.unregisterAction("actionName")
        assertNull(
            "Unregister should remove all names for the entry", registry.getEntry("actionName")
        )

        assertNull(
            "Unregister should remove all names for the entry", registry.getEntry("anotherName")
        )
    }

    /**
     * Test situation overrides for an action entry
     */
    @Test
    public fun testEntryActionForSituation() {
        val defaultAction: Action = TestAction()
        val openAction: Action = TestAction()
        val receiveAction: Action = TestAction()

        registry.registerAction(defaultAction, "action")

        val entry = registry.registerAction(defaultAction, "action")
        entry.setSituationOverride(Action.Situation.PUSH_OPENED, openAction)
        entry.setSituationOverride(Action.Situation.PUSH_RECEIVED, receiveAction)

        assertEquals(
            "Should return situation override action",
            entry.getActionForSituation(Action.Situation.PUSH_OPENED),
            openAction
        )
        assertEquals(
            "Should return situation override action",
            entry.getActionForSituation(Action.Situation.PUSH_RECEIVED),
            receiveAction
        )
        assertEquals(
            "Should return default action when no situation overrides exist",
            entry.getActionForSituation(Action.Situation.MANUAL_INVOCATION),
            defaultAction
        )
    }

    /**
     * Validates an entry
     *
     * @param entry Entry to validate
     * @param names Expected names
     */
    private fun validateEntry(entry: ActionRegistry.Entry?, vararg names: String) {
        requireNotNull(entry)
        val entryNames = entry.getNames()

        assertEquals("Entry's name count is unexpected", entryNames.size, names.size)

        for (name in names) {
            assertTrue("Entry does not contain: $name", entryNames.contains(name) == true)
        }
    }

    /**
     * Validates an entry
     *
     * @param entry Entry to validate
     * @param action Expected default action
     * @param names Expected names
     */
    private fun validateEntry(entry: ActionRegistry.Entry?, action: Action, vararg names: String) {
        validateEntry(entry, *names)
        assertEquals("Default action is unexpected", entry?.getDefaultAction(), action)
    }
}
