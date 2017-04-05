/* Copyright 2017 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.content.Intent;

import com.urbanairship.BaseTestCase;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.shadows.ShadowApplication;

import java.net.MalformedURLException;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class OpenExternalUrlActionTest extends BaseTestCase {

    private OpenExternalUrlAction action;


    @Before
    public void setup() {
        action = new OpenExternalUrlAction();
    }

    /**
     * Test accepts arguments
     */
    @Test
    public void testAcceptsArguments() throws MalformedURLException {
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "http://example.com");
        assertTrue("Should accept valid url string", action.acceptsArguments(args));

        args = ActionTestUtils.createArgs(Action.SITUATION_WEB_VIEW_INVOCATION, "adfadfafdsaf adfa dsfadfsa example");
        assertTrue("Should accept any string", action.acceptsArguments(args));

        args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_OPENED, "http://example.com");
        assertTrue("Should accept valid url", action.acceptsArguments(args));

        args = ActionTestUtils.createArgs(Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON, "http://example.com");
        assertTrue("Should accept Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON", action.acceptsArguments(args));

        args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_RECEIVED, "http://example.com");
        assertFalse("Should not accept Action.SITUATION_PUSH_RECEIVED", action.acceptsArguments(args));
    }

    /**
     * Test perform tries to start an activity with the URL
     */
    @Test
    public void testPerform() throws MalformedURLException {
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
        ShadowApplication application = ShadowApplication.getInstance();
        Intent intent = application.getNextStartedActivity();
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.getFlags());
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals(expectedUri, intent.getDataString());
    }
}
