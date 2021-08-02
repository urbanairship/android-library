package com.urbanairship.actions;/* Copyright Airship and Contributors */

import android.content.Intent;
import android.os.Bundle;

import com.urbanairship.BaseTestCase;
import com.urbanairship.UAirship;
import com.urbanairship.base.Supplier;
import com.urbanairship.push.PushMessage;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowApplication;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

public class DeepLinkActionTest extends BaseTestCase {

    private DeepLinkAction action;
    private UAirship mockShip;

    @Before
    public void setup() {
        mockShip = Mockito.mock(UAirship.class);
        action = new DeepLinkAction(new Supplier<UAirship>() {
            @Nullable
            @Override
            public UAirship get() {
                return mockShip;
            }
        });
    }

    @Test
    public void testPerform() {
        String deepLink = "http://example.com";
        when(mockShip.deepLink(deepLink)).thenReturn(true);

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_WEB_VIEW_INVOCATION, deepLink);
        ActionResult result = action.perform(args);

        assertEquals(result.getStatus(), ActionResult.STATUS_COMPLETED);
        assertEquals(deepLink, result.getValue().getString());
        verify(mockShip).deepLink(deepLink);
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
    public void testPerformFallback() {
        String deepLink = "http://example.com";
        when(mockShip.deepLink(deepLink)).thenReturn(false);

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_WEB_VIEW_INVOCATION, deepLink);
        ActionResult result = action.perform(args);

        assertEquals(deepLink, result.getValue().getString());
        validateLastActivity(deepLink, null);
    }

    @Test
    public void testPerformFallbackAnyString() {
        String deepLink = "adfadfafdsaf adfa dsfadfsa example";
        when(mockShip.deepLink(deepLink)).thenReturn(false);

        ActionArguments args = ActionTestUtils.createArgs(Action.SITUATION_WEB_VIEW_INVOCATION, deepLink);
        ActionResult result = action.perform(args);

        assertEquals(deepLink, result.getValue().getString());
        validateLastActivity(deepLink, null);
    }

    @Test
    public void testFallbackPushMessage() {
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
        ShadowApplication application = shadowOf(RuntimeEnvironment.application);
        Intent intent = application.getNextStartedActivity();
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.getFlags());
        assertEquals(Intent.ACTION_VIEW, intent.getAction());
        assertEquals(expectedUri, intent.getDataString());

        if (message != null) {
            assertEquals(message, PushMessage.fromIntent(intent));
        }
    }
}
