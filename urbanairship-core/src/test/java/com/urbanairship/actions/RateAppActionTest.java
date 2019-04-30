/* Copyright Airship and Contributors */

package com.urbanairship.actions;

import android.content.Intent;
import android.net.Uri;

import com.urbanairship.AirshipConfigOptions;
import com.urbanairship.BaseTestCase;
import com.urbanairship.TestApplication;
import com.urbanairship.json.JsonMap;

import org.junit.Before;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowApplication;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

public class RateAppActionTest extends BaseTestCase {

    private RateAppAction action;

    @Before
    public void setup() {
        action = new RateAppAction();

        AirshipConfigOptions configOptions = new AirshipConfigOptions.Builder()
                .setAppStoreUri(Uri.parse("cool://link"))
                .setDevelopmentAppKey("appKey")
                .setDevelopmentAppSecret("appSecret")
                .build();

        TestApplication.getApplication().setOptions(configOptions);
    }

    /**
     * Test accepted arguments
     */
    @Test
    public void testAcceptsArguments() {
        // Test payload without prompt
        JsonMap linkPayload = JsonMap.newBuilder()
                                     .put(RateAppAction.SHOW_LINK_PROMPT_KEY, false)
                                     .build();

        verifyAcceptsArgumentValue(linkPayload, true);

        // Test payload with prompt
        JsonMap linkPromptPayload = JsonMap.newBuilder()
                                           .put(RateAppAction.SHOW_LINK_PROMPT_KEY, true)
                                           .build();

        verifyAcceptsArgumentValue(linkPromptPayload, true);

        // Test customized prompt
        JsonMap customizedMessagePayload = JsonMap.newBuilder()
                                                  .put(RateAppAction.SHOW_LINK_PROMPT_KEY, true)
                                                  .put(RateAppAction.TITLE_KEY, "some title")
                                                  .put(RateAppAction.BODY_KEY, "some body")
                                                  .build();

        verifyAcceptsArgumentValue(customizedMessagePayload, true);

        // Test empty arguments
        verifyAcceptsArgumentValue(null, true);

    }

    /**
     * Test perform with link.
     */
    @Test
    public void testPerformLink() {
        // Test payload without prompt
        JsonMap linkPayload = JsonMap.newBuilder()
                                     .put(RateAppAction.SHOW_LINK_PROMPT_KEY, false)
                                     .build();

        verifyPerform(linkPayload, new PerformCallback() {
            @Override
            public void verify(Intent intent) {
                assertEquals(Intent.ACTION_VIEW, intent.getAction());
                assertEquals("cool://link", intent.getDataString());
                assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.getFlags());
            }
        });

        // Test empty payload
        verifyPerform(null, new PerformCallback() {
            @Override
            public void verify(Intent intent) {
                assertEquals(Intent.ACTION_VIEW, intent.getAction());
                assertEquals("cool://link", intent.getDataString());
                assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, intent.getFlags());
            }
        });
    }

    /**
     * Test perform with prompt.
     */
    @Test
    public void testPerformPrompt() {
        // Test payload with only prompt
        JsonMap promptPayload = JsonMap.newBuilder()
                                       .put(RateAppAction.SHOW_LINK_PROMPT_KEY, true)
                                       .build();

        verifyPerform(promptPayload, new PerformCallback() {
            @Override
            public void verify(Intent intent) {
                assertEquals(RateAppAction.SHOW_RATE_APP_INTENT_ACTION, intent.getAction());
                assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP, intent.getFlags());

                assertEquals(Uri.parse("cool://link"), intent.getParcelableExtra(RateAppAction.STORE_URI_KEY));
            }
        });

        // Test customized prompt
        JsonMap customizedMessagePayload = JsonMap.newBuilder()
                                                  .put(RateAppAction.SHOW_LINK_PROMPT_KEY, true)
                                                  .put(RateAppAction.TITLE_KEY, "some title")
                                                  .put(RateAppAction.BODY_KEY, "some body")
                                                  .build();
        // Test empty payload
        verifyPerform(customizedMessagePayload, new PerformCallback() {
            @Override
            public void verify(Intent intent) {
                assertEquals(RateAppAction.SHOW_RATE_APP_INTENT_ACTION, intent.getAction());
                assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP, intent.getFlags());

                assertEquals(Uri.parse("cool://link"), intent.getParcelableExtra(RateAppAction.STORE_URI_KEY));
                assertEquals("some title", intent.getStringExtra(RateAppAction.TITLE_KEY));
                assertEquals("some body", intent.getStringExtra(RateAppAction.BODY_KEY));
            }
        });
    }

    private interface PerformCallback {

        void verify(Intent intent);

    }

    private void verifyPerform(JsonMap value, PerformCallback verifyCallback) {
        ShadowApplication application = shadowOf(RuntimeEnvironment.application);

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
            assertEquals(ActionResult.STATUS_COMPLETED, result.getStatus());

            Intent intent = application.getNextStartedActivity();
            verifyCallback.verify(intent);
        }
    }

    private void verifyAcceptsArgumentValue(JsonMap jsonMap, boolean shouldAccept) {
        @Action.Situation int[] situations = new int[] {
                Action.SITUATION_PUSH_OPENED,
                Action.SITUATION_MANUAL_INVOCATION,
                Action.SITUATION_WEB_VIEW_INVOCATION,
                Action.SITUATION_AUTOMATION,
                Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON
        };

        for (@Action.Situation int situation : situations) {
            ActionArguments args = ActionTestUtils.createArgs(situation, jsonMap == null ? null : ActionValue.wrap(jsonMap));
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
