/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import android.content.Intent;

import com.urbanairship.BaseTestCase;
import com.urbanairship.UrlAllowList;

import org.junit.Test;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowApplication;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

public class OpenExternalUrlActionTest extends BaseTestCase {

    private final UrlAllowList urlAllowList = mock(UrlAllowList.class);
    private final OpenExternalUrlAction action = new OpenExternalUrlAction(() -> urlAllowList);

    /**
     * Test accepts arguments
     */
    @Test
    public void testAcceptsArguments() {
        when(urlAllowList.isAllowed(any(), eq(UrlAllowList.SCOPE_OPEN_URL))).thenReturn(true);

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "http://example.com");
        assertTrue("Should accept valid url string", action.acceptsArguments(args));

        args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_OPENED, "http://example.com");
        assertTrue("Should accept valid url", action.acceptsArguments(args));

        args = ActionTestUtils.createArgs(Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON, "http://example.com");
        assertTrue("Should accept Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON", action.acceptsArguments(args));

        args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_RECEIVED, "http://example.com");
        assertFalse("Should not accept Action.SITUATION_PUSH_RECEIVED", action.acceptsArguments(args));
    }

    /**
     * Test accepts arguments for URLs that are allowed.
     */
    @Test
    public void testUrlAllowList() {
        when(urlAllowList.isAllowed("https://yep.example.com", UrlAllowList.SCOPE_OPEN_URL)).thenReturn(true);
        when(urlAllowList.isAllowed("https://nope.example.com", UrlAllowList.SCOPE_OPEN_URL)).thenReturn(false);

        assertTrue(action.acceptsArguments(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "https://yep.example.com")));
        assertFalse(action.acceptsArguments(ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "https://nope.example.com")));
    }

    /**
     * Test perform tries to start an activity with the URL
     */
    @Test
    public void testPerform() {
        when(urlAllowList.isAllowed(any(), eq(UrlAllowList.SCOPE_OPEN_URL))).thenReturn(true);

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_WEB_VIEW_INVOCATION, "http://example.com");
        ActionResult result = action.perform(args);

        assertEquals("Value should be the uri", "http://example.com", result.getValue().getString());
        validateLastActivity("http://example.com");

        args = ActionTestUtils.createArgs(Action.SITUATION_WEB_VIEW_INVOCATION, "adfadfafdsaf adfa dsfadfsa example");
        result = action.perform(args);
        assertEquals("Value should be the uri", "adfadfafdsaf adfa dsfadfsa example", result.getValue().getString());
        validateLastActivity("adfadfafdsaf adfa dsfadfsa example");
    }

    /**
     * Helper method to validate the activity is launched correctly from
     * the open url action
     */
    private void validateLastActivity(String expectedUri) {
        ShadowApplication application = shadowOf(RuntimeEnvironment.application);
        Intent intent = application.getNextStartedActivity();
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.getFlags());
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals(expectedUri, intent.getDataString());
    }

}
