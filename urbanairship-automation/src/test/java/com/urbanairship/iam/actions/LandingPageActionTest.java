/* Copyright Airship and Contributors */

package com.urbanairship.iam.actions;

import com.urbanairship.ShadowAirshipExecutorsLegacy;
import com.urbanairship.TestApplication;
import com.urbanairship.UAirship;
import com.urbanairship.actions.Action;
import com.urbanairship.actions.ActionArguments;
import com.urbanairship.actions.ActionResult;
import com.urbanairship.actions.ActionValue;
import com.urbanairship.automation.InAppAutomation;
import com.urbanairship.automation.Schedule;
import com.urbanairship.iam.InAppMessage;
import com.urbanairship.iam.html.HtmlDisplayContent;
import com.urbanairship.js.UrlAllowList;
import com.urbanairship.json.JsonValue;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mockito;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;


@Config(
        sdk = 28,
        shadows = { ShadowAirshipExecutorsLegacy.class },
        application = TestApplication.class
)
@LooperMode(LooperMode.Mode.LEGACY)
@RunWith(AndroidJUnit4.class)
public class LandingPageActionTest {

    private LandingPageAction action;
    private UrlAllowList urlAllowList;
    private InAppAutomation inAppAutomation;

    @Before
    public void setup() {
        inAppAutomation = mock(InAppAutomation.class);
        action = new LandingPageAction(() -> inAppAutomation);

        urlAllowList = UAirship.shared().getUrlAllowList();
    }

    /**
     * Test accepts arguments
     */
    @Test
    public void testAcceptsArguments() {
        // Basic URIs
        verifyAcceptsArgumentValue("https://www.urbanairship.com", true);

        // Payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("url", "https://www.urbanairship.com");
        payload.put("cache_on_receive", true);
        verifyAcceptsArgumentValue(payload, true);
    }

    /**
     * Test accepts arguments rejects payloads that do not
     * define a url
     */
    @Test
    public void testRejectsArguments() {
        urlAllowList.addEntry("*");
        verifyAcceptsArgumentValue(null, false);
        verifyAcceptsArgumentValue("", false);
        // Empty payload
        Map<String, Object> payload = new HashMap<>();
        verifyAcceptsArgumentValue(payload, false);
    }

    /**
     * Test accepts arguments for URLs that are allowed.
     */
    @Test
    public void testUrlAllowList() {
        urlAllowList.addEntry("https://yep.example.com");

        // Basic URIs
        verifyAcceptsArgumentValue("https://yep.example.com", true);
        verifyAcceptsArgumentValue("https://nope.example.com", false);

        // Payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("url", "https://yep.example.com");
        payload.put("cache_on_receive", true);
        verifyAcceptsArgumentValue(payload, true);

        payload.put("url", "https://nope.example.com");
        verifyAcceptsArgumentValue(payload, false);
    }

    /**
     * Test perform for every situation the action accepts
     */
    @Test
    public void testPerform() {
        urlAllowList.addEntry("*");

        // Verify scheme less URIs turn into https
        verifyPerform("www.urbanairship.com", "https://www.urbanairship.com");

        // Verify common file URIs
        verifyPerform("file://urbanairship.com", "file://urbanairship.com");
        verifyPerform("https://www.urbanairship.com", "https://www.urbanairship.com");
        verifyPerform("http://www.urbanairship.com", "http://www.urbanairship.com");

        // Verify message URIs
        verifyPerform("message://message_id", "message://message_id");

        // Verify basic payload
        Map<String, Object> payload = new HashMap<>();
        payload.put("url", "http://example.com");
        verifyPerform(payload, "http://example.com");

        // Verify payload without a scheme
        payload.put("url", "www.example.com");
        verifyPerform(payload, "https://www.example.com");
    }

    private void verifyPerform(Object value, final String expectedUrl) {
        @Action.Situation int[] situations = new int[] {
                Action.SITUATION_PUSH_OPENED,
                Action.SITUATION_MANUAL_INVOCATION,
                Action.SITUATION_WEB_VIEW_INVOCATION,
                Action.SITUATION_AUTOMATION
        };

        for (@Action.Situation final int situation : situations) {
            final ActionArguments args = createArgs(situation, value);

            ActionResult result = action.perform(args);
            assertTrue("Should return 'null' result for situation " + situation, result.getValue().isNull());

            verify(inAppAutomation).schedule(Mockito.argThat(new ArgumentMatcher<Schedule<InAppMessage>>() {

                @Override
                public boolean matches(Schedule<InAppMessage> argument) {
                    try {
                        InAppMessage message = argument.getData();
                        if (!message.getType().equals(InAppMessage.TYPE_HTML)) {
                            return false;
                        }

                        if (!message.getDisplayBehavior().equals(InAppMessage.DISPLAY_BEHAVIOR_IMMEDIATE)) {
                            return false;
                        }

                        if (message.isReportingEnabled()) {
                            return false;
                        }

                        HtmlDisplayContent displayContent = message.getDisplayContent();
                        if (displayContent.getRequireConnectivity()) {
                            return false;
                        }

                        if (!displayContent.getUrl().equals(expectedUrl)) {
                            return false;
                        }
                    } catch (IllegalArgumentException e) {
                        return false;
                    }

                    return true;
                }
            }));

            clearInvocations(inAppAutomation);
        }

    }

    private void verifyAcceptsArgumentValue(Object value, boolean shouldAccept) {
        @Action.Situation int[] situations = new int[] {
                Action.SITUATION_PUSH_OPENED,
                Action.SITUATION_MANUAL_INVOCATION,
                Action.SITUATION_WEB_VIEW_INVOCATION,
                Action.SITUATION_FOREGROUND_NOTIFICATION_ACTION_BUTTON
        };

        for (@Action.Situation int situation : situations) {
            ActionArguments args = createArgs(situation, value);
            if (shouldAccept) {
                assertTrue("Should accept arguments in situation " + situation,
                        action.acceptsArguments(args));
            } else {
                assertFalse("Should reject arguments in situation " + situation,
                        action.acceptsArguments(args));
            }

        }

    }

    private ActionArguments createArgs(int situation, Object value) {
        return new ActionArguments(situation, ActionValue.wrap(JsonValue.wrapOpt(value)), null);
    }

}
