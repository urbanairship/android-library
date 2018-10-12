package com.urbanairship.actions;/* Copyright 2018 Urban Airship and Contributors */


import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.urbanairship.BaseTestCase;
import com.urbanairship.push.PushMessage;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.shadows.ShadowApplication;


import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

public class DeepLinkActionTest extends BaseTestCase {

    private DeepLinkAction action;


    @Before
    public void setup() {
        action = new DeepLinkAction();;
    }

    @Test
    public void testAcceptsArguments() {
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_MANUAL_INVOCATION, "http://example.com");
        assertTrue("Should accept valid url string", action.acceptsArguments(args));

        args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_OPENED, "http://example.com");
        assertTrue("Should accept valid url", action.acceptsArguments(args));

        args = ActionTestUtils.createArgs(Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON, "http://example.com");
        assertTrue("Should accept Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON", action.acceptsArguments(args));

        args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_RECEIVED, "http://example.com");
        assertFalse("Should not accept Action.SITUATION_PUSH_RECEIVED", action.acceptsArguments(args));
    }

    @Test
    public void testPerform() {
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_WEB_VIEW_INVOCATION, "http://example.com");
        ActionResult result = action.perform(args);

        assertEquals("Value should be the uri", "http://example.com", result.getValue().getString());
        validateLastActivity("http://example.com", null);

        args = ActionTestUtils.createArgs(Action.SITUATION_WEB_VIEW_INVOCATION, "adfadfafdsaf adfa dsfadfsa example");
        result = action.perform(args);
        assertEquals("Value should be the uri", "adfadfafdsaf adfa dsfadfsa example", result.getValue().getString());
        validateLastActivity("adfadfafdsaf adfa dsfadfsa example", null);
    }

    @Test
    public void testPerformPushMessage() {
        Bundle bundle = new Bundle();
        bundle.putString("oh", "hi");
        PushMessage message = new PushMessage(bundle);

        Bundle metadata = new Bundle();
        metadata.putParcelable(ActionArguments.PUSH_MESSAGE_METADATA, message);
        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_PUSH_OPENED, "http://example.com", metadata);
        ActionResult result = action.perform(args);

        assertEquals("Value should be the uri", "http://example.com", result.getValue().getString());
        validateLastActivity("http://example.com", message);
    }


    private void validateLastActivity(@NonNull String expectedUri, @Nullable PushMessage message) {
        ShadowApplication application = ShadowApplication.getInstance();
        Intent intent = application.getNextStartedActivity();
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.getFlags());
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals(expectedUri, intent.getDataString());

        if (message != null) {
            assertEquals(message, PushMessage.fromIntent(intent));
        }
    }

}