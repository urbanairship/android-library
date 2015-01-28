/*
Copyright 2009-2015 Urban Airship Inc. All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE URBAN AIRSHIP INC ``AS IS'' AND ANY EXPRESS OR
IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO
EVENT SHALL URBAN AIRSHIP INC OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.urbanairship.actions;

import com.urbanairship.ApplicationMetrics;
import com.urbanairship.RobolectricGradleTestRunner;
import com.urbanairship.TestApplication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricGradleTestRunner.class)
public class ActionRegistryTest {

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
        assertEquals("Default entries changed", 7, registry.getEntries().size());

        validateEntry(registry.getEntry("^p"), "^p", "landing_page_action");
        validateEntry(registry.getEntry("^d"), "^d", "deep_link_action");
        validateEntry(registry.getEntry("^+t"), "^+t", "add_tags_action");
        validateEntry(registry.getEntry("^-t"), "^-t", "remove_tags_action");
        validateEntry(registry.getEntry("^u"), "^u", "open_external_url_action");
        validateEntry(registry.getEntry("add_custom_event_action"), "add_custom_event_action");
        validateEntry(registry.getEntry("^s"), "^s", "share_action");
    }

    /**
     * Test the landing page default predicate rejects Situation.PUSH_RECEIVED
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
                entry.getPredicate().apply(ActionTestUtils.createArgs(Situation.PUSH_RECEIVED, "value", null)));
    }

    /**
     * Test the landing page default predicate accepts Situation.PUSH_RECEIVED
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
                entry.getPredicate().apply(ActionTestUtils.createArgs(Situation.PUSH_RECEIVED, "value", null)));
    }

    /**
     * Test the add custom event default predicate rejects Situation.PUSH_RECEIVED and
     * Situation.PUSH_OPENED.
     */
    @Test
    public void testAddCustomEventDefaultPredicateReject() {
        registry.registerDefaultActions();

        ActionRegistry.Entry entry = registry.getEntry("add_custom_event_action");
        assertNotNull("Add custom event should have a default predicate", entry.getPredicate());

        assertFalse("Add custom event should reject PUSH_RECEIVED.",
                entry.getPredicate().apply(ActionTestUtils.createArgs(Situation.PUSH_RECEIVED, "value", null)));

        assertFalse("Add custom event should reject PUSH_OPENED.",
                entry.getPredicate().apply(ActionTestUtils.createArgs(Situation.PUSH_OPENED, "value", null)));
    }

    /**
     * Test the add custom event default predicate accepts Situation.MANUAL_INVOCATION and
     * Situation.WEB_VIEW_INVOCATION.
     */
    @Test
    public void testAddCustomEventDefaultPredicateAccepts() {
        registry.registerDefaultActions();

        ActionRegistry.Entry entry = registry.getEntry("add_custom_event_action");
        assertNotNull("Add custom event should have a default predicate", entry.getPredicate());

        assertTrue("Add custom event should accept MANUAL_INVOCATION.",
                entry.getPredicate().apply(ActionTestUtils.createArgs(Situation.MANUAL_INVOCATION, "value", null)));

        assertTrue("Add custom event should accept WEB_VIEW_INVOCATION.",
                entry.getPredicate().apply(ActionTestUtils.createArgs(Situation.WEB_VIEW_INVOCATION, "value", null)));
    }

    /**
     * Test the landing page default predicate rejects Situation.PUSH_RECEIVED
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
                entry.getPredicate().apply(ActionTestUtils.createArgs(Situation.PUSH_RECEIVED, "value", null)));
    }

    /**
     * Test registering an action.
     */
    @Test
    public void testRegisterAction() {
        Action action = new TestAction();
        String[] names = new String[] { "who", "are", "we?" };

        // Register without a predicate
        ActionRegistry.Entry entry = registry.registerAction(action, names);
        validateEntry(entry, names);
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
     * Test registering a null action and/or empty names
     */
    @Test
    public void testRegisterActionInvalid() {
        int actionCount = registry.getEntries().size();

        ActionRegistry.Entry entry = registry.registerAction(null, "hi");
        assertEquals("Null action should not register", actionCount, registry.getEntries().size());
        assertNull(entry);

        entry = registry.registerAction(new TestAction(), (String[]) null);
        assertEquals("Null name should not register", actionCount, registry.getEntries().size());
        assertNull(entry);

        entry = registry.registerAction(new TestAction(), new String[] { });
        assertEquals("Empty names should not register", actionCount, registry.getEntries().size());
        assertNull(entry);

        entry = registry.registerAction(new TestAction(), "");
        assertEquals("Empty name should not register", actionCount, registry.getEntries().size());
        assertNull(entry);

        entry = registry.registerAction(new TestAction(), "", "actionName");
        assertEquals("Empty name should not register", actionCount, registry.getEntries().size());
        assertNull(entry);
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
        entry.addSituationOverride(openAction, Situation.PUSH_OPENED);
        entry.addSituationOverride(receiveAction, Situation.PUSH_RECEIVED);

        assertEquals("Should return situation override action",
                entry.getActionForSituation(Situation.PUSH_OPENED), openAction);
        assertEquals("Should return situation override action",
                entry.getActionForSituation(Situation.PUSH_RECEIVED), receiveAction);
        assertEquals("Should return default action when no situation overrides exist",
                entry.getActionForSituation(Situation.MANUAL_INVOCATION), defaultAction);
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
