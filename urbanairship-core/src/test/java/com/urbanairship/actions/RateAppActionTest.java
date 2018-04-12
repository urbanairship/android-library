/* Copyright 2018 Urban Airship and Contributors */

package com.urbanairship.actions;

import android.content.Intent;
import android.os.Bundle;

import com.urbanairship.BaseTestCase;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.shadows.ShadowApplication;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;


public class RateAppActionTest extends BaseTestCase {

    private RateAppAction action;

    @Before
    public void setup() {
        action = new RateAppAction();
    }

    /**
    * Test accepted arguments
    */
    @Test
    public void testAcceptsArguments() {
        // Test payload without prompt
        Map<String, Object> linkPayload = new HashMap<>();
        linkPayload.put(RateAppAction.SHOW_LINK_PROMPT_KEY, false);

        verifyAcceptsArgumentValue(linkPayload, true);

        // Test payload with prompt
        Map<String, Object> linkPromptPayload = new HashMap<>();
        linkPromptPayload.put(RateAppAction.SHOW_LINK_PROMPT_KEY, true);

        verifyAcceptsArgumentValue(linkPromptPayload, true);

        // Test customized prompt
        Map<String, Object> customizedMessagePayload = new HashMap<>();
        customizedMessagePayload.put(RateAppAction.TITLE_KEY, "some title");
        customizedMessagePayload.put(RateAppAction.BODY_KEY, "some body");
        customizedMessagePayload.put(RateAppAction.SHOW_LINK_PROMPT_KEY, true);

        verifyAcceptsArgumentValue(customizedMessagePayload, true);
    }

    /**
    * Test rejected arguments
    */
    @Test
    public void testRejectsArguments() {
        // Test payload without prompt
        Map<String, Object> noShowLinkPromptOptionPayload = new HashMap<>();
        noShowLinkPromptOptionPayload.put(RateAppAction.TITLE_KEY, "some title");
        noShowLinkPromptOptionPayload.put(RateAppAction.BODY_KEY, "some body");

        verifyAcceptsArgumentValue(noShowLinkPromptOptionPayload, false);

        // Test payload with title that's too long
        Map<String, Object> titleTooLongPayload = new HashMap<>();
        titleTooLongPayload.put(RateAppAction.TITLE_KEY, String.format("%50s", "Title too long").replace(' ', '*'));
        titleTooLongPayload.put(RateAppAction.BODY_KEY, "some body");

        verifyAcceptsArgumentValue(titleTooLongPayload, false);

        // Test payload with body that's too long
        Map<String, Object> bodyTooLongPayload = new HashMap<>();
        bodyTooLongPayload.put(RateAppAction.TITLE_KEY, "some title");
        bodyTooLongPayload.put(RateAppAction.BODY_KEY, String.format("%100s", "Body too long").replace(' ', '*'));

        verifyAcceptsArgumentValue(bodyTooLongPayload, false);
    }

    /**
     * Test perform for every situation the action accepts
     */
    @Test
    public void testPerform() {
        // Test payload without prompt
        Map<String, Object> linkPayload = new HashMap<>();
        linkPayload.put(RateAppAction.SHOW_LINK_PROMPT_KEY, false);

        verifyPerform(linkPayload, linkPayload);

        // Test payload with prompt
        Map<String, Object> linkPromptPayload = new HashMap<>();
        linkPromptPayload.put(RateAppAction.SHOW_LINK_PROMPT_KEY, true);

        verifyPerform(linkPromptPayload, linkPromptPayload);

        // Test customized prompt
        Map<String, Object> customizedMessagePayload = new HashMap<>();
        customizedMessagePayload.put(RateAppAction.TITLE_KEY, "some title");
        customizedMessagePayload.put(RateAppAction.BODY_KEY, "some body");
        customizedMessagePayload.put(RateAppAction.SHOW_LINK_PROMPT_KEY, true);

        verifyPerform(customizedMessagePayload, customizedMessagePayload);
    }

    private void verifyPerform(Object value, Map<String, Object> expectedExtras) {
        ShadowApplication application = ShadowApplication.getInstance();

        @Action.Situation int[] situations = new int[] {
                Action.SITUATION_PUSH_OPENED,
                Action.SITUATION_MANUAL_INVOCATION,
                Action.SITUATION_WEB_VIEW_INVOCATION,
                Action.SITUATION_AUTOMATION,
                Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON
        };

        for (@Action.Situation int situation : situations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, value);

            ActionResult result = action.perform(args);
            assertTrue("Should return 'null' result for situation " + situation, result.getValue().isNull());

            Intent intent = application.getNextStartedActivity();

            if (Boolean.TRUE.equals(expectedExtras.get(RateAppAction.SHOW_LINK_PROMPT_KEY))) {
                Boolean showLinkPrompt = intent.getExtras().getBoolean(RateAppAction.SHOW_LINK_PROMPT_KEY);
                assertNotNull(showLinkPrompt);

                assertEquals("Invalid intent action for situation " + situation,
                        RateAppAction.SHOW_RATE_APP_INTENT_ACTION, intent.getAction());

                // Test that all extras added to intent are present
                Bundle bundle = intent.getExtras();

                if (bundle != null) {
                    for (String key : expectedExtras.keySet()) {
                        Object expectedExtra = expectedExtras.get(key);
                        Object extra = bundle.get(key);
                        assertEquals("Invalid extra for situation" + situation,
                                expectedExtra, extra);
                    }
                }

                assertEquals("Invalid intent flags for situation " + situation,
                        Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP, intent.getFlags());

            } else {
                // Open link directly when showLinkPrompt == false
                assertEquals("Invalid intent action for situation " + situation,
                        Intent.ACTION_VIEW, intent.getAction());
            }
        }
    }

    private void verifyAcceptsArgumentValue(Object value, boolean shouldAccept) {
        @Action.Situation int[] situations = new int[] {
                Action.SITUATION_PUSH_OPENED,
                Action.SITUATION_MANUAL_INVOCATION,
                Action.SITUATION_WEB_VIEW_INVOCATION,
                Action.SITUATION_AUTOMATION,
                Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON
        };

        for (@Action.Situation int situation : situations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, value);
            if (shouldAccept) {
                assertTrue("Should accept arguments in situation " + situation,
                        action.acceptsArguments(args));
            } else {
                assertFalse("Should reject arguments in situation " + situation,
                        action.acceptsArguments(args));
            }

        }
    }
}
