/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import com.urbanairship.ApplicationMetrics;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ActionRegistryTest extends BaseTestCase {

    private ActionRegistry registry;

    private ApplicationMetrics metrics;

    @Before
    public void setup() {
        registry = new ActionRegistry();
        metrics = mock(ApplicationMetrics.class);
        TestApplication.getApplication().setApplicationMetrics(metrics);
    }

    /**
     * Tests that the default actions are registered under the correct names
     */
    @Test
    public void testDefaultActions() {
        registry.registerDefaultActions();
        assertEquals("Default entries changed", 17, registry.getEntries().size());

        validateEntry(registry.getEntry("^p"), "^p", "landing_page_action");
        validateEntry(registry.getEntry("^d"), "^d", "deep_link_action");
        validateEntry(registry.getEntry("^+t"), "^+t", "add_tags_action");
        validateEntry(registry.getEntry("^-t"), "^-t", "remove_tags_action");
        validateEntry(registry.getEntry("^u"), "^u", "open_external_url_action");
        validateEntry(registry.getEntry("add_custom_event_action"), "add_custom_event_action");
        validateEntry(registry.getEntry("^s"), "^s", "share_action");
        validateEntry(registry.getEntry("^mc"), "^mc", "open_mc_action");
        validateEntry(registry.getEntry("^c"), "^c", "clipboard_action");
        validateEntry(registry.getEntry("^mco"), "^mco", "open_mc_overlay_action");
        validateEntry(registry.getEntry("toast_action"), "toast_action");
        validateEntry(registry.getEntry("^w"), "^w", "wallet_action");
        validateEntry(registry.getEntry("^csa"), "^csa", "cancel_scheduled_actions");
        validateEntry(registry.getEntry("^sa"), "^sa", "schedule_actions");
        validateEntry(registry.getEntry("^fdi"), "^fdi", "fetch_device_info");
        validateEntry(registry.getEntry("^cc"), "^cc", "channel_capture_action");
        validateEntry(registry.getEntry("^ef"), "^ef", "enable_feature");

    }

    @Test
    public void testDefaultActionsFromResource() {
        registry.registerDefaultActions(TestApplication.getApplication().getApplicationContext());
        assertEquals("Default entries changed", 17, registry.getEntries().size());

        validateEntry(registry.getEntry("^p"), "^p", "landing_page_action");
        validateEntry(registry.getEntry("^d"), "^d", "deep_link_action");
        validateEntry(registry.getEntry("^+t"), "^+t", "add_tags_action");
        validateEntry(registry.getEntry("^-t"), "^-t", "remove_tags_action");
        validateEntry(registry.getEntry("^u"), "^u", "open_external_url_action");
        validateEntry(registry.getEntry("add_custom_event_action"), "add_custom_event_action");
        validateEntry(registry.getEntry("^s"), "^s", "share_action");
        validateEntry(registry.getEntry("^mc"), "^mc", "open_mc_action");
        validateEntry(registry.getEntry("^c"), "^c", "clipboard_action");
        validateEntry(registry.getEntry("^mco"), "^mco", "open_mc_overlay_action");
        validateEntry(registry.getEntry("toast_action"), "toast_action");
        validateEntry(registry.getEntry("^w"), "^w", "wallet_action");
        validateEntry(registry.getEntry("^csa"), "^csa", "cancel_scheduled_actions");
        validateEntry(registry.getEntry("^sa"), "^sa", "schedule_actions");
        validateEntry(registry.getEntry("^fdi"), "^fdi", "fetch_device_info");
        validateEntry(registry.getEntry("^cc"), "^cc", "channel_capture_action");
        validateEntry(registry.getEntry("^ef"), "^ef", "enable_feature");
    }

    /**
     * Test the landing page default predicate rejects Action.SITUATION_PUSH_RECEIVED
     * if the app has not been opened in the last week.
     */
    @Test
    public void testLandingPageDefaultPredicateReject() {
        registry.registerDefaultActions();

        ActionRegistry.Entry entry = registry.getEntry("^p");
        assertNotNull("Landing Page should have a default predicate", entry.getPredicate());

        // Set the last open to 8 days ago
        when(metrics.getLastOpenTimeMillis()).thenReturn(System.currentTimeMillis() - 8 * 24 * 1000 * 3600);

        assertFalse("Should reject PUSH_RECEIVED when the app has not been opened in the week.",
                entry.getPredicate().apply(ActionTestUtils.createArgs(Action.SITUATION_PUSH_RECEIVED, "value", null)));
    }

    /**
     * Test the landing page default predicate accepts Action.SITUATION_PUSH_RECEIVED
     * if the app has been opened in the last week.
     */
    @Test
    public void testLandingPageDefaultPredicateAccepts() {
        registry.registerDefaultActions();

        ActionRegistry.Entry entry = registry.getEntry("^p");
        assertNotNull("Landing Page should have a default predicate", entry.getPredicate());

        // Set the last open to 6 days ago
        when(metrics.getLastOpenTimeMillis()).thenReturn(System.currentTimeMillis() - 6 * 24 * 1000 * 3600);

        assertTrue("Should accept PUSH_RECEIVED when the app has been opened in the last week.",
                entry.getPredicate().apply(ActionTestUtils.createArgs(Action.SITUATION_PUSH_RECEIVED, "value", null)));
    }

    /**
     * Test the add custom event default predicate rejects Action.SITUATION_PUSH_RECEIVED.
     */
    @Test
    public void testAddCustomEventDefaultPredicateReject() {
        registry.registerDefaultActions();

        ActionRegistry.Entry entry = registry.getEntry("add_custom_event_action");
        assertNotNull("Add custom event should have a default predicate", entry.getPredicate());

        assertFalse("Add custom event should reject PUSH_RECEIVED.",
                entry.getPredicate().apply(ActionTestUtils.createArgs(Action.SITUATION_PUSH_RECEIVED, "value", null)));
    }

    /**
     * Test that the fetch device info default predicate rejects Action.SITUATION_PUSH_RECEIVED and
     * Action.SITUATION_PUSH_OPENED.
     */
    @Test
    public void testFetchDeviceInfoDefaultPredicateReject() {
        registry.registerDefaultActions();

        ActionRegistry.Entry entry = registry.getEntry("fetch_device_info");
        assertNotNull("Fetch device info should have a default predicate", entry.getPredicate());

        assertFalse("Fetch device info should reject PUSH_RECEIVED.",
                entry.getPredicate().apply(ActionTestUtils.createArgs(Action.SITUATION_PUSH_RECEIVED, "value", null)));

        assertFalse("Fetch device info should reject PUSH_OPENED.",
                entry.getPredicate().apply(ActionTestUtils.createArgs(Action.SITUATION_PUSH_OPENED, "value", null)));
    }

    /**
     * Test the add custom event default predicate accepts Action.SITUATION_MANUAL_INVOCATION and
     * Action.SITUATION_WEB_VIEW_INVOCATION.
     */
    @Test
    public void testFetchDeviceInfoDefaultPredicateAccepts() {
        registry.registerDefaultActions();

        ActionRegistry.Entry entry = registry.getEntry("fetch_device_info");
        assertNotNull("Fetch device info should have a default predicate", entry.getPredicate());

        assertTrue("Fetch device info should accept MANUAL_INVOCATION.",
                entry.getPredicate().apply(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "value", null)));

        assertTrue("Fetch device info should accept WEB_VIEW_INVOCATION.",
                entry.getPredicate().apply(ActionTestUtils.createArgs(Action.SITUATION_WEB_VIEW_INVOCATION, "value", null)));
    }


    /**
     * Test the landing page default predicate rejects Action.SITUATION_PUSH_RECEIVED
     * if the app has never been opened.
     */
    @Test
    public void testLandingPagePredicateAppNeverOpened() {
        registry.registerDefaultActions();

        ActionRegistry.Entry entry = registry.getEntry("^p");
        assertNotNull("Landing Page should have a default predicate", entry.getPredicate());

        // Set to -1, the default.
        when(metrics.getLastOpenTimeMillis()).thenReturn(-1L);

        assertFalse("Should not accept PUSH_RECEIVED when the app has never been opened.",
                entry.getPredicate().apply(ActionTestUtils.createArgs(Action.SITUATION_PUSH_RECEIVED, "value", null)));
    }

    /**
     * Test registering an action.
     */
    @Test
    public void testRegisterAction() {
        // Register without a predicate
        ActionRegistry.Entry entry = registry.registerAction(new TestAction(), "who", "are", "we?");
        validateEntry(entry, "who", "are", "we?");
    }

    /**
     * Test registering an action with a an existing
     * registered name.
     */
    @Test
    public void testRegisterActionNameConflict() {
        Action someAction = new TestAction();
        registry.registerAction(someAction, "action!");

        Action someOtherAction = new TestAction();
        registry.registerAction(someOtherAction, "action!", "operation!");

        validateEntry(registry.getEntry("action!"), someOtherAction, "action!", "operation!");

        // Register the original action that conflicts with one of the registered names
        registry.registerAction(someAction, "action!");
        validateEntry(registry.getEntry("action!"), someAction, "action!");
        validateEntry(registry.getEntry("operation!"), someOtherAction, "operation!");
    }

    /**
     * Test registering a null action.
     */
    @Test(expected=IllegalArgumentException.class)
    public void testRegisterNullAction() {
        registry.registerAction((Action)null, "hi");
    }

    /**
     * Test registering a null action class.
     */
    @Test(expected=IllegalArgumentException.class)
    public void testRegisterNullActionClass() {
        registry.registerAction((Class<? extends Action>) null, "hi");
    }

    /**
     * Test registering a null action names
     */
    @Test(expected=IllegalArgumentException.class)
    public void testRegisterNullActionNames() {
        registry.registerAction(new TestAction(), (String[]) null);
    }

    /**
     * Test registering a empty action name
     */
    @Test(expected=IllegalArgumentException.class)
    public void testRegisterEmptyActionNames() {
        registry.registerAction(new TestAction(), "");
    }

    /**
     * Test trying to register multiple names where one is empty.
     */
    @Test(expected=IllegalArgumentException.class)
    public void testRegisterMultipleEmptyActionNames() {
        registry.registerAction(new TestAction(), "what", "");
    }

    /**
     * Test unregistering an action.
     */
    @Test
    public void testUnregisterAction() {
        registry.registerAction(new TestAction(), "actionName", "anotherName");

        validateEntry(registry.getEntry("actionName"), "actionName", "anotherName");

        registry.unregisterAction("actionName");
        assertNull("Unregister should remove all names for the entry", registry.getEntry("actionName"));
        assertNull("Unregister should remove all names for the entry", registry.getEntry("anotherName"));
    }

    /**
     * Test situation overrides for an action entry
     */
    @Test
    public void testEntryActionForSituation() {
        Action defaultAction = new TestAction();
        Action openAction = new TestAction();
        Action receiveAction = new TestAction();

        registry.registerAction(defaultAction, "action");

        ActionRegistry.Entry entry = registry.registerAction(defaultAction, "action");
        entry.addSituationOverride(openAction, Action.SITUATION_PUSH_OPENED);
        entry.addSituationOverride(receiveAction, Action.SITUATION_PUSH_RECEIVED);

        assertEquals("Should return situation override action",
                entry.getActionForSituation(Action.SITUATION_PUSH_OPENED), openAction);
        assertEquals("Should return situation override action",
                entry.getActionForSituation(Action.SITUATION_PUSH_RECEIVED), receiveAction);
        assertEquals("Should return default action when no situation overrides exist",
                entry.getActionForSituation(Action.SITUATION_MANUAL_INVOCATION), defaultAction);
    }

    /**
     * Validates an entry
     *
     * @param entry Entry to validate
     * @param names Expected names
     */
    private void validateEntry(ActionRegistry.Entry entry, String... names) {
        List<String> entryNames = entry.getNames();

        assertEquals("Entry's name count is unexpected", entryNames.size(), names.length);

        for (String name : names) {
            assertTrue("Entry does not contain: " + name, entryNames.contains(name));
        }
    }

    /**
     * Validates an entry
     *
     * @param entry Entry to validate
     * @param action Expected default action
     * @param names Expected names
     */
    private void validateEntry(ActionRegistry.Entry entry, Action action, String... names) {
        validateEntry(entry, names);
        assertEquals("Default action is unexpected", entry.getDefaultAction(), action);
    }
}
